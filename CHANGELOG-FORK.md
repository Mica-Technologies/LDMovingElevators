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

### Changed

- Removed upstream's Discord invite from our documentation. Reproducing it implies it is a support
  channel for this fork, which it is not. The upstream repository and README are linked instead.
- `.gitignore` and `.gitattributes` aligned with the Mica scaffolding, with fork additions in a
  delimited block below the inherited content.
- `fr_fr.json` renormalized to LF (line endings only).
