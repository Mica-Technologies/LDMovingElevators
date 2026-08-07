# LDMovingElevators

An **unofficial fork** of [Moving Elevators](https://github.com/SuperMartijn642/MovingElevators) by
[SuperMartijn642](https://github.com/SuperMartijn642), maintained by
[Mica Technologies](https://github.com/Mica-Technologies) for **Minecraft Forge 1.12 only**.

> ### ⚠️ This is not the official Moving Elevators mod
>
> - This repository is **not affiliated with, endorsed by, or supported by SuperMartijn642.**
> - Builds published here are **not** the official mod. They are our own builds, with our own
>   changes, and they carry our own version numbers.
> - **Do not report problems with this fork to the upstream project.** SuperMartijn642 did not
>   write these changes and cannot support them. File issues on
>   [this repository's issue tracker](../../issues) instead.
> - If you want the official mod, get it from
>   [CurseForge](https://www.curseforge.com/minecraft/mc-mods/moving-elevators) or the
>   [upstream repository](https://github.com/SuperMartijn642/MovingElevators). For anything other
>   than Forge 1.12, the official mod is what you want.

## Why this fork exists

Moving Elevators is actively developed upstream across many Minecraft versions. Our servers and
modpacks run **Forge 1.12**, so this fork exists to keep that one branch maintained and building on
our release infrastructure.

**Scope of support:**

| | |
|---|---|
| **Minecraft version** | 1.12.x (developed against 1.12.2) |
| **Mod loader** | Forge only |
| **Other versions** | Not supported here — use the [official mod](https://www.curseforge.com/minecraft/mc-mods/moving-elevators) |

We track only the `forge-1.12` line. The other loader/version branches inherited from upstream are
left untouched and receive no attention from us.

### Dependencies

This mod requires SuperMartijn642's libraries, same as upstream:

- [SuperMartijn642's Core Lib](https://www.curseforge.com/minecraft/mc-mods/supermartijn642s-core-lib)
- [SuperMartijn642's Config Lib](https://www.curseforge.com/minecraft/mc-mods/supermartijn642s-config-lib)

### Downloads

Builds are published to this repository's [Releases](../../releases) page.

- **Releases** are versioned `YYYY.MM.DD` and are the ones to use.
- **Pre-releases** are cut automatically on every push. They are not guaranteed to be stable, and
  they are pruned after 90 days.

---

## What the mod does

**Moving Elevators** allows you to build real **moving elevators**! These elevators will move
**you** as well as **other entities** on the platform to **multiple floors** at the press of a
button! The platform can have **different sizes** and can move at **different speeds**! You can
even **disguise** the Elevator Controller and Display as **other blocks**!

### Elevator Controller

- The Elevator Controller will be your button panel at every floor
- Right-click a side without buttons to change the platform speed and size, and the floor name
- Right-click the middle button to request the platform and use the other two to move up and down
- Right-click with a block to disguise the Elevator Controller as that block
- Send a redstone signal to request the platform and see if the platform is there with a comparator

### Elevator Display

- The Elevator Display shows the different floors and the current elevator position
- Right-click the current floor to request the platform and use the other buttons to go to different floors
- Right-click a floor button with a dye to change the color of that floor
- Right-click with a block to disguise the Elevator Display as that block

### Remote Elevator Panel

- Can be bound to an Elevator Controller and can be placed anywhere
- Elevator Displays can be put on top just like the Elevator Controller

### Remote Elevator Display

- Shows which floor an elevator is currently at, and nothing else — it has no buttons and takes no
  redstone input
- Bound to an Elevator Controller by right-clicking the controller with it, then placed anywhere
- While the cabin is moving it shows the floor it is nearest to
- A full cube, so it can be disguised as another block like the Elevator Controller and Display

### Remote Elevator Indicator

- The same floor readout as the Remote Elevator Display, but as a slim plate on the face of the block
  behind it, the way a hall indicator sits above a real lift door
- Bound the same way: right-click an Elevator Controller with it, then place it
- Directional — the plate exists on one side only — and it pops off if the wall behind it is removed
- No disguise option, unlike the full-cube display; a two-pixel plate has nothing to disguise. Both
  styles exist side by side, so pick whichever suits the shaft

### Remote Elevator Call Panel

- A tall, narrow landing panel: floor readout at the top, up and down call buttons below it
- The buttons are real hall calls, not the Remote Elevator Panel's "move the cabin one floor" arrows.
  Pressing one fetches the cabin to that landing and tells the elevator which way you then want to
  travel, so the call joins the queue and is served in sweep order
- The arrows light while a call is outstanding, and go out when it is served or dropped
- Right-clicking the readout at the top reports which controller the panel is bound to
- Bound the same way as the other remote blocks, and mounts on a wall like the Indicator

### Elevator Car Panel

- The fixture you ride with: mount it inside the cabin. It shows the current floor and direction of
  travel over a bank of buttons that light for the floors currently selected
- Clicking it opens a list of the elevator's actual floors rather than mapping hits onto the drawn
  buttons — floors are added and removed at will, so a fixed grid can never match a shaft
- Destinations chosen here join the same queue as landing calls, so a full trip is served in one
  sweep
- The screen also carries **Open doors**, **Close doors** and an **Alarm** button. The alarm is held
  down to ring rather than fired in a burst, and it rings in the cabin and at every landing at once
- Bound to an Elevator Controller the same way as the other remote blocks

### Elevator Doors

- Sliding doors for a landing, in two sizes: a 2x2 double doorway whose leaves meet in the middle and
  retract into either side, and a 1x2 narrow one with a single leaf
- Each is placed as one item and removed as one unit, and **finds its own elevator from where it
  stands** — there is nothing to bind. It looks for a landing within 12 blocks at its own height
- They open when the cabin arrives, and when a landing button is pressed while the cabin is already
  there. They close on their own after a configurable dwell, and immediately if the cabin leaves, so
  a landing door never stands open on an empty shaft
- They reopen instead of closing on anything living standing in the doorway
- Redstone power forces them open, as an emergency override
- Stack them for a taller doorway
- Right-click a door to have it report what it found and what it is waiting for

### Elevator Bank Lobby Panel

- A destination-dispatch station bound to **several** elevators at once. You pick where you are going
  before boarding and the panel decides which car collects you
- There is no up/down call on it, deliberately: choosing a destination tells the bank the whole trip,
  which is the only way it can send a car that is already going that way
- Dispatch weighs each car by distance, by whether it is mid-trip and which way it is headed, and by
  how many calls it already has. A car already coming to your landing travelling your way wins
  outright, so two people going the same way share the trip
- A car dispatched from a lobby panel holds the landing for 15 seconds rather than the usual one,
  since whoever called it is walking over rather than standing at the doors. **Close doors** on the
  car panel cuts that short
- Bind by right-clicking each Elevator Controller you want in the bank with the panel item; clicking
  one again takes it back out. Sneak-right-click in the air to clear the item entirely
- Right-click a placed, configured panel with another panel item to copy its whole bank onto it —
  a lobby with stations facing three ways does not need the binding walk done three times
- Banking is something the panel does, not a mode an elevator is in. A banked elevator keeps its own
  controllers, buttons and doors, still works alone, and breaking the panel leaves nothing behind

### Call queue

Buttons and displays pressed while the cabin is already moving are remembered rather than ignored,
and served once it gets there. Calls are dispatched in **sweep order** — the cabin finishes the
floors ahead of it in its current direction before reversing — rather than in the order they were
pressed, so it behaves like a real elevator rather than a one-shot platform. A cabin already
travelling will stop for a call that comes in ahead of it, provided it can still brake in time;
anything closer than that is left for the return trip. It waits a second at each floor before moving
on. Queued calls survive a save and are dropped if their floor is removed.

The Remote Elevator Panel's up/down arrows are deliberately *not* queued: they mean "take the cabin
from this floor to the next one", which only means anything while it is standing there.

### Sounds

Elevators make noise, and there is an on/off toggle for it in the controller's screen along with a
button to pick one of two schemes:

| Scheme | Sounds like |
|---|---|
| **Standard** | Struck bells, in the vein of an older building. Arrival sounds the same whichever way the car is about to go |
| **Modern** | Clean chimes, and the arrival announces the car's next direction: rising for up, falling for down, two flat notes when it is going nowhere |

Both settings belong to the elevator rather than to the controller you happened to open, so an
elevator cannot beep at some floors and not others. Existing elevators start audible and on Standard.

The alarm on the car panel is the one sound the toggle does not silence — a button someone is holding
down that makes no noise is indistinguishable from a broken one.

### Emergency stop

A moving cabin sweeps its own shaft every five seconds, ten blocks above and below, and stops if it
finds a player in there. It is the column the cabin sweeps, not a radius around it, so standing on a
landing beside a moving cabin is safe and will not halt anything; passengers do not trigger it
either.

Stopping means crawling to the nearest floor rather than halting mid-shaft, which would trap whoever
is inside. It then holds the doors open for 30 seconds, and only returns to service once the shaft is
clear — if somebody is still in there when the timer runs out, that buys another 30 seconds. Every
readout flashes "E"/"ST" while this lasts. Calls are kept, so nothing has to be pressed again.

This is per elevator, never per bank: a bank shares dispatch, not shafts, so one blocked shaft does
not strand a whole building.

---

## Configuration

Config lives in `config/movingelevators-common.toml`, written by SuperMartijn642's Config Lib. There
are six options, all under `[General]`:

| Option | Default | Range | What it does |
|---|---|---|---|
| `maxCabinHorizontalSize` | `11` | 1–15 | Maximum width of an elevator cabin. Higher numbers may cause lag |
| `maxCabinVerticalSize` | `11` | 1–15 | Maximum height of an elevator cabin. Higher numbers may cause lag |
| `allowUnbreakableBlocks` | `false` | — | Whether the elevator may move unbreakable blocks. Turning this on can let players move bedrock and portals |
| `doorAutoCloseTicks` | `240` | 20–1200 | How long Elevator Doors stay open before closing on their own, in ticks. 20 ticks is one second, so the default is 12 seconds |
| `elevatorDwellTicks` | `200` | 0–1200 | How long an elevator waits at a floor before moving on to its next call, in ticks — boarding time. The default is 10 seconds. "Close doors" inside the cabin cuts it short; a floor a bank lobby panel sent the car to is held longer, since whoever called it is walking over |
| `movingCabinLight` | `6` | 0–15 | Minimum light level inside a cabin while it is moving. A cabin is lifted out of the world as it travels, so nothing inside it lights anything and it would otherwise go dark. This lights the cabin, not the shaft. Set to `0` for the old behaviour |

---

## Step-by-step guide

### Creating an elevator

1. Craft at least 2 Elevator Controllers
2. Place the first Elevator Controller where you want your elevator
3. Right-click on a side of the Elevator Controller that has no buttons
4. Set your desired platform size and speed
5. Gather the blocks for your platform
6. Place the blocks one block lower than the Elevator Controller, in front of the side with the buttons
7. Place the other Elevator Controllers above or below the first one, facing the same direction
8. Use the middle button to request the platform and the other two to move up or down

### Adding an Elevator Display

1. Craft 1 or 2 Elevator Displays
2. Place the first Elevator Display on top of an Elevator Controller
3. (Optional) Place the second Elevator Display on top of the first one for an extra tall display
4. Use the button for the current floor to request the platform, and the other buttons to go to other floors

### Disguising an Elevator Controller or Display

1. Hold the block you want to use as a disguise in your hand
2. Right-click one of the sides without buttons

To remove the disguise, shift-right-click one of those sides with an empty hand.

### Changing a floor name

1. Right-click one of the sides of the Elevator Controller or Display without buttons
2. Enter the desired floor name in the text field

### Coloring a floor

1. Hold a dye in your hand
2. Right-click the button of a floor on the Elevator Display

### Binding a Remote Elevator Panel

1. Hold the Remote Elevator Panel in your hand
2. Right-click on an Elevator Controller

The Remote Elevator Display, Indicator, Call Panel and Car Panel all use the same item class, so they
are all bound this way too.

### Binding an Elevator Bank Lobby Panel

1. Hold an Elevator Bank Lobby Panel in your hand
2. Right-click each Elevator Controller you want the panel to dispatch — one per elevator is enough,
   and the panel counts them back to you as you go
3. Right-click a controller again to take that elevator back out of the bank
4. Place the panel in the lobby

To give a lobby a second station without repeating that walk, hold a fresh Bank Lobby Panel and
right-click the one you already placed and configured: it copies the whole bank onto the item in your
hand. To empty an item and start over, sneak-right-click with it in the air.

### Placing Elevator Doors

1. Craft an Elevator Door (2x2) or Elevator Door (Narrow) (1x2)
2. Place it in the landing's opening. It faces you as you place it, and there is nothing to bind —
   each doorway finds its own elevator, looking for a landing within 12 blocks at its own height
3. (Optional) Stack another doorway on top for a taller opening

Right-click a door to have it tell you which controller it found and what it is currently waiting
for, which is the first thing to check if a doorway is not opening.

---

## FAQ

**My elevator just went through a block, is this normal?**
Yes. The platform ignores blocks not at floor level, for performance reasons — checking hundreds of
blocks every tick is not a good idea.

**Is there a limit on the distance between floors?**
No.

**Does the Elevator Controller consume energy?**
No.

**Can I use this in my modpack?**
Upstream allows its mod in modpacks. This fork is built for our own packs; if you want to ship it in
yours, please make it clear to your users that it is an unofficial build, not SuperMartijn642's.

---

## Building

Requires a JDK to run Gradle. ForgeGradle 6 needs **Java 17 or newer** for Gradle itself; the mod
compiles against a **Java 8** toolchain, which Gradle will locate or provision automatically.

```bash
# Build the mod (jar lands in build/libs/)
JAVA_HOME="/path/to/jdk-17" ./gradlew build

# Run the dev client / server
JAVA_HOME="/path/to/jdk-17" ./gradlew runClient
JAVA_HOME="/path/to/jdk-17" ./gradlew runServer

# Regenerate data-generator output
JAVA_HOME="/path/to/jdk-17" ./gradlew runData
```

### Changelogs

Two files, kept deliberately separate:

| File | Contents |
|---|---|
| [`changelog.md`](changelog.md) | **Upstream's** release notes, an unmodified mirror of SuperMartijn642's file. Never add fork entries here — upstream prepends to the top of it, so anything we put there conflicts on every merge. |
| [`CHANGELOG-FORK.md`](CHANGELOG-FORK.md) | **This fork's** changes, keyed by the `YYYY.MM.DD` release tags we publish. |

The newest heading in `changelog.md` doubles as the upstream release this fork sits on; the release
workflow parses it and prints it in each GitHub release body.

### Versioning

Release builds take their version from the git tag that CI creates immediately before building
(`YYYY.MM.DD`, or `YYYY.MM.DD-pre.HHMM.<tz>+<sha>` for a pre-release). Local builds with no such tag
fall back to `mod_version` in `gradle.properties`, which records the upstream release this fork
currently sits on. To force a version, pass `-PmodVersionOverride=...`.

See [CLAUDE.md](CLAUDE.md) for the fuller developer notes.

---

## Credits and upstream

All original design, code, and artwork are by **SuperMartijn642**. This fork exists only to keep the
Forge 1.12 branch alive for our own use, and claims no credit for the mod itself.

- Upstream repository: <https://github.com/SuperMartijn642/MovingElevators>
- Upstream README (including SuperMartijn642's own community links):
  <https://github.com/SuperMartijn642/MovingElevators/blob/1.15/README.md>
- Official downloads: <https://www.curseforge.com/minecraft/mc-mods/moving-elevators>

> **Note on community links:** upstream's README links to SuperMartijn642's Discord server. We
> deliberately do not reproduce that link here, because putting it in *our* README implies it is a
> support channel for *this* fork — it is not, and its members should not be fielding questions about
> our builds. Follow the upstream README link above if you are looking for SuperMartijn642's
> community.

## Licensing

The upstream project declares **all rights reserved** (see `mod_license` in `gradle.properties`), and
this fork inherits that. It is published here for use in Mica Technologies servers and modpacks. If
you want to redistribute this mod or this fork, take it up with SuperMartijn642 first.
