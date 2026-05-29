### Added

- New Keyset 2.0 Fabric UI with tabbed Bindings, Conflicts, Auto-Switch, and Share workflows across active Fabric 1.20.x and 1.21.x targets.
- Profile sharing flow with generated import codes, import confirmation, local share history, and empty-profile validation.
- Auto-switch rules for applying profiles when joining matching servers.

### Changed

- Dialogs now use sharp dim overlays with vertically centered content instead of the vanilla blurred background.
- Toasts now appear from the bottom-left, stack upward, and auto-dismiss after ten seconds.
- Fabric 26.1 metadata now targets Minecraft 26.1.2 with current Fabric Loader, Fabric API, and matching NeoForge metadata.

### Fixed

- Fixed the Conflicts tab count badge so the count is drawn once without the double-shadow artifact.
- Fixed tutorial and in-place UI rebuilds so minor state changes do not restart the full screen fade.
- Improved Share tab sizing, import placeholder behavior, invalid-code warning display, hover polish, conflict key tinting, and empty states.
