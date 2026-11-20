#!/usr/bin/env bash
# Wait for the Screenshots workflow on HEAD (a non-main branch) to publish the "shots" branch, then extract it
# into out/shots/: PNGs, gradle.log and test results. Uses only git (no gh token needed).
# On failure prints the Kotlin compile errors and failing tests.
set -uo pipefail
sha=$(git rev-parse HEAD)
echo "Waiting for shots of ${sha:0:7}…"
for i in $(seq 1 90); do
  if git fetch -q --depth 1 origin shots 2>/dev/null && [ "$(git show FETCH_HEAD:SHA 2>/dev/null)" = "$sha" ]; then
    rm -rf out/shots && mkdir -p out/shots
    git archive FETCH_HEAD | tar -x -C out/shots
    find out/shots -name '*.png' | sort | sed 's|^|  |'
    if grep -q "BUILD SUCCESSFUL" out/shots/gradle.log 2>/dev/null; then echo "BUILD SUCCESSFUL"; exit 0; fi
    echo "BUILD FAILED"
    grep -E '^e: |What went wrong|Could not|FAILED|Exception|Caused by|expected:<' out/shots/gradle.log | head -60
    exit 1
  fi
  sleep 20
done
echo "Timed out"; exit 1
