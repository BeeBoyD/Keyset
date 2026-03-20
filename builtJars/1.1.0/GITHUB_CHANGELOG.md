# Keyset 1.1.0 Technical Changelog

`1.1.0` starts the modern-only Keyset release line for Minecraft `1.20.1+` across Fabric, Quilt, Forge, and NeoForge.

Older `1.16.5-1.19.4` builds remain on the `1.0.x` line for critical bug fixes only. The profile/config format remains compatible across the supported release line.

## Modern UI and layout stability

- Reworked the `1.20.1+` Keyset screen to scale down more predictably on narrow or short windows.
- Added compact and ultra-compact layout rules for the paged workflow so profile controls, navigator filters, and Safe Fix panels stop fighting for the same pixels.
- Reserved explicit header space inside compact cards instead of drawing section labels on top of live widgets.
- Updated compact conflict rows to use row-height-aware text and badge placement, which removes clipping and vertical overlap in tighter list layouts.
- Trimmed or suppressed low-space summaries before they collide with action buttons in the selected-conflict and Safe Fix cards.

## Controls-screen integration

- Reworked Controls-screen button injection to track the exact Keyset button per screen instance instead of cleaning up by translated text.
- Prevented duplicate Keyset buttons when reopening `Controls` or changing GUI scale.
- Prevented the global open-screen hotkey from stacking Keyset on top of an already-open Keyset screen.
- Kept the release/runtime path remap-safe so the shipped jars use the same button/opening logic as the dev runs.

## Config and manual-edit handling

- Successful saves now refresh `config/keybindprofiles.json.bak`.
- Broken primary configs now trigger backup-based recovery before falling back to defaults.
- Broken primary and backup configs are archived with timestamped suffixes before recovery proceeds.
- Recovery and backup-refresh problems now emit visible status messages inside the modern Keyset screen.
- Manual edits made in vanilla `Controls` now report sync failures instead of silently diverging from the active profile.
- The JSON schema remains unchanged, so existing `keybindprofiles.json` data stays compatible.

## Packaging and release fixes

- Fixed the `1.21.9` Fabric resource source-set wiring so shared assets, including the embedded icon, are present in shipped jars.
- Kept the release bundle focused on the current `1.20.1+` target matrix.
- Rebuilt the `1.1.0` artifacts from a clean workspace with `collectTargetJars`.

## Packaged targets

### Fabric / Quilt

- `1.20.1-1.20.2`
- `1.20.3-1.20.6`
- `1.20.4`
- `1.21.1`
- `1.21.4`
- `1.21.9`
- `1.21-1.21.11`

### Forge

- `1.20.1-1.20.2`
- `1.20.3-1.20.6`
- `1.20.4`
- `1.21.1`

### NeoForge

- `1.20.1-1.20.2`
- `1.20.4`
- `1.20.6`
- `1.21.1`
- `1.21.4`
- `1.21-1.21.11`
