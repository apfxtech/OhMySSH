package com.example.ohmyssh.terminal

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

private const val ESC = "\u001B"

fun encodeKeyEvent(event: KeyEvent, applicationCursorKeys: Boolean): String? {
    if (event.type != KeyEventType.KeyDown) return null

    val cursorPrefix = if (applicationCursorKeys) "${ESC}O" else "$ESC["

    val special = when (event.key) {
        Key.DirectionUp -> "${cursorPrefix}A"
        Key.DirectionDown -> "${cursorPrefix}B"
        Key.DirectionRight -> "${cursorPrefix}C"
        Key.DirectionLeft -> "${cursorPrefix}D"
        Key.MoveHome -> "${cursorPrefix}H"
        Key.MoveEnd -> "${cursorPrefix}F"
        Key.PageUp -> "$ESC[5~"
        Key.PageDown -> "$ESC[6~"
        Key.Delete -> "$ESC[3~"
        Key.Insert -> "$ESC[2~"
        Key.Enter, Key.NumPadEnter -> "\r"
        Key.Backspace -> "\u007F"
        Key.Tab -> if (event.isCtrlPressed) return null else "\t"
        Key.Escape -> ESC
        Key.F1 -> "${ESC}OP"
        Key.F2 -> "${ESC}OQ"
        Key.F3 -> "${ESC}OR"
        Key.F4 -> "${ESC}OS"
        Key.F5 -> "$ESC[15~"
        Key.F6 -> "$ESC[17~"
        Key.F7 -> "$ESC[18~"
        Key.F8 -> "$ESC[19~"
        Key.F9 -> "$ESC[20~"
        Key.F10 -> "$ESC[21~"
        Key.F11 -> "$ESC[23~"
        Key.F12 -> "$ESC[24~"
        else -> null
    }
    if (special != null) {
        return if (event.isAltPressed) ESC + special else special
    }

    if (event.isMetaPressed) return null
    if (event.isCtrlPressed) {
        val code = event.utf16CodePoint
        // Desktop delivers Ctrl+letter with the control character already in
        // the code point (^C == 3); mapping it as a letter would drop it.
        if (code in 1..31) return code.toChar().toString()
        val fromCode = (if (code > 0) code.toChar().lowercaseChar() else null)
            ?.takeIf { it in 'a'..'z' || it in "[\\]^_ " }
        val ch = fromCode ?: keyToChar(event.key) ?: return null
        val ctrl = when (ch) {
            in 'a'..'z' -> (ch - 'a' + 1).toChar()
            '[' -> '\u001B'
            '\\' -> '\u001C'
            ']' -> '\u001D'
            '^' -> '\u001E'
            '_' -> '\u001F'
            ' ' -> '\u0000'
            else -> return null
        }
        return ctrl.toString()
    }

    return null
}

private fun keyToChar(key: Key): Char? = when (key) {
    Key.A -> 'a'; Key.B -> 'b'; Key.C -> 'c'; Key.D -> 'd'; Key.E -> 'e'
    Key.F -> 'f'; Key.G -> 'g'; Key.H -> 'h'; Key.I -> 'i'; Key.J -> 'j'
    Key.K -> 'k'; Key.L -> 'l'; Key.M -> 'm'; Key.N -> 'n'; Key.O -> 'o'
    Key.P -> 'p'; Key.Q -> 'q'; Key.R -> 'r'; Key.S -> 's'; Key.T -> 't'
    Key.U -> 'u'; Key.V -> 'v'; Key.W -> 'w'; Key.X -> 'x'; Key.Y -> 'y'
    Key.Z -> 'z'; Key.Spacebar -> ' '
    else -> null
}
