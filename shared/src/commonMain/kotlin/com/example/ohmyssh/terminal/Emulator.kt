package com.example.ohmyssh.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.platform.PlatformLock
import com.example.ohmyssh.platform.withLock

class TerminalEmulator(
    var cols: Int = 80,
    var rows: Int = 24,
    private val maxScrollback: Int = 10000,
) {
    private val lock = PlatformLock()

    // Scrollback + main screen. The grid is the last [rows] lines of [main].
    private val scrollback = ArrayDeque<TermLine>()
    private var main: MutableList<TermLine> = MutableList(rows) { TermLine(cols) }
    private var alt: MutableList<TermLine> = MutableList(rows) { TermLine(cols) }
    private var usingAlt = false

    /// Lines that have left the top of the main grid over the session's life.
    /// Grid row y sits at absolute row [scrolledLines] + y, which is what lets
    /// the command log hold on to a place on screen while output scrolls under
    /// it.
    private var scrolledLines = 0L

    var revision: Long by mutableLongStateOf(0L)
        private set

    var title: String = ""
        private set

    var cursorX = 0
        private set
    var cursorY = 0
        private set
    var cursorVisible = true
        private set

    private var savedX = 0
    private var savedY = 0
    private var savedFg = CellColor.DEFAULT
    private var savedBg = CellColor.DEFAULT
    private var savedAttr = 0

    private var fg = CellColor.DEFAULT
    private var bg = CellColor.DEFAULT
    private var attr = 0

    private var scrollTop = 0
    private var scrollBottom = rows - 1

    private var autowrap = true
    private var originMode = false
    private var pendingWrap = false
    var applicationCursorKeys = false
        private set
    private var insertMode = false

    private var charsetGraphics = false
    private var charsetGraphicsG1 = false
    private var usingG1 = false

    var onOutput: ((String) -> Unit)? = null
    var onResize: ((cols: Int, rows: Int, pixelWidth: Int, pixelHeight: Int) -> Unit)? = null
    var onBell: (() -> Unit)? = null

    var onInput: ((String) -> Unit)? = null

    var onShellSignal: ((ShellSignal) -> Unit)? = null

    val viewWidth: Int get() = cols
    val viewHeight: Int get() = rows

    val usingAltScreen: Boolean get() = usingAlt

    var isMeasured: Boolean = false
        private set

    val scrollbackSize: Int get() = lock.withLock { scrollback.size }

    private val grid: MutableList<TermLine> get() = if (usingAlt) alt else main

    private enum class ParseState { GROUND, ESCAPE, CSI, OSC, CHARSET_G0, CHARSET_G1, ESC_HASH }

    private var state = ParseState.GROUND
    private val csiParams = StringBuilder()
    private val oscBuffer = StringBuilder()
    private var oscEscape = false

    fun sendKeys(data: String) {
        if (data.isEmpty()) return
        onInput?.invoke(data)
        onOutput?.invoke(data)
    }

    fun write(text: String) {
        lock.withLock {
            for (ch in text) process(ch)
        }
        revision++
    }

    private fun process(ch: Char) {
        when (state) {
            ParseState.GROUND -> ground(ch)
            ParseState.ESCAPE -> escape(ch)
            ParseState.CSI -> csi(ch)
            ParseState.OSC -> osc(ch)
            ParseState.CHARSET_G0 -> {
                charsetGraphics = ch == '0'
                state = ParseState.GROUND
            }
            ParseState.CHARSET_G1 -> {
                charsetGraphicsG1 = ch == '0'
                state = ParseState.GROUND
            }
            ParseState.ESC_HASH -> {
                if (ch == '8') {
                    for (line in grid) {
                        for (x in 0 until cols) line.set(x, 'E', fg, bg, 0)
                    }
                }
                state = ParseState.GROUND
            }
        }
    }

    private fun ground(ch: Char) {
        when (ch) {
            '\u001B' -> state = ParseState.ESCAPE
            '\r' -> {
                cursorX = 0
                pendingWrap = false
            }
            '\n', '\u000B', '\u000C' -> lineFeed()
            '\b' -> {
                if (cursorX > 0) cursorX--
                pendingWrap = false
            }
            '\t' -> {
                cursorX = ((cursorX / 8) + 1) * 8
                if (cursorX >= cols) cursorX = cols - 1
            }
            '\u0007' -> onBell?.invoke()
            '\u000E' -> usingG1 = true
            '\u000F' -> usingG1 = false
            '\u0000' -> {}
            else -> {
                if (ch.code < 0x20) return
                putChar(ch)
            }
        }
    }

    private fun putChar(raw: Char) {
        val graphics = if (usingG1) charsetGraphicsG1 else charsetGraphics
        val ch = if (graphics) decGraphics(raw) else raw

        if (pendingWrap && autowrap) {
            grid[cursorY].wrapped = true
            cursorX = 0
            lineFeed()
        }
        pendingWrap = false

        val line = grid[cursorY]
        if (insertMode) line.insertCells(cursorX, 1, bg)
        line.set(cursorX, ch, fg, bg, attr)

        if (cursorX == cols - 1) {
            pendingWrap = true
        } else {
            cursorX++
        }
    }

    private fun lineFeed() {
        if (cursorY == scrollBottom) {
            scrollUp(1)
        } else if (cursorY < rows - 1) {
            cursorY++
        }
    }

    private fun scrollUp(count: Int) {
        repeat(count) {
            val removed = grid.removeAt(scrollTop)
            if (!usingAlt && scrollTop == 0) {
                scrollback.addLast(removed)
                if (scrollback.size > maxScrollback) scrollback.removeFirst()
                scrolledLines++
                grid.add(scrollBottom, TermLine(cols))
            } else {
                removed.clear(bgColor = bg)
                grid.add(scrollBottom, removed)
            }
        }
    }

    private fun scrollDown(count: Int) {
        repeat(count) {
            val removed = grid.removeAt(scrollBottom)
            removed.clear(bgColor = bg)
            grid.add(scrollTop, removed)
        }
    }

    private fun escape(ch: Char) {
        state = ParseState.GROUND
        when (ch) {
            '[' -> {
                csiParams.setLength(0)
                state = ParseState.CSI
            }
            ']' -> {
                oscBuffer.setLength(0)
                oscEscape = false
                state = ParseState.OSC
            }
            '(' -> state = ParseState.CHARSET_G0
            ')' -> state = ParseState.CHARSET_G1
            '#' -> state = ParseState.ESC_HASH
            '7' -> saveCursor()
            '8' -> restoreCursor()
            'D' -> lineFeed()
            'E' -> {
                cursorX = 0
                lineFeed()
            }
            'M' -> {
                if (cursorY == scrollTop) scrollDown(1) else if (cursorY > 0) cursorY--
            }
            'c' -> reset()
            '=' -> {}
            '>' -> {}
            else -> {}
        }
    }

    private fun osc(ch: Char) {
        when {
            ch == '\u0007' -> {
                finishOsc()
                state = ParseState.GROUND
            }
            ch == '\u001B' -> oscEscape = true
            oscEscape && ch == '\\' -> {
                finishOsc()
                state = ParseState.GROUND
            }
            else -> {
                oscEscape = false
                if (oscBuffer.length < 4096) oscBuffer.append(ch)
            }
        }
    }

    private fun finishOsc() {
        val payload = oscBuffer.toString()
        val split = payload.indexOf(';')
        if (split <= 0) return
        val body = payload.substring(split + 1)
        when (payload.substring(0, split)) {
            "0", "2" -> title = body
            "7" -> fileUrlPath(body).takeIf { it.isNotBlank() }
                ?.let { signal(ShellSignal.WorkingDirectory(it)) }
            "133" -> parseSemanticPrompt(body)?.let { signal(it) }
        }
    }

    private fun parseSemanticPrompt(body: String): ShellSignal? {
        val kind = body.firstOrNull() ?: return null
        val rest = body.drop(1).removePrefix(";")
        return when (kind) {
            'A' -> ShellSignal.PromptStart
            'B' -> ShellSignal.InputStart
            'C' -> ShellSignal.Executing(rest.substringAfter("cmd=", "").ifEmpty { null })
            'D' -> ShellSignal.Finished(rest.substringBefore(';').toIntOrNull())
            else -> null
        }
    }

    /// The lock is reentrant, so a listener is free to read the cursor and the
    /// screen from here. That matters: a prompt mark is only worth anything
    /// read at the exact point in the stream where it arrived.
    private fun signal(event: ShellSignal) {
        onShellSignal?.invoke(event)
    }

    private fun csi(ch: Char) {
        if (ch in '0'..'9' || ch == ';' || ch == '?' || ch == '>' || ch == '!' ||
            ch == '"' || ch == '\'' || ch == ' ' || ch == ':'
        ) {
            if (csiParams.length < 64) csiParams.append(ch)
            return
        }
        state = ParseState.GROUND

        val raw = csiParams.toString()
        val isPrivate = raw.startsWith("?")
        val params = raw.removePrefix("?").removePrefix(">").removeSuffix("\"").removeSuffix("!")
            .split(';')
            .map { it.substringBefore(':') }
            .map { it.toIntOrNull() ?: 0 }

        fun p(index: Int, default: Int = 0): Int = params.getOrNull(index) ?: default
        fun pOr1(index: Int): Int = p(index).let { if (it == 0) 1 else it }

        when (ch) {
            'A' -> moveCursor(cursorX, cursorY - pOr1(0))
            'B' -> moveCursor(cursorX, cursorY + pOr1(0))
            'C' -> moveCursor(cursorX + pOr1(0), cursorY)
            'D' -> moveCursor(cursorX - pOr1(0), cursorY)
            'E' -> moveCursor(0, cursorY + pOr1(0))
            'F' -> moveCursor(0, cursorY - pOr1(0))
            'G', '`' -> moveCursor(pOr1(0) - 1, cursorY)
            'H', 'f' -> {
                val top = if (originMode) scrollTop else 0
                moveCursor(pOr1(1) - 1, top + pOr1(0) - 1)
            }
            'd' -> moveCursor(cursorX, pOr1(0) - 1)
            'J' -> eraseDisplay(p(0))
            'K' -> eraseLine(p(0))
            'L' -> insertLines(pOr1(0))
            'M' -> deleteLines(pOr1(0))
            '@' -> grid[cursorY].insertCells(cursorX, pOr1(0), bg)
            'P' -> grid[cursorY].deleteCells(cursorX, pOr1(0), bg)
            'X' -> grid[cursorY].clear(cursorX, cursorX + pOr1(0), bg)
            'S' -> scrollUp(pOr1(0))
            'T' -> scrollDown(pOr1(0))
            'm' -> sgr(params)
            'r' -> {
                scrollTop = (pOr1(0) - 1).coerceIn(0, rows - 1)
                scrollBottom = (p(1, rows).let { if (it == 0) rows else it } - 1)
                    .coerceIn(scrollTop, rows - 1)
                moveCursor(0, if (originMode) scrollTop else 0)
            }
            'h' -> setMode(params, isPrivate, true)
            'l' -> setMode(params, isPrivate, false)
            'c' -> onOutput?.invoke("\u001B[?6c")
            'n' -> when (p(0)) {
                5 -> onOutput?.invoke("\u001B[0n")
                6 -> onOutput?.invoke("\u001B[${cursorY + 1};${cursorX + 1}R")
            }
            's' -> saveCursor()
            'u' -> restoreCursor()
            't' -> {}
            else -> {}
        }
    }

    private fun moveCursor(x: Int, y: Int) {
        val minY = if (originMode) scrollTop else 0
        val maxY = if (originMode) scrollBottom else rows - 1
        cursorX = x.coerceIn(0, cols - 1)
        cursorY = y.coerceIn(minY, maxY)
        pendingWrap = false
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0)
                for (y in cursorY + 1 until rows) grid[y].clear(bgColor = bg)
            }
            1 -> {
                eraseLine(1)
                for (y in 0 until cursorY) grid[y].clear(bgColor = bg)
            }
            2 -> for (y in 0 until rows) grid[y].clear(bgColor = bg)
            3 -> {
                for (y in 0 until rows) grid[y].clear(bgColor = bg)
                scrollback.clear()
            }
        }
    }

    private fun eraseLine(mode: Int) {
        val line = grid[cursorY]
        when (mode) {
            0 -> line.clear(cursorX, cols, bg)
            1 -> line.clear(0, cursorX + 1, bg)
            2 -> line.clear(bgColor = bg)
        }
    }

    private fun insertLines(count: Int) {
        if (cursorY < scrollTop || cursorY > scrollBottom) return
        repeat(count.coerceAtMost(scrollBottom - cursorY + 1)) {
            val removed = grid.removeAt(scrollBottom)
            removed.clear(bgColor = bg)
            grid.add(cursorY, removed)
        }
    }

    private fun deleteLines(count: Int) {
        if (cursorY < scrollTop || cursorY > scrollBottom) return
        repeat(count.coerceAtMost(scrollBottom - cursorY + 1)) {
            val removed = grid.removeAt(cursorY)
            removed.clear(bgColor = bg)
            grid.add(scrollBottom, removed)
        }
    }

    private fun setMode(params: List<Int>, isPrivate: Boolean, enable: Boolean) {
        for (mode in params) {
            if (isPrivate) {
                when (mode) {
                    1 -> applicationCursorKeys = enable
                    6 -> {
                        originMode = enable
                        moveCursor(0, if (originMode) scrollTop else 0)
                    }
                    7 -> autowrap = enable
                    25 -> cursorVisible = enable
                    47, 1047 -> switchScreen(enable)
                    1048 -> if (enable) saveCursor() else restoreCursor()
                    1049 -> {
                        if (enable) {
                            saveCursor()
                            switchScreen(true)
                            for (y in 0 until rows) alt[y].clear()
                            moveCursor(0, 0)
                        } else {
                            switchScreen(false)
                            restoreCursor()
                        }
                    }
                    2004 -> {}
                }
            } else {
                when (mode) {
                    4 -> insertMode = enable
                }
            }
        }
    }

    private fun switchScreen(toAlt: Boolean) {
        if (usingAlt == toAlt) return
        usingAlt = toAlt
        scrollTop = 0
        scrollBottom = rows - 1
    }

    private fun sgr(params: List<Int>) {
        var i = 0
        if (params.isEmpty()) {
            fg = CellColor.DEFAULT
            bg = CellColor.DEFAULT
            attr = 0
            return
        }
        while (i < params.size) {
            when (val code = params[i]) {
                0 -> {
                    fg = CellColor.DEFAULT
                    bg = CellColor.DEFAULT
                    attr = 0
                }
                1 -> attr = attr or CellAttr.BOLD
                2 -> attr = attr or CellAttr.DIM
                3 -> attr = attr or CellAttr.ITALIC
                4 -> attr = attr or CellAttr.UNDERLINE
                7 -> attr = attr or CellAttr.INVERSE
                8 -> attr = attr or CellAttr.INVISIBLE
                9 -> attr = attr or CellAttr.STRIKETHROUGH
                21, 22 -> attr = attr and (CellAttr.BOLD or CellAttr.DIM).inv()
                23 -> attr = attr and CellAttr.ITALIC.inv()
                24 -> attr = attr and CellAttr.UNDERLINE.inv()
                27 -> attr = attr and CellAttr.INVERSE.inv()
                28 -> attr = attr and CellAttr.INVISIBLE.inv()
                29 -> attr = attr and CellAttr.STRIKETHROUGH.inv()
                in 30..37 -> fg = CellColor.indexed(code - 30)
                39 -> fg = CellColor.DEFAULT
                in 40..47 -> bg = CellColor.indexed(code - 40)
                49 -> bg = CellColor.DEFAULT
                in 90..97 -> fg = CellColor.indexed(code - 90 + 8)
                in 100..107 -> bg = CellColor.indexed(code - 100 + 8)
                38, 48 -> {
                    val setFg = code == 38
                    when (params.getOrNull(i + 1)) {
                        5 -> {
                            val index = params.getOrNull(i + 2) ?: 0
                            if (setFg) fg = CellColor.indexed(index) else bg = CellColor.indexed(index)
                            i += 2
                        }
                        2 -> {
                            val r = params.getOrNull(i + 2) ?: 0
                            val g = params.getOrNull(i + 3) ?: 0
                            val b = params.getOrNull(i + 4) ?: 0
                            if (setFg) fg = CellColor.rgb(r, g, b) else bg = CellColor.rgb(r, g, b)
                            i += 4
                        }
                    }
                }
            }
            i++
        }
    }

    private fun saveCursor() {
        savedX = cursorX
        savedY = cursorY
        savedFg = fg
        savedBg = bg
        savedAttr = attr
    }

    private fun restoreCursor() {
        cursorX = savedX.coerceIn(0, cols - 1)
        cursorY = savedY.coerceIn(0, rows - 1)
        fg = savedFg
        bg = savedBg
        attr = savedAttr
        pendingWrap = false
    }

    private fun reset() {
        fg = CellColor.DEFAULT
        bg = CellColor.DEFAULT
        attr = 0
        scrollTop = 0
        scrollBottom = rows - 1
        autowrap = true
        originMode = false
        insertMode = false
        applicationCursorKeys = false
        cursorVisible = true
        charsetGraphics = false
        charsetGraphicsG1 = false
        usingG1 = false
        for (y in 0 until rows) grid[y].clear()
        moveCursor(0, 0)
    }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols < 2 || newRows < 2) return
        isMeasured = true
        if (newCols == cols && newRows == rows) return
        lock.withLock {
            for (line in scrollback) line.resize(newCols)
            for (screen in listOf(main, alt)) {
                for (line in screen) line.resize(newCols)
                while (screen.size < newRows) screen.add(TermLine(newCols))
                while (screen.size > newRows) {
                    val removed = screen.removeAt(0)
                    if (screen === main) {
                        scrollback.addLast(removed)
                        if (scrollback.size > maxScrollback) scrollback.removeFirst()
                        scrolledLines++
                    }
                }
            }
            cols = newCols
            rows = newRows
            scrollTop = 0
            scrollBottom = rows - 1
            cursorX = cursorX.coerceIn(0, cols - 1)
            cursorY = cursorY.coerceIn(0, rows - 1)
        }
        revision++
    }

    fun <T> snapshot(
        offset: Int,
        block: (lines: List<TermLine>, cursorRow: Int, topRow: Long) -> T,
    ): T =
        lock.withLock {
            val back = scrollback.size
            val clamped = offset.coerceIn(0, back)
            val lines = ArrayList<TermLine>(rows)
            var index = back - clamped
            while (lines.size < rows && index < back) {
                lines.add(scrollback[index])
                index++
            }
            var gridIndex = 0
            while (lines.size < rows && gridIndex < rows) {
                lines.add(grid[gridIndex])
                gridIndex++
            }
            val cursorRow = if (clamped == 0) cursorY else cursorY + clamped
            block(lines, cursorRow, scrolledLines - clamped)
        }

    /// Absolute row of the first line [snapshot] returns at this offset.
    /// A selection is held in absolute rows, so it stays on its own text while
    /// output scrolls underneath it.
    fun topRow(offset: Int): Long = lock.withLock {
        scrolledLines - offset.coerceIn(0, scrollback.size)
    }

    fun cursorPoint(): ScreenPoint = lock.withLock {
        ScreenPoint(scrolledLines + cursorY, cursorX)
    }

    fun logicalLineAt(row: Long): LogicalLine? = lock.withLock {
        if (usingAlt) return@withLock null
        val first = lineAt(row) ?: return@withLock null

        val text = StringBuilder(first.textRange(0, first.size))
        var last = row
        var line = first
        // Bounded by what the emulator can hold, so a wrapped flag left set on
        // a recycled row cannot spin this.
        val limit = row + rows + maxScrollback
        while (line.wrapped && last < limit) {
            val next = lineAt(last + 1) ?: break
            text.append(next.textRange(0, next.size))
            line = next
            last++
        }
        LogicalLine(text = text.toString(), firstRow = row, lastRow = last)
    }

    private fun lineAt(row: Long): TermLine? {
        val gridRow = row - scrolledLines
        if (gridRow >= 0) return grid.getOrNull(gridRow.toInt())
        val index = scrollback.size + gridRow
        return if (index >= 0) scrollback.getOrNull(index.toInt()) else null
    }

    /// Text of everything between two absolute points, both ends inclusive.
    fun textBetween(from: ScreenPoint, to: ScreenPoint): String = lock.withLock {
        val forward = from.row < to.row || (from.row == to.row && from.column <= to.column)
        val start = if (forward) from else to
        val end = if (forward) to else from
        buildString {
            var row = start.row
            while (row <= end.row) {
                val line = lineAt(row)
                if (line != null) {
                    val first = if (row == start.row) start.column.coerceIn(0, line.size) else 0
                    val last =
                        if (row == end.row) (end.column + 1).coerceIn(0, line.size) else line.size
                    val text = line.textRange(first, last)
                    // A wrapped row carries no line break of its own: adding one
                    // would cut a long command in half on paste.
                    if (line.wrapped && row < end.row) {
                        append(text)
                    } else {
                        // Only a run that reaches the end of a row can be the
                        // blank padding; a range picked out mid-row is kept as
                        // selected, spaces and all.
                        append(if (last >= line.size) text.trimEnd() else text)
                        if (row < end.row) append('\n')
                    }
                }
                row++
            }
        }
    }

    /// The run of non-blank cells around [point], for select-a-word — kept
    /// whitespace-delimited so a path or a URL comes out whole.
    fun wordAt(point: ScreenPoint): IntRange? = lock.withLock {
        val line = lineAt(point.row) ?: return@withLock null
        if (line.size == 0) return@withLock null
        val x = point.column.coerceIn(0, line.size - 1)
        if (line.chars[x].isWhitespace()) return@withLock null
        var first = x
        while (first > 0 && !line.chars[first - 1].isWhitespace()) first--
        var last = x
        while (last + 1 < line.size && !line.chars[last + 1].isWhitespace()) last++
        first..last
    }

    fun allText(): String = lock.withLock {
        buildString {
            for (line in scrollback) appendLine(line.textRange(0, line.size).trimEnd())
            for (line in grid) appendLine(line.textRange(0, line.size).trimEnd())
        }
    }

    private fun decGraphics(ch: Char): Char = when (ch) {
        'j' -> '┘'
        'k' -> '┐'
        'l' -> '┌'
        'm' -> '└'
        'n' -> '┼'
        'q' -> '─'
        't' -> '├'
        'u' -> '┤'
        'v' -> '┴'
        'w' -> '┬'
        'x' -> '│'
        'a' -> '▒'
        '`' -> '◆'
        '~' -> '·'
        'f' -> '°'
        'g' -> '±'
        'o' -> '⎺'
        'p' -> '⎻'
        'r' -> '⎼'
        's' -> '⎽'
        else -> ch
    }
}
