# Configuration

Config lives in `config/movingelevators-common.toml`, written by SuperMartijn642's Config Lib. There
are eleven options, all under `[General]`.

It is a **common** config: in multiplayer the server's copy is what counts.

## Cabin

### `maxCabinHorizontalSize`

`integer` 1–15 · default `11`

Maximum width of an elevator cabin. Higher numbers may cause lag.

### `maxCabinVerticalSize`

`integer` 1–15 · default `11`

Maximum height of an elevator cabin. Higher numbers may cause lag.

### `maxCabinSpeed`

`integer` 1–10 · default `10`

The fastest an elevator may be set to travel, in **tenths of a block per tick** — `10` is one block
per tick. Lowering it also lowers the top of the speed slider in the elevator's screen, so existing
elevators cannot sit above the new limit.

### `allowUnbreakableBlocks`

`boolean` · default `false`

Whether the elevator may move unbreakable blocks.

!!! warning

    Turning this on can let players move bedrock and portals with an elevator. That is the whole
    reason it is off.

### `movingCabinLight`

`integer` 0–15 · default `6`

Minimum light level inside a cabin while it is moving.

A cabin is lifted out of the world as it travels, so nothing inside it lights anything and it would
otherwise go dark mid-trip. This lights **the cabin, not the shaft**. Set to `0` for the old
behaviour.

## Timing

All values are in **ticks**. 20 ticks is one second.

### `elevatorDwellTicks`

`integer` 0–1200 · default `200` (10 seconds)

How long an elevator waits at a floor before moving on to its next call — boarding time.

**Close doors** inside the cabin cuts it short. A floor a [bank lobby panel](../guide/banks.md) sent
the car to is held longer; see below.

### `bankedDwellTicks`

`integer` 0–2400 · default `300` (15 seconds)

How long an elevator holds a floor a bank lobby panel sent it to. Longer than an ordinary stop
because whoever called it is walking over rather than standing at the doors. **Close doors** cuts it
short.

### `doorAutoCloseTicks`

`integer` 20–1200 · default `240` (12 seconds)

How long [elevator doors](../guide/doors.md) stay open before closing on their own.

Independent of the dwell above — but the cabin leaving closes the door regardless, so this only
matters while the cabin is staying put.

### `emergencyHoldTicks`

`integer` 20–2400 · default `600` (30 seconds)

How long an elevator stays out of service after an [emergency stop](../guide/service.md#emergency-stop).

**A minimum, not a limit.** If somebody is still in the shaft when it expires, it waits again.

## Ranges

### `doorLinkRange`

`integer` 1–32 · default `12`

How far an elevator door looks for a landing to attach itself to when placed, in blocks.

Measured from the **edge of the cabin**, not from the controller behind it, so it means the same
thing however large the cabin is and wherever its controller is mounted. The door must also stand
within the cabin's own height at that landing, so a door well above or below the cabin floor finds
nothing regardless of this value.

### `shaftScanReach`

`integer` 0–32 · default `10`

How far above and below a moving cabin counts as being in its way, in blocks.

The cabin **always** sweeps everything it has passed through since its last check as well, so this is
its warning about what lies *ahead* rather than the whole of what it notices. Setting it to `0` does
not disable the emergency stop.

## Suggested adjustments

| Situation | Change |
| --- | --- |
| Busy public lobby | Lower `elevatorDwellTicks` so cars turn around faster |
| Survival server, worried about grief | Leave `allowUnbreakableBlocks` off; consider lowering `maxCabinHorizontalSize` |
| Lag on a big pack | Lower the two `maxCabinSize` options — cabin volume is the cost driver |
| Doors feel sticky | Lower `doorAutoCloseTicks` |
| Decorative lift, no safety wanted | `shaftScanReach` to `0` — note this only narrows the look-ahead |
