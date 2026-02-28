# Keyset

Keybind profiles and conflict resolution for Minecraft (client-side).

## Features
- Multiple keybind profiles with instant switching
- Cleaner Fabric UI with responsive layout, tooltips, profile status badges, and inline guidance
- Conflict visualization with grouping, search, and per-profile previews
- Scrollable conflict list with jump, clear, and reassign actions
- One-click auto-resolve with preview and undo
- Export/import profiles (JSON + clipboard)
- Clean UI that fits vanilla style

## Supported Versions / Loaders
| Minecraft | Fabric / Quilt | Forge | NeoForge | Status |
| --- | --- | --- | --- | --- |
| 1.16.5 | Supported | Build-verified | N/A | Forge dev launch is currently blocked on the old Loom/Yarn runtime stack |
| 1.17.1 | Supported | Build-verified | N/A | Forge dev launch is currently blocked on macOS by the old GLFW icon path |
| 1.18.2 | Supported | Supported | N/A | Verified |
| 1.19.2 | Supported | Supported | N/A | Verified |
| 1.19.4 | Supported | Supported | N/A | Verified |
| 1.20.1-1.20.2 | Supported | Supported | Supported | Verified |
| 1.20.3-1.20.4 | Supported | Supported | Supported | Verified |
| 1.20.5-1.20.6 | Supported | Planned | Planned | Fabric only today |
| 1.21-1.21.11 | Supported | Planned | Planned | Fabric only today |

