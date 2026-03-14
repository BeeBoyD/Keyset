![Keyset logo](logo-modrinth.png)

# Keyset

[![Modrinth](https://img.shields.io/modrinth/dt/keyset?logo=modrinth&label=Modrinth)](https://modrinth.com/project/keyset)
[![Source](https://img.shields.io/badge/source-GitHub-24292f)](https://github.com/BeeBoyD/Keyset)
[![License](https://img.shields.io/github/license/BeeBoyD/Keyset)](https://github.com/BeeBoyD/Keyset/blob/main/LICENSE)
[![Client-Side](https://img.shields.io/badge/side-client--side-4caf50)](https://modrinth.com/project/keyset)

Keyset is a profile-based keybind manager for modded Minecraft.

Save complete control layouts, switch between them instantly, inspect conflicts with clearer context, and preview safe cleanup before anything is applied.

## 1.0.0 Highlights

- New dashboard-style UI on the current `1.20.1+` Fabric line
- Clearer labels for what is live, what is read-only, and what Safe Fix changes
- Stable `1.0.0` release line

Legacy `1.16.5-1.19.4` builds keep the same feature set and file format, but use the older screen layout.

## What It Does

- Multiple keybind profiles with instant switching
- Starter profiles: `Default`, `PvP`, `Building`, `Tech`
- Conflict browser with search and grouping
- Direct actions for a selected conflict
- Safe auto-fix with preview, apply, and undo
- Clipboard JSON export and import

## Quick Start

1. Open Minecraft's `Controls` screen.
2. Click `Keyset`.
3. Pick a profile or create one from your live controls.
4. Save the layout with `Save Live`.
5. Fix binds directly or preview `Safe Fix` first.

## Downloads

Download Keyset from [Modrinth](https://modrinth.com/project/keyset).

Published files are split by loader and Minecraft range, for example:

- `keyset-fabric-1.20.1-1.20.2-1.0.0.jar`
- `keyset-forge-1.21.1-1.0.0.jar`
- `keyset-neoforge-1.21-1.21.11-1.0.0.jar`

## Supported Versions

| Minecraft | Fabric | Quilt | Forge | NeoForge |
| --- | --- | --- | --- | --- |
| 1.16.5 | ✅ | ✅ | ⚠️ | N/A |
| 1.17.1 | ✅ | ✅ | ⚠️ | N/A |
| 1.18.2 | ✅ | ✅ | ✅ | N/A |
| 1.19.2 | ✅ | ✅ | ✅ | N/A |
| 1.19.4 | ✅ | ✅ | ✅ | N/A |
| 1.20.1-1.20.2 | ✅ | ✅ | ✅ | ✅ |
| 1.20.3-1.20.4 | ✅ | ✅ | ✅ | ✅ |
| 1.20.5-1.20.6 | ✅ | ✅ | ✅ | ✅ |
| 1.21.1 | ✅ | ✅ | ✅ | ✅ |
| 1.21.2-1.21.4 | ✅ | ✅ | ❌ | ✅ |
| 1.21.5-1.21.11 | ✅ | ✅ | ❌ | ✅ |

Notes:

- Quilt uses the Fabric-compatible jars.
- Forge support is intentionally capped at `1.21.1`.
- Forge `1.16.5` and `1.17.1` remain early targets.

## Privacy

Keyset stores profile data locally in:

```text
config/keybindprofiles.json
```

It does not send telemetry or upload your controls anywhere.

## Modpack Notes

- Client-side only
- Safe to include in packs
- No gameplay content

## Status

Current stable release line: `1.0.0`
