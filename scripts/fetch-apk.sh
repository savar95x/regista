#!/usr/bin/env bash
# Download the APK of the latest GitHub Release (or a given tag) into ./out/
# Usage: scripts/fetch-apk.sh [vX.Y.Z]
# Note: through some proxies `gh release download <tag>` fails while the no-tag (latest) form works.
set -euo pipefail
mkdir -p out
rm -f out/Regista-*.apk
if [ $# -ge 1 ]; then
  gh release download "$1" --pattern '*.apk' --dir out --clobber
else
  gh release download --pattern '*.apk' --dir out --clobber
fi
ls -la out/Regista-*.apk
