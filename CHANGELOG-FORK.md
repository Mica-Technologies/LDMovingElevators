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

- **A documentation site**, published to GitHub Pages from `docs/` by
  `deploy-wiki-pages-main.yml`. MkDocs Material, mirroring the sibling MCMCP setup so the two read
  alike and the toolchain is one thing to learn rather than two. The README had grown into a wiki
  that happened to live in one file; this splits it into per-block reference and task-shaped guides
  without losing the fork disclaimers, which are repeated on the home page and given a page of their
  own. `mkdocs build --strict` runs in CI, so a broken internal link fails the deploy rather than
  shipping a 404.

- **Elevator Bank Indicator** — one wall readout for a whole bank, a column per elevator showing its
  name, the floor it is on and which way it is going. A four-shaft lobby needed four separate
  indicators and four separate glances; this answers in one. It is a new block rather than a change
  to the existing indicator, so one readout per shaft remains just as valid a way to build.
  The plate widens itself to fit, from two columns up to six, growing about a block past its own on
  each side at full width. Beyond six the extra elevators stay linked and working but have no column,
  and clicking the block says how many are missing.

- **Cabin music.** Vanilla's shopping-mall record for the Standard scheme, which is already elevator
  music in everything but name, and something ambient for Modern. It plays only while somebody is
  actually aboard — an empty lift playing to itself would be every lift in a building playing at
  once, heard from every landing, and a track started for nobody cannot be switched off by whoever it
  eventually annoys. Its switch is on the car panel, since it is turned off by the person listening
  to it, and it is its own setting rather than part of the sound scheme: wanting the chimes and not
  the music is an ordinary preference. Switching it off takes effect at the end of the track playing,
  because a sound already handed to a client cannot be recalled.
- **Two more sounds.** A low thud as the brake lets go, for the people already aboard, and a dry tick
  when a call registers — sounded at the button pressed rather than at the cabin, since a landing
  button whose lift is eight floors away otherwise gives no sign it took.

- **Five more configuration options**: `bankedDwellTicks`, `emergencyHoldTicks`, `doorLinkRange`,
  `shaftScanReach` and `maxCabinSpeed`. The speed ceiling was previously written twice, once in the
  screen and once in the message that validates it, in different units; both now read the setting.

- **Comparators read more than "is it here".** The landing readouts report which floor the cabin is on
  as a signal strength, following it as it travels; the call panel reports the calls waiting at its
  landing, and which direction they are for, since a build that only knows somebody is waiting cannot
  tell an arriving lift which way they mean to go. Each block reports the thing it already displays.
  The controller and remote panel still report cabin presence exactly as before, so redstone built
  against them is untouched.

- **Fire recall, driven by City Super Mod's fire alarms — optionally.** Craft an **Elevator Alarm
  Linker**, sneak-click a fire alarm panel with it and then right-click an elevator controller: the
  elevator returns to that controller's floor whenever that alarm sounds. The gesture is City Super
  Mod's own, since that is how its sounders are wired to a panel and there is no sense in inventing a
  second way — and CSM's own linker can do the second step from City Super Mod 2026.08.10 onwards, so
  once you have wired a panel to its sounders you can attach the lifts without changing tools. An
  older City Super Mod is fine: only that convenience is missing, and this mod's own linker still
  does the job. When the alarm sounds the elevator drops everything it was asked to do,
  returns to that floor and waits there with its doors open. Its readouts scroll FIRE
  RECALL throughout. Fire alarms only: a storm warning sends people to shelter, which they need
  working lifts to reach.
  CSM is **not** a dependency: it is compiled against and never required, so this mod builds and runs
  exactly as before without it, and the feature simply never triggers.

- **Service modes.** *Out of service* is a switch on the elevator controller: the elevator answers
  nobody, is skipped by bank dispatch, and scrolls OUT OF SERVICE on its readouts so nobody stands
  waiting for a lift that is not coming. *Independent service* is a key switch inside the cabin: the
  car still goes where its passengers ask but stops being offered to the building, which is how you
  keep a lift to yourself without taking it off the network. The key switch is shown to everybody and
  always looks locked; turning it needs the `movingelevators.independent_service` permission, which
  operators have by default and a permissions mod can grant to anyone. Hovering it explains what it
  is, what the setting does and that it is locked; a car on independent service also alternates
  between its floor and "IS" on the landing readouts, so it is clear from outside why that lift keeps
  passing you by.

- **Cabins have a capacity, and refuse to move when it is exceeded.** One passenger per block of cabin
  floor — it is standing room that runs out, so floor area rather than volume. An overloaded cabin
  holds its doors open at the floor it is standing at, since the way out of an overload is for
  somebody to leave and a shut door makes that the one thing nobody can do; it buzzes inside until
  they do; and its readouts scroll OVERLOAD two characters at a time in place of the floor. It also
  takes no calls while overloaded, so nothing is quietly lost while it waits.
