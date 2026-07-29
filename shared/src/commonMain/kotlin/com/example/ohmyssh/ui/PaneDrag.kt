package com.example.ohmyssh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.fs.FileBrowserState
import com.example.ohmyssh.fs.FileBrowsers
import com.example.ohmyssh.fs.FileEntry
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.theme.appColors
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

class DragPayload(
    val sourceKey: String,
    val browser: FileBrowserState,
    val entries: List<FileEntry>,
) {
    val label: String
        get() = if (entries.size == 1) entries.first().name else "${entries.size} items"
}

object PaneDrag {
    var payload: DragPayload? by mutableStateOf(null)
        private set

    var position: Offset by mutableStateOf(Offset.Zero)
        private set

    private val targets = mutableStateMapOf<String, Rect>()

    val hovered: String?
        get() {
            val active = payload ?: return null
            return targets.entries
                .firstOrNull { (key, bounds) ->
                    key != active.sourceKey && bounds.contains(position)
                }
                ?.key
        }

    fun setTarget(key: String, bounds: Rect) {
        targets[key] = bounds
    }

    fun removeTarget(key: String) {
        targets.remove(key)
    }

    fun begin(payload: DragPayload, at: Offset) {
        this.payload = payload
        position = at
    }

    fun moveTo(at: Offset) {
        if (payload != null) position = at
    }

    fun cancel() {
        payload = null
    }

    fun drop() {
        val active = payload ?: return
        val targetKey = hovered
        payload = null
        if (targetKey == null) return

        val target = FileBrowsers.byKey(targetKey) ?: return
        target.scope.launch {
            try {
                val report = target.receive(active.browser, active.entries)
                val skipped = if (report.skipped > 0) {
                    " · ${report.skipped} symlinked folder" +
                        "${if (report.skipped == 1) "" else "s"} skipped"
                } else {
                    ""
                }
                AppToasts.show(
                    "Copied ${report.copied} file${if (report.copied == 1) "" else "s"}$skipped",
                )
            } catch (failure: Exception) {
                Log.error("files", "copy failed: $failure", failure)
                AppToasts.show("Copy failed: $failure")
            }
        }
    }
}

@Composable
fun Modifier.paneDropTarget(key: String): Modifier {
    androidx.compose.runtime.DisposableEffect(key) {
        onDispose { PaneDrag.removeTarget(key) }
    }
    return onGloballyPositioned { coordinates ->
        PaneDrag.setTarget(key, coordinates.boundsInWindowSafe())
    }
}

private fun LayoutCoordinates.boundsInWindowSafe(): Rect {
    val origin = positionInWindow()
    return Rect(origin.x, origin.y, origin.x + size.width, origin.y + size.height)
}

@Composable
fun Modifier.paneDragSource(
    key: Any,
    payload: () -> DragPayload,
    onLongPress: () -> Unit,
): Modifier {
    var coordinates by remember(key) { mutableStateOf<LayoutCoordinates?>(null) }

    return this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(key) {
            fun toWindow(local: Offset): Offset =
                coordinates?.let { it.positionInWindow() + local } ?: local

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)

                var released = false
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation()
                    released = true
                }
                if (released) return@awaitEachGesture

                PaneDrag.begin(payload(), toWindow(down.position))
                var moved = false
                try {
                    while (true) {
                        val change = nextChange(down) ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            break
                        }
                        if ((change.position - down.position).getDistance() >
                            viewConfiguration.touchSlop
                        ) {
                            moved = true
                        }
                        PaneDrag.moveTo(toWindow(change.position))
                        change.consume()
                    }
                } finally {
                    if (moved) PaneDrag.drop() else PaneDrag.cancel()
                }
                if (!moved) onLongPress()
            }
        }
}

private suspend fun AwaitPointerEventScope.nextChange(
    down: PointerInputChange,
): PointerInputChange? = awaitPointerEvent().changes.firstOrNull { it.id == down.id }

@Composable
fun PaneDragGhost(rootOrigin: Offset) {
    val payload = PaneDrag.payload ?: return
    val colors = appColors
    val local = PaneDrag.position - rootOrigin

    Box(
        Modifier
            .offset {
                IntOffset((local.x + 14f).roundToInt(), (local.y + 14f).roundToInt())
            }
            .alpha(0.92f)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.accent)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                payload.label,
                style = TextStyle(
                    color = colors.onAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                ),
            )
        }
    }
}
