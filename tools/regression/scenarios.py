"""
The regression scenarios. Each takes the Game and returns a list of Checks; anything measured
rather than asserted goes into game.metrics.

Timings are generous where they guard liveness ("the car arrived at all") and tight where the
tightness is the point ("the door opened within a second of the cabin arriving"), so a slow
machine does not fail the suite but a real regression does.
"""
import time

import testsite as S
from harness import Check

DOOR_HOLD_SECONDS = 240 / 20      # doorAutoCloseTicks in the dev server's config
TRAVEL_TIMEOUT = 30


# --- small readers --------------------------------------------------------------------------------

def cabin_at(game, car, floor):
    return game.block_id(*car.cabin_floor_center(floor)) == "minecraft:iron_block"


def door_open(game, car, floor):
    """Open only if every block of the doorway says so; None if they disagree."""
    states = [game.te(*cell).get("open") for cell in car.door_cells(floor)]
    if all(states):
        return True
    if not any(states):
        return False
    return None


def door_bindings(game, car, floor):
    return [(n.get("controllerX"), n.get("controllerY"))
            for n in (game.te(*cell) for cell in car.door_cells(floor))]


def wait_arrival(game, car, floor, timeout=TRAVEL_TIMEOUT):
    ok, secs = game.poll(lambda: cabin_at(game, car, floor), timeout)
    return ok, secs


def wait_departure(game, car, floor, timeout):
    ok, secs = game.poll(lambda: not cabin_at(game, car, floor), timeout, interval=0.1)
    return ok, secs


def press_call(game, car, floor, up):
    """Stand on the landing in front of the car's call panel and press up or down on it."""
    px, py, pz = car.call_panel(floor)
    game.teleport(px + 0.5, floor, 193.5, fly=False)
    game.wait_ticks(5)
    game.look_at(px + 0.5, py + (0.45 if up else 0.2), 195.876)
    game.use()


def press_controller(game, car, floor, where):
    """From inside the cabin, press the controller's top ('up'), middle ('call') or bottom ('down')."""
    height = {"up": 0.85, "call": 0.5, "down": 0.15}[where]
    game.look_at(car.cx + 0.5, floor + height, car.cz)
    game.use()


def send_car(game, car, floor, where, attempts=3):
    """Presses until the car leaves, so a click the client dropped is not mistaken for a fault in
    the elevator. Returns how many presses it took, or 0 if the car never left."""
    for attempt in range(1, attempts + 1):
        press_controller(game, car, floor, where)
        left, _ = wait_departure(game, car, floor, 1.5)
        if left:
            return attempt
    return 0


# --- scenarios ------------------------------------------------------------------------------------

def adoption(game):
    checks = []
    for car in S.CARS:
        for floor in S.FLOORS:
            got = door_bindings(game, car, floor)
            ok = all(b == (car.cx, floor) for b in got)
            checks.append(Check("adoption.%s@%d" % (car.name, floor), ok, str(got)))
    return checks


def idle(game):
    checks = [Check("idle.cabin.%s@%d" % (car.name, S.BOTTOM), cabin_at(game, car, S.BOTTOM)) for car in S.CARS]
    for car in S.CARS:
        for floor in S.FLOORS:
            checks.append(Check("idle.doors_shut.%s@%d" % (car.name, floor), door_open(game, car, floor) is False))
    return checks


