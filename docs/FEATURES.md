# NEI feature map

Status legend: **ported** = behaves like NEI within 1.20.1 limits; **adapted** = same workflow, different
UI because EMI owns the screen; **not ported** = deliberately left to EMI or impossible without
forking it.

| NEI feature (2.8.44-GTNH) | Status | Where |
| --- | --- | --- |
| Item panel beside the GUI | ported | `dev.nei.panel` |
| Panel side, columns, rows, scale | ported | `NeiConfigData.Panel`, Cloth screen |
| Panel search bar | ported | `NeiPanelRenderer`, `NeiPanelInput` |
| NEI search language | ported | `dev.nei.search` (unit tested) |
| Item subsets (create/rename/delete, add/remove items) | ported | `dev.nei.item` |
| Subset persistence | ported | `config/nei-subsets.json` |
| Bookmarks + bookmark groups | ported | `dev.nei.bookmark` |
| Bookmark persistence and history | ported | `config/nei-bookmarks.json` |
| Hidden items | ported | `HideList`, `H` |
| Item IDs, mod names, tags in tooltips | ported | `dev.nei.tooltip` |
| Recipe lookup (R) | adapted | EMI recipe screens; panel forwards R |
| Use lookup (U) | adapted | EMI recipe screens; panel forwards U |
| Machine "?" lookup | adapted | `WorkstationLookup` uses EMI workstations |
| Recipe categories and tabs | adapted | EMI categories/tabs |
| Recipe page layout/paging | adapted | EMI recipe screens |
| Ghost recipe / overlay rendering | not ported | no EMI API; EMI crafts instead |
| Transfer rectangles inside machine GUIs | not ported | no EMI API |
| Per-handler tab metadata and ordering | not ported | EMI categories are code-defined |
| Handler hiding (hiddenhandlers.cfg) | not ported | see `removeRecipes` in plugin API |
| Recipe filters | adapted | plugins can `removeRecipes(Predicate)` |
| Autocrafting / craft tree | ported | `dev.nei.autocraft` |
| Craft-to-inventory button | adapted | `C` runs the plan via EMI's filler |
| Cheat mode: give item | ported | panel left click, `Cheats` |
| Cheat mode: delete hovered | ported | utility screen |
| Heal / time / weather / gamemode | ported | utility screen |
| Enchant / spawn mob | ported | utility screen |
| Item dump to text files | ported | `DumpService`, `F8` |
| Key bindings | ported | loader key mapping, `NeiKeybinds` |
| Config GUI | ported (Cloth Config) | `NeiConfigScreen`, fallback screen |
| Profiler overlay | not ported | vanilla has a profiler |
| Enchantment/potion helper tabs | not ported | outside the request scope |
| Loader compatibility modules | ported (new) | `dev.nei.compat` |

## Behaviour notes

* The panel never steals clicks outside its bounds; EMI's panels keep working.
* `showEverywhere` exists but the panel is only rendered for container screens. Rendering it over
  EMI's recipe screens needs an input hook for non-container screens and is tracked in
  [PORTING.md](PORTING.md).
* Autocrafting requires an open screen whose handler can craft the recipe (the player inventory for
  2×2, a crafting table for 3×3). It refuses to start rather than consuming items it cannot finish.
* Cheat actions are only offered when the player is in creative or has permission level 2; the client
  never mutates the inventory directly except through `clickCreativeStack`.
