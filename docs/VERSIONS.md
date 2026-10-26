# Supported versions and branches

## Support matrix

| Minecraft | Fabric | Forge | Notes |
| --- | --- | --- | --- |
| 1.20.1 | yes | yes | Full supported version; current release |
| 1.20.2 | no | no | Compiles only after Java changes |
| 1.20.4 | no | no | Same screen API change, plus newer loader APIs |
| 1.20.5+ | no | no | Item NBT was replaced by data components |
| 1.21.x | no | no | NeoForge/EMI and item component changes |

Only Minecraft **1.20.1** is supported without changing Java source. The Fabric and Forge builds
share that source; they are separate release artifacts, not separate Minecraft ports.

## Why 1.20.2 is not a code-free version

A compile check against Minecraft 1.20.2, Yarn 1.20.2+build.4, Forge 48.1.0 and EMI
1.1.22+1.20.2 failed in three common classes:

```text
FallbackConfigScreen.java:28: renderBackground(DrawContext) not found
SubsetEditScreen.java:70:   renderBackground(DrawContext) not found
UtilityScreen.java:47:      renderBackground(DrawContext) not found
```

1.20.2 requires `renderBackground(DrawContext, int, int, float)`. Updating those calls is a Java
source change, so 1.20.2 does not meet the no-code-change rule. Later versions also change item
data storage, so the gap grows rather than shrinks.

## Branches

All branches point at the same Minecraft 1.20.1 source. They exist so each released artifact has a
stable branch, even though one codebase produces both loader jars.

| Branch | Purpose |
| --- | --- |
| `main` | Current development, Minecraft 1.20.1 |
| `1.20.1` | Frozen Minecraft 1.20.1 line |
| `1.20.1-fabric` | Fabric artifact line for Minecraft 1.20.1 |
| `1.20.1-forge` | Forge artifact line for Minecraft 1.20.1 |

Do not create branches for 1.20.2 or newer unless the required Java changes are actually made. An
empty or broken version branch is worse than an honest support boundary.

## Modrinth versions

The Modrinth project (slug `maemi`, id `kU7DtD47`) has one version per released loader artifact:

| Modrinth version | Loader | Minecraft |
| --- | --- | --- |
| `1.0.1-fabric` | Fabric | 1.20.1 |
| `1.0.1-forge` | Forge | 1.20.1 |

The upload helper is `scripts/upload-modrinth.sh`. It skips version numbers that already exist, so
re-running it is safe.

## Adding a real port

A new Minecraft version needs, at minimum:

1. a branch named for the exact Minecraft version and loader
2. updated `gradle.properties` mappings and dependency versions
3. updated `fabric.mod.json` and `mods.toml` version ranges
4. Java changes for every compile error — not just metadata edits
5. a separate jar and a new Modrinth version with the correct game version and loader
6. manual in-game verification before calling it supported

The last step cannot be replaced by a successful Gradle build.
