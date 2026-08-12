# Installation

## Requirements

| | |
| --- | --- |
| Minecraft | 1.12.2 |
| Mod loader | Minecraft Forge for 1.12.2 |
| Java | 8 |
| Dependencies | Core Lib and Config Lib — **both required** |

## Dependencies

This mod requires SuperMartijn642's libraries, same as upstream. They are hard requirements: without
them the game will not start.

- [SuperMartijn642's Core Lib](https://www.curseforge.com/minecraft/mc-mods/supermartijn642s-core-lib)
- [SuperMartijn642's Config Lib](https://www.curseforge.com/minecraft/mc-mods/supermartijn642s-config-lib)

Get the **1.12.2** builds of both.

## Installing

1. Install Minecraft Forge for 1.12.2.
2. Download the two libraries above and drop them in `mods/`.
3. Download a build of this fork from
   [Releases](https://github.com/Mica-Technologies/LDMovingElevators/releases) and drop it in `mods/`
   as well.
4. Start the game.

!!! warning "Get the jar from *this* repository, not CurseForge"

    The CurseForge listing is SuperMartijn642's official mod. It is a different mod with a different
    feature set — none of the [fork additions](../about/fork.md) are in it. Mixing the two is not
    supported and they share a mod id, so Forge will refuse to load both at once.

## Client and server

Install the same jar on both. The mod has client-only rendering code, but the simulation is
server-side, so a dedicated server needs it too and every player needs it to join.

## Configuration

On first launch the mod writes `config/movingelevators-common.toml`. All eleven options are
documented in the [configuration reference](../reference/configuration.md).

It is a **common** config, which means the server's copy is what counts in multiplayer. Changing your
own copy of `maxCabinSpeed` will not let you outrun the server's limit.

## Optional integration: City Super Mod

If [City Super Mod](https://github.com/Mica-Technologies/minecraft-city-super-mod) is installed, the
[Elevator Alarm Linker](../reference/blocks.md#elevator-alarm-linker) can tie an elevator to a CSM
fire alarm panel, so the car recalls to a chosen floor whenever that alarm sounds.

This is entirely optional. CSM is not a dependency, nothing breaks without it, and the alarm linker
simply has nothing to bind to.

## Upgrading from the official mod

Worlds built with upstream's Moving Elevators load here — the block and item ids are unchanged, and
existing elevators keep working. Existing elevators start audible and on the **Standard**
[sound scheme](appearance.md#sound).

Going back the other way is where it breaks. A world that has used the fork's blocks — doors, wall
panels, banks — will drop them as unknown blocks on the official mod, because those blocks do not
exist there. **Back up before switching either direction.**
