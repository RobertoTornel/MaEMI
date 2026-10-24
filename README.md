# Not Enough Items — Fabric/Forge (1.20.1)

A faithful, from-scratch port of **Not Enough Items**' feature set to Minecraft **1.20.1**, built as a
**bolted-on addon for [EMI](https://github.com/emilyploszaj/emi)**. EMI is never forked or patched;
everything lives in this mod and talks to EMI through its API (plus a few additive client mixins where
EMI exposes no hook).

The project is multiloader through **Architectury**: `common` holds all logic and UI, `fabric` and
`forge` supply the loader glue.

## Features (work in progress)

- NEI item panel: pagination, item counts, hidden items, sorting, configurable side/rows/columns
- NEI search language: `@mod`, `$tag` (ore dictionary), `#tooltip`, `&id`, `-`/`!` negation,
  `|`, `{}` grouping, quotes, `*`/`?` wildcards and `r/.../` regex
- Item subsets, bookmarks and hidden-item lists, persisted as JSON
- Autocrafting (plan + execute from the player inventory) — the headline feature
- Utility buttons (time, weather, gamemode, heal, trash) and NEI-style cheat actions
- Automatic mod compatibility modules that disable themselves when their mod is absent
- Cloth Config settings screen

## Building

```bash
./gradlew build        # or: gradle build
```

Artifacts land in `fabric/build/libs` and `forge/build/libs`.

## Requirements

- Minecraft 1.20.1
- [EMI](https://modrinth.com/mod/emi) 1.1.20+
- Cloth Config 11+ (optional but recommended)
