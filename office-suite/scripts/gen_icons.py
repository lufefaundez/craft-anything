#!/usr/bin/env python3
"""Generates clean-room 24x24 vector icons (SVG, Android Vector Drawable, Apple XCAssets) for 100% of specs/ribbon-schema.json."""

from __future__ import annotations

import json
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

# Palette accents (Fluent / OfiOfi DocxDocx)
INK = "#424242"
BLUE = "#185ABD"
TEAL = "#008272"
ORANGE = "#D83B01"
GREEN = "#107C41"
PURPLE = "#8764B8"
GOLD = "#C19C00"

# Hand-crafted geometric primitives for flagship & frequent Word commands (pure vector paths, 24x24 viewBox)
BESPOKE_PRIMITIVES: dict[str, list[dict[str, str]]] = {
    "ic_edit_paste": [
        {"tag": "rect", "x": "5", "y": "5", "width": "11", "height": "15", "rx": "1.5", "stroke": INK},
        {"tag": "path", "d": "M8.5 5V3.5C8.5 2.95 8.95 2.5 9.5 2.5H11.5C12.05 2.5 12.5 2.95 12.5 3.5V5", "stroke": BLUE},
        {"tag": "rect", "x": "11", "y": "10", "width": "8.5", "height": "10.5", "rx": "1", "fill": "#FFFFFF", "stroke": BLUE},
        {"tag": "line", "x1": "13", "y1": "13", "x2": "17.5", "y2": "13", "stroke": BLUE},
        {"tag": "line", "x1": "13", "y1": "16", "x2": "17.5", "y2": "16", "stroke": BLUE},
    ],
    "ic_edit_cut": [
        {"tag": "circle", "cx": "6.5", "cy": "17.5", "r": "2.5", "stroke": INK},
        {"tag": "circle", "cx": "17.5", "cy": "17.5", "r": "2.5", "stroke": INK},
        {"tag": "path", "d": "M8.5 15.5L17 4", "stroke": INK},
        {"tag": "path", "d": "M15.5 15.5L7 4", "stroke": BLUE},
    ],
    "ic_edit_copy": [
        {"tag": "rect", "x": "4.5", "y": "3.5", "width": "10", "height": "13", "rx": "1.2", "stroke": INK},
        {"tag": "rect", "x": "9.5", "y": "7.5", "width": "10", "height": "13", "rx": "1.2", "fill": "#FFFFFF", "stroke": BLUE},
    ],
    "ic_edit_formatPainter": [
        {"tag": "rect", "x": "5", "y": "3.5", "width": "11", "height": "5", "rx": "1", "stroke": GOLD},
        {"tag": "path", "d": "M16 6H19V11H12V19.5", "stroke": INK},
        {"tag": "rect", "x": "10.5", "y": "16", "width": "3", "height": "5", "rx": "0.8", "stroke": INK},
    ],
    "ic_format_bold": [
        {"tag": "path", "d": "M7 4.5H13.2C15.4 4.5 17 5.9 17 8C17 9.5 16.1 10.7 14.7 11.2C16.5 11.7 17.6 13.1 17.6 15C17.6 17.5 15.7 19.5 13 19.5H7V4.5Z", "stroke": INK, "stroke-width": "2"},
        {"tag": "line", "x1": "7.5", "y1": "11.5", "x2": "14.5", "y2": "11.5", "stroke": INK, "stroke-width": "2"},
    ],
    "ic_format_italic": [
        {"tag": "line", "x1": "11", "y1": "4.5", "x2": "16.5", "y2": "4.5", "stroke": INK},
        {"tag": "line", "x1": "7.5", "y1": "19.5", "x2": "13", "y2": "19.5", "stroke": INK},
        {"tag": "line", "x1": "14", "y1": "4.5", "x2": "10", "y2": "19.5", "stroke": INK},
    ],
    "ic_format_underline": [
        {"tag": "path", "d": "M7 4.5V12C7 14.8 9.2 17 12 17C14.8 17 17 14.8 17 12V4.5", "stroke": INK},
        {"tag": "line", "x1": "5.5", "y1": "20", "x2": "18.5", "y2": "20", "stroke": BLUE},
    ],
    "ic_format_strikethrough": [
        {"tag": "path", "d": "M15.5 7.5C15 5.8 13.5 5 11.5 5C9.3 5 8 6.2 8 7.8C8 9.2 9 10 11.5 10.6", "stroke": INK},
        {"tag": "path", "d": "M8 16.5C8.6 18.2 10.2 19 12.2 19C14.5 19 16 17.8 16 16.2", "stroke": INK},
        {"tag": "line", "x1": "4", "y1": "12", "x2": "20", "y2": "12", "stroke": BLUE},
    ],
    "ic_format_subscript": [
        {"tag": "path", "d": "M5 6L12 16M12 6L5 16", "stroke": INK},
        {"tag": "path", "d": "M15.5 15C15.5 13.8 16.5 13 17.8 13C19 13 19.8 13.7 19.8 14.8C19.8 16.3 15.5 18 15.5 19.5H20", "stroke": BLUE},
    ],
    "ic_format_superscript": [
        {"tag": "path", "d": "M5 8L12 18M12 8L5 18", "stroke": INK},
        {"tag": "path", "d": "M15.5 6C15.5 4.8 16.5 4 17.8 4C19 4 19.8 4.7 19.8 5.8C19.8 7.3 15.5 9 15.5 10.5H20", "stroke": BLUE},
    ],
    "ic_format_color": [
        {"tag": "path", "d": "M6.5 15.5L12 4L17.5 15.5", "stroke": INK},
        {"tag": "line", "x1": "8.5", "y1": "11.5", "x2": "15.5", "y2": "11.5", "stroke": INK},
        {"tag": "rect", "x": "4.5", "y": "18", "width": "15", "height": "3", "rx": "0.5", "fill": ORANGE, "stroke": ORANGE},
    ],
    "ic_format_highlight": [
        {"tag": "path", "d": "M13.5 4L19 9.5L10.5 18H5V12.5L13.5 4Z", "stroke": INK},
        {"tag": "rect", "x": "4.5", "y": "18.5", "width": "15", "height": "2.8", "rx": "0.5", "fill": "#FFE03D", "stroke": GOLD},
    ],
    "ic_format_clear": [
        {"tag": "path", "d": "M5 15L11.5 5H17L10.5 15H5Z", "stroke": INK},
        {"tag": "path", "d": "M14 14L19.5 19.5M19.5 14L14 19.5", "stroke": ORANGE},
    ],
    "ic_para_alignLeft": [
        {"tag": "line", "x1": "4", "y1": "5.5", "x2": "20", "y2": "5.5", "stroke": INK},
        {"tag": "line", "x1": "4", "y1": "10", "x2": "14", "y2": "10", "stroke": INK},
        {"tag": "line", "x1": "4", "y1": "14.5", "x2": "18.5", "y2": "14.5", "stroke": INK},
        {"tag": "line", "x1": "4", "y1": "19", "x2": "13", "y2": "19", "stroke": INK},
    ],
    "ic_para_alignCenter": [
        {"tag": "line", "x1": "4", "y1": "5.5", "x2": "20", "y2": "5.5", "stroke": INK},
        {"tag": "line", "x1": "7", "y1": "10", "x2": "17", "y2": "10", "stroke": INK},
        {"tag": "line", "x1": "5", "y1": "14.5", "x2": "19", "y2": "14.5", "stroke": INK},
        {"tag": "line", "x1": "8", "y1": "19", "x2": "16", "y2": "19", "stroke": INK},
    ],
    "ic_para_alignRight": [
        {"tag": "line", "x1": "4", "y1": "5.5", "x2": "20", "y2": "5.5", "stroke": INK},
        {"tag": "line", "x1": "10", "y1": "10", "x2": "20", "y2": "10", "stroke": INK},
        {"tag": "line", "x1": "5.5", "y1": "14.5", "x2": "20", "y2": "14.5", "stroke": INK},
        {"tag": "line", "x1": "11", "y1": "19", "x2": "20", "y2": "19", "stroke": INK},
    ],
    "ic_para_justify": [
        {"tag": "line", "x1": "4", "y1": "5.5", "x2": "20", "y2": "5.5", "stroke": INK},
        {"tag": "line", "x1": "4", "y1": "10", "x2": "20", "y2": "10", "stroke": INK},
        {"tag": "line", "x1": "4", "y1": "14.5", "x2": "20", "y2": "14.5", "stroke": INK},
        {"tag": "line", "x1": "4", "y1": "19", "x2": "20", "y2": "19", "stroke": INK},
    ],
    "ic_para_bullets": [
        {"tag": "circle", "cx": "5.5", "cy": "6.5", "r": "1.2", "fill": BLUE, "stroke": BLUE},
        {"tag": "circle", "cx": "5.5", "cy": "12", "r": "1.2", "fill": BLUE, "stroke": BLUE},
        {"tag": "circle", "cx": "5.5", "cy": "17.5", "r": "1.2", "fill": BLUE, "stroke": BLUE},
        {"tag": "line", "x1": "9.5", "y1": "6.5", "x2": "20", "y2": "6.5", "stroke": INK},
        {"tag": "line", "x1": "9.5", "y1": "12", "x2": "20", "y2": "12", "stroke": INK},
        {"tag": "line", "x1": "9.5", "y1": "17.5", "x2": "20", "y2": "17.5", "stroke": INK},
    ],
    "ic_para_numbering": [
        {"tag": "path", "d": "M4.5 5.5H6V8.5M4.5 11.5C4.5 10.7 6.5 10.7 6.5 11.5C6.5 12.4 4.5 13.5 4.5 14H6.8M4.5 16.5H6.5L5.2 17.8C6.4 17.8 6.8 19.2 5.5 19.5", "stroke": BLUE},
        {"tag": "line", "x1": "9.5", "y1": "6.5", "x2": "20", "y2": "6.5", "stroke": INK},
        {"tag": "line", "x1": "9.5", "y1": "12", "x2": "20", "y2": "12", "stroke": INK},
        {"tag": "line", "x1": "9.5", "y1": "17.5", "x2": "20", "y2": "17.5", "stroke": INK},
    ],
    "ic_edit_undo": [
        {"tag": "path", "d": "M7.5 8H14.5C17.5 8 19.5 10 19.5 13C19.5 16 17.5 18 14.5 18H9", "stroke": BLUE},
        {"tag": "path", "d": "M10.5 5L6.5 8L10.5 11", "stroke": BLUE},
    ],
    "ic_edit_redo": [
        {"tag": "path", "d": "M16.5 8H9.5C6.5 8 4.5 10 4.5 13C4.5 16 6.5 18 9.5 18H15", "stroke": BLUE},
        {"tag": "path", "d": "M13.5 5L17.5 8L13.5 11", "stroke": BLUE},
    ],
    "ic_file_save": [
        {"tag": "path", "d": "M4.5 4.5H16.5L19.5 7.5V19.5H4.5V4.5Z", "stroke": BLUE},
        {"tag": "rect", "x": "7.5", "y": "4.5", "width": "7", "height": "4.5", "stroke": BLUE},
        {"tag": "rect", "x": "7.5", "y": "13", "width": "9", "height": "6.5", "stroke": INK},
    ],
    "ic_file_print": [
        {"tag": "rect", "x": "7", "y": "3.5", "width": "10", "height": "5", "stroke": INK},
        {"tag": "rect", "x": "4", "y": "8.5", "width": "16", "height": "8", "rx": "1.5", "stroke": INK},
        {"tag": "rect", "x": "7", "y": "13.5", "width": "10", "height": "7", "fill": "#FFFFFF", "stroke": BLUE},
    ],
    "ic_file_share": [
        {"tag": "path", "d": "M12 15V4.5M12 4.5L8.5 8M12 4.5L15.5 8", "stroke": BLUE},
        {"tag": "path", "d": "M5.5 12V18.5C5.5 19.3 6.2 20 7 20H17C17.8 20 18.5 19.3 18.5 18.5V12", "stroke": INK},
    ],
    "ic_tools_search": [
        {"tag": "circle", "cx": "10.5", "cy": "10.5", "r": "6", "stroke": BLUE},
        {"tag": "line", "x1": "15", "y1": "15", "x2": "20", "y2": "20", "stroke": INK},
    ],
    "ic_insert_table": [
        {"tag": "rect", "x": "4", "y": "4.5", "width": "16", "height": "15", "rx": "1", "stroke": BLUE},
        {"tag": "line", "x1": "4", "y1": "9.5", "x2": "20", "y2": "9.5", "stroke": INK},
        {"tag": "line", "x1": "4", "y1": "14.5", "x2": "20", "y2": "14.5", "stroke": INK},
        {"tag": "line", "x1": "9.5", "y1": "4.5", "x2": "9.5", "y2": "19.5", "stroke": INK},
        {"tag": "line", "x1": "14.5", "y1": "4.5", "x2": "14.5", "y2": "19.5", "stroke": INK},
    ],
    "ic_insert_picture": [
        {"tag": "rect", "x": "3.5", "y": "4.5", "width": "17", "height": "15", "rx": "1.5", "stroke": INK},
        {"tag": "circle", "cx": "8.5", "cy": "9.5", "r": "1.5", "stroke": GOLD},
        {"tag": "path", "d": "M4.5 17.5L10.5 12L14 15L16.5 12.5L20 16.5", "stroke": GREEN},
    ],
    "ic_insert_shape": [
        {"tag": "rect", "x": "4", "y": "5", "width": "9", "height": "9", "stroke": BLUE},
        {"tag": "circle", "cx": "15", "cy": "15", "r": "5", "fill": "#FFFFFF", "stroke": ORANGE},
    ],
    "ic_insert_chart": [
        {"tag": "line", "x1": "4", "y1": "19.5", "x2": "20", "y2": "19.5", "stroke": INK},
        {"tag": "rect", "x": "5.5", "y": "12", "width": "3.2", "height": "7.5", "stroke": GREEN},
        {"tag": "rect", "x": "10.4", "y": "6.5", "width": "3.2", "height": "13", "stroke": BLUE},
        {"tag": "rect", "x": "15.3", "y": "9.5", "width": "3.2", "height": "10", "stroke": ORANGE},
    ],
    "ic_insert_link": [
        {"tag": "path", "d": "M9.5 14.5L14.5 9.5", "stroke": INK},
        {"tag": "path", "d": "M11 6.5L13 4.5C14.7 2.8 17.3 2.8 19 4.5C20.7 6.2 20.7 8.8 19 10.5L17 12.5", "stroke": BLUE},
        {"tag": "path", "d": "M13 17.5L11 19.5C9.3 21.2 6.7 21.2 5 19.5C3.3 17.8 3.3 15.2 5 13.5L7 11.5", "stroke": INK},
    ],
    "ic_insert_equation": [
        {"tag": "path", "d": "M17.5 6H7L13 12L7 18H17.5", "stroke": PURPLE, "stroke-width": "1.8"},
    ],
    "ic_review_spelling": [
        {"tag": "path", "d": "M5 13.5L8.5 5.5L12 13.5M6.5 10.5H10.5", "stroke": INK},
        {"tag": "path", "d": "M11.5 17L14.5 19.5L20 12.5", "stroke": GREEN, "stroke-width": "1.8"},
    ],
    "ic_review_trackChanges": [
        {"tag": "rect", "x": "4.5", "y": "4", "width": "15", "height": "16", "rx": "1.2", "stroke": INK},
        {"tag": "line", "x1": "7.5", "y1": "8.5", "x2": "16.5", "y2": "8.5", "stroke": ORANGE},
        {"tag": "line", "x1": "7.5", "y1": "12.5", "x2": "13.5", "y2": "12.5", "stroke": INK},
        {"tag": "circle", "cx": "16.5", "cy": "16.5", "r": "3", "fill": "#FFFFFF", "stroke": BLUE},
    ],
    "ic_review_newComment": [
        {"tag": "path", "d": "M4.5 5.5H19.5V16H11L7 19.5V16H4.5V5.5Z", "stroke": PURPLE},
        {"tag": "line", "x1": "12", "y1": "8.5", "x2": "12", "y2": "13", "stroke": BLUE},
        {"tag": "line", "x1": "9.7", "y1": "10.8", "x2": "14.3", "y2": "10.8", "stroke": BLUE},
    ],
    "ic_view_zoom": [
        {"tag": "circle", "cx": "10.5", "cy": "10.5", "r": "6", "stroke": BLUE},
        {"tag": "line", "x1": "15", "y1": "15", "x2": "20", "y2": "20", "stroke": INK},
        {"tag": "line", "x1": "10.5", "y1": "8", "x2": "10.5", "y2": "13", "stroke": INK},
        {"tag": "line", "x1": "8", "y1": "10.5", "x2": "13", "y2": "10.5", "stroke": INK},
    ],
    "ic_ui_toggleReflow": [
        {"tag": "rect", "x": "6", "y": "3.5", "width": "12", "height": "17", "rx": "2", "stroke": BLUE},
        {"tag": "line", "x1": "9", "y1": "8", "x2": "15", "y2": "8", "stroke": INK},
        {"tag": "line", "x1": "9", "y1": "11.5", "x2": "15", "y2": "11.5", "stroke": INK},
        {"tag": "line", "x1": "9", "y1": "15", "x2": "13", "y2": "15", "stroke": INK},
    ],
}


