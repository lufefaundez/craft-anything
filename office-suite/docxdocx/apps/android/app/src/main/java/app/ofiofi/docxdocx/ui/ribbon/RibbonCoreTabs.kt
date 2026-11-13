package app.ofiofi.docxdocx.ui.ribbon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ofiofi.docxdocx.RibbonControlSpec
import app.ofiofi.docxdocx.ui.shell.RibbonFormatSummary
import app.ofiofi.docxdocx.ui.theme.OfficeTokens

data class CommandPresetOption(
    val id: String,
    val labelEsCl: String,
    val commandId: String,
    val paramsJson: String,
)

data class RibbonStyleTileSpec(
    val engineName: String,
    val labelEsCl: String,
    val previewSp: Float,
    val isBold: Boolean,
)

data class RibbonColorSwatchSpec(
    val nameEsCl: String,
    val hex: String?,
    val argb: Long,
)

/**
 * Shared command presets & JSON payload builders for Inicio, Insertar, Diseño, and Disposición.
 * Pure Kotlin so JVM unit tests verify every engine payload contract without Android stubs.
 */
object RibbonCorePresets {
    val fonts: List<String> = listOf(
        "Aptos",
        "Calibri",
        "Carlito",
        "Times New Roman",
        "Caladea",
        "Arial",
        "Liberation Sans",
        "Courier New",
        "Georgia",
    )

    val sizes: List<String> = listOf(
        "8", "9", "10", "10.5", "11", "12", "14", "16", "18", "20", "22", "24", "26", "28", "36", "48", "72",
    )

    val styles: List<RibbonStyleTileSpec> = listOf(
        RibbonStyleTileSpec("Normal", "Normal", 11f, false),
        RibbonStyleTileSpec("No Spacing", "Sin espaciado", 10.5f, false),
        RibbonStyleTileSpec("Heading 1", "Título 1", 13.5f, true),
        RibbonStyleTileSpec("Heading 2", "Título 2", 12f, true),
        RibbonStyleTileSpec("Title", "Título", 14.5f, false),
        RibbonStyleTileSpec("Subtitle", "Subtítulo", 11f, false),
    )

    val paletteSwatches: List<RibbonColorSwatchSpec> = listOf(
        RibbonColorSwatchSpec("Automático", "auto", 0xFF1F1F1F),
        RibbonColorSwatchSpec("Negro", "000000", 0xFF000000),
        RibbonColorSwatchSpec("Azul Word", "185ABD", 0xFF185ABD),
        RibbonColorSwatchSpec("Azul marino", "0F2C59", 0xFF0F2C59),
        RibbonColorSwatchSpec("Rojo", "C0392B", 0xFFC0392B),
        RibbonColorSwatchSpec("Verde", "107C41", 0xFF107C41),
        RibbonColorSwatchSpec("Naranjo", "D35400", 0xFFD35400),
        RibbonColorSwatchSpec("Gris oscuro", "595959", 0xFF595959),
        RibbonColorSwatchSpec("Amarillo", "FFF000", 0xFFFFF000),
        RibbonColorSwatchSpec("Sin relleno", null, 0xFFFFFFFF),
    )

    val paperSizes: List<CommandPresetOption> = listOf(
        CommandPresetOption("letter", "Carta (21,59 × 27,94 cm)", "layout.size", """{"name":"Letter"}"""),
        CommandPresetOption("oficio_cl", "Oficio Chile (21,59 × 33,00 cm)", "layout.size", """{"width":612,"height":935.43}"""),
        CommandPresetOption("legal", "Legal EE.UU. (21,59 × 35,56 cm)", "layout.size", """{"name":"Legal"}"""),
        CommandPresetOption("a4", "A4 (21,00 × 29,70 cm)", "layout.size", """{"name":"A4"}"""),
    )

    val marginPresets: List<CommandPresetOption> = listOf(
        CommandPresetOption("normal", "Normal (2,54 cm / 3,00 cm)", "layout.margins", """{"preset":"normal"}"""),
        CommandPresetOption("narrow", "Estrecho (1,27 cm)", "layout.margins", """{"preset":"narrow"}"""),
        CommandPresetOption("moderate", "Moderado (2,54 × 1,91 cm)", "layout.margins", """{"preset":"moderate"}"""),
        CommandPresetOption("wide", "Ancho (2,54 × 5,08 cm)", "layout.margins", """{"preset":"wide"}"""),
        CommandPresetOption("mirrored", "Reflejado", "layout.margins", """{"preset":"mirrored"}"""),
    )

