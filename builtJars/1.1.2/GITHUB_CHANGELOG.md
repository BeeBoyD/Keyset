# Keyset 1.1.2

`1.1.2` adds NeoForge support for Minecraft 26.1 — the first fully unobfuscated (MojMap) Minecraft release. Fabric and Quilt 26.1 support shipped in `1.1.1`; NeoForge now reaches full parity.

No changes to existing `1.20.1–1.21.11` jars.

## What's new

- **NeoForge support for Minecraft 26.1** — full feature parity with the Fabric/Quilt 26.1 jar: profiles, conflict browser, Safe Fix, clipboard JSON, and the Controls-screen button on both the `Controls` page and the `Key Binds` sub-screen.
- Uses `dev.architectury.loom-no-remap` 1.14.473, the toolchain that enables native MojMap NeoForge builds.

## Loaders

| Minecraft | Fabric | Quilt | Forge | NeoForge |
| --- | --- | --- | --- | --- |
| 26.1 | ✅ | ✅ | ❌ | ✅ |

Forge 26.1 support is pending upstream loader tooling.

## Packaged targets

### NeoForge

- `26.1` → `keyset-neoforge-26.1-1.1.2.jar`

## Notes

- Quilt uses the Fabric-compatible jar (shipped in `1.1.1`).
- Existing `1.1.1` Fabric/Quilt jar for `26.1` and all `1.1.0` jars for `1.20.1–1.21.11` are unchanged.
