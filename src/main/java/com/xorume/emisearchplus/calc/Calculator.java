package com.xorume.emisearchplus.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Calculadora da barra de pesquisa: + - * / % ^ ( ), sufixos k (mil), m (milhão), b (bilhão), st (pilha de 64)
 * e "x" como multiplicação. Ex.: "64*9+12", "3st+5", "1.5k/8".
 */
public final class Calculator {
    private static final Pattern ALLOWED = Pattern.compile("^[0-9.,+\\-*/%^()xX\\s kKmMbBsStT]+$");

    private final String s;
    private int pos;

    private Calculator(String s) {
        this.s = s;
    }

    /** Resultado, ou null se o texto não for uma expressão (um número sozinho também não conta). */
    public static Double tryEval(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty() || !ALLOWED.matcher(t).matches()) return null;
        if (!t.matches(".*\\d.*")) return null;
        if (!t.matches(".*([+*/%^()xX]|\\d\\s*-|[kKmMbB]|st|ST|St).*")) return null;
        try {
            Calculator c = new Calculator(t.replace(',', '.'));
            double v = c.expr();
            c.ws();
            if (c.pos != c.s.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return new BigDecimal(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** "= 588  (9×64 + 12)" */
    public static String describe(double v) {
        StringBuilder sb = new StringBuilder("= ").append(format(v));
        if (v == Math.rint(v) && Math.abs(v) >= 64 && Math.abs(v) < 1e15) {
            long n = (long) Math.abs(v);
            long st = n / 64, rest = n % 64;
            sb.append("  (").append(st).append("×64");
            if (rest != 0) sb.append(" + ").append(rest);
            sb.append(')');
        }
        return sb.toString();
    }

    private void ws() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
    }

    private boolean eat(char c) {
        ws();
        if (pos < s.length() && Character.toLowerCase(s.charAt(pos)) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private double expr() {
        double v = term();
        while (true) {
            if (eat('+')) v += term();
            else if (eat('-')) v -= term();
            else return v;
        }
    }

    private double term() {
        double v = factor();
        while (true) {
            if (eat('*') || eat('x')) v *= factor();
            else if (eat('/')) v /= factor();
            else if (eat('%')) v %= factor();
            else return v;
        }
    }

    private double factor() {
        double b = unary();
        if (eat('^')) return Math.pow(b, factor());
        return b;
    }

    private double unary() {
        if (eat('-')) return -unary();
        if (eat('+')) return unary();
        return primary();
    }

    private double primary() {
        ws();
        double v;
        if (eat('(')) {
            v = expr();
            if (!eat(')')) throw new IllegalStateException();
        } else {
            int st = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (st == pos) throw new IllegalStateException();
            v = Double.parseDouble(s.substring(st, pos));
        }
        ws();
        if (pos < s.length()) {
            char c = Character.toLowerCase(s.charAt(pos));
            if (c == 'k') { pos++; v *= 1e3; }
            else if (c == 'm') { pos++; v *= 1e6; }
            else if (c == 'b') { pos++; v *= 1e9; }
            else if (c == 's' && pos + 1 < s.length() && Character.toLowerCase(s.charAt(pos + 1)) == 't') { pos += 2; v *= 64; }
        }
        return v;
    }
}
