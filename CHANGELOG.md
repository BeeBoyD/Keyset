# Changelog

## Unreleased
### Added
- Gradle multi-project scaffold with shared core, a version-range adapter, and initial platform modules.
- A first Fabric 1.20.1 build target for representative validation.
- Spotless formatting, Gradle wrapper support, and a short contributing guide.
- Immutable core profile models, profile lifecycle operations, and JSON persistence with safe file writes.
- Unit tests covering starter profiles, fallback behavior, and profile config round-trips.
- Loader-agnostic live binding descriptors and a pure conflict analysis engine with key/category grouping.
- Conflict query support for search filtering without re-reading live Minecraft state.
- A real Fabric 1.20.1 Keyset screen opened from Controls, with profile create/rename/duplicate/delete/apply/capture flows.
- Clipboard export/import for profile JSON and a first safe auto-resolve preview/apply/undo flow on the active profile.
- A scrollable conflict list with per-binding jump, clear, and reassign actions wired into the vanilla keybind editor.
- Fabric leaf modules for `1.20.3-1.20.6` and `1.21-1.21.11`, each backed by their own thin version shim.

### Changed
- Defined the support matrix and documented the multi-loader architecture in the README.
- Documented the import behavior for currently unavailable keys.
- Documented starter profile seeding and stable internal profile ids in the README.
- Documented conflict grouping and search behavior in the README.
- Updated the README to reflect the playable Fabric 1.20.1 vertical slice and the current auto-resolve behavior.
- Manual edits made through the vanilla keybind screen now sync back into the active Keyset profile as sticky user changes.
- The Fabric UI now explains the workflow directly with tooltips, profile-state badges, clearer action labels, and selected-profile conflict previews.
- Upgraded the Fabric build stack to Gradle `9.2.0` and Loom `1.15.4` so the latest wired Fabric range can build cleanly.

### Fixed
- Core JUnit tests now run correctly under the newer Gradle wrapper by including the JUnit Platform launcher at runtime.
