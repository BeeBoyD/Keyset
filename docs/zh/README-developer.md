# Keyset — 开发者参考

本文档说明 Keyset 的架构、模块布局、build 系统和贡献流程。

面向玩家的概览见[根 README](../../README.md)。

---

## Architecture

Keyset 是一个 Gradle 多模块项目。所有业务逻辑都在 `modules/core` 中：纯 Java，不导入 Minecraft 或 loader。平台 entrypoint 只是薄 wrapper。

```
modules/
  core/                       # Pure Java, GSON only, Java 8 release target
    conflict/                 # Conflict detection, grouping, querying, auto-resolve
    binding/                  # KeyBinding descriptors
    profile/                  # Profile snapshots, JSON serialization, active profile manager
  common/
    v1_20_1-to-v1_20_2/       # Minecraft API shim per version range
    v1_20_3-to-v1_20_6/
    v1_21-to-v1_21_11/
platforms/
  fabric/{version}/           # Thin entrypoints + event listeners only
  forge/{version}/
  neoforge/{version}/
```

### Layer Rules (strict)

| Layer | Rule |
|---|---|
| `core` | Pure business logic. Java 8 release target. NO Minecraft imports — ever. |
| `common-v*` | Minecraft API shims only. Keep small. |
| `platform-*` | Entrypoints + event listeners only. NO business logic. |

### Key Data Flow

1. Platform reads live MC keybindings → `KeysetConflicts.detect()` → UI groups via `KeysetConflictGroupMode`
2. Profile save: capture MC bindings → `KeysetProfile` → `KeysetProfilesJson.toJson()` → `config/keybindprofiles.json`
3. Auto-fix: `core/conflict/` resolves deterministically, protects vanilla binds, respects sticky user edits

---

## Build System

Build 在 GitHub Actions 中运行。除非调试 build 系统，否则不要在本地运行完整 workspace build。

### CI Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `ci.yml` | Push/PR to `main`, `main-next`, `main-legacy`, `develop` | Runs `verifyCiWorkspace` + spotless; builds beta if `[build:<label>]` in commit |
| `release.yml` | Manual dispatch | Full build + publish to Modrinth + GitHub |
| `full-workspace.yml` | Manual dispatch | Full workspace including legacy targets |

### Triggering a beta build

在 commit message 中添加 `[build:<label>]` 标签：

```bash
fix: something [build:fabric-1.21.1 build:fabric-1.21.4]
fix: something [build:all]
```

### Local commands

```bash
# Pre-commit checks
./gradlew verifyCiWorkspace          # spotless + core/common tests

# Auto-format
./gradlew spotlessApply

# Run a specific test
./gradlew :core:test --tests "net.beeboyd.keyset.SomeTest"

# In-game testing
./run-fabric.sh <mc-version>
./run-forge.sh <mc-version>
./run-neoforge.sh <mc-version>
./run-quilt.sh <mc-version>
```

### Publishing (CI-managed)

```bash
./gradlew publishMods -PreleaseType=stable
./gradlew publishMods -PpublishDryRun=true
```

需要环境变量 `MODRINTH_TOKEN`，或 `gradle-local.properties` 中的 `modrinthToken`（gitignored）。

### Remapping Overrides

`platform-forge-1_20_6`, `platform-forge-1_21_1`, `platform-fabric-26_1`, `platform-neoforge-26_1` 由于 mapping 冲突使用 `jar` 而不是 `remapJar`。见根 `build.gradle` 中的 `jarTaskOverrides`。

## Version Matrix

| Minecraft        | Fabric | Forge | NeoForge |
|------------------|:------:|:-----:|:--------:|
| 1.20.1 – 1.20.2  | ✅     | ✅    | ✅       |
| 1.20.3 – 1.20.6  | ✅     | ✅    | ✅       |
| 1.21.1           | ✅     | ✅    | ✅       |
| 1.21.2 – 1.21.11 | ✅     | ❌    | ✅       |
| 26.1.x           | ✅     | ❌    | ✅       |

## Share API

Backend: `share.beeboyd.com/api/keyset/`

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/keyset/shares` | POST | Upload profile JSON → `{code, expires_at}` |
| `/api/keyset/shares/:code` | GET | Download by code → `{data, meta, expires_at}` |

Code: 8 alphanumeric chars (no O/0/I/1) · Expiry: 90 days · Auth: none
Rate limits: 10 uploads/hr, 30 downloads/min per IP
Meta: `{username, profileName}`
Client: `ShareApiClient.java` — `java.net.http.HttpClient`, no new dependencies

## 26.1 (Mojang mappings)

26.1 是第一个未混淆的 Minecraft 版本。不使用 Yarn。

Key API differences from Yarn 1.21.x:
- `MinecraftClient` → `Minecraft`
- `client.options.allKeys` → `client.options.keyMappings`
- `DrawContext` → `GuiGraphicsExtractor`
- `Screen#render` → `Screen#extractRenderState`
- `fabric.mod.json` range: `"minecraft": ">=26.1 <26.2"`
- Java 25 required

---

## Branches

| Branch | MC Versions | Status |
|---|---|---|
| `main` | 1.20.1–26.1 | Active (`1.1.x` / `2.0.x`) |
| `main-next` | next gen | Next-gen dev |
| `main-legacy` | 1.16.5–1.19.4 | Critical fixes only (`1.0.x`) |

Short-lived branches: `feature/*`, `fix/*`, `mc/common/*`, `mc/fabric/*`, `mc/forge/*`, `mc/neoforge/*`, `release/*`

A `core/` bug fix should be committed on `main` and cherry-picked to `main-next` and `main-legacy` as needed.

## Legacy targets (1.16.5 – 1.19.4)

`1.0.x` 发布线只接收关键 bug 修复。
Share、Auto-Switch 和 2.0 UI 不会 backport。

Parity verification for active targets should cover Fabric 1.20.x, Fabric 1.21.x, Fabric 26.1, Forge 1.20.x-1.21.1, and NeoForge 1.20.x-26.1. Forge and NeoForge targets intentionally reuse the matching Fabric screen/service sources through Gradle source sets, so UI fixes must be applied to every active Fabric source set that a loader target includes.

---

## Quality Rules

- `modules/core/` — no Minecraft imports, ever
- Keep version shims small and explicit
- No polling-heavy runtime behavior
- Prefer exact file ranges before opening whole files
- Do not scan `.gradle/`, `build/`, `out/`, `target/`, or `run/` unless debugging generated output

---

## Commit Rules

- Do **not** add `Co-Authored-By: Claude` or any AI authorship lines to commits
- Use conventional commits: `feat:`, `fix:`, `refactor:`, `docs:`, `chore:`

---

## Profile Format

配置保存在 `config/keybindprofiles.json`。该格式在受支持的 release family 中保持稳定。未知 keybind ID 会被保留。

---

## i18n

Lang 文件位于：

```
platforms/fabric/{version}/src/main/resources/assets/keyset/lang/
```

The canonical source is `en_us.json`. Translations are synced across all version targets. Available locales: `en_us`, `es_es`, `pt_br`, `fr_fr`, `de_de`, `ro_ro`, `it_it`, `ru_ru`, `zh_cn`, `zh_tw`, `ja_jp`, `ko_kr`, `pl_pl`, `uk_ua`, `nl_nl`, `sv_se`.
