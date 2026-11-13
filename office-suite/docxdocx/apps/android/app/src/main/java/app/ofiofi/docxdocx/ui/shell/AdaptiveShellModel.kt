package app.ofiofi.docxdocx.ui.shell

import app.ofiofi.docxdocx.RibbonControlSpec
import app.ofiofi.docxdocx.RibbonGroupSpec
import app.ofiofi.docxdocx.RibbonSchemaCatalog
import app.ofiofi.docxdocx.RibbonTabSpec

enum class ShellFormFactor {
    TABLET_EXPANDED_RIBBON,
    PHONE_COMPACT_SHEET,
}

sealed interface RibbonGroupSlot {
    data class Tall(val control: RibbonControlSpec) : RibbonGroupSlot
    data class StackedPair(
        val top: RibbonControlSpec,
        val bottom: RibbonControlSpec?,
    ) : RibbonGroupSlot
}

data class RibbonSearchMatch(
    val tabId: String,
    val tabLabelEsCl: String,
    val groupLabelEsCl: String,
    val control: RibbonControlSpec,
)

data class RibbonFormatSummary(
    val font: String = "Aptos",
    val size: String = "12",
    val style: String = "Normal",
    val colorHex: String = "auto",
) {
    companion object {
        val Default = RibbonFormatSummary()
    }
}

object AdaptiveShellModel {
    fun resolveFormFactor(isExpandedWidth: Boolean): ShellFormFactor =
        if (isExpandedWidth) ShellFormFactor.TABLET_EXPANDED_RIBBON else ShellFormFactor.PHONE_COMPACT_SHEET

    /**
     * Arranges controls within a group into Office-faithful vertical columns:
     * - `row == "full"` or `kind == "large_button"` / `"style_gallery"` -> Tall column
     * - `row == "0"` and `"1"` -> Paired top/bottom compact stack
     */
    fun layoutGroupColumns(group: RibbonGroupSpec): List<RibbonGroupSlot> {
        val slots = mutableListOf<RibbonGroupSlot>()
        var pendingTop: RibbonControlSpec? = null

        for (control in group.controls) {
            val isTall = control.row == "full" ||
                control.kind == "large_button" ||
                control.kind == "style_gallery" ||
                control.kind == "table_grid"
            if (isTall) {
                pendingTop?.let {
                    slots.add(RibbonGroupSlot.StackedPair(it, null))
                    pendingTop = null
                }
                slots.add(RibbonGroupSlot.Tall(control))
            } else if (pendingTop == null) {
                pendingTop = control
            } else {
                slots.add(RibbonGroupSlot.StackedPair(pendingTop, control))
                pendingTop = null
            }
        }
        pendingTop?.let { slots.add(RibbonGroupSlot.StackedPair(it, null)) }
        return slots
    }

    /**
     * Filters tabs including only currently active contextual tabs alongside the 9 core tabs.
     */
    fun visibleTabs(
        catalog: RibbonSchemaCatalog,
        activeContexts: Set<String>,
    ): List<RibbonTabSpec> {
        val contextual = catalog.contextualTabs.filter { tab ->
            tab.contextualTrigger != null && activeContexts.contains(tab.contextualTrigger)
        }
        return catalog.coreTabs + contextual
    }

    /**
     * Searches across all tabs, groups, and submenus by Spanish (`es-CL`), English (`en-US`),
     * or `commandId` substring (accent- and case-insensitive for fast mobile discovery).
     */
    fun searchCommands(
        catalog: RibbonSchemaCatalog,
        query: String,
        maxResults: Int = 30,
    ): List<RibbonSearchMatch> {
        val normalizedQuery = foldSpanish(query.trim())
        if (normalizedQuery.isEmpty()) return emptyList()

        val matches = mutableListOf<RibbonSearchMatch>()
        val allTabs = catalog.coreTabs + catalog.contextualTabs
        for (tab in allTabs) {
            for (group in tab.groups) {
                for (control in group.controls) {
                    collectMatches(tab, group, control, normalizedQuery, matches, maxResults)
                    if (matches.size >= maxResults) return matches
                }
            }
        }
        return matches
    }

