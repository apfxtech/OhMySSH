package com.example.ohmyssh.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QPageAppBarAction
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.navigation.PlatformBackHandler
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.fs.FileSource
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.confirmDestructive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

const val kMaxEditableBytes = 1 shl 20

private val fontSize = 13.sp

private const val MAX_DIFF_LINES = 1500

fun computeModifiedLines(saved: List<String>, current: List<String>): Set<Int> {
    var lo = 0
    val shortest = min(saved.size, current.size)
    while (lo < shortest && saved[lo] == current[lo]) lo++
    if (lo == saved.size && lo == current.size) return emptySet()

    var hiSaved = saved.size
    var hiCurrent = current.size
    while (hiSaved > lo && hiCurrent > lo && saved[hiSaved - 1] == current[hiCurrent - 1]) {
        hiSaved--
        hiCurrent--
    }

    val before = saved.subList(lo, hiSaved)
    val after = current.subList(lo, hiCurrent)
    val n = before.size
    val m = after.size
    if (m == 0) return emptySet()
    if (n == 0 || n > MAX_DIFF_LINES || m > MAX_DIFF_LINES) {
        return (lo until lo + m).toSet()
    }

    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in 1..n) {
        for (j in 1..m) {
            dp[i][j] = if (before[i - 1] == after[j - 1]) {
                dp[i - 1][j - 1] + 1
            } else {
                max(dp[i - 1][j], dp[i][j - 1])
            }
        }
    }

    val modified = mutableSetOf<Int>()
    var i = n
    var j = m
    while (j > 0) {
        if (i > 0 && before[i - 1] == after[j - 1]) {
            i--
            j--
        } else if (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) {
            modified.add(lo + j - 1)
            j--
        } else {
            i--
        }
    }
    return modified
}

@Composable
fun FileEditorPage(source: FileSource, path: String, name: String) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scroll = rememberScrollState()

    var text by remember(path) { mutableStateOf("") }
    var original by remember(path) { mutableStateOf("") }
    var loading by remember(path) { mutableStateOf(true) }
    var saving by remember(path) { mutableStateOf(false) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    // Preserved so saving a CRLF file does not rewrite every line ending.
    var crlf by remember(path) { mutableStateOf(false) }
    var savedLines by remember(path) { mutableStateOf<List<String>>(emptyList()) }

    val dirty = text != original
    val lines = remember(text) { text.split("\n") }
    val modifiedLines = remember(text, savedLines) { computeModifiedLines(savedLines, lines) }

    LaunchedEffect(path) {
        try {
            val attrs = source.stat(path)
            if ((attrs.size ?: 0) > kMaxEditableBytes) {
                throw IllegalStateException("File is too large to edit here")
            }
            val bytes = source.read(path)
            if (bytes.contains(0)) {
                throw IllegalStateException("This looks like a binary file")
            }
            var decoded = bytes.decodeToString()
            crlf = decoded.contains("\r\n")
            if (crlf) decoded = decoded.replace("\r\n", "\n")

            original = decoded
            text = decoded
            savedLines = decoded.split("\n")
            loading = false
        } catch (failure: Exception) {
            Log.error("editor", "open $path failed: $failure", failure)
            loading = false
            error = failure.message ?: "$failure"
        }
    }

    suspend fun confirmDiscard(): Boolean {
        if (!dirty) return true
        return confirmDestructive(
            title = "Discard changes?",
            message = "$name has unsaved edits.",
            actionLabel = "Discard",
        )
    }

    PlatformBackHandler(enabled = true) {
        scope.launch { if (confirmDiscard()) navigator.pop() }
    }

    QScaffold(
        appBar = {
            QPageAppBar(
                title = name,
                subtitle = if (dirty) "Modified" else path,
                statusColor = if (dirty) colors.warning else null,
                leading = null,
                actions = {
                    if (!loading && error == null) {
                        QPageAppBarAction(
                            tooltip = if (source.isLocal) "Save" else "Save to server",
                            icon = Icons.Outlined.CloudUpload,
                            iconSize = 21.dp,
                            native = true,
                            onPressed = if (dirty && !saving) {
                                {
                                    scope.launch {
                                        saving = true
                                        try {
                                            val payload = if (crlf) {
                                                text.replace("\n", "\r\n")
                                            } else {
                                                text
                                            }
                                            source.write(path, payload.encodeToByteArray())
                                            original = text
                                            savedLines = text.split("\n")
                                            AppToasts.show("Saved $name")
                                        } catch (failure: Exception) {
                                            Log.error("editor", "save $path failed", failure)
                                            AppToasts.show("Save failed: $failure")
                                        } finally {
                                            saving = false
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                },
            )
        },
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
            error != null -> QEmptyView(
                icon = Icons.Outlined.ErrorOutline,
                title = "Cannot edit this file",
                message = error!!,
            )
            else -> {
                val style = TextStyle(
                    color = colors.terminalForeground,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.4f,
                )
                val lineHeightPx = with(density) { (fontSize * 1.4f).toPx() }
                val gutterWidth = with(density) {
                    max(34f, lines.size.toString().length * 7.5f + 14f).toDp()
                }

                Row(Modifier.fillMaxSize().background(colors.terminalBackground)) {
                    Canvas(Modifier.width(gutterWidth).fillMaxHeight()) {
                        drawRect(colors.background, size = size)
                        drawRect(
                            colors.divider,
                            topLeft = Offset(size.width - 1, 0f),
                            size = Size(1f, size.height),
                        )

                        val modifiedTint = colors.warning.copy(alpha = 0.10f)
                        for (index in lines.indices) {
                            val top = 10f + index * lineHeightPx - scroll.value
                            if (top + lineHeightPx < 0 || top > size.height) continue

                            val isModified = modifiedLines.contains(index)
                            if (isModified) {
                                drawRect(
                                    modifiedTint,
                                    topLeft = Offset(2f, top),
                                    size = Size(size.width - 3, lineHeightPx),
                                )
                                drawRect(
                                    colors.warning,
                                    topLeft = Offset(0f, top),
                                    size = Size(2f, lineHeightPx),
                                )
                            }

                            val label = "${index + 1}"
                            val layout = measurer.measure(
                                label,
                                TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (isModified) colors.warning else colors.textMuted,
                                ),
                            )
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(
                                    size.width - layout.size.width - 7,
                                    top + (lineHeightPx - layout.size.height) / 2,
                                ),
                            )
                        }
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = style,
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(scroll)
                            .padding(start = 8.dp, top = 10.dp, end = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}
