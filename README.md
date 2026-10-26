# MaEMI

MaEMI adds Not Enough Items-style crafting conveniences to **Minecraft 1.20.1** on top of
[EMI](https://github.com/emilyploszaj/emi/). It does not replace EMI: EMI still owns the item
index, search, recipe screens and recipe book, and there is no separate MaEMI panel to configure
away. MaEMI supplies the parts EMI does not:

- recursive **autocrafting** from the hovered item
- **tool-aware matching** that reuses tools instead of demanding a fresh copy
- tag-aware ingredient planning
- **AE2 terminal support**, including ME network stock and the terminal crafting grid
- extra tooltips, a data dump and a small settings screen

## Features

### Recursive autocrafting

- Press **C** on an item in EMI or in a container to plan and run the craft.
- The planner walks the recipe tree, crafts intermediates first and orders the steps so every
  ingredient is produced before it is consumed.
- Cycles are detected, planning depth is bounded, and failed branches are abandoned after a
  bounded number of attempts.
- If a craft cannot be completed, the message names the real missing ingredient rather than the
  item you asked for.
- A craft that is already satisfied still adds the requested amount instead of silently doing
  nothing.
- Every recursive step is restricted to recipes the currently open screen can fill, not just the
  first step.
- Plans run on the client tick with a short delay between steps; pressing the key again replaces
  the current plan after returning anything left in the grid.
- The current plan can be cancelled by opening another screen or by starting a new craft.

### Craft amounts

| Keys | Amount |
| --- | --- |
| **C** | 1 |
| **Ctrl + C** | 32 |
| **Shift + C** | 64 |
| **Ctrl + Shift + C** | as many as the current stock supports |

Ctrl+Shift+C is bounded by the `autocraft.maxCrafts` setting (default 512) and the planner's
step limit. The amount is read from the modifiers held when C is pressed.

### Tools and damageable items

- GregTech tools and other damageable items carry generated NBT such as tool stats and damage.
- MaEMI compares those items without their NBT, so a used or specialised tool matches the plain
  tool a recipe asks for.
- A tool is a **requirement**, not a consumed ingredient: one copy can satisfy every step in the
  plan.
- Tool recipes with multiple batches are split into single crafts so the planner never asks for
  one tool per batch.
- Candidate ingredients already in stock, or directly craftable from stock, are preferred over
  obscure variants.

### Tags and ingredient choice

- Recipes that accept a tag such as `#minecraft:planks` or `#forge:tools/hammers` are matched
  against every member of the tag.
- Candidates are scored by whether they are in stock, whether their own recipe is shallow-craftable
  from stock, and then by recipe order.
- This avoids choosing an obscure modded variant when a stocked, ordinary ingredient is available.

### Leftovers and cleanup

- When a plan finishes, fails, is cancelled or is replaced, items left in the crafting grid are
  quick-moved back to the player inventory.
- Items held on the cursor are placed back into an empty player-inventory slot.
- Vanilla crafting grids and AE2 crafting-terminal grids are handled.

### AE2 terminals

- Hovering an item in an AE2 storage or crafting terminal works even when EMI has no cached hover:
  MaEMI falls back to the focused slot and then to the slot under the cursor.
- The ME network counts as stock even when AE2's **expose network inventory to EMI** option is off
  (the default). MaEMI reads the terminal's synced client repo directly.
- Items in the terminal's own crafting grid count as stock.
- Autocrafting in an AE2 crafting terminal transfers each recipe into the grid, takes the output,
  and repeats for the requested amount. This uses AE2's own terminal handler rather than injecting
  items.
- AE2 is optional. If it is absent, none of this is loaded and nothing else changes.
- Autocrafting needs a terminal with a crafting grid. A storage terminal can be hovered, but pressing
  C there reports that the screen cannot fill a recipe; use a crafting terminal or a wireless
  crafting terminal.

### Screens and mod support

- Vanilla crafting table, player inventory and crafting-station screens accept recipes that fit
  their grid.
- Machine and terminal screens accept recipes that their own EMI recipe handler can fill; open the
  machine and press C there.
- EMI's recipe browser is not a crafting screen. Pressing C there reports the wrong screen instead
  of pretending to craft.
- Any mod that supplies a standard EMI recipe handler can be used; the screen decides what is
  possible.

### Hover and tooltips

- Hover lookup checks EMI's cached hover, the scaled cursor position, vanilla's focused slot, and
  finally the container slot under the cursor.
- Extra tooltip lines show the registry id, mod name, stack count, tags and selected NBT data.
- Tooltip limits and individual line groups are configurable.

### Configuration and diagnostics

- **P** opens the settings screen.
- Cloth Config 11+ provides the normal config UI when installed; without it MaEMI opens a built-in
  fallback screen.
- Settings live in `config/nei.json` and include autocraft depth, maximum craft amount, tooltip
  options, compatibility report logging and general toggles.
- **F8** writes a data dump for bug reports to `config/nei-dump/`: item ids, recipes, tags and
  related metadata.
- A compatibility report is printed to the log at startup by default, naming detected and missing
  supported mods.

### Loader support

- One jar per loader: Fabric and Forge.
- The Fabric jar is client-only metadata.
- The Forge jar is runtime-guarded to the client; it does not add server content.
- No blocks, items, entities, world generation, networking or server-side recipes are added.

### Supported Minecraft versions

| Minecraft | Fabric | Forge | Release branch |
| --- | --- | --- | --- |
| 1.20.1 | supported | supported | `main`, `1.20.1` |
| 1.20.2 | supported | supported | `1.20.2` |
| 1.20.4 | supported | — | `1.20.4` |
| 1.20.6 | supported | — | `1.20.6` |
| 1.21.1 | supported | — | `1.21.x`, `1.21.1` |

See [version support notes](https://github.com/RobertoTornel/MaEMI/blob/main/docs/VERSIONS.md)
for the full matrix and branch layout.

## Dependencies

### Required

| Dependency | Version | Notes |
| --- | --- | --- |
| Minecraft | 1.20.1 | Forge range is `[1.20.1,1.20.2)` |
| Java | 17 or newer | The project is built with Java 21 |
| EMI | 1.1.20 or newer | Tested with EMI 1.1.22+1.20.1 |
| Fabric Loader | 0.15.0 or newer | Fabric jar only |
| Fabric API | Matching 1.20.1 build | Fabric jar only; tested with 0.92.2+1.20.1 |
| Forge | 47.x | Forge jar only; tested with 47.2 and 47.4 |

EMI is the only mod MaEMI cannot run without. MaEMI is client-side; installing it on a dedicated
server does nothing useful.

### Optional

| Dependency | Version | Purpose |
| --- | --- | --- |
| Cloth Config | 11.0.0 or newer | Normal settings UI |
| Mod Menu | 7.0.0 or newer | Fabric mod-list config entry |
| Applied Energistics 2 | 15.x for 1.20.1 | AE2 terminal stock, hover and crafting-grid support |

Without Cloth Config, MaEMI uses its built-in fallback settings screen. Mod Menu is only a shortcut
to the settings screen on Fabric. AE2 support is entirely optional.

### Detected mod integrations

These are optional detection targets, not hard dependencies. A module is enabled only when the
target mod is present; missing modules are skipped and listed in the startup compatibility report.
They do not change the requirement that EMI must be installed.

| Mod | Detected id(s) |
| --- | --- |
| GregTech CEu | `gtceu` |
| Applied Energistics 2 | `ae2` |
| Create | `create` |
| Mekanism | `mekanism` |
| Thermal | `thermal`, `thermal_extra` |
| Immersive Engineering | `immersiveengineering` |
| Botania | `botania` |
| Ars Nouveau | `ars_nouveau` |
| Tinkers' Construct | `tconstruct` |
| Farmer's Delight | `farmersdelight` |
| Modern Industrialization | `modern_industrialization` |
| Powah | `powah` |
| Ad Astra | `ad_astra` |

## Keybinds

| Key | Action |
| --- | --- |
| **C** | Autocraft 1 of the hovered item |
| **Ctrl + C** | Autocraft 32 |
| **Shift + C** | Autocraft 64 |
| **Ctrl + Shift + C** | Autocraft as many as possible |
| **P** | Open MaEMI settings |
| **F8** | Write the data dump |

The retired MaEMI panel, bookmark and hide actions are not registered, so they cannot steal keys
from EMI or other mods.

## Installation

1. Install EMI, plus Fabric API on Fabric.
2. Download the jar for your loader:
   - `maemi-fabric-1.0.1.jar` for Fabric
   - `maemi-forge-1.0.1.jar` for Forge
3. Put exactly one of them in `mods/`. Do not install both loader jars in one instance.
4. Launch the game. The first launch creates `config/nei.json`.
5. Optionally install Cloth Config for the normal settings UI and Mod Menu for the Fabric config
   button.

## Building

Requirements: Java 21 and the Gradle wrapper.

```bash
./gradlew build
```

The loader jars are written to:

- `fabric/build/libs/maemi-fabric-1.0.1.jar`
- `forge/build/libs/maemi-forge-1.0.1.jar`

## Limitations

- Autocrafting uses the open screen's EMI handler. A recipe that the screen cannot fill is not
  planned, even if another machine elsewhere could make it.
- Machines are not automated remotely; the player must open the machine or terminal and press C.
- AE2 stock and output handling depend on AE2's client terminal APIs. AE2 is optional and is not
  required to use MaEMI.
- MaEMI does not add server-side recipe processing, items or world content.
- The data dump is diagnostic output, not an import format.

## Links

- [Source and issues](https://github.com/RobertoTornel/MaEMI)
- [Latest release](https://github.com/RobertoTornel/MaEMI/releases/latest)
- [Architecture notes](https://github.com/RobertoTornel/MaEMI/blob/main/docs/ARCHITECTURE.md)
- [Tooling and Modrinth upload](https://github.com/RobertoTornel/MaEMI/blob/main/docs/TOOLING.md)
- [Credits](https://github.com/RobertoTornel/MaEMI/blob/main/docs/CREDITS.md)

## License

MaEMI is licensed under the **GNU Lesser General Public License v3.0 or later**
(LGPL-3.0-or-later). See [LICENSE](https://github.com/RobertoTornel/MaEMI/blob/main/LICENSE).