package app.ofiofi.docxdocx.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.ofiofi.docxdocx.ui.canvas.RulerMath
import app.ofiofi.docxdocx.ui.ribbon.RibbonCorePresets
import app.ofiofi.docxdocx.ui.shell.RibbonFormatSummary
import app.ofiofi.docxdocx.ui.theme.OfficeTokens
import java.util.Locale

enum class OfficeModalDialogKind {
    FONT,
    PARAGRAPH,
    TABS,
    PAGE_SETUP,
    TABLE_PROPERTIES,
}

data class EngineCommandCall(
    val commandId: String,
    val paramsJson: String,
)

/**
 * Pure-Kotlin modal dialog router and engine command batch builder for Font, Paragraph,
 * Tab Stops, Page Setup (cm -> pt), and Table Properties.
 */
object OfficeDialogRouter {
    fun dialogForCommand(commandId: String): OfficeModalDialogKind? = when (commandId) {
        "format.fontDialog" -> OfficeModalDialogKind.FONT
        "para.dialog" -> OfficeModalDialogKind.PARAGRAPH
        "para.tabs" -> OfficeModalDialogKind.TABS
        "layout.pageSetup" -> OfficeModalDialogKind.PAGE_SETUP
        "table.properties" -> OfficeModalDialogKind.TABLE_PROPERTIES
        else -> null
    }

    fun cmToPt(cmText: String, fallbackPt: Float): Float {
        val cm = cmText.trim().replace(',', '.').toFloatOrNull() ?: return fallbackPt
        return (cm * RulerMath.POINTS_PER_CM).coerceIn(0f, 1584f)
    }

    fun buildParagraphCommands(
        alignCmd: String,
        indentLeftCm: String,
        indentRightCm: String,
        firstLineCm: String,
        spaceBeforePt: String,
        spaceAfterPt: String,
        lineSpacingCmd: String,
    ): List<EngineCommandCall> = buildList {
        add(EngineCommandCall(alignCmd, "{}"))
        val lPt = cmToPt(indentLeftCm, 0f)
        val rPt = cmToPt(indentRightCm, 0f)
        val fPt = cmToPt(firstLineCm, 0f)
        add(
            EngineCommandCall(
                "para.indents",
                String.format(Locale.US, """{"left":%.1f,"right":%.1f,"firstLine":%.1f}""", lPt, rPt, fPt),
            ),
        )
        val bPt = (spaceBeforePt.trim().replace(',', '.').toFloatOrNull() ?: 0f).coerceIn(0f, 1584f)
        val aPt = (spaceAfterPt.trim().replace(',', '.').toFloatOrNull() ?: 8f).coerceIn(0f, 1584f)
        add(
            EngineCommandCall(
                "para.spacing",
                String.format(Locale.US, """{"before":%.1f,"after":%.1f}""", bPt, aPt),
            ),
        )
        add(EngineCommandCall(lineSpacingCmd, "{}"))
    }

    fun buildPageSetupCommands(
        topCm: String,
        bottomCm: String,
        leftCm: String,
        rightCm: String,
        orientation: String,
        paperPresetId: String,
    ): List<EngineCommandCall> = buildList {
        val tPt = cmToPt(topCm, 70.87f)
        val bPt = cmToPt(bottomCm, 70.87f)
        val lPt = cmToPt(leftCm, 85.04f)
        val rPt = cmToPt(rightCm, 85.04f)
        add(
            EngineCommandCall(
                "layout.margins",
                String.format(Locale.US, """{"top":%.2f,"bottom":%.2f,"left":%.2f,"right":%.2f}""", tPt, bPt, lPt, rPt),
            ),
        )
        add(EngineCommandCall("layout.orientation", """{"value":"$orientation"}"""))
        val paper = RibbonCorePresets.paperSizes.firstOrNull { it.id == paperPresetId }
        if (paper != null) {
            add(EngineCommandCall(paper.commandId, paper.paramsJson))
        }
    }

    fun buildTabStopCommand(positionCm: String, align: String, leader: String): EngineCommandCall {
        val posPt = cmToPt(positionCm, 36f)
        return EngineCommandCall(
            "para.setTab",
            String.format(Locale.US, """{"pos":%.2f,"align":"$align","leader":"$leader"}""", posPt),
        )
    }
}

