# Changelog

## Unreleased

### Added

- Added a root `run-quilt.sh` helper that maps requested Quilt versions to the matching Fabric-compatible dev target.
- Added a tracked `logo.png` project logo for public-facing repo and future release branding.

### Changed

- Rewrote the README for a public release audience and split Quilt into its own support-matrix column.

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
- Versioned collected release jars in `builtJars/<loader>/<version>`.

### Changed

- Reorganized the repo into `modules/` and `platforms/` to make the multiloader layout easier to maintain.
- Standardized jar names so supported Minecraft ranges are visible in the artifact filename.
- Refined the public README into a cleaner release-facing project page.
- Capped the active Forge line at `1.21.1`.
- Set the active release version to `1.0.0-alpha`.

### Fixed

- Core JSON parsing compatibility for older Gson versions bundled by legacy leaves.
- Fabric UI rendering issues across old and modern versions, including text visibility, selection geometry, clipping, and screen redraw regressions.
- Forge and NeoForge resource-pack metadata so current dev launches no longer fail resource discovery due to missing `pack.mcmeta`.
- NeoForge `1.20.6+` bootstrap and metadata wiring so modern NeoForge leaves load as valid mods and initialize correctly.
- Forge `1.21.1` userdev bootstrap so the synthetic Minecraft module stays named `minecraft` and the client launches cleanly in the current Loom-based setup.
