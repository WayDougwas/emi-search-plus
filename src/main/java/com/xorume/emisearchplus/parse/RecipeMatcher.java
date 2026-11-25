package com.xorume.emisearchplus.parse;

import com.xorume.emisearchplus.Config;
import com.xorume.emisearchplus.TextNorm;
import com.xorume.emisearchplus.query.SearchIndex;
import com.xorume.emisearchplus.ui.SccStock;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.search.EmiSearch;
import dev.emi.emi.search.Query;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Testa receitas contra o texto da barra de pesquisa da tela de receitas. Não é thread-safe: uma instância por thread. */
public final class RecipeMatcher {
    /** Dados que precisam ser lidos no main thread antes de filtrar. */
    public record Context(EmiPlayerInventory inventory, SccStock stock) {
        public static final Context EMPTY = new Context(null, null);
    }

    private record Clause(ExtPlusParser.Part part, boolean all, Predicate<EmiStack> test, String text, boolean negated) {}

    private final List<Clause> clauses = new ArrayList<>();
    private final Context ctx;
    private final Map<EmiRecipeCategory, Boolean> categoryCache = new IdentityHashMap<>();
    public final boolean empty;

    public RecipeMatcher(String text) {
        this(text, Context.EMPTY);
    }

    public RecipeMatcher(String text, Context ctx) {
        this.ctx = ctx == null ? Context.EMPTY : ctx;
        if (Config.get().extendedPlus()) {
            ExtPlusParser.Result r = ExtPlusParser.parseRecipes(text);
            for (ExtPlusParser.Clause c : r.clauses) {
                switch (c.part()) {
                    case CATEGORY, SPECIAL -> clauses.add(new Clause(c.part(), false, null, c.text(), c.negated()));
                    default -> {
                        if (c.query() != null) clauses.add(new Clause(c.part(), c.all(), memo(c.query()), null, false));
                    }
                }
            }
        } else if (!text.isBlank()) {
            EmiSearch.CompiledQuery cq = new EmiSearch.CompiledQuery(text);
            if (!cq.isEmpty()) {
                Map<EmiStack, Boolean> cache = new IdentityHashMap<>();
                clauses.add(new Clause(ExtPlusParser.Part.ANY, false,
                        s -> cache.computeIfAbsent(SearchIndex.canonical(s), cq::test), null, false));
            }
        }
        empty = clauses.isEmpty();
    }

    /** O texto usa ?inv / ?scc (precisa do Context). */
    public static boolean needsContext(String text) {
        return text.indexOf('?') >= 0;
    }

    private static Predicate<EmiStack> memo(Query q) {
        Map<EmiStack, Boolean> cache = new IdentityHashMap<>();
        return s -> cache.computeIfAbsent(SearchIndex.canonical(s), c -> {
            try {
                Boolean b = EmiSearch.bakedStacks != null && EmiSearch.bakedStacks.contains(c) ? q.matches(c) : q.matchesUnbaked(c);
                return b != q.negated;
            } catch (Throwable t) {
                return false;
            }
        });
    }

    public boolean test(EmiRecipe r) {
        for (Clause c : clauses) {
            if (!clause(r, c)) return false;
        }
        return true;
    }

    /** Para o destaque: algum stack do ingrediente bate com alguma cláusula de itens? */
    public boolean highlights(EmiIngredient ing) {
        if (ing == null || ing.isEmpty()) return false;
        for (Clause c : clauses) {
            if (c.test() == null) continue;
            for (EmiStack s : ing.getEmiStacks()) {
                if (!s.isEmpty() && c.test().test(s)) return true;
            }
        }
        return false;
    }

    private boolean clause(EmiRecipe r, Clause c) {
        return switch (c.part()) {
            case INPUTS -> ingredients(safe(r, 0), c);
            case OUTPUTS -> ingredients(safe(r, 1), c);
            case OTHERS -> ingredients(safe(r, 2), c);
            case ANY -> {
                List<EmiIngredient> all = new ArrayList<>(safe(r, 0));
                all.addAll(safe(r, 1));
                all.addAll(safe(r, 2));
                yield ingredients(all, c);
            }
            case CATEGORY -> category(r, c.text()) != c.negated();
            case SPECIAL -> special(r, c.text()) != c.negated();
        };
    }

    private boolean category(EmiRecipe r, String text) {
        EmiRecipeCategory cat;
        try {
            cat = r.getCategory();
        } catch (Throwable t) {
            return false;
        }
        if (cat == null) return false;
        return categoryCache.computeIfAbsent(cat, k -> {
            try {
                String ns = k.getId().getNamespace();
                if (text.startsWith("@")) {
                    String m = text.substring(1);
                    return ns.contains(m) || TextNorm.norm(EmiUtil.getModName(ns)).contains(m);
                }
                return TextNorm.norm(k.getName().getString()).contains(text) || k.getId().toString().contains(text);
            } catch (Throwable t) {
                return false;
            }
        });
    }

    private boolean special(EmiRecipe r, String key) {
        try {
            return switch (key) {
                case "inv", "inventario", "inventory" -> ctx.inventory() != null && ctx.inventory().canCraft(r);
                case "scc", "terminal", "rede" -> ctx.stock() != null && ctx.stock().hasInputs(safe(r, 0));
                default -> false;
            };
        } catch (Throwable t) {
            return false;
        }
    }

    private static List<? extends EmiIngredient> safe(EmiRecipe r, int which) {
        try {
            List<? extends EmiIngredient> l = switch (which) {
                case 0 -> r.getInputs();
                case 1 -> r.getOutputs();
                default -> r.getCatalysts();
            };
            return l == null ? List.of() : l;
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** Sem &: algum ingrediente bate. Com &: todos os ingredientes (não vazios) batem. Um ingrediente bate se algum stack dele bate. */
    private static boolean ingredients(List<? extends EmiIngredient> list, Clause c) {
        boolean any = false;
        for (EmiIngredient ing : list) {
            if (ing == null || ing.isEmpty()) continue;
            boolean m = false;
            for (EmiStack s : ing.getEmiStacks()) {
                if (!s.isEmpty() && c.test().test(s)) {
                    m = true;
                    break;
                }
            }
            if (m) {
                any = true;
                if (!c.all()) return true;
            } else if (c.all()) {
                return false;
            }
        }
        return any;
    }
}
