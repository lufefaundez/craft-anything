package app.ofiofi.docxdocx

import app.ofiofi.docxdocx.ui.shell.AdaptiveShellModel
import app.ofiofi.docxdocx.ui.shell.RibbonGroupSlot
import app.ofiofi.docxdocx.ui.shell.ShellFormFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveShellTest {
    private val sampleCatalog = RibbonSchemaCatalog(
        coreTabs = listOf(
            RibbonTabSpec(
                id = "home",
                labelEsCl = "Inicio",
                labelEnUs = "Home",
                groups = listOf(
                    RibbonGroupSpec(
                        id = "clipboard",
                        labelEsCl = "Portapapeles",
                        labelEnUs = "Clipboard",
                        controls = listOf(
                            RibbonControlSpec("edit.paste", "split_button", "full", "ic_edit_paste", "Pegar", "Paste"),
                            RibbonControlSpec("edit.cut", "small_button", "0", "ic_edit_cut", "Cortar", "Cut"),
                            RibbonControlSpec("edit.copy", "small_button", "1", "ic_edit_copy", "Copiar", "Copy"),
                        ),
                    ),
                    RibbonGroupSpec(
                        id = "font",
                        labelEsCl = "Fuente",
                        labelEnUs = "Font",
                        dialogCommandId = "dialog.font",
                        controls = listOf(
                            RibbonControlSpec("format.bold", "toggle", "0", "ic_format_bold", "Negrita", "Bold"),
                        ),
                    ),
                ),
            ),
            RibbonTabSpec("insert", "Insertar", "Insert", emptyList()),
            RibbonTabSpec("draw", "Dibujar", "Draw", emptyList()),
            RibbonTabSpec("design", "Diseño", "Design", emptyList()),
            RibbonTabSpec("layout", "Disposición", "Layout", emptyList()),
            RibbonTabSpec("references", "Referencias", "References", emptyList()),
            RibbonTabSpec("mailings", "Correspondencia", "Mailings", emptyList()),
            RibbonTabSpec("review", "Revisar", "Review", emptyList()),
            RibbonTabSpec("view", "Vista", "View", emptyList()),
        ),
        contextualTabs = listOf(
            RibbonTabSpec("table_design", "Diseño de tabla", "Table Design", emptyList(), contextualTrigger = "in_table"),
            RibbonTabSpec("picture_format", "Formato de imagen", "Picture Format", emptyList(), contextualTrigger = "image_selected"),
        ),
        qat = listOf(
            RibbonControlSpec("edit.undo", "small_button", "0", "ic_edit_undo", "Deshacer", "Undo"),
        ),
        smartphoneQuickBar = listOf(
            RibbonControlSpec("format.bold", "toggle", "0", "ic_format_bold", "Negrita", "Bold"),
        ),
    )

    @Test
    fun adaptiveFormFactorSwitchesBetweenTabletRibbonAndSmartphoneBottomSheet() {
        assertEquals(ShellFormFactor.TABLET_EXPANDED_RIBBON, AdaptiveShellModel.resolveFormFactor(true))
        assertEquals(ShellFormFactor.PHONE_COMPACT_SHEET, AdaptiveShellModel.resolveFormFactor(false))
    }

    @Test
    fun groupLayoutBuildsTallAndStackedPairsCorrectly() {
        val clipboard = sampleCatalog.coreTabs.first().groups.first()
        val slots = AdaptiveShellModel.layoutGroupColumns(clipboard)
        assertEquals(2, slots.size)
        assertTrue(slots[0] is RibbonGroupSlot.Tall)
        assertTrue(slots[1] is RibbonGroupSlot.StackedPair)
        val pair = slots[1] as RibbonGroupSlot.StackedPair
        assertEquals("edit.cut", pair.top.commandId)
        assertEquals("edit.copy", pair.bottom?.commandId)
    }

    @Test
    fun contextualTabsAppearOnlyWhenContextTriggered() {
        assertEquals(9, AdaptiveShellModel.visibleTabs(sampleCatalog, emptySet()).size)
        val withTable = AdaptiveShellModel.visibleTabs(sampleCatalog, setOf("in_table"))
        assertEquals(10, withTable.size)
        assertEquals("table_design", withTable.last().id)
    }

    @Test
    fun commandSearchMatchesAccentFoldedSpanishLabelsAndCommandIds() {
        val negritaMatches = AdaptiveShellModel.searchCommands(sampleCatalog, "negrita")
        assertEquals(1, negritaMatches.size)
        assertEquals("format.bold", negritaMatches.first().control.commandId)

        val pasteMatches = AdaptiveShellModel.searchCommands(sampleCatalog, "edit.paste")
        assertEquals("edit.paste", pasteMatches.first().control.commandId)
    }

    @Test
    fun formatStateParserExtractsActiveTogglesAndContexts() {
        val json = """{"bold":true,"align":"center","inTable":true}"""
        val toggles = AdaptiveShellModel.parseActiveToggleIds(json)
        val contexts = AdaptiveShellModel.parseActiveContexts(json)

        assertTrue(toggles.contains("format.bold"))
        assertTrue(toggles.contains("para.alignCenter"))
        assertTrue(contexts.contains("in_table"))
    }
}
