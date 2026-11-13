package app.ofiofi.docxdocx

import android.view.KeyEvent
import app.ofiofi.docxdocx.input.DocumentTextSink
import app.ofiofi.docxdocx.input.WordInputBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.word_mobile_ffi.CommandOutcome
import uniffi.word_mobile_ffi.SelectionGeometry

private class FakeTextSink : DocumentTextSink {
    val events = mutableListOf<String>()

    override fun commitText(text: String, joinWithPrevious: Boolean): CommandOutcome {
        events += "commit:$text:$joinWithPrevious"
        return dummyOutcome()
    }

    override fun deleteSurrounding(beforeChars: Int, afterChars: Int): CommandOutcome {
        events += "del:$beforeChars:$afterChars"
        return dummyOutcome()
    }

    override fun executeCommand(commandId: String, paramsJson: String): CommandOutcome {
        events += "cmd:$commandId"
        return dummyOutcome()
    }

    private fun dummyOutcome() = CommandOutcome(
        resultJson = "{}",
        formatStateJson = "{}",
        dirty = true,
        canUndo = true,
        canRedo = false,
        pageCount = 1u,
        wordCount = 1uL,
        status = "OK",
        selection = SelectionGeometry(caret = null, selectionRects = emptyList(), isCollapsed = true),
    )
}

class WordInputBridgeTest {
    @Test
    fun spanishComposingTextReplacesActiveCompositionAndCommitsCleanly() {
        val sink = FakeTextSink()
        val outcomes = mutableListOf<CommandOutcome>()
        val bridge = WordInputBridge(sink) { outcomes += it }

        bridge.setComposingText("ca")
        bridge.setComposingText("canció")
        bridge.commitText("canción ")

        assertEquals(0, bridge.composingCharCount)
        assertEquals(
            listOf(
                "commit:ca:false",
                "del:2:0",
                "commit:canció:false",
                "del:6:0",
                "commit:canción :false",
            ),
            sink.events,
        )
        assertTrue(outcomes.size == 5)
    }

    @Test
    fun hardwareCtrlShortcutsAndEnterMapToExactWordcraftCommands() {
        val sink = FakeTextSink()
        val bridge = WordInputBridge(sink) {}

        assertTrue(bridge.dispatchKey(isActionDown = true, keyCode = KeyEvent.KEYCODE_ENTER, isCtrlOrMeta = false, isShift = false))
        assertTrue(bridge.dispatchKey(isActionDown = true, keyCode = KeyEvent.KEYCODE_B, isCtrlOrMeta = true, isShift = false))
        assertTrue(bridge.dispatchKey(isActionDown = true, keyCode = KeyEvent.KEYCODE_DEL, isCtrlOrMeta = false, isShift = false))

        assertEquals(listOf("cmd:text.newParagraph", "cmd:format.bold", "del:1:0"), sink.events)
    }
}
