# Changelog

## 1.0.15

### Planning can no longer freeze the game

- **The planner is now resumable.** Planning walks the recipe graph: a recipe's inputs, their
  alternatives, and back. That walk used to be recursion, so it had to finish inside one call,
  which is exactly why a big modpack could hold the render thread for 25 seconds. The same three
  steps are now frames on an explicit stack, so the walk can be paused between any two of them
  and resumed on the next client tick.
- **Five milliseconds of work per tick, no more.** A tick is 50 ms and a frame 16, so the game
  cannot stall; a request now shows "working out the plan..." and completes on its own. A
  normal item takes about a second, and the answer is identical to before.
- **Total work is bounded too**, at 1.5 s; past that the mod says the recipe tree was too large
  rather than spinning.
- **Candidate recipes are filtered once per item, not once per parent.** Every parent that needs
  the same ingredient re-checked the same hundreds of recipes; the stock-independent part of
  that check is now cached per plan.

## 1.0.12

### "Craft all" searches for the real maximum

- **Ctrl+Shift+C no longer reports ingredients you are carrying.** The maximum was found by a
  halving ladder: for a cap of 512 it tried 256, 128, 64 and 32, then gave up and returned the
  512 plan -- the one guaranteed to be missing something. It is now a real binary search, so the
  largest amount that can actually be crafted is what gets planned, and the answer is never the
  request it already knew was impossible.
- If not even one can be made, the message describes a single craft's blocker instead of the
  wish list of a five-hundred-fold request.
- **The "carried by the player" count is no longer doubled.** The open screen's menu also lists
  the player's own slots, so a stack of 23 read as 46.

## 1.0.11

### Tools are never crafted

- **A missing tool is now reported as a missing tool.** The planner treated a GregTech file or
  hammer like any other ingredient, so a recipe that wanted a file you did not own produced a
  crafting plan for the file instead. That is where the surprise tools came from, and clamping
  the amount never addressed it: the tool was still a product. A tool the player does not carry
  now stops the plan with "you need a wrought_iron_file", which is the real situation.
- **The closing message says what was actually made.** It previously read the record after
  clearing it and so always claimed nothing was produced -- an instrument that lied.

## 1.0.9

### Autocrafting actually does what it says

- **A step now really crafts.** EMI's fill places the ingredients for every batch but clicks the
  output exactly once, then reports success. A plan that asked for nine items made one and said it
  was finished. Each batch is now filled and checked on its own.
- **Every step is verified before the plan moves on.** The expected output is counted before and
  after a fill; if it never arrives the step is retried and then reported as stuck, instead of
  being counted as done. "Finished" with nothing crafted was the worst possible answer.
- **Items you are carrying are no longer called missing.** Stock identity used to include the
  item's NBT, so a pristine stack from a recipe never matched the tagged copy in your inventory.
  The log shows it plainly: `missing gtceu:bronze_normal_fluid_pipe: 9 carried by the player`.
  Items are matched by id, as EMI itself does.
- **The message names the real obstacle.** A failed plan used to report whichever item the search
  was standing on, usually an intermediate with a perfectly good recipe -- a steam turbine was
  answered with "missing lv_machine_hull" seconds after that hull had been crafted. Missing
  items are now followed down to ingredients that genuinely have no usable recipe.
- **Repeated crafts are folded together.** A single machine planned 49 steps, 17 of them
  identical; identical recipes are merged into one step with a batch count, and a tool is never
  planned twice.

## 1.0.8

### Planning no longer freezes the game

- **A plan has two seconds, not half a minute.** Exploring a GregTech-sized recipe graph could
  take 25 seconds on the render thread, which looks like a crash. Planning now stops at a wall
  clock budget and says the tree was too large, instead of filling in an answer it never found.
- **Alternatives and recipes are tried in a bounded number.** One ingredient can list 144
  interchangeable tools; walking every one of them multiplied the work for nothing, because the
  list is already sorted best-first.
