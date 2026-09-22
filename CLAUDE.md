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
JAVA_HOME="/path/to/jdk-17" ./gradlew runClient    # dev client, from run/client
JAVA_HOME="/path/to/jdk-17" ./gradlew runServer    # dev dedicated server, from run/server
JAVA_HOME="/path/to/jdk-17" ./gradlew runData      # regenerate src/generated/resources
JAVA_HOME="/path/to/jdk-17" ./gradlew clean       # NOT `clean build` -- see gotchas below

JAVA_HOME="/path/to/jdk-17" bash .github/scripts/server-smoke-test.sh   # boot a server, assert "Done"
```

There are no unit tests (`:test` is `NO-SOURCE`). Verification means building and launching.

### The client and the server run side by side

`runClient` works from `run/client` and `runServer` from `run/server`, so a dedicated server and a
client can be up at the same time from one checkout, and the client can join the server at
`localhost` (its `server.properties` is `online-mode=false` for the unauthenticated dev account).
This is deliberate: the reports that are hard to reproduce -- bank doors not opening, doors opening
for the wrong car, a rider falling through the cabin floor -- all come from dedicated servers, and
an integrated server does not exercise the same packet and chunk paths. Each run directory has its
own `mods/`, `config/` and world:

- **`run/client/config/mcmcp.cfg`** and **`run/server/config/mcmcp.cfg`** give each side its own
  MCMCP identity, so both are separately addressable through the orchestrator. The client enables
  only the client endpoint (port 25592) and the server only the server endpoint (port 25593, with
  direct world edits allowed). Their `instanceId` / `instanceSecret` are generated on first launch;
  never copy a populated identity between the two.
- **`run/server/world`** started life as a copy of the client's "New World" save, which holds the
  building and elevator bank the wiki screenshots were taken in.
- The MCMCP jar has to be in **both** `mods/` folders. The `mcmcp-deploy` skill's target table
  lists both.

`.github/scripts/server-smoke-test.sh` boots the server from `run/server` and reads the verdict
from both Gradle's stdout and `run/server/logs/latest.log`. The PR workflow runs it as a separate
job, because the class of bug it catches -- client-only code reached from common code, a mixin in
the wrong array -- compiles cleanly and only fails at server startup.

Local JDK 17 locations: `C:/Users/ahawk/.jdks/azul-17.0.19` on the Windows machine,
`/Users/ahawk/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home` on the Mac.

## IntelliJ run configurations

`.idea/runConfigurations/` holds six **versioned** run configurations, numbered the way the
GregTechCEu buildscript numbers its generated ones in the sibling mods:

| | Task |
|---|---|
| 1. Run Client | `runClient` |
| 2. Run Server | `runServer` |
| 3. Run Data Generators | `runData` |
| 4. Build Jars | `build` |
| 5. Clean | `clean` |
| 6. Generate IntelliJ Runs | `genIntellijRuns` |

The sibling mods get these generated at import time by the `idea-ext` plugin. This project doesn't
use that buildscript, so they are simply committed. They are plain `GradleRunConfiguration` files
referencing nothing but `$PROJECT_DIR$` and a task name, which is what makes them portable enough to
version. **If a task is renamed, update the matching XML** — nothing verifies these automatically.

**Do not commit anything else from `.idea/`.** ForgeGradle's `genIntellijRuns` writes its own
`Application` configs into the same directory (`runClient.xml`, `runServer.xml`, `runData.xml`,
displayed as "Forge Client"/"Forge Server"/"Forge Data"). Those are the better configs for
day-to-day debugging, but they embed absolute paths into `~/.gradle` and the checkout — committing
them would break other machines and leak the local username. `.gitignore` versions only files
matching `[0-9]__*.xml`, so generated and personal configs are excluded by default rather than by
being named individually.

## Architecture

Package root is `com.supermartijn642.movingelevators` — **upstream's namespace, deliberately kept.**
Renaming it would collide with upstream's own coremod/mixin discovery and make every future upstream
merge a manual conflict. Do not rebrand the package.

```
MovingElevators.java        @Mod entry point; registration
MovingElevatorsClient.java  client-only setup
MovingElevatorsConfig.java  config, via SuperMartijn642's Config Lib
blocks/                     every block and block entity. Upstream's Controller / Display /
                            RemoteController families and the camo (disguise) plumbing, plus the
                            fork's: the WallPanelBlock family (remote display, indicator, landing
                            call panel, car panel, bank lobby panel), which mount on a wall face and
                            pop off with it, and the ElevatorDoor blocks, which find their own
                            landing rather than being bound to one by hand
