#!/usr/bin/env python3
"""Read-only custom-to-upstream 2.0 port inventory; never merges or writes source."""
import argparse
import pathlib
import re
import subprocess
import sys

CUSTOM_CRITICAL = (
    "src/main/kotlin/app/morphe/engine/installation/AdbApkInstaller.kt",
    "src/main/kotlin/app/morphe/engine/patches/PatchBundleLoader.kt",
    "src/main/kotlin/app/morphe/engine/patches/PatchRepository.kt",
    "src/main/kotlin/app/morphe/engine/util/AdbManager.kt",
    "src/main/kotlin/app/morphe/gui/ui/screens/home/HomeViewModel.kt",
    "src/main/kotlin/app/morphe/gui/ui/screens/patches/PatchSelectionScreen.kt",
)

def git(*args, check=True):
    result = subprocess.run(["git", *args], text=True, capture_output=True)
    if check and result.returncode:
        raise RuntimeError(f"git {' '.join(args)}: {result.stderr.strip()}")
    return result

def changed(base, head):
    return set(filter(None, git("diff", "--name-only", base, head).stdout.splitlines()))

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--upstream", required=True)
    ap.add_argument("--report", required=True)
    args = ap.parse_args()
    head = git("rev-parse", "HEAD").stdout.strip()
    base = git("merge-base", head, args.upstream).stdout.strip()
    upstream = changed(base, args.upstream)
    custom = changed(base, head)
    overlap = upstream & custom
    tree = git("merge-tree", "--write-tree", head, args.upstream, check=False)
    conflict_lines = [
        line for line in tree.stdout.splitlines()
        if line.startswith("CONFLICT ") or line.startswith("Auto-merging ")
    ]
    conflict_only = [line for line in conflict_lines if line.startswith("CONFLICT ")]
    # No false claim of a successful merge: the dry-run produces an index/tree only.
    manifest = pathlib.Path("gradle/libs.versions.toml").read_text()
    patcher = re.search(r'^morphe-patcher\s*=\s*"([^"]+)"', manifest, re.M)
    actual = patcher.group(1) if patcher else "NOT_FOUND"
    missing = [p for p in CUSTOM_CRITICAL if not pathlib.Path(p).is_file()]
    report = [
        "# Custom 2.0 stable migration preflight",
        "",
        f"- Custom HEAD: `{head}`",
        f"- Pinned official stable 2.0.0: `{args.upstream}`",
        f"- Shared merge base: `{base}`",
        f"- Upstream-modified paths since shared base: **{len(upstream)}**",
        f"- Custom-modified paths since shared base: **{len(custom)}**",
        f"- Overlapping paths requiring manual review: **{len(overlap)}**",
        f"- Read-only merge-tree exit: **{tree.returncode}**",
        f"- Explicit conflict lines: **{len(conflict_only)}**",
        f"- Current Custom morphe-patcher dependency: **{actual}**",
        "",
        "## High-priority overlapping source files",
        *[f"- `{p}`" for p in sorted(overlap) if p in CUSTOM_CRITICAL],
        "",
        "## Git merge-tree conflict diagnostics (first 80)",
        *[f"- {line}" for line in conflict_only[:80]],
        "",
        "This report is NOT a merged 2.0 build, a runtime acceptance, or a device test.",
        "No source merge, public binary upload, Android action, or release is executed.",
    ]
    pathlib.Path(args.report).write_text("\n".join(report) + "\n")
    if missing:
        raise RuntimeError("Missing protected Custom paths: " + ", ".join(missing))
    if actual != "2.0.0":
        raise RuntimeError(f"Expected patcher dependency 2.0.0 for preflight, got {actual}")
    if tree.returncode not in (0, 1):
        raise RuntimeError("merge-tree execution failed: " + tree.stderr.strip())
    print(f"Preflight report ready, overlaps={len(overlap)}, explicit_conflicts={len(conflict_only)}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
