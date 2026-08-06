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

1. Hold an Elevator Controller in your hand
2. Right-click on an Elevator Controller

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
