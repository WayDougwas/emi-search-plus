package com.xorume.emisearchplus.ui;

import com.xorume.emisearchplus.Config;
import com.xorume.emisearchplus.TextNorm;
import com.xorume.emisearchplus.query.SearchIndex;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Sugestões para o termo sob o cursor: @mod, #tag, &id, %aba criativa; com o campo vazio, o histórico. */
public final class Autocomplete {
    /** display: texto mostrado; insert: o que entra no lugar de [start, end). */
    /** history: a entrada de histórico/fixada que esta sugestão representa (para fixar com clique direito), ou null. */
    public record Suggestion(String display, String hint, String insert, int start, int end, String history) {
        public Suggestion(String display, String hint, String insert, int start, int end) {
            this(display, hint, insert, start, end, null);
        }
    }

    private record Cand(String value, String hint, String key) {}

    private static List<Cand> mods, tags, ids, tabs;

    private Autocomplete() {}

    private static boolean boundary(char c) {
        return Character.isWhitespace(c) || c == '|' || c == '{' || c == '}';
    }

    public static List<Suggestion> compute(String text, int cursor, List<String> history, List<String> pins) {
        int max = Config.get().maxSuggestions;
        List<Suggestion> out = new ArrayList<>();
        if (text.isBlank()) {
            Set<String> seen = new LinkedHashSet<>();
            if (pins != null) {
                for (String h : pins) {
                    if (h == null || h.isBlank() || !seen.add(h)) continue;
                    out.add(new Suggestion("★ " + h, "", h, 0, text.length(), h));
                }
            }
            if (history != null) {
                for (String h : history) {
                    if (out.size() >= max + (pins == null ? 0 : pins.size())) break;
                    if (h == null || h.isBlank() || !seen.add(h)) continue;
                    out.add(new Suggestion(h, "", h, 0, text.length(), h));
                }
            }
            return out;
        }
        cursor = Math.max(0, Math.min(cursor, text.length()));
        int start = cursor;
        while (start > 0 && !boundary(text.charAt(start - 1))) start--;
        int end = cursor;
        while (end < text.length() && !boundary(text.charAt(end))) end++;
        String tok = text.substring(start, cursor);
        int off = 0;
        while (off < tok.length() && tok.charAt(off) == '-') off++;
        if (off < tok.length() && "<>^".indexOf(tok.charAt(off)) >= 0) {
            off++;
            if (off + 1 < tok.length() && tok.charAt(off) == '&' && "@#&%$~".indexOf(tok.charAt(off + 1)) >= 0) off++;
        }
        while (off < tok.length() && tok.charAt(off) == '-') off++;
        if (off >= tok.length()) return out;
        char p = tok.charAt(off);
        if ("@#&%".indexOf(p) < 0) return out;
        String partial = tok.substring(off + 1);
        if (partial.startsWith("\"")) partial = partial.substring(1);
        String np = TextNorm.norm(partial);
        List<Cand> pool = switch (p) {
            case '@' -> mods();
            case '#' -> tags();
            case '&' -> ids();
            default -> tabs();
        };
        List<Cand> hits = new ArrayList<>();
        for (Cand c : pool) if (c.key.contains(np)) hits.add(c);
        hits.sort(Comparator.<Cand>comparingInt(c -> rank(c, np)).thenComparingInt(c -> c.value.length()).thenComparing(c -> c.value));
        for (Cand c : hits) {
            if (out.size() >= max) break;
            String ins = c.value.indexOf(' ') >= 0 ? "\"" + c.value + "\"" : c.value;
            if (ins.equals(partial)) continue;
            out.add(new Suggestion(p + c.value, c.hint, p + ins, start + off, end));
        }
        return out;
    }

    private static int rank(Cand c, String np) {
        String v = TextNorm.norm(c.value);
        if (v.startsWith(np)) return 0;
        int colon = v.indexOf(':');
        if (colon >= 0 && v.startsWith(np, colon + 1)) return 1;
        if (c.key.startsWith(np)) return 2;
        return 3;
    }

    private static List<Cand> mods() {
        if (mods == null || mods.isEmpty()) {
            List<Cand> l = new ArrayList<>();
            try {
                for (IModInfo m : ModList.get().getMods()) {
                    int n = SearchIndex.modCount(m.getModId());
                    String hint = m.getDisplayName() + (n > 0 ? " · " + n : "");
                    l.add(new Cand(m.getModId(), hint, TextNorm.norm(m.getModId() + " " + m.getDisplayName())));
                }
            } catch (Throwable ignored) {
            }
            mods = l;
        }
        return mods;
    }

    private static List<Cand> tags() {
        if (tags == null || tags.isEmpty()) {
            java.util.Map<String, String> hints = new java.util.TreeMap<>();
            addTags(BuiltInRegistries.FLUID, hints, " fluid");
            addTags(BuiltInRegistries.BLOCK, hints, " block");
            addTags(BuiltInRegistries.ITEM, hints, "");
            List<Cand> l = new ArrayList<>(hints.size());
            for (java.util.Map.Entry<String, String> e : hints.entrySet()) l.add(new Cand(e.getKey(), e.getValue(), e.getKey()));
            tags = l;
        }
        return tags;
    }

    /** id da tag -> "87" (itens) / "12 block" / "3 fluid"; registros posteriores sobrescrevem (itens por último). */
    private static <T> void addTags(Registry<T> reg, java.util.Map<String, String> hints, String suffix) {
        try {
            reg.getTags().forEach(p -> hints.put(p.getFirst().location().toString(), p.getSecond().size() + suffix));
        } catch (Throwable ignored) {
        }
    }

    private static List<Cand> ids() {
        if (ids == null || ids.isEmpty()) {
            List<Cand> l = new ArrayList<>();
            try {
                for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) l.add(new Cand(id.toString(), "", id.toString()));
                for (ResourceLocation id : BuiltInRegistries.FLUID.keySet()) l.add(new Cand(id.toString(), "fluid", id.toString()));
            } catch (Throwable ignored) {
            }
            ids = l;
        }
        return ids;
    }

    private static List<Cand> tabs() {
        if (tabs == null || tabs.isEmpty()) {
            List<Cand> l = new ArrayList<>();
            try {
                for (net.minecraft.world.item.CreativeModeTab tab : net.minecraft.world.item.CreativeModeTabs.allTabs()) {
                    if (tab.getType() != net.minecraft.world.item.CreativeModeTab.Type.CATEGORY) continue;
                    String n = tab.getDisplayName().getString();
                    int c = tab.getDisplayItems().size();
                    l.add(new Cand(n, c > 0 ? String.valueOf(c) : "", TextNorm.norm(n)));
                }
            } catch (Throwable ignored) {
            }
            tabs = l;
        }
        return tabs;
    }

    /** Tags/abas mudam ao entrar num mundo. */
    public static void invalidate() {
        tags = null;
        tabs = null;
        mods = null;
    }
}
