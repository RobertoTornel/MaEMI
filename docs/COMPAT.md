# Compatibility modules

## How a module is enabled

```
module enabled  <=>  every id in requiredMods is loaded
                 &&  no id in conflictingMods is loaded
                 &&  NeiClient is initialised
```

The check runs inside `NeiCompat.install` during EMI's registration, before the module's
`install` is called, so a module can freely reference its target mod's classes: if the mod is
missing, that code is never reached. Every module is also wrapped in a try/catch; a broken module is
logged and disabled for the session instead of preventing NEI or EMI from starting.

The report is logged at startup and available from the config code through
`NeiCompat.report()` (id, required mods, enabled/missing reason, preset count).

## Modules shipped

All current modules are namespace subset presets: when the mod is present, the first time the panel
is opened NEI creates a subset named after the mod containing every stack whose registry namespace
belongs to it. This is the 1.20.1 equivalent of NEI's per-mod item lists and costs the host mod
nothing.

| Module id | Mod ids | Subset |
| --- | --- | --- |
| `gregtech` | `gtceu` | GregTech |
| `ae2` | `ae2` | Applied Energistics |
| `create` | `create` | Create |
| `mekanism` | `mekanism` | Mekanism |
| `thermal` | `thermal` | Thermal |
| `immersiveengineering` | `immersiveengineering` | Immersive Engineering |
| `botania` | `botania` | Botania |
| `ars_nouveau` | `ars_nouveau` | Ars Nouveau |
| `tconstruct` | `tconstruct` | Tinkers' Construct |
| `farmersdelight` | `farmersdelight` | Farmer's Delight |
| `modern_industrialization` | `modern_industrialization` | Modern Industrialization |
| `powah` | `powah` | Powah |
| `ad_astra` | `ad_astra` | Ad Astra |

Presets are only created once; an existing subset with the same name is never overwritten, so player
edits survive pack updates.

## Generic compatibility (no module needed)

* **Any mod's recipes** are visible as soon as it registers with EMI (its own plugin or an EMI
  addon). NEI does not need a handler per machine.
* **Machine lookups** use `EmiRecipeManager.getWorkstations(category)`, so the "?" button works for
  every category that declares its workstation, including GregTech, AE2 and Create machines.
* **Search by tag** uses `$`, resolved through the item's own tags, so `c:` and `forge:`
  conventions both work.
* **Fluids, tags and NBT stacks** are handled by EMI's stack type; nothing assumes items only.

## Monifactory (Forge 1.20.1)

Monifactory runs Forge 47.2.0 with EMI 1.1.22 (Forge), GTCEu, AE2, Thermal, Curios, KubeJS and EMI
Loot. NEI's Forge jar:

* is a normal Forge mod (`mods.toml`, `displayTest = IGNORE_ALL_VERSION`, client only),
* depends only on `emi` (>= 1.1.20) and optionally `cloth_config`,
* uses no GTCEu/AE2/Thermal classes at compile time, so a pack update cannot link against a
  different GT version,
* enables the `gregtech`, `ae2` and `thermal` subset modules because those mods are present, and
  leaves the rest disabled.

The autocrafting path uses only public EMI API (`EmiRecipeFiller.performFill`, `EmiCraftContext`,
`BoM`), all of which exist in 1.1.22.

## Adding a module

```java
modules.add(new ModSubsetModule("mymod", "My Mod", List.of("mymod"), Set.of("mymod")));
```

For behaviour beyond a subset, implement `CompatModule` in `dev.nei.compat` and register it in
`BuiltinCompat`; implement `appendTooltip` or `install(EmiRegistry)`. Keep any direct reference to
the target mod inside that class so the class is only loaded when the mod is present.
