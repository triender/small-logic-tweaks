# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Small Logic Tweaks is a Minecraft mod for Fabric (Quilt-compatible) and NeoForge ("Vanilla+" style) that fixes specific logic/behavior friction points (e.g. Bone Meal on dirt, tree felling ("Timber"), jungle sapling drop rates, magma-block cooking) without adding tech progression or automation. See `README.md` for the full feature list and `DESIGN.md` for the design rationale behind each tweak (useful context when a change touches gameplay balance, not just code).

## Multi-branch versioning (important)

This repo ships parallel versions for different Minecraft releases as **separate long-lived branches**, not a single mainline:

- `main` — general/default branch (receives merges from the newest version branch)
- `26.3` — targets Minecraft 26.3 (newest; the control branch that owns `scripts/`)
- `26.2` — targets Minecraft 26.2
- `26.1.2` — targets Minecraft 26.1.2

The same logical fix or feature is typically ported to every supported version branch. When making a change that isn't purely version-specific, check whether it needs to be cherry-picked/replicated to the sibling branches. `gradle.properties` on each branch pins `minecraft_version`/`mod_version` for that branch (`mod_version` must end with `-mc<minecraft_version>`, and the branch name must equal `minecraft_version`). `build.gradle` is kept identical across version branches; change it on the newest branch and sync it to the others.

## Module layout (multi-loader, no abstraction library)

Each version branch is a Gradle multi-project. There is deliberately **no Architectury/runtime abstraction library**: shared code talks to the loader only through Java `ServiceLoader` SPIs (`core/platform/IPlatformHelper`, `INetworkHelper`, located by `Services`), and each loader module has a thin adapter.

- `common/` — loader-neutral code: config, `core/*`, `handler/*` (all gameplay logic), `mixin/*`, `network/*` (`ConfigSyncPayload`, `ClientConfigSync`), `util/*`, `ConfigFlags`, `SmallLogicTweaksCommon` (`MOD_ID`, `LOGGER`, `init()`), plus all `assets/`, `data/`, the mixin config and the JUnit tests. Its classpath only has Minecraft + Mixin annotations (Loom is used just to provide the Minecraft jar), so **importing any Fabric/NeoForge API in `common/` fails to compile** — that is the guard rail. Common code must never reference a loader entrypoint (use `SmallLogicTweaksCommon`, not `SmallLogicTweaks`).
- `fabric/` — Fabric Loom: `SmallLogicTweaks`/`SmallLogicTweaksClient` entrypoints, `SmallLogicTweaksEvents` (Fabric event wiring + facades used by GameTests), `TweaksConfigCondition`, Fabric SPI impls, datagen, GameTests, `fabric.mod.json`.
- `neoforge/` — ModDevGradle: `SmallLogicTweaksNeoForge`/`SmallLogicTweaksNeoForgeClient` (`@Mod`), `NeoForgeEvents`, `NeoForgeConfigCondition`, NeoForge SPI impls, `META-INF/neoforge.mods.toml`.
- Loader modules compile `common`'s sources/resources into their own jar (`commonJava`/`commonResources` configurations, MultiLoader pattern, no jar-in-jar). MC 26.x is unobfuscated, so no remapping/refmap is involved anywhere.
- A future `forge/` module would follow the same adapter pattern without touching `common/`.

## Build & test commands

Gradle wrapper drives everything; run from repo root:

```
./gradlew build                        # build every module
./gradlew compileJava                  # compile common + all loaders (fast check)
./gradlew :common:test                 # JUnit 5 unit tests (common/src/test)
./gradlew :fabric:runGameTestServer    # Fabric GameTest integration tests, headless server in fabric/run-gametest/
./gradlew :fabric:runClient            # Fabric dev client (fabric/run/)
./gradlew :neoforge:runClient          # NeoForge dev client (neoforge/run/)
./gradlew :neoforge:runServer          # NeoForge dev server (neoforge/run_server/)
./gradlew :common:jacocoTestReport     # coverage report (depends on `test`)
```

Run a single JUnit test class: `./gradlew :common:test --tests "net.enderirt.smalllogictweaks.ConfigValidationTest"`. Release jars land in `fabric/build/libs/small-logic-tweaks-fabric-<version>.jar` and `neoforge/build/libs/small-logic-tweaks-neoforge-<version>.jar`.

