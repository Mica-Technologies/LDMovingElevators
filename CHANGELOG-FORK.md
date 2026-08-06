# LDMovingElevators fork changelog

Changes made by **Mica Technologies** in this unofficial fork. Upstream's own release notes live in
[`changelog.md`](changelog.md), which is kept as an **unmodified mirror** of SuperMartijn642's file
so it always fast-forwards on an upstream merge — never add fork entries there, or every upstream
release becomes a merge conflict at the top of the file.

Two things to know about the versions below:

- Headings here are the **date-based release tags this repo publishes** (`YYYY.MM.DD`), not
  upstream's semver. The two schemes are unrelated.
- Each release records the **upstream release it is based on**. That is the same value as
  `mod_version` in `gradle.properties` and the newest heading in `changelog.md`; the release
  workflow reads it from `changelog.md` and prints it in the GitHub release body.

The file is named `CHANGELOG-FORK.md` rather than `CHANGELOG.md` on purpose: macOS checkouts are
case-insensitive, so `CHANGELOG.md` and `changelog.md` would collide into a single file locally
while remaining two files on the Linux CI runners.

---

## Unreleased

Based on upstream **Moving Elevators 1.4.12**.

### Added

- Mica standard GitHub Actions workflows: PR build check, release/pre-release publishing from the
  `forge-1.12` branch with checksums, and automatic pruning of pre-releases past 90 days.
- Mod version is now derived from the release tag CI creates (`YYYY.MM.DD`, or
  `YYYY.MM.DD-pre.HHMM.<tz>+<sha>`), falling back to `mod_version` in `gradle.properties` for local
  builds. `-PmodVersionOverride=` forces a specific value.
- `printModVersion` / `printArchivesBaseName` / `printModName` / `printMinecraftVersion` Gradle
  tasks, which the release workflow uses for jar naming and the release body.
- `README.md`, which the `forge-1.12` branch previously lacked entirely. Identifies the fork as
  unofficial, unaffiliated with and unendorsed by SuperMartijn642, and Forge 1.12 only.
- `CLAUDE.md` with build commands, architecture notes and the fork-specific gotchas.
- This changelog, split from upstream's `changelog.md`.
- GitHub release bodies now state the upstream release each build is based on, parsed from the
  newest heading in `changelog.md`. Date-versioned releases otherwise lose that lineage entirely.
  The same step warns if `mod_version` and `changelog.md` disagree, which is the usual symptom of a
  half-finished upstream merge.
- **Full releases** additionally quote upstream's release notes for that version inline.
  Pre-releases deliberately don't: they are cut on every push, and would repeat the same bullets
  until the upstream base version changes.
- Versioned IntelliJ run configurations in `.idea/runConfigurations/`, numbered to match the
  generated ones in the sibling Mica mods: Run Client, Run Server, Run Data Generators, Build Jars,
  Clean, Generate IntelliJ Runs. Committed rather than generated, since this project doesn't use
  the buildscript that generates them. ForgeGradle's own `genIntellijRuns` output stays local — it
  embeds absolute paths.
- `.github/scripts/extract-upstream-lineage.sh`, holding the lineage logic so it is testable rather
  than buried in workflow YAML, and `.github/scripts/test-release-tooling.sh`, which exercises it
  (including the failure paths) and asserts the release/pre-release split in the workflow. Runs in
  CI as a `Test Release Tooling` job on every pull request.

### Fixed

- Build failed on Linux and macOS. `processSources`, `prepareDataResources` and the data run's
  `--existing` property used `layout.buildDirectory.dir("/sources")` and `.dir("/data_resources")`;
  the leading slash makes Gradle resolve those absolutely, so the build tried to create `/sources`
  at the filesystem root. Invisible upstream because Windows resolves a bare `/…` onto the current
  drive, but fatal on the Ubuntu CI runners.
- `gradlew` was committed with mode `100644`, which fails every `./gradlew` step on Linux runners
  with "permission denied". Now `100755`, matching the sibling mods.
- A pre-release version rendered into `mcmod.info` as `2026.08.06+-pre.…`, because upstream's
  `1.4.9` → `1.4.9+a` rewrite fired on versions whose suffix already began with a SemVer separator.
- `processResources` and `prepareDataResources` packaged stale metadata. They substitute project
  properties into `mcmod.info`, `modid.mixins.json` and `pack.mcmeta`, but declared only `version`
  as a task input — so editing any `mod_*` value in `gradle.properties` left Gradle considering the
  task up to date, and the change never reached the jar. The values that feed the substitution are
  now declared as inputs.

### Changed

- `mcmod.info` now identifies the fork rather than the official mod: the in-game author is Mica
  Technologies, the URL points at this repository instead of upstream's CurseForge page, and the
  credits field names SuperMartijn642 as the original author while stating that this build is
  unofficial and unendorsed. `mod_sources` and `mod_issues` were repointed here too, so upstream's
  issue tracker is no longer advertised to players running our build.
- The mod description is prefixed `[Unofficial Fork]`, so the in-game mod list shows it at a
  glance. `mod_id`, `mod_name`, `mod_package` and `maven_group` are deliberately left as upstream's
  — the id is a compatibility contract with existing saves, packs and dependent mods, and the
  package is load-bearing for coremod and mixin discovery.

### Removed

- CurseForge and Modrinth publishing: the `publishMods` block, the
  `me.modmuss50.mod-publish-plugin` plugin, and the `publishing_*` / `curseforge_*` / `modrinth_*`
  properties. They carried SuperMartijn642's own project IDs (CurseForge 373051, Modrinth
  9KZOe6HD), so running the task would have published our builds to his listings. We have no
  projects on either site; releases go to GitHub Releases.

- Removed upstream's Discord invite from our documentation. Reproducing it implies it is a support
  channel for this fork, which it is not. The upstream repository and README are linked instead.
- `.gitignore` and `.gitattributes` aligned with the Mica scaffolding, with fork additions in a
  delimited block below the inherited content.
- `fr_fr.json` renormalized to LF (line endings only).
