"""
Minimal MCMCP client: talks MCP (JSON-RPC over streamable HTTP) straight to a game's endpoint.

The token and port come from the game's own run/<side>/config/mcmcp.cfg, so nothing secret is
stored here. Each Endpoint keeps its MCP session for the life of the object.
"""
import json
import re
import time
import urllib.error
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]


def _read_cfg(side):
    text = (REPO / "run" / side / "config" / "mcmcp.cfg").read_text(encoding="utf-8")
    token = re.search(r"S:authToken=(\S+)", text).group(1)
    port = int(re.search(r"I:%sPort=(\d+)" % side, text).group(1))
    return token, port


class McpError(RuntimeError):
    pass


class Endpoint:
    def __init__(self, side, timeout=120):
        self.side = side
        self.token, self.port = _read_cfg(side)
        self.url = "http://127.0.0.1:%d/mcp" % self.port
        self.timeout = timeout
        self.session = None
        self._id = 0

    def _post(self, payload):
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
            "Authorization": "Bearer " + self.token,
            "Origin": "http://127.0.0.1",
        }
        if self.session:
            headers["Mcp-Session-Id"] = self.session
        req = urllib.request.Request(self.url, json.dumps(payload).encode(), headers)
        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
            sid = resp.headers.get("Mcp-Session-Id")
            if sid:
                self.session = sid
            body = resp.read().decode("utf-8")
        if not body.strip():
            return None
        if body.lstrip().startswith("{"):
            return json.loads(body)
        # Server-sent events: take the last data line carrying a JSON-RPC message.
        last = None
        for line in body.splitlines():
            if line.startswith("data:"):
                last = json.loads(line[5:].strip())
        return last

    def connect(self, wait_seconds=300):
        deadline = time.time() + wait_seconds
        while True:
            try:
                self.session = None
                self._post({"jsonrpc": "2.0", "id": 0, "method": "initialize", "params": {
                    "protocolVersion": "2025-06-18", "capabilities": {},
                    "clientInfo": {"name": "movingelevators-regression", "version": "1"}}})
                self._post({"jsonrpc": "2.0", "method": "notifications/initialized"})
                return self
            except (urllib.error.URLError, ConnectionError, OSError):
                if time.time() > deadline:
                    raise
                time.sleep(2)

    def call(self, tool, **args):
        self._id += 1
        reply = self._post({"jsonrpc": "2.0", "id": self._id, "method": "tools/call",
                            "params": {"name": tool, "arguments": args}})
        if reply is None:
            raise McpError("%s: empty reply" % tool)
        if "error" in reply:
            raise McpError("%s: %s" % (tool, reply["error"]))
        result = reply["result"]
        texts = [c.get("text", "") for c in result.get("content", []) if c.get("type") == "text"]
        joined = "\n".join(texts)
        if result.get("isError"):
            raise McpError("%s: %s" % (tool, joined))
        if isinstance(result.get("structuredContent"), dict):
            return result["structuredContent"]
        for t in texts:
            try:
                return json.loads(t)
            except ValueError:
                continue
        return joined

    def tools(self):
        self._id += 1
        reply = self._post({"jsonrpc": "2.0", "id": self._id, "method": "tools/list", "params": {}})
        return reply["result"]["tools"]
