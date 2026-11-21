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

    def test_zip_checksum_readback_and_rollbacks_retained(self):
        self.assertIn("gh release create", self.source)
        self.assertIn("--prerelease", self.source)
        self.assertIn("gh release download", self.source)
        self.assertIn("Published ZIP bytes do not match", self.source)
        self.assertIn("Published checksum manifest mismatch", self.source)
        self.assertNotIn("gh release delete", self.source)
        self.assertNotIn("git push", self.source)
        self.assertNotIn("gh pr merge", self.source)


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



if __name__ == "__main__":
    unittest.main()
