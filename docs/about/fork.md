# This is a fork

**LDMovingElevators** is an unofficial fork of
[Moving Elevators](https://github.com/SuperMartijn642/MovingElevators) by
[SuperMartijn642](https://github.com/SuperMartijn642), maintained by
[Mica Technologies](https://github.com/Mica-Technologies) for **Minecraft Forge 1.12 only**.

!!! danger "This is not the official Moving Elevators mod"

    - This repository is **not affiliated with, endorsed by, or supported by SuperMartijn642.**
    - Builds published here are **not** the official mod. They are our own builds, with our own
      changes, and they carry our own version numbers.
    - **Do not report problems with this fork to the upstream project.** SuperMartijn642 did not
      write these changes and cannot support them. File issues on
      [this repository's issue tracker](https://github.com/Mica-Technologies/LDMovingElevators/issues)
      instead.
    - If you want the official mod, get it from
      [CurseForge](https://www.curseforge.com/minecraft/mc-mods/moving-elevators) or the
      [upstream repository](https://github.com/SuperMartijn642/MovingElevators). For anything other
      than Forge 1.12, the official mod is what you want.

## Why this fork exists

Moving Elevators is actively developed upstream across many Minecraft versions. Our servers and
modpacks run **Forge 1.12**, so this fork exists to keep that one branch maintained and building on
our release infrastructure.

## Scope of support

| | |
|---|---|
| **Minecraft version** | 1.12.x (developed against 1.12.2) |
| **Mod loader** | Forge only |
| **Other versions** | Not supported here — use the [official mod](https://www.curseforge.com/minecraft/mc-mods/moving-elevators) |

We track only the `forge-1.12` line. The other loader/version branches inherited from upstream are
left untouched and receive no attention from us.

## It looks like the official mod in game

Worth knowing if you are shipping this in a pack: the mod id is still `movingelevators`, the package
namespace is still upstream's, and `mcmod.info` still carries SuperMartijn642's author list,
description and website. In game, this fork therefore presents itself much like the official mod.

That is deliberate — renaming the package would collide with upstream's own coremod and mixin
discovery and make every future upstream merge a manual conflict — but it does mean **your users
cannot tell the difference from the mod list alone**. If you ship this, say so in your pack notes.

## Can I use this in my modpack?

Upstream allows its mod in modpacks. This fork is built for our own packs; if you want to ship it in
yours, please make it clear to your users that it is an unofficial build, not SuperMartijn642's.

See also [Credits and licence](credits.md) — the upstream project declares all rights reserved, and
this fork inherits that.

## Community links

Upstream's README links to SuperMartijn642's Discord server. We deliberately **do not** reproduce
that link here, because putting it in *our* documentation implies it is a support channel for *this*
fork — it is not, and its members should not be fielding questions about our builds.

Follow the [upstream README](https://github.com/SuperMartijn642/MovingElevators/blob/1.15/README.md)
if you are looking for SuperMartijn642's community.
