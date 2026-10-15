#!/usr/bin/env bash
#
# The test results of the last run, one line for each module: tests, skipped, failed.
#
# A green build proves little for the modules that need a database: a test class whose server is not
# reachable is skipped, not failed. Run the suites (./gradlew test --continue), then this, and read
# the "skipped" column before calling a dialect verified. docs/VERIFICATION.md says how a release
# records it.
#
#   ./gradlew test --continue && scripts/test-matrix.sh

set -euo pipefail
cd "$(dirname "$0")/.."

printf '| module | tests | skipped | failed |\n|---|---:|---:|---:|\n'
for dir in */build/test-results/test; do
    module="${dir%%/*}"
    # every testsuite element carries its own counts: add them up
    read -r tests skipped failed < <(
        cat "$dir"/*.xml 2>/dev/null \
            | grep -o '<testsuite [^>]*' \
            | sed -E 's/.*tests="([0-9]+)" skipped="([0-9]+)" failures="([0-9]+)" errors="([0-9]+)".*/\1 \2 \3 \4/' \
            | awk '{t+=$1; s+=$2; f+=$3+$4} END {print t+0, s+0, f+0}'
    )
    printf '| %s | %s | %s | %s |\n' "$module" "$tests" "$skipped" "$failed"
done
