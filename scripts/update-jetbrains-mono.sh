#!/usr/bin/env bash
# Update the bundled JetBrains Mono (terminal font, D8a) to the latest upstream
# release. Used by .github/workflows/font-update.yml and runnable locally.
# Prints "UPDATED <old> -> <new>" or "UP_TO_DATE <version>".
set -euo pipefail
DIR="composeApp/src/androidMain/assets/xterm/fonts"
CUR="$(cat "$DIR/JetBrainsMono.VERSION" 2>/dev/null || echo none)"
LATEST="$(curl -fsSL https://api.github.com/repos/JetBrains/JetBrainsMono/releases/latest \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["tag_name"])')"
if [ "$LATEST" = "$CUR" ]; then echo "UP_TO_DATE $CUR"; exit 0; fi
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
VER="${LATEST#v}"
curl -fsSL -o "$TMP/jbm.zip" \
  "https://github.com/JetBrains/JetBrainsMono/releases/download/${LATEST}/JetBrainsMono-${VER}.zip"
unzip -q "$TMP/jbm.zip" -d "$TMP/x"
for w in Regular Bold; do
  test -s "$TMP/x/fonts/webfonts/JetBrainsMono-$w.woff2" || { echo "missing $w woff2 in release" >&2; exit 1; }
  cp "$TMP/x/fonts/webfonts/JetBrainsMono-$w.woff2" "$DIR/"
done
cp "$TMP/x/OFL.txt" "$DIR/JetBrainsMono-OFL.txt"
echo "$LATEST" > "$DIR/JetBrainsMono.VERSION"
echo "UPDATED $CUR -> $LATEST"
