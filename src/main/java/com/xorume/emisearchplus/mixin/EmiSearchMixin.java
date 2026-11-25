package com.xorume.emisearchplus.mixin;

import com.xorume.emisearchplus.TextNorm;
import com.xorume.emisearchplus.query.SearchIndex;
import com.xorume.emisearchplus.ui.Autocomplete;
import dev.emi.emi.search.EmiSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Índice sem acentos e índices extras (iniciais, fuzzy, stacks canônicos). */
@Mixin(value = EmiSearch.class, remap = false)
public class EmiSearchMixin {
    // ModifyArg (e não Redirect): o REMI envolve essas mesmas chamadas com WrapOperation
    @ModifyArg(method = "bake", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/searchtree/SuffixArray;add(Ljava/lang/Object;Ljava/lang/String;)V"))
    private static String esp$add(String text) {
        return TextNorm.norm(text);
    }

    @Inject(method = "bake", at = @At("TAIL"))
    private static void esp$baked(CallbackInfo ci) {
        SearchIndex.rebuild();
        Autocomplete.invalidate();
    }
}
