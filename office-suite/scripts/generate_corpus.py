#!/usr/bin/env python3
"""Generates deterministic Chilean & global .docx parity fixtures under office-suite/corpus/word/."""

from __future__ import annotations

import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

SUITE_DIR = Path(__file__).resolve().parent.parent
CORPUS_DIR = SUITE_DIR / "corpus" / "word"

CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

DOC_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/></w:rPr></w:rPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:rPr><w:b/><w:color w:val="185ABD"/><w:sz w:val="32"/></w:rPr>
  </w:style>
</w:styles>"""


def make_paragraph(text: str, bold: bool = False, italic: bool = False, style: str | None = None, align: str | None = None) -> str:
    ppr = ""
    if style or align:
        ppr = "<w:pPr>"
        if style:
            ppr += f'<w:pStyle w:val="{escape(style)}"/>'
        if align:
            ppr += f'<w:jc w:val="{escape(align)}"/>'
        ppr += "</w:pPr>"
    rpr = ""
    if bold or italic:
        rpr = "<w:rPr>"
        if bold:
            rpr += "<w:b/>"
        if italic:
            rpr += "<w:i/>"
        rpr += "</w:rPr>"
    return f'<w:p>{ppr}<w:r>{rpr}<w:t xml:space="preserve">{escape(text)}</w:t></w:r></w:p>'


def make_table(rows: list[list[str]]) -> str:
    xml_rows = []
    for r_idx, row in enumerate(rows):
        cells = []
        for cell in row:
            fill = '<w:shd w:val="clear" w:color="auto" w:fill="DDE3F8"/>' if r_idx == 0 else ""
            cells.append(
                f"<w:tc><w:tcPr>{fill}</w:tcPr>{make_paragraph(cell, bold=(r_idx == 0))}</w:tc>"
            )
        xml_rows.append(f"<w:tr>{''.join(cells)}</w:tr>")
    return (
        "<w:tbl><w:tblPr>"
        '<w:tblBorders><w:top w:val="single" w:sz="4" w:color="BDBDBD"/>'
        '<w:bottom w:val="single" w:sz="4" w:color="BDBDBD"/>'
        '<w:left w:val="single" w:sz="4" w:color="BDBDBD"/>'
        '<w:right w:val="single" w:sz="4" w:color="BDBDBD"/>'
        '<w:insideH w:val="single" w:sz="4" w:color="D4D4D4"/>'
        '<w:insideV w:val="single" w:sz="4" w:color="D4D4D4"/></w:tblBorders>'
        f"</w:tblPr>{''.join(xml_rows)}</w:tbl>"
    )


def write_docx(filename: str, body_xml: str, page_w_twips: int = 12240, page_h_twips: int = 15840) -> Path:
    CORPUS_DIR.mkdir(parents=True, exist_ok=True)
    out = CORPUS_DIR / filename
    doc_xml = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        f"<w:body>{body_xml}"
        f'<w:sectPr><w:pgSz w:w="{page_w_twips}" w:h="{page_h_twips}"/>'
        '<w:pgMar w:top="1440" w:right="1701" w:bottom="1440" w:left="1701"/></w:sectPr>'
        "</w:body></w:document>"
    )
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml", CONTENT_TYPES_XML)
        zf.writestr("_rels/.rels", RELS_XML)
        zf.writestr("word/_rels/document.xml.rels", DOC_RELS_XML)
        zf.writestr("word/styles.xml", STYLES_XML)
        zf.writestr("word/document.xml", doc_xml)
    return out


def main() -> int:
    # 1. Oficio Ordinario Chile (21.59 cm x 33.00 cm -> 12240 x 18709 twips)
    oficio = "".join([
        make_paragraph("OFICIO ORDINARIO N.° 124 / 2026", bold=True, align="right"),
        make_paragraph("ANT.: Solicitud de infraestructura tecnológica móvil.", bold=True),
        make_paragraph("MAT.: INFORMA VALIDACIÓN DE SUITE OFIMÁTICA MULTIPLATAFORMA.", bold=True),
        make_paragraph("SANTIAGO, 13 de noviembre de 2026.", align="right"),
        make_paragraph(
            "DE : DIRECTOR DE PROYECTO OFIOFI\nA  : COMITÉ EVALUADOR DE CALIDAD",
            bold=True,
        ),
        make_paragraph(
            "1. Por medio del presente oficio se certifica la ejecución íntegra del motor de composición y "
            "renderizado para documentos OpenXML con soporte métrico en tamaño Oficio Chile y Carta.",
            align="both",
        ),
        make_table([
            ["Módulo", "Plataforma", "Estado"],
            ["wordcraft-engine", "Android + Apple", "Operativo"],
            ["Ribbon 16.115", "Tablet + iPad + Mac", "Completo"],
        ]),
    ])
    write_docx("01_oficio_ordinario_chile.docx", oficio, page_w_twips=12240, page_h_twips=18709)

    # 2. Informe Técnico con tabla presupuestaria
    informe = "".join([
        make_paragraph("Informe Técnico — Evaluación Suite OfiOfi", style="Heading1"),
        make_paragraph(
            "Este informe verifica títulos jerárquicos, fuentes métricas Carlito/Caladea y tablas con sombreado.",
        ),
        make_table([
            ["Ítem", "Cantidad", "Monto CLP"],
            ["Licencia Core", "1", "$0 (Gratuito)"],
            ["Conectores Drive / iCloud / WebDAV", "3", "$0 (Gratuito)"],
        ]),
    ])
    write_docx("02_informe_tecnico_tablas.docx", informe, page_w_twips=12240, page_h_twips=15840)

    # 3. Carta Formal Chile
    carta = "".join([
        make_paragraph("Santiago, 13 de noviembre de 2026", align="right"),
        make_paragraph("Estimados usuarios y desarrolladores:", bold=True),
        make_paragraph(
            "OfiOfi ofrece edición completa de documentos en teléfonos, tabletas, iPhone, iPad, Mac y Apple Vision Pro "
            "manteniendo compatibilidad de formatos .docx sin costos de suscripción.",
            align="both",
        ),
        make_paragraph("Atentamente,\nEquipo OfiOfi", italic=True),
    ])
    write_docx("03_carta_formal_estilos.docx", carta, page_w_twips=12240, page_h_twips=15840)

    # 4. Documento Científico / Superíndices / Fórmulas en texto
    ciencia = "".join([
        make_paragraph("Notas de Arquitectura y Paridad Tipográfica", style="Heading1"),
        make_paragraph("Velocidad objetivo: 120 Hz de desplazamiento mediante caché de teselas RGBA8888."),
        make_paragraph("Identidad matemática: E = mc2 y CO2 usando STIX Two Math como respaldo libre."),
    ])
    write_docx("04_ecuaciones_multinivel.docx", ciencia, page_w_twips=11906, page_h_twips=16838)  # A4

    print(f"Generated 4 corpus fixtures in {CORPUS_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
