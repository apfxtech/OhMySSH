package com.example.ohmyssh.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

class TerminalPalette(
    val cursor: Color,
    val selection: Color,
    val foreground: Color,
    val background: Color,
    val ansi: List<Color>,
) {
    fun colorFor(cell: Int, isForeground: Boolean, attr: Int): Color {
        var color = when {
            cell == CellColor.DEFAULT -> if (isForeground) foreground else background
            CellColor.isRgb(cell) -> Color(
                red = (cell shr 16 and 0xFF) / 255f,
                green = (cell shr 8 and 0xFF) / 255f,
                blue = (cell and 0xFF) / 255f,
            )
            cell < 16 -> ansi[cell]
            cell < 232 -> {
                val index = cell - 16
                val r = index / 36
                val g = (index % 36) / 6
                val b = index % 6
                fun channel(v: Int): Float = (if (v == 0) 0 else 55 + v * 40) / 255f
                Color(channel(r), channel(g), channel(b))
            }
            else -> {
                val gray = (8 + (cell - 232) * 10) / 255f
                Color(gray, gray, gray)
            }
        }
        if (isForeground && attr and CellAttr.BOLD != 0 && cell in 0..7) {
            color = ansi[cell + 8]
        }
        if (isForeground && attr and CellAttr.DIM != 0) {
            color = color.copy(alpha = color.alpha * 0.55f)
        }
        return color
    }
}

