package com.xorume.emisearchplus.mixin;

import com.xorume.emisearchplus.Config;
import com.xorume.emisearchplus.ui.Highlighter;
import com.xorume.emisearchplus.ui.RecipeFilter;
import com.xorume.emisearchplus.ui.RecipeSearchHolder;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Tuple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Barra de pesquisa na tela de receitas do EMI: filtra as receitas mostradas (Extended+: &lt;entradas &gt;saídas ^outros). */
@Mixin(value = RecipeScreen.class, remap = false)
public abstract class RecipeScreenMixin extends Screen implements RecipeSearchHolder {
    @Shadow private Map<EmiRecipeCategory, List<EmiRecipe>> recipes;
    @Shadow private List<dev.emi.emi.screen.WidgetGroup> currentPage;
    @Shadow private int tab;
    @Shadow private int page;
    @Shadow private int tabPage;
    @Shadow int backgroundWidth;
    @Shadow int backgroundHeight;
    @Shadow int x;
    @Shadow int y;

    @Shadow public abstract EmiRecipeCategory getFocusedCategory();
    @Shadow public abstract void focusCategory(EmiRecipeCategory category);

    @Unique private Map<EmiRecipeCategory, List<EmiRecipe>> esp$all;
    @Unique private EditBox esp$box;
    @Unique private String esp$text = "";
    @Unique private boolean esp$focused;
    @Unique private boolean esp$noMatch;
    @Unique private int esp$shown = -1;
    @Unique private int esp$total;
    @Unique private volatile int esp$gen;
    @Unique private List<Tuple<Integer, Style>> esp$styles = List.of();
    /** Matcher (main thread) usado para destacar os slots que bateram. */
    @Unique private com.xorume.emisearchplus.parse.RecipeMatcher esp$highlight;

    protected RecipeScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private boolean esp$enabled() {
        return Config.get().recipeSearch && this.height >= 140;
    }

    @Override
    public EditBox esp$searchBox() {
        return esp$box;
    }

    // ------------------------------------------------------------------ layout

    /** Abre 18 px embaixo do fundo para a barra. */
    @Inject(method = "init", at = @At(value = "FIELD", target = "Ldev/emi/emi/screen/RecipeScreen;backgroundHeight:I",
            opcode = 181, shift = At.Shift.AFTER, ordinal = 0))
    private void esp$shrink(CallbackInfo ci) {
        if (esp$enabled()) backgroundHeight -= 18;
    }

