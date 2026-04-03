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
- By default, the Gradle workspace includes the active `1.20.1+` release line only. Include legacy maintenance targets with `-PincludeLegacyTargets=true` when you need them.
- PR CI uses `-PworkspaceProfile=ci`, which keeps the verification job on `core`, all active common shims, and one representative platform per active loader.
- `./gradlew build`
- `./gradlew buildRepresentativeTarget`
- `./gradlew buildSmokeTargets`
- `./gradlew buildFabricSmokeTarget`
- `./gradlew buildForgeSmokeTarget`
- `./gradlew buildNeoforgeSmokeTarget`
- `./gradlew buildTargetJars`
- `./gradlew collectTargetJars`
- `./gradlew -PworkspaceProfile=ci verifyCiWorkspace`
- `./gradlew verifyActiveWorkspace`
- `./gradlew verifyReleaseBundle`
- `./gradlew verifyWorkspace`
- `./gradlew -PincludeLegacyTargets=true verifyWorkspace`
- `./gradlew publishMods -PpublishDryRun=true`

Release jars are collected into `builtJars/<version>/<loader>`.
Each `buildTargetJars` run replaces the current `builtJars/<version>/` bundle so stale metadata and renamed targets do not linger between releases.
GitHub Actions now verifies the active workspace on PRs/pushes and builds/uploads the canonical `builtJars/` bundle on CI runs.
Legacy maintenance verification is available through the manual `Full Workspace Verification` workflow or by passing `-PincludeLegacyTargets=true` locally.
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
