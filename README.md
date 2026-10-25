# MaEMI

MaEMI brings Not Enough Items-style crafting conveniences to **Minecraft 1.20.1**, on top of
[EMI](https://github.com/emilyploszaj/emi/). It is not a replacement recipe browser: EMI still owns
the item index, search, recipe screens and recipe book. MaEMI adds the parts EMI does not do on its
own, in one jar per loader:

* recursive **autocrafting** from the hovered item, using the player's inventory
* **tool-aware recipe matching** for GregTech and vanilla tools, so a damaged or NBT-carrying tool
  matches the plain tool a recipe asks for and is reused instead of remade
* tag-aware planning that prefers ingredients you can actually make from what you have
* extra tooltip lines for mod, item id, tags and NBT
* a searchable data dump for bug reports

There is no custom item panel. MaEMI deliberately uses EMI's own index, so there is nothing to
configure away and no second UI competing for the mouse or keyboard.

## Requirements

| Loader | Requirements |
| --- | --- |
| Fabric | Minecraft 1.20.1, Fabric Loader 0.15+, Fabric API, EMI 1.1.20+ |
| Forge | Minecraft 1.20.1, Forge 47+, EMI 1.1.20+ |

Cloth Config 11+ is recommended for the settings screen. Mod Menu is optional on Fabric. MaEMI is
client-side only; it does nothing on a dedicated server.

## Installation

1. Install EMI (and Fabric API on Fabric) as usual.
2. Download the jar for your loader from the [latest release](https://github.com/RobertoTornel/nei-fabric/releases/latest):
   * `maemi-fabric-1.0.0.jar` for Fabric
   * `maemi-forge-1.0.0.jar` for Forge
3. Put exactly one of them in `mods/`. Do not install both loaders' jars in one instance.
4. Launch the game. The first launch creates `config/nei.json`.

## Autocrafting

Hover an item in EMI (or in a container) and press **C**. MaEMI plans the craft and fills the
supported slots for you.

What the planner does:

* **Crafts one more than you already have.** Pressing C on an item you own plans another copy
  instead of silently doing nothing.
* **Works backwards through the recipe tree.** Missing plates, sticks, screws and similar
  intermediates are planned first, up to the configured depth.
* **Uses your real inventory**, including the crafting grid and the stack on the cursor. It does
  not invent items.
* **Reuses tools.** A hammer, file, screwdriver, wire cutter, saw or other tool is required, not
  consumed: one copy satisfies every step. GregTech tools are recognised even though their
  durability lives in `GT.Tool` NBT rather than vanilla damage.
* **Handles tag ingredients.** A recipe that accepts `#minecraft:planks` or `#forge:tools/hammers`
  is matched against every member of the tag. Candidates already in stock, or whose recipe is
  entirely satisfiable from stock, are tried before obscure modded variants.
* **Splits tool recipes into single crafts.** EMI would otherwise ask for one tool per batch;
  MaEMI plans those recipes one at a time so the same tool is reused.
* **Returns leftovers.** When the plan finishes, fails or is cancelled, items left in the crafting
  grid and on the cursor are moved back to the player inventory.
* **Stops cleanly.** If something genuinely cannot be made, the chat message names only the real
  missing ingredient, not the item you asked for.

### Which screens work

The recipe must be fillable in the screen that is open:

* vanilla crafting table, player inventory and crafting-station screens accept crafting recipes
  that fit their grid
* a machine screen accepts the recipes its own EMI recipe handler can fill; open the machine and
  press C there
* EMI's recipe browser is not a crafting screen, so C there reports that the current screen is
  wrong instead of pretending to craft

Only recipes that can run in the current screen are used, including for every intermediate step.

## Tool-aware recipe matching

EMI normally compares item NBT. GregTech tools carry generated stats and damage in NBT, so an
inventory hammer does not equal the plain hammer a recipe shows. MaEMI registers an NBT-insensitive
comparison for tools and damageable items and recognises GregTech tool ids, which makes:

* recipe lookup on a used tool work
* the tool match its recipe ingredient
* tool outputs match the hovered tool
* one tool satisfy a plan that needs it repeatedly

This is generic: it is driven by item identity, damageability, `GT.Tool` NBT and tool name
patterns. No pack-specific item list is involved.

## Tooltips

With tooltips enabled, item tooltips gain configurable lines:

* mod display name
* registry id (`namespace:path`)
* stack count and maximum stack size
* item tags
* NBT keys and values (truncated for readability)

Tags and NBT are off by default because they are noisy in large packs.

## Keys

| Key | Action |
| --- | --- |
| **C** | Autocraft the hovered item |
| **P** | Open MaEMI settings |
| **F8** | Dump EMI's item and recipe index to `config/nei-dump/` |

The retired panel, bookmark, subset and cheat keys are not registered, so they do not take keys
away from EMI or other mods.

## Configuration

Open the settings with **P**, or from Mod Menu on Fabric. The file is `config/nei.json`.

Useful sections:

* **Autocraft** — planning depth and related options.
* **Tooltip** — enable/disable the extra lines, mod name, registry name, tags, NBT and stack count.
* **General** — master toggle and compatibility logging.

The old panel/search/cheat sections are kept so existing config files keep loading; they no longer
drive any UI in this build.

## Data dump

Press **F8** to write text dumps of EMI's index to `config/nei-dump/`. The dump runs on a
background thread and is written atomically. It is intended for bug reports: it shows exactly which
items, tags and recipes the game sees.

## Compatibility

MaEMI reads what is installed and disables compatibility modules whose target mods are absent. It
does not require GregTech: the tool handling is generic, and packs without GT simply do not exercise
the GT paths. It is designed to sit alongside other EMI addons rather than replace them.

## Limitations

* MaEMI cannot craft a recipe in a screen that cannot fill it. Machine recipes need the machine's
  own screen; EMI's recipe browser is read-only.
* Planning is bounded by the configured depth and internal step limit, so extremely deep or cyclic
  recipe chains stop with a message instead of looping forever.
* The custom NEI panel from 1.7.10 is intentionally not reproduced; EMI's index is the item panel.

## Building from source

```bash
git clone https://github.com/RobertoTornel/nei-fabric.git
cd nei-fabric
./gradlew build
```

The jars are written to:

* `fabric/build/libs/maemi-fabric-1.0.0.jar`
* `forge/build/libs/maemi-forge-1.0.0.jar`

A Java 21 JDK is recommended; the build targets Java 17 bytecode.

## Docs and licence

* [Architecture and internals](docs/ARCHITECTURE.md)
* [Credits and third-party notices](docs/CREDITS.md)

MaEMI is licensed under **LGPL-3.0-or-later**. EMI remains a separate project under its own
licence; MaEMI does not modify EMI's code.
