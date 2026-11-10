"""Focused positive/negative regression fixtures for localized Compose resources."""
import pathlib
import tempfile
import unittest
import xml.etree.ElementTree as ET

from verify_compose_resource_keys import validate_locale_catalogs


class LocaleCatalogContractTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.directory = pathlib.Path(tmp.name)
        self.locale = self.directory / "values-fr-rFR"
        self.locale.mkdir()

    def write(self, filename, xml):
        (self.locale / filename).write_text(xml, encoding="utf-8")

    def test_valid_partial_translation_and_plural(self):
        self.write("strings.xml", '<resources><string name="ok">OK</string></resources>')
        self.write("plurals.xml", '<resources><plurals name="files"><item quantity="other">%d files</item></plurals></resources>')
        self.assertEqual(validate_locale_catalogs(self.directory), (1, 2))

    def test_missing_translation_is_allowed_fallback(self):
        self.write("strings.xml", "<resources></resources>")
        self.assertEqual(validate_locale_catalogs(self.directory), (1, 1))

    def test_duplicate_key_is_rejected(self):
        self.write("strings.xml", '<resources><string name="a">A</string><string name="a">B</string></resources>')
        with self.assertRaisesRegex(ValueError, "Duplicate string keys"):
            validate_locale_catalogs(self.directory)

    def test_malformed_xml_is_rejected(self):
        self.write("strings.xml", '<resources><string name="bad">')
        with self.assertRaises(ET.ParseError):
            validate_locale_catalogs(self.directory)

    def test_invalid_root_is_rejected(self):
        self.write("strings.xml", '<xml><string name="a">A</string></xml>')
        with self.assertRaisesRegex(ValueError, "Invalid resource root"):
            validate_locale_catalogs(self.directory)

    def test_plural_without_other_is_rejected(self):
        self.write("plurals.xml", '<resources><plurals name="files"><item quantity="one">one</item></plurals></resources>')
        with self.assertRaisesRegex(ValueError, "lacks 'other'"):
            validate_locale_catalogs(self.directory)


if __name__ == "__main__":
    unittest.main()
