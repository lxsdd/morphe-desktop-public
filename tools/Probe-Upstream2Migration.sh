#!/usr/bin/env bash
# Read-only integration feasibility probe. Never pushes, merges remote branches,
# creates a release, exports a binary, or contacts an Android device.
set -euo pipefail
expected_custom=9604029de80f65762651ab263afe523eba42f323
expected_upstream=9361ea54c91e875c147d6aa73ecf94316e145800
report=${1:-upstream-2-migration-report.txt}
if [[ "$(git rev-parse HEAD)" != "$expected_custom" && "${ALLOW_NEW_CUSTOM_HEAD:-0}" != "1" ]]; then
  echo "REFUSED: custom HEAD differs from reviewed baseline; use ALLOW_NEW_CUSTOM_HEAD=1 for a new candidate." >&2
  exit 2
fi
git fetch --no-tags --depth=200 https://github.com/MorpheApp/morphe-desktop.git "$expected_upstream"
test "$(git rev-parse FETCH_HEAD)" = "$expected_upstream" || { echo "REFUSED: upstream SHA mismatch" >&2; exit 3; }
git config user.name "Morphe migration preflight"
git config user.email "migration-preflight@users.noreply.github.com"
set +e
git merge --no-commit --no-ff "$expected_upstream" > /tmp/morphe-merge-output.txt 2>&1
rc=$?
set -e
{
  echo "CUSTOM_HEAD=$(git rev-parse HEAD)"
  echo "UPSTREAM_2_0_SHA=$expected_upstream"
  echo "MERGE_EXIT=$rc"
  echo "UNMERGED_FILES=$(git diff --name-only --diff-filter=U | wc -l | tr -d ' ')"
  echo "CHANGED_FILES=$(git diff --name-only | wc -l | tr -d ' ')"
  echo "UNMERGED_PATHS_BEGIN"
  git diff --name-only --diff-filter=U
  echo "UNMERGED_PATHS_END"
  if [[ "$rc" -eq 0 ]]; then
    echo "RESULT=MERGE_CLEAN_NEEDS_BUILD_AND_REGRESSION"
  else
    echo "RESULT=CONFLICTS_NEED_CODE_MIGRATION"
  fi
} | tee "$report"
if git rev-parse -q --verify MERGE_HEAD >/dev/null; then git merge --abort; fi
# Exit 0 only to allow the reporting job to complete. A conflict is NOT an accepted build.
