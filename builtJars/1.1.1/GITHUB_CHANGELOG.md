# Keyset 1.1.1

`1.1.1` adds Minecraft 26.1 support on Fabric and Quilt. 26.1 is the first fully unobfuscated Minecraft release (MojMap names shipped natively).

No changes to existing `1.20.1–1.21.11` jars.

## What's new

- **Fabric / Quilt support for Minecraft 26.1** — full feature parity with the `1.20.1+` line: profiles, conflict browser, Safe Fix, clipboard JSON, Controls-screen button.
- **Controls-screen button injection for 26.1** — MC 26.1 splits the Controls page into a top-level `Controls` screen and a `Key Binds` sub-screen. The Keyset button is now injected into both.
- **Language file included** — all UI strings resolve correctly on first launch.

## Loaders

| Minecraft | Fabric | Quilt | Forge | NeoForge |
| --- | --- | --- | --- | --- |
| 26.1 | ✅ | ✅ | ❌ | ❌ |

Forge and NeoForge 26.1 support is pending upstream loader tooling.

## Packaged targets

### Fabric / Quilt

- `26.1`

## Notes

- Quilt uses the Fabric-compatible jar.
- Existing `1.1.0` jars for `1.20.1–1.21.11` are unchanged and remain the current release for those versions.
