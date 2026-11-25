package com.xorume.emisearchplus.mixin;

import com.xorume.emisearchplus.EmiSearchPlus;
import com.xorume.emisearchplus.ui.EmiConfigEntries;
import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.widget.config.ConfigSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Acrescenta o grupo "EMI Search Plus" no fim da lista de configurações do EMI. */
@Mixin(value = ConfigScreen.class, remap = false)
public abstract class ConfigScreenMixin extends net.minecraft.client.gui.screens.Screen {
    @Shadow private ConfigSearch search;

    protected ConfigScreenMixin(net.minecraft.network.chat.Component title) {
        super(title);
    }

    @Unique private boolean esp$collapsed;

    @Inject(method = "init", at = @At("HEAD"))
    private void esp$remember(CallbackInfo ci) {
        esp$collapsed = EmiConfigEntries.wasCollapsed((ConfigScreen) (Object) this);
    }

    @Inject(method = "init", at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
            target = "Ldev/emi/emi/screen/widget/config/ListWidget;setScrollAmount(D)V"))
    private void esp$entries(CallbackInfo ci) {
        try {
            EmiConfigEntries.add((ConfigScreen) (Object) this, () -> search == null ? "" : search.getSearch(), esp$collapsed);
        } catch (Throwable t) {
            EmiSearchPlus.LOG.error("EMI Search Plus: falha ao montar a configuração", t);
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void esp$jump(CallbackInfo ci) {
        // botão de ajuda do EMI Search Plus no canto superior direito
        net.minecraft.client.gui.screens.Screen self = this;
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("?"),
                        b -> net.minecraft.client.Minecraft.getInstance().setScreen(new com.xorume.emisearchplus.ui.HelpScreen(self)))
                .bounds(this.width - 24, 4, 20, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(com.xorume.emisearchplus.ui.HelpScreen.openTooltip()))
                .build());
        if (!EmiConfigEntries.jumpOnInit) return;
        EmiConfigEntries.jumpOnInit = false;
        try {
            ((ConfigScreen) (Object) this).jump(EmiConfigEntries.GROUP_ID);
        } catch (Throwable ignored) {
        }
    }
}
