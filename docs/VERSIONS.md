# Supported versions and branches

## Support matrix

| Minecraft | Fabric | Forge / NeoForge | Notes |
| --- | --- | --- | --- |
| 1.20.1 | yes | yes (Forge 47+) | Stable LTS release line (`main`, `1.20.1`) |
| 1.21.1 | yes | in progress | 1.21.x modern release line (`1.21.x`, `1.21.1`) |

## Branch layout

| Branch | Minecraft | Purpose |
| --- | --- | --- |
| `main` | 1.20.1 | Primary 1.20.1 development branch (Fabric + Forge) |
| `1.20.1` | 1.20.1 | Frozen 1.20.1 release line |
| `1.20.1-fabric` | 1.20.1 | 1.20.1 Fabric builds |
| `1.20.1-forge` | 1.20.1 | 1.20.1 Forge builds |
| `1.21.x` | 1.21.1 | 1.21.x release line with Data Component and modern screen APIs |
| `1.21.1` | 1.21.1 | 1.21.1 release line |

## 1.21 Changes

- **Data Components**: Replaced legacy `ItemStack.getNbt()` with `ComponentChanges` and `CUSTOM_DATA` (`NbtComponent`).
- **Screen APIs**: Modernized `renderBackground(context, mouseX, mouseY, delta)`.
- **Tooltips**: Modernized `ItemStack.getTooltip` injection signature for 1.21.1 Yarn mappings.
- **Java**: Java 21 toolchain and runtime.