def fallback_primitives(icon_name: str) -> list[dict[str, str]]:
    """Builds distinct clean-room category + modifier vector shapes for every Ribbon command icon."""
    key = icon_name.lower()
    # Accent color by semantic domain
    if any(w in key for w in ("delete", "remove", "reject", "eraser", "clear", "close", "stop")):
        accent = ORANGE
    elif any(w in key for w in ("accept", "spelling", "chart", "proofing")):
        accent = GREEN
    elif any(w in key for w in ("table", "header", "footer", "hf_")):
        accent = TEAL
    elif any(w in key for w in ("comment", "equation", "smartart")):
        accent = PURPLE
    else:
        accent = BLUE

    elems: list[dict[str, str]] = []
    # Base geometric silhouette by prefix family
    if key.startswith(("ic_table_", "ic_insert_table")):
        elems += [
            {"tag": "rect", "x": "4", "y": "5", "width": "14", "height": "12", "rx": "1", "stroke": INK},
            {"tag": "line", "x1": "4", "y1": "10", "x2": "18", "y2": "10", "stroke": INK},
            {"tag": "line", "x1": "11", "y1": "5", "x2": "11", "y2": "17", "stroke": INK},
        ]
    elif key.startswith(("ic_para_", "ic_list_")):
        elems += [
            {"tag": "line", "x1": "4", "y1": "6.5", "x2": "18", "y2": "6.5", "stroke": INK},
            {"tag": "line", "x1": "4", "y1": "11.5", "x2": "15", "y2": "11.5", "stroke": INK},
            {"tag": "line", "x1": "4", "y1": "16.5", "x2": "17", "y2": "16.5", "stroke": INK},
        ]
    elif key.startswith(("ic_format_", "ic_font_")):
        elems += [
            {"tag": "path", "d": "M5.5 16L10.5 5L15.5 16", "stroke": INK},
            {"tag": "line", "x1": "7.2", "y1": "12.5", "x2": "13.8", "y2": "12.5", "stroke": INK},
        ]
    elif key.startswith(("ic_draw_",)):
        elems += [
            {"tag": "path", "d": "M5 17.5L14.5 6L17.5 9L8 19.5H5V17.5Z", "stroke": INK},
        ]
    elif key.startswith(("ic_design_", "ic_styles_")):
        elems += [
            {"tag": "rect", "x": "4.5", "y": "5", "width": "11", "height": "13", "rx": "1.2", "stroke": INK},
            {"tag": "rect", "x": "8", "y": "8", "width": "10", "height": "10", "rx": "1", "fill": "#FFFFFF", "stroke": accent},
        ]
    elif key.startswith(("ic_layout_", "ic_hf_")):
        elems += [
            {"tag": "rect", "x": "5", "y": "4", "width": "13", "height": "16", "rx": "1.2", "stroke": INK},
            {"tag": "line", "x1": "8", "y1": "4", "x2": "8", "y2": "20", "stroke": accent},
            {"tag": "line", "x1": "5", "y1": "8", "x2": "18", "y2": "8", "stroke": accent},
        ]
    elif key.startswith(("ic_picture_", "ic_shape_", "ic_wordart_", "ic_smartart_", "ic_chart_", "ic_arrange_", "ic_object_")):
        elems += [
            {"tag": "rect", "x": "4", "y": "5", "width": "12", "height": "12", "rx": "1.5", "stroke": INK},
            {"tag": "circle", "cx": "14.5", "cy": "14.5", "r": "4.5", "fill": "#FFFFFF", "stroke": accent},
        ]
    elif key.startswith(("ic_review_", "ic_readaloud_")):
        elems += [
            {"tag": "path", "d": "M4.5 5.5H17.5V15H10.5L7 18V15H4.5V5.5Z", "stroke": INK},
        ]
    elif key.startswith(("ic_references_", "ic_mailings_", "ic_outline_", "ic_fields_")):
        elems += [
            {"tag": "rect", "x": "5", "y": "4.5", "width": "13", "height": "15", "rx": "1.2", "stroke": INK},
            {"tag": "line", "x1": "8", "y1": "8.5", "x2": "15", "y2": "8.5", "stroke": accent},
            {"tag": "line", "x1": "8", "y1": "12.5", "x2": "13", "y2": "12.5", "stroke": INK},
        ]
    else:
        elems += [
            {"tag": "rect", "x": "5", "y": "4", "width": "13", "height": "16", "rx": "1.5", "stroke": INK},
            {"tag": "line", "x1": "8", "y1": "9", "x2": "15", "y2": "9", "stroke": INK},
            {"tag": "line", "x1": "8", "y1": "13", "x2": "13", "y2": "13", "stroke": INK},
        ]

    # Action badge in bottom-right corner
    if any(w in key for w in ("add", "new", "insert", "create", "grow", "increase")):
        elems += [
            {"tag": "line", "x1": "17.5", "y1": "14.5", "x2": "17.5", "y2": "20.5", "stroke": accent},
            {"tag": "line", "x1": "14.5", "y1": "17.5", "x2": "20.5", "y2": "17.5", "stroke": accent},
        ]
    elif any(w in key for w in ("delete", "remove", "reject", "close", "clear")):
        elems += [
            {"tag": "line", "x1": "15", "y1": "15", "x2": "20", "y2": "20", "stroke": accent},
            {"tag": "line", "x1": "20", "y1": "15", "x2": "15", "y2": "20", "stroke": accent},
        ]
    elif any(w in key for w in ("next", "forward", "right", "redo", "indent", "promote")):
        elems.append({"tag": "path", "d": "M15 15L19.5 17.5L15 20", "stroke": accent})
    elif any(w in key for w in ("previous", "back", "left", "undo", "outdent", "demote")):
        elems.append({"tag": "path", "d": "M19.5 15L15 17.5L19.5 20", "stroke": accent})
    elif any(w in key for w in ("find", "search", "inspect", "preview", "zoom")):
        elems += [
            {"tag": "circle", "cx": "16.5", "cy": "16.5", "r": "2.8", "fill": "#FFFFFF", "stroke": accent},
            {"tag": "line", "x1": "18.5", "y1": "18.5", "x2": "21", "y2": "21", "stroke": accent},
        ]
    else:
        elems.append({"tag": "circle", "cx": "17.5", "cy": "17.5", "r": "2.5", "fill": "#FFFFFF", "stroke": accent})

    return elems


