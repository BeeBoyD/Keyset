<p align="center">
  <img src="logo.png" alt="Keyset logo" width="192" />
</p>

# Keyset

[![GitHub Release](https://img.shields.io/github/v/release/BeeBoyD/Keyset?display_name=tag&label=GitHub%20Release)](https://github.com/BeeBoyD/Keyset/releases)
[![Modrinth](https://img.shields.io/modrinth/dt/keyset?logo=modrinth&label=Modrinth)](https://modrinth.com/project/keyset)
[![License](https://img.shields.io/github/license/BeeBoyD/Keyset)](https://github.com/BeeBoyD/Keyset/blob/main/LICENSE)
[![Client-Side](https://img.shields.io/badge/side-client--side-4caf50)](https://github.com/BeeBoyD/Keyset)

Keyset is a profile-based keybind manager for modded Minecraft.

Save complete control layouts, switch between them instantly, inspect conflicts with clearer context, and preview safe cleanup before anything is applied.

## 1.0.0 Highlights

- New dashboard-style UI on the current `1.20.1+` Fabric line, with a clearer profile flow, action cards, and richer conflict rows
- Better labels and guidance so it is easier to understand what is live, what is read-only, and what Safe Fix will change
- Cleaner public docs and release copy for GitHub and Modrinth
- Stable `1.0.0` release line

Legacy `1.16.5-1.19.4` builds keep the same core features and file format, but use the older screen layout.

## Why Use It

- Keep separate keybind setups for different playstyles, packs, or tasks
- Swap profiles instantly without manually rebuilding controls
- Find overloaded keys faster in big modpacks
- Clean up conflicts without stomping important vanilla binds
- Export and import profile JSON when you want to share or back up a setup

## Quick Start

1. Open Minecraft's `Controls` screen and click `Keyset`.
2. Pick an existing profile or create a new one from your current live controls.
3. Click `Save Live` when your current layout is where you want it.
4. Search the conflict list by action, key, category, or internal id.
5. Use `Open Controls`, `Clear Selected`, or `Pick New Key` for direct fixes.
6. Use `Preview` in `Safe Fix` when you want a guided cleanup pass first.

## Features

- Multiple keybind profiles with instant switching
- Starter profiles: `Default`, `PvP`, `Building`, `Tech`
- Conflict browser with search and grouping by key or category
- Direct actions for the selected conflict
- Safe auto-fix flow with preview, apply, and undo
- Clipboard JSON export and import
- Persistent local storage in `config/keybindprofiles.json`

## Downloads

- [GitHub Releases](https://github.com/BeeBoyD/Keyset/releases)
- [Modrinth Releases](https://modrinth.com/project/keyset)

Release assets are published per loader and Minecraft range, for example:

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

## How Keyset Behaves

### Profiles

- The active profile persists across restarts.
- `Save Live` captures the current controls into the selected profile.
- Missing or currently unavailable keybind ids are preserved and can reactivate later.

### Conflicts

- Conflicts can be grouped by assigned key or by category.
- Search matches display names, categories, key labels, and internal ids.
- Cross-category conflicts are still shown.
- Direct actions work on the active profile only.

### Safe Fix

- Never changes important vanilla binds by default: movement, inventory, chat, escape, and drop
- Prefers modded or less-protected binds first
- Respects sticky user edits saved into the active profile
- Prefers unused plain keys before harder-to-reach fallbacks
- Avoids overwriting healthy non-conflicting assignments
- Produces deterministic output for the same input

## Privacy

Keyset does not collect analytics, send telemetry, or upload your keybind data anywhere.

The mod stores its profile data locally in:

```text
config/keybindprofiles.json
```

## Modpack Notes

- Client-side only
- Safe to include in packs
- Does not add gameplay content
- Designed to stay additive instead of fighting other keybind mods

## Status

Current stable release line: `1.0.0`
