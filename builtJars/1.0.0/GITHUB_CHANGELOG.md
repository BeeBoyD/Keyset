# Keyset 1.0.0 Technical Changelog

Release date: `2026-03-14`

## Release Scope

`1.0.0` promotes Keyset from the `1.0.0-alpha` line to a stable multi-loader release with synchronized artifacts for Fabric, Forge, and NeoForge. The release also finalizes the modern UI pass, shared release-copy cleanup, and the follow-up responsive layout fixes that were required after real in-game validation on multiple window sizes.

## UI And UX Changes

- Reworked the modern screen implementation into a dashboard-style layout with dedicated profile, navigator, and Safe Fix areas.
- Reduced cognitive load across the UI by shortening button labels, helper copy, and state chips.
- Added responsive layout handling for constrained widths and heights:
  - chips cap and wrap instead of colliding
  - stacked panels reserve card space before sizing the conflict list
  - compact/paged fallbacks activate before overlapping text or controls can render
- Lowered the compact-mode threshold for full-page detail views so the navigator/fix pages stay usable on slightly smaller windows.
- Kept the legacy `1.16.5-1.19.4` screen path intact rather than forcing the new layout onto older implementations.

## Cross-Version Modern UI Work

- Ported and stabilized the screen implementation across the current DrawContext-based `1.20.1+` leaves:
  - `1.20.1`
  - `1.20.4`
  - `1.20.6`
  - `1.21.1`
  - `1.21.4`
  - `1.21.9`
  - `1.21.11`
- Preserved per-version rendering shims where APIs differ:
  - `drawBorder(...)` vs `drawStrokedRectangle(...)`
  - `setLeftPos(...)` fallback on older list widgets
  - version-specific visibility handling in conflict list widgets

## Docs And Release Assets

- Updated the root README and Modrinth README for the stable launch.
- Refreshed the public changelog to document the stable release transition.
- Standardized the release bundle so built artifacts can be gathered under a versioned `builtJars/1.0.0` directory with loader-specific subfolders.

## Artifact Matrix

Fabric artifacts:

- `1.16.5`
- `1.17.1`
- `1.18.2`
- `1.19.2`
- `1.19.4`
- `1.20.1-1.20.2`
- `1.20.3-1.20.6`
- `1.20.4`
- `1.21-1.21.11`
- `1.21.1`
- `1.21.4`
- `1.21.9`

Forge artifacts:

- `1.16.5`
- `1.17.1`
- `1.18.2`
- `1.19.2`
- `1.19.4`
- `1.20.1-1.20.2`
- `1.20.3-1.20.6`
- `1.20.4`
- `1.21.1`

NeoForge artifacts:

- `1.20.1-1.20.2`
- `1.20.4`
- `1.20.6`
- `1.21-1.21.11`
- `1.21.1`
- `1.21.4`

## Validation

- Local merge status:
  - `main` contains `fix: stabilize responsive keyset release ui` (`d94df6f`)
  - `develop` contains the same work through merge commit `201ef8a`
- Runtime validation completed during the stabilization pass for representative targets, including Fabric and Forge.
- Full jar collection was executed through Gradle release collection tasks before assembling this bundle.