Cross-version verification and releases go through `scripts/deploy.ps1`, which lives only on the newest version branch (currently `26.3`) and builds every other branch in its own git worktree under `%LOCALAPPDATA%\small-logic-tweaks-deploy` (the current checkout is never switched; only committed code is built). What gets verified/released is decided solely by `scripts/deploy_config.json`: `branches[]` (all are verified), `branches[].deploy` (released this run), `branches[].loaders` (`"fabric"`, `"neoforge"`: one jar and one Modrinth/CurseForge version each; `"quilt"`: extra tag on the Fabric jar), `quilt_test_loader`, and `platforms.modrinth/curseforge/github/git_push`. `.\scripts\deploy.ps1 -DryRun` is the full cross-version check (changelog, clean compile, JUnit, GameTest, packaging + jar isolation, and a headless server boot test with a forced mixin audit for every declared Quilt/NeoForge loader) plus a release preview — prefer it when validating cross-version changes. Without `-DryRun` it is a release action (needs `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN` + `CURSEFORGE_API_KEY`, `GITHUB_TOKEN` for the enabled platforms), not a dev command. The script reads per-loader artifacts, version/type and changelog from the root `exportReleaseInfo` Gradle task (`build/release/info.json`) and passes `modrinthProjectId`/`modrinthLoaders` as project properties. NeoForge versions are published as `<mod_version>-neoforge`, as beta while `neoforge_version` is a beta. Running a server with `-Dslt.mixinAudit=true` makes the mod force a Mixin audit after startup and log `[Mixin audit] PASSED`.

## Architecture

### Entry points
- Fabric (`fabric.mod.json`): `SmallLogicTweaks` (`ModInitializer`) registers the config-sync payload, calls `SmallLogicTweaksCommon.init()` (loads config), `TweaksConfigCondition.initialize()`, then `SmallLogicTweaksEvents.register()`; `SmallLogicTweaksClient` routes the synced config to `ClientConfigSync`; `SmallLogicTweaksDataGenerator`; `gametest.Gametest` / `gametest.LootTableGameTest` (`fabric-gametest`).
- NeoForge (`neoforge.mods.toml`): `SmallLogicTweaksNeoForge` (`@Mod`) does the same sequence with NeoForge events (payload registered as optional); `SmallLogicTweaksNeoForgeClient` (`@Mod(dist = CLIENT)`) registers the client payload handler and restores the local config on logout.

### Config system (`SmallLogicTweaksConfig`)
Two live instances, not one:
- `LOCAL_INSTANCE` — what's persisted to `config/small_logic_tweaks.json` on disk (atomic write via temp file + `ATOMIC_MOVE`, 1MB size cap, BOM stripping, case-insensitive key normalization on load).
- `ACTIVE_INSTANCE` — what gameplay code actually reads; on a client connected to a server, this is overwritten by the server's config pushed via `ConfigSyncPayload` (see `network.ClientConfigSync`), then restored to `LOCAL_INSTANCE` on disconnect.

`validate()` unconditionally clamps every numeric field to a safe range and rewrites all `_comment_*` fields (self-healing against corrupted/hand-edited JSON). `fallbackFailsafe(rawReceived)` is called client-side when applying a server config: if numeric fields don't match a locally-revalidated copy (i.e. the server sent out-of-range/tampered values), the corresponding feature (Timber, End Phantom) is force-disabled locally rather than trusting the server blindly. When adding a new config field, also add its `_comment_*` string, a `validate()` bound check if numeric, and — if the field could plausibly be spoofed by a malicious server — cross-check logic in `fallbackFailsafe()`.

