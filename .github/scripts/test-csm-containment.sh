#!/usr/bin/env bash
# Asserts that City Super Mod types are named in exactly one source file.
#
# CSM is a compile-only dependency: it is absent at runtime unless a player has installed it, so any
# class that names a CSM type throws NoClassDefFoundError the moment it is resolved without CSM. The
# mod stays safe only because every such name is confined to CsmCompat, which is reached solely behind
# a Loader.isModLoaded("csm") check. An import anywhere else compiles perfectly and fails only at
# runtime, in the configuration nobody tests -- which is precisely why it is worth a build-time check.
#
# Deliberately POSIX-ish: no mapfile, no arrays. macOS ships bash 3.2 and this has to be runnable by
# hand as well as on the Ubuntu runners, for the same reason test-release-tooling.sh is.
set -eu

cd "$(dirname "$0")/../.."
expected="src/main/java/com/supermartijn642/movingelevators/compat/CsmCompat.java"

offenders=$(grep -rl "micatechnologies" src/main/java/ || true)

if [ "$offenders" = "$expected" ]; then
    echo "ok  CSM types confined to ${expected}"
    exit 0
fi

echo "FAIL  City Super Mod types must appear only in ${expected}"
echo "      found in:"
echo "$offenders" | sed 's/^/        /'
echo
echo "      CSM is compile-only and absent at runtime. Move the code behind CsmCompat."
exit 1
