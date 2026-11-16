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

## Supported build-code visibility and qualified searches

The Custom supported-app overview, expanded stable/experimental version chips,
patched-app source details, and read-only installed-device detail now print **every
catalog-required Android build code** for that version in stable numeric order.
A version with no build-code restrictions retains its previous label. The
existing source-specific stable/experimental division and confirmed update
counts are unchanged; a version name alone is not claimed to match every APK
build. Long chip labels can reveal their full contents via the existing tooltip.

Optional web-search actions in expanded source and device details now use the
same per-version, ABI-aware and form-factor-aware query builder as the main
supported-app catalog. If patch metadata supplies just one build code but no
ABI mapping, that exact code is kept; ambiguous unqualified multiple build
codes are never reduced to an invented primary build. Experimental web lookup
remains explicitly optional and never changes stable update eligibility,
downloads, installed APK metadata, signer or ownership facts.

Native positive/negative tests exercise unqualified/single/multiple build
metadata, deterministic formatting, stable-versus-experimental isolation,
duplicate experimental suggestions, and version/build/form-factor query
construction. No user device, local file, keystore, private source or public
binary is modified. Source-only Draft, awaiting exact public 3-OS and JAR CI;
not an owner-tested new version.

## 2.2 appearance outlines: preserved Custom navigation and semantic indicators

The official Desktop 2.2.0 optional *Outlines* control has been adapted to the
existing Custom **Appearance tab in its established Settings modal**, with
persistent `AppConfig.useOutlines`, root Compose state, English/German text and
search/deep-link targeting. The default remains `true` so existing user
configurations retain their current look without migration or value loss.
Turning it off is purely visual; it changes no APK, app version, patch source,
trust, signer, ADB, owner or installation operation.

Shared design-system `MorpheTheme.outlines`, `outlineStroke` and
`Modifier.outlineBorder` drive the live appearance for Custom application
cards (monochrome and gradient), buttons, badges, interactive chips, avatars,
device controls, navigation pills, dropdowns, text/search fields, panels,
tooltips and color-picker decoration. Critical semantic indicators override
the decorative preference: explicit OUTLINED/DANGER actions, text input error
and focus states, expanded dropdowns, active navigation and the device-offline
retry indicator retain their outlines. Error bars, selected swatch rings and
existing confirmations remain visible. Default normal borders and the
unchanged modal close controls are retained when enabled.

No wholesale upstream settings-page route was copied over the private Custom
modal: the existing search, explicit dismiss button, ADB identity, sources and
signing panels are preserved. Native tests cover absence/default-on schema
migration, persisted default-off settings round-trip, static searchable
localized outlines terms, and positive/negative mandatory-border policy.
Generated resource keys and complete Gradle/JAR qualification are required
on the **exact public SHA**. This is a source-only Draft, not a 2.2 release or
Windows GUI owner acceptance.

## 2026-11-16 upstream application names and owner-gated public Windows download

The official Morphe Desktop 2.3.0-dev line introduced form-factor display and,
after dev.1, an upstream repair for applications whose manifest label is an
Android `@string/...` resource. This Draft imports **only that focused app-name
repair** from the public upstream commit
`dc09eb9e0f71dfa156ada236d00d1a4d60ad29c9`, not an unreviewed wholesale
2.3 development update. A plain APK's `resources.arsc` and an APKM/APKS/XAPK
bundle's nested base.apk table can now resolve the actual app display name
without requiring a patch bundle. Existing public bundle base-selection
logic, exact-version update rules, patch identity and signer guards are not
replaced. The nested resource table has a 32-MiB **decompressed** read cap;
missing or corrupted tables retain original manifest inspection safely and
do **not** invent a friendly name. Synthetic native regressions exercise
literal names, referenced names, APK and bundled base.apk lookup, absent and
malformed tables and package identity preservation. No APK/device/user file
was touched during source development.

The official Patches stable channel is still 2.2.0 as checked on Nov 16,
while 2.3.0-dev.9 is experimental (YouTube downloads and debug changes).
Its existence must not be advertised as a stable application update unless
an actually configured, compatible patch source and its app-specific supported
versions prove it. Desktop 2.3.0-dev.1 form-factor badges and history
migration are **not** silently enabled in this build.

**A complete owner-gated release route is staged, not activated.** The new
`.github/workflows/public_preview_delivery.yml` is restricted to the
public repository and only a same-repo candidate PR, plus a deliberate
`pull_request labeled` event with the exact label
`morphe-public-preview-approved` applied by the GitHub repository owner.
Source synchronizations, regular CI, unrelated labels and chat continuations
cannot trigger any distribution. It verifies the *live exact PR HEAD* against
the approved event SHA, checks all **four** completed public CI jobs on that
SHA (Ubuntu/Windows/macOS native + JAR integrity), re-verifies the full pinned
source manifest and Compose resources, independently rebuilds Windows with
native tests, checks the single executable JAR and packaged ZIP entries,
creates a complete Java-21-compatible Windows launcher ZIP with LICENSE,
NOTICE, README, provenance and SHA-256, and then downloads the *actual*
published GitHub Releases asset to independently verify exact ZIP bytes and
checksum manifest. It publishes only a uniquely SHA-named **public prerelease
preview** and refuses to overwrite any existing release. Existing public
beta.4 and private rollback artifacts are **not removed**. The 2.2 *core*
preview is explicitly not advertised as the completely ported private app,
and it may continue showing internal version 2.1.1 until the complete 2.2 UI
and private-source parity are separately qualified.

The workflow itself, its release contract tests and verifier are pinned by
the public-source manifest; Ubuntu public CI exercises the negative/positive
source contract. Until a new exact public CI finishes successfully **and the
owner explicitly authorizes the preview-release label event**, no new binary
is uploaded or linked as a real download. No private GitHub Actions runner,
private repository checkout, private credential, key, device access, merge,
paid resource or user-file mutation occurs. The public Draft remains separate
from protected public main and published beta.4.

