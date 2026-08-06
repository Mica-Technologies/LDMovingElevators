#!/usr/bin/env bash
#
# Resolves this fork's upstream lineage from changelog.md and publishes it to GITHUB_ENV for the
# release workflow.
#
# changelog.md is an unmodified mirror of SuperMartijn642's release notes (see CHANGELOG-FORK.md
# for why), so its newest section is exactly the upstream release this fork sits on. Our own
# releases are tagged YYYY.MM.DD, which on its own says nothing about that.
#
# Sets in GITHUB_ENV:
#   UPSTREAM_BASE_VERSION   e.g. "1.4.12" -- printed in every release body
#   UPSTREAM_RELEASE_NOTES  the newest section's notes, quoted in FULL release bodies only.
#                           Pre-releases are cut on every push and would otherwise repeat the
#                           same bullets until the upstream base version changes.
#
# Exit status:
#   0  lineage resolved. An empty notes section warns but still succeeds -- the lineage line
#      alone still identifies the upstream release.
#   1  changelog.md is missing, has no parseable heading, or contains the reserved heredoc
#      delimiter.
#
# Environment overrides, used by test-release-tooling.sh:
#   CHANGELOG_FILE, GRADLE_PROPERTIES_FILE, GITHUB_ENV

set -euo pipefail

CHANGELOG_FILE="${CHANGELOG_FILE:-changelog.md}"
GRADLE_PROPERTIES_FILE="${GRADLE_PROPERTIES_FILE:-gradle.properties}"
: "${GITHUB_ENV:?must point at a file to write (the runner sets this automatically)}"

# Heredoc delimiter for the multi-line notes value. GITHUB_ENV needs heredoc syntax for anything
# multi-line, and content containing the delimiter could close the block early and inject
# arbitrary environment variables into the job -- so we refuse rather than emit it. This matters
# because changelog.md takes its content from upstream on every merge.
DELIMITER='UPSTREAM_NOTES_EOF_a7f3c1'

if [ ! -f "$CHANGELOG_FILE" ]; then
  echo "::error::$CHANGELOG_FILE not found"
  exit 1
fi

# --- The upstream version this fork sits on --------------------------------------------------

upstreamVersion=$(grep -m1 -oE '^### Moving Elevators .+$' "$CHANGELOG_FILE" \
  | sed -E 's/^### Moving Elevators //' || true)

if [ -z "$upstreamVersion" ]; then
  echo "::error file=$CHANGELOG_FILE::Could not parse an upstream version from $CHANGELOG_FILE. Expected a newest heading of the form '### Moving Elevators <version>'."
  exit 1
fi

echo "UPSTREAM_BASE_VERSION=$upstreamVersion" >> "$GITHUB_ENV"
echo "Upstream base version: $upstreamVersion"

# gradle.properties records the same thing for the build. A mismatch means an upstream merge
# updated one and not the other -- worth flagging, but not worth blocking a release over.
if [ -f "$GRADLE_PROPERTIES_FILE" ]; then
  modVersionProperty=$(grep -E '^mod_version *=' "$GRADLE_PROPERTIES_FILE" \
    | head -n1 | cut -d= -f2- | xargs || true)
  if [ "$modVersionProperty" != "$upstreamVersion" ]; then
    echo "::warning file=$GRADLE_PROPERTIES_FILE::mod_version ($modVersionProperty) does not match the newest $CHANGELOG_FILE heading ($upstreamVersion). One of them is stale after an upstream merge."
  fi
fi

# --- The notes under that heading -------------------------------------------------------------

# Everything between the newest '### ' heading and the next one. Leading blank lines are dropped
# by sed; trailing ones fall out of command substitution eating trailing newlines.
section=$(awk '/^### /{ if (seen) exit; seen=1; next } seen { print }' "$CHANGELOG_FILE" \
  | sed '/./,$!d')

if printf '%s\n' "$section" | grep -qxF "$DELIMITER"; then
  echo "::error file=$CHANGELOG_FILE::$CHANGELOG_FILE contains the reserved heredoc delimiter $DELIMITER."
  exit 1
fi

if [ -n "$section" ]; then
  {
    echo "UPSTREAM_RELEASE_NOTES<<$DELIMITER"
    echo "Upstream release notes for Moving Elevators $upstreamVersion:"
    echo
    printf '%s\n' "$section"
    echo "$DELIMITER"
  } >> "$GITHUB_ENV"
  echo "Captured $(printf '%s\n' "$section" | wc -l | xargs) line(s) of upstream notes."
else
  echo "::warning file=$CHANGELOG_FILE::No notes found under the newest $CHANGELOG_FILE heading; publishing without inline upstream notes."
  echo "UPSTREAM_RELEASE_NOTES=" >> "$GITHUB_ENV"
fi
