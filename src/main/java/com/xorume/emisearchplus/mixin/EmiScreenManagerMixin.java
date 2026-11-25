package com.xorume.emisearchplus.mixin;

import com.xorume.emisearchplus.ui.SearchOverlay;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O EMI recebe mouse/teclado direto do MouseHandler/KeyboardHandler, antes dos eventos de tela do NeoForge.
 * Sem isto, um clique no menu "pesquisar por" ou na lista de sugestões chegava antes ao EMI
 * (que abria a receita do item atrás do menu).
 */
@Mixin(value = EmiScreenManager.class, remap = false)
public class EmiScreenManagerMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private static void esp$click(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        if (SearchOverlay.mouseClicked(mx, my, button)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private static void esp$release(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        if (SearchOverlay.consumeRelease(button)) cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private static void esp$key(int key, int scan, int mods, CallbackInfoReturnable<Boolean> cir) {
        if (SearchOverlay.keyPressed(key, scan, mods)) cir.setReturnValue(true);
    }
}