elevator/                   the actual simulation: ElevatorGroup drives a cage between floors and
                            owns the call queue, dwell timers, door requests, alarm and
                            emergency-stop state; ElevatorBank scores which car of a bank answers a
                            destination request (stateless, and no elevator knows it is in a bank);
                            ElevatorSoundScheme maps a "moment" to a sound so callers name the
                            moment rather than the sample; ElevatorCabinLevel presents the moving
                            cage as a fake Level so blocks inside it render and tick, plus collision
                            and fall-damage handlers
gui/                        elevator config screen, the car panel's floor-select screen, the bank
                            lobby's destination screen, and their widgets
model/                      CamoBakedModel — renders a block disguised as another block
packets/                    client->server actions (one class per button, largely). The elevator
                            screen's all extend ControllerPacket / ElevatorGroupPacket, which do the
                            reach and null-group checks — a new one must too. The car panel's
                            deliberately don't: it rides inside the cabin, where its position is not
                            a world position
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

**Sounds:** no audio ships with the mod. Every entry in
`src/main/resources/assets/movingelevators/sounds.json` points at a vanilla sample, and the schemes in
`ElevatorSoundScheme` differ by volume and pitch rather than by file. `tools/generate-sounds.py` can
synthesise purpose-built samples instead, but it needs a Vorbis *encoder* installed — Minecraft only
registers the JOrbis codec, and macOS's `afconvert` decodes Vorbis without being able to encode it.

## Conventions & gotchas

### Versioning

`build.gradle` resolves the mod version in this order:

1. `-PmodVersionOverride=...` or the `MOD_VERSION` environment variable. **The release workflow
   passes the property** to the build and to `printModVersion`, so CI states the version rather
   than inferring it: a dispatched release and the pushed pre-release of the same commit leave
   both tags on it, and MCMCP shipped a release stamped as a pre-release that way on 2026-09-09.
   The workflow also deletes the other local tags on the commit and refuses to publish unless the
   built version equals `<tag>-forge-<minecraft_suffix>`.
2. the release-shaped git tag on HEAD (`YYYY.MM.DD`, or `YYYY.MM.DD-pre.HHMM.<tz>+<sha>`), which CI
   creates immediately before building. A release tag outranks a pre-release tag on the same
   commit, so a local build off a release commit still names itself correctly.
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
  `run: ./gradlew` step on the Ubuntu runners with "permission denied". The same goes for every
  script under `.github/scripts/`.
- **The release workflow's step names are checked by `test-release-tooling.sh`.** "Pin the mod
  version to the tag being published", "Fail if the built version is not the tag being
  published", "Upload the release asset", "Verify the release has the jar" and "Publish the
  release" are each a step somebody could tidy away without anything else noticing; the test
  exists so that tidying fails the PR instead.
- **Build-directory paths must stay relative.** Upstream writes
  `layout.buildDirectory.dir("/sources")`; the leading slash makes Gradle resolve it absolutely, so
  off Windows the build tries to create `/sources` at the filesystem root and dies. An upstream
  merge will try to reintroduce this — there is a comment on `processSources` explaining it.

### Publishing was removed

Upstream published to CurseForge and Modrinth from Gradle. That `publishMods` block, the
`me.modmuss50.mod-publish-plugin` plugin and the `publishing_*` / `curseforge_*` / `modrinth_*`
properties are **gone** from this fork: they carried SuperMartijn642's own project IDs (CurseForge
`373051`, Modrinth `9KZOe6HD`), so running the task would have pushed our builds to his listings.

We have no projects on either site. Releases go to **GitHub Releases** via `.github/workflows/`.
Only re-add publishing alongside real project IDs of our own — `build.gradle` carries a note at the
removal site.

### Dev runs need the classes dir forced onto the classpath

`runClient` / `runServer` / `runData` take their classpath from `sourceSets.main.runtimeClasspath`,
which on this project resolves the project's own output to `build/libs/<jar>` rather than
`build/classes/java/main`. Two things then break, either of which alone stops the mod loading:

- `reobfJar` rewrites that jar in place, so its classes carry SRG names while dev is deobfuscated.
- Its manifest declares a `TweakClass`. `CoreModManager.discoverCoreMods` adds **any** jar declaring
  one to `ignoredModFiles` and `continue`s before the branch that would treat an
  `FMLCorePluginContainsFMLMod` jar as a mod candidate, so the `@Mod` inside is never found.
  `ForceLoadAsMod` is not an escape hatch — FML 1.12.2 does not read it.

The symptom is subtle: the run starts fine and the mod is simply *absent*. For `runData` that shows
up as `Found 0 generators for modid 'movingelevators'` and a non-zero exit.

`build.gradle` fixes this by prepending `sourceSets.main.output` to the run tasks' classpath, and
dropping `build/libs` from it, in a `doFirst`. It has to be a `doFirst` on the tasks: assigning
`sourceSets.main.runtimeClasspath` — in the script body, in `afterEvaluate`, or in
`gradle.projectsEvaluated` — is silently overwritten by ForgeGradle's own later wiring.

