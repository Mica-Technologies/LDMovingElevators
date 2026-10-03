"""
The regression site: two elevators side by side in open sky, built from nothing on every run.

Built in the sky so the terrain underneath never matters, and cleared and rebuilt each run so a
run can never inherit state from the one before it. Everything is placed with block metadata and
tile-entity NBT rather than by a player, which means doors are left to find their own elevator --
that adoption is one of the things under test.

    north (-z) is the landing side, south (+z) the controllers

        z=186..195  landing platform (y = floor - 1), panels on the wall's north face at z=195
        z=196       wall, with a doorway for each car
        z=197..199  the two shafts: car A at x=-201..-199, car B at x=-197..-195
        z=200       controllers, facing north into their shafts
        z=201..203  service ledge behind the controllers
"""

FLOORS = [150, 160, 170, 180]
BOTTOM, TOP = FLOORS[0], FLOORS[-1]

# Site bounds, cleared before every build and after every run.
BOX_MIN = (-210, BOTTOM - 4, 184)
BOX_MAX = (-186, TOP + 6, 206)

# Facing values. Controllers and wall panels store EnumFacing differently in their metadata.
NORTH_INDEX = 2           # EnumFacing.getIndex(), used by controllers and remote displays
NORTH_HORIZONTAL = 2      # EnumFacing.getHorizontalIndex(), used by doors and wall panels


class Car:
    def __init__(self, name, cx):
        self.name = name
        self.cx, self.cz = cx, 200
        self.cabin_x = (cx - 1, cx + 1)
        self.cabin_z = (197, 199)
        # The doorway in the cabin's mouth: origin (west) cell first.
        self.door_x = (cx - 1, cx)
        self.door_z = 196

    def controller(self, floor):
        return (self.cx, floor, self.cz)

    def binding(self, floor):
        return {"controllerX": self.cx, "controllerY": floor, "controllerZ": self.cz, "controllerFacing": NORTH_HORIZONTAL}


    def cabin_floor_center(self, floor):
        return (self.cx, floor - 1, 198)

    def stand_pos(self):
        """Where a rider stands: the middle of the cabin, as block-centre coordinates."""
        return (self.cx + 0.5, None, 198.5)

    def door_cells(self, floor):
        return [(x, y, self.door_z) for x in self.door_x for y in (floor, floor + 1)]

    def call_panel(self, floor):
        return (self.cx - 2 if self.name == "A" else self.cx + 2, floor, 195)


CAR_A = Car("A", -200)
CAR_B = Car("B", -196)
CARS = (CAR_A, CAR_B)

# Fixtures on the bottom landing, bound to car A unless noted.
COMPARATOR = (CAR_A.call_panel(BOTTOM)[0], BOTTOM, 194)
REMOTE_INDICATOR = (-202, BOTTOM + 1, 195)
REMOTE_DISPLAY = (-204, BOTTOM + 1, 195)
LOBBY_PANEL = (-198, BOTTOM + 1, 195)          # both cars
BANK_INDICATOR = (-198, BOTTOM + 2, 195)       # both cars
DISPLAY = (CAR_A.cx, BOTTOM + 1, CAR_A.cz)     # upstream display, above car A's bottom controller
CAR_PANEL = (CAR_A.cx - 1, BOTTOM + 1, 198)    # inside car A, on a pillar at its south-west corner
CAR_PILLAR = [(CAR_A.cx - 1, BOTTOM, 199), (CAR_A.cx - 1, BOTTOM + 1, 199)]
FIRE_PANEL = (-205, BOTTOM, 188)


def data(fields):
    """Core Lib block entities keep their own fields under "data", so NBT merges have to as well."""
    return {"data": fields} if isinstance(fields, dict) else "{data:%s}" % fields


def block_pos_long(x, y, z):
    """BlockPos.toLong, as a signed 64-bit value."""
    value = ((x & 0x3FFFFFF) << 38) | ((y & 0xFFF) << 26) | (z & 0x3FFFFFF)
    return value - (1 << 64) if value >= (1 << 63) else value


def clear(game):
    game.fill("minecraft:air", BOX_MIN, BOX_MAX)


