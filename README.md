# Keyset

A clean, vanilla-friendly keybind manager for modded Minecraft.

Switch between control profiles, spot conflicts fast, and auto-fix the safe cases before your Controls screen turns into a landfill.

## Why Keyset

Modpacks pile bindings on top of bindings. Vanilla shows the damage, but it does not help much when you want to:

- keep separate control layouts for different playstyles
- see exactly which binds are colliding
- fix the low-risk conflicts without wrecking movement or inventory muscle memory
- move profiles between instances without hand-editing `options.txt`

Keyset keeps that workflow client-side, additive, and close to vanilla UI expectations.

## Features

- Multiple keybind profiles with instant switching
- Starter profiles: `Default`, `PvP`, `Building`, and `Tech`
- Conflict browser with search and grouping by key or category
- Direct actions for a selected conflict: `Find`, `Clear Key`, `Rebind`
- Safe auto-fix flow with preview, apply, and undo
- Clipboard JSON export/import
- Persistent config at `config/keybindprofiles.json`
- Shared core logic for multi-loader and multi-version maintenance

## Support Matrix

| Minecraft | Fabric / Quilt | Forge | NeoForge | Status |
| --- | --- | --- | --- | --- |
| 1.16.5 | Supported | Build-verified | N/A | Forge dev launch is blocked here by the old Loom/Yarn runtime stack |
| 1.17.1 | Supported | Build-verified | N/A | Forge dev launch is blocked here on this macOS setup by the old GLFW icon path |
| 1.18.2 | Supported | Supported | N/A | Verified |
| 1.19.2 | Supported | Supported | N/A | Verified |
| 1.19.4 | Supported | Supported | N/A | Verified |
| 1.20.1-1.20.2 | Supported | Supported | Supported | Verified |
| 1.20.3-1.20.4 | Supported | Supported | Supported | Verified |
| 1.20.5-1.20.6 | Supported | Planned | Planned | Fabric only today |
| 1.21-1.21.11 | Supported | Planned | Planned | Fabric only today |

Quilt uses the Fabric-compatible targets.

## Quick Start

1. Open `Controls`.
2. Click `Keyset`.
3. Pick or create a profile on the left.
4. Search or group conflicts on the right.
5. Use `Find`, `Clear Key`, `Rebind`, or `Preview Fix`.
6. Apply a safe preview if it looks right, or undo it.

## How Keyset Behaves

### Profiles

- The active profile is persisted across restarts.
- `Save` captures the current live bindings into the selected profile.
- Deleting the active profile safely falls back to `Default`.
- Unknown or currently missing bind ids are retained so they can reactivate later when the mod reappears.

### Conflict View

- Group conflicts by assigned key or by category.
- Search matches binding names, category names, key labels, and internal ids.
- Conflicts across categories are still shown.
- Direct conflict actions work on the active profile only, by design.

### Safe Auto-Fix

- Never changes critical vanilla binds by default: movement, inventory, chat, escape, and drop.
- Prefers less-protected and modded binds first.
- Respects sticky user edits captured into the active profile.
- Prefers unused plain keys before harder-to-reach or modifier-heavy fallbacks.
- Avoids overwriting healthy non-conflicting assignments.
- Always produces deterministic output for the same input.

### Import / Export

- Export the selected profile as JSON to the clipboard.
- Import profile JSON from the clipboard.
- Name collisions are renamed safely instead of overwriting existing profiles.
- Empty or invalid payloads are rejected instead of silently seeding defaults.

## Config Format

Profiles are stored in:

```text
config/keybindprofiles.json
```

Current schema shape:

```json
{
  "schema": 1,
  "activeProfile": "default",
  "profiles": {
    "default": {
      "name": "Default"
    }
  }
}
```

Profile ids are stable internal keys. User-facing profile names can be renamed safely.

## Current Architecture

- `core`
  Shared Java logic for profile models, JSON persistence, conflict detection, auto-resolve, and UI-facing contracts.
- `common-v1_16_5-to-v1_18_x`
  Legacy client shim for older screen and input APIs.
- `common-v1_19_x`
  1.19-era client shim.
- `common-v1_20_1-to-v1_20_2`
  Shared adapter for the first 1.20 generation.
- `common-v1_20_3-to-v1_20_6`
  Shared adapter for post-1.20.3 GUI/input changes.
- `common-v1_21-to-v1_21_11`
  Shared adapter for 1.21.x GUI/input changes.

Loader leaves stay thin on purpose. Feature logic belongs in `core`, not in per-loader branches.

## Development

Use JDK 21 when running Gradle from the shell. Toolchains handle the per-target compile level.

Useful commands:

```bash
./gradlew build
./gradlew verifyWorkspace
./gradlew buildFabricTargets
./gradlew buildForgeTargets
./gradlew buildNeoForgeTargets
./gradlew buildTargetJars
```

Run a Fabric dev client by requested version:

```bash
./run-fabric.sh 1.21.3
./run-fabric.sh latest
```

Representative direct launcher examples:

```bash
./gradlew :platform-forge-1_20_1:runClient
./gradlew :platform-neoforge-1_20_4:runClient
./gradlew :platform-fabric-1_21_11:runClient
```

Release bytecode targets currently align like this:

- Java 8 for `1.16.5`
- Java 16 for `1.17.1`
- Java 17 for `1.18.2-1.20.2`
- Java 21 for `1.20.3+`

## Notes For Modpacks

- Client-side only
- Safe to include on servers because it does not add gameplay content
- Designed to be additive instead of fighting other keybind mods

## Status

This repo is in active pre-release development. The shared core, Fabric matrix, and the first Forge and NeoForge targets are wired. The next big step is finishing the Phase 3 UI pass and then rounding out the remaining Forge and NeoForge ranges.
