package com.example.ohmyssh.terminal

object CellColor {
    const val DEFAULT: Int = -1
    const val RGB_FLAG: Int = 1 shl 24

    fun indexed(index: Int): Int = index and 0xFF

    fun rgb(r: Int, g: Int, b: Int): Int =
        RGB_FLAG or (r shl 16) or (g shl 8) or b

    fun isRgb(color: Int): Boolean = color >= RGB_FLAG
}

object CellAttr {
    const val BOLD = 1
    const val DIM = 2
    const val ITALIC = 4
    const val UNDERLINE = 8
    const val INVERSE = 16
    const val INVISIBLE = 32
    const val STRIKETHROUGH = 64
}

class TermLine(cols: Int) {
    var chars = CharArray(cols) { ' ' }
    var fg = IntArray(cols) { CellColor.DEFAULT }
    var bg = IntArray(cols) { CellColor.DEFAULT }
    var attrs = IntArray(cols)

    val size: Int get() = chars.size

    fun clear(from: Int = 0, to: Int = size, bgColor: Int = CellColor.DEFAULT) {
        for (i in from until to.coerceAtMost(size)) {
            chars[i] = ' '
            fg[i] = CellColor.DEFAULT
            bg[i] = bgColor
            attrs[i] = 0
        }
    }

    fun resize(cols: Int) {
        // Read the old width once: [size] is derived from chars, so it changes
        // under the loops below and would leave the grown cells holding the
        // zeros copyOf pads with — NUL text on an ANSI-black background.
        val old = chars.size
        if (cols == old) return
        chars = chars.copyOf(cols).also { arr -> for (i in old until cols) arr[i] = ' ' }
        fg = fg.copyOf(cols).also { arr -> for (i in old until cols) arr[i] = CellColor.DEFAULT }
        bg = bg.copyOf(cols).also { arr -> for (i in old until cols) arr[i] = CellColor.DEFAULT }
        attrs = attrs.copyOf(cols)
    }

    fun set(x: Int, ch: Char, fgColor: Int, bgColor: Int, attr: Int) {
        if (x < 0 || x >= size) return
        chars[x] = ch
        fg[x] = fgColor
        bg[x] = bgColor
        attrs[x] = attr
    }

    fun insertCells(x: Int, count: Int, bgColor: Int) {
        if (x >= size) return
        val n = count.coerceAtMost(size - x)
        chars.copyInto(chars, x + n, x, size - n)
        fg.copyInto(fg, x + n, x, size - n)
        bg.copyInto(bg, x + n, x, size - n)
        attrs.copyInto(attrs, x + n, x, size - n)
        clear(x, x + n, bgColor)
    }

    fun deleteCells(x: Int, count: Int, bgColor: Int) {
        if (x >= size) return
        val n = count.coerceAtMost(size - x)
        chars.copyInto(chars, x, x + n, size)
        fg.copyInto(fg, x, x + n, size)
        bg.copyInto(bg, x, x + n, size)
        attrs.copyInto(attrs, x, x + n, size)
        clear(size - n, size, bgColor)
    }

    fun textRange(from: Int, to: Int): String =
        chars.concatToString(from.coerceIn(0, size), to.coerceIn(0, size))
}
