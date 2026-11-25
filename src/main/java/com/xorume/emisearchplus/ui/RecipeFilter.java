package com.xorume.emisearchplus.ui;

import com.xorume.emisearchplus.EmiSearchPlus;
import com.xorume.emisearchplus.parse.RecipeMatcher;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Filtragem das receitas da RecipeScreen numa thread de fundo, com debounce. */
public final class RecipeFilter {
    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EMI Search Plus recipe filter");
        t.setDaemon(true);
        return t;
    });

    private RecipeFilter() {}

    public static void schedule(Runnable r) {
        EXEC.schedule(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                EmiSearchPlus.LOG.error("EMI Search Plus: erro ao filtrar receitas", t);
            }
        }, 150, TimeUnit.MILLISECONDS);
    }

    /** Mapa filtrado (sem categorias vazias), ou null se cancelado (stale.getAsBoolean() virou true). */
    public static Map<EmiRecipeCategory, List<EmiRecipe>> filter(Map<EmiRecipeCategory, List<EmiRecipe>> all, String text,
                                                                RecipeMatcher.Context ctx, BooleanSupplier stale) {
        RecipeMatcher m = new RecipeMatcher(text, ctx);
        if (m.empty) return new LinkedHashMap<>(all);
        Map<EmiRecipeCategory, List<EmiRecipe>> out = new LinkedHashMap<>();
        int n = 0;
        for (Map.Entry<EmiRecipeCategory, List<EmiRecipe>> e : all.entrySet()) {
            List<EmiRecipe> keep = new ArrayList<>();
            for (EmiRecipe r : e.getValue()) {
                if ((++n & 255) == 0 && stale.getAsBoolean()) return null;
                try {
                    if (m.test(r)) keep.add(r);
                } catch (Throwable ignored) {
                }
            }
            if (!keep.isEmpty()) out.put(e.getKey(), keep);
        }
        return out;
    }

    public static int count(Map<?, ? extends List<?>> m) {
        int n = 0;
        if (m != null) for (List<?> l : m.values()) n += l.size();
        return n;
    }
}