Data-driven JSON (recipes/loot tables) can be gated on config flags via the `small_logic_tweaks:config_flag` load condition whose `key` (`"charcoal_dye"`, `"coal_dye"`, `"jungle_leaves"`) maps to an `ACTIVE_INSTANCE` boolean in `ConfigFlags` (common). Each loader has a thin adapter (`TweaksConfigCondition` for Fabric's `fabric:load_conditions`, `NeoForgeConfigCondition` for `neoforge:conditions`), so a conditional JSON file carries **both** keys — keep them in sync when adding one.

### Gameplay logic (`common/.../handler/*`)
All gameplay logic lives in loader-neutral handlers: `BoneMealHandler` (Bone Meal→grass/mycelium), `TimberHandler` (tree felling: BFS over connected logs/leaves, capped by config, enchantment-level-gated via `ModEnchants.Timber`, cached per-`BlockPos` for 2s), `ConcreteHardeningHandler` (water bottle → concrete), `DirtReversionHandler`, `EndPhantomHandler` (insomnia timer counts time in the End). Loader adapters only translate events: `SmallLogicTweaksEvents` (Fabric) and `NeoForgeEvents` (NeoForge) — e.g. the three use-block handlers run in the same order on both loaders and the first non-`PASS` result wins. When adding a tweak: put the logic in a handler, gate it on its own `ACTIVE_INSTANCE` boolean first thing, then wire it in **both** adapters; use `debugLog(...)`/debug flags for verbose tracing rather than unconditional `LOGGER.info`.

### Mixins (`mixin/`, listed in `small-logic-tweaks.mixins.json`)
Mixins live in `common/` and are applied by both loaders (NeoForge via `[[mixins]]` in `neoforge.mods.toml`). NeoForge patches some vanilla methods, so a target that exists on Fabric may not on NeoForge (e.g. NeoForge moves `AnvilMenu#createResult`'s body into `createResultInternal`, hence `AnvilMenuMixin` lists both); the deploy boot tests catch this.
- `TimberMiningSpeedMixin` — adjusts `BlockStateBase#getDestroyProgress` using `TimberHandler.getTimberSpeedFactor` so multi-log Timber chops take proportionally longer.
- `ItemEntityMixin` / `ItemEntityMergeMixin` — implement "Aesthetic Kitchen" (cooking food `ItemEntity`s resting on magma/fire/lava, with or without a cauldron/cover block) and prevent cooking items from merging with other dropped stacks mid-cook. Both mixins duplicate the same `slt$isCookable*` cache/lookup logic against the `small_logic_tweaks:magma_cookable` item tag + `magmaCookTimes`/`aestheticCookResults` config maps — keep them in sync if you change cookability rules.
- `ThrownPotionMixin` — splash water bottles harden nearby concrete powder (3x3x3, probabilistic spread, direct hit is always converted).
- `ServerPlayerMixin` — cancels `TIME_SINCE_REST` stat increments outside the End, in support of the End-Phantom tweak.
- `network.KitchenRegistry` — a `ConcurrentHashMap<BlockPos, StoveReservation>` ensuring only one `ItemEntity` cooks per heat-source block per tick (anti-duplication/mass-cook exploit).

### Networking
`ConfigSyncPayload` (a `CustomPacketPayload` record carrying the config as a JSON string, built by `ConfigSyncPayload.fromLocalConfig()`) is pushed server→client when a player joins (`ServerPlayConnectionEvents.JOIN` on Fabric, `PlayerLoggedInEvent` on NeoForge, via `Services.NETWORK`). Multiplayer requires the mod on both sides for synchronized behavior (per `README.md`).

## Testing conventions

- `common/src/test/java/...` — plain JUnit 5 (`org.junit.jupiter`) unit tests, no Minecraft runtime: config validation/boundary-clamping, failsafe cross-checks, resource-condition logic, network payload round-tripping. Fast; run with `./gradlew :common:test`.
- `fabric/src/main/java/.../gametest/` — Fabric GameTest integration tests (`@GameTest`) that run inside an actual (headless) Minecraft server via `./gradlew :fabric:runGameTestServer`; used when behavior depends on real block/world state (e.g. `testConcreteHardening`, recipe resource-condition registration, actual Phantom spawning). These live under `main`, not `test`, because they need the full mod/game environment — keep new gameplay-integration tests here rather than trying to fake it with mocks in `src/test`. They exercise the shared handlers/mixins; NeoForge-specific wiring is covered by the deploy boot test (no NeoForge GameTests yet).

## Changelog discipline

`CHANGE_LOG.md` is machine-parsed by the root `build.gradle`'s `getChangelogText()` (used for Modrinth publish and `verifyChangelog`/`printChangelog` Gradle tasks), so its structure is load-bearing, not just documentation. Rules are fully specified in `CHANGELOG_RULES.md` (written in Vietnamese); key points:
- Every version section header must be `# <emoji> <name> (Version X.Y.Z)` and every section must end with a `---` separator line — the parser matches on `line.startsWith("# ")` + `contains("Version " + versionStr)` and stops at the next `---`.
- Keep a `# 🛠️ Unreleased (Đang phát triển)` section at the very top; add bullets there as you work, then rename it to the new version header (with date) and create a fresh empty `Unreleased` section when cutting a release.
- Sub-sections use fixed `### <emoji> <Category>` headers: `✨ Added`, `🔧 Fixed`, `⚙️ Changed`, `🛡️ Security`.
- For a change that only applies to one Minecraft version's branch, prefix the bullet with `**[Chỉ dành cho bản 26.2]**` / `**[Chỉ dành cho bản 26.1.2]**`.
- Changelog entries themselves should be written in user-facing English describing player-visible effect, not raw commit/technical detail (per rule 5), even though the rules doc explaining this is in Vietnamese.

## Localization

User-facing strings live in `common/src/main/resources/assets/small_logic_tweaks/lang/` (`en_us.json`, `vi_vn.json`). Keep both in sync when adding new translatable text (e.g. new enchantment/item names, in-game chat feedback like the client's "server configuration is invalid" message).
