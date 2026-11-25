package com.xorume.emisearchplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/emisearchplus.json — reescrito ao carregar para incluir chaves novas. */
public final class Config {
    /** "extended_plus" (gramática do NEI GTNH) ou "emi" (parser original do EMI). */
    public String searchMode = "extended_plus";
    /** Ligado: só "&" (ou "&&") soltos combinam termos e o espaço faz parte do texto. Desligado: espaço = E. */
    public boolean explicitAnd = false;
    /** Ignora acentos ("picareta" acha "Picáreta"). */
    public boolean accentInsensitive = true;
    /** Busca por iniciais ("ic" acha "Iron Chestplate") para termos de 2 a 4 letras. */
    public boolean initials = true;
    /** Tolerância a erros de digitação: "fallback" (só quando nada foi achado), "always" ou "off". */
    public String fuzzy = "fallback";
    /** Também pesquisa pelo nome em inglês (en_us) quando o jogo está em outro idioma. */
    public boolean englishNames = true;
    /** Curingas "*" nos termos (iron*ingot, *_ore). */
    public boolean wildcards = true;
    /** Contorna, na tela de receitas, os slots que bateram com a pesquisa. */
    public boolean highlightMatches = true;
    /** Mostra quantos itens a busca achou dentro da barra principal. */
    public boolean showResultCount = true;
    /** Guarda o histórico e as buscas fixadas em config/emisearchplus-history.json. */
    public boolean saveHistory = true;
    /** Barra de pesquisa na tela de receitas. */
    public boolean recipeSearch = true;
    /** Realce de sintaxe no modo Extended+. */
    public boolean syntaxHighlight = true;
    /** Lista de sugestões (@mod, #tag, &id, %aba) e histórico. */
    public boolean autocomplete = true;
    public int maxSuggestions = 8;
    /** Calculadora na barra de pesquisa (64*9+12). */
    public boolean calculator = true;

    private static Config instance = new Config();

    public static Config get() { return instance; }

    public boolean extendedPlus() { return !"emi".equalsIgnoreCase(searchMode); }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("emisearchplus.json");
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (Exception e) {
            EmiSearchPlus.LOG.error("Falha ao gravar {}", path(), e);
        }
    }

    public static void load() {
        Path p = path();
        Gson gson = GSON;
        try {
            if (Files.exists(p)) {
                Config c = gson.fromJson(Files.readString(p, StandardCharsets.UTF_8), Config.class);
                if (c != null) instance = c;
            }
            if (instance.maxSuggestions < 1) instance.maxSuggestions = 8;
            Files.writeString(p, gson.toJson(instance), StandardCharsets.UTF_8);
        } catch (Exception e) {
            EmiSearchPlus.LOG.error("Falha ao ler {}", p, e);
        }
    }
}
