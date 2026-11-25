package com.xorume.emisearchplus.query;

import com.xorume.emisearchplus.Config;
import com.xorume.emisearchplus.TextNorm;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.search.AliasQuery;
import dev.emi.emi.search.ModQuery;
import dev.emi.emi.search.NameQuery;
import dev.emi.emi.search.Query;
import dev.emi.emi.search.RegexModQuery;
import dev.emi.emi.search.RegexNameQuery;
import dev.emi.emi.search.RegexTagQuery;
import dev.emi.emi.search.RegexTooltipQuery;
import dev.emi.emi.search.TagQuery;
import dev.emi.emi.search.TooltipQuery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Queries próprias + fábrica das folhas usadas pelo parser Extended+ e pelo modo EMI. */
public final class Queries {
    /** Ligado durante a segunda passada (fuzzy "fallback"). */
    public static final ThreadLocal<Boolean> FUZZY_PASS = ThreadLocal.withInitial(() -> false);
    /** Tipo do último token lido pelo parser original do EMI (modo "emi"). */
    public static final ThreadLocal<dev.emi.emi.search.QueryType> EMI_TYPE = new ThreadLocal<>();

    private Queries() {}

    // ---------------------------------------------------------------- lógicas

    /**
     * Avalia uma query sem deixar exceção escapar. Algumas queries do EMI quebram com itens de outros mods
     * (ex.: RegexTooltipQuery com tooltip nulo) e uma exceção derrubava a busca inteira.
     * Em caso de erro a query conta como "não bateu" (antes da negação).
     */
    public static boolean safe(Query q, EmiStack s, boolean baked) {
        try {
            return baked ? q.matches(s) : q.matchesUnbaked(s);
        } catch (Throwable t) {
            return false;
        }
    }

    public static final class And extends Query {
        private final List<Query> qs;
        public And(List<Query> qs) { this.qs = qs; }
        @Override public boolean matches(EmiStack s) { for (Query q : qs) if (safe(q, s, true) == q.negated) return false; return true; }
        @Override public boolean matchesUnbaked(EmiStack s) { for (Query q : qs) if (safe(q, s, false) == q.negated) return false; return true; }
    }

    public static final class Or extends Query {
        private final List<Query> qs;
        public Or(List<Query> qs) { this.qs = qs; }
        @Override public boolean matches(EmiStack s) { for (Query q : qs) if (safe(q, s, true) != q.negated) return true; return false; }
        @Override public boolean matchesUnbaked(EmiStack s) { for (Query q : qs) if (safe(q, s, false) != q.negated) return true; return false; }
    }

    public static final class Not extends Query {
        private final Query q;
        public Not(Query q) { this.q = q; }
        @Override public boolean matches(EmiStack s) { return safe(q, s, true) == q.negated; }
        @Override public boolean matchesUnbaked(EmiStack s) { return safe(q, s, false) == q.negated; }
    }

    /** Envelope de proteção para a query de topo (CompiledQuery.test chama direto, sem And/Or). */
    public static final class Safe extends Query {
        private final Query q;
        public Safe(Query q) { this.q = q; }
        @Override public boolean matches(EmiStack s) { return safe(q, s, true) != q.negated; }
        @Override public boolean matchesUnbaked(EmiStack s) { return safe(q, s, false) != q.negated; }
    }

    public static final class Const extends Query {
        private final boolean v;
        public Const(boolean v) { this.v = v; }
        @Override public boolean matches(EmiStack s) { return v; }
    }

    /** Conjunto pré-calculado para stacks indexados; predicado para os demais. */
    public static final class SetQuery extends Query {
        private final Set<EmiStack> valid;
        private final Predicate<EmiStack> unbaked;
        public SetQuery(Set<EmiStack> valid, Predicate<EmiStack> unbaked) { this.valid = valid; this.unbaked = unbaked; }
        @Override public boolean matches(EmiStack s) { return valid.contains(s); }
        @Override public boolean matchesUnbaked(EmiStack s) { return unbaked.test(s); }
    }

    /** Usa a query do EMI para stacks indexados e um predicado normalizado para os demais. */
    public static final class Wrap extends Query {
        private final Query inner;
        private final Predicate<EmiStack> unbaked;
        public Wrap(Query inner, Predicate<EmiStack> unbaked) { this.inner = inner; this.unbaked = unbaked; }
        @Override public boolean matches(EmiStack s) { return inner.matches(s); }
        @Override public boolean matchesUnbaked(EmiStack s) { return unbaked.test(s); }
    }

    /** Predicado simples avaliado sempre na hora (id, aba, tipo). */
    public static final class Pred extends Query {
        private final Predicate<EmiStack> p;
        public Pred(Predicate<EmiStack> p) { this.p = p; }
        @Override public boolean matches(EmiStack s) { try { return p.test(s); } catch (Throwable t) { return false; } }
    }

