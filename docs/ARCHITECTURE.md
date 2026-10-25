# Architecture

MaEMI is an Architectury multiloader project. EMI stays the recipe index and screen renderer; this
mod only adds behaviour through public EMI APIs and a few additive client mixins.

## Modules

| Path | Responsibility |
| --- | --- |
| `common` | Almost all logic: planner, EMI plugin, tool detection, recipe index, config, tooltips, dump |
| `fabric` | Fabric entrypoints, key registration, Cloth Config/Mod Menu wiring, Fabric metadata |
| `forge` | Forge entrypoints, key registration, Forge event wiring, `mods.toml` |

`common` must not reference loader classes. Loader-specific code lives behind the small
`dev.nei.platform` interfaces and is supplied at client init.

## Startup

Both loaders call `NeiClient.init(platform)`, which loads `config/nei.json` and stores the platform
adapter. EMI then calls `NeiEmiPlugin.register(EmiRegistry)` from its own entrypoint. Registration
installs:

1. **Tool comparisons** — iterates the item registry and gives tools/damageable items an
   NBT-insensitive default comparison. This is done before EMI builds its `byOutput` index, so
   lookup on a used tool finds its recipe.
2. **Recipe index helpers** — `RecipeRegistry` keeps the tool-output fallback index used only when
   EMI has no exact output match for a tool.
3. **Compatibility modules** — `NeiCompat` evaluates each module against loaded mods and skips
   missing targets before class-loading module code.
4. **Debris cleanup** — no custom panel is registered. The old panel classes remain in the source
   tree for config compatibility but are not rendered and do not intercept input.

## Autocrafting pipeline

```
C key (+ Ctrl/Shift modifiers)
  -> HoveredStack.get()          cached EMI hover, scaled cursor hover, focused slot
  -> AutoCraftService.requestCraft
       -> RecipeRegistry.recipesFor(target)
       -> canFillIn(recipe, currentScreen)
       -> CraftPlanner.stockForScreen(screen, recipes)
       -> CraftPlanner.planAdditional / planAll
       -> execution in AutoCraftService.tick()
```

### Screen filter

`canFillIn` is the contract with the current screen:

* `InventoryScreen` / `CraftingScreen`: only `minecraft:crafting` (or equivalent) categories, and
  only recipes whose input count fits the 2x2/3x3 grid.
* Other `HandledScreen`s: the recipe must have a valid `EmiRecipeHandler` for that screen. Machine
  screens therefore accept their own recipes and nothing else.

The same predicate is passed into the planner and applied to **every** recursive step, not only
the requested item. That is what prevents an extruder step being planned while a crafting table is
open.

### Screen stock

For AE2 screens, `stockForScreen` reads the terminal's client `repo` reflectively and converts every
`AEItemKey` entry to an `EmiStack` with its stored amount. That is necessary because AE2's
"expose network inventory to EMI" option defaults to off, which would otherwise hide the network
from the planner. Player inventory, the terminal's crafting grid and the cursor are added on top.
If the repo cannot be read (a different AE2 version), the code falls back to the screen's EMI
handler and its `EmiPlayerInventory`.

For all other screens the EMI handler is the source of starting stock, with the no-handler fallback
counting the player inventory and vanilla crafting grid. Standard handlers already include their
crafting grid in `getInputSources`, so the grid is not added a second time for them.

The hover fallback in `HoveredStack` uses `HandledScreen.focusedSlot` after EMI's cached and
cursor lookups, so item lists that are made of slots (AE2's terminal repo) are hoverable even when
EMI has no widget hover for them.

### Planner

`CraftPlanner` is a depth-first, backtracking planner over `EmiRecipe` objects.

* **Stock keys** identify what is actually owned. Non-tools use `StackIO.key`, which includes NBT
  material data for GT items. Tools use `tool:<id>` and are never consumed by planning.
* **Stock accounting** consumes inputs from a working map. Failed recipe branches are fully rolled
  back (stock, steps and missing set) before the next candidate is tried.
* **Tag inputs** are expanded to every `EmiStack` candidate. Candidates are scored once:
  direct stock, then shallow craftability (all of one recipe's inputs are in stock), then any
  recipe. Ties prefer the target's tool material, then quantity, then id.
* **Tools** are recognised by `ToolIdentity` from damageability, `GT.Tool` NBT and GregTech tool
  name suffixes. A tool requirement is reduced to one copy and reused at every depth.
* **Batches** are computed from output amount. Recipes with a reusable tool input are emitted as
  separate one-item steps, because EMI's filler would otherwise require multiple copies of the
  tool.
* **Amounts** — C plans one, Ctrl+C 32, Shift+C 64 and Ctrl+Shift+C uses `planAll`, which
  probes the cap and then binary-searches the largest amount whose plan has no missing inputs.
* **AE2 execution** — AE2's EMI handler only transfers a recipe into its terminal grid and does not
  take the output. For handlers in `appeng.*`, the executor calls `craft` directly (bypassing the
  player-inventory-only `canCraft` check), waits for the grid, quick-moves the output slot, and
  repeats once per batch. This also lets the plan use network stock without changing AE2's config.
* **Ordering** — after planning, steps are topologically sorted by produced/consumed stock keys so
  an ingredient is always made before the step that consumes it. Independent steps keep planner
  order.
* **Limits** — `MAX_STEPS`, `MAX_ATTEMPTS`, configured depth and cycle detection stop pathological
  trees. Missing leaves are reported as the real missing item, not the requested item.

### Execution

`AutoCraftService.tick()` runs one step at a time with a short delay. Before filling it re-resolves
the handler for the current screen and calls `EmiRecipeFiller.performFill` with the step's batch
count. Failures are retried a bounded number of times; when the plan ends, fails or is cancelled,
`returnCraftingItems()` shift-clicks crafting-grid slots (including AE2's `CraftingMatrixSlot`) and
cursor contents back into the player inventory.

## Tooltips and dump

`ItemStackTooltipMixin` appends `TooltipLines` output. `DumpService` writes EMI's item and recipe
lists to `config/nei-dump/` on a daemon thread, using a temp file and atomic move.

## Constraints

* EMI's source is never modified and no EMI internals are reimplemented.
* No custom recipe index is authoritative: EMI's index is queried first, with a tool-only fallback.
* Client-only. Dedicated servers must never load GUI or client classes.