@Composable
fun TerminalView(
    terminal: TerminalEmulator,
    palette: TerminalPalette,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    contentPadding: Dp = 8.dp,
    readOnly: Boolean = false,
    autofocus: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }

    val baseStyle = TextStyle(fontSize = fontSize, fontFamily = FontFamily.Monospace)
    val cellLayout = remember(fontSize, density) {
        measurer.measure("W", baseStyle)
    }
    // size.width rounds the glyph advance up to a whole pixel; text is drawn
    // with the true fractional advance, so the per-column error accumulates
    // and the cursor drifts past the text. getLineRight keeps the fraction.
    val cellWidth = cellLayout.getLineRight(0)
    val cellHeight = cellLayout.size.height.toFloat()

    var scrollOffsetRows by remember { mutableIntStateOf(0) }
    var scrollRemainder by remember { mutableStateOf(0f) }

    // The IME bridge: a sentinel keeps backspace detectable as a deletion.
    val sentinel = "​​"
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(sentinel, selection = TextRange(sentinel.length)))
    }

    fun send(data: String) {
        if (readOnly) return
        scrollOffsetRows = 0
        terminal.sendKeys(data)
    }

    // The requester is not attached until the field is laid out, and a request
    // before that throws rather than no-opping.
    fun focusInput() {
        runCatching { focusRequester.requestFocus() }
    }

    BoxWithConstraints(
        modifier
            .background(palette.background)
            .onPreviewKeyEvent { event ->
                if (readOnly) return@onPreviewKeyEvent false
                // Paste first, so Ctrl/Cmd+V never reaches the shell as ^V.
                if (isPasteChord(event)) {
                    clipboard.getText()?.text?.let { send(it.replace("\r\n", "\r").replace("\n", "\r")) }
                    return@onPreviewKeyEvent true
                }
                val encoded = encodeKeyEvent(event, terminal.applicationCursorKeys)
                if (encoded != null) {
                    send(encoded)
                    true
                } else {
                    false
                }
            }
            .scrollable(
                orientation = Orientation.Vertical,
                state = rememberScrollableState { delta ->
                    scrollRemainder += delta
                    val rowsDelta = (scrollRemainder / cellHeight).toInt()
                    if (rowsDelta != 0) {
                        scrollRemainder -= rowsDelta * cellHeight
                        scrollOffsetRows =
                            (scrollOffsetRows + rowsDelta).coerceIn(0, terminal.scrollbackSize)
                    }
                    delta
                },
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusInput() })
            },
    ) {
        val insetPx = with(density) { contentPadding.toPx() } * 2
        val widthPx = (with(density) { maxWidth.toPx() } - insetPx).coerceAtLeast(0f)
        val heightPx = (with(density) { maxHeight.toPx() } - insetPx).coerceAtLeast(0f)
        val cols = max(2, floor(widthPx / cellWidth).toInt())
        val rows = max(2, floor(heightPx / cellHeight).toInt())

        // A degenerate box (a tab that is laid out at zero size) would otherwise
        // shrink the emulator to 2x2 and throw the session's screen away.
        val measured = widthPx >= cellWidth * 2 && heightPx >= cellHeight * 2
        LaunchedEffect(cols, rows, measured) {
            if (!measured) return@LaunchedEffect
            terminal.resize(cols, rows)
            terminal.onResize?.invoke(cols, rows, widthPx.roundToInt(), heightPx.roundToInt())
        }

        if (autofocus) {
            LaunchedEffect(Unit) { focusInput() }
        }

        val revision = terminal.revision

        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(contentPadding)) {
            @Suppress("UNUSED_EXPRESSION")
            revision
            terminal.snapshot(scrollOffsetRows) { lines, cursorRow ->
                for ((row, line) in lines.withIndex()) {
                    val y = row * cellHeight
                    var x = 0
                    while (x < line.size) {
                        val bgColor = line.bg[x]
                        val bgAttr = line.attrs[x] and CellAttr.INVERSE
                        var end = x + 1
                        while (end < line.size && line.bg[end] == bgColor &&
                            (line.attrs[end] and CellAttr.INVERSE) == bgAttr
                        ) {
                            end++
                        }
                        val inverse = bgAttr != 0
                        val color = if (inverse) {
                            palette.colorFor(line.fg[x], true, 0)
                        } else {
                            palette.colorFor(bgColor, false, 0)
                        }
                        if (color != palette.background || inverse) {
                            drawRect(
                                color = color,
                                topLeft = Offset(x * cellWidth, y),
                                size = Size((end - x) * cellWidth, cellHeight),
                            )
                        }
                        x = end
                    }

                    x = 0
                    while (x < line.size) {
                        val fgColor = line.fg[x]
                        val attr = line.attrs[x]
                        var end = x + 1
                        while (end < line.size && line.fg[end] == fgColor && line.attrs[end] == attr) {
                            end++
                        }
                        val text = line.textRange(x, end)
                        if (text.isNotBlank() && attr and CellAttr.INVISIBLE == 0) {
                            val inverse = attr and CellAttr.INVERSE != 0
                            val color = if (inverse) {
                                palette.colorFor(line.bg[x], false, 0)
                            } else {
                                palette.colorFor(fgColor, true, attr)
                            }
                            drawText(
                                textMeasurer = measurer,
                                text = text,
                                topLeft = Offset(x * cellWidth, y),
                                style = baseStyle.copy(
                                    color = color,
                                    fontWeight = if (attr and CellAttr.BOLD != 0) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (attr and CellAttr.ITALIC != 0) FontStyle.Italic else FontStyle.Normal,
                                ),
                            )
                            if (attr and CellAttr.UNDERLINE != 0) {
                                drawRect(
                                    color = color,
                                    topLeft = Offset(x * cellWidth, y + cellHeight - 2),
                                    size = Size((end - x) * cellWidth, 1.5f),
                                )
                            }
                        }
                        x = end
                    }
                }

                if (terminal.cursorVisible && scrollOffsetRows == 0 && !readOnly) {
                    val cx = terminal.cursorX * cellWidth
                    val cy = cursorRow * cellHeight
                    if (cursorRow < lines.size) {
                        drawRect(
                            color = palette.cursor.copy(alpha = 0.85f),
                            topLeft = Offset(cx, cy),
                            size = Size(cellWidth, cellHeight),
                        )
                    }
                }
            }
        }

        BasicTextField(
            value = fieldValue,
            onValueChange = { next ->
                val text = next.text
                when {
                    text.length < sentinel.length -> {
                        send("\u007F")
                        fieldValue = TextFieldValue(sentinel, selection = TextRange(sentinel.length))
                    }
                    text.length > sentinel.length -> {
                        val typed = text.removePrefix(sentinel)
                        if (typed.isNotEmpty()) send(typed.replace("\n", "\r"))
                        fieldValue = TextFieldValue(sentinel, selection = TextRange(sentinel.length))
                    }
                    else -> fieldValue = next.copy(selection = TextRange(sentinel.length))
                }
            },
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            // No .focusable() here: that would add a focus target of its own
            // and swallow the request, leaving the field — and so every
            // keystroke — unfocused.
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focusRequester),
        )
    }
}

private fun isPasteChord(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return event.key == Key.V && (event.isCtrlPressed || event.isMetaPressed)
}