- **An emergency stop button on the car panels.** Not a toggle: an emergency stop a passenger can
  also cancel is a switch, and the elevator already decides when it is safe to resume — it waits out
  its hold and checks the shaft is clear. Moving, it halts and crawls to the nearest floor; already
  parked, it goes out of service with the doors open.

- **Elevator Bank Car Panel** — the in-cabin panel for an elevator that belongs to a bank. It has the
  floor readout, the direction arrows, the doors and the alarm, and deliberately no floor buttons:
  destination dispatch takes floor selection away from the car, since you say where you are going at
  the lobby before boarding. Bind it to a controller like the other panels.

- **Elevators have names.** A bank lobby panel now says *which* car is coming — "Elevator B is on its
  way to Floor 3" — which is the half of destination dispatch that was missing, and the difference
  between "a lift is coming somewhere" and "stand by that one". Linking a shaft to a lobby panel
  gives it the next free letter automatically, so this works without anyone naming anything, and a
  name you set yourself is never overwritten. The name belongs to the elevator rather than to the
  bank, so a car answers to it from its own car panel and from a second lobby panel that never did
  the naming. Set it in the elevator controller's screen.
  A lobby panel says whether the car is coming or already standing there, and which floors it will
  serve for people boarding — so two passengers sent to the same car are told the same pair of floors
  and can see they are riding together. When a bank sends a car to a landing, that landing's own
  readouts flash the car's name until it leaves, so a passenger in a lobby of four shafts knows which door to stand at rather than only that
  something is coming.

- **Elevator sounds**, with an on/off toggle in the elevator controller's screen. A soft tick each
  time the cabin passes a landing, so a ride has some sense of progress, and a two-note chime on
  arrival alongside the existing arrival sound. The setting belongs to the elevator rather than to
  one controller, and existing elevators start with sounds on.

- **Two sound schemes**, picked with a cycling button in the elevator controller's screen beside the
  sound toggle. **Standard** is the struck bell of an older building: a mellow bell as each floor
  goes by, well under the arrival so passing a floor never sounds like getting to one, a ding-dong on
  arrival whichever way the car is about to go, and the car settling as a short thump underneath it.
  **Modern** is clean electronic chimes, and its arrival announces where the car is going next the
  way a hall lantern does — the pair rises when it is about to go up, falls when it is about to go
  down, and is two flat notes when it has nothing else to serve. The point is that somebody waiting
  on a landing can tell from the next room whether the car that just arrived is the one they want.
  Direction comes from the hall call being answered at that landing if there was one, since whoever
  is stepping in has already said which way they are going, and otherwise from the next queued call;
  both notes are chosen on arrival rather than as each plays, so a call landing in between cannot
  leave a rising chime finishing as a falling one. The scheme belongs to the elevator, like the sound
  toggle and the cabin size, and existing elevators start on Standard.
- **Alarm bell** on the Elevator Car Panel, held down to ring rather than firing a fixed burst. It
  rings in the cabin and at every loaded landing at once, because an alarm only the trapped passenger
  can hear is not an alarm — whoever can help is by definition not in the cabin. Holding is
  self-limiting where a burst is not, and it is what the real button does: the button repeats "still
  held" while it is down and the elevator stops ringing when those run out, so an alarm can never be
  left running by a disconnect, a closed screen or a player dying with the panel open. It is the one
  sound not gated on the elevator's sound toggle — that switch is for the noises the elevator makes
  on its own, and a button someone is actively holding down that produces no sound is
  indistinguishable from a broken one. Both schemes ring the same bell, since an alarm should not
  sound like a pleasant arrival whatever the rest of the scheme sounds like.
- **Emergency stop.** While a cabin is moving it sweeps its own shaft every five seconds and stops if
  it finds anything living in there. The sweep covers the volume the cabin has actually travelled
  since the last one rather than a fixed window around where it happens to be — a window is only safe
  if the cabin cannot outrun it, and at the top speed it covers a hundred blocks between sweeps. It
  is the cabin's footprint extended vertically rather than a radius, because the shaft is exactly the
  column the cabin sweeps: somebody standing on a landing beside it is in no danger and must not be
  able to halt the lift. Riders are told apart from hazards by where their feet are — inside the
  cabin's own span, rather than merely touching it, since anything standing on the roof touches the
  cabin from above and anything pressed against a wall from the shaft side touches it from without.
  Anything on the roof therefore stops the lift, the roof being outside the cab. Stopping means
  crawling to the nearest floor rather than halting mid-shaft, which would trap whoever is inside,
  and then sitting there with the doors open for thirty seconds — open, because whoever is in the
  shaft may want out through the cabin, and whoever is inside should not be held in a box that has
  just stopped for an emergency. That timer is a minimum rather than a licence: if the shaft is still
  occupied when it expires, it buys another thirty seconds instead of the elevator deciding its wait
  was up and running into someone. Returning to service resets the calls rather than resuming them,
  since half a minute out of service is long enough that whoever pressed those buttons has had every
  chance to walk off, and a lift setting out on errands nobody is waiting for is worse than one that
  asks to be told again. Every readout flashes "E" and "ST" while it lasts, driven off world time so
  the whole building is on the same beat without anything synchronising it. Emergency is per elevator
  and never per bank: a bank shares dispatch, not shafts, and halting every car in a building because
  one shaft has a person in it would strand everybody else, which is its own hazard rather than a
  cure for this one.
