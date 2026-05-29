# Repository Guidelines

## Project Structure & Module Organization
Keyset is a Gradle multi-project Minecraft mod. Shared logic lives in `modules/core/src/main/java`. Minecraft API shims are grouped under `modules/common/*`, such as `modules/common/v1_20_1-to-v1_20_2` and `modules/common/v26_1`. Loader and version entrypoints live in `platforms/<loader>/<version>`, for example `platforms/fabric/1_21_11` and `platforms/neoforge/26_1`. Shared resources are merged from `shared-resources/`. Release artifacts are staged into `builtJars/<version>/<loader>`.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repo root and run Gradle itself on Java 25 for this branch.

- `./gradlew build`: compile all currently included subprojects.
- `./gradlew verifyActiveWorkspace`: run Spotless and `check` for the default active workspace.
- `./gradlew -PincludeLegacyTargets=true verifyWorkspace`: include legacy `1.16.5-1.19.4` targets for a full maintenance sweep.
- `./gradlew buildRepresentativeTarget`: build the primary Fabric release target.
- `./gradlew buildTargetJars`: rebuild `builtJars/<version>/` from scratch and collect current release jars plus metadata.
- `./gradlew verifyReleaseBundle`: confirm the expected `builtJars/` bundle exists.
- `./run-fabric.sh`, `./run-forge.sh`, `./run-neoforge.sh`: launch a local dev client for a loader/version pair.

## Coding Style & Naming Conventions
Java is formatted with Spotless using `googleJavaFormat(1.35.0)`. Keep `modules/core` free of Minecraft or loader imports. Keep version shims explicit and small, and keep platform modules thin. Use existing naming patterns: `UpperCamelCase` for classes, `lowerCamelCase` for methods and fields, and Gradle project names like `platform-fabric-1_21_4` or `common-v1_21-to-v1_21_11`.

## Testing Guidelines
Tests use JUnit 5. Most unit coverage belongs beside core logic in `modules/core/src/test/java`. Add focused tests for serialization, conflict detection, and profile-state behavior. Run `./gradlew verifyActiveWorkspace` before opening a PR. Use the full workspace verification only when touching legacy targets.

## Commit & Pull Request Guidelines
Recent history uses short typed subjects such as `fix: ...`, `feat(platform-neoforge): ...`, `chore: ...`, and `release: ...`. Keep commits imperative and scoped when useful. Target `develop` for ongoing work, keep `main` buildable, and note affected loaders or Minecraft ranges in PRs. Include screenshots for UI changes and mention README or changelog updates when behavior changes.
