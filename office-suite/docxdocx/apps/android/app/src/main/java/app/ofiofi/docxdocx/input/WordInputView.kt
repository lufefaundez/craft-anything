package app.ofiofi.docxdocx.input

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import uniffi.word_mobile_ffi.CommandOutcome
import uniffi.word_mobile_ffi.MobileDocumentSession

/**
 * Pure Kotlin bridge between Android's [InputConnection] lifecycle and
 * `MobileDocumentSession`, tracking active IME composition (tildes, ñ, swipe, dictation).
 */
interface DocumentTextSink {
    fun commitText(text: String, joinWithPrevious: Boolean): CommandOutcome
    fun deleteSurrounding(beforeChars: Int, afterChars: Int): CommandOutcome
    fun executeCommand(commandId: String, paramsJson: String): CommandOutcome
}

class SessionDocumentTextSink(
    private val session: MobileDocumentSession,
) : DocumentTextSink {
    override fun commitText(text: String, joinWithPrevious: Boolean): CommandOutcome =
        session.imeCommitText(text, joinWithPrevious)

    override fun deleteSurrounding(beforeChars: Int, afterChars: Int): CommandOutcome =
        session.imeDeleteSurrounding(beforeChars.toUInt(), afterChars.toUInt())

    override fun executeCommand(commandId: String, paramsJson: String): CommandOutcome =
        session.executeCommand(commandId, paramsJson)
}

class WordInputBridge(
    private val sink: DocumentTextSink,
    private val onOutcome: (CommandOutcome) -> Unit,
) {
    var composingCharCount: Int = 0
        private set

    fun setComposingText(text: CharSequence?): Boolean {
        val next = text?.toString().orEmpty()
        if (composingCharCount > 0) {
            onOutcome(sink.deleteSurrounding(composingCharCount, 0))
            composingCharCount = 0
        }
        if (next.isNotEmpty()) {
            onOutcome(sink.commitText(next, joinWithPrevious = false))
            composingCharCount = next.length
        }
        return true
    }

    fun finishComposingText(): Boolean {
        composingCharCount = 0
        return true
    }

    fun commitText(text: CharSequence?): Boolean {
        val incoming = text?.toString().orEmpty()
        val hadComposing = composingCharCount > 0
        if (hadComposing) {
            onOutcome(sink.deleteSurrounding(composingCharCount, 0))
            composingCharCount = 0
        }
        if (incoming.isNotEmpty()) {
            val outcome = if (incoming == "\n") {
                sink.executeCommand("text.newParagraph", "{}")
            } else {
                sink.commitText(incoming, joinWithPrevious = !hadComposing)
            }
            onOutcome(outcome)
        }
        return true
    }

    fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        val safeBefore = beforeLength.coerceAtLeast(0)
        val safeAfter = afterLength.coerceAtLeast(0)
        if (safeBefore == 0 && safeAfter == 0) return true
        if (composingCharCount > 0) {
            composingCharCount = (composingCharCount - safeBefore).coerceAtLeast(0)
        }
        onOutcome(sink.deleteSurrounding(safeBefore, safeAfter))
        return true
    }

    fun handleKeyEvent(event: KeyEvent): Boolean =
        dispatchKey(
            isActionDown = event.action == KeyEvent.ACTION_DOWN,
            keyCode = event.keyCode,
            isCtrlOrMeta = event.isCtrlPressed || event.isMetaPressed,
            isShift = event.isShiftPressed,
            unicodeChar = event.unicodeChar,
        )

    fun dispatchKey(
        isActionDown: Boolean,
        keyCode: Int,
        isCtrlOrMeta: Boolean,
        isShift: Boolean,
        unicodeChar: Int = 0,
    ): Boolean {
        if (!isActionDown) return false

        if (isCtrlOrMeta) {
            val cmd = when (keyCode) {
                KeyEvent.KEYCODE_B -> "format.bold"
                KeyEvent.KEYCODE_I -> "format.italic"
                KeyEvent.KEYCODE_U -> "format.underline"
                KeyEvent.KEYCODE_Z -> if (isShift) "edit.redo" else "edit.undo"
                KeyEvent.KEYCODE_Y -> "edit.redo"
                KeyEvent.KEYCODE_A -> "select.all"
                KeyEvent.KEYCODE_C -> "edit.copy"
                KeyEvent.KEYCODE_X -> "edit.cut"
                KeyEvent.KEYCODE_V -> "edit.paste"
                else -> null
            }
            if (cmd != null) {
                finishComposingText()
                onOutcome(sink.executeCommand(cmd, "{}"))
                return true
            }
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                deleteSurroundingText(1, 0)
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                deleteSurroundingText(0, 1)
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                finishComposingText()
                onOutcome(sink.executeCommand("text.newParagraph", "{}"))
                true
            }
            KeyEvent.KEYCODE_TAB -> {
                finishComposingText()
                onOutcome(sink.executeCommand("text.tab", "{}"))
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                finishComposingText()
                onOutcome(sink.executeCommand("caret.left", "{}"))
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                finishComposingText()
                onOutcome(sink.executeCommand("caret.right", "{}"))
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                finishComposingText()
                onOutcome(sink.executeCommand("caret.up", "{}"))
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                finishComposingText()
                onOutcome(sink.executeCommand("caret.down", "{}"))
                true
            }
            else -> {
                if (unicodeChar > 0 && !Character.isISOControl(unicodeChar)) {
                    commitText(String(Character.toChars(unicodeChar)))
                } else {
                    false
                }
            }
        }
    }
}

/**
 * Custom [BaseInputConnection] forwarding Android soft-keyboard composing spans, diacritics,
 * dictation chunks, and hardware keyboard shortcuts to [WordInputBridge].
 */
class WordInputConnection(
    targetView: View,
    private val bridge: WordInputBridge,
) : BaseInputConnection(targetView, true) {
    private val editableBuffer = SpannableStringBuilder()

    override fun getEditable(): Editable = editableBuffer

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        super.setComposingText(text, newCursorPosition)
        return bridge.setComposingText(text)
    }

    override fun finishComposingText(): Boolean {
        super.finishComposingText()
        return bridge.finishComposingText()
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        super.commitText(text, newCursorPosition)
        return bridge.commitText(text)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        super.deleteSurroundingText(beforeLength, afterLength)
        return bridge.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (bridge.handleKeyEvent(event)) return true
        return super.sendKeyEvent(event)
    }
}

/**
 * Focusable Android [View] acting as the IME text-editor endpoint over the tile canvas.
 */
class WordInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var bridge: WordInputBridge? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val activeBridge = bridge ?: return null
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        return WordInputConnection(this, activeBridge)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (bridge?.handleKeyEvent(event) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    fun showSoftKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }
}

@Composable
fun WordInputHost(
    bridge: WordInputBridge,
    onViewReady: (WordInputView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            WordInputView(ctx).also { view ->
                view.bridge = bridge
                onViewReady(view)
            }
        },
        update = { view ->
            view.bridge = bridge
        },
        modifier = modifier,
    )
}
