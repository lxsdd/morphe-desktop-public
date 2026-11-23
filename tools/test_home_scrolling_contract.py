"""Negative source guards for the fixed toolbar / scroll viewport regression.

The native Gradle build separately compiles both composables. These guards stop
accidental removal of the scroll boundary and reset when changing list filters.
No GUI, physical device, user data, network or filesystem outside source reads.
"""
from pathlib import Path
import unittest

ROOT = Path("src/main/kotlin/app/morphe/gui/ui/screens/home/components")
SUPPORTED = ROOT / "SupportedAppsListPane.kt"
YOURS = ROOT / "YourAppsPane.kt"
DEVICE = ROOT / "DeviceInventoryPane.kt"
SOURCE_SHEET = Path("src/main/kotlin/app/morphe/gui/ui/components/SourceManagementSheet.kt")
STRINGS = Path("src/main/composeResources/values/strings.xml")
STRINGS_DE = Path("src/main/composeResources/values-de-rDE/strings.xml")


class HomeScrollingContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.supported = SUPPORTED.read_text(encoding="utf-8")
        cls.yours = YOURS.read_text(encoding="utf-8")
        cls.device = DEVICE.read_text(encoding="utf-8")
        cls.source_sheet = SOURCE_SHEET.read_text(encoding="utf-8")
        cls.strings = STRINGS.read_text(encoding="utf-8")
        cls.strings_de = STRINGS_DE.read_text(encoding="utf-8")

    def test_all_apps_list_is_bounded_and_clipped(self):
        self.assertIn(".heightIn(", self.supported)
        self.assertIn(".clipToBounds(),", self.supported)
        self.assertIn("LazyColumn(", self.supported)
        self.assertLess(
            self.supported.index(".clipToBounds(),"),
            self.supported.index("LazyColumn("),
        )

    def test_your_apps_list_is_bounded_and_clipped(self):
        self.assertIn("paneMaxHeight - headerSearchAllowance", self.yours)
        self.assertIn(".clipToBounds(),", self.yours)
        self.assertLess(
            self.yours.index(".clipToBounds(),"),
            self.yours.index("LazyColumn("),
        )

    def test_all_apps_resets_when_search_or_sort_changes(self):
        self.assertIn("LaunchedEffect(filter, searchQuery, sortMode)", self.supported)
        self.assertIn("listState.scrollToItem(0)", self.supported)

    def test_your_apps_resets_when_category_changes(self):
        self.assertIn("scrollResetKey = Triple(filter, searchQuery, sortMode)", self.supported)
        self.assertIn("LaunchedEffect(scrollResetKey)", self.yours)
        self.assertIn("listState.scrollToItem(0)", self.yours)

    def test_device_inventory_resets_on_filter_search_sort_and_diagnostics(self):
        self.assertIn("LaunchedEffect(selectedDeviceSerial, filter, sort, searchQuery, diagnosticsVisible)", self.device)
        self.assertIn("listState.scrollToItem(0)", self.device)
        self.assertIn(".clipToBounds())", self.device)
        self.assertLess(self.device.index(".clipToBounds())"), self.device.index("LazyColumn("))

    def test_device_channel_and_signer_explanations_are_wired(self):
        self.assertIn("latestCompatibleDeviceAppOffer(catalogApp, installedVersion)", self.device)
        self.assertIn("targetOffer?.channel?.takeIf { newerVersion }", self.device)
        self.assertIn("device_overview_experimental_target_hint", self.device)
        self.assertIn("device_details_find_experimental_version", self.device)
        self.assertIn("device_overview_signer_review_hint", self.device)
        self.assertNotIn("PatchEngine.patch(", self.device)

    def test_incomplete_history_reasons_are_fully_readable_not_ellipsized(self):
        banner = self.supported.split("if (!isLoading && uncheckedAppCount > 0) {", 1)[1]
        banner = banner.split("val searchVisible = true", 1)[0]
        self.assertIn('Res.string.home_update_checks_incomplete_intro', banner)
        self.assertIn('uncheckedDescriptions.forEach { description ->', banner)
        self.assertIn('text = "• $description"', banner)
        self.assertIn("softWrap = true", banner)
        self.assertIn(".verticalScroll(uncheckedScrollState)", banner)
        self.assertIn("rememberScrollbarAdapter(uncheckedScrollState)", banner)
        self.assertIn("home_update_checks_count_only_confirmed", banner)
        self.assertIn("onClick = onRetry", banner)
        self.assertIn("onClick = onManageSources", banner)
        self.assertNotIn("maxLines = 3", banner)
        self.assertNotIn("overflow = TextOverflow.Ellipsis", banner)
        self.assertNotIn("uncheckedDescriptions.joinToString", banner)

    def test_expanded_reasons_preserve_bounded_app_lists(self):
        self.assertIn("val diagnosticHeightAllowance = if (!isLoading && uncheckedAppCount > 0) 170.dp else 0.dp", self.supported)
        self.assertEqual(2, self.supported.count("paneMaxHeight = paneMaxHeight - diagnosticHeightAllowance"))
        self.assertIn("150.dp + diagnosticHeightAllowance", self.supported)

    def test_unknown_source_channel_is_not_mislabeled_stable(self):
        badge = self.source_sheet.split("private fun ChannelBadge(", 1)[1].split(
            "/** Developer section", 1
        )[0]
        self.assertIn("Channel.STABLE -> stringResource(Res.string.version_label_stable)", badge)
        self.assertIn("Channel.PRERELEASE -> stringResource(Res.string.version_label_prerelease)", badge)
        self.assertIn("Channel.LOCAL -> stringResource(Res.string.version_label_local)", badge)
        self.assertIn("else -> stringResource(Res.string.version_label_unknown)", badge)
        self.assertNotIn("else -> stringResource(Res.string.version_label_stable)", badge)
        for resources in (self.strings, self.strings_de):
            for key in ("version_label_unknown", "home_update_checks_incomplete_intro",
                        "home_update_checks_count_only_confirmed"):
                self.assertIn('name="' + key + '"', resources)

    def test_scrolling_never_changes_update_eligibility(self):
        for name in ("hasConfirmedAppUpdate", "hasConfirmedPatchUpdate"):
            self.assertIn(name, self.supported)
        self.assertNotIn("clearUpdates", self.yours)
        self.assertNotIn("writeDevice", self.yours)


if __name__ == "__main__":
    unittest.main()
