package app.ofiofi.docxdocx

import app.ofiofi.docxdocx.ui.ribbon.RibbonCorePresets
import app.ofiofi.docxdocx.ui.shell.AdaptiveShellModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RibbonCoreTabsTest {
    @Test
    fun `parseActiveToggleIds detects wordcraft strike highlight and formatting toggles`() {
        val sampleState = """
            {
              "font": "Calibri",
              "size": 14.0,
              "bold": true,
              "italic": false,
              "underline": true,
              "strike": true,
              "highlight": "FFFF00",
              "style": "Heading1",
              "styleName": "Heading 1",
              "color": "185ABD",
              "align": "justify"
            }
        """.trimIndent()

        val toggles = AdaptiveShellModel.parseActiveToggleIds(sampleState)
        assertTrue(toggles.contains("format.bold"))
        assertTrue(toggles.contains("format.underline"))
        assertTrue(toggles.contains("format.strike"))
        assertTrue(toggles.contains("format.highlight"))
        assertTrue(toggles.contains("para.justify"))

        val summary = AdaptiveShellModel.parseFormatSummary(sampleState)
        assertEquals("Calibri", summary.font)
        assertEquals("14", summary.size)
        assertEquals("Heading 1", summary.style)
        assertEquals("185ABD", summary.colorHex)
    }

    @Test
    fun `core presets provide Chilean Oficio Carta Legal A4 and interactive engine payloads`() {
        val sizes = RibbonCorePresets.paperSizes
        val oficio = sizes.first { it.id == "oficio_cl" }
        assertEquals("""{"width":612,"height":935.43}""", oficio.paramsJson)
        assertTrue(sizes.any { it.id == "letter" && it.paramsJson == """{"name":"Letter"}""" })
        assertTrue(sizes.any { it.id == "a4" && it.paramsJson == """{"name":"A4"}""" })

        assertEquals("""{"rows":4,"cols":6}""", RibbonCorePresets.buildTableInsertPayload(4, 6))
        assertEquals("""{"rows":8,"cols":1}""", RibbonCorePresets.buildTableInsertPayload(20, 0))
        assertEquals("""{"name":"Aptos"}""", RibbonCorePresets.buildFontPayload("Aptos"))
        assertEquals("""{"size":10.5}""", RibbonCorePresets.buildSizePayload("10.5"))
        assertEquals("""{"color":"185ABD"}""", RibbonCorePresets.buildColorPayload("format.color", "185ABD"))
        assertEquals("""{"color":null}""", RibbonCorePresets.buildColorPayload("design.pageColor", null))
    }

    @Test
    fun `presetsForCommand covers Disposicion Inicio and Insertar interactive menus`() {
        assertEquals(5, RibbonCorePresets.presetsForCommand("layout.margins").size)
        assertEquals(2, RibbonCorePresets.presetsForCommand("layout.orientation").size)
        assertEquals(5, RibbonCorePresets.presetsForCommand("layout.columns").size)
        assertEquals(4, RibbonCorePresets.presetsForCommand("layout.break").size)
        assertEquals(5, RibbonCorePresets.presetsForCommand("format.changeCase").size)
        assertEquals(4, RibbonCorePresets.presetsForCommand("insert.pageNumber").size)
        assertEquals(6, RibbonCorePresets.styles.size)
    }
}
