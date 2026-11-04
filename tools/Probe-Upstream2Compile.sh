#!/usr/bin/env bash
# Sandbox-only compatibility check: "ours" for conflicted hunks is NOT a
# migration strategy. This temporary merge is never committed or pushed.
set -euo pipefail
upstream=9361ea54c91e875c147d6aa73ecf94316e145800
git fetch --no-tags --depth=200 https://github.com/MorpheApp/morphe-desktop.git "$upstream"
test "$(git rev-parse FETCH_HEAD)" = "$upstream"
git config user.name "Morphe disposable compatibility audit"
git config user.email "morphe-compatibility@users.noreply.github.com"
echo "BASE_CUSTOM_SHA=$(git rev-parse HEAD)"
echo "BASE_UPSTREAM_SHA=$upstream"
set +e
git merge -X ours --no-commit --no-ff "$upstream" > "$RUNNER_TEMP/morphe-temp-merge.log" 2>&1
merge_rc=$?
set -e
if [[ "$merge_rc" -ne 0 ]]; then
  echo "TEMPORARY_MERGE_FAILED=1"
  cat "$RUNNER_TEMP/morphe-temp-merge.log"
  git diff --name-only --diff-filter=U
  exit 1
fi
echo "TEMPORARY_MERGE_FOR_COMPILE_ONLY=1"
echo "MIGRATION_COMPLETED=NO"
echo "NOTE=Conflict hunks preferentially kept Custom, requiring independent functional reconciliation"
git status --short > "$RUNNER_TEMP/morphe-merge-status.txt"
sed -n '1,100p' "$RUNNER_TEMP/morphe-merge-status.txt"
./gradlew test --no-daemon -Dorg.gradle.mirror.maven.settings=true
echo "TEMPORARY_MERGE_TESTS=PASS_NEEDS_MANUAL_CODE_AUDIT"
