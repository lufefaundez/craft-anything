package app.ofiofi.docxdocx

import app.ofiofi.docxdocx.input.KeyboardShortcutRegistry
import app.ofiofi.docxdocx.print.PrintLayoutHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrintAdapterTest {
    @Test
    fun `print layout helper converts points to mils expands page ranges and identifies Oficio Chile`() {
        // US Letter 612pt = 8.5 in -> 8500 mils
        assertEquals(8500, PrintLayoutHelper.pointsToMils(612f))
        assertEquals(11000, PrintLayoutHelper.pointsToMils(792f))

        assertEquals(listOf(0, 1, 2, 3, 4), PrintLayoutHelper.expandPageRanges(emptyList(), totalPages = 5))
        assertEquals(listOf(0, 1, 3, 4), PrintLayoutHelper.expandPageRanges(listOf(0 to 1, 3 to 9), totalPages = 5))
        assertEquals(emptyList<Int>(), PrintLayoutHelper.expandPageRanges(listOf(0 to 2), totalPages = 0))

        assertEquals("Oficio Chile", PrintLayoutHelper.selectMediaSizeLabel(612f, 935.43f))
        assertEquals("ISO A4", PrintLayoutHelper.selectMediaSizeLabel(595.28f, 841.89f))
        assertEquals("US Legal", PrintLayoutHelper.selectMediaSizeLabel(612f, 1008f))
        assertEquals("Carta", PrintLayoutHelper.selectMediaSizeLabel(612f, 792f))
    }

    @Test
    fun `keyboard shortcut registry maps complete Word formatting navigation print and editing bindings`() {
        assertEquals("format.bold", KeyboardShortcutRegistry.resolveNormalizedKey("B", ctrlOrMeta = true)?.commandId)
        assertEquals("format.italic", KeyboardShortcutRegistry.resolveNormalizedKey("I", ctrlOrMeta = true)?.commandId)
        assertEquals("format.underline", KeyboardShortcutRegistry.resolveNormalizedKey("U", ctrlOrMeta = true)?.commandId)
        assertEquals("edit.undo", KeyboardShortcutRegistry.resolveNormalizedKey("Z", ctrlOrMeta = true, shift = false)?.commandId)
        assertEquals("edit.redo", KeyboardShortcutRegistry.resolveNormalizedKey("Z", ctrlOrMeta = true, shift = true)?.commandId)
        assertEquals("edit.find", KeyboardShortcutRegistry.resolveNormalizedKey("F", ctrlOrMeta = true)?.commandId)
        assertEquals("edit.findReplace", KeyboardShortcutRegistry.resolveNormalizedKey("H", ctrlOrMeta = true)?.commandId)
        assertEquals("file.print", KeyboardShortcutRegistry.resolveNormalizedKey("P", ctrlOrMeta = true)?.commandId)
        assertEquals("para.justify", KeyboardShortcutRegistry.resolveNormalizedKey("J", ctrlOrMeta = true)?.commandId)
        assertEquals("para.oneAndHalf", KeyboardShortcutRegistry.resolveNormalizedKey("5", ctrlOrMeta = true)?.commandId)
        assertEquals(
            """{"kind":"page"}""",
            KeyboardShortcutRegistry.resolveNormalizedKey("ENTER", ctrlOrMeta = true)?.paramsJson,
        )
        assertEquals(
            "format.subscript",
            KeyboardShortcutRegistry.resolveNormalizedKey("EQUALS", ctrlOrMeta = true, shift = false)?.commandId,
        )
        assertEquals(
            "format.superscript",
            KeyboardShortcutRegistry.resolveNormalizedKey("EQUALS", ctrlOrMeta = true, shift = true)?.commandId,
        )
        assertNull(KeyboardShortcutRegistry.resolveNormalizedKey("B", ctrlOrMeta = false))
    }
}
