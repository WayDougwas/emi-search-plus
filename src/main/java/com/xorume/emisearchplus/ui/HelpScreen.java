package com.xorume.emisearchplus.ui;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.ConfigSearch;
import dev.emi.emi.screen.widget.config.EmiNameWidget;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.ListWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Ajuda do EMI Search Plus, montada com os widgets da tela de configuração do EMI
 * (EmiNameWidget, ListWidget, GroupNameWidget, ConfigSearch) para ficar com a mesma cara.
 * Conteúdo do arquivo de idioma: emisearchplus.help.&lt;seção&gt;.title e emisearchplus.help.&lt;seção&gt;.&lt;n&gt; = "exemplo|descrição".
 * Exemplo começando com "`" não é clicável (teclas etc.); sem exemplo vira um parágrafo.
 */
public class HelpScreen extends Screen {
    /** Tecla de abrir a ajuda (Controles → EMI Search Plus); pode ficar sem tecla. */
    public static final net.minecraft.client.KeyMapping KEY = new net.minecraft.client.KeyMapping("key.emisearchplus.open_help",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_F1, "key.categories.emisearchplus");

    private static final String[] SECTIONS = {"basic", "prefixes", "tolerant", "recipes", "keys", "calc", "config"};

    private final Screen parent;
    private ListWidget list;
    private ConfigSearch search;
    private EditBox searchField;
    private String lastSearch = "";
    private double lastScroll;

    public HelpScreen(Screen parent) {
        super(Component.translatable("emisearchplus.help.title"));
        this.parent = parent;
    }

    private static String tr(String key) {
        return Language.getInstance().getOrDefault(key)
                .replace("{help}", keyName(KEY))
                .replace("{search}", keyName(HoverSearch.KEY));
    }

    /** Nome da tecla atual, ou "(sem tecla)". */
    public static String keyName(net.minecraft.client.KeyMapping k) {
        return k.isUnbound() ? Component.translatable("emisearchplus.key.none").getString() : k.getTranslatedKeyMessage().getString();
    }

