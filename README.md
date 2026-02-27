# Keyset

Keybind profiles and conflict resolution for Minecraft (client-side).

## Features
- Multiple keybind profiles with instant switching
- Conflict visualization with grouping and search
- One-click auto-resolve with preview and undo
- Export/import profiles (JSON + clipboard)
- Clean UI that fits vanilla style

## Supported Versions / Loaders (Planned)
| Minecraft | Fabric / Quilt | Forge | NeoForge | Status |
| --- | --- | --- | --- | --- |
| 1.16.5–1.18.x | Planned | Planned | N/A | Planned |
| 1.19.x | Planned | Planned | N/A | Planned |
| 1.20.1–1.20.2 | Planned | Planned | Planned | Planned |
| 1.20.3–1.20.6 | Planned | Planned | Planned | Planned |

Initial development target: 1.20.1 (Fabric) for scaffolding validation.

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

## Modpack Notes
- Client-side only; safe to include on servers.
