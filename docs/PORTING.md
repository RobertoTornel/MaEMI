# Porting notes: MaEMI 1.7.10 → 1.20.1 on EMI

This document records what MaEMI actually does, what EMI provides instead, and where the two do not
meet. It is the file to read before adding a feature.

## Why not compile MaEMI?

MaEMI 2.8.44-GTNH depends on:

* `GuiContainerManager` and `GuiContainerManager.getStackMouseOver` — removed in 1.13+.
* ASM transformers (`MaEMITransformer`, `GuiContainerManager.transformer`) — no longer viable.
* Numeric item IDs and `ItemList.itemMap` — replaced by registry ids long ago.
* Its own packet channel and server-side `MaEMIServerConfig` — Fabric/Forge networking differs.
* `IRecipeHandler`/HandlerInfo/GuiRecipe — a complete GUI framework that 1.20.1 mods no longer use.

Porting the code would mean porting the framework, then every handler. EMI already provides the
framework *and* the handlers for modern mods, so this project implements MaEMI's **user-facing
behaviour** on top of EMI instead.

## Mapping

| MaEMI concept | 1.20.1 / EMI |
| --- | --- |
| `IRecipeHandler` + `TemplateRecipeHandler` | `EmiRecipe` / `BasicEmiRecipe` |
| `API.registerRecipeHandler` | `EmiRegistry.addRecipe` (one recipe per output) |
| `HandlerInfo` size | `EmiRecipe.getDisplayWidth/getDisplayHeight` |
| `registerGuiOverlay` (GuiContainer class) | `EmiRegistry.addRecipeHandler(ScreenHandlerType, EmiRecipeHandler)` |
| `IOverlayHandler.craft` | `EmiRecipeHandler.craft(EmiRecipe, EmiCraftContext)` |
| `IRecipeOverlayRenderer` (ghost grid) | no equivalent |
| `RecipeTransferRect` (arrows in machine GUI) | no equivalent |
| `IMaEMIGuiHandler.hideItemPanelSlot` | `EmiRegistry.addExclusionArea` |
| `IStackPositioner` | recipe widgets |
| `IRecipeFilter` | `EmiRegistry.removeRecipes(Predicate)` at registration time |
| `IConfigureMaEMI` | `EmiPlugin` + this mod's `NeiPlugin` |
| `RecipeChainMath` / `AutoCraftingManager` | EMI BoM/recipe tree, plus `dev.nei.autocraft` |
| `Recipe.RecipeId` JSON | `EmiRecipe.getId()` (`Identifier`) |
| `gtFluidName` fake stacks | native `EmiStack` fluids |
| damage `Short.MAX_VALUE` wildcard | `Ingredient`/`EmiIngredient` lists |

## What this mod adds on top of EMI

* An **item panel** (EMI's index is a sidebar; MaEMI's panel is a full grid next to the GUI, with its
  own search and subset tabs).
* **MaEMI search syntax**, including `@` mod, `$` tag, `#` tooltip, `&` id, groups, negation and
  regex. EMI's own search remains untouched.
* **Subsets and bookmarks** as first-class, persistent data.
* **Autocrafting**: a planner that expands the recipe graph from the player's inventory and runs it
  through `EmiRecipeFiller.performFill`, the same path as EMI's craft button.
* **Compatibility modules** that are enabled by mod presence.

## Hooks used

```
HandledScreenMixin   render TAIL; mouseClicked/keyPressed HEAD
HandledScreenAccessor x, y, backgroundWidth/Height, handler, focusedSlot
KeyboardMixin        Keyboard#onChar -> panel typing
MouseMixin           Mouse#onMouseScroll -> panel scrolling
ItemStackTooltipMixin getTooltip RETURN
```

The input layer is deliberate: `Screen.charTyped` and `Screen.mouseScrolled` are interface defaults
(`ParentElement`), and Mixin refuses to inject into interface default methods on Forge. Hooking
`Keyboard#onChar` and `Mouse#onMouseScroll` works identically on both loaders.

## Known gaps

1. **No ghost-recipe overlay.** MaEMI previews a recipe on the crafting grid; EMI only performs it.
   Implementing the preview would need a mixin into EMI's recipe screen and a parallel renderer.
2. **No transfer rectangles inside machine GUIs.** EMI's recipe handlers work on screen handler
   types; showing MaEMI's arrows would require an EMI screen mixin.
3. **Panel on non-container screens.** The panel renders only where input can be routed
   (`HandledScreen`). EMI's recipe screen is a `Screen`, so the panel is hidden there; doing it
   properly needs loader screen events (Fabric `ScreenEvents`, Forge `ScreenEvent`) rather than a
   mixin.
4. **Handler tab ordering/hiding.** EMI has no per-category config; plugins can remove recipes but
   not rename or reorder categories.

## Threading and safety

* Panel filtering is cached; the cache key includes the query, subset, sort mode, hide-list size and
  index size.
* Autocrafting runs one step per client tick, with a two-tick delay, so the screen handler settles
  between fills.
* Every EMI call that can race client startup is wrapped: a missing index yields an empty list, never
  a crash.