    /** "Ajuda do EMI Search Plus (F1)" com a tecla configurada. */
    public static Component openTooltip() {
        return KEY.isUnbound() ? Component.translatable("emisearchplus.help.open.nokey")
                : Component.translatable("emisearchplus.help.open", KEY.getTranslatedKeyMessage());
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // a mesma tecla fecha a ajuda (se o campo de busca não estiver focado)
        if ((searchField == null || !searchField.isFocused()) && KEY.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(key, scan))) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    protected void init() {
        if (list != null) {
            lastScroll = list.getScrollAmount();
            lastSearch = search.getSearch();
        }
        list = new ListWidget(minecraft, width, height, 40, height - 60);
        addRenderableOnly(new EmiNameWidget(width / 2, 16));
        int w = Math.min(400, width - 40);
        int x = (width - w) / 2;
        search = new ConfigSearch(x + 2, height - 52, w - 4, 18);
        EditBox field = search.field;
        searchField = field;
        addRenderableWidget(field);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, height - 30, w, 20).build());

        Supplier<String> q = () -> search.getSearch();
        int rowW = list.getRowWidth();
        boolean first = true;
        for (String id : SECTIONS) {
            String base = "emisearchplus.help." + id;
            GroupNameWidget group = new GroupNameWidget("emisearchplus.help." + id, Component.literal(tr(base + ".title")));
            list.addEntry(group);
            if (first && com.xorume.emisearchplus.Config.get().explicitAnd) {
                // aviso do modo "E explícito" no topo da primeira seção
                Entry note = new Entry("", tr("emisearchplus.help.explicit_note"), false, false, false, q, rowW);
                group.children.add(note);
                note.parentGroups.add(group);
                list.addEntry(note);
            }
            first = false;
            boolean recipe = id.equals("recipes");
            boolean calc = id.equals("calc");
            List<Entry> entries = new ArrayList<>();
            for (int i = 1; Language.getInstance().has(base + "." + i); i++) {
                String raw = tr(base + "." + i);
                int bar = raw.indexOf('\t');
                String ex = bar < 0 ? "" : raw.substring(0, bar);
                String desc = bar < 0 ? raw : raw.substring(bar + 1);
                boolean click = !ex.isEmpty() && !ex.startsWith("`");
                if (ex.startsWith("`")) ex = ex.substring(1);
                if (click && !calc && com.xorume.emisearchplus.Config.get().explicitAnd) ex = toExplicit(ex, recipe);
                entries.add(new Entry(ex, desc, click, recipe, calc, q, rowW));
            }
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                e.endGroup = i == entries.size() - 1;
                group.children.add(e);
                e.parentGroups.add(group);
                list.addEntry(e);
            }
        }
        addWidget(list);
        search.setText(lastSearch);
        list.setScrollAmount(lastScroll);
    }

    @Override
    public void render(GuiGraphics raw, int mx, int my, float delta) {
        EmiDrawContext ctx = EmiDrawContext.wrap(raw);
        list.setScrollAmount(list.getScrollAmount());
        super.render(ctx.raw(), mx, my, delta);
        list.render(ctx.raw(), mx, my, delta);
        ctx.drawCenteredTextWithShadow(Component.translatable("emisearchplus.help.subtitle").withStyle(ChatFormatting.GRAY), width / 2, 28, -1);
        if (list.getHoveredEntry() != null) {
            List<ClientTooltipComponent> tip = list.getHoveredEntry().getTooltip(mx, my);
            if (tip != null && !tip.isEmpty()) EmiRenderHelper.drawTooltip(this, ctx, tip, mx, my, Math.min(width / 2 - 16, 200));
        }
    }

    /** Coloca o exemplo na barra de onde a ajuda foi aberta e volta. */
    void use(String example) {
        onClose();
        if (parent instanceof RecipeSearchHolder h && h.esp$searchBox() != null) {
            EditBox b = h.esp$searchBox();
            b.setValue(example);
            b.moveCursorToEnd(false);
            b.setFocused(true);
            return;
        }
        try {
            EmiApi.setSearchText(example);
            if (EmiScreenManager.search != null) {
                EmiScreenManager.search.setFocused(true);
                EmiScreenManager.search.moveCursorToEnd(false);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Linha da ajuda: exemplo (caixa no estilo da barra de pesquisa, com realce) + descrição. */
    private final class Entry extends ConfigEntryWidget {
        private final String example, desc;
        private final boolean clickable, recipe, calc;
        private int exX, exY, exW, exH;
        private int cachedWidth = -1;
        private List<FormattedCharSequence> descLines = List.of(), exLines = List.of();
        /** Exemplo já colorido (calculado uma vez). */
        private FormattedCharSequence styled;

        Entry(String example, String desc, boolean clickable, boolean recipe, boolean calc, Supplier<String> search, int rowW) {
            super(Component.literal(example.isEmpty() ? desc : example), List.of(), search, heightFor(example, desc, rowW));
            this.example = example;
            this.desc = desc;
            this.clickable = clickable;
            this.recipe = recipe;
            this.calc = calc;
            setChildren(List.of());
        }

        @Override
        public String getSearchableText() {
            return example + " " + desc;
        }

        private void wrap(int width) {
            if (width == cachedWidth) return;
            cachedWidth = width;
            int exCol = example.isEmpty() ? 0 : exColumn(width);
            int descW = Math.max(40, width - exCol - 12 - (exCol > 0 ? 8 : 0));
            descLines = font.split(Component.literal(desc), descW);
            exLines = example.isEmpty() ? List.of() : font.split(Component.literal(example), Math.max(20, exCol - 8));
        }

        @Override
        public void render(GuiGraphics raw, int index, int y, int x, int width, int height, int mx, int my, boolean hovered, float delta) {
            EmiDrawContext ctx = EmiDrawContext.wrap(raw);
            // linha do grupo, igual aos ConfigEntryWidget do EMI
            ctx.fill(x + 4, y + height / 2 - 1, 6, 2, -1);
            if (endGroup) ctx.fill(x + 2, y - 4, 2, height / 2 + 5, -1);
            else ctx.fill(x + 2, y - 4, 2, height + 4, -1);
            x += 10;
            width -= 10;
            ctx.fill(x, y, width, height, 0x66000000);
            wrap(width);
            int tx = x + 6;
            if (!example.isEmpty()) {
                int exCol = exColumn(width);
                exX = x + 4;
                exY = y + 3;
                exW = exCol;
                exH = height - 6;
                boolean over = clickable && mx >= exX && mx < exX + exW && my >= exY && my < exY + exH;
                if (clickable) {
                    // caixa no estilo da barra de pesquisa
                    ctx.fill(exX, exY, exW, exH, over ? 0xFFA0A0A0 : 0xFF6F6F6F);
                    ctx.fill(exX + 1, exY + 1, exW - 2, exH - 2, 0xFF000000);
                } else {
                    ctx.fill(exX, exY, exW, exH, 0x40FFAA00);
                }
                int ly = exY + (exH - exLines.size() * 10) / 2 + 1;
                boolean single = exLines.size() == 1;
                for (FormattedCharSequence l : exLines) {
                    FormattedCharSequence text = l;
                    if (clickable && single && !calc) {
                        if (styled == null) {
                            try {
                                styled = Highlighter.format(Highlighter.styles(example, recipe), example, 0);
                            } catch (Throwable t) {
                                styled = l;
                            }
                        }
                        text = styled;
                    }
                    ctx.raw().drawString(font, text, exX + 4, ly, clickable ? (calc ? 0x7FFF7F : 0xFFFFFF) : 0xFFD060, true);
                    ly += 10;
                }
                tx = exX + exW + 8;
            }
            int ly = y + (height - descLines.size() * 10) / 2 + 1;
            for (FormattedCharSequence l : descLines) {
                ctx.raw().drawString(font, l, tx, ly, example.isEmpty() ? 0xB0B0B0 : 0xFFFFFF, true);
                ly += 10;
            }
        }

        @Override
        public List<ClientTooltipComponent> getTooltip(int mx, int my) {
            if (clickable && mx >= exX && mx < exX + exW && my >= exY && my < exY + exH) {
                return List.of(ClientTooltipComponent.create(Component.translatable("emisearchplus.help.click").getVisualOrderText()));
            }
            return List.of();
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && clickable && mx >= exX && mx < exX + exW && my >= exY && my < exY + exH) {
                use(example);
                return true;
            }
            return false;
        }
    }

    /**
     * Adapta um exemplo ao modo "E explícito": onde dois termos ficam lado a lado (erro nesse modo),
     * insere "& " antes do segundo. Ex.: "ingot -@minecraft" -> "ingot & -@minecraft".
     */
    static String toExplicit(String ex, boolean recipe) {
        String cur = ex;
        for (int i = 0; i < 12; i++) {
            com.xorume.emisearchplus.parse.ExtPlusParser.Result r = com.xorume.emisearchplus.parse.ExtPlusParser.spans(cur, recipe);
            int at = r.errorAt;
            if (at <= 0 || at >= cur.length() || !Character.isWhitespace(cur.charAt(at - 1))) break;
            cur = cur.substring(0, at) + "& " + cur.substring(at);
        }
        return cur;
    }

    private static int exColumn(int width) {
        return Math.min(150, Math.max(70, width * 2 / 5));
    }

    /** Altura da linha, calculada com a largura da lista (a mesma regra de quebra do render). */
    private static int heightFor(String example, String desc, int rowW) {
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        int width = rowW - 10;
        int exCol = example.isEmpty() ? 0 : exColumn(width);
        int descW = Math.max(40, width - exCol - 12 - (exCol > 0 ? 8 : 0));
        int dl = font.split(Component.literal(desc), descW).size();
        int el = example.isEmpty() ? 0 : font.split(Component.literal(example), Math.max(20, exCol - 8)).size();
        return Math.max(20, Math.max(dl, el) * 10 + 10);
    }
}
