package app.ofiofi.docxdocx

import app.ofiofi.docxdocx.ui.canvas.RulerMath
import app.ofiofi.docxdocx.ui.shell.DesktopEnvironmentDetector
import app.ofiofi.docxdocx.ui.shell.DesktopEnvironmentSignals
import app.ofiofi.docxdocx.ui.shell.DesktopModePreference
import app.ofiofi.docxdocx.ui.shell.DocCommentParser
import app.ofiofi.docxdocx.ui.shell.DocxFontCatalog
import app.ofiofi.docxdocx.ui.shell.ShellMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopDeXAndRulerTest {
    @Test
    fun `resolveShellMode activates DESKTOP automatically on Samsung DeX and Android Desk mode`() {
        val dexOnPhoneScreen = DesktopEnvironmentSignals(
            isSamsungDex = true,
            isUiModeDesk = false,
            hasPcFeature = false,
            hasHardwareKeyboard = false,
            hasMousePointer = false,
            smallestScreenWidthDp = 390,
            windowWidthDp = 1280,
            preference = DesktopModePreference.AUTO,
        )
        assertEquals(ShellMode.DESKTOP, DesktopEnvironmentDetector.resolveShellMode(dexOnPhoneScreen))

        val mouseAndKeyboardOnWideMonitor = DesktopEnvironmentSignals(
            isSamsungDex = false,
            isUiModeDesk = false,
            hasPcFeature = false,
            hasHardwareKeyboard = true,
            hasMousePointer = true,
            smallestScreenWidthDp = 390,
            windowWidthDp = 960,
            preference = DesktopModePreference.AUTO,
        )
        assertEquals(ShellMode.DESKTOP, DesktopEnvironmentDetector.resolveShellMode(mouseAndKeyboardOnWideMonitor))

        val normalPhonePortrait = DesktopEnvironmentSignals(
            isSamsungDex = false,
            isUiModeDesk = false,
            hasPcFeature = false,
            hasHardwareKeyboard = false,
            hasMousePointer = false,
            smallestScreenWidthDp = 390,
            windowWidthDp = 412,
            preference = DesktopModePreference.AUTO,
        )
        assertEquals(ShellMode.PHONE, DesktopEnvironmentDetector.resolveShellMode(normalPhonePortrait))

        val manualDesktopOverrideOnPhone = normalPhonePortrait.copy(preference = DesktopModePreference.FORCE_DESKTOP)
        assertEquals(ShellMode.DESKTOP, DesktopEnvironmentDetector.resolveShellMode(manualDesktopOverrideOnPhone))
    }

    @Test
    fun `ruler payloads clamp and format margins and indents for engine commands`() {
        val marginJson = RulerMath.buildMarginsPayload(leftPt = 54.0f, rightPt = 72.5f)
        assertEquals("""{"left":54.0,"right":72.5}""", marginJson)

        val clampedMargins = RulerMath.buildMarginsPayload(topPt = 2.0f, bottomPt = 400.0f)
        assertEquals("""{"top":14.0,"bottom":280.0}""", clampedMargins)

        val indentsJson = RulerMath.buildIndentsPayload(leftPt = 18.0f, rightPt = 9.0f, firstLinePt = 28.3f)
        assertEquals("""{"left":18.0,"right":9.0,"firstLine":28.3}""", indentsJson)

        val ticks = RulerMath.buildTicks(totalPoints = 612f, marginStartPt = 72f)
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.any { it.isMajor && it.label == "1" })
    }

    @Test
    fun `DocCommentParser parses review comments threads and resolved states`() {
        val json = """
            [
              {"id":1,"author":"Luis Felipe","date":"2026-11-13T10:00Z","resolved":false,"parent":null,"text":"Revisar encabezado"},
              {"id":2,"author":"Asistente","date":"2026-11-13T10:05Z","resolved":true,"parent":1,"text":"Corregido"}
            ]
        """.trimIndent()
        val parsed = DocCommentParser.parseList(json)
        assertEquals(2, parsed.size)
        assertEquals(1, parsed[0].id)
        assertEquals("Luis Felipe", parsed[0].author)
        assertEquals(null, parsed[0].parent)
        assertEquals(1, parsed[1].parent)
        assertTrue(parsed[1].resolved)
    }

    @Test
    fun `DocxFontCatalog merges system families deduplicated without hidden dot fonts`() {
        val merged = DocxFontCatalog.mergeFontFamilies(
            listOf(".SF NS", "Roboto", "Helvetica Neue", "calibri", "Noto Serif"),
        )
        assertTrue(merged.none { it.startsWith(".") })
        assertEquals(1, merged.count { it.equals("Calibri", ignoreCase = true) })
        assertTrue(merged.contains("Roboto"))
        assertTrue(merged.contains("Helvetica Neue"))
        assertTrue(merged.contains("Noto Serif"))
    }
}
