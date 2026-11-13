#!/usr/bin/env python3
"""Verifies extracted multi-app OfficeShellKit abstractions for Waves 1-3 (Word/Excel/PowerPoint)."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

SUITE_DIR = Path(__file__).resolve().parent.parent
SHELL_PKG_DIR = SUITE_DIR / "packages" / "OfficeShellKit"
SHELL_SRC = SHELL_PKG_DIR / "Sources" / "OfficeShellKit" / "OfficeShellKit.swift"
ANDROID_TOKENS = (
    SUITE_DIR
    / "docxdocx/apps/android/app/src/main/java/app/ofiofi/docxdocx/ui/theme/OfficeTokens.kt"
)
APPLE_TOKENS = (
    SUITE_DIR
    / "docxdocx/apps/apple/Packages/OfficeCoreKit/Sources/OfficeCoreKit/Theme/OfficeTokens.swift"
)
ATTRIBUTION_MD = SUITE_DIR / "ATTRIBUTION.md"


def main() -> int:
    errors: list[str] = []

    if not SHELL_SRC.is_file():
        errors.append(f"Missing OfficeShellKit source: {SHELL_SRC}")
        return fail(errors)

    src_text = SHELL_SRC.read_text(encoding="utf-8")
    for required_token in (
        "XlsxXlsx",
        "PptxPptx",
        "DocxDocx",
        "gridcraft",
        "deckcraft",
        "wordcraft",
        "#107C41",
        "#B7472A",
        "#185ABD",
        "OfficeShellDocumentBridge",
        "ShellStorageTarget",
    ):
        if required_token not in src_text:
            errors.append(f"Missing abstraction token '{required_token}' in OfficeShellKit.swift")

    if "ofiofi cloud" in src_text.lower():
        errors.append("Forbidden proprietary OfiOfi Cloud string found in OfficeShellKit.swift")

    for path in (ANDROID_TOKENS, APPLE_TOKENS, ATTRIBUTION_MD):
        if not path.is_file():
            errors.append(f"Missing shared suite file: {path}")
        else:
            txt = path.read_text(encoding="utf-8")
            if "Excel" not in txt or "Powerpoint" not in txt and "PowerPoint" not in txt:
                errors.append(f"Missing multi-app (Excel/PowerPoint) tokens in {path.name}")

    if errors:
        return fail(errors)

    proc = subprocess.run(
        ["swift", "test"],
        cwd=SHELL_PKG_DIR,
        capture_output=True,
        text=True,
        check=False,
    )
    if proc.returncode != 0:
        errors.append(f"swift test in OfficeShellKit failed:\n{proc.stdout}\n{proc.stderr}")
        return fail(errors)

    print("verify_shell_abstraction OK: OfficeShellKit (Word/Excel/PowerPoint + storage + Fluent tokens) verified via swift test")
    return 0


def fail(errors: list[str]) -> int:
    for err in errors:
        print(f"ERROR: {err}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
