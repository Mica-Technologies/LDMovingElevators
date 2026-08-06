# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

**LDMovingElevators** is Mica Technologies' unofficial fork of
[Moving Elevators](https://github.com/SuperMartijn642/MovingElevators) by SuperMartijn642, scoped to
**Minecraft 1.12.2 / Forge only** (mod id: `movingelevators`). The mod adds multi-floor elevators
that physically move players and entities on a configurable platform.

Upstream maintains one branch per loader + Minecraft version. We track **`forge-1.12`** and nothing
else — it is this repo's default branch, and the other inherited branches get no attention. The
`upstream` remote points at SuperMartijn642's repository.

Build system is **ForgeGradle 6** with a hand-written `build.gradle` — *not* the GregTechCEu
Buildscripts setup used by the sibling Mica mods (RCMC, CSM, LDOG). There is no `addon.gradle` here;
`build.gradle` is directly editable.

## Git Guidelines

- **Create commits** when work reaches a logical checkpoint -- keep them descriptive and well-organized.
- **Never push** to any remote. The user will review and push manually.
- Use conventional, descriptive commit messages that explain *why*, not just *what*.
- Group related changes into single commits; don't lump unrelated work together.
- Commit identity for this repo is `mica-alex` (the global default). Verify with
  `git config user.email` before committing.

## Build Commands

ForgeGradle 6 requires **Java 17+ to run Gradle**, while the mod compiles against a **Java 8**
toolchain (`java_target=8` in `gradle.properties`). Both are needed. Set `JAVA_HOME` to a 17+ JDK;
Gradle locates or auto-provisions the Java 8 toolchain via the foojay resolver in `settings.gradle`.

```bash
JAVA_HOME="/path/to/jdk-17" ./gradlew build       # compile + jar -> build/libs/
JAVA_HOME="/path/to/jdk-17" ./gradlew runClient    # dev client
JAVA_HOME="/path/to/jdk-17" ./gradlew runServer    # dev dedicated server
JAVA_HOME="/path/to/jdk-17" ./gradlew runData      # regenerate src/generated/resources
JAVA_HOME="/path/to/jdk-17" ./gradlew clean
```

There are no unit tests (`:test` is `NO-SOURCE`). Verification means building and launching.

On this machine, JDK 17 is at
`/Users/ahawk/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home`.

## Architecture

Package root is `com.supermartijn642.movingelevators` — **upstream's namespace, deliberately kept.**
Renaming it would collide with upstream's own coremod/mixin discovery and make every future upstream
merge a manual conflict. Do not rebrand the package.

```
MovingElevators.java        @Mod entry point; registration
MovingElevatorsClient.java  client-only setup
MovingElevatorsConfig.java  config, via SuperMartijn642's Config Lib
blocks/                     Controller / Display / RemoteController blocks + block entities,
                            and the camo (disguise) block plumbing
elevator/                   the actual simulation: ElevatorGroup drives a cage between floors,
                            ElevatorCabinLevel presents the moving cage as a fake Level so blocks
                            inside it render and tick, plus collision and fall-damage handlers
gui/                        elevator config screen and its widgets
model/                      CamoBakedModel — renders a block disguised as another block
packets/                    client->server actions (one class per button, largely)
generators/                 data generators; output is committed under src/generated/resources
core/CoreMod.java           FML coremod plugin — registers the mixin config
mixin/                      LevelChunkMixin (common), LevelRendererMixin +
                            vintagium/SodiumWorldRendererMixin (client-only)
extensions/                 interfaces mixed into vanilla types
```

**Dependencies:** SuperMartijn642's Core Lib and Config Lib, both pulled from CurseForge Maven and
pinned by file ID in `gradle.properties`. They are hard requirements at runtime.

**Mixins:** the config is `src/main/resources/modid.mixins.json`, renamed to
`movingelevators.mixins.json` at resource-processing time. Client-only mixins must stay in the
`client` array — putting one under `mixins` loads it on a dedicated server and crashes it.

## Conventions & gotchas

### Versioning

`build.gradle` resolves the mod version in this order:

1. `-PmodVersionOverride=...` or the `MOD_VERSION` environment variable
2. the release-shaped git tag on HEAD (`YYYY.MM.DD`, or `YYYY.MM.DD-pre.HHMM.<tz>+<sha>`), which CI
   creates immediately before building
3. `mod_version` in `gradle.properties` — the upstream release this fork sits on; bump it when
   merging upstream

Tag matching is deliberately narrow so upstream's `archive/1.16.1`-style tags can never be picked
up. The resolved value is written back onto the `mod_version` property, so `processResources`,
`prepareDataResources` and `publishMods` all see it without extra wiring.

### Things that will silently break CI

- **`printModVersion` / `printArchivesBaseName` / `printModName` / `printMinecraftVersion` are a
  contract** with `.github/workflows/build-mod-release-pre-release-main.yml`. Renaming one breaks
  the release build.
- **`build.gradle` prints a JVM banner during configuration that `-q` does not suppress.** That is
  why the workflow reads those tasks with `| tail -n 1 | xargs` rather than the plain `| xargs` the
  sibling mods use. Don't "simplify" it.
- **`gradlew` must stay mode `100755`.** It was committed as `100644`, which fails every
  `run: ./gradlew` step on the Ubuntu runners with "permission denied".
- **Build-directory paths must stay relative.** Upstream writes
  `layout.buildDirectory.dir("/sources")`; the leading slash makes Gradle resolve it absolutely, so
  off Windows the build tries to create `/sources` at the filesystem root and dies. An upstream
  merge will try to reintroduce this — there is a comment on `processSources` explaining it.

### Publishing is wired to upstream's projects — do not run it

`publishMods` in `build.gradle` is configured with **SuperMartijn642's own project IDs**
(`curseforge_project_id=373051`, `modrinth_project_id=9KZOe6HD`). Running `./gradlew publishAll`
with `CURSEFORGE_TOKEN` / `MODRINTH_TOKEN` set would attempt to publish this fork's build to *his*
CurseForge and Modrinth listings.

Nothing in CI calls it — the workflows only run `build` — and it should stay that way unless and
until those IDs are repointed at our own projects, or the task is removed.

### Changelogs are split — respect the split

- **`changelog.md` is an unmodified upstream mirror.** Never add fork entries to it, not even a
  header comment. Upstream prepends new sections to the very top of that file, so anything we put
  there guarantees a merge conflict on every single upstream release — which is precisely what the
  split exists to prevent.
- **`CHANGELOG-FORK.md` is ours**, keyed by the `YYYY.MM.DD` release tags this repo publishes. Add
  entries under `## Unreleased`.
- It is named `CHANGELOG-FORK.md` and not `CHANGELOG.md` because macOS checkouts are
  case-insensitive: `CHANGELOG.md` and `changelog.md` collapse into one file locally while staying
  two files on the Linux runners. Don't "fix" the name.
- The newest `### Moving Elevators X.Y.Z` heading in `changelog.md` is the upstream release this
  fork sits on. It should match `mod_version` in `gradle.properties`, and the release workflow
  parses it (`Extract Upstream Lineage`) to print the fork's lineage in every release body — the
  step fails the build if that heading shape stops parsing. Keep it intact.

### Fork hygiene

- Keep the diff against upstream small and legible. Where a fork-specific change is needed in a file
  we inherited, mark it with a `Mica` comment explaining *why*, so the next upstream merge doesn't
  quietly revert it.
- `.gitignore` and `.gitattributes` keep fork additions in a delimited block below the upstream
  content, for the same reason.
- `mcmod.info` still carries upstream's `authorList`, `description` and `url` (his CurseForge page).
  In-game, this fork therefore presents itself much like the official mod. Worth revisiting if these
  builds are ever distributed beyond our own packs.

## CI

Three workflows, matching the sibling Mica mods (see the header comment in each for the fork-specific
deltas):

- `test-mod-build-pr.yml` — builds every pull request.
- `build-mod-release-pre-release-main.yml` — on push to `forge-1.12`, tags the commit and publishes a
  pre-release with checksums. `workflow_dispatch` with `release=true` cuts a full release. The tag is
  created *before* the build, because the version resolution above reads it.
- `cleanup-mod-pre-releases.yml` — prunes pre-releases past 90 days, keeping the newest 3 and
  anything with 5+ downloads. Its `workflow_run` trigger matches the release workflow by name, so
  those two strings must stay in sync.

## Planning docs

`docs/agent-plans/` is **gitignored** — it holds implementation plans and agent working notes. It is
local scratch; nothing in it is authoritative. When a plan and the code disagree, **the code wins**:
verify by reading the source before believing a checkbox.