## 2026-11-16 Custom distribution identity and truthful update diagnostics

An owner Windows smoke screenshot of the *published, SHA-verified* public
`custom-v2.2-core-preview-970ddcf0b271` exposed an actual production/UI
integration defect: its generated version and executable JAR still identified
as upstream `2.1.1`, and the inherited `UpdateChecker` compared that number
to **MorpheApp/morphe-desktop**, thus misleadingly offering the upstream
`2.2.0` download in a Custom 2.2 core preview. The upstream changelog button
was similarly scoped to a different product. This is a **new, unpublished**
public-source correction, not a retroactive change to already released bytes.

- The **distribution** version now uses the precise prerelease identity
  `2.2.0-core-preview.2` throughout Gradle, embedded CLI resources, JAR
  metadata and app UI. It explicitly does NOT claim full private canonical
  2.2 feature parity or silently promote the Core preview to official stable.
- `UpdateChecker` now considers **only** properly tagged, non-draft releases
  from `lxsdd/morphe-desktop-public`, with a single verified-shape ZIP asset
  hosted on the exact same repository/tag. Stable preference never receives
  prereleases; existing preview users can receive newer previews and stable
  successors. Unknown tag shapes, wrong hosts, draft and malformed API data,
  missing ZIPs and offline checks fail closed without inventing an update.
  The Custom release notes button opens the exact matching GitHub release,
  while `Herunterladen` opens its actual Custom ZIP; neither opens a different
  official Morphe Desktop installation or changelog. User-selected update
  channel preferences remain unchanged, default preview detection includes
  `-core-preview`, and existing per-version suppression remains exact.
- The owner-gated public Windows preview delivery workflow now reads the
  running distribution version **from the immutable source `gradle.properties`**
  when constructing its unique SHA-tagged release, title and filename;
  existing release tags/assets cannot be overwritten. The 404-safe tag
  preflight, complete exact-SHA public four-job CI contract, owner-label-only
  activation, single executable Windows ZIP and real asset readback all stay
  mandatory. Previous 2.2-Core preview and beta.4 remain usable fallbacks.
- The patched-app overview now names which saved records have incomplete
  app/source checks and offers an explicit **recheck** through the existing
  source refresh callback. It does not claim a saved app to be fully current
  merely because the confirmed-update count is zero. An available newer
  experimental app version is labelled **Experimental app update** instead
  of being visually mistaken for a stable patch target. Existing per-source
  signer, APK version/build-code, package identity, device boundary, and
  change-log provenance gates remain authoritative; no new sources are
  auto-enabled or trusted and no user files/devices are modified by source
  staging.
- Native synthetic tests cover custom version ordering (including historic
  public preview/beta tags), stable/experimental isolation, spoofed ZIP URLs,
  absent/duplicated assets, invalid metadata, exact release URL and snapshot
  versions, plus positive/negative experimental app badges and named unknown
  source checks. Public CI independently pins the full executable source,
  tests, resources and owner release workflow using SHA-identical manifest.

Current official Morphe Patches stable release reached **v2.3.0** on Nov 16,
with Android app-specific target compatibility distinct from a source release
version. Existing patch source configuration determines whether the new bundle
is actually loaded and whether the user's recorded per-app patches have
relevant supported updates. A public repository build cannot attest to the
owner's complete configured source list, its persisted trust, or their
private device/patch receipt history: unresolved entries must remain unknown.

This is a **Draft-only source block** until a new exact-SHA three-OS CI and
independent JAR gate succeed. No new public ZIP, private CI, merge, tag,
release or device action is authorized by implementing these corrections.

## 2026-11-16 owner GUI observation: bounded scrolling and actual patch eligibility

The owner's Windows screenshot of the 2.2-core preview exposed scrollable
history rows painting into the fixed toolbar and stale list offsets when the
filter/search/sort state changes. The fix adds `Modifier.clipToBounds()` to
both supported-app and patched-history lazy list viewports, and resets each
list's scroll index when the search, filter or sort mode changes. It does not
alter file, APK, patch, device, signer, source or update eligibility state.
Five source guard regressions plus the exact public native build are required.

An independent audit of official Morphe Patches `v2.3.0` (released on
2026-11-16 13:10:51 UTC) shows the newest supported targets per *official*
app-specific compatibility catalog:
- Reddit: newest stable `2026.40.0`; experimental `2026.46.0`.
  Owner's `2026.45.0 → 2026.46.0` therefore remains **experimental**.
- YouTube: newest stable `21.30.216`; experimental `21.45.82`,
  matching the owner's installed version. This does not preclude new
  **patch features** in the just-released bundle.
- YouTube Music: newest stable `9.30.53` (matches owner version);
  experimental `9.46.51`. New patches exist in the bundle.

The other nine apps in the owner's twelve-item history (Amazon Shopping,
CapCut, Facebook, Flightradar24, Kleinanzeigen, MEGA, Messenger, SoundCloud,
Telegram Web) have **no app-specific target in this official three-app
catalog**. Their actual status depends on configured *third-party* patch
sources and exact released bundle metadata, neither available in this audit.
It is not safe to declare these nine apps up-to-date or unsupported
globally. This is a source-level audit and must not be represented as a
successful live scan of the user's local patch source configuration.

The public Draft already has independent source changes for Custom-only
application release identity, the new `2.2.0-core-preview.2` version,
Custom release URLs, experiment-specific app-update badges, and named
incomplete checks with retry. This block leaves them intact. The previous
public ZIP remains at its original older SHA; no release, merge or private
runner is triggered by this source development.

