package com.xorume.emisearchplus.mixin;

import com.xorume.emisearchplus.Config;
import com.xorume.emisearchplus.ui.Highlighter;
import dev.emi.emi.screen.widget.EmiSearchWidget;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Tuple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Realce de sintaxe Extended+ na barra principal do EMI. */
@Mixin(value = EmiSearchWidget.class, remap = false)
public abstract class EmiSearchWidgetMixin {
    @Shadow private List<Tuple<Integer, Style>> styles;

    // responder do campo (lambda do construtor): roda antes do EmiSearch.search, depois de o EMI montar os estilos
    @Inject(method = "lambda$new$1", require = 0, at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/search/EmiSearch;search(Ljava/lang/String;)V"))
    private void esp$highlight(String text, CallbackInfo ci) {
        Config c = Config.get();
        if (!c.extendedPlus() || !c.syntaxHighlight || text.isEmpty()) return;
        try {
            List<Tuple<Integer, Style>> s = Highlighter.styles(text, false);
            if (!s.isEmpty()) styles = s;
        } catch (Throwable ignored) {
        }
    }
}
