#!/usr/bin/env bash
#
# Boot a dedicated server via `./gradlew runServer` and assert it reaches "Done (".
#
# Why this exists: mod code that compiles fine can still be impossible to load on a
# dedicated server -- a client-only class referenced from common code, a mixin listed
# under `mixins` instead of `client` in movingelevators.mixins.json, a Side.CLIENT packet
# handler that touches Minecraft directly. Forge only catches those at server startup, so
# `./gradlew build` is perfectly happy right up until production dies. The sibling SUM mod
# shipped three such bugs at once in 2026.07.19 and took a server down; every one of them
# was reachable from a single server boot. This mod's players are on dedicated servers,
# which is also where its hard-to-reproduce reports come from.
#
# `runServer` never returns on success, so we background it, tail the logs for a verdict,
# then shut it down.
#
# Env:
#   SMOKE_TIMEOUT   seconds to wait for startup (default 900)
#   SMOKE_LOG       Gradle stdout/stderr log file path (default server-smoke.log)
#   GRADLE_ARGS     extra arguments for the runServer line (default none)
#
# Runs on the Ubuntu CI runners and, by hand, in Git Bash on Windows and in macOS's bash 3.2,
# which is why it avoids mapfile and looks for taskkill before pkill.

set -uo pipefail

cd "$(dirname "$0")/../.."

TIMEOUT="${SMOKE_TIMEOUT:-900}"
LOG="${SMOKE_LOG:-server-smoke.log}"
GRADLE_ARGS="${GRADLE_ARGS:-}"

# build.gradle gives the dedicated server its own run directory, so the client's world, config
# and mods folder are never in play here and the two can run side by side.
RUN_DIR="run/server"
GAME_LOG="$RUN_DIR/logs/latest.log"

# Signals a successfully started dedicated server.
SUCCESS_RE='Done \([0-9.]+s\)!'

# Any of these mean the server is not coming up. "for invalid side" is the specific
# signature of client-only code reaching the server.
FAILURE_RE='Encountered an unexpected exception|MissingModsException|for invalid side|A fatal error has occurred|The state engine was in incorrect state|Failed to start the minecraft server|FML has found a problem|Minecraft EULA not accepted'

# The EULA has to be accepted or the server exits before it loads a single mod. Getting this
# wrong is invisible locally, where an already-accepted eula.txt lingers from earlier manual
# runs, and only shows up on a clean CI checkout.
mkdir -p "$RUN_DIR/logs"
printf 'eula=true\n' > "$RUN_DIR/eula.txt"

# A stale log would let a previous run's "Done" satisfy this one. Under ForgeGradle the
# console appender does not always come up in dev, so the verdict is read from both Gradle's
# stdout and the game's own log file.
rm -f "$GAME_LOG"

echo "==> Starting dedicated server (timeout ${TIMEOUT}s)${GRADLE_ARGS:+ with ${GRADLE_ARGS}}"
# GRADLE_ARGS is deliberately unquoted: it may carry more than one argument.
# shellcheck disable=SC2086
./gradlew runServer $GRADLE_ARGS \
  -Dhttp.socketTimeout=60000 -Dhttp.connectionTimeout=60000 \
  -Dorg.gradle.internal.http.socketTimeout=60000 \
  -Dorg.gradle.internal.http.connectionTimeout=60000 \
  > "$LOG" 2>&1 &
GRADLE_PID=$!

logs_match() { # logs_match <regex>
  grep -qE "$1" "$LOG" 2>/dev/null || grep -qE "$1" "$GAME_LOG" 2>/dev/null
}

verdict="timeout"
elapsed=0
while [ "$elapsed" -lt "$TIMEOUT" ]; do
  if logs_match "$FAILURE_RE"; then
    verdict="crash"
    break
  fi
  if logs_match "$SUCCESS_RE"; then
    verdict="ok"
    break
  fi
  if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
    # Gradle exited without ever printing "Done (" -- build failure or early abort.
    verdict="exited"
    break
  fi
  sleep 5
  elapsed=$((elapsed + 5))
done

echo "==> Stopping server (verdict: ${verdict}, after ${elapsed}s)"
# The Gradle wrapper spawns a single-use daemon, which spawns the server JVM; only the leaf
# holds the world's session.lock, so the whole tree has to go. Windows has no process groups
# worth the name, but taskkill can walk the tree from the wrapper's pid -- its WINDOWS pid,
# which is not the MSYS pid that $! holds in Git Bash; `ps -W` maps one to the other. Getting
# this wrong is silent: taskkill reports "not found", `|| true` swallows it, and the wait below
# never returns.
if command -v taskkill >/dev/null 2>&1; then
  winpid="$(ps -W -p "$GRADLE_PID" 2>/dev/null | awk -v p="$GRADLE_PID" '$1 == p { print $4 }')"
  taskkill //PID "${winpid:-$GRADLE_PID}" //T //F >/dev/null 2>&1 || true
else
  kill "$GRADLE_PID" 2>/dev/null
  pkill -f 'net.minecraft' 2>/dev/null
  pkill -f 'GradleWrapperMain' 2>/dev/null
fi
# Bounded rather than a bare `wait`, so a kill that did not take leaves a complaint instead of
# a hung job.
for _ in $(seq 1 30); do
  kill -0 "$GRADLE_PID" 2>/dev/null || break
  sleep 1
done
if kill -0 "$GRADLE_PID" 2>/dev/null; then
  echo "::warning::the runServer process tree (pid $GRADLE_PID) is still alive after the stop"
fi
wait "$GRADLE_PID" 2>/dev/null

if [ "$verdict" = "ok" ]; then
  echo "==> PASS: dedicated server reached startup"
  { grep -E "$SUCCESS_RE" "$LOG"; grep -E "$SUCCESS_RE" "$GAME_LOG"; } 2>/dev/null | head -1
  exit 0
fi

echo "==> FAIL: dedicated server did not start (${verdict})"
echo "----- matching failure lines -----"
{ grep -nE "$FAILURE_RE" "$LOG"; grep -nE "$FAILURE_RE" "$GAME_LOG"; } 2>/dev/null | head -20
echo "----- last 120 lines of $LOG -----"
tail -120 "$LOG"
if [ -f "$GAME_LOG" ]; then
  echo "----- last 120 lines of $GAME_LOG -----"
  tail -120 "$GAME_LOG"
fi
exit 1
