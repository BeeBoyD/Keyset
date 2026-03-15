# Keyset 1.0.0 Technical Changelog

Release date: `2026-03-14`

## Release Scope

`1.0.0` promotes Keyset from the `1.0.0-alpha` line to a stable multi-loader release for Fabric, Quilt, Forge, and NeoForge users. This release finalizes the modern UI pass, stabilizes the responsive layout work that followed in-game testing, and cleans up the public-facing documentation for the stable line.

> Support policy note:
> Future releases for Minecraft versions below `1.20.1` will be limited to critical bug fixes only.
> Profile compatibility will stay the same across the supported release line.

## UI And Workflow Changes

- Reworked the modern `1.20.1+` screen flow into a clearer dashboard layout with dedicated profile, navigator, and Safe Fix areas.
- Tightened labels, action names, and helper copy so live edits, previews, and cleanup actions are easier to interpret in-game.
- Improved readability for conflict browsing by reducing layout density and making the selected-bind actions easier to identify.
- Preserved the legacy `1.16.5-1.19.4` screen path instead of forcing the new layout onto older implementations.

## Responsive Layout Fixes

- Added width-aware and height-aware layout fallbacks to prevent text and controls from overlapping on smaller windows.
- Capped and wrapped status chips instead of allowing header collisions.
- Reserved action-card space before sizing stacked conflict content.
- Added compact and paged fallbacks for narrow or short detail views.
- Lowered the compact-mode threshold slightly so full-page detail screens remain usable on smaller windows before paging kicks in.

## Compatibility And Behavior

- Profile data remains compatible across the supported release line.
- Legacy builds below `1.20.1` remain available, but future work there is now focused on critical bug-fix maintenance.
- Quilt continues to use the Fabric-compatible jars.
- Forge support remains intentionally capped at `1.21.1`.

## Artifact Matrix

Fabric / Quilt assets:

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

Forge assets:

- `1.16.5`
- `1.17.1`
- `1.18.2`
- `1.19.2`
- `1.19.4`
- `1.20.1-1.20.2`
- `1.20.3-1.20.6`
- `1.20.4`
- `1.21.1`

NeoForge assets:

- `1.20.1-1.20.2`
- `1.20.4`
- `1.20.6`
- `1.21-1.21.11`
- `1.21.1`
- `1.21.4`

## Operational Notes

- Release assets are published per loader and Minecraft range rather than as one universal jar.
- Public docs now include GitHub, Modrinth, and CurseForge download paths.
- The GitHub release body is intended to stay technical, but public-facing, without exposing internal repository-only workflow details.
