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


class HomeScrollingContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.supported = SUPPORTED.read_text(encoding="utf-8")
        cls.yours = YOURS.read_text(encoding="utf-8")

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

    def test_scrolling_never_changes_update_eligibility(self):
        for name in ("hasConfirmedAppUpdate", "hasConfirmedPatchUpdate"):
            self.assertIn(name, self.supported)
        self.assertNotIn("clearUpdates", self.yours)
        self.assertNotIn("writeDevice", self.yours)


if __name__ == "__main__":
    unittest.main()
