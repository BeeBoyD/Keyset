# Keyset

Keybind profiles and conflict resolution for Minecraft (client-side).

## Features
- Multiple keybind profiles with instant switching
- Conflict visualization with grouping and search
- One-click auto-resolve with preview and undo
- Export/import profiles (JSON + clipboard)
- Clean UI that fits vanilla style

## Supported Versions / Loaders
| Minecraft | Fabric / Quilt | Forge | NeoForge | Status |
| --- | --- | --- | --- | --- |
| 1.16.5-1.18.x | Planned | Planned | N/A | Planned |
| 1.19.x | Planned | Planned | N/A | Planned |
| 1.20.1-1.20.2 | Scaffolded | Scaffolded | Scaffolded | Active range |
| 1.20.3-1.20.6 | Planned | Planned | Planned | Planned |

Current build validation target: Fabric 1.20.1.

## Architecture
- `core`: shared Java module for data models, JSON persistence, conflict detection, auto-resolve, and UI-facing contracts.
- `common-v1_20_1-to-v1_20_2`: first version-range adapter shared by loader leaf modules.
- `platform-fabric-1_20_1`: first fully wired mod target used to validate the scaffold.
- `platform-forge-1_20_1`: Forge placeholder module reserved for loader glue.
- `platform-neoforge-1_20_1`: NeoForge placeholder module reserved for loader glue.

This scaffold keeps feature logic out of loader modules and keeps version shims isolated so later Minecraft bumps can stay narrow.

## How To Use
1. Open Controls.
2. Click the new "Keyset" button.
3. Pick a profile or create one.
4. Review conflicts and apply fixes.

## Profile Rules
- The active profile is persisted across restarts.
- Deleting the active profile safely falls back to Default.

## Auto-Resolve Rules (Safe Defaults)
- Never changes critical vanilla binds by default (movement, inventory, chat, ESC, drop).
- Prefers reassigning modded binds first.
- Respects user-customized binds unless explicitly allowed.
- Prefers unused keys before modifier combos.
- Uses modifiers in order: Shift, Ctrl, Alt.
- Avoids hard-to-reach keys unless no alternatives.
- Deterministic: same input => same output.

## Export / Import
- Export profiles to JSON.
- Import JSON and merge or add as new profiles with collision handling.
- Clipboard import/export is supported when feasible.

## Config
- Stored at `config/keybindprofiles.json` with schema versioning.
- Schema shape starts as `{ "schema": 1, "activeProfile": "...", "profiles": { ... } }`.
- Imported keys that are not currently available will be retained in profile data and treated as inactive until the keybind becomes available again.

## Modpack Notes
- Client-side only; safe to include on servers.

## Development
- Use JDK 17 for the current 1.20.1 validation target.
- `./gradlew build` builds every scaffolded module.
- `./gradlew buildRepresentativeTarget` validates the Fabric 1.20.1 target.
- `./gradlew buildTargetJars` produces the currently wired jar outputs.
- `./gradlew verifyWorkspace` runs formatting and checks.