def hall_call_and_doors(game):
    checks = []
    a = S.CAR_A
    press_call(game, a, 170, up=True)
    ok, secs = wait_arrival(game, a, 170)
    checks.append(Check("call.A_arrives_170", ok, "%.1fs" % secs))
    game.metrics["call.A_150_to_170_seconds"] = round(secs, 2)
    opened, secs = game.poll(lambda: door_open(game, a, 170), 3)
    checks.append(Check("call.door_A@170_opens_on_arrival", opened is True, "%.1fs" % secs))
    checks.append(Check("call.door_A@150_stays_shut", door_open(game, a, 150) is False))
    checks.append(Check("call.door_B@170_stays_shut", door_open(game, S.CAR_B, 170) is False))
    checks.append(Check("call.B_did_not_move", cabin_at(game, S.CAR_B, S.BOTTOM)))
    closed, secs = game.poll(lambda: door_open(game, a, 170) is False, DOOR_HOLD_SECONDS + 4)
    checks.append(Check("call.door_A@170_auto_closes", closed, "%.1fs" % secs))
    game.metrics["call.door_hold_seconds"] = round(secs, 2)

    # A hall call at the bottom while the car is away: the call panel's comparator lights while the
    # call is pending and goes out once the car has answered it.
    press_call(game, a, S.BOTTOM, up=True)
    lit, secs = game.poll(lambda: game.block(*S.COMPARATOR)["state"].get("powered") == "true", 3, 0.1)
    checks.append(Check("comparator.lights_on_hall_call", lit, "%.1fs" % secs))
    ok, _ = wait_arrival(game, a, S.BOTTOM)
    checks.append(Check("comparator.A_answers_call", ok))
    out, secs = game.poll(lambda: game.block(*S.COMPARATOR)["state"].get("powered") == "false", 3, 0.1)
    checks.append(Check("comparator.goes_out_when_served", out, "%.1fs" % secs))
    return checks


def obstruction(game):
    """The car is at the bottom with its doors open from the last call: stand in the doorway."""
    checks = []
    a = S.CAR_A
    opened, _ = game.poll(lambda: door_open(game, a, S.BOTTOM), 3)
    checks.append(Check("obstruct.precondition_door_open", opened is True))
    game.teleport(a.door_x[1] + 0.5, S.BOTTOM, a.door_z + 0.5, fly=False)
    time.sleep(DOOR_HOLD_SECONDS + 3)
    checks.append(Check("obstruct.held_open_while_standing_in_it", door_open(game, a, S.BOTTOM) is True))
    game.teleport(a.door_x[1] + 0.5, S.BOTTOM, 190.5, fly=False)
    closed, secs = game.poll(lambda: door_open(game, a, S.BOTTOM) is False, 4)
    checks.append(Check("obstruct.closes_once_clear", closed, "%.1fs" % secs))
    return checks


def redstone(game):
    """Car B's doors at 170 have no cabin, so only redstone can open them."""
    checks = []
    b = S.CAR_B
    torch_spot = (b.door_x[0], 171, 195)
    game.cmd("setblock %d %d %d minecraft:redstone_block" % torch_spot)
    opened, secs = game.poll(lambda: door_open(game, b, 170), 3, 0.1)
    checks.append(Check("redstone.opens_doorway_with_no_cabin", opened is True, "%.1fs" % secs))
    game.metrics["redstone.open_seconds"] = round(secs, 2)
    game.cmd("setblock %d %d %d minecraft:air" % torch_spot)
    closed, secs = game.poll(lambda: door_open(game, b, 170) is False, 3, 0.1)
    checks.append(Check("redstone.closes_when_removed", closed, "%.1fs" % secs))

    # Power arriving without a neighbour update: the direct block write MCMCP does. Picked up by the
    # periodic safety re-read rather than by neighborChanged, so it is allowed up to a few seconds.
    game.place([{"x": torch_spot[0], "y": torch_spot[1], "z": torch_spot[2], "block": "minecraft:redstone_block"}])
    opened, secs = game.poll(lambda: door_open(game, b, 170), 5, 0.1)
    checks.append(Check("redstone.direct_write_eventually_opens", opened is True, "%.1fs" % secs))
    game.place([{"x": torch_spot[0], "y": torch_spot[1], "z": torch_spot[2], "block": "minecraft:air"}])
    closed, secs = game.poll(lambda: door_open(game, b, 170) is False, 5, 0.1)
    checks.append(Check("redstone.direct_removal_eventually_closes", closed, "%.1fs" % secs))
    return checks


