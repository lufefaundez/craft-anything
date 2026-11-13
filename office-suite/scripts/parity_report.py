#!/usr/bin/env python3
"""Automated Word 16.115 & Chilean/Global document parity audit for OfiOfi DocxDocx."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

SUITE_DIR = Path(__file__).resolve().parent.parent
WORKSPACE_DIR = SUITE_DIR / "docxdocx"
SPECS_DIR = SUITE_DIR / "specs"
CORPUS_DIR = SUITE_DIR / "corpus" / "word"
FONTS_DIR = SUITE_DIR / "assets" / "fonts"
REPORT_PATH = SUITE_DIR / "corpus" / "PARITY-REPORT.md"


@dataclass
class CheckResult:
    name: str
    weight: float
    score: float
    detail: str


def run_python_script(script_name: str) -> tuple[bool, str]:
    proc = subprocess.run(
        [sys.executable, str(SUITE_DIR / "scripts" / script_name)],
        capture_output=True,
        text=True,
        check=False,
    )
    output = (proc.stdout or proc.stderr).strip()
    return proc.returncode == 0, output


def check_ui_tokens_and_presets() -> CheckResult:
    tokens = json.loads((SPECS_DIR / "fluent-tokens.json").read_text(encoding="utf-8"))
    android_theme = (
        WORKSPACE_DIR
        / "apps/android/app/src/main/java/app/ofiofi/docxdocx/ui/theme/OfficeTokens.kt"
    ).read_text(encoding="utf-8")
    apple_tokens = (
        WORKSPACE_DIR
        / "apps/apple/Packages/OfficeCoreKit/Sources/OfficeCoreKit/Theme/OfficeTokens.swift"
    ).read_text(encoding="utf-8")
    presets_swift = (
        WORKSPACE_DIR
        / "apps/apple/Packages/OfficeCoreKit/Sources/OfficeCoreKit/WebDavAndTemplates.swift"
    ).read_text(encoding="utf-8")
    presets_kt = (
        WORKSPACE_DIR
        / "apps/android/app/src/main/java/app/ofiofi/docxdocx/storage/DocumentStorage.kt"
    ).read_text(encoding="utf-8")

    def hex_to_swift(hex_str: str) -> str:
        raw = hex_str.strip().lstrip("#")
        r, g, b = int(raw[0:2], 16), int(raw[2:4], 16), int(raw[4:6], 16)
        a = int(raw[6:8], 16) if len(raw) == 8 else 0xFF
        return (
            f"Color(.sRGB, red: {r / 255.0:.4f}, green: {g / 255.0:.4f}, "
            f"blue: {b / 255.0:.4f}, opacity: {a / 255.0:.4f})"
        )

    def hex_to_kt(hex_str: str) -> str:
        raw = hex_str.strip().lstrip("#")
        r, g, b = int(raw[0:2], 16), int(raw[2:4], 16), int(raw[4:6], 16)
        a = int(raw[6:8], 16) if len(raw) == 8 else 0xFF
        return f"Color(0x{(a << 24) | (r << 16) | (g << 8) | b:08X})"

    word_accent = tokens["apps"]["word"]["accent_light"]
    canvas_bg = tokens["surfaces"]["light"]["canvas"]
    ribbon_card = tokens["surfaces"]["light"]["ribbon_card"]

    subchecks = [
        ("Android word accent", hex_to_kt(word_accent) in android_theme),
        ("Apple word accent", hex_to_swift(word_accent) in apple_tokens),
        ("Android canvas surface", hex_to_kt(canvas_bg) in android_theme),
        ("Apple canvas surface", hex_to_swift(canvas_bg) in apple_tokens),
        ("Ribbon card surface (Android)", hex_to_kt(ribbon_card) in android_theme),
        ("Ribbon card surface (Apple)", hex_to_swift(ribbon_card) in apple_tokens),
        ("Chilean Oficio preset (Apple)", "Oficio Ordinario Chile" in presets_swift),
        ("Chilean Oficio preset (Android)", "Oficio Ordinario Chile" in presets_kt),
        ("Word 16.115 UI spec present", (SPECS_DIR / "word-ui-es-16.115.md").is_file()),
    ]
    passed = sum(1 for _, ok in subchecks if ok)
    ratio = passed / len(subchecks)
    failed_names = [n for n, ok in subchecks if not ok]
    detail = (
        f"{passed}/{len(subchecks)} Fluent/Chilean preset checks passed"
        if not failed_names
        else f"Failed: {', '.join(failed_names)}"
    )
    return CheckResult("Fluent UI Tokens & Chilean Presets", 0.20, ratio, detail)


def check_corpus_parity() -> CheckResult:
    if not CORPUS_DIR.is_dir() or not list(CORPUS_DIR.glob("*.docx")):
        ok_gen, gen_out = run_python_script("generate_corpus.py")
        if not ok_gen:
            return CheckResult("Rust Corpus & Hostile Parity", 0.35, 0.0, f"generate_corpus failed: {gen_out}")

    proc = subprocess.run(
        [
            "cargo",
            "run",
            "--quiet",
            "-p",
            "word-mobile-ffi",
            "--example",
            "corpus_parity",
            "--",
            str(CORPUS_DIR),
            str(FONTS_DIR),
        ],
        cwd=WORKSPACE_DIR,
        capture_output=True,
        text=True,
        check=False,
    )
    if proc.returncode != 0:
        return CheckResult(
            "Rust Corpus & Hostile Parity",
            0.35,
            0.0,
            f"corpus_parity failed (exit {proc.returncode}): {proc.stderr.strip() or proc.stdout.strip()}",
        )

    payload = json.loads(proc.stdout.strip())
    docs = payload.get("documents", [])
    doc_score = sum(1 for d in docs if d.get("ok")) / max(1, len(docs))
    hostile_ok = bool(payload.get("hostile_ok"))
    oficio_doc = next((d for d in docs if "oficio" in d.get("file", "")), None)
    oficio_geom_ok = bool(oficio_doc and oficio_doc.get("height_pt", 0) > 930.0)
    a4_doc = next((d for d in docs if "ecuaciones" in d.get("file", "")), None)
    a4_geom_ok = bool(a4_doc and 590.0 <= a4_doc.get("width_pt", 0) <= 600.0)

    final_score = (0.70 * doc_score) + (0.15 * float(hostile_ok)) + (0.075 * float(oficio_geom_ok)) + (0.075 * float(a4_geom_ok))
    detail = (
        f"{sum(1 for d in docs if d.get('ok'))}/{len(docs)} corpus docs OK, "
        f"hostile={hostile_ok}, oficio_pt={oficio_geom_ok}, a4_pt={a4_geom_ok}"
    )
    return CheckResult("Rust Corpus & Hostile Parity", 0.35, final_score, detail)


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify visual, command, icon, and document parity.")
    parser.add_argument("--min-score", type=float, default=0.95, help="Minimum weighted parity score (0.0..1.0)")
    args = parser.parse_args()

    schema_ok, schema_out = run_python_script("validate_ribbon_schema.py")
    icons_ok, icons_out = run_python_script("check_icons_coverage.py")

    results = [
        CheckResult("Ribbon Command Schema (177 cmds)", 0.25, 1.0 if schema_ok else 0.0, schema_out),
        CheckResult("Clean-Room Vector Icons Coverage", 0.20, 1.0 if icons_ok else 0.0, icons_out),
        check_ui_tokens_and_presets(),
        check_corpus_parity(),
    ]

    total = sum(r.weight * r.score for r in results)

    lines = [
        "# Parity Report — OfiOfi DocxDocx (Word 16.115 & Chilean Corpus)",
        "",
        f"**Weighted Parity Score:** `{total:.4f}` (required >= `{args.min_score:.2f}`)",
        "",
        "| Check | Weight | Score | Detail |",
        "|---|---:|---:|---|",
    ]
    for r in results:
        lines.append(f"| {r.name} | {r.weight:.2f} | {r.score:.4f} | {r.detail} |")

    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"parity_report score={total:.4f} min={args.min_score:.2f}")
    for r in results:
        print(f"  - {r.name}: {r.score:.4f} ({r.detail})")

    if total + 1e-9 < args.min_score:
        print(f"ERROR: Parity score {total:.4f} below minimum {args.min_score:.2f}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
