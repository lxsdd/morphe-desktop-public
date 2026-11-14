# Morphe Desktop Custom 2.1.1-beta.3 — Unofficial public update

**Unofficial community-maintained derivative. Not associated with, endorsed by or
published on behalf of the original Morphe project.** The upstream name and
trademarks remain with their owners. See [NOTICE](../NOTICE) and
[LICENSE](../LICENSE), including GPLv3 Section 7 supplementary conditions.

## Scope

This public update contains the prior beta.2 legacy-MPP backward-compatibility
fix plus consistent historical app-name presentation and patch-scoped update
evidence across the Home UI. Source, resources
and regressions are integrity-pinned at this commit. Previously accepted Windows
UI behavior is not automatically accepted for this new binary. The
[`public_beta_source_manifest.tsv`](../tools/public_beta_source_manifest.tsv)
lists the exact Git blob hash of every relevant product/build/resource file.
GitHub Actions rejects any missing, extra or mismatched source file before
building. **No private repository documentation, credentials, certificates,
keystore contents, device identifiers, user files, private releases or
private GitHub Actions workflows are bundled.**

Older Patcher-1.x compiled MPPs are no longer rejected simply because the
runtime Patcher is 2.x. Read-only load tests cover published official,
riky-dev and rushiranpise MPPs. Future-engine, signing, source trust and
exact-device protections remain enabled.

**Update semantics:** "App updates" only means a newer app target actually
supported by an enabled patch source in the selected Stable/Dev channel, not
an arbitrary newer version appearing in a public APK store. "Patch updates"
requires newer, relevant changes scoped to that app and patch source, rather
than a repository-wide release number alone. Unknown or unavailable metadata
is presented as an incomplete check, never assumed to mean up-to-date.
Historical app titles use original package identity and trusted catalog names
without modifying the signed output, patch history or selected device.

It includes the multi-source patch workflow, exact-ADB-device safeguards,
installed APK inspection and guarded direct fresh installation, truthful
package/signer/update-owner checks, cached Android app names and detailed
source updates, unified responsive overlays, cancellable source deletion
confirmation and German/English consent and loss-risk warnings.

## Install and use

Requires a Java 21 or newer runtime. Download the Windows ZIP from the
[public releases](https://github.com/lxsdd/morphe-desktop-public/releases),
extract to a fresh directory and run `Start-Morphe-Windows.cmd`.
No Android APK, patch-bundle, signing key or third-party patch source is
included. You select and authorize any app/patch/device operation explicitly.

This is a **beta**, not a stable update or a claim of successful Android
deployment on arbitrary devices. Exact-commit Linux, Windows and macOS
source tests and integrity checks accompany the published release. The separately built public ZIP gets its own SHA-256 and package
verification. Legacy bundles are tested for loading, not for patching arbitrary
Android APKs. Never use a public binary without corresponding public source.

## Reproducibility and licensing

Build from the release's tagged public commit with JetBrains JDK 21:
`./gradlew build --no-daemon`. Verify the source manifest by running
`python tools/verify_public_beta_source_manifest.py`. The release
includes the GPLv3 `LICENSE`, additional `NOTICE`, a runnable desktop
JAR, launcher, exact Git source SHA and `SHA256SUMS.txt`. Third-party
dependencies and bundled fonts retain their independent licenses and
attributions. The modified version must not be presented as official Morphe.
