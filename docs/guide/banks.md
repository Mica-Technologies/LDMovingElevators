# Elevator banks

A **bank** is several elevators answering one lobby together. You choose your destination *before*
boarding, and the bank decides which car collects you.

!!! info "Banking is something the panel does, not a mode an elevator is in"

    A banked elevator keeps its own controllers, buttons and doors, still works alone, and breaking
    the panel leaves nothing behind. No elevator knows it is in a bank.

## Destination dispatch

There is no up/down call on a lobby panel, deliberately. Choosing a destination tells the bank the
**whole trip**, which is the only way it can send a car that is already going that way.

Dispatch weighs each car by:

- how far away it is,
- whether it is mid-trip and which way it is headed,
- how many calls it already has.

A car already coming to your landing **travelling your way wins outright**, so two people going the
same way share the trip.

## Building one

You need one [Elevator Bank Lobby Panel](../reference/blocks.md#elevator-bank-lobby-panel) per lobby
station, and two or more elevators.

1. Hold the Bank Lobby Panel item.
2. **Right-click one Elevator Controller in each shaft** you want in the bank. One per elevator is
   enough — the panel finds the rest of that shaft's floors, and its own landing, by itself. It
   counts the elevators back to you as you go.
3. Right-click a controller again to take that elevator back out.
4. Place the panel in the lobby.

The item **keeps its links after you place a panel**, so one lobby panel per floor takes no extra
work — bind once, then place one at every landing.

To empty an item and start over, **sneak-right-click with it in the air**.

### Copying a bank onto another panel

Right-click a placed, configured panel with **another panel item** and it copies the whole bank onto
the item in your hand. A lobby with stations facing three ways does not need the binding walk done
three times.

## Floors must line up

Shafts in a bank **may serve different floors** — a service lift that skips the mezzanine is fine.
What they may not do is disagree about one. Two checks are made, and either refuses the bind:

| Conflict | Example |
| --- | --- |
| Two **named** floors at the same height with different names | y=4 is "Lobby" on one shaft and "Basement" on the other |
| The **same name** at two different heights | "Lobby" is y=4 on one shaft and y=9 on the other |

!!! note "Only named floors are compared"

    Floors you have not given an explicit name are skipped entirely. A shaft whose ground floor is
    named "Lobby" links happily to one whose ground floor is still unnamed — the check has nothing
    to compare, so there is nothing to disagree about.

    In practice that means an unnamed bank always links, and naming floors is what makes the check
    useful. Name them.

The refusal names the floor and both values, so the message tells you what to fix. Sneak-click a
placed panel to see what it is linked to and whether the linked elevators agree.

## Riding a banked elevator

At the lobby panel, enter your destination floor. The panel tells you which car is coming:

- *An elevator is on its way — going to …*
- *Elevator **A** is ready to board — going to …* (if the elevator is named)

A car dispatched from a lobby panel holds the landing for **15 seconds** (`bankedDwellTicks`) rather
than the usual 10, since whoever called it is walking over rather than standing at the doors.
**Close doors** on the car panel cuts that short.

Name your elevators, in the controller screen. A bank that says "Elevator A is ready" is far easier
to use than one that says "an elevator is ready".

## Inside the car

Use the [Elevator Bank Car Panel](../reference/blocks.md#elevator-bank-car-panel) rather than the
ordinary car panel. It shows the current floor and direction, and has **open doors**, **close doors**
and **alarm** — but **no floor buttons**, because in a bank you entered your destination at the lobby
before you boarded.

## Showing the whole bank

The [Elevator Bank Indicator](../reference/blocks.md#elevator-bank-indicator) is one readout for the
whole bank instead of one per shaft: a column per elevator showing its name, the floor it is on, and
which way it is travelling.

Link it exactly like the lobby panel — right-click one controller in each shaft. The plate widens
itself to fit, from two columns up to six, overhanging its own block at the wider end. Past six the
extra elevators stay linked and working but have no column of their own.

## Emergencies are per elevator

An [emergency stop](service.md#emergency-stop) is **never per bank**. A bank shares dispatch, not
shafts, so one blocked shaft does not strand a whole building — the other cars carry on and dispatch
simply routes around it.
