# Changelog

## Unreleased

Last updated: 2026-02-28 17:24 EET

### Added

- Multi-project Gradle scaffold with shared core modules, version shims, and loader leaf targets.
- Spotless formatting, Gradle wrapper support, and workspace verification tasks.
- Immutable core profile models, starter profiles, lifecycle operations, and JSON persistence with safe file writes.
- Unit coverage for starter profiles, fallback behavior, and config round-trips.
- Loader-agnostic live binding descriptors and a pure conflict analysis engine with grouping and filtering.
- A playable Fabric UI flow with profile management, conflict browsing, direct bind actions, clipboard import/export, and safe auto-fix preview/apply/undo.
- Exact Fabric leaf modules for `1.16.5`, `1.17.1`, `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.4`, `1.21.9`, and `1.21.11`.
- Forge leaf modules for `1.16.5`, `1.17.1`, `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1-1.20.2`, and `1.20.4`.
- NeoForge leaf modules for `1.20.1-1.20.2` and `1.20.4`.
- A root `run-fabric.sh` helper that maps requested Fabric versions to the nearest supported dev-launch leaf.

### Changed

- Documented the support matrix and the multi-loader architecture in the README.
- Documented import behavior for unavailable keys, starter-profile seeding, stable profile ids, and auto-fix rules.
- Synced manual edits made in the vanilla keybind screen back into the active Keyset profile as sticky user changes.
- Iterated on the Fabric UI with a more compact layout, tooltips, profile state badges, contextual footer guidance, and selected-profile conflict previews.
- Upgraded the modern Fabric build stack to Gradle `9.2.0` and Loom `1.15.4`.
- Documented the per-range Java toolchain strategy and exact verified Fabric/Forge/NeoForge targets.
- Refined the public README into a mod-page style overview with clearer setup, behavior, and development sections.

### Fixed

- Fixed Forge and NeoForge dev resource roots by adding valid `pack.mcmeta` metadata, removing the old `failed to load a valid ResourcePackInfo` warning from current `1.20.x` launches.
- Fixed the shared modern `1.20.4+` Keyset screen shell so its custom panels render from `renderBackground(...)` on clients whose base `Screen.render(...)` repaints the background.
- Fixed the shared `1.20.1-1.20.2` Keyset screen render path so Forge, NeoForge, and Fabric clients that do not call `renderBackground(...)` from `Screen.render(...)` do not leave stale Controls frames or tooltip trails behind the Keyset UI.
- Fixed the `1.21.11` crash caused by applying background blur twice in one frame.
- Fixed modern Fabric custom text rendering so badges, headers, empty states, conflict rows, and rebind helper text no longer appear as blank hit targets.
- Fixed the `1.21.11` conflict list renderer so row text uses entry geometry instead of mouse coordinates while hovered.
- Fixed overlapping and clipped controls on the Fabric Keyset screen by moving auto-fix actions into a shared footer row, trimming verbose copy, and preserving list space on short windows.
- Fixed Fabric screen text layering and button placement regressions across old and new leaves.
- Fixed legacy Fabric metadata so `1.16.5` and `1.17.1` use the correct `fabric` aggregator id.
- Fixed version-leaf wiring for `1.19.2`, `1.20.4`, `1.21.1`, `1.21.4`, and `1.21.9` so each target uses the correct screen/input/keybinding API surface.
- Fixed core JSON parsing to stay compatible with older Gson versions bundled by legacy Forge leaves.
- Fixed legacy Forge bootstrap shims for `1.17.1`, `1.18.2`, and `1.19.2`.
- Fixed NeoForge bootstrap wiring for `1.20.1` and `1.20.4`.
- Fixed NeoForge `1.20.4` metadata so the dev runtime uses the correct `mods.toml` format and loader range.

## v0.1.0 - TBD

- Added: Initial public release not cut yet.
- Changed: Pending release branch.
- Fixed: Pending release branch.
