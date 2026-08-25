package com.example.ohmyssh.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.ohmyssh.platform.epochMicros
import com.example.ohmyssh.theme.QAppColors
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import kotlinx.coroutines.withTimeoutOrNull
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

private class Selection(val start: ScreenPoint, val end: ScreenPoint)

private fun selectionOf(anchor: ScreenPoint?, head: ScreenPoint?): Selection? {
    if (anchor == null || head == null) return null
    val forward = anchor.row < head.row || (anchor.row == head.row && anchor.column <= head.column)
    return if (forward) Selection(anchor, head) else Selection(head, anchor)
}

private class ClickTracker {
    var micros = 0L
    var point: ScreenPoint? = null
}

private const val DOUBLE_CLICK_MICROS = 400_000L

@Composable
fun TerminalView(
    terminal: TerminalEmulator,
    paste: PasteQueue,
    palette: TerminalPalette,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    contentPadding: Dp = 8.dp,
    readOnly: Boolean = false,
    autofocus: Boolean = true,
) {
    val colors = appColors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
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
    val padPx = with(density) { contentPadding.toPx() }

    var scrollOffsetRows by remember { mutableIntStateOf(0) }
    var scrollRemainder by remember { mutableStateOf(0f) }

    var selectionAnchor by remember { mutableStateOf<ScreenPoint?>(null) }
    var selectionHead by remember { mutableStateOf<ScreenPoint?>(null) }
    val selection = selectionOf(selectionAnchor, selectionHead)
    val lastClick = remember { ClickTracker() }

    // The IME bridge: a sentinel keeps backspace detectable as a deletion.
    val sentinel = "​​"
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(sentinel, selection = TextRange(sentinel.length)))
    }

    fun clearSelection() {
        selectionAnchor = null
        selectionHead = null
    }

    // The requester is not attached until the field is laid out, and a request
    // before that throws rather than no-opping.
    fun focusInput() {
        runCatching { focusRequester.requestFocus() }
    }

    fun send(data: String) {
        if (readOnly) return
        scrollOffsetRows = 0
        clearSelection()
        terminal.sendKeys(data)
    }

    fun copySelection() {
        val range = selectionOf(selectionAnchor, selectionHead) ?: return
        val text = terminal.textBetween(range.start, range.end)
        clearSelection()
        if (text.isEmpty()) return
        clipboard.setText(AnnotatedString(text))
        AppToasts.show("Copied")
    }

    // Pasted text is handed to the queue rather than written straight out: a
    // block of commands has to arrive one at a time, or the first one that asks
    // a question is answered by the rest of the paste.
    fun submitPaste(text: String) {
        if (readOnly || text.isEmpty()) return
        scrollOffsetRows = 0
        clearSelection()
        paste.submit(text)
    }

    fun pasteClipboard() {
        submitPaste(clipboard.getText()?.text ?: return)
    }

    fun selectWord(point: ScreenPoint) {
        val word = terminal.wordAt(point)
        selectionAnchor = if (word == null) point else ScreenPoint(point.row, word.first)
        selectionHead = if (word == null) point else ScreenPoint(point.row, word.last)
    }

    BoxWithConstraints(
        modifier
            .background(palette.background)
            .onPreviewKeyEvent { event ->
                if (isCopyChord(event) && selection != null) {
                    copySelection()
                    return@onPreviewKeyEvent true
                }
                if (readOnly) return@onPreviewKeyEvent false
                // Paste first, so Ctrl/Cmd+V never reaches the shell as ^V.
                if (isPasteChord(event)) {
                    pasteClipboard()
                    return@onPreviewKeyEvent true
                }
                if (paste.remaining > 0 && isPasteCancelChord(event)) {
                    paste.cancel()
                    // Ctrl+C carries on to the shell: the command the queue is
                    // waiting on is the one that needs interrupting.
                    if (event.key == Key.Escape) return@onPreviewKeyEvent true
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
                detectTapGestures(
                    onTap = {
                        clearSelection()
                        focusInput()
                    },
                )
            }
            .pointerInput(cellWidth, cellHeight, padPx) {
                fun pointAt(position: Offset): ScreenPoint {
                    val column = floor((position.x - padPx) / cellWidth).toInt()
                        .coerceIn(0, max(0, terminal.viewWidth - 1))
                    val row = floor((position.y - padPx) / cellHeight).toInt()
                        .coerceIn(0, max(0, terminal.viewHeight - 1))
                    return ScreenPoint(terminal.topRow(scrollOffsetRows) + row, column)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPoint = pointAt(down.position)

                    if (down.type == PointerType.Mouse) {
                        if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                        var anchored = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!anchored) {
                                    val now = epochMicros()
                                    val again = lastClick.point == downPoint &&
                                        now - lastClick.micros < DOUBLE_CLICK_MICROS
                                    lastClick.micros = now
                                    lastClick.point = downPoint
                                    // Consuming the second click keeps the tap
                                    // handler above from wiping the word it just
                                    // selected.
                                    if (again) {
                                        selectWord(downPoint)
                                        change.consume()
                                    }
                                }
                                break
                            }
                            if (!event.buttons.isPrimaryPressed) break
                            val point = pointAt(change.position)
                            if (!anchored) {
                                if (point == downPoint) continue
                                anchored = true
                                selectionAnchor = downPoint
                            }
                            selectionHead = point
                            change.consume()
                        }
                        return@awaitEachGesture
                    }

                    // A touch drag scrolls; only a press that stays put turns
                    // into a selection, so the timeout expiring is the signal.
                    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@withTimeoutOrNull
                            if (!change.pressed || change.isConsumed) return@withTimeoutOrNull
                            val moved = (change.position - down.position).getDistance()
                            if (moved > viewConfiguration.touchSlop) return@withTimeoutOrNull
                        }
                    } == null
                    if (!held) return@awaitEachGesture

                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectWord(downPoint)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        selectionHead = pointAt(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        val boxWidthPx = with(density) { maxWidth.toPx() }
        val boxHeightPx = with(density) { maxHeight.toPx() }
        val widthPx = (boxWidthPx - padPx * 2).coerceAtLeast(0f)
        val heightPx = (boxHeightPx - padPx * 2).coerceAtLeast(0f)
        val cols = max(2, floor(widthPx / cellWidth).toInt())
        val rows = max(2, floor(heightPx / cellHeight).toInt())

        // A degenerate box (a tab that is laid out at zero size) would otherwise
        // shrink the emulator to 2x2 and throw the session's screen away.
        val measured = widthPx >= cellWidth * 2 && heightPx >= cellHeight * 2
        LaunchedEffect(cols, rows, measured) {
            if (!measured) return@LaunchedEffect
            // Reflow moves lines between the grid and the scrollback, so the
            // rows a selection points at are no longer the ones it was drawn on.
            clearSelection()
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
            terminal.snapshot(scrollOffsetRows) { lines, cursorRow, topRow ->
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

                    if (selection != null) {
                        val absolute = topRow + row
                        if (absolute >= selection.start.row && absolute <= selection.end.row) {
                            val from = if (absolute == selection.start.row) {
                                selection.start.column.coerceIn(0, line.size)
                            } else {
                                0
                            }
                            val to = if (absolute == selection.end.row) {
                                (selection.end.column + 1).coerceIn(0, line.size)
                            } else {
                                line.size
                            }
                            if (to > from) {
                                drawRect(
                                    color = palette.selection,
                                    topLeft = Offset(from * cellWidth, y),
                                    size = Size((to - from) * cellWidth, cellHeight),
                                )
                            }
                        }
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

        if (selection != null) {
            var pillSize by remember { mutableStateOf(IntSize.Zero) }
            val topRow = terminal.topRow(scrollOffsetRows)
            val startRow = (selection.start.row - topRow).toInt()
            val endRow = (selection.end.row - topRow).toInt()
            val shape = RoundedCornerShape(8.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .onSizeChanged { pillSize = it }
                    .offset {
                        val gap = cellHeight * 0.35f
                        val above = padPx + startRow * cellHeight - pillSize.height - gap
                        val below = padPx + (endRow + 1) * cellHeight + gap
                        val y = if (above >= 0f) above else below
                        val x = padPx + selection.start.column * cellWidth
                        IntOffset(
                            x.roundToInt()
                                .coerceIn(0, (boxWidthPx - pillSize.width).roundToInt().coerceAtLeast(0)),
                            y.roundToInt()
                                .coerceIn(0, (boxHeightPx - pillSize.height).roundToInt().coerceAtLeast(0)),
                        )
                    }
                    .clip(shape)
                    .background(colors.card)
                    .border(1.dp, colors.divider, shape),
            ) {
                SelectionAction(Icons.Outlined.ContentCopy, "Copy", colors) {
                    copySelection()
                    focusInput()
                }
                if (!readOnly) {
                    Box(Modifier.width(1.dp).height(18.dp).background(colors.divider))
                    SelectionAction(Icons.Outlined.ContentPaste, "Paste", colors) {
                        pasteClipboard()
                        focusInput()
                    }
                }
            }
        }

        if (paste.remaining > 0) {
            PasteStrip(paste, colors, Modifier.align(Alignment.TopEnd).padding(6.dp))
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
                        // The field hands over one keystroke at a time, so
                        // several characters with a newline among them came off
                        // the clipboard. A lone newline is the Enter key.
                        if (typed.length > 1 && typed.contains('\n')) {
                            submitPaste(typed)
                        } else if (typed.isNotEmpty()) {
                            send(typed.replace('\n', '\r'))
                        }
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

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    colors: QAppColors,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = colors.accent,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = TextStyle(color = colors.textPrimary, fontSize = 12.5.sp))
    }
}

@Composable
private fun PasteStrip(paste: PasteQueue, colors: QAppColors, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val label = when (paste.waiting) {
        PasteWait.SECRET -> "Paste held · password prompt"
        PasteWait.FULLSCREEN -> "Paste held"
        PasteWait.PROMPT -> "Waiting for prompt"
        PasteWait.NONE -> "Pasting"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.divider, shape),
    ) {
        Text(
            "$label · ${paste.remaining} left",
            style = TextStyle(color = colors.textSecondary, fontSize = 12.5.sp),
            modifier = Modifier.padding(start = 11.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
        )
        Box(Modifier.width(1.dp).height(18.dp).background(colors.divider))
        Text(
            "Cancel",
            style = TextStyle(color = colors.accent, fontSize = 12.5.sp),
            modifier = Modifier
                .clickable { paste.cancel() }
                .padding(horizontal = 11.dp, vertical = 7.dp),
        )
    }
}

/// Esc drops what is left of a paste; so does Ctrl+C, which is the key someone
/// reaches for when the command the queue is waiting on has to stop.
private fun isPasteCancelChord(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return event.key == Key.Escape || (event.key == Key.C && event.isCtrlPressed)
}

private fun isPasteChord(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return event.key == Key.V && (event.isCtrlPressed || event.isMetaPressed)
}

// Ctrl+C only copies while something is selected; with nothing selected it
// stays the interrupt the shell expects.
private fun isCopyChord(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return event.key == Key.C && (event.isCtrlPressed || event.isMetaPressed)
}
