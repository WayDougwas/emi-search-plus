package com.xorume.emisearchplus.query;

import com.xorume.emisearchplus.TextNorm;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.search.EmiSearch;
import dev.emi.emi.search.Query;
import dev.emi.emi.search.TooltipQuery;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * $rótulo&gt;número: acha a linha do tooltip que contém o rótulo e compara o primeiro número depois dele.
 * Ex.: $"attack damage"&gt;=10, $energia&gt;1k, $durabilidade&lt;500.
 */
public final class NumericTooltipQuery extends Query {
    private static final Pattern FORM = Pattern.compile("^(.*?)\\s*(>=|<=|!=|>|<|=)\\s*(-?\\d+(?:[.,]\\d+)?)([kKmMgG]?)$");
    private static final Pattern NUMBER = Pattern.compile("-?\\d[\\d.,]*");

    private final String label;
    private final String op;
    private final double value;
    private final Set<EmiStack> valid = Collections.newSetFromMap(new IdentityHashMap<>());

    private NumericTooltipQuery(String label, String op, double value) {
        this.label = label;
        this.op = op;
        this.value = value;
        // candidatos: stacks indexados cujo tooltip contém o rótulo (conjunto do próprio EMI)
        TooltipQuery pre = new TooltipQuery(label);
        Set<EmiStack> baked = EmiSearch.bakedStacks;
        if (baked != null) {
            for (EmiStack s : baked) {
                if (pre.matches(s) && test(s)) valid.add(s);
            }
        }
    }

    /** null se o texto não for uma comparação. */
    public static Query tryCreate(String text) {
        Matcher m = FORM.matcher(text.strip());
        if (!m.matches()) return null;
        String label = TextNorm.norm(m.group(1).strip());
        if (label.isEmpty()) return null;
        double v = Double.parseDouble(m.group(3).replace(',', '.'));
        v *= switch (m.group(4).toLowerCase()) {
            case "k" -> 1e3;
            case "m" -> 1e6;
            case "g" -> 1e9;
            default -> 1;
        };
        return new NumericTooltipQuery(label, m.group(2), v);
    }

    private boolean test(EmiStack s) {
        List<Component> lines;
        try {
            lines = s.getTooltipText();
        } catch (Throwable t) {
            return false;
        }
        for (Component c : lines) {
            String line = TextNorm.norm(c.getString());
            int at = line.indexOf(label);
            if (at < 0) continue;
            Double n = firstNumber(line, at + label.length());
            if (n == null) n = firstNumber(line, 0);
            if (n != null && compare(n)) return true;
        }
        return false;
    }

    private static Double firstNumber(String line, int from) {
        Matcher m = NUMBER.matcher(line);
        if (!m.find(Math.min(from, line.length()))) return null;
        String raw = m.group();
        while (!raw.isEmpty() && (raw.endsWith(".") || raw.endsWith(","))) raw = raw.substring(0, raw.length() - 1);
        try {
            // "1,000" / "1.000" (milhar) vs "1.5" / "1,5" (decimal)
            if (raw.matches("-?\\d{1,3}([.,]\\d{3})+")) return Double.parseDouble(raw.replaceAll("[.,]", ""));
            return Double.parseDouble(raw.replace(',', '.').replaceAll("\\.(?=.*\\.)", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean compare(double n) {
        return switch (op) {
            case ">" -> n > value;
            case ">=" -> n >= value;
            case "<" -> n < value;
            case "<=" -> n <= value;
            case "!=" -> n != value;
            default -> n == value;
        };
    }

    @Override
    public boolean matches(EmiStack s) {
        return valid.contains(s);
    }

    @Override
    public boolean matchesUnbaked(EmiStack s) {
        return test(s);
    }
}
