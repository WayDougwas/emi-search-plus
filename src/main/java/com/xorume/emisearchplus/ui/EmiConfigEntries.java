package com.xorume.emisearchplus.ui;

import com.xorume.emisearchplus.Config;
import com.xorume.emisearchplus.EmiSearchPlus;
import dev.emi.emi.config.ConfigEnum;
import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.widget.config.BooleanWidget;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.EnumWidget;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.IntWidget;
import dev.emi.emi.search.EmiSearch;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Grupo "EMI Search Plus" dentro da tela de configuração do EMI, com os widgets do próprio EMI. */
public final class EmiConfigEntries {
    public static final String GROUP_ID = "emisearchplus";
    /** Abrir a tela já rolada até o grupo (botão "Config" da lista de mods). */
    public static boolean jumpOnInit;

    private EmiConfigEntries() {}

    public enum SearchMode implements ConfigEnum {
        EXTENDED_PLUS("extended_plus"), EMI("emi");

        final String id;

        SearchMode(String id) { this.id = id; }

        @Override public String getName() { return id; }

        @Override public Component getText() { return Component.translatable("emisearchplus.config.searchMode." + id); }

        static SearchMode of(String s) { return "emi".equalsIgnoreCase(s) ? EMI : EXTENDED_PLUS; }
    }

    public enum Fuzzy implements ConfigEnum {
        FALLBACK, ALWAYS, OFF;

        @Override public String getName() { return name().toLowerCase(Locale.ROOT); }

        @Override public Component getText() { return Component.translatable("emisearchplus.config.fuzzy." + getName()); }

        static Fuzzy of(String s) {
            for (Fuzzy f : values()) if (f.getName().equalsIgnoreCase(s)) return f;
            return FALLBACK;
        }
    }

    private static List<ClientTooltipComponent> tooltip(String key) {
        List<ClientTooltipComponent> out = new ArrayList<>();
        for (String line : Component.translatable(key).getString().split("\n")) {
            out.add(ClientTooltipComponent.create(Component.literal(line).withStyle(ChatFormatting.GRAY).getVisualOrderText()));
        }
        return out;
    }

    private static MutableComponent label(String key) {
        return Component.translatable("emisearchplus.config." + key);
    }

    /** Mutator do EMI: set() grava o valor e chama updateChanges() da tela. */
    private static <T> ConfigScreen.Mutator<T> mutator(ConfigScreen screen, Supplier<T> get, Consumer<T> set) {
        return screen.new Mutator<T>() {
            @Override protected T getValue() { return get.get(); }

            @Override
            protected void setValue(T v) {
                set.accept(v);
                Config.save();
            }
        };
    }

    /** Refaz a busca atual da barra do EMI para refletir a mudança. */
    private static void research() {
        try {
            if (EmiScreenManager.search != null) EmiScreenManager.search.update();
        } catch (Throwable ignored) {
        }
    }

    public static void add(ConfigScreen screen, Supplier<String> search, boolean collapsed) {
        Config c = Config.get();
        GroupNameWidget group = new GroupNameWidget(GROUP_ID, Component.translatable("emisearchplus.config.group"));
        group.collapsed = collapsed;
        screen.list.addEntry(group);
        List<ConfigEntryWidget> entries = new ArrayList<>();

        entries.add(new EnumWidget(label("searchMode"), tooltip("emisearchplus.config.searchMode.tooltip"), search,
                mutator(screen, () -> (ConfigEnum) SearchMode.of(c.searchMode), v -> {
                    c.searchMode = ((SearchMode) v).id;
                    research();
                }), v -> true));
        entries.add(bool(screen, search, "explicitAnd", () -> c.explicitAnd, v -> {
            c.explicitAnd = v;
            research();
        }));
        entries.add(bool(screen, search, "syntaxHighlight", () -> c.syntaxHighlight, v -> {
            c.syntaxHighlight = v;
            research();
        }));
        entries.add(bool(screen, search, "recipeSearch", () -> c.recipeSearch, v -> c.recipeSearch = v));
        entries.add(bool(screen, search, "accentInsensitive", () -> c.accentInsensitive, v -> {
            c.accentInsensitive = v;
            rebake();
        }));
        entries.add(bool(screen, search, "englishNames", () -> c.englishNames, v -> {
            c.englishNames = v;
            rebake();
        }));
        entries.add(bool(screen, search, "wildcards", () -> c.wildcards, v -> {
            c.wildcards = v;
            research();
        }));
        entries.add(bool(screen, search, "highlightMatches", () -> c.highlightMatches, v -> c.highlightMatches = v));
        entries.add(bool(screen, search, "showResultCount", () -> c.showResultCount, v -> c.showResultCount = v));
        entries.add(bool(screen, search, "saveHistory", () -> c.saveHistory, v -> {
            c.saveHistory = v;
            if (v) HistoryStore.save();
        }));
        entries.add(bool(screen, search, "initials", () -> c.initials, v -> {
            c.initials = v;
            research();
        }));
        entries.add(new EnumWidget(label("fuzzy"), tooltip("emisearchplus.config.fuzzy.tooltip"), search,
                mutator(screen, () -> (ConfigEnum) Fuzzy.of(c.fuzzy), v -> {
                    c.fuzzy = ((Fuzzy) v).getName();
                    research();
                }), v -> true));
        entries.add(bool(screen, search, "autocomplete", () -> c.autocomplete, v -> c.autocomplete = v));
        entries.add(new IntWidget(label("maxSuggestions"), tooltip("emisearchplus.config.maxSuggestions.tooltip"), search,
                mutator(screen, () -> c.maxSuggestions, v -> c.maxSuggestions = Math.max(1, Math.min(20, v)))));
        entries.add(bool(screen, search, "calculator", () -> c.calculator, v -> c.calculator = v));

        for (int i = 0; i < entries.size(); i++) {
            ConfigEntryWidget e = entries.get(i);
            e.endGroup = i == entries.size() - 1;
            group.children.add(e);
            e.parentGroups.add(group);
            screen.list.addEntry(e);
        }
    }

    private static BooleanWidget bool(ConfigScreen screen, Supplier<String> search, String key, Supplier<Boolean> get, Consumer<Boolean> set) {
        return new BooleanWidget(label(key), tooltip("emisearchplus.config." + key + ".tooltip"), search, mutator(screen, get, set));
    }

    /** O índice sem acento é montado no bake do EMI: refaz em segundo plano. */
    private static void rebake() {
        Thread t = new Thread(() -> {
            try {
                EmiSearch.bake();
                Minecraft.getInstance().execute(EmiConfigEntries::research);
            } catch (Throwable e) {
                EmiSearchPlus.LOG.error("EMI Search Plus: falha ao reindexar", e);
            }
        }, "EMI Search Plus rebake");
        t.setDaemon(true);
        t.start();
    }

    /** O grupo estava recolhido na tela anterior? */
    public static boolean wasCollapsed(ConfigScreen screen) {
        if (screen.list == null) return false;
        for (Object o : screen.list.children()) {
            if (o instanceof GroupNameWidget g && GROUP_ID.equals(g.id)) return g.collapsed;
        }
        return false;
    }
}
