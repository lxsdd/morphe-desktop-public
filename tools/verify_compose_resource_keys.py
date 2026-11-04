#!/usr/bin/env python3
"""Fail closed if source code references Compose strings/plurals absent from base resources.

A successful Kotlin compile does not guarantee the GUI can load a translated
resource at runtime. Always validate the default resource catalog separately.
"""
from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAIN = ROOT / "src" / "main"
RESOURCES = MAIN / "composeResources" / "values"
REFERENCE = re.compile(r"\bRes\.(string|plurals)\.([A-Za-z_]\w*)")

def names(path: pathlib.Path, tag: str) -> set[str]:
    root = ET.parse(path).getroot()
    found = [item.attrib["name"] for item in root.findall(tag)]
    duplicates = sorted({name for name in found if found.count(name) > 1})
    if duplicates:
        raise ValueError(f"Duplicate {tag} keys in {path}: {duplicates}")
    return set(found)

def main() -> int:
    defined = {
        "string": names(RESOURCES / "strings.xml", "string"),
        "plurals": names(RESOURCES / "plurals.xml", "plurals"),
    }
    missing: dict[tuple[str, str], list[str]] = {}
    count = 0
    for path in sorted((MAIN / "kotlin").rglob("*.kt")):
        source = path.read_text(encoding="utf-8")
        for match in REFERENCE.finditer(source):
            count += 1
            kind, key = match.groups()
            if key not in defined[kind]:
                line = source.count("\n", 0, match.start()) + 1
                missing.setdefault((kind, key), []).append(f"{path.relative_to(ROOT)}:{line}")
    if missing:
        print("FAIL: Compose resources referenced in source but missing from default resources:")
        for (kind, key), locations in sorted(missing.items()):
            print(f"  {kind}:{key} <- {', '.join(locations[:5])}")
        return 1
    print(f"PASS: {count} Compose string/plurals references resolve in base resource XML.")
    for key in ("nav_sources", "nav_apps"):
        if key not in defined["string"]:
            print(f"FAIL: critical GUI 2.0 navigation resource string:{key} is absent")
            return 1
    print("PASS: GUI 2.0 navigation resource keys are included.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
