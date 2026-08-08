#!/bin/bash
# Build GoWorkBro release: Windows installer + Android APK
# Reads Supabase credentials from lib/services/local_config.dart
#
# Usage:
#   bash scripts/build_release.sh         # build both
#   bash scripts/build_release.sh windows  # Windows only
#   bash scripts/build_release.sh apk      # Android only

set -e

export PATH="/c/flutter/bin:$PATH"
cd "$(dirname "$0")/.."

CONFIG="lib/services/local_config.dart"
if [ ! -f "$CONFIG" ]; then
  echo "❌ lib/services/local_config.dart not found!"
  echo "   Create it with your Supabase credentials."
  exit 1
fi

URL=$(grep 'localSupabaseUrl' "$CONFIG" | sed "s/.*= *'//;s/'.*//")
KEY=$(grep 'localSupabaseAnonKey' "$CONFIG" | sed "s/.*= *'//;s/'.*//")

if [ -z "$URL" ] || [ -z "$KEY" ]; then
  echo "❌ Could not parse credentials from $CONFIG"
  exit 1
fi

DART_DEFINES="--dart-define=SUPABASE_URL=$URL --dart-define=SUPABASE_ANON_KEY=$KEY"
TARGET="${1:-all}"

build_windows() {
  echo "🏗️  Building Windows release..."
  flutter build windows --release $DART_DEFINES
  echo "✅ Windows build complete: build/windows/x64/runner/Release/goworkbro.exe"
}

build_apk() {
  echo "🏗️  Building Android APK..."
  export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
  flutter build apk --release $DART_DEFINES
  echo "✅ APK build complete: build/app/outputs/flutter-apk/app-release.apk"
}

case "$TARGET" in
  windows) build_windows ;;
  apk)     build_apk ;;
  all)     build_windows; build_apk ;;
  *)       echo "Usage: bash scripts/build_release.sh [windows|apk|all]"; exit 1 ;;
esac
