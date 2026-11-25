package com.xorume.emisearchplus.query;

import com.xorume.emisearchplus.EmiSearchPlus;
import com.xorume.emisearchplus.TextNorm;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.search.EmiSearch;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Índices extras construídos logo depois do {@code EmiSearch.bake()}. */
public final class SearchIndex {
    /** Stack equivalente (ignora quantidade) -> instância indexada pelo EMI. */
    private static volatile Map<EmiStack, EmiStack> canon = Map.of();
    /** Iniciais das palavras do nome, alinhado com {@link #initialStacks}. */
    private static volatile String[] initials = new String[0];
    private static volatile EmiStack[] initialStacks = new EmiStack[0];
    /** Palavra normalizada do nome -> stacks. */
    private static volatile Map<String, List<EmiStack>> words = Map.of();
    /** Nome em inglês normalizado (só quando difere do nome no idioma atual). */
    private static volatile net.minecraft.client.searchtree.SuffixArray<EmiStack> english;
    private static volatile Map<EmiStack, String> englishNames = Map.of();
    /** Todos os stacks indexados e seus nomes normalizados (para buscas por padrão: palavra inteira, curingas). */
    private static volatile EmiStack[] allStacks = new EmiStack[0];
    private static volatile String[] allNames = new String[0];
    /** Quantos stacks indexados cada namespace (mod) tem. */
    private static volatile Map<String, Integer> modCounts = Map.of();
    /** Item -> nomes normalizados das abas criativas (preenchido sob demanda). */
    private static volatile Map<Item, Set<String>> tabs;

    private SearchIndex() {}

