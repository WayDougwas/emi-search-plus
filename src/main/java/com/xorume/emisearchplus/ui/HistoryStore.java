package com.xorume.emisearchplus.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xorume.emisearchplus.Config;
import com.xorume.emisearchplus.EmiSearchPlus;
import com.xorume.emisearchplus.mixin.EmiSearchWidgetAccessor;
import dev.emi.emi.screen.EmiScreenManager;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Histórico das barras (principal e de receitas) e buscas fixadas, em config/emisearchplus-history.json. */
public final class HistoryStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final List<String> MAIN_PINNED = new ArrayList<>();
    public static final List<String> RECIPE_PINNED = new ArrayList<>();
    private static List<String> mainLoaded = new ArrayList<>();
    private static boolean seeded;

    private static final class Data {
        List<String> main = new ArrayList<>();
        List<String> recipe = new ArrayList<>();
        List<String> mainPinned = new ArrayList<>();
        List<String> recipePinned = new ArrayList<>();
    }

    private HistoryStore() {}

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("emisearchplus-history.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (!Files.exists(p)) return;
            Data d = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Data.class);
            if (d == null) return;
            if (d.main != null) mainLoaded = new ArrayList<>(d.main);
            if (d.recipe != null) {
                RecipeSearchHolder.HISTORY.clear();
                RecipeSearchHolder.HISTORY.addAll(d.recipe);
            }
            if (d.mainPinned != null) MAIN_PINNED.addAll(d.mainPinned);
            if (d.recipePinned != null) RECIPE_PINNED.addAll(d.recipePinned);
        } catch (Exception e) {
            EmiSearchPlus.LOG.warn("EMI Search Plus: histórico ilegível", e);
        }
    }

    /** Coloca o histórico salvo na barra do EMI (uma vez, se ela ainda estiver vazia). */
    public static void seed(List<String> widgetHistory) {
        if (seeded || widgetHistory == null) return;
        seeded = true;
        if (Config.get().saveHistory && widgetHistory.isEmpty()) widgetHistory.addAll(mainLoaded);
    }

    public static void save() {
        if (!Config.get().saveHistory) return;
        try {
            Data d = new Data();
            List<String> main = null;
            try {
                if (EmiScreenManager.search != null) main = ((EmiSearchWidgetAccessor) (Object) EmiScreenManager.search).esp$getSearchHistory();
            } catch (Throwable ignored) {
            }
            d.main = new ArrayList<>(main != null && (seeded || mainLoaded.isEmpty()) ? main : mainLoaded);
            d.recipe = new ArrayList<>(RecipeSearchHolder.HISTORY);
            d.mainPinned = new ArrayList<>(MAIN_PINNED);
            d.recipePinned = new ArrayList<>(RECIPE_PINNED);
            Files.writeString(path(), GSON.toJson(d), StandardCharsets.UTF_8);
        } catch (Exception e) {
            EmiSearchPlus.LOG.warn("EMI Search Plus: falha ao gravar o histórico", e);
        }
    }

    public static void togglePin(List<String> pins, String q) {
        if (q == null || q.isBlank()) return;
        if (!pins.remove(q)) pins.add(0, q);
        save();
    }
}
