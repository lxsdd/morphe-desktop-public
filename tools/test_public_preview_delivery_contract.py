"""Synthetic, fail-closed checks for owner-approved PUBLIC preview delivery."""
import pathlib
import unittest

WORKFLOW = pathlib.Path(".github/workflows/public_preview_delivery.yml")


class PublicPreviewDeliveryContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = WORKFLOW.read_text(encoding="utf-8")

    def test_no_automatic_release(self):
        self.assertIn("types: [labeled]", self.source)
        self.assertIn("github.event.action == 'labeled'", self.source)
        self.assertIn("github.event.label.name == 'morphe-public-preview-approved'", self.source)
        self.assertIn("github.actor == github.repository_owner", self.source)
        self.assertNotIn("workflow_dispatch:", self.source)

    def test_public_source_provenance_only(self):
        self.assertIn("github.repository == 'lxsdd/morphe-desktop-public'", self.source)
        self.assertIn("github.event.pull_request.head.repo.full_name == github.repository", self.source)
        self.assertIn("github.event.pull_request.base.ref == 'main'", self.source)
        self.assertIn("python tools/verify_public_beta_source_manifest.py", self.source)
        self.assertNotIn("morphe-desktop-custom", self.source)
        self.assertNotIn("actions/upload-artifact", self.source)

    def test_source_SHA_and_four_required_public_jobs(self):
        self.assertIn("ref: ${{ github.event.pull_request.head.sha }}", self.source)
        self.assertIn("head_sha=$sha", self.source)
        for name in (
            "Source and tests (ubuntu-latest)",
            "Source and tests (windows-latest)",
            "Source and tests (macos-latest)",
            "PR desktop JAR integrity",
        ):
            self.assertIn(name, self.source)
        self.assertIn("./gradlew build --no-daemon --console=plain", self.source)

    def test_expected_absent_preview_release_tag_does_not_raise_on_404(self):
        # Regression: gh api GET /releases/tags/<new tag> returns 404 before
        # the first publication and PowerShell -ErrorActionPreference Stop
        # can terminate the job even with stderr redirected to null.
        self.assertIn('gh api "repos/$repo/releases?per_page=100"', self.source)
        self.assertIn("releaseInventory | Where-Object", self.source)
        self.assertNotIn('gh api "repos/$repo/releases/tags/$tag"', self.source)

    def test_source_bound_custom_version_not_stale_upstream_update(self):
        # A complete Custom 2.2 beta must not identify itself as upstream 2.1.1.
        import re
        gradle = pathlib.Path("gradle.properties").read_text(encoding="utf-8")
        self.assertRegex(gradle, r"(?m)^version\s*=\s*\d+\.\d+\.\d+-(?:beta|rc)\.\d+$")
        checker = pathlib.Path("src/main/kotlin/app/morphe/engine/UpdateChecker.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn("api.github.com/repos/lxsdd/morphe-desktop-public/releases", checker)
        self.assertNotIn("MorpheApp/morphe-desktop/refs/heads", checker)
        self.assertIn("RELEASE_VERSION=$releaseVersion", self.source)
        self.assertIn('custom-v$releaseVersion-$short', self.source)
        self.assertNotIn('custom-v2.2-core-preview-$short', self.source)
        self.assertNotIn('The internal app version may still show 2.1.1', self.source)

    def test_patcher_250_real_experimental_source_is_immutable_and_not_an_app_update(self):
        catalog = pathlib.Path("gradle/libs.versions.toml").read_text(encoding="utf-8")
        workflow = pathlib.Path(".github/workflows/build_pull_request.yml").read_text(encoding="utf-8")
        test = pathlib.Path("src/test/kotlin/app/morphe/engine/OfficialStablePatchBundleSmokeTest.kt").read_text(encoding="utf-8")
        self.assertIn('morphe-patcher = "2.5.0"', catalog)
        self.assertIn("v2.4.0-dev.7/patches-2.4.0-dev.7.mpp", workflow)
        self.assertIn("f904d0cb74ca98562d4e0e4f2ab61d7d62d72b7504ef3bc0d1ae0309074b9056", workflow)
        self.assertIn("MORPHE_OFFICIAL_EXPERIMENTAL_PATCHES_TEST_FILE", workflow)
        self.assertIn("experimentalOfficialBundleLoadsWithPatcher250AndKeepsSigner", test)
        self.assertIn('assertEquals("2.5.0", PatcherCompatibility.currentPatcherVersion)', test)

    def test_upstream23_source_progress_is_really_wired_without_weaker_custom_guards(self):
        loader = pathlib.Path("src/main/kotlin/app/morphe/gui/util/EnabledSourcesLoader.kt").read_text(encoding="utf-8")
        vm = pathlib.Path("src/main/kotlin/app/morphe/gui/ui/screens/home/HomeViewModel.kt").read_text(encoding="utf-8")
        banner = pathlib.Path("src/main/kotlin/app/morphe/gui/ui/screens/home/components/HomeBanners.kt").read_text(encoding="utf-8")
        home = pathlib.Path("src/main/kotlin/app/morphe/gui/ui/screens/home/HomeScreen.kt").read_text(encoding="utf-8")
        state = pathlib.Path("src/main/kotlin/app/morphe/gui/ui/components/SourceUpdateBanner.kt").read_text(encoding="utf-8")
        self.assertIn("onSourceProgress: ((SourceLoadProgress) -> Unit)?", loader)
        self.assertIn("val done = completed.incrementAndGet()", loader)
        self.assertIn("val usableResolvedSources: List<ResolvedSource>", loader)
        self.assertIn("sourceReloadEpoch.incrementAndGet()", vm)
        self.assertIn("if (sourceReloadEpoch.get() != sourceEpoch) state", vm)
        self.assertIn("onSourceProgress = { event ->", vm)
        self.assertIn("sourceUpdateBanner = _uiState.value.sourceUpdateBanner.finished(", vm)
        self.assertIn("usableSources = usableResolved.size", vm)
        self.assertIn("failedSources = failedSourceIds.size", vm)
        self.assertIn("onlyLegacyPatcherFailures(", vm)
        self.assertIn("SourceUpdateBanner(", banner)
        self.assertIn("onDismissSourceUpdate = { viewModel.dismissSourceUpdateBanner() }", home)
        self.assertIn("usableSources == total && failedSources == 0", state)
        self.assertIn("MorpheBannerDismiss(onClick = onDismiss)", state)
        self.assertNotIn("PatchEngine.patch(", state)

    def test_desktop23_source_version_browser_is_wired_to_custom_safe_actions(self):
        model = pathlib.Path("src/main/kotlin/app/morphe/gui/ui/screens/patches/VersionBrowserModel.kt").read_text(encoding="utf-8")
        vm = pathlib.Path("src/main/kotlin/app/morphe/gui/ui/screens/patches/PatchesViewModel.kt").read_text(encoding="utf-8")
        ui = pathlib.Path("src/main/kotlin/app/morphe/gui/ui/screens/patches/PatchesScreen.kt").read_text(encoding="utf-8")
        browser = pathlib.Path("src/main/kotlin/app/morphe/gui/ui/screens/patches/components/patches/CustomSourceVersionBrowser.kt").read_text(encoding="utf-8")
        tests = pathlib.Path("src/test/kotlin/app/morphe/gui/ui/screens/patches/VersionBrowserModelTest.kt").read_text(encoding="utf-8")
        self.assertIn("internal fun visibleSourceReleases(", model)
        self.assertIn("state.stableReleases + state.devReleases", model)
        self.assertIn("state.isOffline", model)
        self.assertIn("FollowMode.PINNED -> preference.pinnedTag", model)
        self.assertIn("sourceReleaseIsInUse", model)
        self.assertIn("val releaseLoadEpoch = AtomicLong(0)", vm)
        self.assertIn("if (releaseLoadEpoch.get() != loadEpoch) return@launch", vm)
        self.assertIn("sourceId = activeSourceId", vm)
        self.assertIn("inUseTag = sourceInUseTag(", vm)
        self.assertIn("configRepository.setSourceVersionPref(activeSourceId, pref)", vm)
        self.assertIn("patchSourceManager.updateSource(", vm)
        self.assertIn("PatchCache.getCachedFile", vm)
        self.assertIn("CustomVersionRail(", ui)
        self.assertIn("CustomVersionDetail(", ui)
        self.assertEqual(1, ui.count("BottomActionBar("))
        self.assertNotIn("ChannelSelector(", ui)
        self.assertIn("viewModel::setVersionQuery", ui)
        self.assertIn("viewModel::selectRelease", ui)
        self.assertIn("viewModel.confirmSelection", ui)
        self.assertIn("viewModel.downloadPatches", ui)
        self.assertIn("viewModel.exportOptionsJson", ui)
        self.assertIn("OutlinedTextField(", browser)
        self.assertIn("onPreviewKeyEvent", browser)
        self.assertIn("stringResource(Res.string.custom_versions_offline_warning)", browser)
        self.assertIn("sourceReleaseIsInUse(state, release)", browser)
        self.assertIn("experimentalReleasesAreSearchableFromTheStableTab", tests)
        self.assertIn("missingPinnedReleaseIsNotSilentlyReplaced", tests)

    def test_beta10_zip_checksum_readback_precedes_one_time_retirement(self):
        self.assertIn("gh release create", self.source)
        self.assertIn("--prerelease", self.source)
        self.assertIn("gh release download", self.source)
        self.assertIn("Published ZIP bytes do not match", self.source)
        self.assertIn("Published checksum manifest mismatch", self.source)
        self.assertIn("if ($env:RELEASE_VERSION -eq '2.2.0-beta.10')", self.source)
        self.assertNotIn("if ($env:RELEASE_VERSION -eq '2.2.0-beta.9')", self.source)
        self.assertIn("gh release delete $item.Tag --repo $repo --cleanup-tag --yes", self.source)
        self.assertEqual(1, self.source.count("gh release delete $item.Tag"))
        self.assertLess(self.source.index('Published checksum manifest mismatch'),
                        self.source.index('gh release delete $item.Tag'))
        self.assertLess(self.source.index('PUBLIC_PREVIEW=PASS'),
                        self.source.index('gh release delete $item.Tag'))
        self.assertIn("PUBLIC_OLDER_RELEASE_RETIREMENT=PASS", self.source)
        self.assertNotIn("git push", self.source)
        self.assertNotIn("gh pr merge", self.source)

    def test_beta10_retirement_is_exact_beta9_only_and_fails_closed(self):
        self.assertIn("custom-v2.2.0-beta.9-1be9ee4280bc", self.source)
        self.assertIn("Id = 455839531", self.source)
        self.assertIn("Source = '1be9ee4280bc49892e3ed3d350e8a09c97bf63de'", self.source)
        self.assertIn("Digest = 'sha256:f64d6ac0536e8fcf5648216bd32697be8cde867a7afc78636985aba85b12ca34'", self.source)
        self.assertEqual(1, self.source.count("@{ Tag = 'custom-v2.2.0-beta."))
        self.assertIn("Unexpected total public release inventory", self.source)
        self.assertIn("Beta.10 owner label or PR SHA changed before retirement", self.source)
        self.assertIn("Verified beta.10 release missing or its exact source changed", self.source)
        self.assertIn("Newly published beta.10 asset digest differs", self.source)
        self.assertIn("Unexpected older full-beta release count", self.source)
        self.assertIn("-[0-9a-f]{12}$' -and", self.source)
        self.assertIn("$record[0].target_commitish -ne $item.Source", self.source)
        self.assertIn("digest differs; refuse retirement", self.source)
        self.assertIn("Latest verified beta.10 release not preserved", self.source)
        self.assertIn("github.actor == github.repository_owner", self.source)
        self.assertIn("gh release delete $item.Tag --repo $repo --cleanup-tag --yes", self.source)

    def test_public_ci_real_mpp_fixtures_remain_sha_pinned_and_do_not_bypass_removed_sources(self):
        # A retired 404 repository and a GitHub ToS-blocked repository cannot
        # be CI prerequisites; no unverified mirror or best-effort skip.
        workflow = pathlib.Path(".github/workflows/build_pull_request.yml").read_text(encoding="utf-8")
        self.assertNotIn("github.com/riky-dev/morphe-patches/releases/download/", workflow)
        self.assertNotIn("github.com/hoo-dles/morphe-patches/releases/download/", workflow)
        self.assertIn("sha256sum -c -", workflow)
        for approved in (
            "MORPHE_OFFICIAL_PATCHES_TEST_FILE",
            "MORPHE_THIRDPARTY_PATCHES_TEST_FILE",
            "MORPHE_OFFICIAL_LATEST_PATCHES_TEST_FILE",
            "MORPHE_PIKO_PATCHES_TEST_FILE",
            "MORPHE_FROGGO_PATCHES_TEST_FILE",
            "MORPHE_PATCHEDUP_PATCHES_TEST_FILE",
            "MORPHE_ALIEXPRESS_PATCHES_TEST_FILE",
        ):
            self.assertIn(approved, workflow)



    def test_monotonic_release_guard_is_executed_before_publication(self):
        self.assertIn("python tools/require_monotonic_public_beta.py", self.source)
        self.assertIn("Public prerelease version is not strictly newer", self.source)
        self.assertIn("PUBLIC_RELEASE_VERSION=PASS", pathlib.Path(
            "tools/require_monotonic_public_beta.py"
        ).read_text(encoding="utf-8"))
        self.assertIn("CURRENT_PATCHER_VERSION=2.5.0", self.source)
        self.assertNotIn("OFFICIAL_PATCHER_BASE=v2.4.0", self.source)

    def test_monotonic_release_guard_rejects_same_older_and_malformed(self):
        from require_monotonic_public_beta import require_newer_public_beta
        latest = [{"tag_name": "custom-v2.2.0-beta.6-ca06be0b78a0", "draft": False}]
        with self.assertRaisesRegex(ValueError, "strictly newer"):
            require_newer_public_beta("2.2.0-beta.6", latest)
        with self.assertRaisesRegex(ValueError, "strictly newer"):
            require_newer_public_beta("2.2.0-beta.5", latest)
        with self.assertRaisesRegex(ValueError, "Invalid full Custom version"):
            require_newer_public_beta("2.2.0", latest)
        with self.assertRaisesRegex(ValueError, "Ambiguous full Custom release tag"):
            require_newer_public_beta("2.2.0-beta.7", [
                {"tag_name": "custom-v2.2.0-beta.6-bad", "draft": False}
            ])
        with self.assertRaisesRegex(ValueError, "paginated"):
            require_newer_public_beta("2.2.0-beta.7", latest * 100)

    def test_monotonic_release_guard_accepts_new_beta_and_future_release_order(self):
        from require_monotonic_public_beta import require_newer_public_beta
        prior = [
            {"tag_name": "custom-v2.2.0-beta.6-81fff4b45b94", "draft": False},
            {"tag_name": "custom-v2.2.0-beta.6-ca06be0b78a0", "draft": False},
            {"tag_name": "custom-v2.2-core-preview-970ddcf0b271", "draft": False},
            {"tag_name": "custom-v2.2.0-rc.9-123456789abc", "draft": True},
        ]
        self.assertTrue(require_newer_public_beta("2.2.0-beta.7", prior))
        self.assertTrue(require_newer_public_beta("2.2.0-rc.1", prior))
        with self.assertRaisesRegex(ValueError, "strictly newer"):
            require_newer_public_beta("2.2.0-beta.99", [
                {"tag_name": "custom-v2.2.0-rc.1-123456789abc", "draft": False}
            ])
        self.assertTrue(require_newer_public_beta("2.2.1-beta.1", [
            {"tag_name": "custom-v2.2.0-rc.99-123456789abc", "draft": False}
        ]))

if __name__ == "__main__":
    unittest.main()
