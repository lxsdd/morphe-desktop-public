#!/usr/bin/env python3
"""Fail closed unless every releasable source/build/resource blob matches the pinned public snapshot."""
import pathlib
import re
import subprocess
import sys

MANIFEST = pathlib.Path("tools/public_beta_source_manifest.tsv")
EXTRA = {
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "proguard-rules.pro", "LICENSE", "NOTICE",
    "tools/verify_compose_resource_keys.py",
    "tools/test_verify_compose_resource_keys.py",
}

def is_releasable(path):
    return path.startswith(("src/", "buildSrc/", "gradle/", "aboutlibraries/")) or path in EXTRA

def main():
    expected = {}
    for row in MANIFEST.read_text(encoding="utf-8").splitlines():
        sha, path = row.split("\t", 1)
        if not re.fullmatch(r"[0-9a-f]{40}", sha) or not is_releasable(path) or path in expected:
            raise SystemExit("Invalid or duplicate source manifest row")
        expected[path] = sha
    listing = subprocess.check_output(["git", "ls-tree", "-r", "--full-tree", "HEAD"], text=True)
    actual = {}
    for line in listing.splitlines():
        metadata, path = line.split("\t", 1)
        if is_releasable(path):
            mode, kind, sha = metadata.split()
            if kind != "blob" or mode not in ("100644", "100755"):
                raise SystemExit("Unexpected source item: " + path)
            actual[path] = sha
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    wrong = sorted(p for p in expected.keys() & actual.keys() if expected[p] != actual[p])
    if missing or extra or wrong:
        print("SOURCE_PARITY=FAIL", "MISSING=", missing[:10], "EXTRA=", extra[:10], "WRONG=", wrong[:10])
        raise SystemExit(1)
    if len(expected) < 591:
        raise SystemExit("Suspiciously small source manifest")
    print(f"PUBLIC_BETA_SOURCE_PARITY=PASS verified_files={len(expected)}")

if __name__ == "__main__":
    main()
