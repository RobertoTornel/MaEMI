# Changelog

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
