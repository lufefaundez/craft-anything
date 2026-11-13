package app.ofiofi.docxdocx

import app.ofiofi.docxdocx.ui.dialogs.OfficeDialogRouter
import app.ofiofi.docxdocx.ui.dialogs.OfficeModalDialogKind
import app.ofiofi.docxdocx.ui.ink.InkEngineBridge
import app.ofiofi.docxdocx.ui.ink.InkPoint
import app.ofiofi.docxdocx.ui.ink.InkStroke
import app.ofiofi.docxdocx.ui.ink.InkToolKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogsAndInkTest {
    @Test
    fun `stylus ink engine maps draw commands calculates pressure width and hit tests eraser`() {
        assertEquals(InkToolKind.PEN, InkEngineBridge.toolForCommand("draw.pen"))
        assertEquals(InkToolKind.PENCIL, InkEngineBridge.toolForCommand("draw.pencil"))
        assertEquals(InkToolKind.HIGHLIGHTER, InkEngineBridge.toolForCommand("draw.highlighter"))
        assertEquals(InkToolKind.ERASER, InkEngineBridge.toolForCommand("draw.eraser"))
        assertEquals(InkToolKind.NONE, InkEngineBridge.toolForCommand("draw.select"))
        assertNull(InkEngineBridge.toolForCommand("format.bold"))

        val lightPen = InkEngineBridge.strokeWidthPx(InkToolKind.PEN, 4f, 0.2f)
        val heavyPen = InkEngineBridge.strokeWidthPx(InkToolKind.PEN, 4f, 1.0f)
        val marker = InkEngineBridge.strokeWidthPx(InkToolKind.HIGHLIGHTER, 4f, 0.5f)
        assertTrue(heavyPen > lightPen)
        assertTrue(marker > heavyPen)

        val stroke = InkStroke(
            id = 1L,
            tool = InkToolKind.PEN,
            argb = 0xFF185ABD,
            baseWidthDp = 3f,
            points = listOf(InkPoint(100f, 100f, 0.6f), InkPoint(140f, 105f, 0.7f)),
        )
        assertTrue(InkEngineBridge.hitTestStroke(stroke, 105f, 102f, radiusPx = 18f))
        assertFalse(InkEngineBridge.hitTestStroke(stroke, 300f, 300f, radiusPx = 18f))
    }

    @Test
    fun `modal dialog router resolves dialogs converts cm to pt and builds command batches`() {
        assertEquals(OfficeModalDialogKind.FONT, OfficeDialogRouter.dialogForCommand("format.fontDialog"))
        assertEquals(OfficeModalDialogKind.PARAGRAPH, OfficeDialogRouter.dialogForCommand("para.dialog"))
        assertEquals(OfficeModalDialogKind.TABS, OfficeDialogRouter.dialogForCommand("para.tabs"))
        assertEquals(OfficeModalDialogKind.PAGE_SETUP, OfficeDialogRouter.dialogForCommand("layout.pageSetup"))
        assertEquals(OfficeModalDialogKind.TABLE_PROPERTIES, OfficeDialogRouter.dialogForCommand("table.properties"))
        assertNull(OfficeDialogRouter.dialogForCommand("view.zoom100"))

        val threeCmPt = OfficeDialogRouter.cmToPt("3,0", 0f)
        assertEquals(85.04f, threeCmPt, 0.2f)

        val paraBatch = OfficeDialogRouter.buildParagraphCommands(
            alignCmd = "para.justify",
            indentLeftCm = "1.0",
            indentRightCm = "0.0",
            firstLineCm = "1.25",
            spaceBeforePt = "6",
            spaceAfterPt = "12",
            lineSpacingCmd = "para.oneAndHalf",
        )
        assertEquals("para.justify", paraBatch[0].commandId)
        assertEquals("para.indents", paraBatch[1].commandId)
        assertEquals("para.spacing", paraBatch[2].commandId)
        assertEquals("para.oneAndHalf", paraBatch[3].commandId)

        val pageBatch = OfficeDialogRouter.buildPageSetupCommands(
            topCm = "2.5",
            bottomCm = "2.5",
            leftCm = "3.0",
            rightCm = "3.0",
            orientation = "portrait",
            paperPresetId = "oficio_cl",
        )
        assertEquals(3, pageBatch.size)
        assertEquals("layout.margins", pageBatch[0].commandId)
        assertEquals("""{"value":"portrait"}""", pageBatch[1].paramsJson)
        assertEquals("""{"width":612,"height":935.43}""", pageBatch[2].paramsJson)

        val tabCmd = OfficeDialogRouter.buildTabStopCommand("2,0", "right", "dot")
        assertEquals("para.setTab", tabCmd.commandId)
        assertTrue(tabCmd.paramsJson.contains(""""align":"right""""))
        assertTrue(tabCmd.paramsJson.contains(""""leader":"dot""""))
    }
}
