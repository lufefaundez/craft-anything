package app.ofiofi.docxdocx

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ofiofi.docxdocx.input.SessionDocumentTextSink
import app.ofiofi.docxdocx.input.WordInputBridge
import app.ofiofi.docxdocx.input.WordInputConnection
import app.ofiofi.docxdocx.input.WordInputView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.word_mobile_ffi.MobileDocumentSession

/**
 * Instrumented Android tests running against live `libword_mobile_ffi.so` on device/emulator.
 * Verifies InputConnection composing (tildes/ñ), deletion, enter, tap, double-tap, triple-tap,
 * and selection handle dragging.
 */
@RunWith(AndroidJUnit4::class)
class InputTests {
    @Test
    fun inputConnectionAndTouchSelectionOperateAgainstLiveRustSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DocxAssetInitializer.ensureInitialized(context)

        val session = MobileDocumentSession.newBlank("es-CL")
        var lastOutcome = session.executeCommand("format.state", "{}")
        val bridge = WordInputBridge(SessionDocumentTextSink(session)) { outcome ->
            lastOutcome = outcome
        }

        val inputView = WordInputView(context).apply { this.bridge = bridge }
        assertTrue(inputView.onCheckIsTextEditor())
        val editorInfo = EditorInfo()
        val ic = inputView.onCreateInputConnection(editorInfo) as? WordInputConnection
        assertNotNull(ic)
        ic!!

        // Compose & commit Spanish accented text + ñ
        ic.setComposingText("Año", 1)
        ic.commitText("Año nuevo chileno", 1)
        assertEquals(3uL, lastOutcome.wordCount)

        // New paragraph via IME ENTER key event
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.commitText("Segunda línea con tilde rápida", 1)
        assertEquals(8uL, session.wordCount())

        // Tap & double-tap word selection at top paragraph coordinates
        val collapsed = session.tapAt(0u, 90f, 80f)
        assertNotNull(collapsed.caret)
        val wordSel = session.doubleTapAt(0u, 90f, 80f)
        assertFalse(wordSel.isCollapsed)

        // Triple-tap paragraph selection & handle drag
        val paraSel = session.tripleTapAt(0u, 90f, 80f)
        assertFalse(paraSel.isCollapsed)
        val dragged = session.dragSelectionHandle(false, 0u, 150f, 80f)
        assertNotNull(dragged.caret)

        // Delete 1 char via InputConnection backspace
        ic.deleteSurroundingText(1, 0)
        assertTrue(session.saveDocxBytes().isNotEmpty())
    }
}
