# FAQ

## About the mod

**My elevator just went through a block, is this normal?**

Yes. The platform ignores blocks not at floor level, for performance reasons — checking hundreds of
blocks every tick is not a good idea.

**Is there a limit on the distance between floors?**

No.

**Does the Elevator Controller consume energy?**

No.

**Can I use this in my modpack?**

Upstream allows its mod in modpacks. This fork is built for our own packs; if you want to ship it in
yours, please make it clear to your users that it is an unofficial build, not SuperMartijn642's. See
[This is a fork](../about/fork.md).

## About the fork

**Is this the official Moving Elevators?**

No. It is an [unofficial fork](../about/fork.md) for Forge 1.12 only, not affiliated with or endorsed
by SuperMartijn642. Do not report problems with it upstream.

**Why does it show up in game as SuperMartijn642's mod, then?**

The mod id, package namespace and `mcmod.info` metadata are deliberately left as upstream's —
renaming them would break upstream's own coremod and mixin discovery and turn every future merge into
a manual conflict. It does mean your users cannot tell the difference from the mod list alone, so say
so in your pack notes.

**Can I install this alongside the official mod?**

No. They share a mod id and Forge will refuse to load both.

**Will my upstream world still work?**

Yes — block and item ids are unchanged and existing elevators keep working. Going *back* is the
problem: a world that has used the fork's blocks will drop them as unknown on the official mod. Back
up before switching either direction.

## Troubleshooting

**A door will not open.**

Right-click it — it reports what it found and what it is waiting for. The usual cause is height: a
door looks for a landing at **its own height**, so one placed a block above or below the landing
finds nothing. See [Doors](../guide/doors.md#when-a-door-will-not-open).

**The elevator says "This shaft still looks like it holds a cabin".**

A loose block was left in a landing's cabin space. A shaft holds one cabin, and a stray block sitting
where the cab would be is carried as though it *were* the cab. Clear the shaft.

**The elevator says "No cabin at the current floor".**

The cabin space — one block below the controller, in front of the buttoned side — is empty. Build the
platform.

**A controller is not joining my elevator.**

Check its **facing**. Controllers must all face the same direction to be one shaft; one turned the
wrong way is a separate elevator.

**The car is not coming and nothing looks broken.**

Check its service mode. Readouts show **OUT OF SERVICE**, **IS** (independent service), **FIRE
RECALL**, or a flashing **E**/**ST** (emergency stop). See
[Service modes](../guide/service.md).

**The cabin stopped by itself mid-trip.**

Something living was found in the shaft. The cabin crawls to the nearest floor and holds for 30
seconds. Calls are kept — nothing needs pressing again. See
[Emergency stop](../guide/service.md#emergency-stop).

**A bank lobby panel refuses to bind an elevator.**

The two shafts disagree about a floor they share. Shafts in a bank may serve *different* floors, but
a floor they **share** must have the same name and the same height. The message names the floor.

**The up/down arrows on my Remote Elevator Panel do nothing when the cabin is elsewhere.**

Working as intended. Those arrows mean "take the cabin from this floor to the next one" and are not
queued. For a real hall call, use a
[Remote Elevator Call Panel](blocks.md#remote-elevator-call-panel).

**I cannot turn the independent service key switch.**

It needs permission node `movingelevators.independent_service`, or operator level 2. The switch is
drawn for everyone so the mode is discoverable, but only the permitted can turn it.

**The alarm still sounds with sounds turned off.**

Intended. A held-down alarm button that makes no noise is indistinguishable from a broken one.
