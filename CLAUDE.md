# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Small Logic Tweaks is a Fabric mod for Minecraft ("Vanilla+" style) that fixes specific logic/behavior friction points (e.g. Bone Meal on dirt, tree felling ("Timber"), jungle sapling drop rates, magma-block cooking) without adding tech progression or automation. See `README.md` for the full feature list and `DESIGN.md` for the design rationale behind each tweak (useful context when a change touches gameplay balance, not just code).

## Multi-branch versioning (important)

This repo ships parallel versions for different Minecraft releases as **separate long-lived branches**, not a single mainline:

- `main` — general/default branch
- `26.2` — targets Minecraft 26.2 (current checkout)
- `26.1.2` — targets Minecraft 26.1.2

The same logical fix or feature is typically ported to both `26.2` and `26.1.2`. `verify_branches.ps1` and `deploy_branches.ps1` both iterate over `@("26.1.2", "26.2")`, checking out each branch to compile, test, and (for deploy) publish to Modrinth. When making a change that isn't purely version-specific, check whether it needs to be cherry-picked/replicated to the sibling branch. `gradle.properties` on each branch pins `minecraft_version`/`mod_version` for that branch (currently `26.2` / `1.4.1-mc26.2`).

## Build & test commands

Gradle wrapper (Fabric Loom) drives everything; run from repo root:

```
./gradlew build                  # full build
./gradlew compileJava            # compile only (fast check)
./gradlew test                   # JUnit 5 unit tests (src/test)
./gradlew runGameTestServer      # Fabric GameTest integration tests (src/main/.../gametest), headless server in run-gametest/
./gradlew runClient              # launch dev client (run/)
./gradlew runServer              # launch dev server (run_server/)
./gradlew jacocoTestReport       # coverage report (depends on `test`)
```

Run a single JUnit test class: `./gradlew test --tests "net.enderirt.smalllogictweaks.ConfigValidationTest"`.

`verify_branches.ps1` runs `clean compileJava` → `cleanTest test` → `runGameTestServer` across both `26.1.2` and `26.2`, restoring the original branch afterward — the closest thing to a full CI check; prefer it when validating cross-version changes. `deploy_branches.ps1` does the same plus Modrinth publishing and git push (requires `MODRINTH_TOKEN` and a clean working tree) — this is a release action, not a dev command.

## Architecture

### Entry points (`fabric.mod.json`)
- `SmallLogicTweaks` (common `ModInitializer`) — registers the config-sync payload, loads config, calls `TweaksConfigCondition.initialize()`, then `SmallLogicTweaksEvents.register()`.
- `SmallLogicTweaksClient` (`ClientModInitializer`) — receives the server's config over network and applies it client-side with failsafe checks.
- `SmallLogicTweaksDataGenerator` — Fabric data generation entrypoint.
- `gametest.Gametest` / `gametest.LootTableGameTest` — `fabric-gametest` entrypoints.

### Config system (`SmallLogicTweaksConfig`)
Two live instances, not one:
- `LOCAL_INSTANCE` — what's persisted to `config/small_logic_tweaks.json` on disk (atomic write via temp file + `ATOMIC_MOVE`, 1MB size cap, BOM stripping, case-insensitive key normalization on load).
- `ACTIVE_INSTANCE` — what gameplay code actually reads; on a client connected to a server, this is overwritten by the server's config pushed via `ConfigSyncPayload` (see `SmallLogicTweaksClient`), then restored to `LOCAL_INSTANCE` on disconnect.

`validate()` unconditionally clamps every numeric field to a safe range and rewrites all `_comment_*` fields (self-healing against corrupted/hand-edited JSON). `fallbackFailsafe(rawReceived)` is called client-side when applying a server config: if numeric fields don't match a locally-revalidated copy (i.e. the server sent out-of-range/tampered values), the corresponding feature (Timber, End Phantom) is force-disabled locally rather than trusting the server blindly. When adding a new config field, also add its `_comment_*` string, a `validate()` bound check if numeric, and — if the field could plausibly be spoofed by a malicious server — cross-check logic in `fallbackFailsafe()`.

Data-driven JSON (recipes/tags) can be gated on config flags via `TweaksConfigCondition`, a Fabric `ResourceCondition` (`small_logic_tweaks:config_flag`) whose `key` (`"charcoal_dye"`, `"coal_dye"`, `"jungle_leaves"`) maps to an `ACTIVE_INSTANCE` boolean — this lets a JSON recipe/loot table declare itself conditionally enabled instead of being toggled from Java.

