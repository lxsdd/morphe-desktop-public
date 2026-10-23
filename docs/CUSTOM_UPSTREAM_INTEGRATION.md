# Morphe Desktop Custom: authoritative source and upstream integration

## Status (2026-10-23)

| Role | Repository/ref | Meaning |
| --- | --- | --- |
| Official upstream | `MorpheApp/morphe-desktop` `dev` | Official input; at audit time: `69ee025a40a9c180b4cd59717fd30b37e1295880`, v1.19.0 |
| Fork default branch | `lxsdd/morphe-desktop-public` `main` | Protected 1.18.1 starting point; **not** the current 1.19 Custom application |
| Current Custom source | `feature/device-status-sorting-and-refresh-feedback-20261023` plus this review branch | Unmerged, tested v1.19.0 source candidate |
| Private downstream | `lxsdd/morphe-desktop-custom` `main` | Private 1.18.1 rollback and earlier integration automation; **not** the v1.19 Custom authoritative head |

The present source candidate descends from the precise official v1.19.0 commit above. At the time of this audit, official `main` and `dev` point to that same commit. This statement is dated; use the live CI preflight before subsequent integrations.

## Decisions that prevent drift

1. **One review integration head.** The top of the sequential source PR stack contains the accumulated v1.19.0 changes. Older stacked source PRs are ancestry/history, not individually releasable builds. Public delivery PRs produce temporary unsigned desktop-only previews; they must never be used as the basis for an upstream merge.
2. **Do not conflate the two main branches.** The protected public `main` remains at 1.18.1 until explicitly approved promotion of a consolidated, fully tested source candidate. Keep the private 1.18.1 rollback intact until separate authorization to migrate it. Never silently promote on a test pass.
3. **Official changes are input, not authoritative replacements for Custom device safety.** Merge official `dev` into a disposable review candidate descended from the currently approved Custom source. Preserve exact-serial ADB operation-target rules, local/installed APK hash checks, signer/owner validation, device-to-APK import provenance, patched-app metadata history, link routing restrictions, and read-only inventory fail-closed semantics.
4. **Do not auto-accept even a clean merge.** A Git merge without conflicts may still break behavior. The next upstream version may change package APIs, Compose layouts, patch metadata schema, source resolution, Gradle versions, or device installation semantics.
5. **Zero distribution during integration.** Run repository-only synthetic tests, 3-OS Gradle builds/tests, archive inspection and (when feasible) actual Windows read-only UI acceptance. Any installation/patching on Android hardware requires a distinct explicit user action.
6. **No shadow release.** No unapproved tags, merges, GitHub Releases, public APKs or private signing materials. A successful preview is not the final Custom release or a substitute for Windows acceptance.

## For every subsequent official release / dev commit

1. Pin the official input to a **40-character SHA** and record the previously integrated official baseline. Fetch public official `dev` and verify that the baseline remains in its ancestry.
2. Run `Custom Upstream Compatibility (read-only preflight)`. The workflow reads official `dev`, prints source overlap, attempts an ephemeral, **local only** Git merge and, if there are new upstream commits and no conflicts, runs synthetic tests on Ubuntu, Windows and macOS. It cannot push or publish an artifact.
3. Treat merge conflicts as review gates. Review especially files underneath `src/main/kotlin/app/morphe/engine/`, `src/main/kotlin/app/morphe/gui/`, resource strings, Gradle/toolchain, and workflows. Review any same-path overlap even if Git merged cleanly.
4. Prepare a separate upstream-integration PR with the exact old and new baseline SHAs, review of divergent paths and the test matrix. Reuse the existing private upstream-sync mechanisms for provenance checks and deliberate promotion; do not duplicate the private distribution policy inside the public fork.
5. Maintain safety and end-to-end parity gates before promotion: package/signer checks, split/base APK handling, immutable target serial, two-device switch and installation identity, source/version separation, patch-review status, and no stale inventory during asynchronous scan.
6. Keep the merged candidate in a draft until the end-to-end Windows/Android acceptance that the user authorizes. Upgrade neither protected public main nor private main automatically. Close/retire obsolete temporary delivery PRs after validation to reduce review clutter.

## Current limitations / readiness gates

- The public source stack is deep and not yet consolidated into one reviewed upgrade head targeting public `main`. The current protected public main remains 1.18.1.
- The private `upstream_sync.yml` is robust but tracks **private main's** last integrated 1.18.1 baseline; it does not automatically track the new unmerged public v1.19.0 source. Reconciling those two repositories is an explicit migration action, not a simple upstream version bump.
- The current Custom additions modify upstream application files directly (Home, result, quick patch and ADB integration). They are not independent plugins with a versioned extension API. Thus future official refactors may need explicit adaptation even after a conflict-free merge.
- Three-OS synthetic CI and unsigned preview qualification establish compile/test health, **not** a complete physical-device end-to-end release gate.
- The direct device-to-patch input/UI path and cross-device behavior have not been finally signed off on physical Windows/Android setups.

## Approved UI terminology

Use **Technical priority** for the mechanical sort order (highest-first / lowest-first); use **Needs attention** for confirmed actionable/review-required states. A signer mismatch requires **review**, not an unqualified assertion of a security incident. Unknown install/patch state must not be shown as proven up to date.
