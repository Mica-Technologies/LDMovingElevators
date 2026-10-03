"""
Starts or stops the dev server and client for a regression run.

    python launch.py start      # runServer + runClient, detached; returns once both answer MCMCP
    python launch.py stop       # quits the client and stops the server through MCMCP

Detached because a Gradle run bound to the calling process dies with it. JAVA_HOME defaults to the
Windows JDK named in CLAUDE.md; set it in the environment to use another.
"""
import os
import subprocess
import sys
import time
from pathlib import Path

from mcmcp import Endpoint, REPO

HERE = Path(__file__).resolve().parent
JDK = os.environ.get("JAVA_HOME", r"C:\Users\ahawk\.jdks\azul-17.0.19")


def answering(side):
    try:
        Endpoint(side, timeout=5).connect(wait_seconds=0)
        return True
    except Exception:
        return False


def start():
    # A game left over from an earlier start keeps the ports, and the build outputs open; the new
    # one would then fail to start while every call went on reaching the old build.
    running = [side for side in ("server", "client") if answering(side)]
    if running:
        sys.exit("already running: %s -- run 'launch.py stop' first" % ", ".join(running))
    env = dict(os.environ, JAVA_HOME=JDK)
    flags = subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
    gradlew = str(REPO / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    logs = HERE / "results" / "_launch"
    logs.mkdir(parents=True, exist_ok=True)
    # One after the other: two Gradle invocations starting together in one project contend for its
    # locks, and the loser exits before its game ever starts.
    for task, side in (("runServer", "server"), ("runClient", "client")):
        log = open(logs / (task + ".log"), "w")
        subprocess.Popen(["cmd", "/c", gradlew, task] if os.name == "nt" else [gradlew, task],
                         cwd=str(REPO), env=env, creationflags=flags,
                         stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        try:
            endpoint = Endpoint(side).connect(wait_seconds=600)
        except Exception:
            sys.exit("%s did not come up; see %s" % (task, logs / (task + ".log")))
    # The client endpoint answers before the main menu is up.
    endpoint.call("client_wait", waitFor="screenOpen", screenName="main", ticks=6000)
    print("server and client are up")


def stop():
    for side, tool in (("client", "client_quit"), ("server", "server_stop")):
        if not answering(side):
            continue
        try:
            Endpoint(side, timeout=30).connect(wait_seconds=5).call(tool)
        except Exception as error:
            print("%s: %s" % (side, error))
    deadline = time.time() + 90
    while any(answering(side) for side in ("server", "client")):
        if time.time() > deadline:
            sys.exit("a game is still answering after 90 seconds")
        time.sleep(2)
    # The ports close a moment before the process has finished writing the world and exiting.
    time.sleep(8)
    print("stopped")


if __name__ == "__main__":
    {"start": start, "stop": stop}[sys.argv[1]]()
