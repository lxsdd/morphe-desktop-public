# Morphe Desktop 2.2.0 / Patcher 2.4.0 — public-source compatibility candidate

This **unreleased** public branch preserves the existing public Custom 2.1.1 beta.4 and its safety behavior. It does **not** declare a completed Desktop 2.2.0 migration, a Windows UI acceptance, or authorization to publish any executable.

## Fixed upstream pins

- Official Morphe Desktop v2.2.0 stable: `3c643a3edb1e6f05aac9b6d1005c38b197d399fc`
- Official Morphe Patcher v2.4.0 stable; aligned ARSCLib `50418a07a8`
- Public Custom base: `200e810bd5220ba292f85c9381781661320183b9`
- Only standard public GitHub-hosted runners are eligible. No private repository checkout and no GitHub Actions artifact uploads.

## Product changes from official, publicly licensed source

- 2.4.0 patcher engine and matching ARSCLib/Ktor pins with the required resource-table getter/writer changes and version/ABI/form-factor metadata model; preserve the installed-app label provenance and per-device safeguards already in public beta.4
- Upstream case-insensitive patch-option matching and index override with regression coverage
- Safe output app-folder names on Windows with invalid-character regressions
- OBB extraction and forwarding to PatcherConfig for supported split bundles; retain the existing Custom absolute/safe bundle-staging function and workspace cleanup guards
- Build-code- and form-factor-aware optional APK web search links; keep stable/experimental recommendation separation, Custom readable package names and existing app identity protections
- Upstream HTTP download stream lifecycle correction and snapshot metadata matching without weakening pinned patch bundle signer verification
- Positive/negative package and option regressions; existing signed official and legacy community MPP tests updated for Patcher 2.4.0, including future-Patcher rejection

## Security / release boundaries

Source is drawn **only from already-public Custom beta.4 and official Desktop v2.2.0**, with conservative public-only edits. There are no imported private keys, personal devices, decrypted user data, local machine paths, private release artifacts, or private repository tokens. The generated `tools/public_beta_source_manifest.tsv` pins all releasable source, resource, and build files to their exact Git blob SHAs. Public CI must pass on the **exact** public PR SHA before the candidate can be considered qualified. A passing public SHA is **not** proof that the different private PR #13 SHA passed.

No merge, release, APK build publication, device operation, private-account runner minutes, new cost or agreement is authorized by this branch. The full official 2.2.0 settings/navigation/icons and remaining GUI integration are still outstanding.

## 2.2 local icon UI subsection (same public candidate)

The official 2.2.0 launcher-resource renderer is wired to the existing Custom patched-history cards. Only already-existing local APK/APKM/APKS/XAPK paths are queried, asynchronously; missing or malformed resources retain the initials and existing Custom fills/names. ZIP resources are bounded to 16 MiB each, nested split APKs are streamed without writing temporary copies, and the in-memory cache is invalidated on file size/mtime changes. It performs **no ADB/device access, no disk icon cache and no private key/history writes**. Icon data is presentation-only, not APK/signature/ownership proof. Native negative tests cover missing, corrupt and changed archives. The full settings/navigation and device-icon port remains separately gated.

## 2.2 granular storage and cache subsection (same public Custom UI)

An integrated Storage section is added **inside the existing Custom System settings tab**; it does not replace private/custom navigation or require ADB. Four categories distinguish downloaded .mpp/.jar bundles, local logs, transient workspace files, and repository avatar cache. Bytes and file counts are collected off the UI thread. Per-category deletion is **disabled while patching** and requires an explicit user confirmation. The patch-download category uses the existing signer-pin-preserving deletion primitive and refreshes patch-source observers; avatar deletion clears avatar memory. No app icon data is persisted by the local icon renderer, so no misleading on-disk icon-cache category is presented.

User-facing storage strings are available in default English and German, with normal default-locale fallback for other translations. Native tests verify that downloaded package counts exclude signing.key, refused-signature metadata and other retained source preferences, and that confirmed downloaded-patch cleanup preserves these files. This is a source-level UI capability; no user file was deleted during development and Windows GUI owner acceptance is still outstanding.
