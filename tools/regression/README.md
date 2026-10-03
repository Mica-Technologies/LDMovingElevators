# Regression suite

Drives the dev dedicated server and a client joined to it through their MCMCP endpoints, builds a
test site in the sky above the server's world, runs scripted scenarios against it, and records
what happened. Two runs are compared with `compare.py`.

It exists because the bugs this fork chases -- doors on the wrong car, riders falling through the
floor, a bouncing camera at a stop -- only show on a real server with a real client attached, and
"it looked fine when I tried it" is how two of them survived their first fixes.

## Running it

```bash
python launch.py start                 # runServer + runClient from this checkout, detached
python run.py <label> [scenario ...]   # every scenario, or only those named
python compare.py <before> <after>     # outcome changes, metrics, screenshot diffs
python launch.py stop
```

Build first (`./gradlew build`), and never build while `launch.py start` has a game running --
see the `mod-verify` skill for why. `launch.py` reads `JAVA_HOME`, defaulting to the Windows JDK
named in `CLAUDE.md`. Both endpoints' tokens and ports are read from `run/<side>/config/mcmcp.cfg`;
nothing secret lives here.

Results land in `results/<label>/` (gitignored): `results.json` plus the screenshots. `compare.py`
writes a `diff_*.png` beside each of the later run's screenshots, changed pixels in red.

## What it covers

| Scenario | Asserts |
|---|---|
| `adoption` | every doorway binds to the car whose mouth it stands in, not the one next door |
| `idle` | both cabins parked, every door shut |
| `hall_call_and_doors` | a call fetches the car, its doors open on arrival and close after the hold, nothing else moves; the call panel's comparator lights while a call is pending |
| `obstruction` | a door with someone standing in it stays open, then closes once they leave |
| `redstone` | power opens a doorway with no cabin, by neighbour update and by a direct block write |
| `rides` | a survival rider goes up and down floor by floor: arrives, stays on the cabin floor, unhurt, settles without bouncing, doors open |
| `fire_recall_blocked` | a recalled car does not move while its recall floor is blocked, and leaves within 1.5 s once cleared |
| `screenshots` | fixed views of every renderer, for diffing |
| `performance` | a 1,140-door district: server door ticking, tick time, client frame work (medians of three) |

## Reading a comparison

Measured on one build against itself, so a difference smaller than this is noise:

- screenshots: up to about 3% of pixels in views with sky in them (clouds drift); 0-1% otherwise
- server door ticking and tick time: about 5%
- client frame work: about 20% -- the client window is in the background and shares the machine,
  so treat client numbers as a direction, not a measurement

## Things it learned the hard way

- Clear the player's inventory before anything that clicks. A remote controller in hand binds
  instead of pressing the button it is pointed at, and the car simply never leaves.
- Never teleport a survival player onto a cabin without checking the cabin is there.
- Take screenshots standing, not flying: flight eases the field of view and every frame shifts.
