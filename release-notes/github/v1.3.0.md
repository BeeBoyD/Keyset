## Highlights

- Added direct `Activate Profile Slot 1-5` hotkeys across the active Fabric, Forge, and NeoForge targets, so the first five saved profiles can be applied instantly without opening the Keyset screen.
- Moved Keyset's hotkeys into a dedicated `Keyset` category instead of the generic misc bucket, which keeps the Controls screen cleaner and makes the new slot binds easier to find.

## Context

- This release follows the shortcut-profile request in [issue #2](https://github.com/BeeBoyD/Keyset/issues/2).
- Thanks to `PhunghisKhan` for the suggestion and for helping steer the feature toward direct slot activation.

## Changed

- Fabric active leaves now expose `activateProfileByIndex(...)` in the platform service layer so slot-based activation reuses the existing persisted profile order without changing the core config model.
- Modern `1.21.11` and `26.1` client paths now construct the newer category record types with loader-appropriate identifier objects instead of the older string-based category constructor.

## Compatibility

- Supported release targets are unchanged for `1.3.0`: Fabric/Quilt `1.20.1-1.21.11` plus `26.1`, Forge `1.20.1-1.21.1`, and NeoForge `1.20.1-1.21.11` plus `26.1`.
- Existing profile data remains compatible. Slot hotkeys follow the current saved profile order, so slot 1 maps to the first profile, slot 2 to the second, and so on.
