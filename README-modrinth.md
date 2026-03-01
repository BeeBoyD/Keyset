![Keyset logo](logo-modrinth.png)

# Keyset

[![Modrinth](https://img.shields.io/modrinth/dt/keyset?logo=modrinth&label=Modrinth)](https://modrinth.com/project/keyset)
[![Source](https://img.shields.io/badge/source-GitHub-24292f)](https://github.com/BeeBoyD/Keyset)
[![License](https://img.shields.io/github/license/BeeBoyD/Keyset)](https://github.com/BeeBoyD/Keyset/blob/main/LICENSE)
[![Client-Side](https://img.shields.io/badge/side-client--side-4caf50)](https://modrinth.com/project/keyset)

Keyset is a vanilla-friendly keybind manager for modded Minecraft.

It gives you multiple control profiles, a clear conflict view, and a safe auto-fix flow that helps clean up overloaded modpack controls without stomping over the important vanilla keys.

## ✨ Features

- Multiple keybind profiles with instant switching
- Built-in starter profiles: `Default`, `PvP`, `Building`, `Tech`
- Conflict browser with search and grouping by key or category
- Quick actions for a selected conflict: `Find`, `Clear Key`, `Rebind`
- Safe auto-fix with preview, apply, and undo
- Clipboard JSON export/import
- Persistent profile storage in `config/keybindprofiles.json`

## 📦 Downloads

Download Keyset from [Modrinth](https://modrinth.com/project/keyset).

Published files are split by loader and Minecraft range, for example:

- `keyset-fabric-1.20.1-1.20.2-1.0.0-alpha.jar`
- `keyset-forge-1.21.1-1.0.0-alpha.jar`
- `keyset-neoforge-1.21-1.21.11-1.0.0-alpha.jar`

## ✅ Supported Versions

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
- Forge `1.16.5` and `1.17.1` are included as early alpha targets.

## 🎮 How To Use

### 1. Open Keyset

1. Open Minecraft's `Controls` screen.
2. Click the `Keyset` button.
3. The left side shows profiles.
4. The right side shows conflicts for the current profile.

### 2. Pick Or Create A Profile

- Start with `Default` if you want a baseline profile.
- Use `New` to make a new profile.
- Use `Duplicate` if you want a variation of an existing setup.
- Use `Rename` to clean up profile names.
- Use `Delete` to remove one you no longer need.
- If you delete the active profile, Keyset safely falls back to `Default`.

### 3. Save Your Current Controls

- Set up your keys in the normal Controls screen or inside Keyset's quick actions.
- Click `Save` to capture the current live keybind state into the selected profile.
- Switch profiles at any time to apply that saved layout immediately.

### 4. Review Conflicts

- Use the search box to narrow by binding name, category, key label, or internal id.
- Toggle grouping to see conflicts by assigned key or by category.
- Select a conflict row to unlock quick actions.

### 5. Fix A Conflict Manually

When a conflict row is selected:

- `Find` jumps to the matching binding in the vanilla Controls list.
- `Clear Key` removes that key assignment immediately.
- `Rebind` takes you through the normal reassignment flow.

### 6. Use Safe Auto-Fix

- Click `Preview Fix` to generate a proposed conflict cleanup.
- Review what Keyset wants to change before applying it.
- Click `Apply Fix` if the preview looks correct.
- Use `Undo Fix` if you want to revert the last auto-fix pass.

### 7. Export Or Import Profiles

- Use `Export` to copy the selected profile as JSON.
- Use `Import` to paste profile JSON back in.
- If a profile name already exists, Keyset renames the imported one safely instead of overwriting your current profile.

## 🧠 Rules And Behavior

### Profiles

- The active profile persists across restarts.
- `Save` captures the current live bindings into the selected profile.
- Missing or currently unavailable keybind ids are preserved and can reactivate when the owning mod appears again.

### Conflicts

- Conflicts can be grouped by assigned key or by category.
- Search matches binding names, category names, key labels, and internal ids.
- Cross-category conflicts are still shown.
- Quick actions operate on the active profile only.

### Auto-Fix

- Never changes critical vanilla binds by default: movement, inventory, chat, escape, and drop.
- Prefers modded or less-protected binds first.
- Respects sticky user edits saved into the active profile.
- Prefers unused plain keys before harder-to-reach or modifier-heavy fallbacks.
- Avoids overwriting healthy non-conflicting assignments.
- Produces deterministic output for the same input.

## 🔁 Import / Export

- Export the selected profile as JSON to the clipboard.
- Import profile JSON from the clipboard.
- Name collisions are renamed safely instead of overwriting an existing profile.
- Invalid or empty payloads are rejected instead of silently seeding defaults.

## 🔒 Privacy

Keyset does not collect analytics, send telemetry, or upload your keybind data anywhere.

The mod stores its profile data locally in:

```text
config/keybindprofiles.json
```

That file stays on your machine unless you choose to export or share it yourself.

## 🧩 Modpack Notes

- Client-side only
- Safe to include in packs
- Does not add gameplay content
- Designed to stay additive instead of fighting other keybind mods

## 🚧 Status

Current release line: `1.0.0-alpha`