### Gameplay logic (`SmallLogicTweaksEvents`)
Central home for non-mixin tweaks, registered from `register()`: Bone Meal→grass/mycelium, Timber tree-felling (BFS over connected logs/leaves, capped by config, enchantment-level-gated via `ModEnchants.Timber`, cached per-`BlockPos` for 2s to avoid recompute on mining-progress ticks), poisonous potato composting/brewing, hydro-hardening (water bottle → concrete), and End-dimension Phantom spawning (replaces the vanilla "time since bed" insomnia timer with time spent in the End). Each tweak: register in `register()`, gate on its own `ACTIVE_INSTANCE` boolean first thing, and use `debugLog(...)` (gated by `ENABLE_TIMBER_DEBUG_LOGS`) for verbose tracing rather than unconditional `LOGGER.info`.

### Mixins (`mixin/`, listed in `small-logic-tweaks.mixins.json`)
- `TimberMiningSpeedMixin` — adjusts `BlockStateBase#getDestroyProgress` using `SmallLogicTweaksEvents.getTimberSpeedFactor` so multi-log Timber chops take proportionally longer.
- `ItemEntityMixin` / `ItemEntityMergeMixin` — implement "Aesthetic Kitchen" (cooking food `ItemEntity`s resting on magma/fire/lava, with or without a cauldron/cover block) and prevent cooking items from merging with other dropped stacks mid-cook. Both mixins duplicate the same `slt$isCookable*` cache/lookup logic against the `small_logic_tweaks:magma_cookable` item tag + `magmaCookTimes`/`aestheticCookResults` config maps — keep them in sync if you change cookability rules.
- `ThrownPotionMixin` — splash water bottles harden nearby concrete powder (3x3x3, probabilistic spread, direct hit is always converted).
- `ServerPlayerMixin` — cancels `TIME_SINCE_REST` stat increments outside the End, in support of the End-Phantom tweak.
- `network.KitchenRegistry` — a `ConcurrentHashMap<BlockPos, StoveReservation>` ensuring only one `ItemEntity` cooks per heat-source block per tick (anti-duplication/mass-cook exploit).

### Networking
`ConfigSyncPayload` (a `CustomPacketPayload` record carrying the config as a JSON string) is pushed server→client on `ServerPlayConnectionEvents.JOIN`. Multiplayer requires the mod on both sides for synchronized behavior (per `README.md`).

## Testing conventions

- `src/test/java/...` — plain JUnit 5 (`org.junit.jupiter`) unit tests, no Minecraft runtime: config validation/boundary-clamping, failsafe cross-checks, resource-condition logic, network payload round-tripping. Fast; run with `./gradlew test`.
- `src/main/java/.../gametest/` — Fabric GameTest integration tests (`@GameTest`) that run inside an actual (headless) Minecraft server via `./gradlew runGameTestServer`; used when behavior depends on real block/world state (e.g. `testConcreteHardening`, recipe resource-condition registration, actual Phantom spawning). These live under `main`, not `test`, because they need the full mod/game environment — keep new gameplay-integration tests here rather than trying to fake it with mocks in `src/test`.

## Changelog discipline

`CHANGE_LOG.md` is machine-parsed by `build.gradle`'s `getChangelogText()` (used for Modrinth publish and `verifyChangelog`/`printChangelog` Gradle tasks), so its structure is load-bearing, not just documentation. Rules are fully specified in `CHANGELOG_RULES.md` (written in Vietnamese); key points:
- Every version section header must be `# <emoji> <name> (Version X.Y.Z)` and every section must end with a `---` separator line — the parser matches on `line.startsWith("# ")` + `contains("Version " + versionStr)` and stops at the next `---`.
- Keep a `# 🛠️ Unreleased (Đang phát triển)` section at the very top; add bullets there as you work, then rename it to the new version header (with date) and create a fresh empty `Unreleased` section when cutting a release.
- Sub-sections use fixed `### <emoji> <Category>` headers: `✨ Added`, `🔧 Fixed`, `⚙️ Changed`, `🛡️ Security`.
- For a change that only applies to one Minecraft version's branch, prefix the bullet with `**[Chỉ dành cho bản 26.2]**` / `**[Chỉ dành cho bản 26.1.2]**`.
- Changelog entries themselves should be written in user-facing English describing player-visible effect, not raw commit/technical detail (per rule 5), even though the rules doc explaining this is in Vietnamese.

## Localization

User-facing strings live in `src/main/resources/assets/small_logic_tweaks/lang/` (`en_us.json`, `vi_vn.json`). Keep both in sync when adding new translatable text (e.g. new enchantment/item names, in-game chat feedback like the client's "server configuration is invalid" message).
