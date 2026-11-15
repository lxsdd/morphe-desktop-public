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

## 2.2 settings search/deep-link subsection

The established Custom Settings **modal** now has a bilingual, content-aware search in the left rail. It indexes only static localized UI labels and descriptions for 22 real controls/sections (not any PAT, signing password, keystore path or user/device data). All whitespace-separated terms must match. Selecting a result routes to the existing Appearance/Advanced/System tab and requests bring-into-view for the *actual* label/toggle/collapsible panel; guarded advanced panels expand but their values are **not** changed. Appearance controls hidden by monochrome and link controls hidden by their parent toggle route to the visible parent instead of silently toggling a preference. Normal navigation and prior explicit close controls remain intact.

Native positive/negative tests cover German accents, mixed-case multiword queries, cross-category search, blank/unknown terms, duplicate routes and dependent control targeting. These changes preserve existing SettingsDialogHost persistence and security, with no device API, write or release behavior changed. Public CI on the exact new source SHA must pass Windows/macOS/Linux and JAR integrity. Owner GUI interaction/visual acceptance remains unverified.

## 2.2 community-patch discovery and Appearance setting (opt-in)

An optional `More apps` card from official Desktop 2.2 is integrated into the existing Custom **ALL** supported-apps list and managed by a persisted Appearance toggle. It is **off by default** on migrated config and is never shown for Yours, Device, App Updates, Patch Updates, loading, errors, empty catalogs, or a nonblank search filter. It makes no claim that third-party apps/versions are supported or that an app update exists. It does not feed patch-source or update detection and never silently installs patches or touches user devices.

The official public external URL is `https://morphe-patches.software`. On first click, a bilingual risk notice explains independent-source security/compatibility and requires explicit confirmation before opening the browser. Acknowledgement is persisted only by that confirmation; cancellation makes no config change. The card/toggle share root Compose state and the existing ConfigRepository, preserving prior Custom settings and per-app selection. Static settings search now includes the new toggle. Positive/negative native tests check default-off schema migration, serialization round-trip and exclusion from update/device/filtered lists. The feature does not change the protected source's trust/verification or update-owner gates.

**Cost/licensing boundary:** the new files and translations are public-safe source, not a release or third-party patch endorsement; exact public CI must qualify 637/637 blob pins on Windows/macOS/Linux and the independent JAR job. The private product branch and public beta.4 remain untouched.

## 2.2 local launcher icon visibility and verified device inventory source

The existing 2.2 local APK icon renderer is now controllable with an Appearance toggle, persisted via the shared ConfigRepository and provided to all Compose cards via root state. Previous installations default to **visible**, matching the already-shipped public candidate. Disabling icons prevents local file-stat probes and extractions in the composable, shows the original initials, and changes neither patch installs nor package/signer ownership metadata. English/German localized labels and static settings-search routing are included; no sensitive data is indexed.

The Custom device inventory may reuse **only existing local patched history** launcher icons, and only when the selected installed package matches the recorded installed package, its version matches, an explicit 64-digit output SHA-256 exists, and the inventory's *exactBuildVerified* attestation is true. Unknown, stale, changed, renamed, other-device or unverified packages always use the initial, never a misleading historical icon. No ADB icon reads/pulls, device mutations, private keystores, persistent icon caches or new privileged calls are introduced.

Pure regressions cover positive exact-match, negative null/changed version/package/hash and unset attestation, unchanged package fallback, old config default, and serialization round-trip. This is a source-only Draft requiring full exact-commit Windows/Linux/macOS native tests and JAR validation, not owner hardware GUI acceptance or publication.
