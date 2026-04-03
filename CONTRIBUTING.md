# Contributing

## Development Flow
- Keep `main` stable and buildable.
- Use `develop` as the integration branch for ongoing work.
- Keep Minecraft version-range work isolated from feature work.
- Update `README.md` and `CHANGELOG.md` after each coherent chunk.
- Use the GitHub issue forms for bug reports and suggestions so loader/version/log details stay consistent.

## Branch Roles
- `feature/*`: generic features and UI work.
- `fix/*`: generic bug fixes.
- `mc/common/*`: shared Minecraft API range shims.
- `mc/fabric/*`, `mc/forge/*`, `mc/neoforge/*`: loader-specific version work only.
- `release/*`: release prep, metadata, changelog, and README polish.

## Build Commands
- Use Java 25 to run Gradle on this branch. The active release line includes Minecraft 26.1 targets, and Gradle toolchains handle the lower target versions from there.
- CI also runs on JDK 25.
- `./gradlew build`
- `./gradlew buildRepresentativeTarget`
- `./gradlew buildSmokeTargets`
- `./gradlew buildFabricSmokeTarget`
- `./gradlew buildForgeSmokeTarget`
- `./gradlew buildNeoforgeSmokeTarget`
- `./gradlew buildTargetJars`
- `./gradlew collectTargetJars`
- `./gradlew verifyReleaseBundle`
- `./gradlew verifyWorkspace`
- `./gradlew publishMods -PpublishDryRun=true`

Release jars are collected into `builtJars/<version>/<loader>`.
GitHub Actions now builds and uploads the canonical `builtJars/` bundle on CI runs.
The bundle also includes generated `GITHUB_CHANGELOG.md`, `MODRINTH_CHANGELOG.md`, and `BUILD_INFO.md` metadata files.

## Module Layout
- `core`: loader-agnostic logic, data models, serialization, conflict engine, auto-resolve engine, and UI contracts.
- `common-v1_20_1-to-v1_20_2`: the first shared Minecraft API range adapter.
- `platform-*`: thin loader entrypoints and platform glue.

## Quality Rules
- Keep core logic pure and testable.
- Keep version shims small and explicit.
- Do not add polling-heavy runtime behavior.
- Document public APIs and unusual compatibility decisions.
- Pull requests should summarize affected loaders/version ranges and include verification notes.
