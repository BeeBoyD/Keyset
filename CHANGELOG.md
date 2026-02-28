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
- Legacy Fabric leaf modules for `1.16.5`, `1.17.1`, `1.18.2`, and `1.19.x`, backed by dedicated `common-v1_16_5-to-v1_18_x` and `common-v1_19_x` shims.
- Exact Fabric leaf modules for `1.19.2`, `1.20.4`, `1.21.1`, `1.21.4`, and `1.21.9`, so the current Fabric matrix can be verified against real API breakpoints instead of optimistic shared ranges.

### Changed
- Defined the support matrix and documented the multi-loader architecture in the README.
- Documented the import behavior for currently unavailable keys.
- Documented starter profile seeding and stable internal profile ids in the README.
- Documented conflict grouping and search behavior in the README.
- Updated the README to reflect the playable Fabric 1.20.1 vertical slice and the current auto-resolve behavior.
- Manual edits made through the vanilla keybind screen now sync back into the active Keyset profile as sticky user changes.
- The Fabric UI now explains the workflow directly with tooltips, profile-state badges, clearer action labels, and selected-profile conflict previews.
- Upgraded the Fabric build stack to Gradle `9.2.0` and Loom `1.15.4` so the latest wired Fabric range can build cleanly.
- The Fabric UI now adapts better to short screens by shrinking the detail card, preserving the conflict list, and moving more guidance into contextual copy and hover tooltips.
- Documented the per-range Java toolchain strategy, including the Java 17 Loom dev-launch requirement for `1.16.5` and `1.17.1`.
- Updated the README to list the exact Fabric versions that now compile and dev-launch cleanly.
- Unified the Fabric Keyset screen layout across every currently wired Fabric leaf so the compact two-pane flow, footer actions, and selection summary behave consistently from `1.16.5` through `1.21.11`.

### Fixed
- Core JUnit tests now run correctly under the newer Gradle wrapper by including the JUnit Platform launcher at runtime.
- Fixed the `1.21.11` crash caused by applying background blur twice in one frame.
- Fixed legacy Fabric metadata so `1.16.5` and `1.17.1` depend on the correct aggregator mod id (`fabric`) instead of the newer `fabric-api` id.
- Fixed Fabric version-leaf wiring so `1.19.2`, `1.20.4`, `1.21.1`, `1.21.4`, and `1.21.9` all use the correct screen/input/keybinding APIs for their exact Minecraft versions.
- Fixed the Fabric screen UX on both legacy and modern versions with contextual footer help, more actionable tooltips, and clearer conflict summary text.
- Fixed overlapping and clipped controls on the Fabric Keyset screen across legacy and modern versions by moving auto-fix actions into a shared footer row, trimming verbose copy, and preserving list space on short windows.
