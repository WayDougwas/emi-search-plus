<p align="center"><img src="src/main/resources/emisearchplus_logo.png" width="96" alt="EMI Search Plus"></p>

<h1 align="center">EMI Search Plus (EMIS+)</h1>

<p align="center">A client-side add-on for <a href="https://github.com/emilyploszaj/emi">EMI</a> that turns its search bar into a real query language and adds a search bar to the recipe screen.<br>Minecraft 1.21.1 · NeoForge · EMI 1.1.24+</p>

---

## Features

- **Extended+ syntax** (from GT New Horizons' NEI): `{ }` groups, `|` OR, `-` NOT, `r/regex/`, `"quotes"`, and an optional explicit `&` AND mode.
- **Recipe search bar**: `<inputs`, `>outputs`, `^catalysts`, `!category`, `?inv`. Matching slots are highlighted.
- **More prefixes**: `&id`, `%tab`, `~type`, `=whole word`, `$tooltip>number`, and `*` wildcards.
- **Smarter matching**: ignores accents, also searches English names, understands initials, and tolerates typos.
- **Quality of life**: autocomplete, pinned history, result counter, a "search by" menu for the hovered item, a calculator, and an in-game help screen (F1).
- **Configurable** from EMI's own config screen.

## Quick reference

```
iron ingot              both words (space = AND)
{iron|gold} -@minecraft iron or gold, not from Minecraft
=tar                    the whole word "tar" (not Mortar)
tar*  /  *tar           a word starting / ending with "tar"
$"attack damage">=10    a number in the tooltip
<#c:ingots >@create     recipe bar: an input tagged c:ingots, an output from Create
```

Press **F1** in game for the full list, with clickable examples.

## Building

Standard NeoForge [ModDevGradle](https://github.com/neoforged/ModDevGradle) project. Requires JDK 21.

```sh
./gradlew build        # jar in build/libs/
./gradlew runClient    # dev client with EMI
```

Versions (NeoForge, EMI and the mod) are set in `gradle.properties`.

GitHub Actions builds every push. Pushing a tag `v<mod_version>` (for example `v1.6.1`) publishes a release with the jar attached.

## License

[MIT](LICENSE). The search syntax is inspired by the Extended+ mode of GT New Horizons' NotEnoughItems. Built on top of EMI by Emi.
