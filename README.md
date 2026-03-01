# Keyset

Keyset is a client-side Minecraft mod for managing keybind profiles, surfacing conflicts, and resolving the low-risk cases without rewriting your entire Controls workflow.

It stays close to vanilla: additive UI, predictable behavior, and no background polling nonsense.

## What It Does

- Multiple keybind profiles with instant switching
- Built-in starter profiles: `Default`, `PvP`, `Building`, `Tech`
- Conflict browser with search and grouping by key or category
- Quick actions for a selected conflict: `Find`, `Clear Key`, `Rebind`
- Safe auto-fix flow with preview, apply, and undo
- JSON export/import through the clipboard
- Persistent config in `config/keybindprofiles.json`

## Supported Versions

| Minecraft | Fabric / Quilt | Forge | NeoForge |
| --- | --- | --- | --- |
| 1.16.5 | Yes | Build-verified | N/A |
| 1.17.1 | Yes | Build-verified | N/A |
| 1.18.2 | Yes | Yes | N/A |
| 1.19.2 | Yes | Yes | N/A |
| 1.19.4 | Yes | Yes | N/A |
| 1.20.1-1.20.2 | Yes | Yes | Yes |
| 1.20.3-1.20.4 | Yes | Yes | Yes |
| 1.20.5-1.20.6 | Yes | Yes | Yes |
| 1.21.1 | Yes | Yes | Yes |
| 1.21.2-1.21.4 | Yes | No | Yes |
| 1.21.5-1.21.11 | Yes | No | Yes |

Notes:
- Quilt uses the Fabric-compatible jars.
- Forge support is intentionally capped at `1.21.1`.
- NeoForge `1.20.6+` uses patched named mappings so it can stay on the shared source stack.

## How To Use

1. Open `Controls`.
2. Click `Keyset`.
3. Choose a profile on the left.
4. Review conflicts on the right.
5. Use `Find`, `Clear Key`, `Rebind`, or `Preview Fix`.
6. Apply the preview if it looks correct, or undo it.

## Profile Rules

- The active profile persists across restarts.
- `Save` captures the current live bindings into the selected profile.
- Deleting the active profile falls back to `Default`.
- Missing or currently unavailable keybind ids are preserved and can reactivate when the owning mod is present again.

## Conflict Rules

- Conflicts can be grouped by assigned key or by category.
- Search matches binding names, category names, key labels, and internal ids.
- Cross-category conflicts are still shown.
- Quick actions operate on the active profile only.

## Auto-Fix Rules

- Never changes critical vanilla binds by default: movement, inventory, chat, escape, and drop.
- Prefers modded or less-protected binds first.
- Respects sticky user edits saved into the active profile.
- Prefers unused plain keys before harder-to-reach or modifier-heavy fallbacks.
- Avoids overwriting healthy non-conflicting assignments.
- Produces deterministic output for the same input.

## Import / Export

- Export the selected profile as JSON to the clipboard.
- Import profile JSON from the clipboard.
- Name collisions are renamed safely instead of overwriting an existing profile.
- Invalid or empty payloads are rejected instead of silently seeding defaults.

## Config

Profiles are stored in:

```text
config/keybindprofiles.json
```

Schema:

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

## Build Outputs

Release jars are collected into:

```text
builtJars/fabric/1.0.0-alpha
builtJars/forge/1.0.0-alpha
builtJars/neoforge/1.0.0-alpha
```

Examples:

- `keyset-fabric-1.20.1-1.20.2-1.0.0-alpha.jar`
- `keyset-forge-1.21.1-1.0.0-alpha.jar`
- `keyset-neoforge-1.21-1.21.11-1.0.0-alpha.jar`

## Development

Use JDK 21 when running Gradle from the shell.

Useful commands:

```bash
./gradlew build
./gradlew verifyWorkspace
./gradlew buildFabricTargets
./gradlew buildForgeTargets
./gradlew buildNeoForgeTargets
./gradlew buildAllJars
```

Version-mapped launch helpers:

```bash
./run-fabric.sh 1.21.3
./run-forge.sh 1.21.1
./run-neoforge.sh 1.21.11
```

## Architecture

- `modules/core`
  Shared profile models, JSON persistence, conflict detection, auto-resolve logic, and UI-facing contracts.
- `modules/common/*`
  Version-range shims for Minecraft API differences.
- `platforms/fabric/*`
  Fabric and Quilt-compatible leaf modules.
- `platforms/forge/*`
  Forge leaf modules through `1.21.1`.
- `platforms/neoforge/*`
  NeoForge leaf modules, including the modern patched-mapping targets.

## Modpack Notes

- Client-side only
- Safe to include in packs
- Does not add gameplay content
- Designed to stay additive instead of fighting other keybind mods

## Status

Current release line: `1.0.0-alpha`