    public static void rebuild() {
        try {
            Set<EmiStack> baked = EmiSearch.bakedStacks;
            if (baked == null) return;
            Map<EmiStack, EmiStack> c = new HashMap<>(baked.size() * 2);
            List<String> ini = new ArrayList<>(baked.size());
            List<EmiStack> iniS = new ArrayList<>(baked.size());
            Map<String, List<EmiStack>> w = new HashMap<>();
            Map<String, Integer> mc = new HashMap<>();
            net.minecraft.locale.Language en = EnglishText.language();
            net.minecraft.client.searchtree.SuffixArray<EmiStack> enArr = en == null ? null : new net.minecraft.client.searchtree.SuffixArray<>();
            Map<EmiStack, String> enNames = new IdentityHashMap<>();
            List<EmiStack> allS = new ArrayList<>(baked.size());
            List<String> allN = new ArrayList<>(baked.size());
            for (EmiStack s : baked) {
                c.putIfAbsent(s, s);
                try {
                    if (s.getId() != null) mc.merge(s.getId().getNamespace(), 1, Integer::sum);
                } catch (Throwable ignored) {
                }
                String name;
                try {
                    name = TextNorm.norm(s.getName().getString());
                } catch (Throwable t) {
                    continue;
                }
                allS.add(s);
                allN.add(name);
                if (en != null) {
                    try {
                        String e = TextNorm.norm(EnglishText.of(s.getName(), en));
                        if (!e.isEmpty() && !e.equals(name)) {
                            enArr.add(s, e);
                            enNames.put(s, e);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (String word : name.split("[^\\p{L}\\p{N}]+")) {
                    if (word.isEmpty()) continue;
                    sb.append(word.charAt(0));
                    if (word.length() >= 3) w.computeIfAbsent(word, k -> new ArrayList<>(2)).add(s);
                }
                if (sb.length() >= 2) {
                    ini.add(sb.toString());
                    iniS.add(s);
                }
            }
            canon = c;
            allStacks = allS.toArray(new EmiStack[0]);
            allNames = allN.toArray(new String[0]);
            initials = ini.toArray(new String[0]);
            initialStacks = iniS.toArray(new EmiStack[0]);
            words = w;
            modCounts = mc;
            if (enArr != null) enArr.generate();
            english = enArr;
            englishNames = enNames;
            tabs = null;
        } catch (Throwable t) {
            EmiSearchPlus.LOG.error("EMI Search Plus: falha ao indexar", t);
        }
    }

    /** Stacks cujo nome em inglês contém n; null se o índice em inglês não existe (jogo já em inglês). */
    public static Set<EmiStack> byEnglish(String n) {
        net.minecraft.client.searchtree.SuffixArray<EmiStack> arr = english;
        if (arr == null) return null;
        Set<EmiStack> out = Collections.newSetFromMap(new IdentityHashMap<>());
        out.addAll(arr.search(n));
        return out;
    }

    public static String englishName(EmiStack s) {
        return englishNames.get(canonical(s));
    }

    /** Stacks cujo nome normalizado (ou nome em inglês) casa com o padrão. */
    public static Set<EmiStack> byNamePattern(java.util.regex.Pattern p) {
        Set<EmiStack> out = Collections.newSetFromMap(new IdentityHashMap<>());
        EmiStack[] st = allStacks;
        String[] names = allNames;
        Map<EmiStack, String> en = englishNames;
        for (int i = 0; i < st.length; i++) {
            if (p.matcher(names[i]).find()) {
                out.add(st[i]);
            } else {
                String e = en.get(st[i]);
                if (e != null && p.matcher(e).find()) out.add(st[i]);
            }
        }
        return out;
    }

    public static int modCount(String namespace) {
        return modCounts.getOrDefault(namespace, 0);
    }

    /** A instância indexada equivalente, ou o próprio stack. */
    public static EmiStack canonical(EmiStack s) {
        EmiStack c = canon.get(s);
        return c == null ? s : c;
    }

    public static Set<EmiStack> byInitials(String prefix) {
        Set<EmiStack> out = Collections.newSetFromMap(new IdentityHashMap<>());
        String[] ini = initials;
        EmiStack[] st = initialStacks;
        for (int i = 0; i < ini.length; i++) {
            if (ini[i].startsWith(prefix)) out.add(st[i]);
        }
        return out;
    }

    public static Set<EmiStack> fuzzy(String token) {
        Set<EmiStack> out = Collections.newSetFromMap(new IdentityHashMap<>());
        int max = token.length() >= 8 ? 2 : 1;
        for (Map.Entry<String, List<EmiStack>> e : words.entrySet()) {
            String w = e.getKey();
            boolean hit = osa(w, token, max) <= max;
            // palavra maior cujo começo bate com erro, ex.: "chestplte" ~ "chestplate" (só termos de 5+ letras)
            if (!hit && token.length() >= 5 && w.length() > token.length()) {
                hit = osa(w.substring(0, token.length()), token, 1) <= 1;
            }
            if (hit) out.addAll(e.getValue());
        }
        return out;
    }

    /** Distância Damerau-Levenshtein (optimal string alignment), cortada em max+1. */
    public static int osa(String a, String b, int max) {
        int n = a.length(), m = b.length();
        if (Math.abs(n - m) > max) return max + 1;
        int[] prev2 = new int[m + 1], prev = new int[m + 1], cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, prev2[j - 2] + 1);
                }
                cur[j] = v;
                rowMin = Math.min(rowMin, v);
            }
            if (rowMin > max) return max + 1;
            int[] t = prev2; prev2 = prev; prev = cur; cur = t;
        }
        return prev[m];
    }

    public static Set<String> tabsOf(Item item) {
        Map<Item, Set<String>> t = tabs;
        if (t == null) {
            t = buildTabs();
            if (!t.isEmpty()) tabs = t;
        }
        Set<String> s = t.get(item);
        return s == null ? Set.of() : s;
    }

    public static List<String> tabNames() {
        List<String> out = new ArrayList<>();
        try {
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
                out.add(tab.getDisplayName().getString());
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Map<Item, Set<String>> buildTabs() {
        Map<Item, Set<String>> m = new HashMap<>();
        try {
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
                String name = TextNorm.norm(tab.getDisplayName().getString());
                for (ItemStack st : tab.getDisplayItems()) {
                    m.computeIfAbsent(st.getItem(), k -> new HashSet<>(2)).add(name);
                }
            }
        } catch (Throwable t) {
            EmiSearchPlus.LOG.warn("EMI Search Plus: abas criativas indisponíveis", t);
        }
        return m;
    }
}