- **Elevator Bank Lobby Panel.** A destination-dispatch station bound to several elevators at once:
  you enter where you are going before boarding, and it decides which car collects you. There is no
  up/down call on it, deliberately — a bank knows the whole trip the moment the destination is
  chosen, which is the only reason it can send a car that is already going that way. Elevators know
  nothing about banks: binding lives entirely on the panel, so a banked elevator is an ordinary
  elevator that also happens to receive calls from one, keeps its own controllers, buttons and doors,
  still works alone, and leaves nothing to clean up when the panel is pulled out. Banking is
  something a building does, not a mode an elevator is in. Dispatch scores each car by distance, by
  whether it is mid-trip and which way, and by how many calls it already has; a car already booked to
  collect from your landing travelling your way beats everything else outright, because sharing the
  trip is the entire point of a bank. Destinations are held back rather than queued when the call is
  placed — a destination in the queue is indistinguishable from a floor the car should already be
  going to — and move into the queue when the car reaches the pickup floor, all at once, which is
  exactly how two people heading the same way end up sharing a trip. A banked pickup holds the
  landing for fifteen seconds rather than the usual one, since whoever called it is walking over
  rather than standing at the doors; "Close doors" cuts that short, which is the only reason that
  button exists. Binding toggles: right-clicking a controller with the panel item adds that elevator
  to the bank and clicking it again takes it out, so there is one gesture to learn. Right-clicking a
  placed, configured panel with another panel item copies its whole bank — a lobby with stations
  facing three ways would otherwise mean walking the binding route three times, and a bank where one
  panel knows about three cars and its neighbour knows about two is a miserable thing to debug.
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
  arrows light while a call is outstanding. One panel can serve a whole bank: right-click a
  controller in each shaft it should cover, and a press sends exactly one elevator — whichever is
  best placed to answer, sharing a car already coming for somebody going the same way rather than
  fetching the lot. Linked to one shaft it is unchanged. Linking works the same way as the bank
  lobby panel's, including the rule that shafts must agree about the floors they share, and
  sneak-clicking a placed panel lists what it is linked to.
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

- **Elevator doors are drawn in one batch.** Each door block was drawn on its own -- lighting set up,
  texture bound and one box sent -- so a district in view cost over a thousand draw calls a frame.
  Doors now go into Forge's shared batch and are drawn together. They look exactly as before; their
  shade is now fixed rather than depending on whatever happened to be drawn just ahead of them. On
  the regression suite's 290-doorway district, the frame's block-entity time fell by 45%.

- **A departing cabin costs less to send.** Every player in the dimension is sent the cabin's
  contents when it sets off. That included, for every block entity in it, a second full copy of its
  data that only the server uses (to drop the cabin as items), and each collision box as a compound
  of six named numbers. Players now get neither: the copy is left out and the boxes go as one packed
  array. Saved worlds are unchanged.

- **Elevators no longer all run their periodic checks on the same tick, and a fire recall stops
  causing a tick spike.** Every elevator counted ticks from zero, and all of a world's elevators
  are created together, so every occupancy count, alarm poll and comparator check in the world
  landed on the same tick. Each elevator now starts at its own phase, and the overload check and
  cabin music share one occupancy count when they coincide. With a recall blocked on the
  regression site, the worst tick fell from 22.7 ms to 2.2 ms.

- **Doors work out their landing only when something it depends on changes.** Once a door's
  redstone power was cached, most of what it still cost every tick was finding its elevator again
  and scanning that elevator's floors for its own landing. Doors now keep the answer until their
  binding changes, an elevator is added, removed or rebuilt, or the elevator's floors or cabin height
  change. The elevator's per-landing door bookkeeping also stops allocating a number object per
  lookup.

- **A rider no longer bobs up and down when the cabin stops, or falls out when it next leaves.**
  The cabin's height is advanced by adding its speed each tick, so it reaches a landing a fraction
  of a nanometre short -- 169.9999999999995 rather than 170 -- and the rider was set down on that.
  When the landing put the floor back underneath them, their feet were already inside it, and
  vanilla collision lets anything already inside a block fall through it. The server refused each
  fall and put the rider back where they had been, so the view jumped by a few centimetres every
  other tick for as long as the cabin stood there; and when it departed, the rider was mid-fall and
  went down the shaft. Riders are now set down on the block grid when they are within float noise
  of it. Found by the new regression suite (`tools/regression`), which lost a survival rider on
  every run before the fix and none in three runs after it.

