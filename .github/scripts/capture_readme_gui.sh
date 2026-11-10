#!/usr/bin/env bash
# Capture screenshots from the actual public beta UI in a clean, isolated Linux session.
# This script deliberately does not attach ADB devices, read user files or install APKs.
set -euo pipefail

test "${GITHUB_REPOSITORY:-}" = "lxsdd/morphe-desktop-public"
test "${GITHUB_REF_NAME:-}" = "docs/readme-screenshots-20261111"

jar="$(find build/libs -maxdepth 1 -type f -name '*-all.jar' | sort | head -1)"
test -n "$jar"
work="$(mktemp -d)"
export MORPHE_DATA_DIR="$work/morphe-data"
export HOME="$work/home"
mkdir -p "$MORPHE_DATA_DIR" "$HOME" docs/images/current
export DISPLAY=:91
Xvfb :91 -screen 0 1600x1000x24 -nolisten tcp > "$work/xvfb.log" 2>&1 &
xvfb_pid=$!
app_pid=""
cleanup() {
  if test -n "$app_pid"; then kill "$app_pid" 2>/dev/null || true; fi
  kill "$xvfb_pid" 2>/dev/null || true
}
trap cleanup EXIT

sleep 2
java -Djava.awt.headless=false -jar "$jar" > "$work/gui.log" 2>&1 &
app_pid=$!
window=""
for i in $(seq 1 100); do
  if ! kill -0 "$app_pid" 2>/dev/null; then
    tail -n 80 "$work/gui.log"
    echo "GUI terminated before screenshot capture" >&2
    exit 1
  fi
  window="$(xdotool search --onlyvisible --name 'Morphe' 2>/dev/null | tail -1 || true)"
  test -n "$window" && break
  sleep 2
done
if test -z "$window"; then
  tail -n 80 "$work/gui.log"
  echo "No visible Morphe desktop window" >&2
  exit 1
fi
xdotool windowsize "$window" 1400 860
xdotool windowmove "$window" 60 60
xdotool windowactivate --sync "$window" || true
# The first GUI start downloads/checks platform-specific native rendering libraries.
sleep 35
if ! kill -0 "$app_pid" 2>/dev/null; then
  tail -n 100 "$work/gui.log"
  echo "GUI terminated during initialization" >&2
  exit 1
fi
import -window "$window" -depth 8 "PNG24:docs/images/current/desktop-apps.png"
# HeaderBar's centered two-tab navigation: Sources occupies the left tab.
xdotool mousemove --window "$window" 655 33 click 1
sleep 6
import -window "$window" -depth 8 "PNG24:docs/images/current/desktop-sources.png"

for file in docs/images/current/desktop-*.png; do
  identify -format '%w %h %k\n' "$file"
  test "$(identify -format '%w' "$file")" -ge 900
  test "$(identify -format '%h' "$file")" -ge 600
  test "$(identify -format '%k' "$file")" -ge 75
  test "$(wc -c < "$file")" -ge 25000
done
if cmp -s docs/images/current/desktop-apps.png docs/images/current/desktop-sources.png; then
  echo "Navigation failed: screenshots are byte-identical" >&2
  exit 1
fi
echo "Captured isolated real-application screenshots. No device or personal data accessed."
