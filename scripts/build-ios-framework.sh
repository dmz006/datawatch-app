#!/usr/bin/env bash
# Build the KMP shared XCFramework for iOS and optionally regenerate the
# Xcode project. Run this once before opening DatawatchClient.xcodeproj
# in Xcode, and again after any change to shared/ Kotlin code.
#
# Usage:
#   ./scripts/build-ios-framework.sh            # debug (default)
#   ./scripts/build-ios-framework.sh release    # release slice (for TestFlight)
#   ./scripts/build-ios-framework.sh --xcodegen # debug + regenerate xcodeproj

set -euo pipefail
cd "$(dirname "$0")/.."

VARIANT="${1:-debug}"
XCODEGEN=false

for arg in "$@"; do
  case "$arg" in
    release) VARIANT=release ;;
    --xcodegen) XCODEGEN=true ;;
  esac
done

TASK="assemble${VARIANT^}XCFramework"

echo "▶ Building KMP shared framework ($VARIANT)..."
./gradlew ":shared:$TASK" --no-daemon

FRAMEWORK_OUT="shared/build/XCFrameworks/$VARIANT/DatawatchShared.xcframework"
echo "✓ XCFramework: $FRAMEWORK_OUT"

if $XCODEGEN; then
  if ! command -v xcodegen &>/dev/null; then
    echo "xcodegen not found — install with: brew install xcodegen"
    exit 1
  fi

  # Export version vars so project.yml can substitute MARKETING_VERSION /
  # CURRENT_PROJECT_VERSION at generate time (same as CI does).
  export DATAWATCH_APP_VERSION
  DATAWATCH_APP_VERSION=$(grep -oP '^DATAWATCH_APP_VERSION=\K.+' gradle.properties)
  export DATAWATCH_APP_VERSION_CODE
  DATAWATCH_APP_VERSION_CODE=$(grep -oP '^DATAWATCH_APP_VERSION_CODE=\K.+' gradle.properties)

  echo "▶ Generating Xcode project (v$DATAWATCH_APP_VERSION / $DATAWATCH_APP_VERSION_CODE)..."
  (cd iosApp && xcodegen generate --spec project.yml)
  echo "✓ iosApp/DatawatchClient.xcodeproj"
fi

echo ""
echo "Ready. Open iosApp/DatawatchClient.xcodeproj in Xcode."