- **Open doors and wall panels no longer throw an exception on every frame.** Forge works out
  where a block entity is drawn from its block's collision box, and in 1.12 a block without one --
  every wall panel, and every door while it stands open -- hands it a null box, so the default
  threw and caught an exception for each of them on every frame. They now give their own block as
  the render box. The text-only fixtures (indicators, call panels, remote displays, lobby panels and
  car panels) also stop being dispatched beyond the distance at which their renderers already drew
  nothing, instead of Forge setting each of them up out to 64 blocks. Nothing looks different.

- **Door elevator re-checks no longer all land on the same tick.** Every door re-checks every
  two seconds that it is bound to the nearest cabin, and every door loaded together did it on the
  same tick, scanning every landing of every elevator in the dimension. The re-checks are now spread
  over the interval by position, each elevator is measured once rather than once per landing, and
  elevators out of link range are skipped outright. A newly placed door still binds on its first
  tick. Doors and car panels, which give no comparator signal, also no longer register for
  comparator updates, so an arriving car stops refreshing comparators around each of its doors; and
  a fixture that moves to a different elevator now deregisters from the old one.

- **A fire recall that cannot dispatch no longer retries every tick, and a rider's landing is
  counted once.** A recalled car that could not leave -- no cabin found, the recall floor
  obstructed, the car overloaded -- re-scanned its shaft every tick for as long as the alarm
  sounded; it now retries once a second. And on the way down, each floor block under a rider
  re-ran the landing's side effects -- the fall event, the fall-damage grace and, on the rider's
  client, a packet to the server -- several times a tick. They now run once per landing.

- **Elevator doors no longer cost the server time while nothing is happening to them.** Every
  door block re-read the redstone power of its whole doorway on every tick -- a walk of the doorway
  and six redstone reads per block, repeated by each of the doorway's blocks -- although power can
  only change when something next to the doorway does. A district of 1,240 door blocks on a live
  server spent nearly four fifths of the server thread there. The doorway's power is now cached:
  any neighbour change on any block of the doorway marks it stale, the first of its blocks to tick
  re-reads it once for all of them, and a safety re-read every two seconds (staggered by position)
  catches changes that arrive without a neighbour update, such as world edits. Door behaviour is
  unchanged -- redstone, cabin arrivals, car-panel buttons, the auto-close and the obstruction hold
  all work as before. On a dev server with 1,120 door blocks, door ticking fell from about 2.0-2.3
  ms to about 0.25-0.29 ms per tick.

- **A rider no longer falls through the cabin floor when the server runs slow.** The report was
  precise: about a floor into a descent, the passenger dropped through the floor to the bottom of
  the shaft while the elevator above them made an emergency stop. Reproduced on a dedicated server
  by freezing the server process for under a second as a car departed. The client simulates the
  moving cabin on its own clock and holds its rider on the floor, so whenever the server stalls --
  or merely ticks below twenty a second, which a busy modpack server does all evening -- the
  position the rider's client sends is further along the trip than the server's cabin. Going down,
  the server's shaft sweep forgave a passenger no further ahead than the cabin's own floor, so after
  a floor or so of drift it read its own rider as somebody standing in the shaft below and stopped
  for them; the stop then told the rider's client the cabin was a floor above where they were
  standing on it, and they fell the rest of the way. The sweep now remembers who it accepted as a
  passenger and lets them run up to three seconds of travel ahead while they stay in the cabin's
  column, and a mid-trip update that replaces the client's cabin -- an emergency stop, a call
  queued from another floor -- carries the rider to the cabin's new position instead of leaving
  them standing on air where it used to be. A car that stops because somebody genuinely is in the
  shaft still stops exactly as before.

- **A bank's doors no longer stay bound to the car next door.** In a bank the shafts stand side by
  side, so a doorway at one shaft's mouth is within link range of the other's cabin too, and an
  earlier adoption rule, which measured to the controller column rather than to the cabin, could
  pick the neighbour. That binding was then permanent: a door only went looking again when its
  binding named no landing at all, and this one named a real landing at the right height. The
  result was the pair of reports from the field -- a landing's doors that never open for the car
  that arrives, and doors that open for a car in the other shaft -- and it was found in exactly
  that state in the world the wiki screenshots were taken in. Every bound door now periodically
  asks whether a strictly nearer cabin exists and moves to it, so worlds built before the fix heal
  on their own instead of needing every doorway broken and replaced.