- **"Craft all" probes a short ladder** instead of running ten full plans through a binary search.
- **Diagnostics are capped per request.** The failure log was 60,000 lines a craft, and writing
  it was part of the delay.

## 1.0.7

### Autocrafting fixes

- **Tools are planned once, not once per craft.** A recipe that needs a file for every batch used
  to plan one file per batch, so asking for ten vacuum tubes crafted five wrought iron files. A
  reusable tool is now required exactly once no matter how many crafts depend on it.
- **"No recipe is known for that item" is gone for items that visibly have recipes.** The planner
  used to follow the whole recipe graph to decide whether a recipe was really an uncrafting recipe,
  and any cycle in that graph (common in GregTech) made it throw away the genuine crafting recipe.
  It now only rejects a recipe when one of its direct inputs is the item itself, or something
  already being planned: the real splitting/uncrafting cases.
- **Rejected recipes are named in the log** with the reason (does not produce this stack, not
  craftable in this screen, consumes what it makes, uncrafting/splitting), so a report of a missing
  recipe can be traced instead of guessed at.
- **Your own inventory is always counted.** Stock used to be whatever EMI's handler for the open
  screen exposed, and for a modded crafting station that slice could leave the player's items out:
  22 tin cables in the inventory were reported missing. The player inventory is now counted
  directly, and the screen's other slots are added on top, each slot once.

## 1.0.5

### Calculator fixes and pinning

- **Search bar result is smaller:** just the number, drawn dimmed at the right edge of EMI's field.
- **Click the value** to pin the calculation and open the floating window.
- **Pin button** (next to close) stores the calculation until you explicitly close it. A pinned
  result is not replaced by new search-bar input.
- **Click the value in the window** to show or hide the expression beneath it.
- **Items bar on one side only:** EMI's index sidebar is restored on the left side, not both.
- Smaller default window size: just the number, nothing more.

## 1.0.4

### The search bar is a calculator now

EMI's search box evaluates arithmetic as you type, so a query like
`((232134/23)*29+2.3)-90000000000000` answers itself instead of searching for an item by that name.
The value is drawn right-aligned inside the search bar in a dim colour, and the field keeps every
click and keystroke — the result is displayed, never interactive.

- Big numbers stay readable. The result is computed in `BigDecimal` and printed with grouping
  separators (`-89,999,999,707,307.004347826087`), not as `-9.0E13`. Scientific notation only
  appears once a value is genuinely beyond reading.
- Supported: `+ - * / %`, parentheses, unary minus, decimals and `^` / `**` powers, with the usual
  precedence. Anything that is not a complete expression — `1/0`, a half-typed `2+`, an item name —
  simply produces no result. It never throws and never disturbs the search.
- Hand-written parser, no script engine. This runs on the render thread on every keystroke, so it is
  bounded in input length, nesting depth and exponent, and it cannot execute code.

### A result window you can move and rename

- New floating window showing the last result. It draws **above EMI's panel and above the player's
  inventory**, and stays put while the rest of the screen changes underneath it.
- Drag it by the title bar. Dragging reveals an editable name label, so renaming and moving are the
  same gesture; the name, position and open state persist.
- A 13px close button with a hover state and a "Close" tooltip. Closing it hides the window; typing a
  new expression brings it back.

### Removed: the old item panel

The custom item panel was dead code. It had already stopped rendering, but its classes, config keys
and keybind were still compiled in and still offered in the config screen, where turning them on did
nothing. All of it is gone: roughly 1,500 lines across seven classes, the `panel.enabled` switch, the
panel keybind, and their translations. EMI owns that screen space and always did in practice.

Bookmarks, hidden items and subsets are kept — only the dead UI was removed, so existing configs and
saved data still load. Old config files containing `panel.enabled` are tolerated and the stale key is
dropped on the next save.

## 1.0.3

### Autocrafting fixes

