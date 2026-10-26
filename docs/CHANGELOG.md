# Changelog

## 1.0.1

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
