# Keyset

Keyset is a vanilla-friendly keybind manager for modded Minecraft.

It gives you multiple control profiles, a clear conflict view, and a safe auto-fix flow that helps clean up messy modpack bindings without stomping over the important vanilla keys.

## ✨ Features

- Multiple keybind profiles with instant switching
- Built-in starter profiles: `Default`, `PvP`, `Building`, `Tech`
- Conflict browser with search and grouping by key or category
- Quick actions for a selected conflict: `Find`, `Clear Key`, `Rebind`
- Safe auto-fix with preview, apply, and undo
- Clipboard JSON export/import
- Persistent profile storage in `config/keybindprofiles.json`

## 📦 Downloads

Download the latest alpha builds from [GitHub Releases](https://github.com/BeeBoyD/Keyset/releases).

Release assets are published per loader and Minecraft range, for example:

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

1. Open `Controls`.
2. Click `Keyset`.
3. Choose a profile on the left.
4. Review conflicts on the right.
5. Use `Find`, `Clear Key`, `Rebind`, or `Preview Fix`.
6. Apply the preview if it looks correct, or undo it.

## 🧠 How It Behaves

### Profiles

- The active profile persists across restarts.
- `Save` captures the current live bindings into the selected profile.
- Deleting the active profile falls back to `Default`.
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

## 🧩 Modpack Notes

- Client-side only
- Safe to include in packs
- Does not add gameplay content
- Designed to stay additive instead of fighting other keybind mods

## ⚙️ Config

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

## 🚧 Status

Current release line: `1.0.0-alpha`
