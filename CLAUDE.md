# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Mod Does

Keyset is a **profile-based keybind manager** for modded Minecraft (client-side only). It lets players store full control layouts, switch between them instantly, detect/resolve keybind conflicts across mods, and import/export profiles as JSON.

Current release line: **1.1.x** targets Minecraft 1.20.1+. Legacy **1.0.x** (1.16.5–1.19.4) is repo-maintained for critical fixes only.

## Build Commands

Use **JDK 17** for the current release line.

```bash
./gradlew build                      # Build all modules
./gradlew buildRepresentativeTarget  # Build primary Fabric 1.21.11 target jar
./gradlew buildTargetJars            # Build + collect release jars
./gradlew collectTargetJars          # Copy remapped jars into builtJars/<version>/<loader>/
./gradlew verifyWorkspace            # Format check + run tests across all modules
```

## Running in-game

Convenience scripts resolve the requested version to the nearest supported module:

```bash
./run-fabric.sh <mc-version> [gradle-args]    # e.g. ./run-fabric.sh 1.20.6
./run-forge.sh <mc-version> [gradle-args]
./run-neoforge.sh <mc-version> [gradle-args]
./run-quilt.sh <mc-version> [gradle-args]     # Quilt uses Fabric jars
```

Example: `./run-fabric.sh 1.21.3 --stacktrace` resolves to `:platform-fabric-1_21_4:runClient`.

## Code Formatting

Spotless (Google Java Format) is enforced. Run `./gradlew spotlessApply` to auto-format before committing.

## Architecture

The codebase is a **multi-module, multi-platform Gradle project**. The key constraint: core logic must remain loader-agnostic and version-agnostic.

```
modules/
  core/                        # Pure Java — zero Minecraft/loader imports, only GSON
    conflict/                  # Conflict detection, grouping, querying, auto-resolve
    binding/                   # KeyBinding descriptors
    profile/                   # Profile snapshots, JSON serialization, active profile manager
  common/
    v1_20_1-to-v1_20_2/       # Minecraft API shim for that version range
    v1_20_3-to-v1_20_6/
    v1_21-to-v1_21_11/
    ... (+ legacy ranges)

platforms/
  fabric/{version}/            # Fabric entrypoints, screen hooks, tick listeners
  forge/{version}/
  neoforge/{version}/
```

**Layer rules:**
- `core` — pure business logic; the conflict engine, profile management, JSON serialization all live here. Target Java 8 release.
- `common-v*` — one module per stable Minecraft API generation; bridges the gap between core and loader-specific code. Keep shims small.
- `platform-*` — thin entrypoints only: `ClientModInitializer`, event/tick listeners, UI screens. No business logic here.

## Key Data Flow

1. **Conflict detection**: platform reads live Minecraft keybindings → passes to `KeysetConflicts.detect()` → UI groups by key or category via `KeysetConflictGroupMode`
2. **Profile save**: capture current Minecraft bindings → build `KeysetProfile` → `KeysetProfilesJson.toJson()` → write `config/keybindprofiles.json`
3. **Auto-fix**: `core/conflict/` resolves conflicts deterministically, protecting vanilla binds and respecting sticky user edits

## Module Dependencies

- Core depends only on GSON (`com.google.code.gson:gson:2.10.1`)
- Common shims depend on core + the relevant Minecraft/mappings version
- Platform modules depend on the appropriate common shim + loader API
- Tests use JUnit 5

## Release Artifacts

Built jars land in `builtJars/<version>/<loader>/`. Quilt reuses Fabric jars. Forge is capped at 1.21.1; NeoForge continues beyond.

## Branch Conventions

- `feature/*` — new features and UI
- `fix/*` — bug fixes
- `mc/common/*` — Minecraft API range shims
- `mc/fabric/*`, `mc/forge/*`, `mc/neoforge/*` — loader-specific version work
- `release/*` — release prep, changelog, README polish

Keep `main` stable and buildable. Use `develop` as the integration branch.

## Quality Rules (from CONTRIBUTING.md)

- Keep core logic pure and testable — no Minecraft imports in `modules/core/`
- Keep version shims small and explicit
- Do not add polling-heavy runtime behavior
- Document public APIs and unusual compatibility decisions
