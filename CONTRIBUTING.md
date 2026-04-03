# Contributing

## Development Flow
- Keep `main` stable and buildable.
- Use `develop` as the integration branch for ongoing work.
- Keep Minecraft version-range work isolated from feature work.
- Update `README.md` and `CHANGELOG.md` after each coherent chunk.

## Branch Roles
- `feature/*`: generic features and UI work.
- `fix/*`: generic bug fixes.
- `mc/common/*`: shared Minecraft API range shims.
- `mc/fabric/*`, `mc/forge/*`, `mc/neoforge/*`: loader-specific version work only.
- `release/*`: release prep, metadata, changelog, and README polish.

## Build Commands
- Use Gradle toolchains instead of pinning your shell JDK to one Minecraft line.
- CI uses JDK 25 and lets Gradle provision lower toolchains for older targets when needed.
- `./gradlew build`
- `./gradlew buildRepresentativeTarget`
- `./gradlew buildSmokeTargets`
- `./gradlew buildFabricSmokeTarget`
- `./gradlew buildForgeSmokeTarget`
- `./gradlew buildNeoforgeSmokeTarget`
- `./gradlew buildTargetJars`
- `./gradlew collectTargetJars`
- `./gradlew verifyWorkspace`
- `./gradlew publishMods -PpublishDryRun=true`

Release jars are collected into `builtJars/<version>/<loader>`.

## Module Layout
- `core`: loader-agnostic logic, data models, serialization, conflict engine, auto-resolve engine, and UI contracts.
- `common-v1_20_1-to-v1_20_2`: the first shared Minecraft API range adapter.
- `platform-*`: thin loader entrypoints and platform glue.

## Quality Rules
- Keep core logic pure and testable.
- Keep version shims small and explicit.
- Do not add polling-heavy runtime behavior.
- Document public APIs and unusual compatibility decisions.
