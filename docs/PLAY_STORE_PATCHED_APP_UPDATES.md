# Patched apps and Google Play updates — reproducible failure / supported guarantees

## User-reported reproduction (2026-10-23)

The patched Amazon Shopping app remains in Google Play's update queue. Google Play starts the update attempt and eventually fails. The user's Pixel 6a previously showed a locally patched **v32.18.0.100** with source `rushiranpise/morphe-patches v1.23.0` and exact installed APK-build proof. **Local APK equality is not evidence of Play update suppression.** No unattended writes to Pixel 6a or Pixel 9 are authorized.

## Platform semantics (official references)

- Google Play documents the app's **application ID**, app-library association, versionCode, signing certificate and user/device availability as inputs to update eligibility. Installer-of-record spoofing does **not** provide an API contract that removes the update offer. See https://developer.android.com/google/play/app-updates
- Android 14+ **update ownership** is a distinct PackageInstaller capability: an initial installer with permission may request ownership, and other installers need user approval for certain updates. It is **not** a promise that Google Play hides the app from its update list. See https://source.android.com/docs/setup/create/app-ownership
- Android enforces signing certificate compatibility for update installation. A Play-signed upstream update cannot replace a differently signed Morphe build using an ordinary same-package update. The exact failure code on this Pixel has **not** yet been observed or recorded. See https://developer.android.com/studio/publish/app-signing
- Google Play supports turning off automatic updates for **one app** in its own settings. The user does not want a recurring manual workaround as the project solution. There is no supported general unprivileged ADB API for Morphe Desktop to change Google's per-app Play preference. See https://support.google.com/googleplay/answer/113412?hl=de

## Source defect fixed by this PR

The GUI used `AdbManager.installApk` with a fabricated installer package such as `com.amazon.venezia`. On a generic error, it retried **without** `-i` and treated the unattributed installation as fully successful. Separately, the shared `AdbApkInstaller` contained the same legacy fallback. That silent downgrade is now prohibited; both paths report installer attribution rejection rather than asserting a protection that was not applied.

A successful attributed install still **does not** certify suppression of Play's update offers. Both Quick and Expert completion views explicitly disclose that. The UI must never display a green `Play-protected` badge simply because a package was installed, signed or assigned an installer source.

## Next acceptance gate — read-only evidence before any Android mutation

On the affected device only when explicitly user-authorized, inspect:
- exact current ADB serial and Amazon package name (no copy of serial to public GitHub);
- `adb -s SERIAL shell pm list packages -i com.amazon.mShop.android.shopping` (installer of record);
- `adb -s SERIAL shell dumpsys package com.amazon.mShop.android.shopping` (target updateOwnerPackageName, versionCode/name, signing identity);
- the **actual Play error** from the user-visible Play UI or a bounded, user-authorized diagnostic, not an attempted replacement.

Keep the Play app-library association, visible offer, automatic update attempt, update-owner proof and Android install failure as **five separate signals**. Do not infer one from another or claim a successful fix without confirming the user-visible Play behavior.

## Existing official patch: the direct opt-in solution

The stable, official **Morphe Patches v1.48.0** contains the universal **`Disable Play Store updates`** patch in
`patches/src/main/kotlin/app/morphe/patches/all/misc/updates/DisablePlayStoreUpdatesPatch.kt`
(verified against tag `v1.48.0`, not an assumption from current dev branch).

It is **disabled by default**. On explicit selection it updates the output APK's manifest `android:versionCode` to `Int.MAX_VALUE` (`2147483647`). The accompanying bytecode extension attempts to return the original versionCode to app-internal PackageInfo lookups. Because Play treats an app as outdated by comparing versionCode, this is a direct mechanism for suppressing the user-reported repeated update offer and failed install attempts. Unlike `-i` attribution or update ownership, it targets the Play version comparison itself. It is not a guarantee against all Play maintenance or unusual device/store behavior.

The Custom patch-selection UI now exposes this **only when** a bundle named `Morphe Patches` contains that exact universal patch. It offers an explicit, persistent selection; the user must still inspect/apply the patch and choose installation on a device separately. Amazon-specific patches from `rushiranpise/morphe-patches` can be selected in the same multi-source patch workflow when loaded, but actual cross-bundle application and Play behavior are **not yet physically qualified**.

**Tradeoffs:** the patch itself warns of unpredictable app issues, does not work with root mounting, and makes an ordinary later in-place APK with a lower versionCode a *downgrade* even if its versionName is newer. Future repatches should retain this patch, preserve signing identity and check installer behavior. Removing the max-versionCode patch might require uninstall/reinstall with app-data loss; do not promise automatic rollback or apply this broadly without consent. See https://github.com/MorpheApp/morphe-patches/tree/v1.48.0 and upstream implementation.

## Choosing an achievable guarantee

- With **unchanged application ID** and a Play-associated app, we cannot promise that the Play Store will never **offer** an update. Update ownership can constrain installers, not guarantee offer disappearance.
- A **distinct application ID** would separate the patched clone from the Play-listed original but risks broken logins, links, app integrations and shared data and is not universally applicable. This is a separate opt-in feature requiring feasibility tests, not a safe automatic migration for Amazon Shopping.
- Per-app **automatic updates off in Play** mitigates automatic attempts while allowing manual update offers. It is a user preference, not a programmable Morphe guarantee, and Google describes limited critical-update exceptions.

Do not implement ad-hoc versionCode inflation, Play Store data clearing, root mutations, uninstall/reinstall, app renaming, or fake package owners on the user's devices as an unreviewed workaround. This project must measure actual outcomes and be explicit about limits.
