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
- Use JDK 17 for the current Fabric 1.20.1 target line.
- `./gradlew build`
- `./gradlew buildRepresentativeTarget`
- `./gradlew buildTargetJars`
- `./gradlew collectTargetJars`
- `./gradlew verifyWorkspace`

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