    val orientationPresets: List<CommandPresetOption> = listOf(
        CommandPresetOption("portrait", "Vertical", "layout.orientation", """{"value":"portrait"}"""),
        CommandPresetOption("landscape", "Horizontal", "layout.orientation", """{"value":"landscape"}"""),
    )

    val columnPresets: List<CommandPresetOption> = listOf(
        CommandPresetOption("one", "Una columna", "layout.columns", """{"count":1}"""),
        CommandPresetOption("two", "Dos columnas", "layout.columns", """{"count":2}"""),
        CommandPresetOption("three", "Tres columnas", "layout.columns", """{"count":3}"""),
        CommandPresetOption("left", "Izquierda estrecha", "layout.columns", """{"preset":"left"}"""),
        CommandPresetOption("right", "Derecha estrecha", "layout.columns", """{"preset":"right"}"""),
    )

    val breakPresets: List<CommandPresetOption> = listOf(
        CommandPresetOption("page", "Salto de página", "layout.break", """{"kind":"page"}"""),
        CommandPresetOption("column", "Salto de columna", "layout.break", """{"kind":"column"}"""),
        CommandPresetOption("nextPage", "Sección: Página siguiente", "layout.break", """{"kind":"nextPage"}"""),
        CommandPresetOption("continuous", "Sección: Continua", "layout.break", """{"kind":"continuous"}"""),
    )

    val changeCasePresets: List<CommandPresetOption> = listOf(
        CommandPresetOption("sentence", "Tipo oración", "format.changeCase", """{"mode":"sentence"}"""),
        CommandPresetOption("lower", "minúsculas", "format.changeCase", """{"mode":"lower"}"""),
        CommandPresetOption("upper", "MAYÚSCULAS", "format.changeCase", """{"mode":"upper"}"""),
        CommandPresetOption("title", "Poner En Mayúsculas Cada Palabra", "format.changeCase", """{"mode":"title"}"""),
        CommandPresetOption("toggle", "aLTERNAR MAY/MIN", "format.changeCase", """{"mode":"toggle"}"""),
    )

    val pageNumberPresets: List<CommandPresetOption> = listOf(
        CommandPresetOption("top", "Principio de página", "insert.pageNumber", """{"position":"top","align":"right"}"""),
        CommandPresetOption("bottom", "Final de página (Derecha)", "insert.pageNumber", """{"position":"bottom","align":"right"}"""),
        CommandPresetOption("bottom_center", "Final de página (Centrado)", "insert.pageNumber", """{"position":"bottom","align":"center"}"""),
        CommandPresetOption("xofy", "Página X de Y", "insert.pageNumber", """{"position":"bottom","align":"right","format":"x of y"}"""),
    )

    fun presetsForCommand(commandId: String): List<CommandPresetOption> = when (commandId) {
        "layout.size" -> paperSizes
        "layout.margins" -> marginPresets
        "layout.orientation" -> orientationPresets
        "layout.columns" -> columnPresets
        "layout.break" -> breakPresets
        "format.changeCase" -> changeCasePresets
        "insert.pageNumber" -> pageNumberPresets
        else -> emptyList()
    }

    fun supportsInteractiveControl(control: RibbonControlSpec): Boolean =
        control.kind in setOf(
            "font_picker",
            "size_picker",
            "style_gallery",
            "table_grid",
            "color_picker",
            "spin_box",
        ) || presetsForCommand(control.commandId).isNotEmpty()

    fun buildTableInsertPayload(rows: Int, cols: Int): String =
        """{"rows":${rows.coerceIn(1, 8)},"cols":${cols.coerceIn(1, 8)}}"""

    fun buildFontPayload(fontName: String): String =
        """{"name":"${fontName.replace("\"", "\\\"")}"}"""

    fun buildSizePayload(sizeStr: String): String =
        """{"size":$sizeStr}"""

    fun buildColorPayload(commandId: String, hex: String?): String = when (commandId) {
        "format.color" -> """{"color":"${hex ?: "auto"}"}"""
        "para.shading", "format.shading" -> if (hex == null || hex == "auto") """{"color":null}""" else """{"color":"$hex"}"""
        "design.pageColor" -> if (hex == null || hex == "auto") """{"color":null}""" else """{"color":"$hex"}"""
        else -> "{}"
    }
}