def rides(game):
    """A survival-mode rider in car A: up floor by floor to the top, then back down."""
    checks = []
    a = S.CAR_A
    x, _, z = a.stand_pos()
    if not cabin_at(game, a, S.BOTTOM):
        # Teleporting a survival player onto a cabin that is not there drops them down the shaft.
        return checks + [Check("ride.precondition_cabin_at_bottom", False)]
    game.cmd("clear " + game.player)
    game.cmd("gamemode 0 " + game.player)
    game.cmd("effect %s clear" % game.player)
    game.teleport(x, S.BOTTOM, z, yaw=0, pitch=0, fly=False)
    game.wait_ticks(20)
    route = [(S.FLOORS[i], S.FLOORS[i + 1], "up") for i in range(len(S.FLOORS) - 1)]
    route += [(S.FLOORS[i], S.FLOORS[i - 1], "down") for i in range(len(S.FLOORS) - 1, 0, -1)]
    for start, end, direction in route:
        label = "ride.%s_%d_to_%d" % (direction, start, end)
        presses = send_car(game, a, start, direction)
        departed = presses > 0
        game.metrics[label + ".presses"] = presses
        arrived, secs = wait_arrival(game, a, end)
        game.wait_ticks(10)
        st = game.player_state()
        pos = st["position"]
        in_cabin = (a.cabin_x[0] <= pos["x"] < a.cabin_x[1] + 1) and (a.cabin_z[0] <= pos["z"] < a.cabin_z[1] + 1)
        on_floor = abs(pos["y"] - end) < 0.2
        checks.append(Check(label + ".departed", departed))
        checks.append(Check(label + ".arrived", arrived, "%.1fs" % secs))
        checks.append(Check(label + ".rider_on_cabin_floor", in_cabin and on_floor,
                            "pos=(%.2f, %.2f, %.2f)" % (pos["x"], pos["y"], pos["z"])))
        checks.append(Check(label + ".rider_unhurt", st["health"] >= st["maxHealth"], "health=%s" % st["health"]))
        # Standing still on the floor once stopped: no bobbing through it and back. The client is
        # sampled because it is the client that bounced; the server kept correcting it.
        heights, grounded = set(), True
        t0 = time.time()
        while time.time() - t0 < 2:
            cs = game.client.call("client_player_state")
            heights.add(round(cs["position"]["y"], 4))
            grounded = grounded and bool(cs.get("onGround"))
        checks.append(Check(label + ".rider_settled_on_floor", heights == {float(end)} and grounded,
                            "heights=%s onGround=%s" % (sorted(heights)[:4], grounded)))
        game.metrics[label + ".seconds"] = round(secs, 2)
        opened, _ = game.poll(lambda: door_open(game, a, end), 3)
        checks.append(Check(label + ".doors_open_with_rider_inside", opened is True))
        if not (arrived and in_cabin and on_floor):
            # The rest of the route means nothing without a rider, and a rider who has fallen out
            # into the world below may drown while the suite waits on a car that never comes.
            checks.append(Check("ride.route_completed", False, "stopped after " + label))
            break
    game.cmd("gamemode 1 " + game.player)
    game.respawn_if_dead()
    game.cmd("effect %s instant_health 1 10" % game.player)
    return checks


def fire_recall_blocked(game):
    """Car A is paired to the fire panel with its top floor as the recall floor, and that floor is
    blocked. It must not move while blocked, and must go as soon as the block is cleared."""
    checks = []
    a = S.CAR_A
    panel = S.FIRE_PANEL
    # Pair: hold a linker already pointing at the panel and right-click the top controller from the
    # service ledge behind it.
    game.cmd("clear " + game.player)
    game.cmd("give %s movingelevators:alarm_linker 1 0 {alarmPanelPos:%dL}" % (game.player, S.block_pos_long(*panel)))
    game.cmd("gamemode 1 " + game.player)
    game.teleport(a.cx + 0.5, S.TOP, 202.5, fly=False)
    game.wait_ticks(10)
    game.client.call("client_select_slot", slot=0)
    game.look_at(a.cx + 0.5, S.TOP + 0.5, a.cz + 1)
    game.use()
    game.wait_ticks(10)

    blocker = (a.cx, S.TOP, 198)
    game.cmd("setblock %d %d %d minecraft:bedrock" % blocker)
    game.cmd("blockdata %d %d %d {a:1b}" % panel)
    time.sleep(5)
    checks.append(Check("recall.blocked_car_stays_put", cabin_at(game, a, S.BOTTOM)))
    checks.append(Check("recall.car_B_unaffected", cabin_at(game, S.CAR_B, S.BOTTOM)))

    tick = game.server.call("server_tick_stats")["last5s"]
    game.metrics["recall.blocked_tick_mean_ms"] = tick["meanMs"]
    game.metrics["recall.blocked_tick_max_ms"] = tick["maxMs"]

    game.cmd("setblock %d %d %d minecraft:air" % blocker)
    departed, secs = wait_departure(game, a, S.BOTTOM, 5)
    checks.append(Check("recall.departs_once_cleared", departed, "%.1fs" % secs))
    checks.append(Check("recall.departs_within_1.5s", departed and secs <= 1.5, "%.1fs" % secs))
    game.metrics["recall.departure_latency_seconds"] = round(secs, 2)
    arrived, _ = wait_arrival(game, a, S.TOP, 40)
    checks.append(Check("recall.arrives_at_recall_floor", arrived))
    opened, _ = game.poll(lambda: door_open(game, a, S.TOP), 5)
    checks.append(Check("recall.doors_open_at_recall_floor", opened is True))

    # End the alarm by removing the panel, which takes it out of CSM's registry.
    game.cmd("setblock %d %d %d minecraft:air" % panel)
    game.cmd("clear " + game.player)
    return checks


