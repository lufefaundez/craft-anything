#!/usr/bin/env python3
"""Verifies 100% clean-room vector icon coverage across SVG, Android Vector Drawables, and Apple XCAssets."""

from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

SUITE_DIR = Path(__file__).resolve().parent.parent
SCHEMA_PATH = SUITE_DIR / "specs" / "ribbon-schema.json"
SVG_DIR = SUITE_DIR / "assets" / "icons" / "svg"
ANDROID_DRAWABLE_DIR = SUITE_DIR / "docxdocx/apps/android/app/src/main/res/drawable"
APPLE_ASSETS_DIR = (
    SUITE_DIR
    / "docxdocx/apps/apple/Packages/OfficeCoreKit/Sources/OfficeCoreKit/Resources/OfficeIcons.xcassets"
)


def main() -> int:
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    required: set[str] = set()

    def walk(node: Any) -> None:
        if isinstance(node, dict):
            if isinstance(node.get("icon"), str):
                required.add(node["icon"])
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for x in node:
                walk(x)

    for sec in ("title_bar", "backstage", "tabs", "contextual_tabs", "status_bar", "smartphone_quick_bar"):
        walk(schema.get(sec))

    errors: list[str] = []
    for name in sorted(required):
        svg_path = SVG_DIR / f"{name}.svg"
        xml_path = ANDROID_DRAWABLE_DIR / f"{name.lower()}.xml"
        apple_svg = APPLE_ASSETS_DIR / f"{name}.imageset" / f"{name}.svg"

        if not svg_path.is_file():
            errors.append(f"Missing SVG: {svg_path.relative_to(SUITE_DIR)}")
        else:
            try:
                root = ET.fromstring(svg_path.read_text(encoding="utf-8"))
                for elem in root.iter():
                    tag = elem.tag.split("}")[-1]
                    if tag in ("text", "image"):
                        errors.append(f"Forbidden <{tag}> in clean-room SVG: {name}")
            except ET.ParseError as e:
                errors.append(f"Malformed SVG {name}: {e}")

        if not xml_path.is_file():
            errors.append(f"Missing Android Vector Drawable: {xml_path.relative_to(SUITE_DIR)}")
        else:
            try:
                ET.fromstring(xml_path.read_text(encoding="utf-8"))
            except ET.ParseError as e:
                errors.append(f"Malformed Android XML {name}: {e}")

        if not apple_svg.is_file():
            errors.append(f"Missing Apple XCAsset SVG: {apple_svg.relative_to(SUITE_DIR)}")

    if errors:
        for err in errors[:20]:
            print(f"ERROR: {err}", file=sys.stderr)
        return 1

    print(f"check_icons_coverage OK: {len(required)}/{len(required)} clean-room icons verified across SVG, Android XML, and Apple XCAssets (100%)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