@Composable
fun OfficeModalDialogHost(
    dialogKind: OfficeModalDialogKind,
    formatSummary: RibbonFormatSummary,
    activeToggleIds: Set<String>,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onSwitchDialog: (OfficeModalDialogKind) -> Unit,
    onExecuteCommands: (List<EngineCommandCall>) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            color = surfaces.ribbonCard,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (dialogKind) {
                    OfficeModalDialogKind.FONT -> FontModalBody(
                        formatSummary = formatSummary,
                        activeToggleIds = activeToggleIds,
                        surfaces = surfaces,
                        accent = accent,
                        onApply = {
                            onExecuteCommands(it)
                            onDismiss()
                        },
                        onCancel = onDismiss,
                    )
                    OfficeModalDialogKind.PARAGRAPH -> ParagraphModalBody(
                        surfaces = surfaces,
                        accent = accent,
                        onOpenTabs = { onSwitchDialog(OfficeModalDialogKind.TABS) },
                        onApply = {
                            onExecuteCommands(it)
                            onDismiss()
                        },
                        onCancel = onDismiss,
                    )
                    OfficeModalDialogKind.TABS -> TabStopsModalBody(
                        surfaces = surfaces,
                        accent = accent,
                        onBackToParagraph = { onSwitchDialog(OfficeModalDialogKind.PARAGRAPH) },
                        onApply = {
                            onExecuteCommands(it)
                            onDismiss()
                        },
                        onCancel = onDismiss,
                    )
                    OfficeModalDialogKind.PAGE_SETUP -> PageSetupModalBody(
                        surfaces = surfaces,
                        accent = accent,
                        onApply = {
                            onExecuteCommands(it)
                            onDismiss()
                        },
                        onCancel = onDismiss,
                    )
                    OfficeModalDialogKind.TABLE_PROPERTIES -> TablePropertiesModalBody(
                        surfaces = surfaces,
                        accent = accent,
                        onApply = {
                            onExecuteCommands(it)
                            onDismiss()
                        },
                        onCancel = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun FontModalBody(
    formatSummary: RibbonFormatSummary,
    activeToggleIds: Set<String>,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onApply: (List<EngineCommandCall>) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedFont by remember { mutableStateOf(formatSummary.font) }
    var selectedSize by remember { mutableStateOf(formatSummary.size) }
    var bold by remember { mutableStateOf(activeToggleIds.contains("format.bold")) }
    var italic by remember { mutableStateOf(activeToggleIds.contains("format.italic")) }
    var underline by remember { mutableStateOf(activeToggleIds.contains("format.underline")) }
    var strike by remember { mutableStateOf(activeToggleIds.contains("format.strike")) }
    var sup by remember { mutableStateOf(activeToggleIds.contains("format.superscript")) }
    var sub by remember { mutableStateOf(activeToggleIds.contains("format.subscript")) }

    Text("Fuente", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Text("Familia tipográfica", color = surfaces.textDim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RibbonCorePresets.fonts.take(4).forEach { f ->
            ChoiceChip(f, selectedFont == f, surfaces, accent) { selectedFont = f }
        }
    }
    Text("Tamaño (pt)", color = surfaces.textDim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("10", "11", "12", "14", "18", "24").forEach { sz ->
            ChoiceChip(sz, selectedSize == sz, surfaces, accent) { selectedSize = sz }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = bold, onCheckedChange = { bold = it })
        Text("Negrita", color = surfaces.text, fontSize = 12.sp)
        Checkbox(checked = italic, onCheckedChange = { italic = it })
        Text("Cursiva", color = surfaces.text, fontSize = 12.sp)
        Checkbox(checked = underline, onCheckedChange = { underline = it })
        Text("Subrayado", color = surfaces.text, fontSize = 12.sp)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = strike, onCheckedChange = { strike = it })
        Text("Tachado", color = surfaces.text, fontSize = 12.sp)
        Checkbox(checked = sup, onCheckedChange = { sup = it; if (it) sub = false })
        Text("Superíndice", color = surfaces.text, fontSize = 12.sp)
        Checkbox(checked = sub, onCheckedChange = { sub = it; if (it) sup = false })
        Text("Subíndice", color = surfaces.text, fontSize = 12.sp)
    }
    DialogActionRow(accent = accent, surfaces = surfaces, onCancel = onCancel) {
        val cmds = buildList {
            add(EngineCommandCall("format.font", RibbonCorePresets.buildFontPayload(selectedFont)))
            add(EngineCommandCall("format.size", RibbonCorePresets.buildSizePayload(selectedSize)))
            if (bold != activeToggleIds.contains("format.bold")) add(EngineCommandCall("format.bold", "{}"))
            if (italic != activeToggleIds.contains("format.italic")) add(EngineCommandCall("format.italic", "{}"))
            if (underline != activeToggleIds.contains("format.underline")) add(EngineCommandCall("format.underline", "{}"))
            if (strike != activeToggleIds.contains("format.strike")) add(EngineCommandCall("format.strike", "{}"))
            if (sup != activeToggleIds.contains("format.superscript")) add(EngineCommandCall("format.superscript", "{}"))
            if (sub != activeToggleIds.contains("format.subscript")) add(EngineCommandCall("format.subscript", "{}"))
        }
        onApply(cmds)
    }
}

@Composable
private fun ParagraphModalBody(
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onOpenTabs: () -> Unit,
    onApply: (List<EngineCommandCall>) -> Unit,
    onCancel: () -> Unit,
) {
    var alignCmd by remember { mutableStateOf("para.alignLeft") }
    var indentLeftCm by remember { mutableStateOf("0.0") }
    var indentRightCm by remember { mutableStateOf("0.0") }
    var firstLineCm by remember { mutableStateOf("0.0") }
    var spaceBeforePt by remember { mutableStateOf("0") }
    var spaceAfterPt by remember { mutableStateOf("8") }
    var lineSpacingCmd by remember { mutableStateOf("para.single") }

    Text("Párrafo", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Text("Alineación", color = surfaces.textDim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "para.alignLeft" to "Izquierda",
            "para.alignCenter" to "Centrada",
            "para.alignRight" to "Derecha",
            "para.justify" to "Justificada",
        ).forEach { (cmd, lbl) ->
            ChoiceChip(lbl, alignCmd == cmd, surfaces, accent) { alignCmd = cmd }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledInput("Sangría izq. (cm)", indentLeftCm, surfaces, accent, Modifier.weight(1f)) { indentLeftCm = it }
        LabeledInput("Sangría der. (cm)", indentRightCm, surfaces, accent, Modifier.weight(1f)) { indentRightCm = it }
        LabeledInput("1.ª línea (cm)", firstLineCm, surfaces, accent, Modifier.weight(1f)) { firstLineCm = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledInput("Esp. anterior (pt)", spaceBeforePt, surfaces, accent, Modifier.weight(1f)) { spaceBeforePt = it }
        LabeledInput("Esp. posterior (pt)", spaceAfterPt, surfaces, accent, Modifier.weight(1f)) { spaceAfterPt = it }
    }
    Text("Interlineado", color = surfaces.textDim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "para.single" to "Sencillo",
            "para.oneAndHalf" to "1,5 líneas",
            "para.double" to "Doble",
        ).forEach { (cmd, lbl) ->
            ChoiceChip(lbl, lineSpacingCmd == cmd, surfaces, accent) { lineSpacingCmd = cmd }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChoiceChip("Tabulaciones...", false, surfaces, accent, onClick = onOpenTabs)
        DialogActionRow(accent = accent, surfaces = surfaces, onCancel = onCancel) {
            onApply(
                OfficeDialogRouter.buildParagraphCommands(
                    alignCmd = alignCmd,
                    indentLeftCm = indentLeftCm,
                    indentRightCm = indentRightCm,
                    firstLineCm = firstLineCm,
                    spaceBeforePt = spaceBeforePt,
                    spaceAfterPt = spaceAfterPt,
                    lineSpacingCmd = lineSpacingCmd,
                ),
            )
        }
    }
}

@Composable
private fun TabStopsModalBody(
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onBackToParagraph: () -> Unit,
    onApply: (List<EngineCommandCall>) -> Unit,
    onCancel: () -> Unit,
) {
    var positionCm by remember { mutableStateOf("1.25") }
    var align by remember { mutableStateOf("left") }
    var leader by remember { mutableStateOf("none") }

    Text("Tabulaciones", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    LabeledInput("Posición (cm)", positionCm, surfaces, accent, Modifier.fillMaxWidth()) { positionCm = it }
    Text("Alineación", color = surfaces.textDim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("left" to "Izquierda", "center" to "Centro", "right" to "Derecha", "decimal" to "Decimal").forEach { (id, lbl) ->
            ChoiceChip(lbl, align == id, surfaces, accent) { align = id }
        }
    }
    Text("Relleno", color = surfaces.textDim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("none" to "Ninguno", "dot" to "2 ......", "hyphen" to "3 -----", "underscore" to "4 _____").forEach { (id, lbl) ->
            ChoiceChip(lbl, leader == id, surfaces, accent) { leader = id }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceChip("Borrar todas", false, surfaces, accent) {
                onApply(listOf(EngineCommandCall("para.clearTabs", "{}")))
            }
            ChoiceChip("Párrafo", false, surfaces, accent, onClick = onBackToParagraph)
        }
        DialogActionRow(accent = accent, surfaces = surfaces, onCancel = onCancel) {
            onApply(listOf(OfficeDialogRouter.buildTabStopCommand(positionCm, align, leader)))
        }
    }
}

@Composable
private fun PageSetupModalBody(
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onApply: (List<EngineCommandCall>) -> Unit,
    onCancel: () -> Unit,
) {
    var topCm by remember { mutableStateOf("2.5") }
    var bottomCm by remember { mutableStateOf("2.5") }
    var leftCm by remember { mutableStateOf("3.0") }
    var rightCm by remember { mutableStateOf("3.0") }
    var orientation by remember { mutableStateOf("portrait") }
    var paperPresetId by remember { mutableStateOf("letter") }

    Text("Configurar página", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledInput("Superior (cm)", topCm, surfaces, accent, Modifier.weight(1f)) { topCm = it }
        LabeledInput("Inferior (cm)", bottomCm, surfaces, accent, Modifier.weight(1f)) { bottomCm = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledInput("Izquierdo (cm)", leftCm, surfaces, accent, Modifier.weight(1f)) { leftCm = it }
        LabeledInput("Derecho (cm)", rightCm, surfaces, accent, Modifier.weight(1f)) { rightCm = it }
    }
    Text("Orientación", color = surfaces.textDim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceChip("Vertical", orientation == "portrait", surfaces, accent) { orientation = "portrait" }
        ChoiceChip("Horizontal", orientation == "landscape", surfaces, accent) { orientation = "landscape" }
    }
    Text("Tamaño del papel", color = surfaces.textDim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RibbonCorePresets.paperSizes.forEach { preset ->
            val shortLabel = when (preset.id) {
                "oficio_cl" -> "Oficio Chile"
                "letter" -> "Carta"
                "legal" -> "Legal"
                else -> "A4"
            }
            ChoiceChip(shortLabel, paperPresetId == preset.id, surfaces, accent) { paperPresetId = preset.id }
        }
    }
    DialogActionRow(accent = accent, surfaces = surfaces, onCancel = onCancel) {
        onApply(
            OfficeDialogRouter.buildPageSetupCommands(
                topCm = topCm,
                bottomCm = bottomCm,
                leftCm = leftCm,
                rightCm = rightCm,
                orientation = orientation,
                paperPresetId = paperPresetId,
            ),
        )
    }
}

@Composable
private fun TablePropertiesModalBody(
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onApply: (List<EngineCommandCall>) -> Unit,
    onCancel: () -> Unit,
) {
    var align by remember { mutableStateOf("left") }
    Text("Propiedades de tabla", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Text("Alineación de la tabla", color = surfaces.textDim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceChip("Izquierda", align == "left", surfaces, accent) { align = "left" }
        ChoiceChip("Centro", align == "center", surfaces, accent) { align = "center" }
        ChoiceChip("Derecha", align == "right", surfaces, accent) { align = "right" }
    }
    DialogActionRow(accent = accent, surfaces = surfaces, onCancel = onCancel) {
        onApply(listOf(EngineCommandCall("table.align", """{"value":"$align"}""")))
    }
}

@Composable
private fun LabeledInput(
    label: String,
    value: String,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = surfaces.textDim, fontSize = 10.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = surfaces.text, fontSize = 13.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(surfaces.canvas)
                .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) Color.White else surfaces.text,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) accent else surfaces.canvas)
            .border(1.dp, if (selected) accent else surfaces.ribbonBorder, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun DialogActionRow(
    accent: Color,
    surfaces: OfficeTokens.SurfacePalette,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = "Cancelar",
            color = surfaces.text,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(5.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
        Text(
            text = "Aceptar",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(accent)
                .clickable(onClick = onConfirm)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