def screenshots(game):
    """Fixed views of every renderer, taken on a freshly built site so they can be diffed."""
    S.build(game)
    game.wait_ticks(60)
    b = S.CAR_B
    game.cmd("setblock %d %d %d minecraft:redstone_block" % (b.door_x[0], 161, 195))
    # Standing, not flying: flight eases the field of view in and out, which shifts every frame by
    # a pixel or two between runs and drowns any real difference in edge noise.
    game.teleport(-198.5, S.BOTTOM, 189.5, yaw=0, pitch=0, fly=False)
    game.wait_rendered()
    game.wait_ticks(60)
    views = {
        "landing_150": ((-198, 152.5, 189), (-199, 151, 196)),
        "doors_160": ((-198.5, 161.5, 191), (-198.5, 160.8, 196)),
        "lobby_and_bank_indicator": ((-197.5, 151.8, 193.2), (-197.5, 151.8, 196)),
        "remote_display_and_indicator": ((-203, 151.5, 192.5), (-203, 151.3, 196)),
        "inside_car_A": ((-199.5, 151.4, 197.3), (-199.8, 150.7, 200)),
        "car_panel": ((-199.6, 151.5, 197.4), (-200.5, 151.4, 198.6)),
    }
    for name, (eye, target) in views.items():
        game.screenshot(name, eye, target, fov=70)
    game.cmd("setblock %d %d %d minecraft:air" % (b.door_x[0], 161, 195))
    return []