- Elevator doors could sit out a whole stop without opening, and the car panel's own door buttons
  did nothing while every other button on it worked. Four faults, each enough on its own: a door
  measured its landing as a flat two blocks either side of the controller, which a doorway standing
  in the mouth of a taller or offset cabin falls outside of; it looked for its elevator by distance
  to the controller column rather than to the cabin, so a deep cabin's doors were out of link range
  entirely and a bank's doors could adopt the car next door; a binding that stopped resolving —
  because that landing's controller had been broken and rebuilt — was never retried, leaving the
  door bound to nothing for good; and an open request the door could not act on yet was spent
  anyway, on the reasoning that arrival would raise a fresh one, so any tick where the door and the
  elevator disagreed about the cabin being there swallowed the request in silence.

  A door now finds its elevator by the **cabin it is standing in the mouth of**, sized and placed as
  configured — cabin size, side offset, depth offset and height offset all count — rather than by
  proximity to the controller, which need not sit behind its cabin at all. It also keeps checking
  rather than deciding once: a binding that stops describing where the door stands, whether because
  the landing was rebuilt, because the door item was bound to a different lift by hand, or because
  the cabin was resized or moved afterwards, sends the door back to look for the landing it is
  actually at. The door's status readout gained an honest answer to match: its "serves landing" line
  tested only that the elevator had a controller at that height, which it does by construction, so
  the one line that exists to report this failure could never report it.

- The bank lobby panel's destination buttons showed one character of the floor's name, so floor 10
  read as "1" — and floors 1 to 9 were right only by luck. Core Lib's `format()` is
  `getFormattedText()`, which wraps every piece of a component in its style code and a trailing
  reset code, so the generated name of floor 10 was twelve characters rather than seven. Drawing it
  hid that, since the font renderer eats the codes; keeping the first three characters for a button
  face did not. Floor names and the scrolling readouts are now built as plain text, which is all
  they ever were — what colour a readout draws in is decided at the draw call, from the floor's
  dye.

- A block left loose in a shaft was carried around as though it were the cabin, with nothing said
  about it. Any movable block in the cabin space at a landing counts as a cabin — the mod has no
  other way to know what a cabin is — so the search that fetches one takes whichever is nearest the
  floor asked for, which can be the stray rather than the car. Every readout then moves while the
  car stays put, because the elevator really is carrying something. It now tells whoever pressed the
  button which floors still look occupied once the car has left, naming them. The automatic paths —
  fire recall, the call queue, bank dispatch — have nobody to tell, which is why this could take a
  building apart in silence.

- Pressing a button while an elevator was travelling broadcast the entire contents of its cabin —
  every block, plus the data of any block entity among them — to every player in the dimension. The
  cabin only changes when a trip begins, so it now rides along only then; a lamp lighting or a call
  being queued sends the state alone. For a large cabin that is most of the message gone.

- A chunk loading while a cabin was in flight could delete the elevator's floors and spill the whole
  cabin onto the ground as items. A controller's block entity is not always present the instant its
  chunk loads, so a floor could look missing when it was only not ready yet. Floors are no longer
  audited while the cabin is moving; one that has genuinely gone is caught by the next check.

- Wall panels were indistinguishable grey slabs in the inventory. Everything a panel shows — readout,
  arrows, buttons — is drawn by its block entity renderer, and an item does not run one, so the icon
  was blank metal on every side. Their item models now carry a darkened screen, which is item-only and
  cannot show through in the world.
- The car panel offered door and alarm controls with nothing on its face to suggest they existed. Both
  car panels now draw them: an alarm bar across the bottom, and on the bank panel the pair of door
  buttons above it, laid out like the screen they open. Both plates hang three pixels below their
  block to make room, which costs nothing since a panel is never mounted flush to a floor.

- Passengers were thrown onto the roof of the cabin whenever it stopped. Arriving pushes anything
  standing where a block is about to appear, but it counted any contact at all as being inside a
  block — and standing on the cabin floor is contact, which is what passengers do. It now requires a
  real overlap in all three directions, so resting on the floor, leaning on a wall and standing in
  front of a two-pixel wall panel are all left alone, while a body genuinely caught in masonry is
  still moved clear.
- The shaft sweep found people the cabin had already driven through rather than people it was about
  to reach. It looked ten blocks either way every five seconds, over which a cabin covers twenty at
  the default speed. It now sweeps twice a second and reaches further ahead the way it is going, by
  everything it will cover before the next sweep.

- Pressing the second arrow at a landing did nothing visible. Both directions were recorded, but a
  press that only added a direction never told the client, so the second lamp stayed dark and the
  button looked broken.
- A floor pressed on the car panel drove the cabin away mid-emergency, with somebody still in the
  shaft — the one thing the feature exists to prevent. The call queue was already held during an
  emergency; a direct press was not.
- The bank lobby panel said which world height a car was coming to rather than which floor.
- A lobby panel item appeared to accumulate links endlessly. It was counting controllers rather than
  elevators, so a second controller in the same shaft added a duplicate. Linking is by shaft now, and
  the item deliberately still keeps its links after a panel is placed, so one panel per floor lobby
  takes no extra work — sneak and use in the air to empty it for a different bank.

- The cabin wrote its blocks into the world without regard for anyone standing there, so arriving
  entombed them. The emergency stop made this sharp rather than theoretical: it levels to the
  nearest floor precisely because somebody is in the shaft, so the safety feature could bury the
  person it fired for. Anyone in the way is now shoved clear of the cabin in the direction it was
  travelling, the way a piston pushes; passengers riding the hollow middle are left alone.