@Composable
fun RibbonInteractiveControl(
    control: RibbonControlSpec,
    formatSummary: RibbonFormatSummary,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    checkedBg: Color,
    onExecuteCommand: (commandId: String, paramsJson: String) -> Unit,
    iconContent: @Composable (iconName: String, contentDescription: String, sizeDp: Int) -> Unit,
    fallbackButton: @Composable () -> Unit,
) {
    when {
        control.kind == "font_picker" -> FontPickerChip(
            currentFont = formatSummary.font,
            surfaces = surfaces,
            accent = accent,
            onSelectFont = { font ->
                onExecuteCommand("format.font", RibbonCorePresets.buildFontPayload(font))
            },
        )
        control.kind == "size_picker" -> SizePickerChip(
            currentSize = formatSummary.size,
            surfaces = surfaces,
            accent = accent,
            onSelectSize = { size ->
                onExecuteCommand("format.size", RibbonCorePresets.buildSizePayload(size))
            },
        )
        control.kind == "style_gallery" -> StyleGalleryStrip(
            activeStyle = formatSummary.style,
            surfaces = surfaces,
            accent = accent,
            checkedBg = checkedBg,
            onSelectStyle = { styleName ->
                onExecuteCommand("styles.apply", """{"style":"$styleName"}""")
            },
        )
        control.kind == "table_grid" -> TableGridControl(
            control = control,
            surfaces = surfaces,
            accent = accent,
            iconContent = iconContent,
            onExecuteCommand = onExecuteCommand,
        )
        control.kind == "color_picker" -> ColorSwatchControl(
            control = control,
            surfaces = surfaces,
            accent = accent,
            iconContent = iconContent,
            onExecuteCommand = onExecuteCommand,
        )
        control.kind == "spin_box" -> CompactSpinControl(
            control = control,
            surfaces = surfaces,
            onExecuteCommand = onExecuteCommand,
        )
        RibbonCorePresets.presetsForCommand(control.commandId).isNotEmpty() -> PresetMenuControl(
            control = control,
            presets = RibbonCorePresets.presetsForCommand(control.commandId),
            surfaces = surfaces,
            iconContent = iconContent,
            onExecuteCommand = onExecuteCommand,
        )
        else -> fallbackButton()
    }
}

@Composable
private fun FontPickerChip(
    currentFont: String,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onSelectFont: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .width(126.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                .background(surfaces.canvas)
                .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.button))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = currentFont,
                color = surfaces.text,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("▾", color = surfaces.textDim, fontSize = 10.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RibbonCorePresets.fonts.forEach { fontName ->
                val isCurrent = fontName.equals(currentFont, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = fontName,
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCurrent) accent else surfaces.text,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectFont(fontName)
                    },
                )
            }
        }
    }
}

@Composable
private fun SizePickerChip(
    currentSize: String,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onSelectSize: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .width(52.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                .background(surfaces.canvas)
                .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.button))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = currentSize,
                color = surfaces.text,
                fontSize = 11.sp,
                maxLines = 1,
            )
            Text("▾", color = surfaces.textDim, fontSize = 10.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RibbonCorePresets.sizes.forEach { sz ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = sz,
                            fontSize = 13.sp,
                            fontWeight = if (sz == currentSize) FontWeight.Bold else FontWeight.Normal,
                            color = if (sz == currentSize) accent else surfaces.text,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectSize(sz)
                    },
                )
            }
        }
    }
}

