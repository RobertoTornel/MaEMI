# Architecture

## Goals and constraints

1. **Port MaEMI's functionality, not its 2013 plumbing.** MaEMI's 1.7.10 code depends on
   `GuiContainerManager`, ASM transformers, Item IDs and a packet channel that simply do not exist
   on 1.20.1. Everything is re-implemented against modern Minecraft and EMI.
2. **EMI is the host, never a fork.** We depend on the published `dev.emi:emi-*:1.1.22+1.20.1`
   artifact, register through `dev.emi.emi.api.*`, and keep the handful of mixins that reach into
   EMI or vanilla internals in one place. No EMI source is copied or patched.
3. **Multiloader by construction.** `common` holds every feature. `fabric` and `forge` only provide
   the loader seam (entrypoint, key bindings, config screen registration). Common code never imports
   a loader API.
4. **Compatibility is discovered, not assumed.** Every integration is a `CompatModule` with a list
   of required mod ids. A module whose mods are missing is disabled before it can load a class.

## Module map (`common`)

| Package | Responsibility |
| --- | --- |
| `dev.nei` | Mod bootstrap, client singleton, key bindings |
| `dev.nei.platform` | The only loader-facing interface (`NeiPlatform`, `NeiKeybind`) |
| `dev.nei.config` | JSON config, Cloth Config screen, fallback screen |
| `dev.nei.search` | MaEMI's search language parser and filters (pure Java, unit tested) |
| `dev.nei.panel` | Item panel rendering and input |
| `dev.nei.item` | Item subsets |
| `dev.nei.bookmark` | Bookmark groups |
| `dev.nei.cheat` | Cheat and utility actions |
| `dev.nei.api` | Public API for addons (recipe handlers, GUI handlers, filters) |
| `dev.nei.recipe` | Recipe handler registry and vanilla handlers |
| `dev.nei.autocraft` | Craft planning and execution |
| `dev.nei.compat` | Auto-discovered mod compatibility modules |
| `dev.nei.integration` | Glue to EMI internals that is too small for its own subsystem |
| `dev.nei.tooltip` | Tooltip augmentation |
| `dev.nei.dump` | Data dumps |
| `dev.nei.hook` | Thin entry points called by mixins |
| `dev.nei.mixin` | Additive client mixins |

## Loader seam

`NeiPlatform` exposes exactly what differs between Fabric and Forge:

- `isModLoaded` / `loaderName` / `isDevelopmentEnvironment`
- `configDirectory`
- `sendCommand` (chat commands used by utility buttons)
- `openConfigScreen`
- `registerKeybind` / `matchesKeybind` (the latter lets the panel react to keys while a GUI is open,
  where vanilla key bindings do not fire)

Both platforms call `Nei.init(platform)` and `NeiClient.init(platform)` from their client-only
initialiser. The Forge mod class checks `FMLEnvironment.dist == Dist.CLIENT` before touching any
client class, so a dedicated server can load the jar without linking `Screen`.

## Mixins

Mixins are additive and confined to the client:

- `HandledScreenMixin` — draws the panel after the container screen finished rendering and offers
  it input first (new `?tid` methods; never cancels ordinary screen behaviour unless the panel
  actually consumed the event).
- `HandledScreenAccessor` — `x`, `y`, `backgroundWidth`, `backgroundHeight`, `handler`,
  `focusedSlot`, needed to position the panel and to find the hovered slot.
- `ScreenMixin` — optional (`showEverywhere`) rendering/input on non-container screens.
- `KeyboardMixin` / `MouseMixin` — `Screen.charTyped` and `Screen.mouseScrolled` are interface
  defaults, which Mixin cannot inject into on Forge. The hooks sit in `Keyboard#onChar` and
  `Mouse#onMouseScroll` instead, so both loaders see panel typing and scrolling.
- `ItemStackTooltipMixin` — appends mod/id/tag/NBT/spawner lines to item tooltips.

No mixin targets EMI unless a feature genuinely cannot be reached through the API; when one does, it
is documented here with the EMI version it was verified against.

## Input routing

Vanilla key bindings do not fire while a screen is open. `NeiHooks.keyPressed` is called first by
`HandledScreenMixin`; it gives the panel the event, then asks `NeiKeybinds.handleKeyPressed`, which
compares the event against the loader registrations through `NeiPlatform.matchesKeybind`. When no
screen is open, `NeiKeybinds.tick` drains the normal bindings instead. A press handled by the screen
is still drained in `tick` so it cannot fire a second time when the player closes the GUI. Text typed
into a focused search field is consumed by the panel before any action key is considered.

## Feedback

`dev.nei.util.Messages` writes to chat history and to `NeiPanelState.status`, and the panel draws that
status line at the bottom of the screen. The HUD (including chat) is hidden behind container screens,
so an action-bar message alone would be invisible exactly when autocrafting or subsets need to
report something.

## Persistence

| File | Contents |
| --- | --- |
| `config/nei.json` | all settings |
| `config/nei-subsets.json` | item subsets |
| `config/nei-bookmarks.json` | bookmark groups |
| `config/nei-hidden.json` | hidden stacks |

All files are written atomically (temporary file + move) and tolerate unknown/missing fields.

## Threading

- Panel filtering is cached and recomputed only when the query, subset, sort or page source changes.
- Dumps and icon extraction run on a single daemon worker; the render thread only reads finished
  results.
- Autocrafting executes on the client thread because it manipulates the open screen handler through
  `ClientPlayerInteractionManager`; a plan is computed up-front so the work per tick is bounded.

## Testing

`common/src/test` contains pure-JVM tests (JUnit 5) for the search language, subset/bookmark
persistence, the compatibility enable/disable matrix and the autocraft planner. None of them start
Minecraft, which keeps CI fast.