package com.example.ohmyssh.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.theme.appColors
import kotlin.math.max

val kTableRowHeight = 48.dp
val kTableHeaderHeight = 34.dp
val kTableFlexMinWidth = 140.dp

/**
 * A table column. [width] is the ceiling for a fixed column; 0 marks the single
 * flexible column that takes whatever is left. [hideLevel] orders the columns out
 * of the way as the window narrows — level 1 goes first, and null never goes.
 */
class QCol(
    val label: String,
    val width: Dp,
    val sortKey: String? = null,
    val right: Boolean = false,
    val hideLevel: Int? = null,
    val mono: Boolean = false,
)

class QSizedCol(val col: QCol, val width: Dp)

/**
 * Resolves which columns are visible at [available] width and how wide each one
 * gets: fixed columns shrink to the widest value actually in [rows] (capped by
 * their declared width) and the flexible column keeps the rest, dropping
 * [QCol.hideLevel] columns one level at a time until it clears its minimum.
 */
fun <T> layoutTableColumns(
    columns: List<QCol>,
    available: Dp,
    rows: List<T>,
    value: (QCol, T) -> String,
): List<QSizedCol> {
    val required = columns.filter { it.width > 0.dp }.associateWith { col ->
        val charWidth = if (col.mono) 6.9f else 7.2f
        var content = 0f
        for (row in rows) content = max(content, (value(col, row).length + 1) * charWidth)
        val label = col.label.length * 6.6f + 20f
        val needed = (max(content, label) + 8f).dp
        if (needed < col.width) needed else col.width
    }

    fun sized(visible: List<QCol>, flexible: Dp): List<QSizedCol> = visible.map { col ->
        QSizedCol(col, if (col.width == 0.dp) flexible else required.getValue(col))
    }

    fun fixedTotal(visible: List<QCol>): Dp =
        visible.filter { it.width > 0.dp }.fold(0.dp) { total, col -> total + required.getValue(col) }

    for (level in 0..3) {
        val visible = columns.filter { it.hideLevel == null || it.hideLevel > level }
        val flexible = available - fixedTotal(visible) - 16.dp
        if (flexible >= kTableFlexMinWidth) return sized(visible, flexible)
    }

    val core = columns.filter { it.hideLevel == null }
    return sized(core, (available - fixedTotal(core) - 16.dp).coerceAtLeast(kTableFlexMinWidth))
}

@Composable
fun QTableHeader(
    columns: List<QSizedCol>,
    sortKey: String?,
    sortAscending: Boolean,
    onSort: (String) -> Unit,
) {
    val colors = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .height(kTableHeaderHeight)
            .background(colors.card.copy(alpha = 0.7f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(8.dp))
        for (sized in columns) {
            val col = sized.col
            val active = col.sortKey != null && col.sortKey == sortKey
            Box(
                Modifier
                    .width(sized.width)
                    .height(kTableHeaderHeight)
                    .then(
                        if (col.sortKey == null) {
                            Modifier
                        } else {
                            Modifier.clickable { onSort(col.sortKey) }
                        },
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = if (col.right) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        col.label.uppercase(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = if (active) colors.textSecondary else colors.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.W600,
                            letterSpacing = 0.6.sp,
                        ),
                    )
                    if (active) {
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun QTableRow(
    columns: List<QSizedCol>,
    onTap: (() -> Unit)? = null,
    tint: Color? = null,
    cell: @Composable (QCol) -> Unit,
) {
    val colors = appColors
    Column(Modifier.fillMaxWidth().background(tint ?: colors.card)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(kTableRowHeight)
                .then(if (onTap == null) Modifier else Modifier.clickable(onClick = onTap)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(8.dp))
            for (sized in columns) {
                Box(
                    Modifier.width(sized.width).padding(horizontal = 4.dp),
                    contentAlignment = if (sized.col.right) {
                        Alignment.CenterEnd
                    } else {
                        Alignment.CenterStart
                    },
                ) {
                    cell(sized.col)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        HorizontalDivider(color = colors.divider.copy(alpha = 0.6f))
    }
}

@Composable
fun QTableCellText(
    text: String,
    right: Boolean = false,
    muted: Boolean = false,
    mono: Boolean = false,
) {
    val colors = appColors
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (right) TextAlign.End else TextAlign.Start,
        style = TextStyle(
            color = if (muted) colors.textMuted else colors.textSecondary,
            fontSize = if (mono) 11.sp else 12.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            letterSpacing = if (mono) 0.3.sp else 0.sp,
        ),
    )
}
