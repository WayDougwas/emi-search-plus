package com.xorume.emisearchplus.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.xorume.emisearchplus.Config;
import com.xorume.emisearchplus.query.Queries;
import com.xorume.emisearchplus.query.SearchIndex;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Tecla "pesquisar isto": com o mouse sobre um item, abre um menu com nome / @mod / &id / #tags / %aba / ~tipo. */
public final class HoverSearch {
    public static final KeyMapping KEY = new KeyMapping("key.emisearchplus.search_hovered",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.emisearchplus");

    private record Opt(String label, String hint, String query) {}

    private static final int ROW = 11;
    private static List<Opt> opts = List.of();
    private static int menuX, menuY, menuW, selected;

    private HoverSearch() {}

    public static boolean isOpen() {
        return !opts.isEmpty();
    }

    public static void close() {
        opts = List.of();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean typing(Screen screen) {
        if (screen.getFocused() instanceof EditBox e && e.isFocused()) return true;
        if (screen instanceof RecipeSearchHolder h && h.esp$searchBox() != null && h.esp$searchBox().isFocused()) return true;
        try {
            return EmiApi.isSearchFocused();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Tecla apertada numa tela: abre o menu se for a tecla do atalho e houver item sob o mouse. */
    public static boolean tryOpen(Screen screen, int key, int scan) {
        if (!KEY.isActiveAndMatches(InputConstants.getKey(key, scan)) || typing(screen)) return false;
        Minecraft mc = Minecraft.getInstance();
        int mx = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth());
        int my = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
        EmiIngredient ing;
        try {
            ing = EmiApi.getHoveredStack(mx, my, false).getStack();
        } catch (Throwable t) {
            return false;
        }
        if (ing == null || ing.isEmpty()) return false;
        List<Opt> l = build(ing);
        if (l.isEmpty()) return false;
        opts = l;
        selected = 0;
        menuX = mx + 8;
        menuY = my;
        return true;
    }

    private static List<Opt> build(EmiIngredient ing) {
        List<Opt> l = new ArrayList<>();
        EmiStack st = ing.getEmiStacks().get(0);
        String name = st.getName().getString();
        if (!name.isBlank()) l.add(new Opt(Component.translatable("emisearchplus.hover.name").getString() + ": " + name, "", quote(name)));
        String en = SearchIndex.englishName(st);
        if (en != null && !en.isBlank()) l.add(new Opt("EN: " + en, "", quote(en)));
        ResourceLocation id = st.getId();
        if (id != null) {
            l.add(new Opt("@" + id.getNamespace(), EmiUtil.getModName(id.getNamespace()), "@" + id.getNamespace()));
            l.add(new Opt("&" + id, "", "&" + id));
        }
        Set<String> tags = new LinkedHashSet<>();
        try {
            if (st.getKey() instanceof Item item) {
                st.getItemStack().getTags().forEach(t -> tags.add(t.location().toString()));
                for (String tab : SearchIndex.tabsOf(item)) l.add(new Opt("%" + tab, "", "%" + quote(tab)));
            } else if (st.getKey() instanceof Fluid f) {
                f.builtInRegistryHolder().tags().forEach(t -> tags.add(t.location().toString()));
            }
        } catch (Throwable ignored) {
        }
        int n = 0;
        for (String t : tags) {
            if (n++ >= 12) break;
            l.add(new Opt("#" + t, "", "#" + t));
        }
        l.add(new Opt("~" + Queries.typeName(st), "", "~" + Queries.typeName(st)));
        return l;
    }

    /** Aplica na barra de receitas (se aberta) ou na principal. Shift: acrescenta em vez de substituir. */
    private static void apply(Opt o) {
        close();
        Minecraft mc = Minecraft.getInstance();
        boolean append = Screen.hasShiftDown();
        String sep = Config.get().explicitAnd ? " & " : " ";
        if (mc.screen instanceof RecipeSearchHolder h && h.esp$searchBox() != null) {
            EditBox b = h.esp$searchBox();
            String v = append && !b.getValue().isBlank() ? b.getValue() + sep + o.query() : o.query();
            b.setValue(v);
            b.moveCursorToEnd(false);
            return;
        }
        try {
            String cur = EmiApi.getSearchText();
            EmiApi.setSearchText(append && cur != null && !cur.isBlank() ? cur + sep + o.query() : o.query());
            if (EmiScreenManager.search != null) EmiScreenManager.search.moveCursorToEnd(false);
        } catch (Throwable ignored) {
        }
    }

    public static void render(GuiGraphics g, int mx, int my) {
        if (opts.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int w = 60;
        for (Opt o : opts) w = Math.max(w, font.width(o.label()) + (o.hint().isEmpty() ? 0 : font.width(o.hint()) + 10) + 8);
        int h = opts.size() * ROW + 2 + ROW;
        int x = Math.max(0, Math.min(menuX, g.guiWidth() - w));
        int y = Math.max(0, Math.min(menuY, g.guiHeight() - h));
        menuX = x;
        menuY = y;
        menuW = w;
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);
        g.fill(x, y, x + w, y + h, 0xF0100010);
        g.renderOutline(x, y, w, h, 0xFF5028A0);
        g.drawString(font, Component.translatable("emisearchplus.hover.title"), x + 4, y + 2, 0xFFD060, false);
        for (int i = 0; i < opts.size(); i++) {
            Opt o = opts.get(i);
            int ry = y + 1 + ROW + i * ROW;
            boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + ROW;
            if (hover) selected = i;
            if (i == selected) g.fill(x + 1, ry, x + w - 1, ry + ROW, 0x806040C0);
            g.drawString(font, o.label(), x + 4, ry + 2, 0xFFFFFF, false);
            if (!o.hint().isEmpty()) g.drawString(font, o.hint(), x + w - 4 - font.width(o.hint()), ry + 2, 0x909090, false);
        }
        g.pose().popPose();
    }

    public static boolean keyPressed(int key) {
        if (opts.isEmpty()) return false;
        int n = opts.size();
        switch (key) {
            case GLFW.GLFW_KEY_DOWN -> selected = (selected + 1) % n;
            case GLFW.GLFW_KEY_UP -> selected = (selected - 1 + n) % n;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> apply(opts.get(selected));
            default -> close(); // Esc ou qualquer outra tecla fecha
        }
        return true;
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (opts.isEmpty()) return false;
        int i = (int) Math.floor((my - menuY - 1 - ROW) / ROW);
        if (button == 0 && mx >= menuX && mx < menuX + menuW && my >= menuY + 1 + ROW && i >= 0 && i < opts.size()) {
            apply(opts.get(i));
        } else {
            close();
        }
        return true;
    }
}
