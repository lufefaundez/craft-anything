package app.ofiofi.docxdocx.input

import android.view.KeyEvent

data class HardwareShortcutMatch(
    val commandId: String,
    val paramsJson: String = "{}",
    val labelEsCl: String,
)

/**
 * Pure-Kotlin hardware keyboard shortcut resolver matching Microsoft Word Windows/Android bindings.
 */
object KeyboardShortcutRegistry {
    fun resolveNormalizedKey(
        keyName: String,
        ctrlOrMeta: Boolean,
        shift: Boolean = false,
        alt: Boolean = false,
    ): HardwareShortcutMatch? {
        if (!ctrlOrMeta || alt) return null
        return when (keyName.uppercase()) {
            "B" -> HardwareShortcutMatch("format.bold", "{}", "Negrita (Ctrl+B)")
            "I", "K" -> HardwareShortcutMatch("format.italic", "{}", "Cursiva (Ctrl+I)")
            "U" -> HardwareShortcutMatch("format.underline", "{}", "Subrayado (Ctrl+U)")
            "Z" -> if (shift) {
                HardwareShortcutMatch("edit.redo", "{}", "Rehacer (Ctrl+Shift+Z)")
            } else {
                HardwareShortcutMatch("edit.undo", "{}", "Deshacer (Ctrl+Z)")
            }
            "Y" -> HardwareShortcutMatch("edit.redo", "{}", "Rehacer (Ctrl+Y)")
            "C" -> HardwareShortcutMatch("edit.copy", "{}", "Copiar (Ctrl+C)")
            "X" -> HardwareShortcutMatch("edit.cut", "{}", "Cortar (Ctrl+X)")
            "V" -> HardwareShortcutMatch("edit.paste", "{}", "Pegar (Ctrl+V)")
            "A" -> HardwareShortcutMatch("select.all", "{}", "Seleccionar todo (Ctrl+A)")
            "F" -> HardwareShortcutMatch("edit.find", "{}", "Buscar (Ctrl+F)")
            "H" -> HardwareShortcutMatch("edit.findReplace", "{}", "Reemplazar (Ctrl+H)")
            "P" -> HardwareShortcutMatch("file.print", "{}", "Imprimir (Ctrl+P)")
            "S" -> HardwareShortcutMatch("file.save", "{}", "Guardar (Ctrl+S)")
            "L" -> HardwareShortcutMatch("para.alignLeft", "{}", "Alinear a la izquierda (Ctrl+L)")
            "E" -> HardwareShortcutMatch("para.alignCenter", "{}", "Centrar (Ctrl+E)")
            "R" -> HardwareShortcutMatch("para.alignRight", "{}", "Alinear a la derecha (Ctrl+R)")
            "J" -> HardwareShortcutMatch("para.justify", "{}", "Justificar (Ctrl+J)")
            "1" -> HardwareShortcutMatch("para.single", "{}", "Interlineado sencillo (Ctrl+1)")
            "2" -> HardwareShortcutMatch("para.double", "{}", "Interlineado doble (Ctrl+2)")
            "5" -> HardwareShortcutMatch("para.oneAndHalf", "{}", "Interlineado 1,5 (Ctrl+5)")
            "ENTER" -> HardwareShortcutMatch("layout.break", """{"kind":"page"}""", "Salto de página (Ctrl+Enter)")
            "EQUALS" -> if (shift) {
                HardwareShortcutMatch("format.superscript", "{}", "Superíndice (Ctrl+Shift+=)")
            } else {
                HardwareShortcutMatch("format.subscript", "{}", "Subíndice (Ctrl+=)")
            }
            else -> null
        }
    }

    fun resolveKeyEvent(event: KeyEvent): HardwareShortcutMatch? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        val ctrlOrMeta = event.isCtrlPressed || event.isMetaPressed
        if (!ctrlOrMeta) return null
        val name = when (event.keyCode) {
            KeyEvent.KEYCODE_B -> "B"
            KeyEvent.KEYCODE_I -> "I"
            KeyEvent.KEYCODE_K -> "K"
            KeyEvent.KEYCODE_U -> "U"
            KeyEvent.KEYCODE_Z -> "Z"
            KeyEvent.KEYCODE_Y -> "Y"
            KeyEvent.KEYCODE_C -> "C"
            KeyEvent.KEYCODE_X -> "X"
            KeyEvent.KEYCODE_V -> "V"
            KeyEvent.KEYCODE_A -> "A"
            KeyEvent.KEYCODE_F -> "F"
            KeyEvent.KEYCODE_H -> "H"
            KeyEvent.KEYCODE_P -> "P"
            KeyEvent.KEYCODE_S -> "S"
            KeyEvent.KEYCODE_L -> "L"
            KeyEvent.KEYCODE_E -> "E"
            KeyEvent.KEYCODE_R -> "R"
            KeyEvent.KEYCODE_J -> "J"
            KeyEvent.KEYCODE_1 -> "1"
            KeyEvent.KEYCODE_2 -> "2"
            KeyEvent.KEYCODE_5 -> "5"
            KeyEvent.KEYCODE_ENTER -> "ENTER"
            KeyEvent.KEYCODE_EQUALS -> "EQUALS"
            else -> return null
        }
        return resolveNormalizedKey(
            keyName = name,
            ctrlOrMeta = true,
            shift = event.isShiftPressed,
            alt = event.isAltPressed,
        )
    }
}
