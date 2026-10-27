#!/usr/bin/env bash
# GoWorkBro test tiers — run the RIGHT amount of test for the phase.
#
#   fast : pure-JVM logic classes, seconds. Run this on every logic change.
#   ui   : Robolectric UI/VM classes (activity boots, Compose semantics).
#          Run when a screen/VM changed, or before committing.
#   all  : everything, with a 15-min hard timeout so a hung test can never
#          eat a whole session again (see AGENTS.md "Testing discipline").
#
# New test classes must be sorted into the right tier below (rule: no
# Robolectric/Compose -> fast, else ui).
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-fast}"

case "$MODE" in
  fast)
    exec ./gradlew :app:testDebugUnitTest --console=plain \
      --tests "com.azarai.goworkbro.BackupTest" \
      --tests "com.azarai.goworkbro.CountdownOpsTest" \
      --tests "com.azarai.goworkbro.DatesTest" \
      --tests "com.azarai.goworkbro.OrderingTest" \
      --tests "com.azarai.goworkbro.SleepOpsTest" \
      --tests "com.azarai.goworkbro.StoreTest" \
      --tests "com.azarai.goworkbro.TodoOrderTest" \
      --tests "com.azarai.goworkbro.UpdaterTest"
    ;;
  ui)
    exec ./gradlew :app:testDebugUnitTest --console=plain \
      --tests "com.azarai.goworkbro.AppSmokeTest" \
      --tests "com.azarai.goworkbro.HomeQuickLogTest" \
      --tests "com.azarai.goworkbro.OverviewFlowTest" \
      --tests "com.azarai.goworkbro.TimerRulesTest"
    ;;
  all)
    timeout 900 ./gradlew :app:testDebugUnitTest --console=plain
    ;;
  *)
    echo "usage: scripts/test.sh [fast|ui|all]" >&2
    exit 1
    ;;
esac
