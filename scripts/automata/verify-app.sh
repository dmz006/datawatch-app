#!/usr/bin/env bash
# Verification script for datawatch Automata working on this repo (type
# "mobile-app"). A task passes only if this exits 0. It runs the same gate as CI
# (.github/workflows/ci.yml) plus the locale/terminology gate. The iOS Swift
# build needs macOS: it is verified by the CI "Build (iOS Simulator)" check on
# the task's PR, not here. See docs/automata-workflow.md.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

echo "== version parity"
v=$(sed -n 's/^DATAWATCH_APP_VERSION=//p' gradle.properties)
grep -q "\"$v\"" shared/src/commonMain/kotlin/com/dmzs/datawatchclient/Version.kt \
  || { echo "gradle.properties version $v is not in Version.kt"; exit 1; }

echo "== locale / Automata terminology gate"
python3 scripts/check-automata-terms.py

echo "== gradle check detekt ktlintCheck"
./gradlew check detekt ktlintCheck --console=plain -q

echo "verify-app: OK"
