# Appearance and sound

## Disguises

The **Elevator Controller**, the **Elevator Display** and the **Remote Elevator Display** are full
cubes and can be disguised as any other block, so a shaft can be finished in whatever the rest of the
building is made of.

1. Hold the block you want to use as a disguise.
2. Right-click one of the sides **without buttons**.

To remove a disguise, **sneak-right-click** one of those sides with an empty hand.

!!! note "Wall panels have no disguise option"

    The Indicator, Call Panel, Car Panel, Bank Lobby Panel and Bank Indicator are two-pixel plates on
    the face of the block behind them — there is nothing to disguise. Both styles exist side by side
    deliberately: use the full-cube Remote Elevator Display where you want to hide it, and the slim
    Indicator where you want it to look like a hall fixture.

## Floor names

Right-click a side of an Elevator Controller or Display without buttons, and enter the name in the
text field. Unnamed floors read *Floor 1*, *Floor 2*, and so on.

Floor names matter for [banks](banks.md): shafts that share a floor must give it the same name and
the same height, or the bind is refused.

## Elevator names

The same screen has an **Elevator name** field, which names the whole elevator rather than one floor.

Set it if you have more than one shaft. The [bank lobby panel](banks.md) and
[bank indicator](../reference/blocks.md#elevator-bank-indicator) show it, and *"Elevator A is ready to
board"* is a great deal more use than *"an elevator is ready to board"*.

## Floor colours

Hold a **dye** and right-click a floor's button on an Elevator Display.

## Cabin lighting

A cabin is lifted out of the world while it travels, so nothing inside it lights anything and it
would otherwise go dark mid-trip.

`movingCabinLight` (default `6`) sets a **minimum light level inside a moving cabin**. It lights the
cabin, not the shaft. Set it to `0` for the old behaviour.

## Sound

Elevators make noise. The controller's screen has an on/off toggle and a button to pick one of two
schemes:

| Scheme | Sounds like |
|---|---|
| **Standard** | Struck bells, in the vein of an older building. Arrival sounds the same whichever way the car is about to go |
| **Modern** | Clean chimes, and arrival announces the car's next direction: rising for up, falling for down, two flat notes when it is going nowhere |

Both settings belong to the **elevator** rather than to the controller you happened to open, so an
elevator cannot beep at some floors and not others. Existing elevators start audible and on Standard.

The moments that make sound are: setting off, passing a floor, stopping, the arrival chime, doors
opening, doors closing, a button beep, overload, and the alarm.

!!! warning "The alarm ignores the toggle"

    The [alarm](service.md#the-alarm) is the one sound the toggle does not silence. A button someone
    is holding down that makes no noise is indistinguishable from a broken one.

### Cabin music

Toggled from the car panel's screen, and **on by default**.

It only plays while somebody is actually aboard. An empty lift playing to itself would mean every
lift in a building playing at once, audible from every landing, and a track started for nobody cannot
be switched off by whoever it eventually annoys.

It also follows the sounds toggle: turning sounds off silences the music too.

### A note on the audio itself

No audio ships with this mod. Every sound points at a **vanilla sample**, and the two schemes differ
by volume and pitch rather than by file. That keeps the jar small and avoids shipping assets that are
not ours to ship.
