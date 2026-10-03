"""
Runs the regression suite against the dev server and client, which must already be up (runServer
and runClient, from this checkout). Results land in tools/regression/results/<label>/.

    python run.py <label> [scenario ...]
"""
import json
import sys
import time
import traceback
from pathlib import Path

import scenarios
import testsite as S
from harness import Check, Game

HERE = Path(__file__).resolve().parent


def prepare(game):
    for command in ("gamerule doDaylightCycle false", "time set 6000", "weather clear 1000000",
                    "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamemode 1 " + game.player, "effect %s clear" % game.player,
                    # Anything in hand changes what a right-click does: a remote controller binds
                    # instead of pressing the button it is pointed at.
                    "clear " + game.player):
        game.cmd(command)
    game.teleport(-198.5, S.BOTTOM + 3, 180.5, yaw=0, pitch=10, fly=True)
    game.wait_rendered()
    S.build(game)
    game.wait_ticks(60)


def main(argv):
    label = argv[0]
    only = set(argv[1:])
    out = HERE / "results" / label
    game = Game(out)
    game.metrics = {}
    game.join()
    results = {"label": label, "started": time.strftime("%Y-%m-%d %H:%M:%S"), "scenarios": {}}
    prepare(game)
    for scenario in scenarios.ORDER:
        if only and scenario.__name__ not in only:
            continue
        print("==", scenario.__name__, flush=True)
        started = time.time()
        try:
            checks = scenario(game)
        except Exception as error:
            traceback.print_exc()
            checks = [Check(scenario.__name__ + ".completed", False, repr(error))]
        for check in checks:
            print("   %s %s %s" % ("PASS" if check.ok else "FAIL", check.name, check.detail), flush=True)
        results["scenarios"][scenario.__name__] = {
            "seconds": round(time.time() - started, 1),
            "checks": [c.as_dict() for c in checks],
        }
    results["metrics"] = game.metrics
    S.clear(game)
    game.teleport(-64.5, 104, 248.5, fly=False)
    (out / "results.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    failed = [c for s in results["scenarios"].values() for c in s["checks"] if not c["ok"]]
    print("\n%d checks, %d failed -> %s" % (
        sum(len(s["checks"]) for s in results["scenarios"].values()), len(failed), out))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
