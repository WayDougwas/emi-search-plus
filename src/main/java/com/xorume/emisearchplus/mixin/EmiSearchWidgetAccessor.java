package com.xorume.emisearchplus.mixin;

import dev.emi.emi.screen.widget.EmiSearchWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = EmiSearchWidget.class, remap = false)
public interface EmiSearchWidgetAccessor {
    @Accessor("searchHistory")
    List<String> esp$getSearchHistory();
}
