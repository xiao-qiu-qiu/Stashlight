# Stashlight — Agent Notes

This is a **client-side Fabric** Minecraft mod. Upstream is [Strange-Quark-007/Stashlight](https://github.com/Strange-Quark-007/Stashlight). All new work for this fork lives in **this repo** and is pushed to the personal fork below.

Work in Chinese when talking to the user. Keep code, identifiers, git messages, and lang keys in the existing English style unless a file is already Chinese.

## Git (required)

This project is managed with git. **After finishing a feature or fix, commit and push to the personal fork.** Do not leave working-tree changes sitting uncommitted.

| Remote | URL | Role |
| --- | --- | --- |
| `origin` | https://github.com/xiao-qiu-qiu/Stashlight | Personal fork. **Default push target.** |
| `upstream` | https://github.com/Strange-Quark-007/Stashlight.git | Original upstream. Fetch only; do not push. |

Branch in use: `26.1` (Minecraft 26.1).

### After writing a new feature

```powershell
git status
git add <changed files>
git commit -m "<type>: <short English summary>"
git push origin HEAD
```

Commit message types used in this repo: `feat`, `fix`, `port`, `docs`, `chore`.

- One logical change per commit. Do not mix unrelated refactors with a feature.
- Do not commit `build/`, `.gradle/`, IDE files, or the built jar unless the user asked to vend a release artifact.
- Never push to `upstream`. Never force-push `26.1` unless the user explicitly asks.
- Pull `upstream/26.1` only when the user wants to sync with the original mod; rebase or merge locally, then push to `origin`.

## Versions and build

| Item | Value |
| --- | --- |
| Minecraft | `26.1` (`minecraft_version` in `gradle.properties`) |
| Mod version / jar | `26.1.2` → `build/libs/Stashlight-26.1.2.jar` |
| Loader | Fabric Loader `0.18.5` |
| Fabric API | `0.144.3+26.1` |
| owo-lib | `0.13.0+26.1` (Wisp Forest Maven + `owo-sentinel` bundled via `include`) |
| Java | **25** (`sourceCompatibility` / `release = 25`). `fabric.mod.json` still says `java >= 21`; compile with 25. |
| Gradle | Wrapper 9.3.0 (`gradlew.bat` on Windows) |
| Mappings | **Mojang mappings** (Yarn names are wrong here) |
| License | GPL-3.0-or-later. Keep original author credit (`Strange Quark`). |

Build:

```powershell
.\gradlew.bat build
```

Runnable / installable jar: `build/libs/Stashlight-26.1.2.jar` (not `*-sources.jar`). Bump `mod_version` in `gradle.properties` when cutting a new jar.

Dependencies: Fabric API and owo-lib are required at runtime. The mod is **client-only** (`environment: client`).

## What the mod does

Opens containers → snapshots their slots into a per-world NBT cache → search UI lists items (including nested shulker / bundle contents) → click a result to x-ray highlight the block.

Default search keybind: **Numpad 5** (`key.stashlight.search_menu`).

## Layout

Root package: `dev.strangequark.stashlight`

| Path | Role |
| --- | --- |
| `Stashlight.java` | Client entry. Keybind, tick save/cleanup, open/break/close hooks. |
| `Init.java` | Cache dir `stashlight_cache/` under the game dir; per-world / per-server filename. |
| `config/Config.java` | `config/stashlight.json`. Persists look-at, small-containers, radius index. Search query + sort key are runtime-only. |
| `screen/SearchScreen.java` | owo-ui search screen: debounce 150ms, dimension cycle, radius slider, sort button, checkboxes. |
| `gui/ItemGrid.java` | Item slots, tooltips, click → highlight. |
| `gui/UIStyle.java` | Layout constants and sort button glyphs (`Aa`, `#↓`, `◎`). |
| `repository/ContainerRepository.java` | In-memory map + search index. Async cleanup/save on a daemon thread (`ChestFinder-Cleanup`). |
| `serializer/Serializer.java` | Compressed NBT via `ItemStack.CODEC`. |
| `logic/filter/*` | Dimension cycle, radius (chunk Chebyshev), hide containers with `< 9` slots. |
| `logic/sort/*` | Name / count / distance. `SortKey` enum order is the cycle order. |
| `render/*` | X-ray wireframe highlight. 5 blink cycles, 750ms on / 250ms off. |
| `mixin/RenderTypeInvoker.java` | Invoker for `RenderType.create` (custom x-ray layer). |
| `util/Util.java` | Dimension path, searchable-block check, double-chest canonical pos (reflection on `CompoundContainer.container1`). |

Data models: `ContainerSnapshot`, `IndexedItem` (precomputed lowercase `searchKey`), `StackKey` (same-item-same-components, ignores count), `HighlightPos`.

Lang files: `src/main/resources/assets/stashlight/lang/en_us.json`, `zh_cn.json`.

## Invariants you must not break

- **Client-only.** Do not add a server entrypoint or send packets for core search/index.
- **Index on screen close, not open.** `ScreenEvents.remove` writes the final inventory. Opening a container only records `lastOpened`.
- **Double chests share one canonical `BlockPos`.** Always go through `Util.getCanonicalPos` / `resolveContainerPositions` before `repository.update` / `remove`. Breaking either half must delete the shared entry.
- **Not indexed:** ender chests, enchanting tables, beacons, and any non-`EntityBlock`. Player inventory is stripped (`stacks.size() - 36`).
- **Dimension key is the identifier path only** (`overworld`, `the_nether`, `the_end`), not `minecraft:overworld`. Filters, highlights, and cache keys all use this. Highlighting refuses a different dimension (action-bar message).
- **Nested search** is live on the `ItemStack` (`DataComponents.CONTAINER` / `BUNDLE_CONTENTS`), not a second index.
- **Saves are off-thread.** `NbtIo.writeCompressed` must not run on the render thread. `isSavePending` prevents overlapping saves. `shutdown()` on disconnect flushes then awaits the executor (5s).
- **Cleanup every 100 ticks** (loaded chunks only): if the block is no longer a searchable container, drop it. Persist every 3000 ticks if dirty.
- **Cache files:** singleplayer = world folder name; multiplayer = `MP_<ip>` with `:` / `/` replaced. Stored under `.minecraft/stashlight_cache/*.dat`.
- **owo-ui + Mojang mappings + Java 25 + MC 26.1 render pipeline.** Highlight code uses `RenderPipeline` / `RenderType` invoker, not 1.21 Yarn `RenderLayer`. Match neighboring code; do not mix Yarn names.

## Localization

UI copy goes through `Component.translatable(...)`. Add **both** `en_us.json` and `zh_cn.json` for every new key.

Hardcoded English that was replaced (do not regress):

- Dimension cycle: `gui.stashlight.label.dimensionCurrent` / `dimensionAll`
- Vanilla dimension names: `gui.stashlight.dimension.<path>` via `Util.dimensionDisplayName`
- Radius slider: `gui.stashlight.label.searchRange` / `rangeAll` / `rangeChunks`

Sort button labels are symbols in `UIStyle`, not lang strings; tooltips are translated.

Container names in the cache are captured with `block.getName().getString()` at index time (already localized for the language used when the chest was closed). Changing that to a block id is a behavior change, not a drive-by.

## Adding a feature (checklist)

1. Read this file and the files in the table above that you will touch.
2. Keep the change scoped. Follow existing naming, Mojang mappings, and owo-ui patterns.
3. New user-visible text → lang keys in **en_us + zh_cn**.
4. Do not expand mixin surface unless the feature cannot be done with Fabric events / owo-ui.
5. `.\gradlew.bat build` and fix compile errors before committing.
6. **Commit, then `git push origin HEAD`.**

## Config keys (`stashlight.json`)

| Field | Default | Meaning |
| --- | --- | --- |
| `lookAtTarget` | `false` | After clicking a result, rotate the player toward the block. |
| `showSmallContainers` | `false` | Include hoppers/droppers/etc. with fewer than 9 slots. |
| `searchRadiusIndex` | `2` | Index into `{4, 8, 16, 32, 64, -1}` chunks (`-1` = unlimited). |