- The destination was checked once before departure and never again, so anything built there in the
  meantime was destroyed on arrival — and a block the cabin was not allowed to break made it drop
  its own floor block as an item instead. It is rechecked in flight now, and the cabin diverts to
  the nearest clear floor.
- The landing panels, car panel and bank lobby panel did not check that whoever pressed them was
  nearby. The elevator screen already did.
- The bank lobby panel stayed silent during an emergency. It now flashes when — and only when —
  every car in the bank is out of service.
- The door's "no landing found within 12 blocks" message said twelve whatever the real range was.

- A bank call to a car already standing at your floor took the request and then never moved. A banked
  destination was only ever collected on arrival, and calling a car to the floor it is already on
  does not produce an arrival — the hall call just opens the doors and returns. So the commonest
  request of all took the call, told the player a car was on its way, and then sat there forever.
  Worse, the stranded booking made the "already coming for you" test keep answering yes, so dispatch
  went on assigning that car to every later request from that floor. Collecting is now a method both
  the arrival path and the already-here path call.
- An idle cabin never spent its dwell timer, so it appeared dead for up to fifteen seconds when next
  called — thirty after an emergency stop. The counter only ran down while something was queued,
  because the empty queue returned first, so an unspent wait was kept forever and handed in full to
  whoever called next. It now counts down before the queue is looked at, which is what a wait at a
  floor means.
- Dispatch could assign a car that was emergency-stopped. A halted car is not a slow car, it is one
  that is not coming, and sending somebody to wait for it is worse than telling them no elevator is
  available. `isEmergencyStopped()` existing with no callers at all was the tell.
- The elevator controller's screen acted on every message it received without checking anything.
  Nothing established that the sender was anywhere near the elevator, so a modified client could
  resize, rename, re-speed or re-target any elevator in the world from any distance; thirteen of the
  messages also fetched the elevator group with no null check, so a controller broken while its
  screen was open crashed the server tick rather than doing nothing. Both checks now live in a base
  class, because the answer is the same for all seventeen and a check that has to be remembered
  seventeen times is one that will be forgotten once. Reach uses the same range vanilla containers
  use to decide an open screen is still usable, since the screen stays open while you walk. The car
  panel's messages are deliberately left out: that panel rides inside the cabin, where its position
  is not a world position, so a reach test would have to account for the fake level before it could
  be trusted not to reject legitimate presses.
- Breaking a controller while the cabin was travelling to it crashed the world tick on arrival. The
  target still pointed at a floor that no longer existed, and stopping looks that floor up by y level
  and indexes the floor data with the result. The cabin is now sent to the nearest surviving floor
  instead, which turns a controller broken mid-trip into a change of destination rather than a crash.
- Saves written before the `floorData` tag existed crashed on the first display draw. Reading always
  filled the floor list from its int array but only filled the floor data when the tag was present,
  while every other lookup in the class indexes the two lists together with a plain `get()`. Both are
  now reconciled to the same length after reading; padding costs nothing and turns a crash into an
  unnamed floor.
- Arriving at a landing cleared both call arrows at once, so a car sent to collect somebody going up
  also put out the down arrow and discarded that call, leaving whoever pressed it waiting for a car
  that was no longer coming. Only the direction actually being served is cleared now, and any
  direction still outstanding re-queues the floor so the car comes back for it. With both arrows lit
  the car answers the one it is already set up for, since only one journey is about to happen.
- Calls discarded from the queue left their arrows lit. A call whose floor no longer exists, one the
  cabin turns out to be sitting at already, and one that cannot be dispatched because the shaft is
  obstructed were all dropped from the queue with the lamp still on. A lit arrow with no call behind
  it is worse than no arrow at all, because pressing it again looks like it did nothing. All three
  paths now put the arrows out, and the one where the cabin is already there opens the doors, which
  is what the passenger was asking for.
- The controller screen's "Show buttons" heading sat above a checkbox reading "Hide controls", so the
  label and the control said opposite things and the screen appeared to be lying whichever way the
  box was ticked — fallout from relabelling the checkbox when hiding the controls started actually
  disabling them. The heading is now just "Controls", which names the section without asserting a
  direction and leaves the checkbox to say which way it goes.
- The per-tick cap on how many floors may have their whole cabin volume scanned counted nothing:
  the counter it reads was zeroed each tick but never incremented, so the cap was never reached.
  It matters more than it did when it was written, because that scan is now reached from every
  door block entity each tick — a doorway has up to four — and from five renderers each frame.

- A moving cabin now carries its own courtesy light (`movingCabinLight`, level 6 by default, 0 to
  turn it off). A cabin with no light source of its own has nothing to be lit by while it travels —
  it is a pocket of air in an unlit shaft — so it went dark regardless. This lights the cabin, not
  the shaft around it.
