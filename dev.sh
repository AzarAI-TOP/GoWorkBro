#!/bin/bash
# Dev script — reads credentials from lib/services/local_config.dart
# and passes them via --dart-define.
#
# Usage:
#   bash dev.sh          # run in debug mode
#   bash dev.sh windows  # build windows release
#   bash dev.sh apk      # build android release

set -e

export PATH="/c/flutter/bin:$PATH"
cd "$(dirname "$0")"

CONFIG="lib/services/local_config.dart"
if [ ! -f "$CONFIG" ]; then
  echo "⚠️  lib/services/local_config.dart not found!"
  echo "   Create it with your Supabase credentials:"
  echo "   const String localSupabaseUrl = 'https://your-project.supabase.co';"
  echo "   const String localSupabaseAnonKey = 'your-publishable-key';"
  exit 1
fi

URL=$(grep 'localSupabaseUrl' "$CONFIG" | sed "s/.*= *'//;s/'.*//")
KEY=$(grep 'localSupabaseAnonKey' "$CONFIG" | sed "s/.*= *'//;s/'.*//")

if [ -z "$URL" ] || [ -z "$KEY" ]; then
  echo "⚠️  Could not parse credentials from $CONFIG"
  exit 1
fi

DART_DEFINES="--dart-define=SUPABASE_URL=$URL --dart-define=SUPABASE_ANON_KEY=$KEY"

case "${1:-run}" in
  run)
    echo "🚀 Running GoWorkBro (debug)..."
    flutter run $DART_DEFINES
    ;;
  windows)
    echo "🏗️  Building Windows release..."
    flutter build windows --release $DART_DEFINES
    ;;
  apk)
    echo "🏗️  Building Android APK..."
    export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
    flutter build apk --release $DART_DEFINES
    ;;
  *)
    echo "Usage: bash dev.sh [run|windows|apk]"
    exit 1
    ;;
esac