def performance(game):
    """A district of 1,160 door blocks -- 290 real two-by-two doorways, half held open by redstone --
    measured on both sides."""
    # Doorways three blocks apart, so a doorway's walk never strays into its neighbour, and each
    # with its halves and leaves flagged as a placed doorway's are.
    blocks = []
    for z in range(160, 189):
        for k in range(10):
            x = -262 + 3 * k
            for right in (0, 1):
                for top in (0, 1):
                    blocks.append({"x": x + right, "y": S.BOTTOM + top, "z": z,
                                   "block": "movingelevators:elevator_door_block",
                                   "metadata": S.NORTH_HORIZONTAL | (4 if right else 0),
                                   "nbt": S.data("{top:%db}" % top)})
    game.place(blocks)
    a, b = (-262, S.BOTTOM, 160), (-233, S.BOTTOM + 1, 188)
    game.fill("minecraft:redstone_block", (-262, S.BOTTOM + 2, 160), (-248, S.BOTTOM + 2, 188), replace_only="minecraft:air")
    game.teleport(-247.5, S.BOTTOM + 8, 148.5, yaw=0, pitch=25, fly=True)
    game.wait_rendered()
    game.wait_ticks(400)
    m = game.metrics
    # Medians of three, because a single short sample of either side moves by a third between two
    # runs of the same build -- more than most of the changes this is meant to measure.
    work, p99, door, tick_mean = [], [], [], []
    for _ in range(3):
        frames = game.client.call("client_frame_stats", sample_seconds=8)["sampled"]
        work.append(frames["renderWork"]["meanMs"])
        p99.append(frames["renderWork"]["p99Ms"])
        ticking = game.server.call("server_profile_ticking", duration_seconds=8, top=3)
        doors = [t for t in ticking["tileEntities"]["byType"] if t["block"] == "movingelevators:elevator_door_block"]
        door.append(doors[0]["totalMicros"] if doors else 0)
        tick_mean.append(game.server.call("server_tick_stats")["last5s"]["meanMs"])
    median = lambda xs: sorted(xs)[len(xs) // 2]
    m["perf.client.renderWork_mean_ms"] = median(work)
    m["perf.client.renderWork_p99_ms"] = median(p99)
    m["perf.server.door_micros_per_tick"] = median(door)
    m["perf.server.tick_mean_ms"] = median(tick_mean)
    sections = game.client.call("client_profile_sections", duration_seconds=8, min_percent=1, max_depth=5)
    m["perf.client.sections"] = sections if isinstance(sections, str) else str(sections)
    game.fill("minecraft:air", (-262, S.BOTTOM + 2, 160), (-248, S.BOTTOM + 2, 188), replace_only="minecraft:redstone_block")
    game.fill("minecraft:air", a, b, replace_only="movingelevators:elevator_door_block")
    return []





def panel_performance(game):
    """A wall of 80 landing and car panels bound to car A, within all of their draw distances, for the
    client's cost of drawing panels."""
    wall_z, x0, x1, y0 = 232, -240, -221, S.BOTTOM
    game.fill("minecraft:stonebrick", (x0, y0, wall_z), (x1, y0 + 3, wall_z))
    both = "[{x:%d,y:%d,z:200,facing:2},{x:%d,y:%d,z:200,facing:2}]" % (S.CAR_A.cx, S.BOTTOM, S.CAR_B.cx, S.BOTTOM)
    kinds = [("movingelevators:remote_call_panel_block", S.data(S.CAR_A.binding(S.BOTTOM))),
             ("movingelevators:remote_indicator_block", S.data(S.CAR_A.binding(S.BOTTOM))),
             ("movingelevators:bank_indicator_block", S.data("{bindings:%s}" % both)),
             ("movingelevators:bank_lobby_panel_block", S.data("{bindings:%s}" % both)),
             ("movingelevators:elevator_car_panel_block", S.data(S.CAR_A.binding(S.BOTTOM)))]
    blocks = []
    i = 0
    for y in range(y0, y0 + 4):
        for x in range(x0, x1 + 1):
            block, nbt = kinds[i % len(kinds)]
            blocks.append({"x": x, "y": y, "z": wall_z - 1, "block": block, "metadata": S.NORTH_HORIZONTAL, "nbt": nbt})
            i += 1
    game.place(blocks)
    game.teleport((x0 + x1) / 2 + 0.5, y0, wall_z - 9.5, yaw=0, pitch=-5, fly=True)
    game.wait_rendered()
    game.wait_ticks(200)
    work, sections = [], []
    for _ in range(3):
        work.append(game.client.call("client_frame_stats", sample_seconds=6)["sampled"]["renderWork"]["meanMs"])
        sections.append(game.client.call("client_profile_sections", duration_seconds=6, min_percent=1, max_depth=5))
    import re
    be = sorted(float(m.group(1)) for t in sections
                for m in [re.search(r"^\s*blockentities [0-9.]+% ([0-9.]+)ms", t if isinstance(t, str) else str(t), re.M)] if m)
    game.metrics["panels.client.renderWork_mean_ms"] = sorted(work)[1]
    game.metrics["panels.client.blockentities_ms"] = be[len(be) // 2] if be else None
    game.screenshot("panel_wall", ((x0 + x1) / 2 + 0.5, y0 + 2, wall_z - 6), ((x0 + x1) / 2 + 0.5, y0 + 1.5, wall_z))
    game.fill("minecraft:air", (x0, y0, wall_z - 1), (x1, y0 + 3, wall_z))
    return []


ORDER = [adoption, idle, hall_call_and_doors, obstruction, redstone, rides, fire_recall_blocked,
         screenshots, performance, panel_performance]
