# Supported versions and branches

MaEMI is built for **every Minecraft version EMI itself has a build for**, and for the same loaders in
the cases where that loader still exists. Each version is its own branch off `main`, carrying the
source changes that version needs.

## Support matrix

| Minecraft | Fabric | Forge | Branch | What changed for this version |
| --- | --- | --- | --- | --- |
| 1.18.2 | yes | — | `1.18.2` | Oldest EMI line; reduced integration surface |
| 1.19.2 | yes | yes | `1.19.2` | `MatrixStack` draw shim |
| 1.19.3 | yes | yes | `1.19.3` | `MatrixStack` draw shim |
| 1.19.4 | yes | yes | `1.19.4` | `MatrixStack` draw shim |
| 1.20 | yes | yes | `1.20` | `DrawContext`, 4-arg `renderBackground`, EMI 1.0.x autocraft call |
| 1.20.1 | yes | yes | `main`, `1.20.1` | Flagship line (Monifactory and most 1.20.1 packs) |
| 1.20.2 | yes | yes | `1.20.2` | 4-arg `renderBackground`, Forge 48 |
| 1.20.4 | yes | — | `1.20.4` | Forge line ends; Fabric only |
| 1.20.6 | yes | — | `1.20.6` | Data components replace item NBT; Java 21 |
| 1.21 | yes | — | `1.21` | Data components; older EMI 1.1.12 API |
| 1.21.1 | yes | — | `1.21.x`, `1.21.1` | Current flagship line |

Forge is listed only where Forge itself exists for that version. From 1.20.4 onward the non-Fabric
loader is NeoForge; MaEMI ships Fabric there and does not publish NeoForge jars.

## What actually differs between the branches

Three APIs moved underneath the code across this range, and those are the only substantive forks:

1. **Screen rendering.**
   - 1.18.2 – 1.19.4: `render(MatrixStack, int, int, float)`. There is no `DrawContext` class yet, so
     the branches carry a hand-written shim at
     `common/src/main/java/dev/nei/render/DrawContext.java` that exposes the 1.20 drawing methods on
     top of the version's own `MatrixStack` helpers. The panel renderer itself is written once
     against that shim.
   - 1.20 – 1.20.2: real `DrawContext`, but `renderBackground` still takes one argument.
   - 1.20.4+: `renderBackground(DrawContext, int, int, float)`.

2. **Item data.**
   - 1.18.2 – 1.20.4: `ItemStack#getNbt()` and `EmiStack#getNbt()` exist and are used directly.
   - 1.20.6+: NBT is gone. Those branches read `DataComponentTypes.CUSTOM_DATA`
     (`NbtComponent`) for tool data, and use `EmiStack#getComponentChanges()` for identity.

3. **EMI's own API.**
   - The autocraft entry point moved from
     `performFill(recipe, screen, EmiFillAction, amount)` in EMI 1.0.x to
     `performFill(recipe, screen, EmiCraftContext.Type, EmiCraftContext.Destination, amount)` in 1.1.x.
   - `EmiRegistry#addAlias` is not present in every EMI build; where it is missing the call is made
     reflectively and simply does nothing.
   - EMI 0.7.x (1.18.2) lacks several of the registry hooks the modern code assumes; that branch
     keeps the integrations that build and drops the rest.

## Building every version

```bash
scripts/build-all-versions.sh          # checks out each branch in a temp worktree, builds, copies jars
scripts/upload-dist.sh                 # publishes dist/<mc>/*.jar to Modrinth, tagged per version
```

Both scripts are safe to re-run; the uploader skips version numbers that already exist.

## Branch list

| Branch | Minecraft |
| --- | --- |
| `main` | 1.20.1 |
| `1.20.1`, `1.20.1-fabric`, `1.20.1-forge` | 1.20.1 artifact lines |
| `1.20` | 1.20 |
| `1.20.2` | 1.20.2 |
| `1.20.4` | 1.20.4 |
| `1.20.6` | 1.20.6 |
| `1.21` | 1.21 |
| `1.21.x`, `1.21.1` | 1.21.1 |
| `1.19.2`, `1.19.3`, `1.19.4` | 1.19.x |
| `1.18.2` | 1.18.2 |
