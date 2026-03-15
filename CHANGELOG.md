# Changelog

## Unreleased

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
