![Keyset logo](logo-modrinth.png)

# Keyset

[![CurseForge](https://img.shields.io/badge/CurseForge-Keyset-f16436)](https://legacy.curseforge.com/minecraft/mc-mods/keyset)
[![Modrinth](https://img.shields.io/modrinth/dt/keyset?logo=modrinth&label=Modrinth)](https://modrinth.com/project/keyset)
[![Source](https://img.shields.io/badge/source-GitHub-24292f)](https://github.com/BeeBoyD/Keyset)

Keyset is a profile-based keybind manager for modded Minecraft.

Save full control layouts, swap them instantly, inspect conflicts with context, and preview safer cleanup before applying changes.

> 💡 **Support tip:** for faster updates, issue tracking, and the best support experience, please use the [Modrinth project page](https://modrinth.com/project/keyset).

> ⚠️ **Release line note:**
> `1.1.x` targets Minecraft `1.20.1+`.
> Older `1.16.5-1.19.4` builds remain on the `1.0.x` line and only receive critical bug fixes.
> Profile compatibility stays the same across the supported release line.

## ✨ What It Does

- Multiple keybind profiles with instant switching
- Starter profiles: `Default`, `PvP`, `Building`, `Tech`
- Conflict browser with search and grouping
- Direct actions for the selected conflict
- Safe auto-fix with preview, apply, and undo
- Clipboard JSON export and import

## 🧭 Quick Start

1. Open Minecraft's `Controls` screen.
2. Click `Keyset`.
3. Pick a profile or create one from your live controls.
4. Save the layout with `Save Live`.
5. Fix a bind directly or use `Safe Fix` preview first.

## 📥 More Places To Download

- [Modrinth](https://modrinth.com/project/keyset)
- [GitHub Releases](https://github.com/BeeBoyD/Keyset/releases)

Published files are split by loader and Minecraft range.

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
| 26.1 | ✅ | ✅ | ❌ | ❌ |

Older `1.16.5-1.19.4` builds remain available on the `1.0.x` line and are now critical-fix-only.

Forge and NeoForge 26.1 support is pending upstream loader tooling.

## 🔒 Privacy

Keyset stores profile data locally in `config/keybindprofiles.json` and does not upload telemetry or keybind data.
