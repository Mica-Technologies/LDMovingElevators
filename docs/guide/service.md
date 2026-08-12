# Service modes and emergencies

An elevator is in exactly one service mode at a time.

| Mode | Answers landing calls | Answers car buttons | Set from |
| --- | --- | --- | --- |
| **Normal service** | Yes | Yes | — |
| **Independent service** | No | Yes | Car panel (permission-gated) |
| **Out of service** | No | No | Controller screen |
| **Fire recall** | No | No | A [linked fire alarm](#fire-recall) |

Every readout shows the mode, so a car that is not coming tells you why rather than appearing broken.

## Out of service

Toggled in the **controller's screen** — right-click a side without buttons.

The elevator stops answering everything. Displays and indicators flash **OUT OF SERVICE**.

This belongs to the elevator, not the controller you opened, so it cannot be out of service at one
floor and in service at another.

## Independent service

Toggled from the **car panel**, with the key switch on its screen.

An elevator in independent service **answers its own car buttons only, not the floors** — the mode a
real lift is put in to move furniture, or to hold a car for one passenger. Readouts show **IS**.

!!! warning "Turning it requires permission"

    The key switch is gated on the ordinary command-permission check: permission node
    `movingelevators.independent_service`, or **operator level 2** without a permissions mod present.

    Players without it see the switch — it is drawn for everyone, so it is obvious the mode exists —
    and get *"Looks like this requires a key I don't have…"* if they try to turn it.

## Emergency stop

A moving cabin **sweeps its own shaft every five seconds**, ten blocks above and below
(`shaftScanReach`), and stops if it finds a player in there.

It is the **column the cabin sweeps, not a radius around it**, so standing on a landing beside a
moving cabin is safe and will not halt anything. Passengers do not trigger it either.

What happens then:

1. The cabin **crawls to the nearest floor** rather than halting mid-shaft, which would trap whoever
   is inside.
2. It holds the doors open for **30 seconds** (`emergencyHoldTicks`, default 600 ticks).
3. It returns to service only once the shaft is clear. If somebody is still in there when the timer
   runs out, that buys **another 30 seconds** — the setting is a minimum, not a limit.

Every readout flashes **E**/**ST** while this lasts. **Calls are kept**, so nothing has to be pressed
again.

This is **per elevator, never per bank**: a bank shares dispatch, not shafts, so one blocked shaft
does not strand a whole building.

### The emergency stop button

The car panel's screen also has a manual **Emergency stop**, which does the same thing on demand.

## The alarm

The car panel has an **Alarm** button. It is **held down to ring** rather than fired in a burst, and
it rings **in the cabin and at every landing at once**.

The alarm is the one sound the [sound toggle](appearance.md#sound) does not silence — a button
someone is holding down that makes no noise is indistinguishable from a broken one.

## Overload

A cabin carrying more than it should refuses to move and shows **OVERLOAD**, with its own sound.

## Fire recall

Requires [City Super Mod](https://github.com/Mica-Technologies/minecraft-city-super-mod). Without it
the [Elevator Alarm Linker](../reference/blocks.md#elevator-alarm-linker) has nothing to bind to and
the feature is simply absent.

An elevator can be tied to a CSM fire alarm panel so that it **returns to a designated floor whenever
that alarm sounds** and stays there. Readouts flash **FIRE RECALL**.

To link one:

1. **Sneak** and right-click a **fire alarm panel** with the Elevator Alarm Linker. It reports the
   panel it is holding.
2. Right-click the **Elevator Controller at the floor the car should recall to**. The controller you
   pick *is* the recall floor.

CSM's own fire alarm linker works for the second step too.

Sneak-right-click the air to forget the held panel.

| Message | Meaning |
| --- | --- |
| *Sneak and right-click a fire alarm panel first* | Step 1 was skipped |
| *That controller is not part of an elevator yet* | The controller is alone in its column — add another floor |
| *Elevator paired to the fire alarm panel at …* | Done |