    private fun collectMatches(
        tab: RibbonTabSpec,
        group: RibbonGroupSpec,
        control: RibbonControlSpec,
        normalizedQuery: String,
        out: MutableList<RibbonSearchMatch>,
        maxResults: Int,
    ) {
        if (out.size >= maxResults) return
        val haystacks = listOf(
            foldSpanish(control.labelEsCl),
            foldSpanish(control.labelEnUs),
            foldSpanish(control.commandId),
            foldSpanish(group.labelEsCl),
        )
        if (haystacks.any { it.contains(normalizedQuery) }) {
            out.add(
                RibbonSearchMatch(
                    tabId = tab.id,
                    tabLabelEsCl = tab.labelEsCl,
                    groupLabelEsCl = group.labelEsCl,
                    control = control,
                ),
            )
        }
        for (sub in control.submenu) {
            collectMatches(tab, group, sub, normalizedQuery, out, maxResults)
            if (out.size >= maxResults) return
        }
    }

    /**
     * Extracts active formatting/context keys from `format.state` JSON returned by `wordcraft-engine`.
     * Pure Kotlin scanning avoids Android framework `org.json` stub differences in JVM tests.
     */
    fun parseActiveToggleIds(formatStateJson: String): Set<String> =
        buildSet {
            if (jsonBool(formatStateJson, "bold")) add("format.bold")
            if (jsonBool(formatStateJson, "italic")) add("format.italic")
            if (jsonBool(formatStateJson, "underline")) add("format.underline")
            if (jsonBool(formatStateJson, "strike") || jsonBool(formatStateJson, "strikethrough")) {
                add("format.strike")
            }
            if (jsonBool(formatStateJson, "superscript")) add("format.superscript")
            if (jsonBool(formatStateJson, "subscript")) add("format.subscript")
            if (jsonString(formatStateJson, "highlight")?.let { it.isNotEmpty() && it != "none" } == true) {
                add("format.highlight")
            }
            when (jsonString(formatStateJson, "align")) {
                "left" -> add("para.alignLeft")
                "center" -> add("para.alignCenter")
                "right" -> add("para.alignRight")
                "justify" -> add("para.justify")
            }
            if (jsonBool(formatStateJson, "bullets")) add("para.bullets")
            if (jsonBool(formatStateJson, "numbering")) add("para.numbering")
            if (jsonBool(formatStateJson, "marks")) add("view.marks")
            if (jsonBool(formatStateJson, "trackChanges") || jsonBool(formatStateJson, "track_changes")) {
                add("review.trackChanges")
            }
        }

    fun parseFormatSummary(formatStateJson: String): RibbonFormatSummary {
        val font = jsonString(formatStateJson, "font")?.takeIf { it.isNotBlank() } ?: "Aptos"
        val rawSize = jsonNumber(formatStateJson, "size")
        val size = rawSize?.let { num ->
            val asInt = num.toInt()
            if (kotlin.math.abs(num - asInt) < 0.01) asInt.toString() else num.toString()
        } ?: "12"
        val style = jsonString(formatStateJson, "styleName")?.takeIf { it.isNotBlank() }
            ?: jsonString(formatStateJson, "style")?.takeIf { it.isNotBlank() }
            ?: "Normal"
        val colorHex = jsonString(formatStateJson, "color")?.takeIf { it.isNotBlank() } ?: "auto"
        return RibbonFormatSummary(font = font, size = size, style = style, colorHex = colorHex)
    }

    fun parseActiveContexts(formatStateJson: String): Set<String> =
        buildSet {
            if (jsonBool(formatStateJson, "inTable") || jsonBool(formatStateJson, "in_table")) {
                add("in_table")
            }
            if (jsonBool(formatStateJson, "inHeaderFooter") || jsonBool(formatStateJson, "in_header_footer")) {
                add("in_header_footer")
            }
            if (jsonBool(formatStateJson, "inEquation") || jsonBool(formatStateJson, "in_equation")) {
                add("in_equation")
            }
            if (jsonBool(formatStateJson, "imageSelected") || jsonBool(formatStateJson, "image_selected")) {
                add("image_selected")
            }
            if (jsonBool(formatStateJson, "shapeSelected") || jsonBool(formatStateJson, "shape_selected")) {
                add("shape_selected")
            }
        }

    private fun jsonBool(json: String, key: String): Boolean {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*(true|false)""")
        return pattern.find(json)?.groupValues?.get(1) == "true"
    }

    private fun jsonString(json: String, key: String): String? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*"([^"]*)"""")
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun jsonNumber(json: String, key: String): Double? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*(-?\d+(?:\.\d+)?)""")
        return pattern.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun foldSpanish(input: String): String =
        input.lowercase()
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')
            .replace('ü', 'u')
}
