# MaEMI for 1.20.1 — Not Enough Items, bolted onto EMI

MaEMI (Not Enough Items) was the recipe viewer and item panel of 1.7.10 modpacks. It cannot be compiled
for 1.20.1: it is built on `GuiContainerManager`, ASM transformers, numeric item IDs and a packet
channel that all disappeared. This project ports **MaEMI's features and workflows** to Minecraft
1.20.1 by building on [EMI](https://github.com/emilyploszaj/emi/) instead of replacing it: EMI keeps
owning recipe indexing and its own panels, and MaEMI adds the parts EMI never had.

The result is a single jar for **Fabric** and **Forge** (Architectury multiloader), and every
compatibility module turns itself off when the mod it targets is not installed.

## Features

* **Item panel** next to the container GUI, on either side, with configurable columns and rows.
  Item grid, search field with MaEMI's search language, subset tabs, page label, hover tooltips.
* **MaEMI search syntax**: plain name search, quotes for literals, `|`, `-`, `!`, groups with
  `{ }`, wildcards `*` and `?`, regular expressions `r/.../`, and the prefixes
  `@mod` (mod name/id), `$tag` (item tag), `#tooltip` and `&identifier`.
* **Item subsets**: `MaEMI` (everything), `Bookmarks`, and any number of user subsets created
  from the panel (right-click a tab to add one; shift+right-click a user tab to rename or delete
  it), stored in `config/nei-subsets.json`. Narrow panels shorten tab labels and show the full
  name as a tooltip instead of hiding tabs.
* **Bookmarks**: named groups plus a "recently looked at" history, in
  `config/nei-bookmarks.json`. Right-click a panel item to bookmark it.
* **Hidden items**: `H` hides the hovered item; the list lives in `config/nei-hidden.json`.
* **Recipe and use lookup**: clicking a panel item with the middle mouse button opens its recipes;
  `R` and `U` work on the stack under the panel cursor. EMI handles the actual recipe screens.
* **Machine lookups ("?" button)**: opens the recipe category whose workstation is the held item,
  or the block you are looking at, for any mod that registers workstations with EMI.
* **Autocrafting**: `C` plans how to obtain the hovered item from what you carry, expands the
  recipe tree (inputs first, cycle-safe), then runs the plan through EMI's own recipe filler. It
  obeys the open screen's rules and stops with a clear message when a step cannot be performed.
  Progress and errors are drawn at the bottom of the screen and copied to chat, so they are readable
  while the GUI is open.
* **Cheat/utility actions**: creative give (click), trash, heal, time, weather, gamemode, enchant,
  spawn, each permission-checked. Right-click the "?" button to open the utility screen.
* **Tooltip augmentation**: registry id, mod name, stack size, tags and (optional) NBT.
* **Data dumps**: `F8` writes items, recipes and tags to `config/nei-dump/` for pack debugging.
* **Cloth Config** screen (`P`, or ModMenu), with a plain fallback screen when Cloth Config is
  absent.

Missing on purpose: MaEMI's ghost-recipe overlay, transfer-rectangle arrows inside machine GUIs and
its per-handler tab configuration. Those rely on MaEMI's own GUI framework; EMI performs crafting
instead of previewing it. See [docs/PORTING.md](docs/PORTING.md).

## Requirements

| | Fabric | Forge |
| --- | --- | --- |
| Minecraft | 1.20.1 | 1.20.1 |
| Loader | Fabric Loader 0.15+ | Forge 47+ |
| EMI | 1.1.20+ | 1.1.20+ |
| Cloth Config | optional, 11+ | optional, 11+ |
| ModMenu | optional | — |

Client only. The jars are in `fabric/build/libs` and `forge/build/libs`.

Both jars are built against **EMI 1.1.22** (the version shipped by Monifactory) and accept EMI
1.1.20 or newer. The Fabric and Forge dev clients were both run on EMI 1.1.22: MaEMI initialises,
the EMI plugin registers, the panel renders next to the inventory, typing filters the grid, the
mouse wheel pages it, and `O` both hides and restores the panel from inside a GUI. Forge
47.2.0 shows zero mixin failures. Autocrafting execution is accepted by EMI's filler for recipes
the open screen supports; see the limitations below before promising a full GregTech chain.

## Default keys

| Key | Action |
| --- | --- |
| `O` | show/hide the item panel |
| `B` | switch to the bookmarks view |
| `C` | autocraft the hovered item |
| `P` | open the settings |
| `H` | hide the hovered item |
| `F8` | dump items/recipes/tags |

All bindings are in `Options → Controls → Not Enough Items`. They also work while a container GUI
is open (vanilla key bindings do not), and are suppressed while the panel search field has focus.

## Compatibility

EMI already unifies recipe display for most 1.20.1 mods, so MaEMI's compatibility layer focuses on
things EMI leaves to the pack: namespace-only item subsets for large mods (GregTech, AE2, Create,
Mekanism, Thermal, Immersive Engineering, Botania, Ars Nouveau, Tinkers', Farmer's Delight, Modern
Industrialization, Powah, Ad Astra) and data-driven tooltips. A module whose required mod is missing
is skipped before its classes are touched, and the reason is logged. See
[docs/COMPAT.md](docs/COMPAT.md).

## Known limitations

* **Autocrafting runs through EMI's recipe handlers.** It can fill any screen EMI can fill (player
  crafting grid, crafting table, and machines whose mod ships an EMI handler). GregTech machines and
  other blocks without a fillable handler are planned but cannot be executed automatically; the
  service reports the step it cannot perform instead of dropping items on the floor.
* **Ghost recipe preview** on the crafting grid is not implemented. EMI's recipe screen and its own
  craft button cover the workflow; MaEMI's overlay rendered the recipe in-place.
* **The panel is built for container screens.** `showEverywhere` extends it to screens that call
  `super.render`, but screens that draw over it can cover the panel, and only screens that call
  `super` for input receive clicks.
* Compatibility subsets are namespace-based, matching MaEMI's per-mod item list rather than its
  hand-written handler blacklists.

## Building

```bash
./gradlew build          # both loaders
./gradlew :fabric:build  # one loader
./gradlew :common:test   # pure-JVM tests
```

Java 17 is required to run the game; the build itself is verified with JDK 17 and 21.

## Layout

```
common/   every feature, no loader imports
fabric/   Fabric entrypoint, key bindings, ModMenu integration
forge/    Forge mod class, key mappings, config screen factory
docs/     architecture, feature map, porting notes, compatibility
```

## Licence and credits

LGPL-3.0-or-later. MaEMI was written by ChickenBones and maintained by the GTNH team; this port is an
independent re-implementation of its behaviour on top of EMI by emilyploszaj and contributors. See
[docs/CREDITS.md](docs/CREDITS.md).