    public static Query and(List<Query> qs) { return qs.size() == 1 && !qs.get(0).negated ? qs.get(0) : new And(qs); }
    public static Query or(List<Query> qs) { return qs.size() == 1 && !qs.get(0).negated ? qs.get(0) : new Or(qs); }

    // ---------------------------------------------------------------- folhas

    public enum Kind { PLAIN, QUOTED, REGEX }

    /**
     * Cria a query de uma folha. prefix: 0 (padrão), '@' mod, '$' tooltip, '#' tag, '&' id, '%' aba criativa, '~' tipo.
     * Lança {@link java.util.regex.PatternSyntaxException} para regex inválida.
     */
    public static Query leaf(char prefix, String text, Kind kind) {
        if (prefix == '$' && kind != Kind.REGEX) {
            Query num = NumericTooltipQuery.tryCreate(text);
            if (num != null) return num;
        }
        // "=palavra": palavra inteira no nome (também em inglês, sem acentos); aceita curingas
        if (prefix == '=') {
            if (kind == Kind.REGEX) return new RegexNameQuery(text);
            boolean wild = kind == Kind.PLAIN && Config.get().wildcards;
            return namePattern(globPattern(TextNorm.norm(text), true, wild));
        }
        // curinga: "tar*" (palavra começa com), "*tar" (termina com), "iron*ingot" / "*tar*" (qualquer lugar)
        boolean wildcard = kind == Kind.PLAIN && Config.get().wildcards && text.indexOf('*') >= 0 && text.replace("*", "").length() > 0;
        if (wildcard && prefix == 0) {
            return namePattern(globPattern(TextNorm.norm(text), false, true));
        }
        if (wildcard) {
            text = "(?i)" + globPattern(text, false, true).pattern();
            kind = Kind.REGEX;
        }
        boolean regex = kind == Kind.REGEX;
        if (regex) Pattern.compile(text); // valida
        return switch (prefix) {
            case '@' -> regex ? new RegexModQuery(text) : mod(TextNorm.norm(text));
            case '$' -> regex ? new RegexTooltipQuery(text) : new TooltipQuery(TextNorm.norm(text));
            case '#' -> regex ? new RegexTagQuery(text) : new TagQuery(text.toLowerCase(Locale.ROOT));
            case '&' -> id(text, regex);
            case '%' -> tab(text, regex);
            case '~' -> type(text, regex);
            default -> regex ? defaultRegex(text) : defaultPlain(TextNorm.norm(text), kind == Kind.PLAIN);
        };
    }

    private static final String NOT_WORD_BEFORE = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_WORD_AFTER = "(?![\\p{L}\\p{N}])";

