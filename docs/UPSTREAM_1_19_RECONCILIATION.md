# Upstream 1.19.0 reconciliation in progress

**Not a merge-ready build. Do not promote, merge or distribute.**

Source: official Morphe Desktop v1.19.0 stable, commit `69ee025a40a9c180b4cd59717fd30b37e1295880`, measured against official 1.18.1 `e6c133010c866d56f074abea7bd0a3bf4f8f9a93`.

Staging source: sanitized private 1.18.1 branch `integration/sanitized-private-1-18-1-20261022`, which has not yet imported 34 private test-file changes.

This branch begins a **path-disjoint update only**: 243 upstream changed paths that did not overlap a modified path in the qualified private 1.18.1 baseline. This is not a semantic proof of compatibility.

**Unresolved conflicts:** 57 paths include 57 paths changed by both codebases. The moved ADB manager, shared patch engine and modified GUI/CLI consumers require manual code-level reconciliation and explicit fail-closed serial guards.

Private repo CI/release workflows are **not** imported; the public source-only workflow remains on this branch. No public artifacts, release or Android operations.

Required: finish private safety tests with anonymous fixtures; resolve all collisions and version pins; then run three-OS CI on the exact final candidate SHA. Pixel 9 stays read-only.
