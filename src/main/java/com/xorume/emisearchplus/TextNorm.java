package com.xorume.emisearchplus;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Minúsculas + remoção de acentos (se ligada na config). */
public final class TextNorm {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private TextNorm() {}

    public static String norm(String s) {
        if (s == null) return "";
        String l = s.toLowerCase(Locale.ROOT);
        if (!Config.get().accentInsensitive) return l;
        for (int i = 0; i < l.length(); i++) {
            if (l.charAt(i) > 127) {
                return MARKS.matcher(Normalizer.normalize(l, Normalizer.Form.NFD)).replaceAll("");
            }
        }
        return l;
    }
}
