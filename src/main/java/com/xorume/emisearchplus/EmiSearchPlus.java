package com.xorume.emisearchplus;

import com.mojang.logging.LogUtils;
import com.xorume.emisearchplus.ui.EmiConfigEntries;
import com.xorume.emisearchplus.ui.SearchOverlay;
import dev.emi.emi.screen.ConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value = EmiSearchPlus.MODID, dist = Dist.CLIENT)
public final class EmiSearchPlus {
    public static final String MODID = "emisearchplus";
    public static final Logger LOG = LogUtils.getLogger();

    /** Versão do EMI em que os mixins foram testados (eles mexem em partes internas do EMI). */
    public static final String TESTED_EMI = "1.1.24";

    private static void checkEmiVersion() {
        try {
            String v = net.neoforged.fml.ModList.get().getModContainerById("emi")
                    .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
            if (!v.startsWith(TESTED_EMI)) {
                LOG.warn("EMI Search Plus was tested with EMI {} but found EMI {}. If some feature stops working, "
                        + "check this log for mixin warnings and report it.", TESTED_EMI, v);
            }
        } catch (Throwable ignored) {
        }
    }

    public EmiSearchPlus(IEventBus modBus, ModContainer container) {
        Config.load();
        checkEmiVersion();
        com.xorume.emisearchplus.ui.HistoryStore.load();
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) -> {
            e.register(com.xorume.emisearchplus.ui.HoverSearch.KEY);
            e.register(com.xorume.emisearchplus.ui.HelpScreen.KEY);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Closing e) -> {
            com.xorume.emisearchplus.ui.HoverSearch.close();
            com.xorume.emisearchplus.ui.HistoryStore.save();
        });
        // botão "Config" na lista de mods: abre a tela de configuração do EMI já no grupo do addon
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> {
            EmiConfigEntries.jumpOnInit = true;
            return new ConfigScreen(parent);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.KeyPressed.Pre e) -> {
            if (SearchOverlay.keyPressed(e.getKeyCode(), e.getScanCode(), e.getModifiers())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.MouseButtonPressed.Pre e) -> {
            if (SearchOverlay.mouseClicked(e.getMouseX(), e.getMouseY(), e.getButton())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.MouseButtonReleased.Pre e) -> {
            if (SearchOverlay.consumeRelease(e.getButton())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ScreenEvent.Render.Post e) ->
                SearchOverlay.render(e.getGuiGraphics(), e.getMouseX(), e.getMouseY()));
    }
}
