![Keyset logo](logo-modrinth.png)

# Keyset

[![Modrinth](https://img.shields.io/modrinth/dt/keyset?logo=modrinth&label=Modrinth)](https://modrinth.com/project/keyset)
[![CurseForge](https://img.shields.io/badge/CurseForge-Keyset-f16436)](https://legacy.curseforge.com/minecraft/mc-mods/keyset)
[![Source](https://img.shields.io/badge/source-GitHub-24292f)](https://github.com/BeeBoyD/Keyset)
[![License](https://img.shields.io/github/license/BeeBoyD/Keyset)](https://github.com/BeeBoyD/Keyset/blob/main/LICENSE)
[![Client-Side](https://img.shields.io/badge/side-client--side-4caf50)](https://modrinth.com/project/keyset)

Keyset is a profile-based keybind manager for modded Minecraft.

Save full control layouts, swap them instantly, inspect conflicts with context, and preview safer cleanup before anything gets applied.

> ⚠️ **Release line note:**
> `1.1.x` targets Minecraft `1.20.1+`.
> Older `1.16.5-1.19.4` builds remain on the `1.0.x` line and only receive critical bug fixes.
> Profile compatibility stays the same across the supported release line.

## ✨ Why It Helps

- Separate profiles for PvP, building, tech packs, or general play
- Faster conflict cleanup in bigger modpacks
- Direct one-bind fixes when you know exactly what you want
- Guided Safe Fix when you want a cleaner automated pass
- JSON export and import for backup or sharing

## 🚀 1.1.x Highlights

- Cleaner modern UI on the active `1.20.1+` line
- More reliable Controls-screen button behavior and screen opening
- Safer config recovery with backup restore and clearer status messages
- Fabric, Quilt, and NeoForge support for Minecraft 26.1 (the first fully unobfuscated release)

Older `1.16.5-1.19.4` builds stay available on the `1.0.x` line with the same profile format, but active development now lives on `1.20.1+`.

## 🧭 Quick Start

1. Open Minecraft's `Controls` screen.
2. Click `Keyset`.
3. Pick a profile or create one from your live controls.
4. Save the layout with `Save Live`.
5. Fix a bind directly or preview `Safe Fix` first.

## 📥 Downloads

- [Modrinth](https://modrinth.com/project/keyset)
- [CurseForge](https://legacy.curseforge.com/minecraft/mc-mods/keyset)
- [GitHub](https://github.com/BeeBoyD/Keyset/releases)

Published files are split by loader and Minecraft range, for example:

- `keyset-fabric-1.20.1-1.20.2-1.1.0.jar`
- `keyset-forge-1.21.1-1.1.0.jar`
- `keyset-neoforge-1.21-1.21.11-1.1.0.jar`

## ✅ Supported Versions

Current `1.1.x` release line:

| Minecraft | Fabric | Quilt | Forge | NeoForge |
| --- | --- | --- | --- | --- |
| 1.20.1-1.20.2 | ✅ | ✅ | ✅ | ✅ |
| 1.20.3-1.20.4 | ✅ | ✅ | ✅ | ✅ |
| 1.20.5-1.20.6 | ✅ | ✅ | ✅ | ✅ |
| 1.21.1 | ✅ | ✅ | ✅ | ✅ |
| 1.21.2-1.21.4 | ✅ | ✅ | ❌ | ✅ |
| 1.21.5-1.21.11 | ✅ | ✅ | ❌ | ✅ |
| 26.1 | ✅ | ✅ | ❌ | ✅ |

Notes:

- Quilt uses the Fabric-compatible jars.
- Forge support is intentionally capped at `1.21.1`.
- Forge 26.1 support is pending upstream loader tooling.
- Older `1.16.5-1.19.4` builds remain on the `1.0.x` line for critical bug fixes only.

## 🔒 Privacy

Keyset stores profile data locally in:

```text
config/keybindprofiles.json
```

It does not send telemetry or upload your controls anywhere.

## 📦 Modpack Notes

- Client-side only
- Safe to include in packs
- No gameplay content

## 📍 Status

Current stable release line: `1.1.2`