    /** Mantém o topo onde estava (o EMI centraliza pela altura já reduzida). */
    @Inject(method = "init", at = @At(value = "FIELD", target = "Ldev/emi/emi/screen/RecipeScreen;y:I",
            opcode = 181, shift = At.Shift.AFTER, ordinal = 0))
    private void esp$shiftY(CallbackInfo ci) {
        if (esp$enabled()) y -= 9;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void esp$init(CallbackInfo ci) {
        if (!esp$enabled() || recipes == null) {
            esp$box = null;
            return;
        }
        if (esp$all == null) {
            esp$all = new LinkedHashMap<>(recipes);
            esp$total = RecipeFilter.count(esp$all);
        }
        EditBox b = new EditBox(this.font, x + 1, y + backgroundHeight + 3, Math.max(40, backgroundWidth - 2), 14,
                Component.translatable("emisearchplus.recipe_search"));
        b.setMaxLength(256);
        b.setHint((com.xorume.emisearchplus.ui.HelpScreen.KEY.isUnbound()
                ? Component.translatable("emisearchplus.recipe_search.hint.nokey")
                : Component.translatable("emisearchplus.recipe_search.hint", com.xorume.emisearchplus.ui.HelpScreen.KEY.getTranslatedKeyMessage()))
                .withStyle(ChatFormatting.DARK_GRAY));
        b.setValue(esp$text);
        b.setFormatter((part, offset) -> {
            Config c = Config.get();
            if (c.extendedPlus() && c.syntaxHighlight && !esp$styles.isEmpty()) {
                try {
                    return Highlighter.format(esp$styles, part, offset);
                } catch (Throwable ignored) {
                }
            }
            return FormattedCharSequence.forward(part, Style.EMPTY);
        });
        b.setResponder(this::esp$changed);
        b.setFocused(esp$focused);
        esp$box = b;
    }

    @Inject(method = "getBounds", at = @At("RETURN"), cancellable = true)
    private void esp$bounds(CallbackInfoReturnable<Bounds> cir) {
        if (esp$box == null) return;
        Bounds b = cir.getReturnValue();
        cir.setReturnValue(new Bounds(b.x(), b.y(), b.width(), b.height() + 20));
    }

    // ------------------------------------------------------------------ filtro

    @Unique
    private void esp$changed(String text) {
        esp$text = text;
        Config c = Config.get();
        esp$styles = c.extendedPlus() && c.syntaxHighlight && !text.isEmpty() ? Highlighter.styles(text, true) : List.of();
        int gen = ++esp$gen;
        Map<EmiRecipeCategory, List<EmiRecipe>> all = esp$all;
        if (all == null) return;
        if (text.isBlank()) {
            esp$apply(null, gen);
            return;
        }
        com.xorume.emisearchplus.parse.RecipeMatcher.Context ctx = com.xorume.emisearchplus.parse.RecipeMatcher.Context.EMPTY;
        if (com.xorume.emisearchplus.parse.RecipeMatcher.needsContext(text)) {
            Minecraft mc = Minecraft.getInstance();
            ctx = new com.xorume.emisearchplus.parse.RecipeMatcher.Context(
                    mc.player == null ? null : dev.emi.emi.api.recipe.EmiPlayerInventory.of(mc.player),
                    com.xorume.emisearchplus.ui.SccStock.snapshot());
        }
        com.xorume.emisearchplus.parse.RecipeMatcher.Context fctx = ctx;
        RecipeFilter.schedule(() -> {
            if (gen != esp$gen) return;
            Map<EmiRecipeCategory, List<EmiRecipe>> res = RecipeFilter.filter(all, text, fctx, () -> gen != esp$gen);
            if (res == null) return;
            Minecraft.getInstance().execute(() -> esp$apply(res, gen));
        });
    }

    @Unique
    private void esp$apply(Map<EmiRecipeCategory, List<EmiRecipe>> res, int gen) {
        if (gen != esp$gen || esp$all == null) return;
        try {
            esp$highlight = res == null || !Config.get().highlightMatches ? null
                    : new com.xorume.emisearchplus.parse.RecipeMatcher(esp$text);
        } catch (Throwable t) {
            esp$highlight = null;
        }
        Map<EmiRecipeCategory, List<EmiRecipe>> target;
        if (res == null) {
            target = esp$all;
            esp$noMatch = false;
            esp$shown = -1;
        } else if (res.isEmpty()) {
            // o EMI não aceita um mapa vazio: mantém o que está na tela e cobre com o aviso
            esp$noMatch = true;
            esp$shown = 0;
            return;
        } else {
            target = res;
            esp$noMatch = false;
            esp$shown = RecipeFilter.count(res);
        }
        if (target == recipes) return;
        EmiRecipeCategory focused = null;
        try {
            focused = getFocusedCategory();
        } catch (Throwable ignored) {
        }
        boolean keep = focused != null && target.containsKey(focused);
        recipes = target;
        if (!keep) {
            tab = 0;
            page = 0;
            tabPage = 0;
        }
        esp$focused = esp$box != null && esp$box.isFocused();
        int cursor = esp$box != null ? esp$box.getCursorPosition() : -1;
        this.rebuildWidgets();
        if (esp$box != null && cursor >= 0) esp$box.setCursorPosition(Math.min(cursor, esp$box.getValue().length()));
        if (keep) {
            try {
                focusCategory(focused);
            } catch (Throwable ignored) {
            }
        }
    }

    @Unique
    private void esp$focus(boolean f) {
        if (esp$box == null) return;
        if (!f && esp$box.isFocused()) RecipeSearchHolder.remember(esp$box.getValue());
        esp$box.setFocused(f);
        esp$focused = f;
        if (f && EmiScreenManager.search != null && EmiScreenManager.search.isFocused()) {
            EmiScreenManager.search.setFocused(false);
        }
    }

    // ------------------------------------------------------------------ desenho

    @Inject(method = "render", at = @At("TAIL"))
    private void esp$render(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        EditBox b = esp$box;
        if (b == null) return;
        if (b.isFocused() && EmiScreenManager.search != null && EmiScreenManager.search.isFocused()) esp$focus(false);
        String count = esp$shown < 0 ? "" : esp$shown + "/" + esp$total;
        int cw = count.isEmpty() ? 0 : this.font.width(count) + 6;
        b.setX(x + 1);
        b.setY(y + backgroundHeight + 3);
        b.setWidth(Math.max(40, backgroundWidth - 2 - cw - 14));
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        com.xorume.emisearchplus.parse.RecipeMatcher hl = esp$highlight;
        if (hl != null && !esp$noMatch && currentPage != null) {
            try {
                for (dev.emi.emi.screen.WidgetGroup grp : currentPage) {
                    for (dev.emi.emi.api.widget.Widget w : grp.widgets) {
                        if (!(w instanceof dev.emi.emi.api.widget.SlotWidget sw) || !hl.highlights(sw.getStack())) continue;
                        Bounds sb = sw.getBounds();
                        int sx = grp.x() + sb.x(), sy = grp.y() + sb.y();
                        g.fill(sx + 1, sy + 1, sx + sb.width() - 1, sy + sb.height() - 1, 0x30FFD040);
                        g.renderOutline(sx, sy, sb.width(), sb.height(), 0xFFFFC020);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (esp$noMatch) {
            g.fill(x + 4, y + 4, x + backgroundWidth - 4, y + backgroundHeight - 4, 0xD0101010);
            Component msg = Component.translatable("emisearchplus.recipe_search.none");
            g.drawCenteredString(this.font, msg, x + backgroundWidth / 2, y + backgroundHeight / 2 - 4, 0xFF5555);
        }
        b.render(g, mx, my, delta);
        if (esp$noMatch) g.renderOutline(b.getX() - 1, b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2, 0xFFFF5555);
        if (!count.isEmpty()) {
            g.drawString(this.font, count, b.getX() + b.getWidth() + 4, b.getY() + 3, esp$noMatch ? 0xFF5555 : 0xA0A0A0, false);
        }
        // botão "?" (ajuda)
        int hx = x + backgroundWidth - 13, hy = b.getY();
        boolean hh = mx >= hx && mx < hx + 12 && my >= hy && my < hy + 14;
        g.fill(hx, hy, hx + 12, hy + 14, hh ? 0xFF505080 : 0xFF303040);
        g.renderOutline(hx, hy, 12, 14, 0xFF8080A0);
        g.drawString(this.font, "?", hx + 4, hy + 3, 0xFFFFFF, false);
        if (hh) g.renderTooltip(this.font, com.xorume.emisearchplus.ui.HelpScreen.openTooltip(), mx, my);
        if (!b.isFocused() && b.isMouseOver(mx, my)) {
            g.renderTooltip(this.font, List.of(
                    Component.translatable("emisearchplus.recipe_search.tip1").getVisualOrderText(),
                    Component.translatable("emisearchplus.recipe_search.tip2").withStyle(ChatFormatting.GRAY).getVisualOrderText(),
                    Component.translatable("emisearchplus.recipe_search.tip3").withStyle(ChatFormatting.GRAY).getVisualOrderText()), mx, my);
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ entrada

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void esp$key(int key, int scan, int mods, CallbackInfoReturnable<Boolean> cir) {
        EditBox b = esp$box;
        if (b == null) return;
        if (b.isFocused()) {
            if (key == 256 || key == 257 || key == 335) {
                esp$focus(false);
            } else {
                b.keyPressed(key, scan, mods);
            }
            cir.setReturnValue(true);
        } else if (key == 70 && Screen.hasControlDown()) {
            esp$focus(true);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void esp$char(char c, int mods, CallbackInfoReturnable<Boolean> cir) {
        EditBox b = esp$box;
        if (b != null && b.isFocused()) {
            b.charTyped(c, mods);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void esp$click(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        EditBox b = esp$box;
        if (b == null) return;
        int hx = x + backgroundWidth - 13, hy = b.getY();
        if (button == 0 && mx >= hx && mx < hx + 12 && my >= hy && my < hy + 14) {
            Minecraft.getInstance().setScreen(new com.xorume.emisearchplus.ui.HelpScreen((Screen) (Object) this));
            cir.setReturnValue(true);
            return;
        }
        if (b.isMouseOver(mx, my)) {
            if (button == 1) b.setValue("");
            esp$focus(true);
            b.mouseClicked(mx, my, 0);
            cir.setReturnValue(true);
        } else if (b.isFocused()) {
            esp$focus(false);
        }
    }
}