Exact Fabric targets currently compiled and dev-launched: `1.16.5`, `1.17.1`, `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.4`, `1.21.9`, and `1.21.11`.
Exact Forge targets currently compiled: `1.16.5`, `1.17.1`, `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, and `1.20.4`.
Exact Forge targets currently dev-launched in this environment: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, and `1.20.4`.
Exact NeoForge targets currently compiled and dev-launched: `1.20.1` and `1.20.4`.

## Architecture
- `core`: shared Java module for data models, JSON persistence, conflict detection, auto-resolve, and UI-facing contracts.
- `common-v1_16_5-to-v1_18_x`: legacy Fabric shim for the pre-1.19 screen and input APIs.
- `common-v1_19_x`: Fabric shim for the 1.19 client GUI/input surface.
- `common-v1_20_1-to-v1_20_2`: first version-range adapter shared by loader leaf modules.
- `common-v1_20_3-to-v1_20_6`: modern Fabric shim for the post-1.20.3 GUI/input changes.
- `common-v1_21-to-v1_21_11`: modern Fabric shim for the 1.21.x GUI/input and keybinding API changes.
- `platform-fabric-1_16_5`: Fabric target for `1.16.5`.
- `platform-fabric-1_17_1`: Fabric target for `1.17.1`.
- `platform-fabric-1_18_2`: Fabric target for `1.18.2`.
- `platform-fabric-1_19_2`: Fabric target for `1.19.2`.
- `platform-fabric-1_19_4`: Fabric target for `1.19.4`.
- `platform-fabric-1_20_1`: Fabric target for `1.20.1-1.20.2`.
- `platform-fabric-1_20_4`: Fabric target for `1.20.4`.
- `platform-fabric-1_20_6`: Fabric target for `1.20.6`.
- `platform-fabric-1_21_1`: Fabric target for `1.21.1`.
- `platform-fabric-1_21_4`: Fabric target for `1.21.4`.
- `platform-fabric-1_21_9`: Fabric target for `1.21.9`.
- `platform-fabric-1_21_11`: Fabric target for `1.21.11`.
- `platform-forge-1_16_5`: Forge target for `1.16.5`.
- `platform-forge-1_17_1`: Forge target for `1.17.1`.
- `platform-forge-1_18_2`: Forge target for `1.18.2`.
- `platform-forge-1_19_2`: Forge target for `1.19.2`.
- `platform-forge-1_19_4`: Forge target for `1.19.4`.
- `platform-forge-1_20_1`: Forge target for `1.20.1-1.20.2`.
- `platform-forge-1_20_4`: Forge target for `1.20.4`.
- `platform-neoforge-1_20_1`: NeoForge target for `1.20.1-1.20.2`.
- `platform-neoforge-1_20_4`: NeoForge target for `1.20.4`.

This keeps feature logic out of loader modules and isolates version shims so later Minecraft bumps stay narrow. Legacy Fabric ranges keep their own small adapters instead of forcing the modern screen/input code to degrade across every target.

## How To Use
1. Open Controls.
2. Hover the new `Keyset` button if you want a quick summary, then open it.
3. Pick a profile on the left. The badge shows whether you are editing the active profile or only previewing a saved one.
4. Use search and the group toggle on the right to narrow the visible conflicts for that profile.
5. Select a conflicting bind to see what it does, then use `Find In Controls`, `Clear Key`, or `Rebind`.
6. Hover any button for a tooltip if the action is unclear.
7. Read the footer if you are unsure what to do next. It changes between preview mode, inactive profiles, and selected binds.
8. Use `Apply` to make a saved profile live, `Save Current` to capture the current controls, or `Preview Auto-Fix` to review safe automated changes before applying them.

## Profile Rules
- First-run config seeds four starter profiles: `Default`, `PvP`, `Building`, and `Tech`.
- The active profile is persisted across restarts.
- Deleting the active profile safely falls back to Default.
- Unknown bind ids from missing mods are retained in profile data so they can reactivate later.

## Auto-Resolve Rules (Safe Defaults)
- Never changes critical vanilla binds by default (movement, inventory, chat, ESC, drop).
- Respects user-customized binds that were explicitly captured into the active profile.
- Prefers reassigning less-protected conflicts before sticky or protected binds.
- Current Fabric 1.20.1 runtime prefers unused plain keys from a conservative fallback pool.
- Avoids overwriting existing non-conflicting assignments.
- Deterministic: same input => same output.

## Export / Import
- Export the selected profile to JSON on the clipboard.
- Import profile JSON from the clipboard and merge it into the current config with collision-safe renaming.
- Empty or invalid clipboard payloads are rejected instead of silently seeding defaults.

## Conflict View Rules
- Conflict grouping supports both assigned-key clusters and category-based views.
- Grouping uses stable internal binding/category ids while still exposing display names for UI.
- Search can match key labels, binding names, category names, and internal ids.
- The current Fabric screen now shows conflicts for the selected profile instead of always mirroring the live active profile.
- The current Fabric screen caches the conflict report and only reruns shared conflict analysis after state-changing actions.
- Binding quick actions operate on the active profile and sync manual edits made in the vanilla keybind editor back into `keybindprofiles.json`.
- Legacy and modern Fabric screens both expose contextual tooltips, visible conflict counters, and footer guidance so inactive-profile actions are easier to understand.
- Legacy and modern Fabric screens now share the same compact action-row layout so buttons, help text, and selection details stay readable on short or scaled windows instead of overlapping.
- The selection card now wraps its helper text to the space above the quick-action row, so higher GUI scales do not push text into the action buttons on legacy or modern Fabric leaves.
- The always-on helper/status line is now kept for real action feedback only, while the normal on-screen copy is shorter so it fits cleanly at higher GUI scales on both older and newer Fabric leaves.
- Fabric badges, header summaries, selection details, and conflict-row labels are now trimmed to the available panel width so GUI scaling does not create clipped or invisible text on wired leaves.
- Modern Fabric leaves now draw custom Keyset labels and conflict-row text with explicit opaque colors so the post-`1.20.1` GUI pipeline does not turn them into blank interactive regions.
- The `1.21.11` Fabric conflict list now uses the row entry geometry provided by Minecraft's newer widget API, so hovered conflict rows stay anchored instead of rendering at mouse-relative positions.
- The injected `Keyset` button on the vanilla Controls screen now chooses a non-overlapping slot based on the existing vanilla widgets instead of assuming one fixed top-right position.

## Config
- Stored at `config/keybindprofiles.json` with schema versioning.
- Schema shape starts as `{ "schema": 1, "activeProfile": "...", "profiles": { ... } }`.
- Profile ids are stable internal keys; profile names stay user-facing and can be renamed safely.
- Imported keys that are not currently available will be retained in profile data and treated as inactive until the keybind becomes available again.

## Modpack Notes
- Client-side only; safe to include on servers.

## Development
- Run Gradle with JDK 21. Toolchains handle per-target compilation and dev launches.
- Release bytecode targets stay aligned to Minecraft requirements: Java 8 for `1.16.5`, Java 16 for `1.17.1`, Java 17 for `1.18.2-1.20.2`, and Java 21 for `1.20.3+`.
- Fabric Loom `runClient` still uses Java 17 for the `1.16.5` and `1.17.1` dev environments because the modern Loom support stack injects helper mods that require Java 17, even though the shipped jars remain compiled to the older bytecode targets above.
- `./gradlew build` builds every scaffolded module.
- `./gradlew buildRepresentativeTarget` validates the latest wired Fabric target.
- `./gradlew buildFabricTargets` validates every wired Fabric leaf target.
- `./gradlew buildForgeTargets` validates every wired Forge leaf target.
- `./gradlew buildNeoForgeTargets` validates every wired NeoForge leaf target.
- `./gradlew buildTargetJars` produces the currently wired jar outputs.
- `./gradlew verifyWorkspace` runs formatting and checks.
- `./run-fabric.sh 1.21.3` resolves a requested Fabric Minecraft version to the nearest supported leaf module and launches its `runClient` task.
