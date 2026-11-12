#!/usr/bin/env python3
"""Generates specs/ribbon-schema.json aligned with Microsoft Word 16.115 (es-CL) and all 651 wordcraft commands."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

SUITE_DIR = Path(__file__).resolve().parent.parent
COMMANDS_PATH = SUITE_DIR / "specs" / "wordcraft-commands.json"
OUT_PATH = SUITE_DIR / "specs" / "ribbon-schema.json"

# Direct es-CL translations for Microsoft Word 16.115 parity (grouped neatly).
ES_CL_LABELS: dict[str, str] = {
    # File / Backstage / QAT / Title
    "file.autosave": "Autoguardado",
    "file.new": "Nuevo",
    "file.newFromTemplate": "Nuevo desde plantilla",
    "file.open": "Abrir",
    "file.save": "Guardar",
    "file.saveAs": "Guardar como",
    "file.saveTemplate": "Guardar como plantilla",
    "file.exportPdf": "Exportar a PDF",
    "file.exportPng": "Exportar página como PNG",
    "file.strictReport": "Informe Strict",
    "file.print": "Imprimir",
    "file.printPdf": "Imprimir en PDF",
    "file.printMarkup": "Imprimir con marcas",
    "file.share": "Compartir",
    "file.close": "Cerrar",
    "file.info": "Información",
    "file.properties": "Propiedades",
    "file.inspect": "Inspeccionar documento",
    "file.protect": "Proteger documento",
    "file.encrypt": "Cifrar con contraseña",
    "file.versions": "Historial de versiones",
    "file.recover": "Recuperar documentos",
    "file.compatibility": "Comprobar compatibilidad",
    "file.accessibility": "Comprobar accesibilidad",
    "file.options": "Opciones",
    "file.rename": "Cambiar nombre",
    "file.setAuthor": "Nombre de usuario",
    "file.listTemplates": "Plantillas personales",
    "file.privacy": "Configuración de privacidad",
    "file.privacyOptions": "Opciones de privacidad",
    "edit.undo": "Deshacer",
    "edit.undoTo": "Deshacer acciones",
    "edit.undoHistory": "Historial de deshacer",
    "edit.redo": "Repetir",
    "edit.repeat": "Repetir acción",
    "tools.search": "Buscar en el documento",
    "tools.customizeRibbon": "Personalizar cinta",
    "tools.customizeKeyboard": "Personalizar teclado",
    "tools.autocorrect": "Opciones de Autocorrección",
    "review.proofing": "Revisar ortografía mientras escribe",
    "proof.packs": "Paquetes de idioma",
    "proof.installPack": "Instalar paquete de idioma",
    "proof.removePack": "Quitar paquete de idioma",
    "review.language.preferred": "Idioma preferido",
    "text.overtype": "Sobrescribir",
    "view.numeral": "Numerales",

    # Inicio - Portapapeles
    "edit.paste": "Pegar",
    "edit.pasteText": "Conservar solo texto",
    "edit.pasteMerge": "Combinar formato",
    "edit.pasteSpecial": "Pegado especial",
    "edit.cut": "Cortar",
    "edit.copy": "Copiar",
    "edit.formatPainter": "Copiar formato",
    "edit.copyFormat": "Copiar atributos",
    "edit.pasteFormat": "Pegar atributos",
    "edit.clipboardPane": "Portapapeles",

    # Inicio - Fuente
    "format.font": "Fuente",
    "format.size": "Tamaño de fuente",
    "format.growFont": "Aumentar tamaño de fuente",
    "format.shrinkFont": "Disminuir tamaño de fuente",
    "format.growFont1": "Aumentar 1 punto",
    "format.shrinkFont1": "Disminuir 1 punto",
    "format.changeCase": "Cambiar mayúsculas y minúsculas",
    "format.clear": "Borrar todo el formato",
    "format.bold": "Negrita",
    "format.italic": "Cursiva",
    "format.underline": "Subrayado",
    "format.doubleUnderline": "Subrayado doble",
    "format.wordUnderline": "Solo palabras",
    "format.underlineColor": "Color de subrayado",
    "format.strikethrough": "Tachado",
    "format.doubleStrikethrough": "Tachado doble",
    "format.subscript": "Subíndice",
    "format.superscript": "Superíndice",
    "format.textEffects": "Efectos de texto y tipografía",
    "format.highlight": "Color de resaltado del texto",
    "format.color": "Color de fuente",
    "format.fontDialog": "Cuadro de diálogo Fuente",
    "format.allCaps": "Mayúsculas",
    "format.smallCaps": "Versalitas",
    "format.hidden": "Oculto",
    "format.outline": "Contorno",
    "format.shadow": "Sombra",
    "format.emboss": "Relieve",
    "format.engrave": "Grabado",
    "format.border": "Borde de caracteres",
    "format.shading": "Sombreado de caracteres",
    "format.enclose": "Enmarcar caracteres",
    "format.phonetic": "Guía fonética",
    "format.spacing": "Espaciado entre caracteres",
    "format.scale": "Escala horizontal",
    "format.position": "Posición",
    "format.resetChar": "Restablecer formato",
    "font.add": "Agregar fuente…",
    "font.loadSystem": "Cargar fuentes del sistema…",

    # Inicio - Párrafo
    "para.bullets": "Viñetas",
    "para.numbering": "Numeración",
    "para.multilevel": "Lista multinivel",
    "para.outdent": "Disminuir sangría",
    "para.indent": "Aumentar sangría",
    "para.sort": "Ordenar",
    "view.marks": "Mostrar todo ¶",
    "para.alignLeft": "Alinear a la izquierda",
    "para.alignCenter": "Centrar",
    "para.alignRight": "Alinear a la derecha",
    "para.justify": "Justificar",
    "para.distribute": "Distribuido",
    "para.lineSpacing": "Espaciado entre líneas y párrafos",
    "para.single": "Interlineado 1,0",
    "para.oneAndHalf": "Interlineado 1,5",
    "para.double": "Interlineado 2,0",
    "para.addSpaceBefore": "Agregar espacio antes del párrafo",
    "para.removeSpaceAfter": "Quitar espacio después del párrafo",
    "para.shading": "Sombreado",
    "para.borders": "Bordes",
    "insert.horizontalLine": "Línea horizontal",
    "para.dialog": "Configuración de párrafo",
    "para.ltr": "Texto de izquierda a derecha",
    "para.rtl": "Texto de derecha a izquierda",
    "para.tabs": "Tabulaciones",

    # Inicio - Estilos
    "para.normal": "Normal",
    "para.heading1": "Título 1",
    "para.heading2": "Título 2",
    "para.heading3": "Título 3",
    "styles.list": "Galería de estilos",
    "styles.pane": "Panel de estilos",
    "view.stylesPane": "Panel de estilos",
    "styles.create": "Crear un estilo",
    "styles.modify": "Modificar estilo",
    "styles.manage": "Administrar estilos",
    "styles.inspector": "Inspector de estilos",
    "styles.updateToMatch": "Actualizar para que coincida",

    # Inicio - Edición & Voz
    "edit.find": "Buscar",
    "edit.findReplace": "Buscar y reemplazar",
    "edit.replace": "Reemplazar",
    "edit.goto": "Ir a",
    "select.all": "Seleccionar todo",
    "select.objects": "Seleccionar objetos",
    "select.similar": "Seleccionar texto con formato similar",
    "review.editor": "Editor",

    # Insertar
    "insert.coverPage": "Portada",
    "insert.blankPage": "Página en blanco",
    "insert.pageBreak": "Salto de página",
    "insert.table": "Tabla",
    "table.draw": "Dibujar tabla",
    "table.fromText": "Convertir texto en tabla",
    "table.quick": "Tablas rápidas",
    "insert.spreadsheet": "Hoja de cálculo",
    "insert.picture": "Imágenes",
    "insert.shape": "Formas",
    "insert.icon": "Iconos",
    "insert.smartArt": "SmartArt",
    "insert.chart": "Gráfico",
    "insert.canvas": "Lienzo de dibujo",
    "insert.link": "Vínculo",
    "insert.removeLink": "Quitar hipervínculo",
    "insert.bookmark": "Marcador",
    "insert.crossReference": "Referencia cruzada",
    "insert.header": "Encabezado",
    "insert.footer": "Pie de página",
    "insert.pageNumber": "Número de página",
    "insert.textBox": "Cuadro de texto",
    "insert.quickParts": "Elementos rápidos",
    "insert.wordArt": "WordArt",
    "insert.dropCap": "Letra capital",
    "insert.signatureLine": "Línea de firma",
    "insert.dateTime": "Fecha y hora",
    "insert.object": "Objeto",
    "insert.textFromFile": "Insertar texto de archivo",
    "insert.equation": "Ecuación",
    "insert.symbol": "Símbolo",

    # Dibujar
    "draw.select": "Seleccionar",
    "draw.lasso": "Selección de lazo",
    "draw.eraser": "Borrador",
    "draw.pen": "Pluma",
    "draw.pencil": "Lápiz",
    "draw.highlighter": "Resaltador",
    "draw.addPen": "Agregar lápiz",
    "draw.inkToShape": "Entrada a forma",
    "draw.convertInk": "Convertir a formas",
    "draw.replay": "Reproducción de entrada",

    # Diseño
    "design.theme": "Temas",
    "design.styleSet": "Conjuntos de estilos",
    "design.themeColors": "Colores",
    "design.themeFonts": "Fuentes",
    "design.paragraphSpacing": "Espaciado entre párrafos",
    "design.effects": "Efectos",
    "design.setDefault": "Establecer como predeterminada",
    "design.watermark": "Marca de agua",
    "design.pageColor": "Color de página",
    "design.pageBorders": "Bordes de página",

    # Disposición
    "layout.margins": "Márgenes",
    "layout.orientation": "Orientación",
    "layout.size": "Tamaño",
    "layout.columns": "Columnas",
    "layout.break": "Saltos",
    "layout.lineNumbers": "Números de línea",
    "layout.hyphenation": "Guiones",
    "layout.pageSetup": "Configurar página",
    "para.indents": "Sangría",
    "para.spacing": "Espaciado",
    "arrange.position": "Posición",
    "arrange.wrap": "Ajustar texto",
    "arrange.bringForward": "Traer adelante",
    "arrange.sendBackward": "Enviar atrás",
    "arrange.selectionPane": "Panel de selección",
    "arrange.align": "Alinear",
    "arrange.group": "Agrupar",
    "arrange.rotate": "Girar",

    # Referencias
    "references.toc": "Tabla de contenido",
    "references.addText": "Agregar texto",
    "references.updateToc": "Actualizar tabla",
    "references.footnote": "Insertar nota al pie",
    "references.endnote": "Insertar nota al final",
    "references.nextFootnote": "Siguiente nota al pie",
    "references.notes": "Mostrar notas",
    "references.citation": "Insertar cita",
    "references.sources": "Administrar fuentes",
    "references.citationStyle": "Estilo",
    "references.bibliography": "Bibliografía",
    "references.caption": "Insertar título",
    "references.tableOfFigures": "Tabla de ilustraciones",
    "references.markEntry": "Marcar entrada",
    "references.index": "Insertar índice",
    "references.markCitation": "Marcar cita",
    "references.tableOfAuthorities": "Tabla de autoridades",
    "references.researcher": "Búsqueda inteligente",

    # Correspondencia
    "mailings.envelopes": "Sobres",
    "mailings.labels": "Etiquetas",
    "mailings.start": "Iniciar combinación",
    "mailings.recipients": "Seleccionar destinatarios",
    "mailings.editRecipients": "Editar lista de destinatarios",
    "mailings.highlightFields": "Resaltar campos",
    "mailings.addressBlock": "Bloque de direcciones",
    "mailings.greetingLine": "Línea de saludo",
    "mailings.insertField": "Insertar campo combinado",
    "mailings.rules": "Reglas",
    "mailings.matchFields": "Asignar campos",
    "mailings.preview": "Vista previa de resultados",
    "mailings.first": "Primer registro",
    "mailings.previous": "Registro anterior",
    "mailings.goToRecord": "Ir al registro",
    "mailings.next": "Siguiente registro",
    "mailings.last": "Último registro",
    "mailings.findRecipient": "Buscar destinatario",
    "mailings.checkErrors": "Comprobar errores",
    "mailings.finish": "Finalizar y combinar",

    # Revisar
    "review.spelling": "Ortografía y gramática",
    "review.thesaurus": "Sinónimos",
    "review.wordCount": "Contar palabras",
    "review.readAloud": "Leer en voz alta",
    "readAloud.playPause": "Reproducir / Pausar",
    "view.accessibilityPane": "Panel de accesibilidad",
    "review.language": "Idioma",
    "review.newComment": "Nuevo comentario",
    "review.deleteComment": "Eliminar comentario",
    "review.previousComment": "Comentario anterior",
    "review.nextComment": "Siguiente comentario",
    "review.resolveComment": "Resolver comentario",
    "review.comments": "Mostrar comentarios",
    "review.trackChanges": "Control de cambios",
    "review.markup": "Mostrar para revisión",
    "review.showMarkup": "Mostrar revisiones",
    "review.changes": "Panel de revisiones",
    "review.accept": "Aceptar",
    "review.acceptAll": "Aceptar todos los cambios",
    "review.reject": "Rechazar",
    "review.rejectAll": "Rechazar todos los cambios",
    "review.previousChange": "Cambio anterior",
    "review.nextChange": "Siguiente cambio",
    "review.compare": "Comparar",
    "review.combine": "Combinar",
    "review.restrict": "Restringir edición",
    "review.hideInk": "Ocultar entrada de lápiz",

    # Vista
    "view.readMode": "Modo lectura",
    "view.printLayout": "Diseño de impresión",
    "view.webLayout": "Diseño web",
    "view.outline": "Esquema",
    "view.draft": "Borrador",
    "view.immersive": "Lector inmersivo",
    "view.focus": "Concentración",
    "view.vertical": "Vertical",
    "view.sideToSide": "En paralelo",
    "view.ruler": "Regla",
    "view.gridlines": "Líneas de la cuadrícula",
    "view.navigationPane": "Panel de navegación",
    "view.zoom": "Zoom",
    "view.zoom100": "100 %",
    "view.zoomIn": "Acercar",
    "view.zoomOut": "Alejar",
    "view.onePage": "Una página",
    "view.multiplePages": "Varias páginas",
    "view.pageWidth": "Ancho de página",
    "view.darkMode": "Cambiar modo oscuro",
    "view.interfaceTheme": "Tema de interfaz",
    "view.newWindow": "Nueva ventana",
    "view.arrangeAll": "Organizar todo",
    "view.split": "Dividir",
    "view.sideBySide": "Ver en paralelo",
    "view.macros": "Macros",
    "tools.macros": "Macros",
    "tools.recordMacro": "Grabar macro",
    "tools.templates": "Plantillas y complementos",

    # Contextual: Table Design & Table Layout
    "table.style": "Estilos de tabla",
    "table.look": "Opciones de estilo de tabla",
    "table.shading": "Sombreado",
    "table.borders": "Bordes",
    "table.borderPainter": "Copiar borde",
    "table.selectTable": "Seleccionar tabla",
    "table.selectRow": "Seleccionar fila",
    "table.selectColumn": "Seleccionar columna",
    "table.selectCell": "Seleccionar celda",
    "table.viewGridlines": "Ver cuadrículas",
    "table.properties": "Propiedades",
    "table.eraser": "Borrador",
    "table.insertRowAbove": "Insertar arriba",
    "table.insertRowBelow": "Insertar debajo",
    "table.insertColumnLeft": "Insertar a la izquierda",
    "table.insertColumnRight": "Insertar a la derecha",
    "table.deleteRow": "Eliminar filas",
    "table.deleteColumn": "Eliminar columnas",
    "table.deleteCells": "Eliminar celdas",
    "table.deleteTable": "Eliminar tabla",
    "table.merge": "Combinar celdas",
    "table.split": "Dividir celdas",
    "table.splitTable": "Dividir tabla",
    "table.autofit": "Autoajustar",
    "table.columnWidth": "Ancho de columna",
    "table.rowHeight": "Alto de fila",
    "table.distributeRows": "Distribuir filas",
    "table.distributeColumns": "Distribuir columnas",
    "table.cellAlign": "Alineación",
    "table.textDirection": "Dirección del texto",
    "table.cellMargins": "Márgenes de celda",
    "table.sort": "Ordenar",
    "table.repeatHeader": "Repetir filas de título",
    "table.toText": "Convertir a texto",
    "table.formula": "Fórmula",

    # Contextual: Picture Format
    "picture.removeBackground": "Quitar fondo",
    "picture.corrections": "Correcciones",
    "picture.color": "Color",
    "picture.effects": "Efectos artísticos",
    "picture.transparency": "Transparencia",
    "picture.compress": "Comprimir imágenes",
    "picture.change": "Cambiar imagen",
    "picture.reset": "Restablecer imagen",
    "picture.style": "Estilos de imagen",
    "picture.border": "Borde de imagen",
    "picture.altText": "Texto alternativo",
    "picture.crop": "Recortar",
    "picture.size": "Tamaño",

    # Contextual: Shape Format
    "shape.change": "Cambiar forma",
    "shape.fill": "Relleno de forma",
    "shape.outline": "Contorno de forma",
    "shape.effects": "Efectos de forma",
    "wordArt.style": "Estilos rápidos de WordArt",
    "wordArt.textFill": "Relleno de texto",
    "wordArt.textOutline": "Contorno de texto",
    "wordArt.transform": "Transformar",
    "shape.textDirection": "Dirección del texto",
    "shape.alignText": "Alinear texto",
    "shape.link": "Crear vínculo",
    "object.altText": "Texto alternativo",

    # Contextual: Equation
    "equation.gallery": "Ecuación",
    "equation.convert": "Convertir",
    "equation.inputFormat": "Formato de entrada",
    "equation.normalText": "Texto normal",
    "equation.insertSymbol": "Símbolos",
    "equation.insertStructure": "Estructuras",
    "equation.justify": "Justificación",
    "equation.display": "Modo presentación",
    "equation.number": "Numeración de ecuación",
    "equation.exit": "Salir de ecuación",

    # Contextual: Chart Design & SmartArt Design
    "chart.editData": "Modificar datos",
    "chart.type": "Cambiar tipo de gráfico",
    "chart.title": "Título del gráfico",
    "chart.legend": "Leyenda",
    "chart.dataLabels": "Etiquetas de datos",
    "smartArt.addShape": "Agregar forma",
    "smartArt.promote": "Promover",
    "smartArt.demote": "Disminuir nivel",
    "smartArt.textPane": "Panel de texto",
    "smartArt.layout": "Diseños",
    "smartArt.colors": "Cambiar colores",
    "smartArt.reset": "Restablecer gráfico",

    # Contextual: Header & Footer / Outlining / Reading
    "hf.previous": "Anterior",
    "hf.next": "Siguiente",
    "layout.differentFirstPage": "Primera página diferente",
    "layout.differentOddEven": "Páginas pares e impares diferentes",
    "layout.linkToPrevious": "Vincular a la anterior",
    "hf.position": "Posición",
    "insert.closeHeader": "Cerrar encabezado y pie",
    "outline.promote": "Promover",
    "outline.demote": "Disminuir nivel",
    "outline.moveUp": "Subir",
    "outline.moveDown": "Bajar",
    "outline.expand": "Expandir",
    "outline.collapse": "Contraer",
    "outline.showLevel": "Mostrar nivel",
    "outline.firstLineOnly": "Mostrar solo primera línea",
    "outline.showFormatting": "Mostrar formato de texto",
    "outline.close": "Cerrar vista Esquema",
    "view.readColumnWidth": "Ancho de columna",
    "view.readTextSize": "Tamaño de texto",
    "view.readLineSpacing": "Espaciado entre líneas",
    "view.readPageColor": "Color de página",
    "font.missing": "Fuentes faltantes",
}


def icon_for(cmd_id: str) -> str:
    return "ic_" + cmd_id.replace(".", "_")


def ctrl(
    catalog: dict[str, dict[str, Any]],
    cmd_id: str,
    kind: str = "small_button",
    *,
    row: int | str = 0,
    es_cl: str | None = None,
    submenu: list[str] | None = None,
    params: dict[str, Any] | None = None,
) -> dict[str, Any]:
    meta = catalog[cmd_id]
    en_us = meta["label"]
    es_label = es_cl or ES_CL_LABELS.get(cmd_id, en_us)
    item: dict[str, Any] = {
        "command_id": cmd_id,
        "kind": kind,
        "row": row,
        "icon": icon_for(cmd_id),
        "labels": {"es-CL": es_label, "en-US": en_us},
    }
    if params is not None:
        item["params_template"] = params
    if submenu:
        item["submenu"] = [
            ctrl(catalog, sub_id, "menu_item", row=0) for sub_id in submenu
        ]
    return item


def shell_ctrl(
    ui_id: str, es_cl: str, en_us: str, kind: str = "small_button", row: int | str = 0
) -> dict[str, Any]:
    return {
        "command_id": ui_id,
        "kind": kind,
        "row": row,
        "icon": "ic_" + ui_id.replace(".", "_"),
        "labels": {"es-CL": es_cl, "en-US": en_us},
    }


def make_group(
    gid: str, es_cl: str, en_us: str, controls: list[dict[str, Any]], dialog_cmd: str | None = None
) -> dict[str, Any]:
    grp: dict[str, Any] = {
        "id": gid,
        "labels": {"es-CL": es_cl, "en-US": en_us},
        "controls": controls,
    }
    if dialog_cmd:
        grp["dialog_command_id"] = dialog_cmd
    return grp


def main() -> None:
    raw = json.loads(COMMANDS_PATH.read_text(encoding="utf-8"))
    catalog = {c["id"]: c for c in raw["commands"]}

    # Core Tabs (9 matching Word 16.115 es-CL)
    tabs = [
        {
            "id": "home",
            "labels": {"es-CL": "Inicio", "en-US": "Home"},
            "groups": [
                make_group(
                    "clipboard",
                    "Portapapeles",
                    "Clipboard",
                    [
                        ctrl(catalog, "edit.paste", "split_button", row="full", submenu=["edit.pasteMerge", "edit.pasteText", "edit.pasteSpecial"]),
                        ctrl(catalog, "edit.cut", "small_button", row=0),
                        ctrl(catalog, "edit.copy", "small_button", row=1),
                        ctrl(catalog, "edit.formatPainter", "small_button", row=0),
                    ],
                    dialog_cmd="edit.clipboardPane",
                ),
                make_group(
                    "font",
                    "Fuente",
                    "Font",
                    [
                        ctrl(catalog, "format.font", "font_picker", row=0, submenu=["font.add", "font.loadSystem"]),
                        ctrl(catalog, "format.size", "size_picker", row=0),
                        ctrl(catalog, "format.growFont", "small_button", row=0),
                        ctrl(catalog, "format.shrinkFont", "small_button", row=0),
                        ctrl(catalog, "format.changeCase", "dropdown", row=0),
                        ctrl(catalog, "format.clear", "small_button", row=0),
                        ctrl(catalog, "format.bold", "toggle", row=1),
                        ctrl(catalog, "format.italic", "toggle", row=1),
                        ctrl(catalog, "format.underline", "split_button", row=1, submenu=["format.doubleUnderline", "format.wordUnderline", "format.underlineColor"]),
                        ctrl(catalog, "format.strikethrough", "toggle", row=1),
                        ctrl(catalog, "format.subscript", "toggle", row=1),
                        ctrl(catalog, "format.superscript", "toggle", row=1),
                        ctrl(catalog, "format.textEffects", "dropdown", row=1, submenu=["format.outline", "format.shadow"]),
                        ctrl(catalog, "format.highlight", "color_picker", row=1),
                        ctrl(catalog, "format.color", "color_picker", row=1),
                    ],
                    dialog_cmd="format.fontDialog",
                ),
                make_group(
                    "paragraph",
                    "Párrafo",
                    "Paragraph",
                    [
                        ctrl(catalog, "para.bullets", "split_button", row=0, submenu=["para.defineBullet", "para.listLevel"]),
                        ctrl(catalog, "para.numbering", "split_button", row=0, submenu=["para.defineNumber", "para.restartNumbering", "para.setNumberingValue"]),
                        ctrl(catalog, "para.multilevel", "dropdown", row=0, submenu=["list.multilevelPreset", "list.define"]),
                        ctrl(catalog, "para.outdent", "small_button", row=0),
                        ctrl(catalog, "para.indent", "small_button", row=0),
                        ctrl(catalog, "para.sort", "small_button", row=0),
                        ctrl(catalog, "view.marks", "toggle", row=0),
                        ctrl(catalog, "para.alignLeft", "toggle", row=1),
                        ctrl(catalog, "para.alignCenter", "toggle", row=1),
                        ctrl(catalog, "para.alignRight", "toggle", row=1),
                        ctrl(catalog, "para.justify", "toggle", row=1),
                        ctrl(catalog, "para.lineSpacing", "dropdown", row=1, submenu=["para.single", "para.oneAndHalf", "para.double", "para.addSpaceBefore", "para.removeSpaceAfter"]),
                        ctrl(catalog, "para.shading", "color_picker", row=1),
                        ctrl(catalog, "para.borders", "dropdown", row=1, submenu=["insert.horizontalLine"]),
                    ],
                    dialog_cmd="para.dialog",
                ),
                make_group(
                    "styles",
                    "Estilos",
                    "Styles",
                    [
                        ctrl(catalog, "styles.list", "style_gallery", row="full"),
                        ctrl(catalog, "para.normal", "small_button", row=0),
                        ctrl(catalog, "para.heading1", "small_button", row=0),
                        ctrl(catalog, "para.heading2", "small_button", row=1),
                        ctrl(catalog, "para.heading3", "small_button", row=1),
                        ctrl(catalog, "styles.pane", "large_button", row="full", submenu=["styles.create", "styles.modify", "styles.manage", "styles.inspector"]),
                    ],
                    dialog_cmd="styles.pane",
                ),
                make_group(
                    "editing",
                    "Edición",
                    "Editing",
                    [
                        ctrl(catalog, "edit.find", "large_button", row="full", submenu=["edit.advancedFind", "edit.goto"]),
                        ctrl(catalog, "edit.replace", "small_button", row=0),
                        ctrl(catalog, "select.all", "dropdown", row=1, submenu=["select.objects", "select.similar"]),
                    ],
                ),
                make_group(
                    "editor",
                    "Editor",
                    "Editor",
                    [
                        ctrl(catalog, "review.editor", "large_button", row="full"),
                        ctrl(catalog, "review.spelling", "small_button", row=0),
                    ],
                ),
            ],
        },
        {
            "id": "insert",
            "labels": {"es-CL": "Insertar", "en-US": "Insert"},
            "groups": [
                make_group("pages", "Páginas", "Pages", [
                    ctrl(catalog, "insert.coverPage", "large_button", row="full"),
                    ctrl(catalog, "insert.blankPage", "large_button", row="full"),
                    ctrl(catalog, "insert.pageBreak", "large_button", row="full"),
                ]),
                make_group("tables", "Tablas", "Tables", [
                    ctrl(catalog, "insert.table", "table_grid", row="full", submenu=["table.draw", "table.fromText", "table.quick", "insert.spreadsheet"]),
                ]),
                make_group("illustrations", "Ilustraciones", "Illustrations", [
                    ctrl(catalog, "insert.picture", "large_button", row="full"),
                    ctrl(catalog, "insert.shape", "large_button", row="full"),
                    ctrl(catalog, "insert.icon", "large_button", row="full"),
                    ctrl(catalog, "insert.smartArt", "large_button", row="full"),
                    ctrl(catalog, "insert.chart", "large_button", row="full"),
                    ctrl(catalog, "insert.canvas", "small_button", row=0),
                ]),
                make_group("links", "Vínculos", "Links", [
                    ctrl(catalog, "insert.link", "large_button", row="full", submenu=["insert.removeLink"]),
                    ctrl(catalog, "insert.bookmark", "small_button", row=0),
                    ctrl(catalog, "insert.crossReference", "small_button", row=1),
                ]),
                make_group("header_footer", "Encabezado y pie de página", "Header & Footer", [
                    ctrl(catalog, "insert.header", "large_button", row="full", submenu=["insert.editHeader", "insert.removeHeader"]),
                    ctrl(catalog, "insert.footer", "large_button", row="full", submenu=["insert.editFooter", "insert.removeFooter"]),
                    ctrl(catalog, "insert.pageNumber", "large_button", row="full", submenu=["layout.pageNumberFormat"]),
                ]),
                make_group("text", "Texto", "Text", [
                    ctrl(catalog, "insert.textBox", "large_button", row="full"),
                    ctrl(catalog, "insert.quickParts", "small_button", row=0, submenu=["insert.autoText", "insert.docProperty", "insert.field"]),
                    ctrl(catalog, "insert.wordArt", "small_button", row=1),
                    ctrl(catalog, "insert.dropCap", "small_button", row=0),
                    ctrl(catalog, "insert.signatureLine", "small_button", row=1),
                    ctrl(catalog, "insert.dateTime", "small_button", row=0),
                    ctrl(catalog, "insert.object", "small_button", row=1, submenu=["insert.textFromFile"]),
                ]),
                make_group("symbols", "Símbolos", "Symbols", [
                    ctrl(catalog, "insert.equation", "large_button", row="full"),
                    ctrl(catalog, "insert.symbol", "large_button", row="full", submenu=["text.copyright", "text.registered", "text.trademark", "text.emDash", "text.nbsp"]),
                ]),
            ],
        },
        {
            "id": "draw",
            "labels": {"es-CL": "Dibujar", "en-US": "Draw"},
            "groups": [
                make_group("draw_tools", "Herramientas", "Tools", [
                    ctrl(catalog, "draw.select", "large_button", row="full"),
                    ctrl(catalog, "draw.lasso", "large_button", row="full"),
                    ctrl(catalog, "draw.eraser", "large_button", row="full"),
                ]),
                make_group("pens", "Lápices", "Pens", [
                    ctrl(catalog, "draw.pen", "large_button", row="full"),
                    ctrl(catalog, "draw.pencil", "large_button", row="full"),
                    ctrl(catalog, "draw.highlighter", "large_button", row="full"),
                    ctrl(catalog, "draw.addPen", "small_button", row=0),
                ]),
                make_group("convert", "Convertir", "Convert", [
                    ctrl(catalog, "draw.inkToShape", "large_button", row="full"),
                    ctrl(catalog, "draw.convertInk", "small_button", row=0),
                ]),
                make_group("replay", "Reproducción", "Replay", [
                    ctrl(catalog, "draw.replay", "large_button", row="full"),
                ]),
            ],
        },
        {
            "id": "design",
            "labels": {"es-CL": "Diseño", "en-US": "Design"},
            "groups": [
                make_group("doc_formatting", "Formato del documento", "Document Formatting", [
                    ctrl(catalog, "design.theme", "large_button", row="full"),
                    ctrl(catalog, "design.styleSet", "style_gallery", row="full"),
                    ctrl(catalog, "design.themeColors", "dropdown", row=0),
                    ctrl(catalog, "design.themeFonts", "dropdown", row=1),
                    ctrl(catalog, "design.paragraphSpacing", "dropdown", row=0),
                    ctrl(catalog, "design.effects", "dropdown", row=1),
                    ctrl(catalog, "design.setDefault", "small_button", row=0),
                ]),
                make_group("page_bg", "Fondo de página", "Page Background", [
                    ctrl(catalog, "design.watermark", "large_button", row="full"),
                    ctrl(catalog, "design.pageColor", "color_picker", row="full"),
                    ctrl(catalog, "design.pageBorders", "large_button", row="full"),
                ]),
            ],
        },
        {
            "id": "layout",
            "labels": {"es-CL": "Disposición", "en-US": "Layout"},
            "groups": [
                make_group(
                    "page_setup",
                    "Configurar página",
                    "Page Setup",
                    [
                        ctrl(catalog, "layout.margins", "large_button", row="full"),
                        ctrl(catalog, "layout.orientation", "large_button", row="full"),
                        ctrl(catalog, "layout.size", "large_button", row="full"),
                        ctrl(catalog, "layout.columns", "large_button", row="full"),
                        ctrl(catalog, "layout.break", "small_button", row=0),
                        ctrl(catalog, "layout.lineNumbers", "small_button", row=1),
                        ctrl(catalog, "layout.hyphenation", "small_button", row=0),
                    ],
                    dialog_cmd="layout.pageSetup",
                ),
                make_group(
                    "layout_paragraph",
                    "Párrafo",
                    "Paragraph",
                    [
                        ctrl(catalog, "para.indents", "spin_box", row=0),
                        ctrl(catalog, "para.spacing", "spin_box", row=1),
                    ],
                    dialog_cmd="para.dialog",
                ),
                make_group("arrange", "Organizar", "Arrange", [
                    ctrl(catalog, "arrange.position", "large_button", row="full"),
                    ctrl(catalog, "arrange.wrap", "large_button", row="full"),
                    ctrl(catalog, "arrange.bringForward", "small_button", row=0),
                    ctrl(catalog, "arrange.sendBackward", "small_button", row=1),
                    ctrl(catalog, "arrange.selectionPane", "small_button", row=0),
                    ctrl(catalog, "arrange.align", "small_button", row=1),
                    ctrl(catalog, "arrange.group", "small_button", row=0),
                    ctrl(catalog, "arrange.rotate", "small_button", row=1),
                ]),
            ],
        },
        {
            "id": "references",
            "labels": {"es-CL": "Referencias", "en-US": "References"},
            "groups": [
                make_group("toc", "Tabla de contenido", "Table of Contents", [
                    ctrl(catalog, "references.toc", "large_button", row="full"),
                    ctrl(catalog, "references.addText", "small_button", row=0),
                    ctrl(catalog, "references.updateToc", "small_button", row=1),
                ]),
                make_group("footnotes", "Notas al pie", "Footnotes", [
                    ctrl(catalog, "references.footnote", "large_button", row="full"),
                    ctrl(catalog, "references.endnote", "small_button", row=0),
                    ctrl(catalog, "references.nextFootnote", "small_button", row=1),
                    ctrl(catalog, "references.notes", "small_button", row=0),
                ], dialog_cmd="references.noteOptions"),
                make_group("research", "Investigación", "Research", [
                    ctrl(catalog, "references.researcher", "large_button", row="full"),
                ]),
                make_group("citations", "Citas y bibliografía", "Citations & Bibliography", [
                    ctrl(catalog, "references.citation", "large_button", row="full"),
                    ctrl(catalog, "references.sources", "small_button", row=0),
                    ctrl(catalog, "references.citationStyle", "dropdown", row=1),
                    ctrl(catalog, "references.bibliography", "large_button", row="full"),
                ]),
                make_group("captions", "Títulos", "Captions", [
                    ctrl(catalog, "references.caption", "large_button", row="full"),
                    ctrl(catalog, "references.tableOfFigures", "small_button", row=0),
                    ctrl(catalog, "references.updateFigures", "small_button", row=1),
                ]),
                make_group("index", "Índice", "Index", [
                    ctrl(catalog, "references.markEntry", "large_button", row="full"),
                    ctrl(catalog, "references.index", "small_button", row=0),
                    ctrl(catalog, "references.updateIndex", "small_button", row=1),
                ]),
                make_group("authorities", "Tabla de autoridades", "Table of Authorities", [
                    ctrl(catalog, "references.markCitation", "large_button", row="full"),
                    ctrl(catalog, "references.tableOfAuthorities", "small_button", row=0),
                    ctrl(catalog, "references.updateAuthorities", "small_button", row=1),
                ]),
            ],
        },
        {
            "id": "mailings",
            "labels": {"es-CL": "Correspondencia", "en-US": "Mailings"},
            "groups": [
                make_group("create", "Crear", "Create", [
                    ctrl(catalog, "mailings.envelopes", "large_button", row="full"),
                    ctrl(catalog, "mailings.labels", "large_button", row="full"),
                ]),
                make_group("start_mail_merge", "Iniciar combinación de correspondencia", "Start Mail Merge", [
                    ctrl(catalog, "mailings.start", "large_button", row="full"),
                    ctrl(catalog, "mailings.recipients", "large_button", row="full"),
                    ctrl(catalog, "mailings.editRecipients", "large_button", row="full"),
                ]),
                make_group("write_insert", "Escribir e insertar campos", "Write & Insert Fields", [
                    ctrl(catalog, "mailings.highlightFields", "toggle", row="full"),
                    ctrl(catalog, "mailings.addressBlock", "large_button", row="full"),
                    ctrl(catalog, "mailings.greetingLine", "large_button", row="full"),
                    ctrl(catalog, "mailings.insertField", "large_button", row="full"),
                    ctrl(catalog, "mailings.rules", "small_button", row=0),
                    ctrl(catalog, "mailings.matchFields", "small_button", row=1),
                ]),
                make_group("preview_results", "Vista previa de resultados", "Preview Results", [
                    ctrl(catalog, "mailings.preview", "toggle", row="full"),
                    ctrl(catalog, "mailings.first", "small_button", row=0),
                    ctrl(catalog, "mailings.previous", "small_button", row=0),
                    ctrl(catalog, "mailings.goToRecord", "spin_box", row=0),
                    ctrl(catalog, "mailings.next", "small_button", row=0),
                    ctrl(catalog, "mailings.last", "small_button", row=0),
                    ctrl(catalog, "mailings.findRecipient", "small_button", row=1),
                    ctrl(catalog, "mailings.checkErrors", "small_button", row=1),
                ]),
                make_group("finish", "Finalizar", "Finish", [
                    ctrl(catalog, "mailings.finish", "large_button", row="full", submenu=["mailings.printDocuments"]),
                ]),
            ],
        },
        {
            "id": "review",
            "labels": {"es-CL": "Revisar", "en-US": "Review"},
            "groups": [
                make_group("proofing", "Revisión", "Proofing", [
                    ctrl(catalog, "review.spelling", "large_button", row="full"),
                    ctrl(catalog, "review.thesaurus", "small_button", row=0),
                    ctrl(catalog, "review.wordCount", "small_button", row=1),
                ]),
                make_group("speech", "Voz", "Speech", [
                    ctrl(catalog, "review.readAloud", "large_button", row="full", submenu=["readAloud.playPause", "readAloud.previous", "readAloud.next", "readAloud.stop"]),
                ]),
                make_group("accessibility", "Accesibilidad", "Accessibility", [
                    ctrl(catalog, "file.accessibility", "large_button", row="full"),
                ]),
                make_group("language", "Idioma", "Language", [
                    ctrl(catalog, "review.language", "large_button", row="full"),
                ]),
                make_group("comments", "Comentarios", "Comments", [
                    ctrl(catalog, "review.newComment", "large_button", row="full"),
                    ctrl(catalog, "review.deleteComment", "small_button", row=0),
                    ctrl(catalog, "review.previousComment", "small_button", row=0),
                    ctrl(catalog, "review.nextComment", "small_button", row=0),
                    ctrl(catalog, "review.resolveComment", "small_button", row=1),
                    ctrl(catalog, "review.comments", "toggle", row=1),
                ]),
                make_group("tracking", "Seguimiento", "Tracking", [
                    ctrl(catalog, "review.trackChanges", "large_button", row="full"),
                    ctrl(catalog, "review.markup", "dropdown", row=0),
                    ctrl(catalog, "review.showMarkup", "dropdown", row=1),
                    ctrl(catalog, "review.changes", "small_button", row=0),
                ], dialog_cmd="review.trackingOptions"),
                make_group("changes", "Cambios", "Changes", [
                    ctrl(catalog, "review.accept", "split_button", row="full", submenu=["review.acceptAll"]),
                    ctrl(catalog, "review.reject", "split_button", row="full", submenu=["review.rejectAll"]),
                    ctrl(catalog, "review.previousChange", "small_button", row=0),
                    ctrl(catalog, "review.nextChange", "small_button", row=1),
                ]),
                make_group("compare", "Comparar", "Compare", [
                    ctrl(catalog, "review.compare", "large_button", row="full", submenu=["review.combine"]),
                ]),
                make_group("protect", "Proteger", "Protect", [
                    ctrl(catalog, "review.restrict", "large_button", row="full"),
                ]),
                make_group("review_ink", "Entrada de lápiz", "Ink", [
                    ctrl(catalog, "review.hideInk", "toggle", row="full"),
                ]),
            ],
        },
        {
            "id": "view",
            "labels": {"es-CL": "Vista", "en-US": "View"},
            "groups": [
                make_group("views", "Vistas", "Views", [
                    ctrl(catalog, "view.readMode", "large_button", row="full"),
                    ctrl(catalog, "view.printLayout", "large_button", row="full"),
                    ctrl(catalog, "view.webLayout", "large_button", row="full"),
                    ctrl(catalog, "view.outline", "small_button", row=0),
                    ctrl(catalog, "view.draft", "small_button", row=1),
                ]),
                make_group("immersive", "Inmersivo", "Immersive", [
                    ctrl(catalog, "view.focus", "large_button", row="full"),
                    ctrl(catalog, "view.immersive", "large_button", row="full"),
                ]),
                make_group("page_movement", "Movimiento de página", "Page Movement", [
                    ctrl(catalog, "view.vertical", "small_button", row=0),
                    ctrl(catalog, "view.sideToSide", "small_button", row=1),
                ]),
                make_group("show", "Mostrar", "Show", [
                    ctrl(catalog, "view.ruler", "toggle", row=0),
                    ctrl(catalog, "view.gridlines", "toggle", row=1),
                    ctrl(catalog, "view.navigationPane", "toggle", row=0),
                ]),
                make_group("zoom", "Zoom", "Zoom", [
                    ctrl(catalog, "view.zoom", "large_button", row="full"),
                    ctrl(catalog, "view.zoom100", "large_button", row="full"),
                    ctrl(catalog, "view.onePage", "small_button", row=0),
                    ctrl(catalog, "view.multiplePages", "small_button", row=1),
                    ctrl(catalog, "view.pageWidth", "small_button", row=0),
                ]),
                make_group("dark_mode", "Modo oscuro", "Dark Mode", [
                    ctrl(catalog, "view.darkMode", "large_button", row="full"),
                    ctrl(catalog, "view.interfaceTheme", "small_button", row=0),
                ]),
                make_group("window", "Ventana", "Window", [
                    ctrl(catalog, "view.newWindow", "large_button", row="full"),
                    ctrl(catalog, "view.arrangeAll", "small_button", row=0),
                    ctrl(catalog, "view.split", "small_button", row=1),
                    ctrl(catalog, "view.sideBySide", "small_button", row=0),
                ]),
                make_group("macros", "Macros", "Macros", [
                    ctrl(catalog, "tools.macros", "large_button", row="full", submenu=["tools.recordMacro"]),
                ]),
            ],
        },
    ]

    # 7 Primary Contextual Tabs (matching Word 16.115 spec + W-009 requirement) + 3 auxiliary modes
    contextual_tabs = [
        {
            "id": "table_design",
            "context_trigger": "table",
            "accent_role": "contextual_teal",
            "labels": {"es-CL": "Diseño de tabla", "en-US": "Table Design"},
            "groups": [
                make_group("table_style_options", "Opciones de estilo de tabla", "Table Style Options", [
                    ctrl(catalog, "table.look", "dropdown", row="full"),
                ]),
                make_group("table_styles", "Estilos de tabla", "Table Styles", [
                    ctrl(catalog, "table.style", "style_gallery", row="full", submenu=["table.newStyle", "table.modifyStyle", "table.deleteStyle"]),
                    ctrl(catalog, "table.shading", "color_picker", row="full"),
                ]),
                make_group("table_borders", "Bordes", "Borders", [
                    ctrl(catalog, "table.borders", "large_button", row="full"),
                    ctrl(catalog, "table.borderPainter", "large_button", row="full"),
                ]),
            ],
        },
        {
            "id": "table_layout",
            "context_trigger": "table",
            "accent_role": "contextual_teal",
            "labels": {"es-CL": "Disposición de tabla", "en-US": "Table Layout"},
            "groups": [
                make_group("table_props", "Tabla", "Table", [
                    ctrl(catalog, "table.selectTable", "dropdown", row="full", submenu=["table.selectRow", "table.selectColumn", "table.selectCell"]),
                    ctrl(catalog, "table.viewGridlines", "toggle", row=0),
                    ctrl(catalog, "table.properties", "large_button", row="full"),
                ]),
                make_group("table_draw", "Dibujar", "Draw", [
                    ctrl(catalog, "table.draw", "large_button", row="full"),
                    ctrl(catalog, "table.eraser", "large_button", row="full"),
                ]),
                make_group("rows_cols", "Filas y columnas", "Rows & Columns", [
                    ctrl(catalog, "table.deleteCells", "large_button", row="full", submenu=["table.deleteColumn", "table.deleteRow", "table.deleteTable"]),
                    ctrl(catalog, "table.insertRowAbove", "small_button", row=0),
                    ctrl(catalog, "table.insertRowBelow", "small_button", row=1),
                    ctrl(catalog, "table.insertColumnLeft", "small_button", row=0),
                    ctrl(catalog, "table.insertColumnRight", "small_button", row=1),
                ]),
                make_group("merge", "Combinar", "Merge", [
                    ctrl(catalog, "table.merge", "large_button", row="full"),
                    ctrl(catalog, "table.split", "small_button", row=0),
                    ctrl(catalog, "table.splitTable", "small_button", row=1),
                ]),
                make_group("cell_size", "Tamaño de celda", "Cell Size", [
                    ctrl(catalog, "table.autofit", "large_button", row="full"),
                    ctrl(catalog, "table.rowHeight", "spin_box", row=0),
                    ctrl(catalog, "table.columnWidth", "spin_box", row=1),
                    ctrl(catalog, "table.distributeRows", "small_button", row=0),
                    ctrl(catalog, "table.distributeColumns", "small_button", row=1),
                ]),
                make_group("alignment", "Alineación", "Alignment", [
                    ctrl(catalog, "table.cellAlign", "dropdown", row="full"),
                    ctrl(catalog, "table.textDirection", "small_button", row=0),
                    ctrl(catalog, "table.cellMargins", "small_button", row=1),
                ]),
                make_group("data", "Datos", "Data", [
                    ctrl(catalog, "table.sort", "large_button", row="full"),
                    ctrl(catalog, "table.repeatHeader", "toggle", row=0),
                    ctrl(catalog, "table.toText", "small_button", row=1),
                    ctrl(catalog, "table.formula", "small_button", row=0),
                ]),
            ],
        },
        {
            "id": "picture_format",
            "context_trigger": "picture",
            "accent_role": "contextual_pink",
            "labels": {"es-CL": "Formato de imagen", "en-US": "Picture Format"},
            "groups": [
                make_group("adjust", "Ajustar", "Adjust", [
                    ctrl(catalog, "picture.removeBackground", "large_button", row="full"),
                    ctrl(catalog, "picture.corrections", "small_button", row=0),
                    ctrl(catalog, "picture.color", "small_button", row=1),
                    ctrl(catalog, "picture.effects", "small_button", row=0),
                    ctrl(catalog, "picture.transparency", "small_button", row=1),
                    ctrl(catalog, "picture.compress", "small_button", row=0),
                    ctrl(catalog, "picture.change", "small_button", row=1),
                    ctrl(catalog, "picture.reset", "small_button", row=0),
                ]),
                make_group("picture_styles", "Estilos de imagen", "Picture Styles", [
                    ctrl(catalog, "picture.style", "style_gallery", row="full"),
                    ctrl(catalog, "picture.border", "color_picker", row=0),
                ]),
                make_group("pic_a11y", "Accesibilidad", "Accessibility", [
                    ctrl(catalog, "picture.altText", "large_button", row="full"),
                ]),
                make_group("pic_size", "Tamaño", "Size", [
                    ctrl(catalog, "picture.crop", "large_button", row="full"),
                    ctrl(catalog, "picture.size", "spin_box", row=0),
                ]),
            ],
        },
        {
            "id": "shape_format",
            "context_trigger": "shape",
            "accent_role": "contextual_pink",
            "labels": {"es-CL": "Formato de forma", "en-US": "Shape Format"},
            "groups": [
                make_group("insert_shapes", "Insertar formas", "Insert Shapes", [
                    ctrl(catalog, "shape.change", "large_button", row="full"),
                    ctrl(catalog, "shape.connect", "small_button", row=0),
                ]),
                make_group("shape_styles", "Estilos de forma", "Shape Styles", [
                    ctrl(catalog, "shape.fill", "color_picker", row=0),
                    ctrl(catalog, "shape.outline", "color_picker", row=1),
                    ctrl(catalog, "shape.effects", "dropdown", row=0),
                ], dialog_cmd="view.formatShapePane"),
                make_group("wordart_styles", "Estilos de WordArt", "WordArt Styles", [
                    ctrl(catalog, "wordArt.style", "style_gallery", row="full"),
                    ctrl(catalog, "wordArt.textFill", "color_picker", row=0),
                    ctrl(catalog, "wordArt.textOutline", "color_picker", row=1),
                    ctrl(catalog, "wordArt.transform", "dropdown", row=0),
                ]),
                make_group("shape_text", "Texto", "Text", [
                    ctrl(catalog, "shape.textDirection", "small_button", row=0),
                    ctrl(catalog, "shape.alignText", "small_button", row=1),
                    ctrl(catalog, "shape.link", "small_button", row=0),
                ]),
                make_group("shape_a11y", "Accesibilidad", "Accessibility", [
                    ctrl(catalog, "object.altText", "large_button", row="full"),
                ]),
            ],
        },
        {
            "id": "equation",
            "context_trigger": "equation",
            "accent_role": "contextual_blue",
            "labels": {"es-CL": "Ecuación", "en-US": "Equation"},
            "groups": [
                make_group("eq_tools", "Herramientas", "Tools", [
                    ctrl(catalog, "equation.gallery", "large_button", row="full"),
                    ctrl(catalog, "equation.normalText", "toggle", row=0),
                    ctrl(catalog, "equation.exit", "small_button", row=1),
                ]),
                make_group("eq_conversions", "Conversiones", "Conversions", [
                    ctrl(catalog, "equation.convert", "dropdown", row="full"),
                    ctrl(catalog, "equation.inputFormat", "dropdown", row=0),
                    ctrl(catalog, "equation.display", "toggle", row=1),
                ]),
                make_group("eq_symbols", "Símbolos", "Symbols", [
                    ctrl(catalog, "equation.insertSymbol", "style_gallery", row="full"),
                ]),
                make_group("eq_structures", "Estructuras", "Structures", [
                    ctrl(catalog, "equation.insertStructure", "style_gallery", row="full"),
                    ctrl(catalog, "equation.justify", "dropdown", row=0),
                    ctrl(catalog, "equation.number", "small_button", row=1),
                ]),
            ],
        },
        {
            "id": "chart_design",
            "context_trigger": "chart",
            "accent_role": "contextual_green",
            "labels": {"es-CL": "Diseño de gráfico", "en-US": "Chart Design"},
            "groups": [
                make_group("chart_layouts", "Diseños de gráfico", "Chart Layouts", [
                    ctrl(catalog, "chart.title", "small_button", row=0),
                    ctrl(catalog, "chart.legend", "small_button", row=1),
                    ctrl(catalog, "chart.dataLabels", "small_button", row=0),
                ]),
                make_group("chart_data", "Datos", "Data", [
                    ctrl(catalog, "chart.editData", "large_button", row="full"),
                ]),
                make_group("chart_type", "Tipo", "Type", [
                    ctrl(catalog, "chart.type", "large_button", row="full"),
                ]),
            ],
        },
        {
            "id": "smartart_design",
            "context_trigger": "smartArt",
            "accent_role": "contextual_pink",
            "labels": {"es-CL": "Diseño de SmartArt", "en-US": "SmartArt Design"},
            "groups": [
                make_group("sa_create", "Crear gráfico", "Create Graphic", [
                    ctrl(catalog, "smartArt.addShape", "large_button", row="full"),
                    ctrl(catalog, "smartArt.textPane", "toggle", row=0),
                    ctrl(catalog, "smartArt.promote", "small_button", row=1),
                    ctrl(catalog, "smartArt.demote", "small_button", row=0),
                ]),
                make_group("sa_layouts", "Diseños", "Layouts", [
                    ctrl(catalog, "smartArt.layout", "style_gallery", row="full"),
                ]),
                make_group("sa_styles", "Estilos SmartArt", "SmartArt Styles", [
                    ctrl(catalog, "smartArt.colors", "large_button", row="full"),
                ]),
                make_group("sa_reset", "Restablecer", "Reset", [
                    ctrl(catalog, "smartArt.reset", "large_button", row="full"),
                ]),
            ],
        },
        {
            "id": "header_footer",
            "context_trigger": "header_footer",
            "accent_role": "contextual_teal",
            "labels": {"es-CL": "Encabezado y pie de página", "en-US": "Header & Footer"},
            "groups": [
                make_group("hf_nav", "Navegación", "Navigation", [
                    ctrl(catalog, "hf.previous", "large_button", row="full"),
                    ctrl(catalog, "hf.next", "large_button", row="full"),
                    ctrl(catalog, "layout.linkToPrevious", "toggle", row=0),
                ]),
                make_group("hf_options", "Opciones", "Options", [
                    ctrl(catalog, "layout.differentFirstPage", "toggle", row=0),
                    ctrl(catalog, "layout.differentOddEven", "toggle", row=1),
                ]),
                make_group("hf_position", "Posición", "Position", [
                    ctrl(catalog, "hf.position", "spin_box", row=0),
                    ctrl(catalog, "insert.closeHeader", "large_button", row="full"),
                ]),
            ],
        },
        {
            "id": "outlining",
            "context_trigger": "outline_view",
            "accent_role": "contextual_blue",
            "labels": {"es-CL": "Esquema", "en-US": "Outlining"},
            "groups": [
                make_group("outline_tools", "Herramientas de esquema", "Outline Tools", [
                    ctrl(catalog, "outline.promoteToHeading1", "small_button", row=0),
                    ctrl(catalog, "outline.promote", "small_button", row=0),
                    ctrl(catalog, "outline.demote", "small_button", row=0),
                    ctrl(catalog, "outline.moveUp", "small_button", row=1),
                    ctrl(catalog, "outline.moveDown", "small_button", row=1),
                    ctrl(catalog, "outline.expand", "small_button", row=1),
                    ctrl(catalog, "outline.collapse", "small_button", row=1),
                    ctrl(catalog, "outline.showLevel", "dropdown", row=0),
                    ctrl(catalog, "outline.firstLineOnly", "toggle", row=1),
                    ctrl(catalog, "outline.showFormatting", "toggle", row=1),
                ]),
                make_group("outline_close", "Cerrar", "Close", [
                    ctrl(catalog, "outline.close", "large_button", row="full"),
                ]),
            ],
        },
        {
            "id": "reading",
            "context_trigger": "read_mode",
            "accent_role": "contextual_blue",
            "labels": {"es-CL": "Lectura", "en-US": "Reading"},
            "groups": [
                make_group("read_layout", "Diseño de lectura", "Reading Layout", [
                    ctrl(catalog, "view.readColumnWidth", "dropdown", row=0),
                    ctrl(catalog, "view.readTextSize", "dropdown", row=0),
                    ctrl(catalog, "view.readLineSpacing", "dropdown", row=1),
                    ctrl(catalog, "view.readPageColor", "color_picker", row=1),
                ]),
            ],
        },
    ]

    title_bar = {
        "qat": [
            ctrl(catalog, "file.autosave", "toggle"),
            shell_ctrl("ui.openBackstage", "Inicio", "Home", "small_button"),
            ctrl(catalog, "file.save", "small_button"),
            ctrl(catalog, "edit.undo", "split_button", submenu=["edit.undoTo", "edit.undoHistory"]),
            ctrl(catalog, "edit.redo", "small_button", submenu=["edit.repeat"]),
            ctrl(catalog, "file.print", "small_button"),
        ],
        "right_actions": [
            ctrl(catalog, "view.commentsPane", "small_button"),
            shell_ctrl("ui.modeSelector", "Edición", "Editing", "dropdown"),
            ctrl(catalog, "file.share", "primary_pill", submenu=["file.exportPdf", "file.exportPng"]),
            ctrl(catalog, "tools.search", "small_button"),
        ],
    }

    backstage = {
        "id": "file",
        "labels": {"es-CL": "Archivo", "en-US": "File"},
        "storage_providers": [
            {"id": "local", "labels": {"es-CL": "En este dispositivo", "en-US": "This Device"}},
            {"id": "icloud", "labels": {"es-CL": "iCloud Drive (Apple)", "en-US": "iCloud Drive"}, "platforms": ["ios", "ipados", "visionos"]},
            {"id": "google_drive", "labels": {"es-CL": "Google Drive (SAF / OAuth)", "en-US": "Google Drive"}},
            {"id": "webdav", "labels": {"es-CL": "Servidor WebDAV", "en-US": "WebDAV Server"}},
        ],
        "sections": [
            ctrl(catalog, "file.new", "backstage_nav", submenu=["file.newFromTemplate", "file.listTemplates"]),
            ctrl(catalog, "file.open", "backstage_nav"),
            ctrl(catalog, "file.info", "backstage_nav", submenu=["file.properties", "file.inspect", "file.protect", "file.encrypt", "file.versions", "file.recover", "file.compatibility"]),
            ctrl(catalog, "file.save", "backstage_nav"),
            ctrl(catalog, "file.saveAs", "backstage_nav", submenu=["file.saveTemplate", "file.rename"]),
            ctrl(catalog, "file.exportPdf", "backstage_nav", submenu=["file.exportPng", "file.strictReport"]),
            ctrl(catalog, "file.print", "backstage_nav", submenu=["file.printPdf", "file.printMarkup"]),
            ctrl(catalog, "file.share", "backstage_nav"),
            ctrl(catalog, "file.options", "backstage_nav", submenu=["file.setAuthor", "review.proofing", "tools.autocorrect", "tools.customizeRibbon", "tools.customizeKeyboard", "proof.packs", "review.language.preferred"]),
            ctrl(catalog, "file.close", "backstage_nav"),
        ],
    }

    status_bar = {
        "left": [
            shell_ctrl("ui.pageIndicator", "Página X de Y", "Page X of Y", "status_text"),
            ctrl(catalog, "review.wordCount", "small_button"),
            ctrl(catalog, "review.spelling", "small_button"),
            ctrl(catalog, "review.language", "small_button"),
            ctrl(catalog, "font.missing", "small_button"),
        ],
        "right": [
            ctrl(catalog, "view.focus", "small_button"),
            ctrl(catalog, "view.readMode", "small_button"),
            ctrl(catalog, "view.printLayout", "small_button"),
            ctrl(catalog, "view.webLayout", "small_button"),
            ctrl(catalog, "view.zoomOut", "small_button"),
            ctrl(catalog, "view.zoom", "status_slider"),
            ctrl(catalog, "view.zoomIn", "small_button"),
        ],
    }

    smartphone_quick_bar = {
        "pinned_actions": [
            ctrl(catalog, "format.bold", "toggle"),
            ctrl(catalog, "format.italic", "toggle"),
            ctrl(catalog, "format.underline", "toggle"),
            ctrl(catalog, "format.color", "color_picker"),
            ctrl(catalog, "para.bullets", "toggle"),
            ctrl(catalog, "para.alignLeft", "dropdown"),
        ],
        "shell_controls": [
            shell_ctrl("ui.openRibbonSheet", "Cinta de opciones", "Ribbon", "small_button"),
            shell_ctrl("ui.toggleReflow", "Vista móvil", "Mobile View", "toggle"),
        ],
    }

    # Gather every mapped command_id in the UI tree above
    mapped_ids: set[str] = set()

    def collect(node: Any) -> None:
        if isinstance(node, dict):
            cid = node.get("command_id")
            if isinstance(cid, str) and not cid.startswith("ui."):
                mapped_ids.add(cid)
            dcid = node.get("dialog_command_id")
            if isinstance(dcid, str) and not dcid.startswith("ui."):
                mapped_ids.add(dcid)
            for v in node.values():
                collect(v)
        elif isinstance(node, list):
            for item in node:
                collect(item)

    collect(tabs)
    collect(contextual_tabs)
    collect(title_bar)
    collect(backstage)
    collect(status_bar)
    collect(smartphone_quick_bar)

    # Remaining internal / navigation / headless / keybinding-only commands
    headless_commands = [
        {"command_id": c["id"], "category": c["category"], "label": c["label"]}
        for c in raw["commands"]
        if c["id"] not in mapped_ids
    ]

    schema = {
        "version": "1.0.0",
        "suite": "OfiOfi",
        "app": "DocxDocx",
        "target_reference": "Microsoft Word 16.115 (es-CL)",
        "total_engine_commands": raw["total"],
        "mapped_ui_commands_count": len(mapped_ids),
        "headless_or_keybinding_commands_count": len(headless_commands),
        "title_bar": title_bar,
        "backstage": backstage,
        "tabs": tabs,
        "contextual_tabs": contextual_tabs,
        "status_bar": status_bar,
        "smartphone_quick_bar": smartphone_quick_bar,
        "headless_or_keybinding_commands": headless_commands,
    }

    OUT_PATH.write_text(json.dumps(schema, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(
        f"Generated {OUT_PATH.relative_to(SUITE_DIR)}: "
        f"{len(tabs)} tabs, {len(contextual_tabs)} contextual tabs, "
        f"{len(mapped_ids)} UI-mapped + {len(headless_commands)} headless = {len(mapped_ids) + len(headless_commands)}/{raw['total']}"
    )


if __name__ == "__main__":
    main()
