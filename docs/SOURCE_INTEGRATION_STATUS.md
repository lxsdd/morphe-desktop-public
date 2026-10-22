# Morphe Desktop source integration (qualification pending)

This is an intentionally unmerged **source-only** staging branch for the public fork.

- Official original baseline: `MorpheApp/morphe-desktop` `e6c133010c866d56f074abea7bd0a3bf4f8f9a93` (1.18.1).
- The source snapshot is being reintroduced as new public commits, **not** by publishing the private downstream repository's Git history or releases.
- Staging is **incomplete**: not all private test fixtures have been safely transferred.
- Device test identifiers must be synthetic. Any private device identities remain excluded.
- CI builds are read-only and must not publish JARs, APKs, packages, releases, signing assets, or any other binary artifacts.
- Official 1.19 and Patcher 2.0 integration is a separate, unqualified engineering task.
- No automatic promotion to `main`; no physical Android device actions (Pixel 9 remains read-only).

Acceptance gates: complete audited sources/tests, deterministic matching build on Linux, Windows and macOS, exact-target/ADB fail-closed tests, migration and signing history checks, and explicit promotion decision.
