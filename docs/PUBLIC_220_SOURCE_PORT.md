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
