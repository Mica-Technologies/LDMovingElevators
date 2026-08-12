# Comparators

Each block reports the thing it already shows, so **a comparator says what the block says**.

| Block | Signal |
| --- | --- |
| Elevator Controller, Remote Elevator Panel | `15` while a cabin is at that floor, `0` otherwise |
| Remote Elevator Indicator, Remote Elevator Display | The floor the cabin is on: `1` for the lowest floor, `0` when it cannot be said |
| Remote Elevator Call Panel | The calls waiting at its landing: `0` none, `7` down, `15` up, `11` both |

The floor readouts **follow the cabin as it travels** rather than only updating when it stops.

!!! note "The controller's signal is unchanged from upstream"

    `15` at the floor, `0` otherwise — so redstone built against the official mod keeps working.

## Reading a floor number

A comparator counts no higher than 15, so in a building taller than fifteen floors everything above
that reads `15`. Use the call panel or an indicator for tall shafts, or read the floor at a landing
you care about rather than for the whole building.

Floor `1` is the lowest floor, not the floor named "1".

## Redstone input

- **Elevator Controller** — a redstone signal requests the cabin to that floor.
- **Elevator Doors** — redstone power forces them open and holds them open. Treat it as an emergency
  override; the elevator already opens them on arrival.
- **Remote Elevator Display, Indicator** — take no redstone input. They are readouts only.

## Examples

**Light a lamp when the car is at this floor**

Comparator off the Elevator Controller into a lamp. `15` while the cabin is there.

**Ring something when a call is waiting**

Comparator off a Remote Elevator Call Panel. Any non-zero output means at least one call is
outstanding at that landing.

**Distinguish up from down calls**

The call panel's four values are distinct, so a pair of comparators with different thresholds
separates them: `7` is down only, `15` is up only, `11` is both.