- **Only recipes the open screen can run count towards reachability.** A wiremill, packer or
  assembler recipe no longer makes an item look craftable from a crafting table. This is what made
  the planner chase octal and hex wires: the machine recipe for them made every wire in a tag look
  obtainable, so it planned a wire-combining chain the crafting table could never run.
- **Missing items are named honestly.** When every variant of a tag fails without planning a single
  step, the report names the ingredient itself instead of whichever variant sorted first. You get
  "tin wire", not "8x tin wire".
- **Much faster planning on large tags.** Reachability results are memoised and the "every input in
  stock" check is cached per stock snapshot. Tags with hundreds of members (oak planks, GT tools)
  no longer stall the client for many seconds before reporting that something is missing.

## 1.0.2

### Autocrafting & Planner Improvements

- **Cycle & reverse-recipe prevention:** Automatically detects and prunes circular uncrafting, splitting, decompressing, and stripping recipes (e.g. multi-wire splitting, storage blocks to ingots, cable stripping) when the composite input is not already in stock. Prevents spurious requests for downstream items (such as octal wires) when crafting basic components.
- **Improved failure reporting:** Backtracking now prioritizes the recipe attempt that planned the deepest intermediate steps instead of whichever attempt failed with the fewest missing items. Preserves the primary recipe's missing ingredients on tie, naming the real missing raw materials rather than an uncrafting dead end.
- **Mod-aware tag candidate sorting:** When resolving tag inputs with multiple modded options, candidate items matching the target recipe's namespace are prioritized over third-party decorative variants (e.g. prioritizing GTCEu blocks over Chipped/Chisel blocks).

## 1.0.1 - multi-version

### Supported Minecraft versions

One source tree, one branch per Minecraft version. Every branch compiles and ships its own jars.

| Minecraft | Fabric | Forge |
| --- | --- | --- |
| 1.18.2 | yes | - |
| 1.19.2 | yes | yes |
| 1.19.3 | yes | yes |
| 1.19.4 | yes | yes |
| 1.20 | yes | yes |
| 1.20.1 | yes | yes |
| 1.20.2 | yes | yes |
| 1.20.4 | yes | - |
| 1.20.6 | yes | - |
| 1.21 | yes | - |
| 1.21.1 | yes | - |

That is every Minecraft version EMI itself has a build for.

### Porting notes

- Minecraft 1.19.x has no `DrawContext`; the panel draws through a small `dev.nei.render.DrawContext`
  shim over the version's own `MatrixStack` helpers, so the renderer is written once.
- Minecraft 1.20.5+ replaced item NBT with data components; those branches read
  `DataComponentTypes.CUSTOM_DATA` instead of `ItemStack#getNbt()`.
- Minecraft 1.18.2 is EMI 0.7.3, which is Fabric-only and predates the modern EMI API. That branch
  ships Fabric only and reads EMI's legacy `EmiRecipes` index; see the version notes for details.
- Screen rendering, tooltip construction and the EMI autocraft entry point changed signature several
  times across these versions; each branch carries the call that its own EMI and Minecraft expect.

### Craft amounts

- C: 1
- Ctrl + C: 32
- Shift + C: 64
- Ctrl + Shift + C: as many as the current stock supports, capped by config and the planner step limit

### AE2

- Read the terminal's synced network repo directly, independent of AE2's expose-to-EMI option
- Count the AE2 terminal crafting grid as stock
- Return AE2 grid leftovers
- Hover the terminal item list through focused-slot and under-cursor fallbacks
- Transfer, take output and repeat for AE2 crafting-terminal batches

### Documentation

- Document the complete feature set and dependency matrix in the README

## 1.0.0

- Initial public release
- Recursive EMI autocrafting with screen-aware recipes
- Tool-aware and tag-aware ingredient planning
- Leftover return and missing-ingredient reporting
- Extra item tooltips and diagnostic data dump
- Fabric and Forge builds