- A moving cabin went dark inside, even one with a glowstone floor. A cabin's blocks are lifted out
  of the world while it travels, so anything luminous in it stops lighting anything; the cabin was
  lit only by whatever the shaft happened to be at the height it was passing. Its lighting is now
  floored at what the cabin itself emits, so a lit cabin stays lit on the way — without pretending an
  unlit one is bright.
- Panel readouts, call arrows and floor lamps now draw at full brightness, as lit displays should.
  This matters most inside a moving cabin, which is the dimmest place a panel is ever mounted and
  exactly when you want to read the floor.

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
- On a server, an elevator carrying a second player emergency-stopped for an obstruction that was not
  there. A passenger's position on the server is the one their client last sent, worked out against
  the client's own copy of the cabin, which is itself a little behind — so the shaft sweep compared a
  round-trip-old passenger against where the cabin is now, found their feet below the floor they were
  standing on, and read a passenger as somebody in the way. The sweep now allows for that delay,
  reaching back only the way the cabin came so that nothing it is actually approaching is excused.
  Singleplayer never showed this because there is no round trip to be late by.
- Passengers were flung onto the cabin roof as it arrived, for the same reason. The cabin shoves
  anything caught in the blocks it is about to place clear of the whole cage, and a passenger whose
  stale position had them a few centimetres inside the floor they were riding on counted as caught.
  Anything merely settled into a surface is now set down on top of it and left where it was, and only
  something genuinely buried is evicted.
- A cabin one block tall emergency-stopped on every trip it carried anybody, and never counted a
  passenger for the overload check or the cabin music. Both ask whether feet are inside the cabin and
  treat the top face as its roof, which is right for a cabin and exactly wrong for a platform — where
  the top face is the floor and standing on it is the only way to ride at all. A cage is now asked
  whether it has an inside to be in, meaning a cell of its own that would hold somebody up, and a
  cage without one is ridden from on top. This covers a platform of any height and any shape, which
  matters because a cage needs only a single block to exist, so most platforms are mostly empty
  cells.
- Anything the cabin's underside came down on was left with its head inside the cabin rather than
  flush beneath it: the push subtracted the entity's width from its feet where it needed its height.
  Nothing corrected it in flight, since the cabin's blocks are out of the world while it travels, so
  the error was only cashed in on arrival — as an eviction down the shaft.

### Changed

- **The dev client and the dev dedicated server run from separate directories**, `run/client` and
  `run/server`, so both can be up at once from one checkout and the client can join the server.
  The problems people report -- bank doors that do not open, doors that open for the wrong car, a
  rider falling through the cabin floor -- come from dedicated servers, and an integrated server
  does not walk the same packet and chunk-loading paths, so a singleplayer world was never going to
  reproduce them. Each side carries its own MCMCP config, so both can be driven at once.

- **Every pull request now boots a dedicated server** from the build it just made
  (`.github/scripts/server-smoke-test.sh`), because a client-only class reached from common code
  or a mixin listed in the wrong array compiles cleanly and only fails at server startup, which is
  precisely where this mod's players are.

- **The release workflow states the version instead of inferring it, and checks the release it
  made.** A dispatched release and the pushed pre-release of the same commit leave both tags on it;
  the build is now told which one it is building (`-PmodVersionOverride`), the other local tags are
  removed for good measure, and the run refuses to publish unless the built version is the tag.
  The release entry is created as an empty draft, the jar is uploaded separately with retries, the
  release is read back from the API to confirm the asset is there, and only then is it published.
  The publish action's own uploader lost assets on a sibling mod's release on 2026-09-17 and left
  it an invisible draft; this repo had the same action.

- **The elevator's name is legible on the bank indicator and no longer crowds the car panel.** On the
  indicator it stood 1.2 face pixels tall, which is present in the way a watermark is present; the
  four gaps between elements gave up a tenth of a pixel each and it now stands 1.7, against the floor
  line's 1.8, so the hierarchy between them survives. On the car panel its screen overlapped the top
  row of button sockets by half a pixel. The name was already as small as a name usefully gets, so
  the room came from the other direction: the button bank and the alarm bar moved down into plate
  that was not being used.

- **The lobby and landing call panels stopped re-resolving their elevators several times a frame.**
  Each question a lobby panel's readout asked — is the bank halted, which landing is this, what is it
  called — walked the panel's bindings and allocated a list of its own, five times over to put one
  word on a screen; the call panel did the same once for its readout and once for each arrow, and
  before the distance cutoff that exists precisely to skip work on panels nobody can read. Both now
  resolve once, after the cutoff, and ask everything of that one answer.

- **The elevator controller's two sound controls are now one.** Off is the first answer to "what does
  this elevator sound like", so a separate on/off checkbox beside the scheme button was two controls
  for one question — and the screen had no row to spare for the out-of-service switch.

- **A lobby panel now shows which floors already have a car coming.** Those buttons highlight and
  alternate between the floor and the name of the car on its way to it. The request leaves the panel
  the instant it is made and the screen stays open, so a press previously left no trace on the thing
  that was pressed; naming the car also answers what a passenger asks next. That a lobby panel is
  shared makes this better rather than worse — everyone waiting can see what is already arranged.

