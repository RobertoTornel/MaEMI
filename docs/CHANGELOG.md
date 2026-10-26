# Changelog

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
