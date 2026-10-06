#!/usr/bin/env bash
# Update the bundled Mermaid (Automaton spec diagrams, Android + iOS) to the
# latest stable npm release that has been out for at least 72 hours (AGENT.md
# dependency rule). Used by .github/workflows/mermaid-update.yml; runnable locally.
# Prints "UPDATED <old> -> <new>" or "UP_TO_DATE <version>".
set -euo pipefail
DIR="composeApp/src/androidMain/assets/mermaid"
MIN_AGE_HOURS="${MIN_AGE_HOURS:-72}"
CUR="$(cat "$DIR/mermaid.VERSION" 2>/dev/null || echo none)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
curl -fsSL -o "$TMP/meta.json" https://registry.npmjs.org/mermaid
read -r LATEST TARBALL < <(META="$TMP/meta.json" MIN_AGE_HOURS="$MIN_AGE_HOURS" python3 -c '
import os, json, re, datetime
d = json.load(open(os.environ["META"])); min_age = float(os.environ["MIN_AGE_HOURS"])
now = datetime.datetime.now(datetime.timezone.utc)
key = lambda v: [int(x) for x in v.split(".")]
cands = []
for v, ts in d["time"].items():
    if not re.fullmatch(r"\d+\.\d+\.\d+", v) or v not in d["versions"]:
        continue
    age = (now - datetime.datetime.fromisoformat(ts.replace("Z", "+00:00"))).total_seconds() / 3600
    if age >= min_age:
        cands.append(v)
v = max(cands, key=key)
print(v, d["versions"][v]["dist"]["tarball"])
')
if [ "$LATEST" = "$CUR" ]; then echo "UP_TO_DATE $CUR"; exit 0; fi
curl -fsSL -o "$TMP/m.tgz" "$TARBALL"
tar xzf "$TMP/m.tgz" -C "$TMP"
test -s "$TMP/package/dist/mermaid.min.js" || { echo "dist/mermaid.min.js missing in $LATEST" >&2; exit 1; }
grep -q 'globalThis\["mermaid"\]\|window.mermaid\|globalThis.mermaid' "$TMP/package/dist/mermaid.min.js" \
  || { echo "mermaid $LATEST no longer exposes a global; update the renderers" >&2; exit 1; }
mkdir -p "$DIR"
cp "$TMP/package/dist/mermaid.min.js" "$DIR/mermaid.min.js"
cp "$TMP/package/LICENSE" "$DIR/mermaid-LICENSE.txt"
echo "$LATEST" > "$DIR/mermaid.VERSION"
echo "UPDATED $CUR -> $LATEST"