def to_svg(elements: list[dict[str, str]]) -> str:
    body = []
    for el in elements:
        tag = el["tag"]
        attrs = {k: v for k, v in el.items() if k != "tag"}
        attrs.setdefault("fill", "none")
        attrs.setdefault("stroke-width", "1.5")
        attrs.setdefault("stroke-linecap", "round")
        attrs.setdefault("stroke-linejoin", "round")
        attr_str = " ".join(f'{k}="{v}"' for k, v in attrs.items())
        body.append(f"  <{tag} {attr_str} />")
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">\n'
        + "\n".join(body)
        + "\n</svg>\n"
    )


def elem_to_android_path_d(el: dict[str, str]) -> str | None:
    tag = el["tag"]
    if tag == "path":
        return el["d"]
    if tag == "line":
        return f'M{el["x1"]},{el["y1"]}L{el["x2"]},{el["y2"]}'
    if tag == "rect":
        x, y, w, h = float(el["x"]), float(el["y"]), float(el["width"]), float(el["height"])
        return f"M{x:g},{y:g}h{w:g}v{h:g}h-{w:g}z"
    if tag == "circle":
        cx, cy, r = float(el["cx"]), float(el["cy"]), float(el["r"])
        return (
            f"M{cx - r:g},{cy:g}"
            f"a{r:g},{r:g} 0 1,0 {2 * r:g},0"
            f"a{r:g},{r:g} 0 1,0 -{2 * r:g},0"
        )
    return None