    /**
     * Padrão para curingas e palavra inteira. wholeWord: limites de palavra nos dois lados (exceto onde houver "*").
     * Sem wholeWord: "x*" = alguma palavra começa com x; "*x" = alguma palavra termina com x; "*x*"/"a*b" = em qualquer lugar.
     */
    public static Pattern globPattern(String text, boolean wholeWord, boolean wildcards) {
        boolean lead = wildcards && text.startsWith("*"), trail = wildcards && text.endsWith("*");
        boolean left = wholeWord ? !lead : trail && !lead;
        boolean right = wholeWord ? !trail : lead && !trail;
        StringBuilder re = new StringBuilder();
        if (left) re.append(NOT_WORD_BEFORE);
        String[] parts = wildcards ? text.split("\\*", -1) : new String[]{text};
        boolean first = true;
        for (String part : parts) {
            if (!first) re.append(".*");
            first = false;
            if (!part.isEmpty()) re.append(Pattern.quote(part));
        }
        if (right) re.append(NOT_WORD_AFTER);
        return Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** Nome (normalizado, e o nome em inglês) casa com o padrão. */
    public static Query namePattern(Pattern p) {
        return new SetQuery(SearchIndex.byNamePattern(p), s -> {
            try {
                if (p.matcher(TextNorm.norm(NameQuery.getText(s).getString())).find()) return true;
                String e = SearchIndex.englishName(s);
                return e != null && p.matcher(e).find();
            } catch (Throwable t) {
                return false;
            }
        });
    }

    public static Query name(String n) {
        return new Wrap(new NameQuery(n), s -> TextNorm.norm(NameQuery.getText(s).getString()).contains(n));
    }

    public static Query mod(String n) {
        return new Wrap(new ModQuery(n), s -> {
            ResourceLocation id = s.getId();
            return id != null && (TextNorm.norm(EmiUtil.getModName(id.getNamespace())).contains(n) || id.getNamespace().contains(n));
        });
    }

    /** Termo sem prefixo: nome + apelidos (+ tooltip/mod/tag conforme a config do EMI) + iniciais/fuzzy. */
    public static Query defaultPlain(String n, boolean allowExtras) {
        List<Query> qs = new ArrayList<>();
        qs.add(name(n));
        qs.add(new AliasQuery(n));
        if (EmiConfig.searchTooltipByDefault) qs.add(new TooltipQuery(n));
        if (EmiConfig.searchModNameByDefault) qs.add(mod(n));
        if (EmiConfig.searchTagsByDefault) qs.add(new TagQuery(n));
        addEnglish(n, qs);
        if (allowExtras) addExtras(n, qs);
        return or(qs);
    }

    /** Nome em inglês (índice extra), quando o jogo não está em inglês. */
    public static void addEnglish(String n, List<Query> qs) {
        if (!Config.get().englishNames || n.isEmpty()) return;
        Set<EmiStack> en = SearchIndex.byEnglish(n);
        if (en != null) qs.add(new SetQuery(en, s -> {
            String e = SearchIndex.englishName(s);
            return e != null && e.contains(n);
        }));
    }

    /** Iniciais e fuzzy, quando cabem para o termo. */
    public static void addExtras(String n, List<Query> qs) {
        Config c = Config.get();
        boolean letters = !n.isEmpty() && n.chars().allMatch(Character::isLetter);
        if (c.initials && letters && n.length() >= 2 && n.length() <= 4) {
            qs.add(new SetQuery(SearchIndex.byInitials(n), s -> false));
        }
        boolean fuzzy = "always".equalsIgnoreCase(c.fuzzy) || ("fallback".equalsIgnoreCase(c.fuzzy) && FUZZY_PASS.get());
        if (fuzzy && letters && n.length() >= 4) {
            qs.add(new SetQuery(SearchIndex.fuzzy(n), s -> false));
        }
    }

    private static Query defaultRegex(String r) {
        List<Query> qs = new ArrayList<>();
        qs.add(new RegexNameQuery(r));
        if (EmiConfig.searchTooltipByDefault) qs.add(new RegexTooltipQuery(r));
        if (EmiConfig.searchModNameByDefault) qs.add(new RegexModQuery(r));
        if (EmiConfig.searchTagsByDefault) qs.add(new RegexTagQuery(r));
        return or(qs);
    }

    private static Predicate<String> textTest(String text, boolean regex) {
        if (regex) {
            Pattern p = Pattern.compile(text, Pattern.CASE_INSENSITIVE);
            return v -> p.matcher(v).find();
        }
        String n = TextNorm.norm(text);
        return v -> TextNorm.norm(v).contains(n);
    }

    public static Query id(String text, boolean regex) {
        Predicate<String> t = textTest(text, regex);
        return new Pred(s -> {
            ResourceLocation id = s.getId();
            return id != null && t.test(id.toString());
        });
    }

    public static Query tab(String text, boolean regex) {
        Predicate<String> t = textTest(text, regex);
        return new Pred(s -> {
            if (!(s.getKey() instanceof Item item)) return false;
            for (String name : SearchIndex.tabsOf(item)) if (t.test(name)) return true;
            return false;
        });
    }

    public static Query type(String text, boolean regex) {
        Predicate<String> t = textTest(text, regex);
        return new Pred(s -> t.test(typeName(s)));
    }

    public static String typeName(EmiStack s) {
        Object k = s.getKey();
        if (k instanceof Item) return "item";
        if (k instanceof Fluid) return "fluid";
        if (k == null) return "";
        String n = k.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return n.isEmpty() ? k.getClass().getName().toLowerCase(Locale.ROOT) : n;
    }

    // ---------------------------------------------------------------- modo "emi"

    /** Substitui as folhas criadas pelo parser original do EMI (prefixos novos, acentos, iniciais/fuzzy). */
    public static void emiAddQuery(String s, boolean negated, List<Query> out,
                                   Function<String, Query> normal, Function<String, Query> regex, boolean isDefault) {
        Query q;
        if (isDefault && s.length() > 1 && "&%~".indexOf(s.charAt(0)) >= 0) {
            char p = s.charAt(0);
            String body = s.substring(1);
            boolean isRegex = body.length() > 1 && body.startsWith("/") && body.endsWith("/");
            boolean quoted = body.length() > 1 && body.startsWith("\"") && body.endsWith("\"");
            if (isRegex || quoted) body = body.substring(1, body.length() - 1);
            try {
                q = leaf(p, body, isRegex ? Kind.REGEX : Kind.PLAIN);
            } catch (Exception e) {
                q = new Const(false);
            }
        } else if (s.length() > 1 && s.startsWith("/") && s.endsWith("/")) {
            q = regex.apply(s.substring(1, s.length() - 1));
        } else {
            boolean quoted = s.length() > 1 && s.startsWith("\"") && s.endsWith("\"");
            String body = TextNorm.norm(quoted ? s.substring(1, s.length() - 1) : s);
            q = normal.apply(body);
            if (isDefault) {
                List<Query> qs = new ArrayList<>();
                qs.add(q);
                addEnglish(body, qs);
                if (!quoted) addExtras(body, qs);
                if (qs.size() > 1) q = new Or(qs);
            }
        }
        q = new Safe(q);
        q.negated = negated;
        out.add(q);
    }
}
