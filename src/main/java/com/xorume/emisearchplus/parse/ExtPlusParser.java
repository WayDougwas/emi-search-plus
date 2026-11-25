package com.xorume.emisearchplus.parse;

import com.xorume.emisearchplus.query.Queries;
import dev.emi.emi.search.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser da sintaxe Extended+ do NEI GTNH
 * (https://wiki.gtnewhorizons.com/wiki/Extended+_NEI_Search_Mode):
 * <pre>
 * orExpr   : seqExpr ('|' seqExpr)*
 * seqExpr  : (' '* unary ' '*)+                 espaço = E
 * unary    : '{' orExpr '}'? | '-' unary | PREFIX token | token
 * token    : 'r'? '/' regex '/'? | '"' texto '"'? | TEXTO      '\' escapa qualquer caractere
 * receitas : clause*   clause : ('<'|'<&'|'>'|'>&'|'^'|'^&')? orExpr | '-'? '!' categoria | '-'? '?' especial
 * Categoria: nome ou id da aba/máquina (!@mod = mod da categoria). Especiais: ?inv (dá para fazer com o inventário),
 * ?scc (todas as entradas existem no estoque do terminal do SCC).
 * $rótulo&gt;número compara números do tooltip ($"attack damage"&gt;=10).
 * </pre>
 * Prefixos: @ mod, $ tooltip, # tag, &amp; id, % aba criativa, ~ tipo de stack, = palavra inteira no nome.
 * Com a opção explicitAnd, só "&amp;"/"&amp;&amp;" soltos significam E e o espaço passa a fazer parte do texto.
 * Um erro de sintaxe faz o resto da query ser ignorado (e marcado em vermelho).
 */
public final class ExtPlusParser {
    public static final String PREFIXES = "@$#&%~=";

    public enum SpanKind { TEXT, PREFIX, REGEX, QUOTED, OPERATOR, NEGATE, RECIPE_OP, ERROR }

    /** Trecho [start, end) para o realce. prefix vale para PREFIX e para o texto que segue um prefixo. */
    public record Span(int start, int end, SpanKind kind, char prefix) {}

    public enum Part { ANY, INPUTS, OUTPUTS, OTHERS, CATEGORY, SPECIAL }

    /** query para ANY/INPUTS/OUTPUTS/OTHERS; text (normalizado) para CATEGORY/SPECIAL. */
    public record Clause(Part part, boolean all, Query query, String text, boolean negated) {
        public Clause(Part part, boolean all, Query query) { this(part, all, query, null, false); }
    }

    public static final java.util.Set<String> SPECIALS = java.util.Set.of("inv", "inventario", "inventory", "scc", "terminal", "rede");

    public static final class Result {
        public Query query;
        public final List<Clause> clauses = new ArrayList<>();
        public final List<Span> spans = new ArrayList<>();
        /** Posição a partir da qual a query foi ignorada, ou -1. */
        public int errorAt = -1;
    }

    private final String s;
    private final boolean recipe;
    /** Opção explicitAnd: "&" é o E e o espaço é texto. */
    private final boolean explicit = com.xorume.emisearchplus.Config.get().explicitAnd;
    private final Result res = new Result();
    private int pos;

    /** Só calcula os trechos para o realce (não monta as queries, que fazem buscas no índice). */
    private boolean spansOnly;

    private ExtPlusParser(String s, boolean recipe) {
        this.s = s;
        this.recipe = recipe;
    }

    /** Query de itens (barra principal). query == null quando vazia. */
    /** Só os trechos do realce de sintaxe (barato: não monta queries). */
    public static Result spans(String text, boolean recipe) {
        ExtPlusParser p = new ExtPlusParser(text, recipe);
        p.spansOnly = true;
        try {
            p.skipWs();
            if (recipe) p.recipeLoop();
            else {
                p.parseOr();
                p.skipWs();
                if (!p.eof()) p.fail();
            }
        } catch (Bail ignored) {
        }
        return p.res;
    }

    public static Result parseItems(String text) {
        ExtPlusParser p = new ExtPlusParser(text, false);
        try {
            p.skipWs();
            Query q = p.parseOr();
            p.skipWs();
            if (!p.eof()) p.fail();
            p.res.query = q;
        } catch (Bail b) {
            // "a primeira parte que faz sentido": reaproveita o trecho antes do erro
            if (p.res.errorAt > 0 && p.res.errorAt < text.length()) p.res.query = parseItems(text.substring(0, p.res.errorAt)).query;
        }
        return p.res;
    }

    /** Query de receitas com cláusulas &lt; &gt; ^. */
    public static Result parseRecipes(String text) {
        ExtPlusParser p = new ExtPlusParser(text, true);
        try {
            p.skipWs();
            p.recipeLoop();
        } catch (Bail b) {
            p.res.clauses.clear();
            if (p.res.errorAt > 0 && p.res.errorAt < text.length()) p.res.clauses.addAll(parseRecipes(text.substring(0, p.res.errorAt)).clauses);
        }
        return p.res;
    }

    private void recipeLoop() {
        while (!eof()) {
            Part part = Part.ANY;
            boolean all = false;
            char c = peek();
            if (recipeWordOp()) {
                parseWordClause();
                skipWs();
                continue;
            }
            if (c == '<' || c == '>' || c == '^') {
                int st = pos;
                pos++;
                part = c == '<' ? Part.INPUTS : c == '>' ? Part.OUTPUTS : Part.OTHERS;
                if (!eof() && peek() == '&') {
                    all = true;
                    pos++;
                }
                span(st, pos, SpanKind.RECIPE_OP, (char) 0);
            }
            skipWs();
            Query q = parseOr();
            res.clauses.add(new Clause(part, all, q));
            skipWs();
            if (!eof() && !atRecipeOp()) fail();
        }
    }

    /** Cria a folha (ou um marcador barato no modo só-realce). */
    private Query leaf(char prefix, String text, Queries.Kind kind) {
        if (spansOnly) {
            if (kind == Queries.Kind.REGEX) java.util.regex.Pattern.compile(text);
            return new Queries.Const(true);
        }
        return Queries.leaf(prefix, text, kind);
    }

    private static final class Bail extends RuntimeException {
        Bail() { super(null, null, false, false); }
    }

    private void fail() {
        res.errorAt = pos;
        span(pos, s.length(), SpanKind.ERROR, (char) 0);
        throw new Bail();
    }

    private boolean eof() { return pos >= s.length(); }

    private char peek() { return s.charAt(pos); }

    private boolean atRecipeOp() {
        if (!recipe || eof()) return false;
        char c = peek();
        return c == '<' || c == '>' || c == '^' || recipeWordOp();
    }

    /** "!categoria", "?especial", "-!..." ou "-?..." na posição atual (só na busca de receitas). */
    private boolean recipeWordOp() {
        if (!recipe || eof()) return false;
        char c = peek();
        if (c == '!' || c == '?') return true;
        return c == '-' && pos + 1 < s.length() && (s.charAt(pos + 1) == '!' || s.charAt(pos + 1) == '?');
    }

    private void parseWordClause() {
        boolean neg = false;
        if (peek() == '-') {
            span(pos, pos + 1, SpanKind.NEGATE, (char) 0);
            pos++;
            neg = true;
        }
        char op = peek();
        int st = pos;
        pos++;
        span(st, pos, SpanKind.RECIPE_OP, (char) 0);
        int ts = pos;
        StringBuilder t = new StringBuilder();
        if (!eof() && peek() == '"') {
            pos++;
            while (!eof() && peek() != '"') {
                if (peek() == '\\' && pos + 1 < s.length()) { t.append(s.charAt(pos + 1)); pos += 2; }
                else { t.append(peek()); pos++; }
            }
            if (!eof()) pos++;
        } else {
            while (!eof() && !Character.isWhitespace(peek()) && peek() != '|' && andAt(pos) == 0) {
                char ch = peek();
                if (ch == '<' || ch == '>' || ch == '^' || ch == '!' || ch == '?') break;
                if (ch == '\\' && pos + 1 < s.length()) { t.append(s.charAt(pos + 1)); pos += 2; continue; }
                t.append(ch);
                pos++;
            }
        }
        String text = com.xorume.emisearchplus.TextNorm.norm(t.toString().strip());
        if (op == '?') {
            if (text.isEmpty()) return; // ainda digitando
            if (!SPECIALS.contains(text)) {
                pos = ts;
                fail();
            }
            span(ts, pos, SpanKind.TEXT, '?');
            res.clauses.add(new Clause(Part.SPECIAL, false, null, text, neg));
        } else {
            span(ts, pos, SpanKind.TEXT, '!');
            if (!text.isEmpty()) res.clauses.add(new Clause(Part.CATEGORY, false, null, text, neg));
        }
        // no modo explícito pode haver um "&" separando da próxima cláusula
        skipWs();
        int and = andAt(pos);
        if (and > 0) {
            span(pos, pos + and, SpanKind.OPERATOR, (char) 0);
            pos += and;
        }
    }

    /** Comparação numérica no fim de um termo $: ">=10", "<5", "=3.5k". Devolve o tamanho (0 = não tem). */
    private int numericTail(int i) {
        java.util.regex.Matcher m = NUMERIC_TAIL.matcher(s);
        m.region(i, s.length());
        return m.lookingAt() ? m.end() - i : 0;
    }

    private static final java.util.regex.Pattern NUMERIC_TAIL =
            java.util.regex.Pattern.compile("(>=|<=|!=|>|<|=)\\s*-?\\d+(?:[.,]\\d+)?[kKmMgG]?(?=\\s|$|[|}&])");

    private void skipWs() {
        while (!eof() && Character.isWhitespace(peek())) pos++;
    }

    private void span(int a, int b, SpanKind k, char prefix) {
        if (b > a) res.spans.add(new Span(a, b, k, prefix));
    }

    /** orExpr; devolve null quando vazio. */
    private Query parseOr() {
        List<Query> alts = new ArrayList<>();
        Query first = parseSeq();
        if (first != null) alts.add(first);
        while (!eof() && peek() == '|') {
            span(pos, pos + 1, SpanKind.OPERATOR, (char) 0);
            pos++;
            skipWs();
            Query q = parseSeq();
            if (q != null) alts.add(q);
        }
        if (alts.isEmpty()) return null;
        return Queries.or(alts);
    }

    private boolean atSeqEnd() {
        if (eof()) return true;
        char c = peek();
        return c == '|' || c == '}' || atRecipeOp();
    }

    private Query parseSeq() {
        List<Query> parts = new ArrayList<>();
        skipWs();
        if (explicit) {
            // E explícito: só "&" combina termos; o espaço faz parte do texto
            boolean needOperand = true;
            while (!atSeqEnd()) {
                int and = andAt(pos);
                if (and > 0) {
                    span(pos, pos + and, SpanKind.OPERATOR, (char) 0);
                    pos += and;
                    skipWs();
                    needOperand = true;
                    continue;
                }
                if (!needOperand) fail(); // dois termos sem "&" entre eles
                Query q = parseUnary();
                if (q != null) parts.add(q);
                needOperand = false;
                skipWs();
            }
        } else {
            while (!atSeqEnd()) {
                int and = andAt(pos);
                if (and > 0) { // "&" solto também vale como E quando o espaço já é E
                    span(pos, pos + and, SpanKind.OPERATOR, (char) 0);
                    pos += and;
                    skipWs();
                    continue;
                }
                Query q = parseUnary();
                if (q != null) parts.add(q);
                skipWs();
            }
        }
        if (parts.isEmpty()) return null;
        return Queries.and(parts);
    }

    /**
     * "&" ou "&&" soltos na posição i (seguidos de espaço, fim, '{' ou '-'): operador E
     * (no modo explícito é o único E; fora dele é opcional, igual ao espaço).
     * "&texto" continua sendo o prefixo de id. Devolve quantos caracteres o operador ocupa (0 = não é).
     */
    private int andAt(int i) {
        if (i >= s.length() || s.charAt(i) != '&') return 0;
        int n = i + 1 < s.length() && s.charAt(i + 1) == '&' ? 2 : 1;
        if (i + n >= s.length()) return n;
        char after = s.charAt(i + n);
        return Character.isWhitespace(after) || after == '{' || after == '-' ? n : 0;
    }

    private Query parseUnary() {
        char c = peek();
        if (c == '{') {
            span(pos, pos + 1, SpanKind.OPERATOR, (char) 0);
            pos++;
            Query inner = parseOr();
            if (!eof() && peek() == '}') {
                span(pos, pos + 1, SpanKind.OPERATOR, (char) 0);
                pos++;
            } else if (!eof()) {
                fail();
            }
            return inner;
        }
        if (c == '-') {
            span(pos, pos + 1, SpanKind.NEGATE, (char) 0);
            pos++;
            if (eof() || Character.isWhitespace(peek())) return null;
            Query inner = parseUnary();
            return inner == null ? null : new Queries.Not(inner);
        }
        char prefix = 0;
        if (PREFIXES.indexOf(c) >= 0) {
            span(pos, pos + 1, SpanKind.PREFIX, c);
            prefix = c;
            pos++;
            if (eof() || Character.isWhitespace(peek()) || peek() == '|' || peek() == '}' || andAt(pos) > 0) return null;
        }
        return token(prefix);
    }

    private Query token(char prefix) {
        int st = pos;
        char c = peek();
        if (c == '/' || (c == 'r' && pos + 1 < s.length() && s.charAt(pos + 1) == '/')) {
            if (c == 'r') pos++;
            pos++;
            StringBuilder re = new StringBuilder();
            while (!eof() && peek() != '/') {
                if (peek() == '\\' && pos + 1 < s.length()) {
                    char n = s.charAt(pos + 1);
                    if (n == '/') re.append('/');
                    else re.append('\\').append(n);
                    pos += 2;
                } else {
                    re.append(peek());
                    pos++;
                }
            }
            if (!eof()) pos++;
            span(st, pos, SpanKind.REGEX, prefix);
            if (re.length() == 0) return null;
            try {
                return leaf(prefix, re.toString(), Queries.Kind.REGEX);
            } catch (Exception e) {
                res.spans.add(new Span(st, pos, SpanKind.ERROR, prefix));
                return new Queries.Const(false);
            }
        }
        if (c == '"') {
            pos++;
            StringBuilder q = new StringBuilder();
            while (!eof() && peek() != '"') {
                if (peek() == '\\' && pos + 1 < s.length()) {
                    q.append(s.charAt(pos + 1));
                    pos += 2;
                } else {
                    q.append(peek());
                    pos++;
                }
            }
            if (!eof()) pos++;
            span(st, pos, SpanKind.QUOTED, prefix);
            if (prefix == '$' && q.length() > 0) {
                int n = numericTail(pos);
                if (n > 0) {
                    span(pos, pos + n, SpanKind.OPERATOR, (char) 0);
                    q.append(s, pos, pos + n);
                    pos += n;
                    return leaf(prefix, q.toString(), Queries.Kind.PLAIN);
                }
            }
            return q.length() == 0 ? null : leaf(prefix, q.toString(), Queries.Kind.QUOTED);
        }
        StringBuilder t = new StringBuilder();
        int end = pos; // fim sem os espaços finais
        while (!eof()) {
            char ch = peek();
            if (ch == '|' || ch == '{' || ch == '}' || ch == '"') break;
            if (Character.isWhitespace(ch)) {
                if (!explicit) break;
                t.append(ch);
                pos++;
                continue;
            }
            if (andAt(pos) > 0) break;
            if (prefix == '$' && t.length() > 0 && (ch == '<' || ch == '>' || ch == '=' || ch == '!')) {
                int n = numericTail(pos);
                if (n > 0) {
                    t.append(s, pos, pos + n);
                    pos += n;
                    end = pos;
                    break;
                }
            }
            if (recipe && t.length() == 0 && (ch == '!' || ch == '?')) break;
            // modo explícito: depois de um espaço, "-", "/" ou um prefixo começam outro termo (que exige "&" antes)
            if (explicit && t.length() > 0 && Character.isWhitespace(s.charAt(pos - 1))
                    && (ch == '-' || ch == '/' || PREFIXES.indexOf(ch) >= 0 || (recipe && (ch == '!' || ch == '?'))
                    || (ch == 'r' && pos + 1 < s.length() && s.charAt(pos + 1) == '/'))) break;
            // no início de um termo eles são operadores; no meio da palavra ("x-ray", "1/2") são texto
            if (t.length() == 0 && (ch == '-' || ch == '/')) break;
            if (recipe && (ch == '<' || ch == '>' || ch == '^')) break;
            if (ch == '\\' && pos + 1 < s.length()) {
                t.append(s.charAt(pos + 1));
                pos += 2;
                end = pos;
                continue;
            }
            t.append(ch);
            pos++;
            end = pos;
        }
        String text = t.toString().strip();
        if (text.isEmpty()) fail();
        span(st, end, SpanKind.TEXT, prefix);
        try {
            return leaf(prefix, text, Queries.Kind.PLAIN);
        } catch (Exception e) {
            return new Queries.Const(false);
        }
    }
}