### `runData` exits 1 even when it succeeds

Core Lib's `CoreLib.onLoadComplete` calls `System.exit(1)` once the generators have run, so Gradle
reports the task as failed on a completely successful generation. Check the log for
`All generators for modid 'movingelevators' took ...` — if that line is there, the run worked.

**A failed `runData` deletes `src/generated` before it bails.** If a run genuinely fails, restore
with `git checkout -- src/generated` rather than assuming the files were meant to go.

### `clean build` in one invocation fails

Run them separately. ForgeGradle resolves the Minecraft dependency during configuration, and
`clean` then deletes what it resolved, so `./gradlew clean build` dies in `compileJava` with
`package net.minecraft does not exist`. `./gradlew clean` followed by `./gradlew build` is fine,
and CI only ever runs `build`.

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
- **`changelog.md`'s structure is load-bearing for CI.** The release workflow's `Extract Upstream
  Lineage` step parses it twice:
  - The newest `### Moving Elevators X.Y.Z` heading is the upstream release this fork sits on. It
    should match `mod_version` in `gradle.properties`; the step prints it as a lineage line in
    *every* release body, and **fails the release** if that heading shape stops parsing. A
    `mod_version` mismatch only warns — that's a stale-merge signal, not a reason to block a
    release.
  - Everything between that heading and the next `### ` becomes the inline upstream notes, quoted
    in **full releases only**. Pre-releases are cut on every push and would otherwise repeat the
    same bullets until the upstream base changes.

  So: keep the `### ` heading shape, and keep sections separated by `### ` headings. The step
  refuses to run if the changelog contains its heredoc delimiter, since that would let file content
  inject arbitrary environment variables into the job.

  The logic lives in `.github/scripts/extract-upstream-lineage.sh`, not inline in the workflow, so
  it can be tested. **Run `.github/scripts/test-release-tooling.sh` after touching it** — CI runs
  the same script as a separate `Test Release Tooling` job on every PR.

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

- `test-mod-build-pr.yml` — builds every pull request, then boots a dedicated server from that
  build (`Dedicated Server Smoke Test`, via `.github/scripts/server-smoke-test.sh`), plus a separate
  `Test Release Tooling` job running `.github/scripts/test-release-tooling.sh` and
  `test-csm-containment.sh`. That last job needs no JDK and finishes in seconds, so it reports
  independently rather than queueing behind a full Minecraft decompile.
- `build-mod-release-pre-release-main.yml` — on push to `forge-1.12`, tags the commit and publishes a
  pre-release with checksums. `workflow_dispatch` with `release=true` cuts a full release. The tag is
  created *before* the build, and the build is told which tag it is (see Versioning). The release
  entry is created as an empty draft by `softprops/action-gh-release@v3`; the jar is then uploaded
  by `gh release upload` with retries, the release is read back from the API to confirm the asset
  is in state `uploaded`, and only then is the draft published. The action's own uploader lost
  assets on CSM's 2026-09-17 release and left it a draft that looked like a build failure, which
  is why none of this is left to it.
- `cleanup-mod-pre-releases.yml` — prunes pre-releases past 90 days, keeping the newest 3 and
  anything with 5+ downloads. Its `workflow_run` trigger matches the release workflow by name, so
  those two strings must stay in sync.

## Documentation site

`docs/` is an **MkDocs Material** site published to GitHub Pages at
<https://mica-technologies.github.io/LDMovingElevators/> by `deploy-wiki-pages-main.yml`. It mirrors
the sibling MCMCP setup deliberately — same theme, same nav shape, same pinned toolchain in
`docs/requirements.txt` — so the two sites are one thing to maintain rather than two.

- The workflow triggers on **`forge-1.12`**, not `main`; the `-main` filename suffix is the Mica
  convention for "the default branch", the same as `build-mod-release-pre-release-main.yml`.
- **One-time setup:** Settings → Pages → Source → "GitHub Actions". Until that is set, the build
  succeeds and the deploy step 404s.
- CI runs `mkdocs build --strict`, so a broken internal link or a nav entry pointing at a missing
  file **fails the deploy**. Preview locally with `pip install -r docs/requirements.txt && mkdocs serve`.
- The README and this site overlap on purpose: the README stays a complete standalone document for
  people reading the repo, and the site is the same material split into per-block reference and
  task-shaped guides. **Update both** when block behaviour changes.
- The fork disclaimers are load-bearing. They appear on the site's home page *and* on
  `docs/about/fork.md`; do not thin them out to avoid repetition.

## Planning docs

`docs/agent-plans/` is **gitignored** — it holds implementation plans and agent working notes. It is
local scratch; nothing in it is authoritative. When a plan and the code disagree, **the code wins**:
verify by reading the source before believing a checkbox.
