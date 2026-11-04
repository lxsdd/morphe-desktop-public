#!/usr/bin/env bash
# Materialize a source-only, unqualified compile candidate in a NEW branch.
# Never modifies main/PR 39/40/42, never publishes binaries or touches devices.
set -euo pipefail
upstream=9361ea54c91e875c147d6aa73ecf94316e145800
target=integration/custom-2-0-source-stage-20261104
if git ls-remote --exit-code origin "refs/heads/$target" >/dev/null 2>&1; then
  echo "STAGE_ALREADY_EXISTS=YES"
  exit 0
fi
custom=$(git rev-parse HEAD)
git fetch --no-tags --depth=200 https://github.com/MorpheApp/morphe-desktop.git "$upstream"
test "$(git rev-parse FETCH_HEAD)" = "$upstream"
git config user.name "Morphe source-stage bot"
git config user.email "source-stage@users.noreply.github.com"
git merge --no-commit --no-ff -X ours "$upstream"
test -z "$(git diff --name-only --diff-filter=U)"
tree=$(git write-tree)
new=$(printf '%s\n' 'WIP: disposable Morphe 2.0 merged source staging (NOT QUALIFIED)' '' 'Upstream: 9361ea54c91e875c147d6aa73ecf94316e145800' 'Custom parent: '"$custom" 'Conflict strategy: -X ours, review mandatory' | git commit-tree "$tree" -p "$custom")
git push origin "$new:refs/heads/$target"
echo "SOURCE_STAGE_SHA=$new"
echo "SOURCE_STAGE_BRANCH=$target"
echo "MIGRATION_COMPLETED=NO"