def to_android_vector(elements: list[dict[str, str]]) -> str:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        '    android:viewportWidth="24"',
        '    android:viewportHeight="24">',
    ]
    for el in elements:
        d = elem_to_android_path_d(el)
        if not d:
            continue
        stroke = el.get("stroke", INK)
        fill = el.get("fill")
        sw = el.get("stroke-width", "1.5")
        fill_attr = f'\n        android:fillColor="{fill}"' if fill and fill != "none" else ""
        lines.append(
            f'    <path\n'
            f'        android:pathData="{d}"\n'
            f'        android:strokeColor="{stroke}"\n'
            f'        android:strokeWidth="{sw}"\n'
            f'        android:strokeLineCap="round"\n'
            f'        android:strokeLineJoin="round"{fill_attr} />'
        )
    lines.append("</vector>\n")
    return "\n".join(lines)


def collect_icons(schema: dict[str, Any]) -> list[str]:
    icons: set[str] = set()

    def walk(node: Any) -> None:
        if isinstance(node, dict):
            if isinstance(node.get("icon"), str):
                icons.add(node["icon"])
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for item in node:
                walk(item)

    for sec in ("title_bar", "backstage", "tabs", "contextual_tabs", "status_bar", "smartphone_quick_bar"):
        walk(schema.get(sec))
    return sorted(icons)


