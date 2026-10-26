# Tooling and release operations

## Project location

The repository and all build output live in:

`/home/mana/Documents/mcmod/MaEMI`

The Git remote is `https://github.com/RobertoTornel/nei-fabric` (the repository name predates the
MaEMI rename). The default branch is `main`.

## Tool locations on this machine

| Tool | Location / value |
| --- | --- |
| JDK 21 | `/usr/lib/jvm/java-21-openjdk-amd64` |
| Gradle wrapper | `./gradlew` in the project root |
| Gradle installation | `/opt/gradle-8.10.2/bin/gradle` |
| Python 3 | `/usr/bin/python3` |
| curl | `/usr/bin/curl` |
| git | `git` from PATH |
| Modrinth API documentation | https://docs.modrinth.com/api/ |
| Modrinth upload script | `scripts/upload-modrinth.sh` |
| Project icon | `assets/icon.png` |
| Manual Monifactory Beta test instance | `/mnt/storage/relocated/home/mana/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Monifactory-Beta.0.13.8-client/minecraft` |

The manual test instance is only a reference location. Nothing in the upload script launches,
installs into or otherwise modifies it.

Optional game-observation utilities available on this machine are `xdotool`, `scrot`,
`tesseract`, `wmctrl` and `flatpak`. Use them only when interactive game testing has been
explicitly approved.

## Building

From the project root:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build
```

The upload script expects these exact files:

- `fabric/build/libs/maemi-fabric-<version>.jar`
- `forge/build/libs/maemi-forge-<version>.jar`

The version comes from `mod_version` in `gradle.properties`.

## Modrinth project

| Field | Value |
| --- | --- |
| Project id | `kU7DtD47` |
| Slug | `maemi` |
| Public URL after approval | https://modrinth.com/mod/maemi |
| Description | NEI autocrafting for EMI, Works with complex modded trees (Tested on monifactory) |

The project was created as a draft, the Fabric and Forge 1.0.1 versions were uploaded, and the
project was submitted for review. A draft is not public until Modrinth approves it.

Current version ids at the time of writing:

- Fabric: `LWg3Cyzy` (`1.0.1-fabric`)
- Forge: `lvR4P3WF` (`1.0.1-forge`)

Modrinth project ids used as version dependencies:

| Dependency | Project id | Type |
| --- | --- | --- |
| EMI | `fRiHVvU7` | required |
| Fabric API | `P7dR8mSH` | required on the Fabric version |
| Cloth Config API | `9s6osm5g` | optional |
| Mod Menu | `mOgUt4GM` | optional on the Fabric version |
| Applied Energistics 2 | `XxWD5pD3` | optional |

## Modrinth token

The API token is **not** stored in the repository. Keep it outside the project with owner-only
permissions:

```bash
install -d -m 700 "$HOME/.config/maemi"
install -m 600 /dev/null "$HOME/.config/maemi/modrinth-token"
# Put the token in that file with an editor, or:
# printf '%s' 'TOKEN' > "$HOME/.config/maemi/modrinth-token"
```

Alternatively export it for one shell:

```bash
export MODRINTH_TOKEN='...'
```

Never commit either form. The upload script reads `MODRINTH_TOKEN` first, then
`MODRINTH_TOKEN_FILE` (default `$HOME/.config/maemi/modrinth-token`).

## Uploading with the script

Build first, then run:

```bash
./scripts/upload-modrinth.sh 1.0.1
```

With no argument it reads `mod_version` from `gradle.properties`.

The script:

1. reads the token without printing it
2. checks the existing version numbers and skips duplicates
3. uploads the Fabric jar as `1.0.1-fabric` and the Forge jar as `1.0.1-forge`
4. adds the dependency ids listed above
5. submits a project that is still a draft for review

It does not build the jars. Run the Gradle build first.

## Manual API upload

If the script is unavailable, the equivalent request for one loader is a multipart POST. Save
this as `version.json` and upload the matching jar:

```json
{
  "project_id": "kU7DtD47",
  "name": "MaEMI 1.0.1 (Fabric)",
  "version_number": "1.0.1-fabric",
  "changelog": "See docs/CHANGELOG.md",
  "dependencies": [
    {"project_id": "fRiHVvU7", "dependency_type": "required"},
    {"project_id": "P7dR8mSH", "dependency_type": "required"},
    {"project_id": "9s6osm5g", "dependency_type": "optional"},
    {"project_id": "mOgUt4GM", "dependency_type": "optional"},
    {"project_id": "XxWD5pD3", "dependency_type": "optional"}
  ],
  "game_versions": ["1.20.1"],
  "version_type": "release",
  "loaders": ["fabric"],
  "featured": false,
  "file_parts": ["file"],
  "primary_file": "file",
  "environment": "client_only"
}
```

```bash
curl -X POST https://api.modrinth.com/v2/version \
  -H "Authorization: $MODRINTH_TOKEN" \
  -F 'data=<version.json;type=application/json' \
  -F 'file=@fabric/build/libs/maemi-fabric-1.0.1.jar;type=application/java-archive'
```

For Forge, use the Forge jar and remove the Fabric API entry from `dependencies`.

After uploading both versions, submit a draft project:

```bash
curl -X PATCH https://api.modrinth.com/v2/project/kU7DtD47 \
  -H "Authorization: $MODRINTH_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"processing","requested_status":"approved"}'
```

Check the project state with:

```bash
curl -H "Authorization: $MODRINTH_TOKEN" \
  https://api.modrinth.com/v2/project/kU7DtD47
```

Do not put the token in shell history if that is a concern on the machine; the script reads the
protected file instead.

## Keeping the Modrinth body in sync

The Modrinth project body is the README. After changing the README, update the project body with
the API (the token is read from the protected file by the shell, not echoed):

```bash
python3 - <<'PY'
import json
body = open('README.md', encoding='utf-8').read()
open('/tmp/maemi-body.json', 'w').write(json.dumps({'body': body}))
PY
curl -X PATCH https://api.modrinth.com/v2/project/kU7DtD47 \
  -H "Authorization: $(cat "$HOME/.config/maemi/modrinth-token")" \
  -H 'Content-Type: application/json' \
  -d @/tmp/maemi-body.json
```

The project icon is uploaded separately with the `/project/{id}/icon` endpoint if it changes.
