package com.xorume.emisearchplus.ui;

import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.List;

/** Implementado pela RecipeScreen do EMI (via mixin) para expor a barra de pesquisa de receitas. */
public interface RecipeSearchHolder {
    /** Histórico de buscas de receitas (mais recente primeiro). */
    List<String> HISTORY = new ArrayList<>();

    /** A barra, ou null se desligada. */
    EditBox esp$searchBox();

    static void remember(String q) {
        if (q == null || q.isBlank()) return;
        HISTORY.remove(q);
        HISTORY.add(0, q);
        while (HISTORY.size() > 30) HISTORY.remove(HISTORY.size() - 1);
    }
}