def main() -> None:
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    icons = collect_icons(schema)

    SVG_DIR.mkdir(parents=True, exist_ok=True)
    ANDROID_DRAWABLE_DIR.mkdir(parents=True, exist_ok=True)
    APPLE_ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    (APPLE_ASSETS_DIR / "Contents.json").write_text(
        json.dumps({"info": {"author": "ofiofi-cleanroom", "version": 1}}, indent=2) + "\n",
        encoding="utf-8",
    )

    for name in icons:
        primitives = BESPOKE_PRIMITIVES.get(name) or fallback_primitives(name)
        svg_text = to_svg(primitives)
        xml_text = to_android_vector(primitives)

        (SVG_DIR / f"{name}.svg").write_text(svg_text, encoding="utf-8")
        (ANDROID_DRAWABLE_DIR / f"{name.lower()}.xml").write_text(xml_text, encoding="utf-8")

        imageset = APPLE_ASSETS_DIR / f"{name}.imageset"
        imageset.mkdir(parents=True, exist_ok=True)
        (imageset / f"{name}.svg").write_text(svg_text, encoding="utf-8")
        contents = {
            "images": [{"filename": f"{name}.svg", "idiom": "universal"}],
            "info": {"author": "ofiofi-cleanroom", "version": 1},
            "properties": {"preserves-vector-representation": True},
        }
        (imageset / "Contents.json").write_text(json.dumps(contents, indent=2) + "\n", encoding="utf-8")

    print(f"Generated {len(icons)} clean-room vector icons across SVG, Android Drawables, and Apple XCAssets.")


if __name__ == "__main__":
    main()