def build(game):
    clear(game)
    wall = "minecraft:stonebrick"
    for floor in FLOORS:
        game.fill("minecraft:quartz_block", (-206, floor - 1, 186), (-190, floor - 1, 196))
        game.fill(wall, (-206, floor, 196), (-190, floor + 3, 196))
        game.fill("minecraft:quartz_block", (-203, floor - 1, 201), (-193, floor - 1, 203))
        for car in CARS:
            game.fill("minecraft:air", (car.door_x[0], floor, 196), (car.door_x[1], floor + 1, 196))

    # Cabins at the bottom landing: a floor each, and car A also carries a pillar with its panel.
    for car in CARS:
        game.fill("minecraft:iron_block", (car.cabin_x[0], BOTTOM - 1, car.cabin_z[0]),
                  (car.cabin_x[1], BOTTOM - 1, car.cabin_z[1]))
    game.place([{"x": p[0], "y": p[1], "z": p[2], "block": "minecraft:stonebrick"} for p in CAR_PILLAR])

    # Controllers last among the structure: each registers its elevator on its first tick and scans
    # for the cabin then.
    game.place([{"x": car.cx, "y": floor, "z": car.cz, "block": "movingelevators:elevator_block",
                 "metadata": NORTH_INDEX} for car in CARS for floor in FLOORS])
    game.wait_ticks(10)

    blocks = []
    for car in CARS:
        for floor in FLOORS:
            for i, x in enumerate(car.door_x):
                for top in (0, 1):
                    blocks.append({"x": x, "y": floor + top, "z": car.door_z,
                                   "block": "movingelevators:elevator_door_block",
                                   "metadata": NORTH_HORIZONTAL | (4 if i else 0),
                                   "nbt": data("{top:%db}" % top)})
            px, py, pz = car.call_panel(floor)
            blocks.append({"x": px, "y": py, "z": pz, "block": "movingelevators:remote_call_panel_block",
                           "metadata": NORTH_HORIZONTAL, "nbt": data(car.binding(floor))})
    both = "[{x:%d,y:%d,z:200,facing:2},{x:%d,y:%d,z:200,facing:2}]" % (CAR_A.cx, BOTTOM, CAR_B.cx, BOTTOM)
    blocks += [
        {"x": REMOTE_INDICATOR[0], "y": REMOTE_INDICATOR[1], "z": REMOTE_INDICATOR[2],
         "block": "movingelevators:remote_indicator_block", "metadata": NORTH_HORIZONTAL, "nbt": data(CAR_A.binding(BOTTOM))},
        {"x": REMOTE_DISPLAY[0], "y": REMOTE_DISPLAY[1], "z": REMOTE_DISPLAY[2],
         "block": "movingelevators:remote_display_block",
         "nbt": data(dict(CAR_A.binding(BOTTOM), facing=NORTH_INDEX))},
        {"x": LOBBY_PANEL[0], "y": LOBBY_PANEL[1], "z": LOBBY_PANEL[2],
         "block": "movingelevators:bank_lobby_panel_block", "metadata": NORTH_HORIZONTAL, "nbt": data("{bindings:%s}" % both)},
        {"x": BANK_INDICATOR[0], "y": BANK_INDICATOR[1], "z": BANK_INDICATOR[2],
         "block": "movingelevators:bank_indicator_block", "metadata": NORTH_HORIZONTAL, "nbt": data("{bindings:%s}" % both)},
        {"x": DISPLAY[0], "y": DISPLAY[1], "z": DISPLAY[2], "block": "movingelevators:display_block"},
        {"x": CAR_PANEL[0], "y": CAR_PANEL[1], "z": CAR_PANEL[2],
         "block": "movingelevators:elevator_car_panel_block", "metadata": NORTH_HORIZONTAL, "nbt": data(CAR_A.binding(BOTTOM))},
        {"x": FIRE_PANEL[0], "y": FIRE_PANEL[1], "z": FIRE_PANEL[2], "block": "csm:firealarmcontrolpanel", "metadata": 2},
    ]
    game.place(blocks)
    # A comparator reading the bottom call panel through its back. Its input faces south.
    game.place([{"x": COMPARATOR[0], "y": COMPARATOR[1], "z": COMPARATOR[2],
                 "block": "minecraft:unpowered_comparator", "metadata": 0}])
