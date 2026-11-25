package com.xorume.emisearchplus.mixin;

import com.xorume.emisearchplus.Config;
import com.xorume.emisearchplus.EmiSearchPlus;
import com.xorume.emisearchplus.parse.ExtPlusParser;
import com.xorume.emisearchplus.query.Queries;
import dev.emi.emi.search.EmiSearch;
import dev.emi.emi.search.Query;
import dev.emi.emi.search.QueryType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Function;

/**
 * Modo "emi": troca as folhas do parser original (prefixos &amp; % ~, acentos, iniciais/fuzzy).
 * Modo "extended_plus": substitui a query inteira pela do parser Extended+.
 */
@Mixin(value = EmiSearch.CompiledQuery.class, remap = false)
public class CompiledQueryMixin {
    @Shadow @Final @Mutable public Query fullQuery;

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/search/QueryType;fromString(Ljava/lang/String;)Ldev/emi/emi/search/QueryType;"))
    private static QueryType esp$type(String s) {
        QueryType t = QueryType.fromString(s);
        Queries.EMI_TYPE.set(t);
        return t;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/search/EmiSearch$CompiledQuery;addQuery(Ljava/lang/String;ZLjava/util/List;Ljava/util/function/Function;Ljava/util/function/Function;)V"))
    private static void esp$addQuery(String s, boolean negated, List<Query> out, Function<String, Query> normal, Function<String, Query> regex) {
        Queries.emiAddQuery(s, negated, out, normal, regex, Queries.EMI_TYPE.get() == QueryType.DEFAULT);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void esp$extendedPlus(String query, CallbackInfo ci) {
        if (!Config.get().extendedPlus()) return;
        try {
            Query q = ExtPlusParser.parseItems(query).query;
            this.fullQuery = q == null ? null : new Queries.Safe(q);
        } catch (Throwable t) {
            EmiSearchPlus.LOG.error("EMI Search Plus: erro ao interpretar '{}'", query, t);
        }
    }
}
