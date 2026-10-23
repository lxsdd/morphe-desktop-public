# Pixel 9 still sees Amazon in Google Play after installer attribution — 2026-10-23

## Verified live result

The user manually ran the guarded Pixel 9 `pm set-installer` experiment. It changed
`InstallerOfRecord` from `null` to `com.android.shell`, but left
`UpdateOwner` unset. **Google Play still offers the Amazon update.** Thus
installer-of-record equality with Pixel 6a is insufficient. Do not repeat
`set-installer` or claim the reported bug fixed.

Both Pixels run Android 17 (SDK 37), Play Store 53.6.15-34, and Amazon
32.18.0.100 / versionCode 1243240206. Pixel 6a has update owner
`com.android.shell` and no Play offer; Pixel 9 has no shown update owner
and still has an offer. This is a correlation only. Android update ownership
controls update installation consent and is not documented to hide Play offers.

## Next safe discriminator

`tools/Compare-AmazonInstalledApks.ps1` hashes the installed `base.apk` of
each connected Pixel directly with Android's `sha256sum` over already paired
Wireless ADB. It also reports split-APK counts. It does not pull APKs, print
device serials/paths, or modify either phone. It works with one connected Pixel,
but can only calculate `BaseApkIdentisch` if both are simultaneously connected.

- If hashes differ: same versionName/versionCode concealed different APK bytes.
  Compare the patch selection and signing identity next.
- If hashes match: the base APK bytes are identical, so inspect Play account/
  library association and package update ownership rather than assuming a
  different Amazon patch build.
- Matching base hashes do **not** prove installed split APKs, Play account state,
  or per-device Play update eligibility are identical.

The official, opt-in universal `Disable Play Store updates` patch remains the
direct versionCode-based mitigation. Do not install it on user devices until
Amazon compatibility, signing continuity, rollback risk and user consent are
addressed. No uninstall, app-data clear, Play Store data clear, merge or release
is authorized by this diagnostic.
