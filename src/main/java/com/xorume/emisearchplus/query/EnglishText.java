package com.xorume.emisearchplus.query;

import com.xorume.emisearchplus.Config;
import com.xorume.emisearchplus.EmiSearchPlus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolve um Component com as traduções en_us, sem trocar o idioma global do jogo. */
public final class EnglishText {
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%])");

    private EnglishText() {}

    /** Tabela en_us, ou null se desligado ou se o jogo já está em inglês. */
    public static Language language() {
        if (!Config.get().englishNames) return null;
        try {
            Minecraft mc = Minecraft.getInstance();
            String sel = mc.getLanguageManager().getSelected();
            if (sel == null || sel.equalsIgnoreCase("en_us")) return null;
            return ClientLanguage.loadFrom(mc.getResourceManager(), List.of("en_us"), false);
        } catch (Throwable t) {
            EmiSearchPlus.LOG.warn("EMI Search Plus: não deu para carregar en_us", t);
            return null;
        }
    }

    public static String of(Component c, Language lang) {
        StringBuilder sb = new StringBuilder();
        append(c, lang, sb, 0);
        return sb.toString();
    }

    private static void append(Component c, Language lang, StringBuilder sb, int depth) {
        if (c == null || depth > 8) return;
        ComponentContents cc = c.getContents();
        if (cc instanceof TranslatableContents tc) {
            String fb = tc.getFallback() != null ? tc.getFallback() : tc.getKey();
            format(lang.getOrDefault(tc.getKey(), fb), tc.getArgs(), lang, sb, depth);
        } else if (cc instanceof PlainTextContents p) {
            sb.append(p.text());
        } else {
            sb.append(MutableComponent.create(cc).getString());
        }
        for (Component sib : c.getSiblings()) append(sib, lang, sb, depth + 1);
    }

    private static void format(String fmt, Object[] args, Language lang, StringBuilder sb, int depth) {
        Matcher m = FORMAT.matcher(fmt);
        int last = 0, implicit = 0;
        while (m.find()) {
            sb.append(fmt, last, m.start());
            last = m.end();
            if ("%".equals(m.group(2))) {
                sb.append('%');
                continue;
            }
            int idx = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : implicit++;
            if (args != null && idx >= 0 && idx < args.length) {
                Object a = args[idx];
                if (a instanceof Component ac) append(ac, lang, sb, depth + 1);
                else sb.append(a);
            }
        }
        sb.append(fmt, last, fmt.length());
    }
}
