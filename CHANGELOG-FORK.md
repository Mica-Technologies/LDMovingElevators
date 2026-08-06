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

- **Elevator Doors**, in two sizes: a 2x2 double doorway whose leaves meet in the middle and retract
  into either side, and a 1x2 narrow one with a single leaf. Each is placed as one item and removed
  as one unit, and finds its own elevator from where it stands — there is nothing to bind. The
  leaves slide open and shut rather than snapping, and their outline follows the leaf so an open
  doorway is not still boxed off. They open when the cabin arrives, and when a landing button is
  pressed while it is already there; they close on their own after a configurable dwell
  (`doorAutoCloseTicks`, 12 seconds by default), and immediately if the cabin leaves — a landing
  door never stands open on an empty shaft. Redstone power forces them open as an emergency
  override. Stack them for a taller doorway; each binds to a controller like the other fixtures.
- **Elevator Car Panel.** The fixture you ride with: a wall plate showing the current floor and
  direction of travel, over a bank of buttons that light for the floors currently selected. Clicking
  it opens a floor list rather than mapping hits onto the drawn buttons — floors are added and
  removed at will, so a fixed grid can never match a shaft, and a block face is far too small to
  hit-test a dozen buttons. Destinations join the same call queue as landing calls, so a full trip
  is served in one sweep. The screen also carries "Open doors" and "Close doors".
- **Remote Elevator Call Panel.** A tall wall-mounted landing panel: floor readout at the top, up
  and down call buttons below. The buttons are real hall calls rather than the existing "move the
  cabin one floor" arrows — pressing either fetches the cabin to that landing and tells the elevator
  which way you then want to travel, so it joins the call queue and gets served in sweep order. The
  arrows light while a call is outstanding.
- **Remote Elevator Indicator.** A second style of the same readout: a slim metal plate that mounts
  on the face of the block behind it, like a real hall indicator above a lift door, rather than
  filling a whole block. It is directional — the plate exists on one side only — and pops off if the
  wall behind it is removed. No camouflage, unlike the full-cube display, since a two-pixel plate has
  nothing to disguise; both styles exist side by side. Uses a silver brushed-metal texture carried
  over from Mica's City Super Mod.
- **Remote Elevator Display.** A new block that shows which floor an elevator is currently at,
  bindable to any controller the same way the Remote Elevator Panel is: right-click a controller
  with it, then place it anywhere. It only reports — it has no buttons and takes no redstone input —
  and it can be camouflaged like the other blocks. While the cabin is moving it shows the floor it
  is nearest to. The label drops a leading "Floor" so it reads "3" rather than "Floor 3", and sits
  on a dark inset panel so it stays legible over any camouflage — the floor's dye colour is kept
  where it reads against that panel and swapped for white where it does not.
- **Call queue.** Button and display presses made while the elevator is already moving are now
  remembered instead of ignored, and served once it arrives. Calls are dispatched in sweep order —
  the cabin finishes the floors ahead of it in its current direction before reversing — rather than
  in press order, so it behaves like a real elevator instead of a one-shot platform. The cabin
  waits one second at each floor before moving on. The up/down arrows are deliberately *not*
  queued: they mean "take the cabin from this floor to the next one", which only has a meaning
  while it is standing there. Queued calls survive a save, and are dropped if their floor is
  removed.
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

- Fall damage stayed cancelled long after leaving an elevator. The grace period compares
  `ticksExisted` against a snapshot stored in the entity's Forge data, which persists to NBT while
  `ticksExisted` restarts at 0 when the entity is reconstructed — so after a relog, or a mob's chunk
  reloading, the delta went negative and read as "still within the grace period" forever. In
  practice: fall immunity for roughly as long as you had been logged in when you last used an
  elevator.
- Stale-controller validation skipped every other floor. It removed entries while counting upwards
  through the same list, so each removal caused the next floor to go unchecked, leaving floors with
  no controller behind them — visible on displays and selectable as destinations.
- Two elevator groups in the same column facing different ways overwrote each other on save. Their
  NBT entries were keyed on position but not facing. Floors and names recovered on load, but cabin
  size, offsets and speed reset to defaults, and a group caught mid-move lost the cabin's blocks
  entirely.
- Defensive guards around the group lifecycle: a controller broken before it ever registered no
  longer throws, nor does removing a floor that is already gone, and the chunk-load handler now
  null-checks the capability like every other call site does.
- Dev runs (`runClient`, `runServer`, `runData`) started without the mod loaded. Their classpath
  resolved the project's own output to the reobfuscated production jar instead of the compiled
  classes, and that jar declares a `TweakClass`, which makes FML drop it from mod discovery
  entirely. Nothing errored — the mod was simply absent, which is why `runData` reported finding no
  generators. The run tasks now get the dev-mapped classes and not the production jar.
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
