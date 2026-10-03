"""
Shared plumbing for the regression run: the two endpoints, the player, polling and screenshots.
"""
import shutil
import time
from pathlib import Path

from mcmcp import Endpoint, McpError, REPO


class Check:
    """One assertion's outcome. Kept as data so two runs can be compared line by line."""

    def __init__(self, name, ok, detail=""):
        self.name, self.ok, self.detail = name, bool(ok), detail

    def as_dict(self):
        return {"name": self.name, "ok": self.ok, "detail": self.detail}


class Game:
    def __init__(self, out_dir):
        self.server = Endpoint("server").connect()
        self.client = Endpoint("client").connect()
        self.out_dir = Path(out_dir)
        (self.out_dir / "screenshots").mkdir(parents=True, exist_ok=True)
        self.player = None

    # --- connection ----------------------------------------------------------------------------

    def join(self):
        info = self.client.call("client_connection_info")
        if not info.get("inWorld"):
            self.client.call("client_gui_click", label="Multiplayer")
            self.client.call("client_gui_click", label="Direct Connect")
            self.client.call("client_gui_text", text="localhost", submit=True)
            self.client.call("client_wait", waitFor="worldLoaded", ticks=1200)
        # The client window is never focused during a run; without this a lost focus pauses it.
        self.client.call("client_view", pauseOnLostFocus=False)
        self.respawn_if_dead()
        players = self.server.call("server_list_players")["players"]
        if not players:
            raise RuntimeError("client joined but the server lists no players")
        self.player = players[0]["name"]
        return self.player

    def respawn_if_dead(self):
        """A failed ride can kill the player, and every later scenario would then run on a corpse."""
        if self.client.call("client_gui_state").get("screenName") == "GuiGameOver":
            # The respawn button only enables a second after the screen opens.
            self.wait_ticks(30)
            self.client.call("client_gui_click", label="Respawn")
            self.client.call("client_wait", waitFor="screenClosed", ticks=200)
            return True
        return False

    # --- commands and blocks -------------------------------------------------------------------

    def cmd(self, command):
        return self.server.call("server_run_command", command=command)

    def block(self, x, y, z, nbt=False):
        return self.server.call("server_get_block", x=x, y=y, z=z, nbt=nbt)

    def block_id(self, x, y, z):
        return self.block(x, y, z)["block"]

    def te(self, x, y, z):
        reply = self.block(x, y, z, nbt=True)
        be = reply.get("blockEntity") or {}
        nbt = be.get("nbt") or {}
        # Core Lib keeps a block entity's own fields under "data"; vanilla and other mods do not.
        return nbt.get("data", nbt) if isinstance(nbt.get("data"), dict) else nbt

    def fill(self, block, a, b, metadata=0, nbt=None, replace_only=None):
        args = dict(mode="fill", block=block, x=a[0], y=a[1], z=a[2], toX=b[0], toY=b[1], toZ=b[2],
                    metadata=metadata)
        if nbt is not None:
            args["nbt"] = nbt
        if replace_only:
            args["replaceOnly"] = replace_only
        return self.server.call("server_set_blocks", **args)

    def place(self, blocks):
        """blocks: list of dicts with x, y, z, block and optionally metadata and nbt."""
        return self.server.call("server_set_blocks", mode="list", blocks=blocks)

    # --- player --------------------------------------------------------------------------------

    def teleport(self, x, y, z, yaw=None, pitch=None, fly=None):
        args = dict(player=self.player, x=x, y=y, z=z)
        if yaw is not None:
            args["yaw"] = yaw
        if pitch is not None:
            args["pitch"] = pitch
        if fly is not None:
            args["fly"] = fly
        return self.server.call("server_teleport_player", **args)

    def player_state(self):
        return self.server.call("server_player_state", player=self.player)

    def look_at(self, x, y, z, attempts=5):
        """Retried, because a position correction from the server landing just after the turn
        puts the camera back where it was; the client reports that rather than hiding it."""
        for attempt in range(attempts):
            try:
                return self.client.call("client_look", lookAtX=x, lookAtY=y, lookAtZ=z)
            except McpError:
                if attempt == attempts - 1:
                    raise
                self.wait_ticks(5)

    def use(self):
        return self.client.call("client_interact", action="use", ticks=1)

    def wait_ticks(self, ticks):
        self.client.call("client_wait", ticks=ticks)

    def wait_rendered(self, x=None, z=None, radius=48):
        args = dict(waitFor="chunksRendered", radius=radius, ticks=600)
        if x is not None:
            args.update(x=int(x), z=int(z))
        return self.client.call("client_wait", **args)

    # --- polling -------------------------------------------------------------------------------

    @staticmethod
    def poll(predicate, timeout, interval=0.25):
        """Calls predicate until it returns truthy or the timeout passes; returns (value, seconds)."""
        start = time.time()
        while True:
            value = predicate()
            if value:
                return value, time.time() - start
            if time.time() - start > timeout:
                return value, time.time() - start
            time.sleep(interval)

    # --- screenshots ---------------------------------------------------------------------------

    def screenshot(self, name, eye, look_at, fov=None):
        # Full-bright: the site is rebuilt for every run, and vanilla updates sky light under a new
        # overhang lazily, so how shaded a landing looks depends on timing rather than on anything
        # the mod draws. Face shading -- what a renderer change can actually alter -- still shows.
        args = {"name": name, "from": list(eye), "look_at": list(look_at), "settle_ms": 8000, "fullbright": True}
        if fov:
            args["fov"] = fov
        reply = self.client.call("client_screenshot", **args)
        dest = self.out_dir / "screenshots" / (name + ".png")
        shutil.copyfile(reply["path"], dest)
        return dest
