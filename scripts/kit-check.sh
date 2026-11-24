#!/usr/bin/env bash
# Diffs this repo's shared kit (app/src/main/java/dev/savar/kit) against the other three apps' main branches.
set -u
cd "$(dirname "$0")/.."
dir=app/src/main/java/dev/savar/kit
tmp=$(mktemp)
status=0
for repo in aria-player minutia hiatus regista; do
  for f in "$dir"/*.kt; do
    name=$(basename "$f")
    if ! curl -fsSL "https://raw.githubusercontent.com/savar95x/$repo/main/$dir/$name" -o "$tmp" 2>/dev/null; then
      echo "$repo: $name missing"; status=1
    elif ! cmp -s "$tmp" "$f"; then
      echo "$repo: $name differs"; status=1
    fi
  done
done
rm -f "$tmp"
[ $status -eq 0 ] && echo "kit in sync with all four repos"
exit $status
