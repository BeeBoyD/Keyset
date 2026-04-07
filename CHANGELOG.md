# Changelog

## Unreleased

## v1.1.3 - 2026-04-07

### Fixed

- Fabric `fabric.mod.json` MC version ranges were incorrect across the entire 1.20.x–1.21.x ladder, causing launchers and modpack tools to load the wrong jar for a given MC version. Most critically, the `1.21.10–1.21.11` jar (compiled against MC 1.21.11, which uses `KeyBinding$Category`/`class_11900`) was accepted by Fabric Loader on MC 1.21.1 where that class does not exist, causing an immediate `NoClassDefFoundError` crash on startup.
- Added `verifyFabricModRanges` build task (wired into `verifyWorkspace`) to catch MC range overlaps and gaps at build time, preventing regressions.

## v1.1.2 - 2026-03-30

### Added

- NeoForge support for Minecraft 26.1 — full feature parity with the Fabric/Quilt 26.1 jar. Uses `dev.architectury.loom-no-remap` 1.14.473 for the first unobfuscated (MojMap) NeoForge release.

### Changed

- Supported-versions table updated: 26.1 NeoForge is now ✅.

## v1.1.1 - 2026-03-28

### Added

- Fabric support for Minecraft 26.1 — the first fully unobfuscated (MojMap) release. Keyset now runs on MC 26.1 with Fabric Loader.
- Quilt support for MC 26.1 via the Fabric-compatible jar (no separate artifact needed).
- The Keyset button is injected into both the `Controls` screen and the new `Key Binds` sub-screen that MC 26.1 splits controls across.
- Missing `en_us.json` language file for MC 26.1 so all UI strings display correctly instead of raw translation keys.

### Changed

- Supported-versions table now includes MC 26.1 (Fabric/Quilt only; Forge and NeoForge 26.1 are pending upstream loader tooling).

## v1.1.0 - 2026-03-20

Released: 2026-03-20

### Changed

- Moved the active release line to `1.1.0` and narrowed packaged release targets to Minecraft `1.20.1+`.
- Updated the public docs to make the modern release focus and legacy critical-fix policy clearer.
- Tightened the modern UI copy so key actions, conflict selection, and recovery states are easier to understand.
- Reworked the modern `1.20.1+` screen layout so compact and tiny windows scale down more gracefully instead of forcing full-size card content into the same space.

### Fixed

- Replaced broad "find the Keyset button by text" cleanup with per-screen button tracking, so reopening `Controls` or changing GUI scale no longer risks duplicate buttons or cross-mod collisions.
- Prevented the global open-screen hotkey from stacking Keyset on top of an already-open Keyset screen.
- Fixed multiple modern UI overlap cases by reserving real header space inside paged cards, shrinking compact conflict rows, and trimming low-space summaries before they collide with action buttons.
- Fixed `1.21.9` Fabric resource packaging so shared assets like the embedded mod icon are present in release jars.
- Stopped swallowing manual Controls-sync failures and now surface clear status messages when manual edits are saved back into the active profile.
- Added safer config recovery with automatic backup refresh, backup-based recovery, timestamped broken-file archiving, and visible recovery warnings.

## v1.0.0 - 2026-03-14

Released: 2026-03-14 13:30 EET

### Added

- Launch branding assets and a Modrinth-focused project page.

### Changed

- Promoted the active release line from `1.0.0-alpha` to `1.0.0`.
- Rebuilt the modern UI into a dashboard layout with a profile deck, conflict navigator, clearer action cards, and richer conflict rows.
- Refined button labels, helper copy, and tooltips so live edits, preview mode, and Safe Fix are easier to understand.
- Rewrote the public README and Modrinth copy for the stable launch.
- Updated the public-facing mod description to better explain the core value of the mod.

### Fixed

- Kept the redesigned modern UI compiling across the current `1.20.1+` DrawContext leaves: `1.20.1`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.4`, `1.21.9`, and `1.21.11`.
- Fixed Fabric / Quilt `1.0.0` runtime bundling so shared profile classes are present in shipped release jars.
- Replaced release-brittle private-field reflection in the Controls button flow and keybind jump screen with remap-safe runtime paths.
- Made the Controls screen button injection idempotent so reopening the screen or changing GUI scale does not create duplicate buttons in fallback corners.
- Cleaned the release collection flow so versioned artifacts are gathered directly into `builtJars/<version>/<loader>`.

## v1.0.0-alpha - 2026-03-01

Released: 2026-03-01 09:15 EET

### Added

- Shared core modules for profile models, config persistence, conflict analysis, and safe auto-resolve logic.
- Starter profiles: `Default`, `PvP`, `Building`, and `Tech`.
- Profile create, rename, duplicate, delete, save, and instant switching flows.
- Conflict browser with search, grouping, and selected-conflict quick actions.
- Preview/apply/undo auto-fix flow.
- Clipboard JSON export/import.
- Fabric / Quilt targets from `1.16.5` through `1.21.11`.
- Forge targets from `1.16.5` through `1.21.1`.
- NeoForge targets from `1.20.1` through `1.21.11`.
- Root launch helpers: `run-fabric.sh`, `run-forge.sh`, and `run-neoforge.sh`.
- Versioned collected release jars in `builtJars/<version>/<loader>`.

### Changed

- Reorganized the repo into `modules/` and `platforms/` to make the multiloader layout easier to maintain.
- Standardized jar names so supported Minecraft ranges are visible in the artifact filename.
- Refined the public README into a cleaner release-facing project page.
- Capped the active Forge line at `1.21.1`.
- Set the active release version to `1.0.0-alpha`.

### Fixed

- Core JSON parsing compatibility for older Gson versions bundled by legacy leaves.
- Screen rendering issues across old and modern versions, including text visibility, selection geometry, clipping, and screen redraw regressions.
- Forge and NeoForge resource-pack metadata so current dev launches no longer fail resource discovery due to missing `pack.mcmeta`.
- NeoForge `1.20.6+` bootstrap and metadata wiring so modern NeoForge leaves load as valid mods and initialize correctly.
- Forge `1.21.1` userdev bootstrap so the synthetic Minecraft module stays named `minecraft` and the client launches cleanly in the current Loom-based setup.
