#!/usr/bin/env python3
"""Validates office-suite/specs/ribbon-schema.json against wordcraft-commands.json (100% coverage & zero invalid IDs)."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

SUITE_DIR = Path(__file__).resolve().parent.parent
COMMANDS_PATH = SUITE_DIR / "specs" / "wordcraft-commands.json"
SCHEMA_PATH = SUITE_DIR / "specs" / "ribbon-schema.json"

REQUIRED_TABS = {"home", "insert", "draw", "design", "layout", "references", "mailings", "review", "view"}
REQUIRED_CONTEXTUAL = {"table_design", "table_layout", "picture_format", "shape_format", "equation", "chart_design", "smartart_design"}


def main() -> int:
    commands_data = json.loads(COMMANDS_PATH.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))

    valid_ids = {c["id"] for c in commands_data["commands"]}
    errors: list[str] = []

    tab_ids = {t["id"] for t in schema.get("tabs", [])}
    missing_tabs = REQUIRED_TABS - tab_ids
    if missing_tabs:
        errors.append(f"Missing required core tabs: {sorted(missing_tabs)}")

    ctx_ids = {t["id"] for t in schema.get("contextual_tabs", [])}
    missing_ctx = REQUIRED_CONTEXTUAL - ctx_ids
    if missing_ctx:
        errors.append(f"Missing required contextual tabs: {sorted(missing_ctx)}")

    if "cloud" in json.dumps(schema.get("backstage", {})).lower() and "ofiofi cloud" in json.dumps(schema).lower():
        errors.append("Forbidden proprietary OfiOfi Cloud reference found in backstage")

    ui_mapped: set[str] = set()

    def walk_ui(node: Any, ctx: str) -> None:
        if isinstance(node, dict):
            if "labels" in node:
                labels = node["labels"]
                if not labels.get("es-CL") or not labels.get("en-US"):
                    errors.append(f"Incomplete bilingual labels at {ctx}: {labels}")
            cid = node.get("command_id")
            if isinstance(cid, str):
                if not cid.startswith("ui."):
                    if cid not in valid_ids:
                        errors.append(f"Unknown command_id '{cid}' at {ctx}")
                    else:
                        ui_mapped.add(cid)
                if not node.get("icon"):
                    errors.append(f"Missing icon key for '{cid}' at {ctx}")
            dcid = node.get("dialog_command_id")
            if isinstance(dcid, str) and not dcid.startswith("ui."):
                if dcid not in valid_ids:
                    errors.append(f"Unknown dialog_command_id '{dcid}' at {ctx}")
                else:
                    ui_mapped.add(dcid)
            for k, v in node.items():
                walk_ui(v, f"{ctx}.{k}")
        elif isinstance(node, list):
            for idx, item in enumerate(node):
                walk_ui(item, f"{ctx}[{idx}]")

    for section in ("title_bar", "backstage", "tabs", "contextual_tabs", "status_bar", "smartphone_quick_bar"):
        walk_ui(schema.get(section), section)

    headless_ids: set[str] = set()
    for entry in schema.get("headless_or_keybinding_commands", []):
        cid = entry.get("command_id")
        if cid not in valid_ids:
            errors.append(f"Unknown headless command_id '{cid}'")
        else:
            headless_ids.add(cid)

    accounted = ui_mapped | headless_ids
    unaccounted = valid_ids - accounted
    if unaccounted:
        errors.append(f"Unaccounted engine commands ({len(unaccounted)}): {sorted(unaccounted)[:10]}")

    overlap = ui_mapped & headless_ids
    if overlap:
        errors.append(f"Commands listed in both UI and headless ({len(overlap)}): {sorted(overlap)[:10]}")

    if errors:
        for err in errors:
            print(f"ERROR: {err}", file=sys.stderr)
        return 1

    print(
        f"validate_ribbon_schema OK: {len(tab_ids)} core tabs, {len(ctx_ids)} contextual tabs, "
        f"{len(ui_mapped)} UI-mapped commands, {len(headless_ids)} headless commands = "
        f"{len(accounted)}/{len(valid_ids)} (100% covered)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
