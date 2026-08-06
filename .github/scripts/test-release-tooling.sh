#!/usr/bin/env bash
#
# Checks for the release tooling. Run by the "Test Release Tooling" job in
# test-mod-build-pr.yml, and runnable locally with:
#
#     .github/scripts/test-release-tooling.sh
#
# Two halves:
#
#   1. extract-upstream-lineage.sh behaviour, including the failure paths. These matter because
#      changelog.md takes its content from upstream on every merge, so the parser meets input it
#      did not choose.
#   2. Invariants in the release workflow itself. The split between full releases (which quote
#      upstream's notes inline) and pre-releases (which don't) lives in YAML, where nothing else
#      would catch it silently flipping.
#
# Exits non-zero if any check fails.

set -uo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
SCRIPT="$REPO_ROOT/.github/scripts/extract-upstream-lineage.sh"
WORKFLOW="$REPO_ROOT/.github/workflows/build-mod-release-pre-release-main.yml"

PASS=0
FAIL=0

pass() { PASS=$((PASS + 1)); printf '  \033[32mok\033[0m   %s\n' "$1"; }
fail() {
  FAIL=$((FAIL + 1))
  printf '  \033[31mFAIL\033[0m %s\n' "$1"
  [ $# -gt 1 ] && printf '         %s\n' "$2"
  return 0
}

check() { # check <description> <expected> <actual>
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "expected [$2], got [$3]"; fi
}

check_contains() { # check_contains <description> <needle> <haystack>
  case "$3" in *"$2"*) pass "$1" ;; *) fail "$1" "expected to find [$2]" ;; esac
}

check_not_contains() { # check_not_contains <description> <needle> <haystack>
  case "$3" in *"$2"*) fail "$1" "did not expect to find [$2]" ;; *) pass "$1" ;; esac
}

# --- 1. extract-upstream-lineage.sh behaviour --------------------------------------------------

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# Runs the real script against a synthetic changelog. Populates RC, OUT and ENV_OUT.
run_lineage() { # run_lineage <changelog contents> [mod_version]
  printf '%s' "$1" > "$WORK/changelog.md"
  printf 'mod_version=%s\n' "${2:-1.4.12}" > "$WORK/gradle.properties"
  : > "$WORK/github_env"
  OUT=$(CHANGELOG_FILE="$WORK/changelog.md" \
        GRADLE_PROPERTIES_FILE="$WORK/gradle.properties" \
        GITHUB_ENV="$WORK/github_env" \
        bash "$SCRIPT" 2>&1)
  RC=$?
  ENV_OUT=$(cat "$WORK/github_env")
}

echo "extract-upstream-lineage.sh"

run_lineage '### Moving Elevators 1.4.12
- first note
- second note

### Moving Elevators 1.4.11
- older note
'
check          "typical changelog exits 0"              "0" "$RC"
check_contains "  captures the newest version"          "UPSTREAM_BASE_VERSION=1.4.12" "$ENV_OUT"
check_contains "  captures the newest section's notes"  "- first note"                 "$ENV_OUT"
check_not_contains "  stops at the next heading"        "- older note"                 "$ENV_OUT"
check_not_contains "  does not warn on a matching mod_version" "::warning" "$OUT"

run_lineage '### Moving Elevators 1.4.12
- only section, no following heading
'
check          "single section exits 0"                 "0" "$RC"
check_contains "  still captures its notes"             "- only section" "$ENV_OUT"

run_lineage '### Moving Elevators 1.4.12


- notes after blank lines


### Moving Elevators 1.4.11
'
check_contains "leading blank lines are trimmed" \
  "$(printf 'Upstream release notes for Moving Elevators 1.4.12:\n\n- notes after blank lines')" "$ENV_OUT"

run_lineage '### Moving Elevators 1.4.12

### Moving Elevators 1.4.11
- older
'
check          "empty newest section still exits 0"     "0" "$RC"
check_contains "  warns about the empty section"        "::warning" "$OUT"
check_contains "  still reports the lineage"            "UPSTREAM_BASE_VERSION=1.4.12" "$ENV_OUT"

run_lineage '### Moving Elevators 1.4.13
- newer than gradle.properties
' '1.4.12'
check          "stale mod_version still exits 0"        "0" "$RC"
check_contains "  warns that one of them is stale"      "::warning" "$OUT"

run_lineage '### Moving Elevators 1.4.12
- innocuous
UPSTREAM_NOTES_EOF_a7f3c1
INJECTED=1
'
check          "delimiter injection fails closed"       "1" "$RC"
check_contains "  reports why"                          "reserved heredoc delimiter" "$OUT"
check_not_contains "  writes no notes"                  "INJECTED=1" "$ENV_OUT"

run_lineage 'no headings at all
'
check          "unparseable changelog fails"            "1" "$RC"
check_contains "  reports why"                          "Could not parse an upstream version" "$OUT"

# The real file in the repo, not a synthetic one: catches an upstream merge that reshapes it.
: > "$WORK/github_env"
OUT=$(cd "$REPO_ROOT" && GITHUB_ENV="$WORK/github_env" bash "$SCRIPT" 2>&1)
check          "the repo's own changelog.md parses"     "0" "$?"
check_contains "  and yields a version"                 "UPSTREAM_BASE_VERSION=" "$(cat "$WORK/github_env")"
check_not_contains "  with mod_version in agreement"    "::warning file" "$OUT"

# --- 2. Release workflow invariants ------------------------------------------------------------

echo
echo "build-mod-release-pre-release-main.yml"

python3 -c 'import yaml' 2>/dev/null || pip install --quiet pyyaml

BODIES=$(python3 - "$WORKFLOW" <<'PY'
import sys, yaml
d = yaml.safe_load(open(sys.argv[1]))
steps = d['jobs']['assemble_release']['steps']
for s in steps:
    n = s.get('name', '')
    if n in ('Create Release Entry', 'Create Pre-Release Entry'):
        print(f'===== {n}')
        print(s.get('with', {}).get('body', ''))
print('===== STEP NAMES')
for s in steps:
    print(s.get('name', ''))
PY
)

if [ -z "$BODIES" ]; then
  fail "workflow YAML parses" "python3 produced no output"
else
  pass "workflow YAML parses"

  RELEASE_BODY=$(printf '%s\n' "$BODIES" | sed -n '/^===== Create Release Entry$/,/^===== Create Pre-Release Entry$/p')
  PRERELEASE_BODY=$(printf '%s\n' "$BODIES" | sed -n '/^===== Create Pre-Release Entry$/,/^===== STEP NAMES$/p')

  check_contains "the lineage step still exists"                "Extract Upstream Lineage" "$BODIES"
  check_contains "release body prints the lineage"              "env.UPSTREAM_BASE_VERSION" "$RELEASE_BODY"
  check_contains "pre-release body prints the lineage"          "env.UPSTREAM_BASE_VERSION" "$PRERELEASE_BODY"
  check_contains "release body quotes upstream notes inline"    "env.UPSTREAM_RELEASE_NOTES" "$RELEASE_BODY"
  check_not_contains "pre-release body does NOT quote them"     "env.UPSTREAM_RELEASE_NOTES" "$PRERELEASE_BODY"
  check_contains "both bodies carry the unofficial-fork notice" "unofficial fork" "$RELEASE_BODY"
  check_contains "  (pre-release too)"                          "unofficial fork" "$PRERELEASE_BODY"
fi

# --- Summary -----------------------------------------------------------------------------------

echo
echo "$PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ] || exit 1
