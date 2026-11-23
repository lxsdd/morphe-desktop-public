"""Release discovery guard: no auto-select, no unverified app updates or signer changes.

Native Kotlin release tests separately assert the semver/published-order behavior.
"""
from pathlib import Path
import unittest

BASE = Path("src/main/kotlin/app/morphe")
REPOSITORY = BASE / "engine/patches/PatchRepository.kt"
DISCOVERY = BASE / "engine/patches/SourceReleaseDiscovery.kt"
ENABLED = BASE / "gui/util/EnabledSourcesLoader.kt"
RESOLVER = BASE / "engine/patches/PatchResolver.kt"
HOME = BASE / "gui/ui/screens/home/HomeScreen.kt"
SHEET = BASE / "gui/ui/components/SourceManagementSheet.kt"
PAGE = BASE / "gui/ui/components/SourcesPage.kt"


class SourceDiscoveryProductionContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = {name: path.read_text(encoding="utf-8") for name, path in (
            ("repo", REPOSITORY), ("discovery", DISCOVERY), ("enabled", ENABLED),
            ("resolver", RESOLVER),
            ("home", HOME), ("sheet", SHEET), ("page", PAGE),
        )}

    def test_publisher_manifest_is_still_the_selected_bundle(self):
        self.assertIn("remoteSource.fetchLatestFromManifest(prerelease)", self.files["repo"])
        self.assertIn("highestPublishedBundleRelease(list, prerelease)", self.files["repo"])
        self.assertIn("suspend fun auditPublishedReleases()", self.files["repo"])
        self.assertIn("cachedReleases = fresh", self.files["repo"])
        self.assertIn("!draft && (assets.firstOrNull", self.files["discovery"])
        self.assertIn("it.isUsablePublishedBundleRelease()", self.files["discovery"])
        self.assertIn("fallbackPublishedBundleRelease(list, prerelease)", self.files["repo"])
        self.assertIn("releases.filter { it.isUsablePublishedBundleRelease()", self.files["repo"])
        self.assertIn("compareVersions(it.tagName, selectedTag) > 0", self.files["discovery"])

    def test_extra_tags_are_discovered_only_on_explicit_refresh(self):
        body = self.files["enabled"]
        self.assertIn("if (!forceRefresh || repo == null || selected.patchFile == null", body)
        self.assertIn("repo.auditPublishedReleases().getOrNull()", body)
        self.assertIn("additionalPublishedReleases(selected.resolvedVersion, liveReleases)", body)
        self.assertNotIn("downloadPatches(", body)
        self.assertNotIn("trustSigningKey(", body)
        self.assertNotIn("setSourceEnabled(", body)

    def test_all_real_source_management_ui_surfaces_receive_diagnostics(self):
        self.assertEqual(2, self.files["home"].count(
            "sourceAdditionalReleases = snapshot.sourceAdditionalReleaseMap()"))
        self.assertIn("sourceAdditionalReleases = sourceAdditionalReleases", self.files["sheet"])
        self.assertIn("additionalReleases = sourceAdditionalReleases[source.id]", self.files["sheet"])
        self.assertIn("sourceAdditionalReleases = sourceAdditionalReleases", self.files["page"])
        self.assertIn("source_additional_release_warning", self.files["sheet"])

    def test_pinned_version_cannot_silently_fall_back_to_another_bundle(self):
        self.assertIn("pref?.mode == FollowMode.PINNED && pref.pinnedTag.isNullOrBlank()",
                      self.files["enabled"])
        self.assertIn("strictPin = pref?.mode == FollowMode.PINNED", self.files["enabled"])
        self.assertNotIn("strictPin = false", self.files["enabled"])
        self.assertIn("val releases = releasesResult.getOrNull()?.filter", self.files["resolver"])
        self.assertIn("it.isUsablePublishedBundleRelease()", self.files["resolver"])
        self.assertIn("if (matched == null && strictPin)", self.files["resolver"])
        self.assertNotIn("release = matched ?: latestStable ?: releases.firstOrNull()",
                         self.files["resolver"])

    def test_unknown_and_older_source_versions_do_not_become_updates(self):
        self.assertIn("if (isNewerVersion(latest, current))", self.files["enabled"])
        self.assertNotIn("if (latest != current)", self.files["enabled"])


if __name__ == "__main__":
    unittest.main()
