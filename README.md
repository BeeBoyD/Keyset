<p align="center">
  <img src="logo.png" alt="Keyset logo" width="192" />
</p>

# Keyset

[![GitHub Release](https://img.shields.io/github/v/release/BeeBoyD/Keyset?display_name=tag&label=GitHub%20Release)](https://github.com/BeeBoyD/Keyset/releases)
[![Modrinth](https://img.shields.io/modrinth/dt/keyset?logo=modrinth&label=Modrinth)](https://modrinth.com/project/keyset)
[![CurseForge](https://img.shields.io/badge/CurseForge-Keyset-f16436)](https://legacy.curseforge.com/minecraft/mc-mods/keyset)
[![License](https://img.shields.io/github/license/BeeBoyD/Keyset)](https://github.com/BeeBoyD/Keyset/blob/main/LICENSE)
[![Client-Side](https://img.shields.io/badge/side-client--side-4caf50)](https://github.com/BeeBoyD/Keyset)

Keyset is a profile-based keybind manager for modded Minecraft.

It lets you store full control layouts, switch between them instantly, inspect conflicts with context, and preview safer cleanup before anything gets applied.

> ⚠️ **Release line note:**
> `1.1.x` targets Minecraft `1.20.1+`.
> Older `1.16.5-1.19.4` builds remain on the `1.0.x` line and only receive critical bug fixes.
> Profile compatibility stays the same across the supported release line.

## ✨ Why Keyset

- Save separate keybind setups for different playstyles, packs, or tasks
- Swap profiles instantly without rebuilding controls by hand
- Find overloaded keys faster in large modpacks
- Fix one bind directly or run a guided cleanup pass
- Export and import profile JSON for backup or sharing

## 🚀 1.1.0 Highlights

- The active release line now focuses on modern `1.20.1+` loaders and Minecraft versions
- Opening Keyset from `Controls` is more reliable across reopen, resize, and GUI scale changes
- Manual edits made in vanilla `Controls` now sync back into the active profile with visible status
- Config loading is safer with backup recovery, broken-file archiving, and clearer recovery messages

Legacy `1.16.5-1.19.4` builds stay available on the `1.0.x` line with the same profile format, but the active UI and feature work now lives on `1.20.1+`.

## 📥 Download

- [GitHub Releases](https://github.com/BeeBoyD/Keyset/releases)
- [Modrinth](https://modrinth.com/project/keyset)
- [CurseForge](https://legacy.curseforge.com/minecraft/mc-mods/keyset)

Release assets are split by loader and Minecraft range, for example:

- `keyset-fabric-1.20.1-1.20.2-1.1.0.jar`
- `keyset-forge-1.21.1-1.1.0.jar`
- `keyset-neoforge-1.21-1.21.11-1.1.0.jar`

Notes:

- Quilt uses the Fabric-compatible jars.
- Forge support is intentionally capped at `1.21.1`.
- Older `1.16.5-1.19.4` builds remain available on the `1.0.x` line for critical bug fixes only.

## 🧭 Quick Start

1. Open Minecraft's `Controls` screen and click `Keyset`.
2. Pick an existing profile or create a new one from your live controls.
3. Click `Save Live` once the current layout is where you want it.
4. Search conflicts by action, key, category, or internal id.
5. Use direct actions for a single bind, or use `Preview` in `Safe Fix` for a guided cleanup pass.

## 🔧 Feature Set

- Multiple keybind profiles with instant switching
- Starter profiles: `Default`, `PvP`, `Building`, `Tech`
- Conflict browser with search and grouping by key or category
- Direct actions for the selected conflict
- Safe auto-fix flow with preview, apply, and undo
- Clipboard JSON export and import
- Persistent local storage in `config/keybindprofiles.json`

## 🧠 Behavior Notes

### Profiles

- The active profile persists across restarts.
- `Save Live` captures the current controls into the selected profile.
- Missing or temporarily unavailable keybind ids are preserved and can reactivate later.

### Conflicts

- Conflicts can be grouped by assigned key or by category.
- Search matches display names, categories, key labels, and internal ids.
- Cross-category conflicts are still shown.
- Direct actions only affect the active profile.

### Safe Fix

- Important vanilla binds are protected by default: movement, inventory, chat, escape, and drop
- Sticky user edits saved into the active profile are respected
- Healthy non-conflicting assignments are not overwritten
- Output is deterministic for the same input

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

Legacy maintenance line:

- `1.0.x` remains available for `1.16.5-1.19.4`
- Those older builds are now critical-fix-only
- Profile compatibility remains stable across the supported release line

## 🔒 Privacy

Keyset does not collect analytics, send telemetry, or upload your keybind data anywhere.

Profile data is stored locally in:

```text
config/keybindprofiles.json
```

## 📦 Modpack Notes

- Client-side only
- Safe to include in packs
- Does not add gameplay content
- Designed to stay additive instead of fighting other keybind mods

## 📍 Status

Current stable release line: `1.1.0`