@Composable
private fun StyleGalleryStrip(
    activeStyle: String,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    checkedBg: Color,
    onSelectStyle: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .height(OfficeTokens.TouchTargets.tabletLargeButton)
            .clip(RoundedCornerShape(6.dp))
            .background(surfaces.canvas)
            .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RibbonCorePresets.styles.forEach { item ->
            val selected = activeStyle.equals(item.engineName, ignoreCase = true) ||
                activeStyle.equals(item.labelEsCl, ignoreCase = true)
            Column(
                modifier = Modifier
                    .widthIn(min = 66.dp, max = 82.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) checkedBg else surfaces.ribbonCard)
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) accent else surfaces.ribbonBorder,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .clickable { onSelectStyle(item.engineName) }
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "AaBbCc",
                    fontSize = item.previewSp.sp,
                    fontWeight = if (item.isBold) FontWeight.Bold else FontWeight.Normal,
                    color = if (item.engineName.startsWith("Heading")) accent else surfaces.text,
                    maxLines = 1,
                )
                Text(
                    text = item.labelEsCl,
                    fontSize = 9.sp,
                    color = surfaces.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TableGridControl(
    control: RibbonControlSpec,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    iconContent: @Composable (String, String, Int) -> Unit,
    onExecuteCommand: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var rows by remember { mutableIntStateOf(3) }
    var cols by remember { mutableIntStateOf(3) }

    Box {
        Column(
            modifier = Modifier
                .height(OfficeTokens.TouchTargets.tabletLargeButton)
                .widthIn(min = 56.dp)
                .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            iconContent(control.icon, control.labelEsCl, 22)
            Spacer(Modifier.height(2.dp))
            Text(control.labelEsCl, color = surfaces.text, fontSize = 9.sp, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Insertar tabla de $rows × $cols",
                    color = surfaces.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                for (r in 1..6) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (c in 1..8) {
                            val active = r <= rows && c <= cols
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(if (active) accent.copy(alpha = 0.25f) else surfaces.canvas)
                                    .border(1.dp, if (active) accent else surfaces.ribbonBorder)
                                    .clickable {
                                        rows = r
                                        cols = c
                                    },
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Insertar tabla $rows × $cols",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent)
                            .clickable {
                                expanded = false
                                onExecuteCommand("insert.table", RibbonCorePresets.buildTableInsertPayload(rows, cols))
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            if (control.submenu.isNotEmpty()) {
                HorizontalDivider(color = surfaces.ribbonBorder)
                control.submenu.forEach { sub ->
                    DropdownMenuItem(
                        text = { Text(sub.labelEsCl, fontSize = 12.sp) },
                        onClick = {
                            expanded = false
                            onExecuteCommand(sub.commandId, "{}")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchControl(
    control: RibbonControlSpec,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    iconContent: @Composable (String, String, Int) -> Unit,
    onExecuteCommand: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val barColor = if (control.commandId == "format.highlight") surfaces.highlightYellow else accent

    Box {
        Column(
            modifier = Modifier
                .size(OfficeTokens.TouchTargets.tabletCompactButton)
                .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                .clickable { expanded = true },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            iconContent(control.icon, control.labelEsCl, 16)
            Spacer(Modifier.height(1.dp))
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(3.dp)
                    .background(barColor),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(control.labelEsCl, color = surfaces.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                RibbonCorePresets.paletteSwatches.chunked(5).forEach { rowSwatches ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowSwatches.forEach { swatch ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(swatch.argb))
                                    .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(4.dp))
                                    .clickable {
                                        expanded = false
                                        val payload = RibbonCorePresets.buildColorPayload(control.commandId, swatch.hex)
                                        val cmd = if (control.commandId == "para.shading") "format.shading" else control.commandId
                                        onExecuteCommand(cmd, payload)
                                    },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun CompactSpinControl(
    control: RibbonControlSpec,
    surfaces: OfficeTokens.SurfacePalette,
    onExecuteCommand: (String, String) -> Unit,
) {
    var currentPt by remember(control.commandId) { mutableIntStateOf(0) }
    val label = if (control.commandId == "para.indents") "Sangría" else "Espacio"

    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(OfficeTokens.Radii.button))
            .background(surfaces.canvas)
            .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.button))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "−",
            color = surfaces.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable {
                    currentPt = (currentPt - 6).coerceAtLeast(0)
                    val payload = if (control.commandId == "para.indents") {
                        """{"left":$currentPt}"""
                    } else {
                        """{"after":$currentPt}"""
                    }
                    onExecuteCommand(control.commandId, payload)
                }
                .padding(horizontal = 3.dp),
        )
        Text("$label ${currentPt}pt", color = surfaces.textDim, fontSize = 10.sp)
        Text(
            text = "+",
            color = surfaces.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable {
                    currentPt = (currentPt + 6).coerceAtMost(144)
                    val payload = if (control.commandId == "para.indents") {
                        """{"left":$currentPt}"""
                    } else {
                        """{"after":$currentPt}"""
                    }
                    onExecuteCommand(control.commandId, payload)
                }
                .padding(horizontal = 3.dp),
        )
    }
}

@Composable
private fun PresetMenuControl(
    control: RibbonControlSpec,
    presets: List<CommandPresetOption>,
    surfaces: OfficeTokens.SurfacePalette,
    iconContent: @Composable (String, String, Int) -> Unit,
    onExecuteCommand: (String, String) -> Unit,
) {
    var expanded by remember(control.commandId) { mutableStateOf(false) }
    val isTall = control.row == "full" || control.kind == "large_button"

    Box {
        if (isTall) {
            Column(
                modifier = Modifier
                    .height(OfficeTokens.TouchTargets.tabletLargeButton)
                    .widthIn(min = 56.dp)
                    .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                    .clickable { expanded = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                iconContent(control.icon, control.labelEsCl, 22)
                Spacer(Modifier.height(2.dp))
                Text("${control.labelEsCl} ▾", color = surfaces.text, fontSize = 9.sp, maxLines = 1)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(OfficeTokens.TouchTargets.tabletCompactButton)
                    .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center,
            ) {
                iconContent(control.icon, control.labelEsCl, 18)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.labelEsCl, fontSize = 13.sp) },
                    onClick = {
                        expanded = false
                        onExecuteCommand(preset.commandId, preset.paramsJson)
                    },
                )
            }
        }
    }
}
