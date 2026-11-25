package com.xorume.emisearchplus.ui;

import com.xorume.emisearchplus.parse.ExtPlusParser;
import dev.emi.emi.search.QueryType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Tuple;

import java.util.ArrayList;
import java.util.List;

/** Realce de sintaxe: produz a lista (fim, estilo) usada pelo EmiSearchWidget e um formatter para EditBox comum. */
public final class Highlighter {
    private static final Style WHITE = Style.EMPTY.withColor(ChatFormatting.WHITE);
    private static final Style OPERATOR = Style.EMPTY.withColor(ChatFormatting.GRAY);
    private static final Style NEGATE = Style.EMPTY.withColor(ChatFormatting.RED);
    private static final Style RECIPE_OP = Style.EMPTY.withColor(ChatFormatting.GOLD);
    private static final Style ERROR = Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)).withUnderlined(true);

    private Highlighter() {}

    private static Style prefixStyle(char p, boolean regex) {
        QueryType t = switch (p) {
            case '@' -> QueryType.MOD;
            case '$' -> QueryType.TOOLTIP;
            case '#' -> QueryType.TAG;
            case 0 -> QueryType.DEFAULT;
            default -> null;
        };
        if (t != null) return regex ? t.regexColor : t.color;
        int rgb = switch (p) {
            case '&' -> 0x55FFFF;
            case '%' -> 0xFF55FF;
            case '=' -> 0x88FF88;
            default -> 0xFFAA00;
        };
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(regex);
    }

    /** Lista ordenada (fim exclusivo, estilo) cobrindo o texto todo. */
    public static List<Tuple<Integer, Style>> styles(String text, boolean recipe) {
        ExtPlusParser.Result r = ExtPlusParser.spans(text, recipe);
        Style[] per = new Style[text.length()];
        for (ExtPlusParser.Span sp : r.spans) {
            Style st = switch (sp.kind()) {
                case TEXT, QUOTED -> prefixStyle(sp.prefix(), false);
                case PREFIX -> prefixStyle(sp.prefix(), false);
                case REGEX -> prefixStyle(sp.prefix(), true);
                case OPERATOR -> OPERATOR;
                case NEGATE -> NEGATE;
                case RECIPE_OP -> RECIPE_OP;
                case ERROR -> ERROR;
            };
            for (int i = Math.max(0, sp.start()); i < Math.min(text.length(), sp.end()); i++) {
                if (per[i] != ERROR) per[i] = st;
            }
        }
        List<Tuple<Integer, Style>> out = new ArrayList<>();
        for (int i = 0; i < per.length; i++) {
            Style st = per[i] == null ? WHITE : per[i];
            if (i + 1 == per.length || (per[i + 1] == null ? WHITE : per[i + 1]) != st) {
                out.add(new Tuple<>(i + 1, st));
            }
        }
        return out;
    }

    /** Formatter para EditBox: (trecho visível, deslocamento) -> texto colorido. */
    public static FormattedCharSequence format(List<Tuple<Integer, Style>> styles, String part, int offset) {
        List<FormattedCharSequence> seqs = new ArrayList<>();
        int start = 0; // em coordenadas do texto completo
        for (Tuple<Integer, Style> t : styles) {
            int end = t.getA();
            int a = Math.max(start, offset), b = Math.min(end, offset + part.length());
            if (b > a) seqs.add(FormattedCharSequence.forward(part.substring(a - offset, b - offset), t.getB()));
            start = end;
        }
        if (start < offset + part.length()) {
            int a = Math.max(start, offset);
            seqs.add(FormattedCharSequence.forward(part.substring(a - offset), WHITE));
        }
        return FormattedCharSequence.composite(seqs);
    }
}
