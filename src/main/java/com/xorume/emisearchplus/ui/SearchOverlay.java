package com.xorume.emisearchplus.ui;

import com.xorume.emisearchplus.Config;
import com.xorume.emisearchplus.calc.Calculator;
import com.xorume.emisearchplus.mixin.EmiSearchWidgetAccessor;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.widget.EmiSearchWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Objects;

/** Lista de sugestões e resultado da calculadora, desenhados por cima da tela, para a barra que estiver focada. */
public final class SearchOverlay {
    private static final int ROW = 11;

    private record Active(EditBox box, List<String> history, List<String> pins) {}

    private static EditBox lastBox;
    private static String lastText;
    private static int lastCursor = -1;
    private static List<Autocomplete.Suggestion> suggestions = List.of();
    private static int selected;
    private static boolean navigated;
    private static String dismissedFor;
    // área desenhada da lista (para cliques)
    private static int listX, listY, listW;

    private SearchOverlay() {}

    private static Active active() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) return null;
        if (screen instanceof RecipeSearchHolder h) {
            EditBox b = h.esp$searchBox();
            if (b != null && b.isFocused()) return new Active(b, RecipeSearchHolder.HISTORY, HistoryStore.RECIPE_PINNED);
        }
        try {
            EmiSearchWidget w = mainWidget(screen);
            if (w != null && w.isFocused()) {
                return new Active(w, ((EmiSearchWidgetAccessor) (Object) w).esp$getSearchHistory(), HistoryStore.MAIN_PINNED);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Tela onde o EMI está ativo (inventários, receitas, configuração do EMI). */
    private static boolean emiScreen(Screen screen) {
        if (screen instanceof RecipeSearchHolder || screen instanceof dev.emi.emi.screen.ConfigScreen) return true;
        try {
            dev.emi.emi.screen.EmiScreenBase base = dev.emi.emi.screen.EmiScreenBase.getCurrent();
            return base != null && !base.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** A barra principal do EMI, se ela está nesta tela. */
    private static EmiSearchWidget mainWidget(Screen screen) {
        EmiSearchWidget w = EmiScreenManager.search;
        if (w == null || !w.visible || screen == null) return null;
        try {
            dev.emi.emi.screen.EmiScreenBase base = dev.emi.emi.screen.EmiScreenBase.getCurrent();
            if (base == null || base.isEmpty()) return null;
        } catch (Throwable ignored) {
        }
        HistoryStore.seed(((EmiSearchWidgetAccessor) (Object) w).esp$getSearchHistory());
        return w;
    }

    private static void refresh(Active a) {
        String text = a.box().getValue();
        int cursor = a.box().getCursorPosition();
        if (a.box() == lastBox && text.equals(lastText) && cursor == lastCursor) return;
        lastBox = a.box();
        lastText = text;
        lastCursor = cursor;
        selected = 0;
        navigated = false;
        if (!Objects.equals(dismissedFor, text)) dismissedFor = null;
        suggestions = Config.get().autocomplete && Calculator.tryEval(text) == null
                ? Autocomplete.compute(text, cursor, a.history(), a.pins()) : List.of();
    }

    private static boolean listVisible() {
        return !suggestions.isEmpty() && dismissedFor == null;
    }

    public static void render(GuiGraphics g, int mx, int my) {
        renderCount(g);
        HoverSearch.render(g, mx, my);
        Active a = active();
        if (a == null) {
            lastBox = null;
            suggestions = List.of();
            return;
        }
        refresh(a);
        EditBox box = a.box();
        Font font = Minecraft.getInstance().font;
        int screenH = g.guiHeight();
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        if (Config.get().calculator) {
            Double v = Calculator.tryEval(box.getValue());
            if (v != null) {
                String s = Calculator.describe(v);
                int w = font.width(s) + 6;
                int x = box.getX() + box.getWidth() - w;
                int y = box.getY() - ROW - 1;
                if (y < 0) y = box.getY() + box.getHeight() + 1;
                g.fill(x, y, x + w, y + ROW, 0xE0101010);
                g.renderOutline(x, y, w, ROW, 0xFF55AA55);
                g.drawString(font, s, x + 3, y + 2, 0x7FFF7F, false);
            }
        }
        if (listVisible()) {
            int w = box.getWidth();
            for (Autocomplete.Suggestion s : suggestions) {
                w = Math.max(w, font.width(s.display()) + (s.hint().isEmpty() ? 0 : font.width(s.hint()) + 8) + 8);
            }
            int h = suggestions.size() * ROW + 2;
            int x = Math.max(0, Math.min(box.getX(), g.guiWidth() - w));
            int y = box.getY() + box.getHeight() + 1;
            if (y + h > screenH) y = box.getY() - h - 1;
            listX = x;
            listY = y;
            listW = w;
            g.fill(x, y, x + w, y + h, 0xF0100010);
            g.renderOutline(x, y, w, h, 0xFF5028A0);
            for (int i = 0; i < suggestions.size(); i++) {
                Autocomplete.Suggestion s = suggestions.get(i);
                int ry = y + 1 + i * ROW;
                boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + ROW;
                if (i == selected || hover) g.fill(x + 1, ry, x + w - 1, ry + ROW, i == selected ? 0x806040C0 : 0x40FFFFFF);
                g.drawString(font, s.display(), x + 4, ry + 2, 0xFFFFFF, false);
                if (!s.hint().isEmpty()) {
                    g.drawString(font, s.hint(), x + w - 4 - font.width(s.hint()), ry + 2, 0x909090, false);
                }
            }
        }
        g.pose().popPose();
    }

    /** "123" dentro da barra principal, à direita, quando há busca. */
    private static void renderCount(GuiGraphics g) {
        if (!Config.get().showResultCount) return;
        try {
            EmiSearchWidget w = mainWidget(Minecraft.getInstance().screen);
            if (w == null || w.getValue().isBlank() || Calculator.tryEval(w.getValue()) != null) return;
            List<?> st = dev.emi.emi.search.EmiSearch.stacks;
            if (st == null) return;
            Font font = Minecraft.getInstance().font;
            String c = String.valueOf(st.size());
            int cw = font.width(c);
            if (font.width(w.getValue()) + cw + 12 > w.getWidth()) return;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.drawString(font, c, w.getX() + w.getWidth() - 4 - cw, w.getY() + (w.getHeight() - 8) / 2, st.isEmpty() ? 0xFF5555 : 0x808080, false);
            g.pose().popPose();
        } catch (Throwable ignored) {
        }
    }

    private static void apply(Active a, int i) {
        if (i < 0 || i >= suggestions.size()) return;
        Autocomplete.Suggestion s = suggestions.get(i);
        String t = a.box().getValue();
        int st = Math.min(s.start(), t.length()), en = Math.min(Math.max(s.end(), st), t.length());
        String nt = t.substring(0, st) + s.insert() + t.substring(en);
        a.box().setValue(nt);
        a.box().setCursorPosition(st + s.insert().length());
        a.box().setHighlightPos(a.box().getCursorPosition());
        dismissedFor = nt; // não reabre logo depois de completar
    }

    /** @return true se a tecla foi consumida. */
    public static boolean keyPressed(int key, int scan, int mods) {
        if (HoverSearch.keyPressed(key)) return true;
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && !(screen instanceof HelpScreen) && emiScreen(screen)
                && HelpScreen.KEY.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(key, scan))) {
            Minecraft.getInstance().setScreen(new HelpScreen(screen));
            return true;
        }
        if (screen != null && HoverSearch.tryOpen(screen, key, scan)) return true;
        Active a = active();
        if (a == null) return false;
        refresh(a);
        boolean enter = key == 257 || key == 335;
        if (enter && Config.get().calculator) {
            Double v = Calculator.tryEval(a.box().getValue());
            if (v != null) {
                String r = Calculator.format(v);
                if (Screen.hasShiftDown()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(r);
                } else {
                    a.box().setValue(r);
                    a.box().moveCursorToEnd(false);
                }
                return true;
            }
        }
        if (!listVisible()) return false;
        int n = suggestions.size();
        switch (key) {
            case 264 -> { selected = (selected + 1) % n; navigated = true; return true; }
            case 265 -> { selected = (selected - 1 + n) % n; navigated = true; return true; }
            case 258 -> { apply(a, selected); return true; }
            case 256 -> { dismissedFor = a.box().getValue(); return true; }
            default -> {
                if (enter && navigated) {
                    apply(a, selected);
                    return true;
                }
                return false;
            }
        }
    }

    /** Botão cujo "soltar" também deve ser engolido (o EMI abre receitas no soltar). */
    private static int pendingRelease = -1;

    public static boolean consumeRelease(int button) {
        if (pendingRelease != button) return false;
        pendingRelease = -1;
        return true;
    }

    /** @return true se o clique foi no menu "pesquisar por" ou numa sugestão. */
    public static boolean mouseClicked(double mx, double my, int button) {
        boolean r = mouseClickedInner(mx, my, button);
        if (r) pendingRelease = button;
        return r;
    }

    private static boolean mouseClickedInner(double mx, double my, int button) {
        if (HoverSearch.mouseClicked(mx, my, button)) return true;
        Active a = active();
        if (a == null || !listVisible() || (button != 0 && button != 1)) return false;
        if (mx < listX || mx >= listX + listW) return false;
        int i = (int) Math.floor((my - listY - 1) / ROW);
        if (my < listY || i < 0 || i >= suggestions.size()) return false;
        if (button == 1) {
            // clique direito numa busca do histórico: fixa / desafixa
            String h = suggestions.get(i).history();
            if (h == null) return false;
            HistoryStore.togglePin(a.pins(), h);
            lastText = null; // recalcula a lista
            return true;
        }
        apply(a, i);
        return true;
    }
}
