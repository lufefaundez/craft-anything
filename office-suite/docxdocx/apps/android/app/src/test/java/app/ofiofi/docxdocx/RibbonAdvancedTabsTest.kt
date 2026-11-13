package app.ofiofi.docxdocx

import app.ofiofi.docxdocx.ui.canvas.RulerMath
import app.ofiofi.docxdocx.ui.ribbon.AdvancedToolPanel
import app.ofiofi.docxdocx.ui.ribbon.RibbonAdvancedPresets
import app.ofiofi.docxdocx.ui.shell.AdaptiveShellModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RibbonAdvancedTabsTest {
    @Test
    fun `find replace comment and footnote payload builders escape quotes and preserve flags`() {
        assertEquals(
            """{"text":"Art. \"88\"","matchCase":true,"wholeWord":false}""",
            RibbonAdvancedPresets.buildFindPayload("Art. \"88\"", matchCase = true, wholeWord = false),
        )
        assertEquals(
            """{"text":"A","with":"B\nC","matchCase":false,"wholeWord":true}""",
            RibbonAdvancedPresets.buildReplacePayload("A", "B\nC", matchCase = false, wholeWord = true),
        )
        assertEquals(
            """{"text":"Revisar firma"}""",
            RibbonAdvancedPresets.buildCommentPayload("  Revisar firma  "),
        )
        assertEquals(
            """{"text":"Fuente: Regl. PNI"}""",
            RibbonAdvancedPresets.buildFootnotePayload("Fuente: Regl. PNI"),
        )
    }

    @Test
    fun `panel triggers contextual banners and ruler toggle work cleanly`() {
        assertEquals(AdvancedToolPanel.FIND_REPLACE, RibbonAdvancedPresets.panelForCommand("edit.find"))
        assertEquals(AdvancedToolPanel.FIND_REPLACE, RibbonAdvancedPresets.panelForCommand("edit.findReplace"))
        assertEquals(AdvancedToolPanel.NEW_COMMENT, RibbonAdvancedPresets.panelForCommand("review.newComment"))
        assertEquals(AdvancedToolPanel.NEW_FOOTNOTE, RibbonAdvancedPresets.panelForCommand("references.footnote"))
        assertNull(RibbonAdvancedPresets.panelForCommand("format.bold"))

        assertEquals(
            "Herramientas de tabla: Diseño y Disposición",
            RibbonAdvancedPresets.contextualBannerLabel(setOf("in_table")),
        )
        assertEquals(
            "Encabezado y pie de página",
            RibbonAdvancedPresets.contextualBannerLabel(setOf("in_header_footer")),
        )
        assertNull(RibbonAdvancedPresets.contextualBannerLabel(emptySet()))

        val reviewActive = AdaptiveShellModel.parseActiveToggleIds("""{"trackChanges":true,"marks":true}""")
        assertTrue(reviewActive.contains("review.trackChanges"))
        assertTrue(reviewActive.contains("view.marks"))
    }

    @Test
    fun `centimeter ruler math produces expected ticks across US Letter width`() {
        val ticks = RulerMath.buildTicks(totalPoints = 612f, marginStartPt = 72f)
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.all { it.positionPt in 0f..612f })
        assertTrue(ticks.any { it.isMajor && it.label != null })
        assertTrue(ticks.any { !it.isMajor })
    }
}
