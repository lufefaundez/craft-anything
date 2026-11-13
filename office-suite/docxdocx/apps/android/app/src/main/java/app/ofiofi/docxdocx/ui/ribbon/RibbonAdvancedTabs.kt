package app.ofiofi.docxdocx.ui.ribbon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import app.ofiofi.docxdocx.ui.theme.OfficeTokens

enum class AdvancedToolPanel {
    FIND_REPLACE,
    NEW_COMMENT,
    NEW_FOOTNOTE,
}

data class ZoomPresetOption(
    val id: String,
    val labelEsCl: String,
    val zoomFactor: Float?,
    val commandId: String? = null,
)

/**
 * Pure-Kotlin helpers & payload builders for Referencias, Correspondencia, Revisar, Vista,
 * Contextual tabs, Find & Replace, Comment Composer, and Footnotes.
 */
object RibbonAdvancedPresets {
    val citationStyles: List<String> = listOf("APA (7.ª ed.)", "IEEE", "Chicago", "MLA", "ISO 690")

    val zoomOptions: List<ZoomPresetOption> = listOf(
        ZoomPresetOption("50", "50 %", 0.5f),
        ZoomPresetOption("75", "75 %", 0.75f),
        ZoomPresetOption("100", "100 %", 1.0f),
        ZoomPresetOption("125", "125 %", 1.25f),
        ZoomPresetOption("150", "150 %", 1.5f),
        ZoomPresetOption("200", "200 %", 2.0f),
        ZoomPresetOption("pageWidth", "Ancho de página", null, commandId = "view.pageWidth"),
    )

    fun panelForCommand(commandId: String): AdvancedToolPanel? = when (commandId) {
        "edit.find", "edit.findReplace" -> AdvancedToolPanel.FIND_REPLACE
        "review.newComment" -> AdvancedToolPanel.NEW_COMMENT
        "references.footnote" -> AdvancedToolPanel.NEW_FOOTNOTE
        else -> null
    }

    fun contextualBannerLabel(activeContexts: Set<String>): String? = when {
        activeContexts.contains("in_table") -> "Herramientas de tabla: Diseño y Disposición"
        activeContexts.contains("in_header_footer") -> "Encabezado y pie de página"
        activeContexts.contains("image_selected") -> "Formato de imagen"
        activeContexts.contains("shape_selected") -> "Formato de forma"
        activeContexts.contains("in_equation") -> "Ecuación"
        else -> null
    }

    fun escapeJsonString(raw: String): String =
        raw.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    fun buildFindPayload(query: String, matchCase: Boolean, wholeWord: Boolean): String =
        """{"text":"${escapeJsonString(query)}","matchCase":$matchCase,"wholeWord":$wholeWord}"""

    fun buildReplacePayload(
        query: String,
        replacement: String,
        matchCase: Boolean,
        wholeWord: Boolean,
    ): String =
        """{"text":"${escapeJsonString(query)}","with":"${escapeJsonString(replacement)}","matchCase":$matchCase,"wholeWord":$wholeWord}"""

    fun buildCommentPayload(text: String): String =
        """{"text":"${escapeJsonString(text.trim())}"}"""

    fun buildFootnotePayload(text: String): String =
        """{"text":"${escapeJsonString(text.trim())}"}"""
}

@Composable
fun FindReplaceOverlayBar(
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onExecuteCommand: (commandId: String, paramsJson: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.ribbonCard)),
        color = surfaces.ribbonCard,
        shape = RoundedCornerShape(OfficeTokens.Radii.ribbonCard),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineInputField(
                    value = query,
                    placeholder = "Buscar en el documento...",
                    onValueChange = {
                        query = it
                        if (it.isNotEmpty()) {
                            onExecuteCommand("edit.find", RibbonAdvancedPresets.buildFindPayload(it, matchCase, wholeWord))
                        }
                    },
                    surfaces = surfaces,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
                InlineActionChip("Anterior", surfaces, enabled = query.isNotEmpty()) {
                    onExecuteCommand("edit.findPrevious", RibbonAdvancedPresets.buildFindPayload(query, matchCase, wholeWord))
                }
                InlineActionChip("Siguiente", surfaces, accent = accent, filled = true, enabled = query.isNotEmpty()) {
                    onExecuteCommand("edit.findNext", RibbonAdvancedPresets.buildFindPayload(query, matchCase, wholeWord))
                }
                InlineActionChip("Cerrar", surfaces) { onDismiss() }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineInputField(
                    value = replacement,
                    placeholder = "Reemplazar con...",
                    onValueChange = { replacement = it },
                    surfaces = surfaces,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
                InlineTogglePill("Aa", matchCase, surfaces, accent) { matchCase = !matchCase }
                InlineTogglePill("Palabra", wholeWord, surfaces, accent) { wholeWord = !wholeWord }
                InlineActionChip("Reemplazar", surfaces, enabled = query.isNotEmpty()) {
                    onExecuteCommand(
                        "edit.replace",
                        RibbonAdvancedPresets.buildReplacePayload(query, replacement, matchCase, wholeWord),
                    )
                }
                InlineActionChip("Todo", surfaces, enabled = query.isNotEmpty()) {
                    onExecuteCommand(
                        "edit.replaceAll",
                        RibbonAdvancedPresets.buildReplacePayload(query, replacement, matchCase, wholeWord),
                    )
                }
            }
        }
    }
}

@Composable
fun QuickNoteComposerBar(
    title: String,
    placeholder: String,
    confirmLabel: String,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.ribbonCard)),
        color = surfaces.ribbonCard,
        shape = RoundedCornerShape(OfficeTokens.Radii.ribbonCard),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            InlineInputField(
                value = text,
                placeholder = placeholder,
                onValueChange = { text = it },
                surfaces = surfaces,
                accent = accent,
                modifier = Modifier.weight(1f),
            )
            InlineActionChip(confirmLabel, surfaces, accent = accent, filled = true, enabled = text.isNotBlank()) {
                onSubmit(text)
                onDismiss()
            }
            InlineActionChip("Cancelar", surfaces) { onDismiss() }
        }
    }
}

@Composable
private fun InlineInputField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(OfficeTokens.Radii.button))
            .background(surfaces.canvas)
            .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.button))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = surfaces.textDim, fontSize = 12.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = surfaces.text, fontSize = 13.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InlineActionChip(
    label: String,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color = surfaces.text,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = when {
            !enabled -> surfaces.textDim
            filled -> Color.White
            else -> surfaces.text
        },
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(OfficeTokens.Radii.button))
            .background(if (filled && enabled) accent else surfaces.canvas)
            .border(1.dp, if (filled && enabled) accent else surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.button))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun InlineTogglePill(
    label: String,
    checked: Boolean,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onToggle: () -> Unit,
) {
    Text(
        text = label,
        color = if (checked) accent else surfaces.textDim,
        fontSize = 11.sp,
        fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .widthIn(min = 32.dp)
            .clip(RoundedCornerShape(OfficeTokens.Radii.button))
            .background(if (checked) accent.copy(alpha = 0.14f) else surfaces.canvas)
            .border(1.dp, if (checked) accent else surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.button))
            .clickable { onToggle() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
