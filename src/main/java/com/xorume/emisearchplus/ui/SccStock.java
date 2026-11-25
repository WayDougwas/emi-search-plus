package com.xorume.emisearchplus.ui;

import com.xorume.emisearchplus.EmiSearchPlus;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estoque do terminal do SCC visto pelo cliente (de.scc.calculator.client.TerminalStock), lido por reflexão:
 * o addon não depende do SCC. Só existe enquanto o SCC mandou o estoque (terminal aberto recentemente).
 */
public final class SccStock {
    private static Method current, keyOf;
    private static boolean tried;

    private final Map<Object, Long> stock;
    private final Map<EmiStack, Long> cache = new HashMap<>();

    private SccStock(Map<Object, Long> stock) {
        this.stock = stock;
    }

    private static synchronized boolean init() {
        if (!tried) {
            tried = true;
            try {
                Class<?> ts = Class.forName("de.scc.calculator.client.TerminalStock");
                Class<?> sk = Class.forName("de.scc.calculator.calc.StackKey");
                current = ts.getMethod("current");
                keyOf = sk.getMethod("of", ItemStack.class);
            } catch (ClassNotFoundException e) {
                // SCC não instalado
            } catch (Throwable t) {
                EmiSearchPlus.LOG.warn("EMI Search Plus: API do SCC mudou, ?scc desativado", t);
            }
        }
        return current != null && keyOf != null;
    }

    /** Cópia do estoque atual (chamar no main thread), ou null se o SCC não está presente/sem dados. */
    @SuppressWarnings("unchecked")
    public static SccStock snapshot() {
        if (!init()) return null;
        try {
            Object m = current.invoke(null);
            if (!(m instanceof Map<?, ?> map) || map.isEmpty()) return null;
            return new SccStock(new HashMap<>((Map<Object, Long>) map));
        } catch (Throwable t) {
            return null;
        }
    }

    private long amount(EmiStack s) {
        if (!(s.getKey() instanceof Item)) return -1;
        return cache.computeIfAbsent(s, k -> {
            try {
                Object key = keyOf.invoke(null, k.getItemStack());
                Long v = stock.get(key);
                return v == null ? 0L : v;
            } catch (Throwable t) {
                return 0L;
            }
        });
    }

    /** Todas as entradas de item existem no estoque na quantidade pedida (entradas que não são itens, como fluidos, são ignoradas). */
    public boolean hasInputs(List<? extends EmiIngredient> inputs) {
        for (EmiIngredient ing : inputs) {
            if (ing == null || ing.isEmpty()) continue;
            long need = Math.max(1, ing.getAmount());
            long best = -1;
            for (EmiStack s : ing.getEmiStacks()) {
                best = Math.max(best, amount(s));
            }
            if (best < 0) continue; // nenhum item (fluido etc.)
            if (best < need) return false;
        }
        return true;
    }
}
