package com.xorume.emisearchplus.mixin;

import com.xorume.emisearchplus.Config;
import com.xorume.emisearchplus.query.Queries;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.search.EmiSearch;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Fuzzy "fallback": se a busca normal não acha nada, liga a tolerância a erros para o CompiledQuery
 * que o próprio SearchWorker.run cria em seguida (mesma thread).
 * Não cancela o run: outros mods (ex.: REMI) envolvem o EmiSearch.apply dele.
 */
@Mixin(targets = "dev.emi.emi.search.EmiSearch$SearchWorker", remap = false)
public abstract class SearchWorkerMixin {
    @Shadow @Final private String query;
    @Shadow @Final private List<? extends EmiIngredient> source;

    @Inject(method = "run", at = @At("HEAD"))
    private void esp$fallback(CallbackInfo ci) {
        Queries.FUZZY_PASS.set(false);
        if (!"fallback".equalsIgnoreCase(Config.get().fuzzy)) return;
        if (query == null || query.chars().noneMatch(Character::isLetter)) return;
        try {
            EmiSearch.CompiledQuery cq = new EmiSearch.CompiledQuery(query);
            if (cq.isEmpty() || esp$anyMatch(cq)) return;
            Queries.FUZZY_PASS.set(true);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void esp$clear(CallbackInfo ci) {
        Queries.FUZZY_PASS.set(false);
    }

    @Unique
    private boolean esp$anyMatch(EmiSearch.CompiledQuery cq) {
        for (EmiIngredient ing : source) {
            for (EmiStack s : ing.getEmiStacks()) {
                if (cq.test(s)) return true;
            }
        }
        return false;
    }
}