- **Linking a bank lobby panel is now about elevators rather than controllers.** An elevator is its
  shaft, so clicking any controller in a shaft links or unlinks that whole elevator; clicking a
  second controller in the same shaft used to add a duplicate, after which the panel reported two
  elevators while dispatching to one. One controller per shaft is all it ever needed — the panel
  finds the rest of that shaft's floors, and its own landing, by itself.
- **A bank refuses elevators whose floors do not line up with the ones already linked.** Shafts may
  serve different floors — an express car skipping the lower half of a building is a real
  arrangement — but a floor two shafts share must have the same name and the same height. Caught
  when you link, since a misaligned bank does not look broken: it quietly turns a shared floor into
  a one-car floor.
- **Sneak-click a placed lobby panel to see what it is linked to**, floor counts and all, including
  any elevator whose controller has since gone. Binding is the one thing about the block that cannot
  be seen in the world.

- **Elevators now wait ten seconds at a floor rather than one**, which is boarding time — one second
  was not long enough to walk in, so a lift could answer a call, open its doors and leave before
  anyone reached it. "Close doors" inside the cabin cuts it short. Configurable as
  `elevatorDwellTicks`.

- **"E" and "ST" on the emergency readouts are translatable.** They were the only user-visible words
  in the mod that were not, being an English abbreviation of "emergency stop".
- **The full-cube Elevator Display now reads from 30 blocks**, matching the other landing fixtures.
  The car panel keeps its shorter 15, since it is read at arm's length inside the cabin.

- **Calls are now collected mid-trip.** A cabin travelling from the first floor to the tenth used to
  sail past somebody calling from the fifth and collect them on the way back, because the queue was
  only consulted while the cabin was stopped. It now stops for a call ahead of it, which is what
  collective control means and most of the point of having a call queue at all. Only calls it can
  still brake for are taken, tested with the same braking relation the movement code itself uses
  rather than a second opinion that could disagree with it — so a floor accepted is never one the
  mover would have to overshoot and come back to. At the default speed that is under a block of
  warning; at the maximum it is twenty, and a call inside that distance is correctly left for the
  return trip. The displaced target goes back in the queue rather than being dropped, so taking the
  nearer floor first costs nothing. Emergency stops are exempt: they are on their way somewhere
  specific and must not be diverted.
- **Elevator doors reopen rather than closing on whoever is standing in them.** Doors closed purely
  on their timer and on redstone, and nothing ever looked to see whether somebody was in the way. The
  question is asked of the whole doorway rather than of the block that happens to contain the player,
  for the same reason redstone power already is: the halves share an open state, so a question only
  one of them could answer yes to would close half a door on somebody standing in the other half. It
  is checked only at the moment an open door would close, so the entity scan costs nothing while the
  door stands open or shut, and it holds for a second rather than cancelling the close outright —
  stepping clear then lets it shut a moment later. Living things only: a dropped item should not be
  able to hold a door open indefinitely, and a door closing on one costs nothing.
- **The landing readouts now draw from 30 blocks rather than 15** — the Remote Elevator Display, the
  Remote Elevator Indicator, the Remote Elevator Call Panel and the bank lobby panel. Not from 32, as
  it looked: the constant is pre-squared, so the old cutoff was 15 blocks. The Elevator Car Panel is
  deliberately left at 15, because it is read from arm's length and it is the one that redraws while
  the cabin moves; the full-cube Elevator Display is unchanged too. Text is expensive to draw, which
  is why upstream put a cutoff here at all. Past 64 blocks, raising this alone would achieve nothing
  — block entities stop being rendered at that range unless they override
  `getMaxRenderDistanceSquared`, which these do not.
- **Generated floor names now start at 1 rather than 0.** The lowest floor of a shaft read "Floor 0";
  it now reads "Floor 1", which is what people expect a ground floor to be called. **This is visible
  in existing worlds:** every unnamed floor now reads one higher than it did, so a shaft someone knew
  as floors 0 to 5 is now 1 to 6. Nothing moves and no elevator behaves differently. The renaming
  happens at the single point where a floor index becomes something a player reads, so every readout
  picks it up together — the elevator display, all four panel styles, the car panel's floor list and
  the placeholder in the controller's name field — while indices stay zero-based everywhere else,
  including in the packets and the call queue. Floors named by hand are untouched, since a custom
  name replaces the generated one outright.

- The elevator controller's "Show buttons" option is now **"Hide controls"**, and it disables the
  controls rather than only hiding them. Previously the face stayed clickable with the graphics
  turned off, so an invisible button still drove the elevator. Hiding them pairs a plain controller —
  still needed to mark a floor — with the new wall panels. The setting is the same underlying flag,
  so existing elevators keep whatever they had, and the config screen still opens from any other side
  of the block, so controls can always be turned back on.

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
