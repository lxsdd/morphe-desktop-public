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

## Confirmed same-codebase regression in the GUI install path

The previous **private Custom 1.18.1** version called the shared `AdbApkInstaller` from `AdbManager.installApk`. It detects `pm help --update-ownership`, probes exact-package update ownership before mutating anything and uses `pm install --update-ownership` when supported. The public **Custom 1.19** GUI instead ran a direct `adb install -i <fabricated store>` and skipped those safeguards. This is a real, source-level regression independent of whether Google Play was visibly offering updates.

The affected GUI path now calls the **same shared Update Ownership-aware backend** again and does **not** spoof a store package by default. The `AdbApkInstaller` still refuses to overwrite an app on a capable Android device if its update owner is absent/foreign; this is fail-closed behavior, not a command to automatically uninstall apps. Exact on-device owner evidence and end-to-end Play behavior still require authorized hardware validation.

## Additional opt-in official patch

The enabled `rushiranpise/morphe-patches v1.23.0` metadata contains eight Amazon Shopping-specific patches at `32.18.0.100` (`versionCode=1243240206`), **none of which disables Play updates**. Official `MorpheApp/morphe-patches v1.48.0` separately includes an *optional, disabled-by-default* universal **Disable Play Store updates** patch that sets manifest `versionCode=2147483647`. Its own warning notes possible application incompatibilities and non-working root-mounted installs. This can inhibit versionCode-based Google Play updates, but does **not** certify a complete solution on this Amazon version.

The patch selection screen now exposes this **opt-in** explicitly for Amazon when the loaded sources contain that exact universal metadata. If it is missing, it clearly states so rather than pretending Amazon's own eight patches include it. It must not be auto-enabled because extreme manifest versionCodes can complicate later updates/downgrades. The device detail view also displays the actually observed versionCode alongside owner info; those are independent read-only facts, not proof of Play detachment.

### Before-versus-after verification

The user's older patched Amazon build **did not cause failed Play update attempts**. The newer build made 2026-10-23 **does**. The old APK's manifest versionCode, selected patch set and installed update owner have not yet been recovered. Therefore do not claim the precise runtime root cause is proven, despite identifying the installer backend regression and absence of any Amazon-specific Play suppression patch in Rushiranpise's metadata. Compare the actual old and new APK files (if retained) and the device's current read-only versionCode/owner before concluding which mechanism changed.

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
