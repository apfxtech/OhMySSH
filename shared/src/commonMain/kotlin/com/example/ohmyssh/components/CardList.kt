package com.example.ohmyssh.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.theme.appColors
import kotlin.math.ceil
import kotlin.math.max

val kGroupedOuterRadius = 12.dp
val kGroupedInnerRadius = 4.dp
val kGroupedGap = 3.dp
val kGroupedHorizontalPadding = 14.dp
val kGroupedCardPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 8.dp, bottom = 6.dp)
val kGroupedGridMaxExtent = 104.dp
val kGroupedGridTileHeight = 104.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupedCard(
    shape: RoundedCornerShape,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    padding: PaddingValues = kGroupedCardPadding,
    background: (@Composable () -> Unit)? = null,
    contentAlignment: Alignment = Alignment.CenterStart,
    modifier: Modifier = Modifier,
    gestureModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = appColors
    Box(
        modifier
            .clip(shape)
            .background(colors.card)
            .let { base ->
                when {
                    // One gesture owner for the whole card: a detectTapGestures
                    // inside a row would consume the press and the card would
                    // stop reacting to taps entirely.
                    onTap != null && onLongPress != null -> base.combinedClickable(
                        onClick = onTap,
                        onLongClick = onLongPress,
                    )
                    onTap != null -> base.clickable(onClick = onTap)
                    onLongPress != null -> base.combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress,
                    )
                    else -> base
                }
            }
            .then(gestureModifier),
    ) {
        background?.invoke()
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = contentAlignment,
        ) {
            content()
        }
    }
}

@Composable
private fun GroupTitle(title: String?, header: (@Composable () -> Unit)?) {
    if (header != null) {
        header()
        return
    }
    if (title == null) return
    Text(
        title,
        style = TextStyle(
            fontSize = 12.5.sp,
            fontWeight = FontWeight.W600,
            color = appColors.textSecondary,
        ),
        modifier = Modifier.padding(start = 12.dp, top = 2.dp, end = 12.dp, bottom = 6.dp),
    )
}

fun groupedListShape(index: Int, count: Int): RoundedCornerShape = RoundedCornerShape(
    topStart = if (index == 0) kGroupedOuterRadius else kGroupedInnerRadius,
    topEnd = if (index == 0) kGroupedOuterRadius else kGroupedInnerRadius,
    bottomStart = if (index == count - 1) kGroupedOuterRadius else kGroupedInnerRadius,
    bottomEnd = if (index == count - 1) kGroupedOuterRadius else kGroupedInnerRadius,
)

fun groupedRowShape(index: Int, count: Int): RoundedCornerShape = RoundedCornerShape(
    topStart = if (index == 0) kGroupedOuterRadius else kGroupedInnerRadius,
    bottomStart = if (index == 0) kGroupedOuterRadius else kGroupedInnerRadius,
    topEnd = if (index == count - 1) kGroupedOuterRadius else kGroupedInnerRadius,
    bottomEnd = if (index == count - 1) kGroupedOuterRadius else kGroupedInnerRadius,
)

private fun listShape(index: Int, last: Int): RoundedCornerShape =
    groupedListShape(index, last + 1)

@Composable
fun <T> GroupedCardList(
    items: List<T>,
    title: String? = null,
    header: (@Composable () -> Unit)? = null,
    onTap: ((T) -> (() -> Unit)?)? = null,
    onLongPress: ((T) -> (() -> Unit)?)? = null,
    cardPadding: PaddingValues = kGroupedCardPadding,
    backgroundBuilder: ((T) -> (@Composable () -> Unit)?)? = null,
    cardGesture: (@Composable (T) -> Modifier)? = null,
    itemBuilder: @Composable (T) -> Unit,
) {
    val last = items.size - 1
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = kGroupedHorizontalPadding),
    ) {
        if (header != null || title != null) GroupTitle(title, header)
        items.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(kGroupedGap))
            GroupedCard(
                shape = listShape(index, last),
                onTap = onTap?.invoke(item),
                onLongPress = onLongPress?.invoke(item),
                padding = cardPadding,
                background = backgroundBuilder?.invoke(item),
                modifier = Modifier.fillMaxWidth(),
                gestureModifier = cardGesture?.invoke(item) ?: Modifier,
            ) {
                itemBuilder(item)
            }
        }
    }
}

@Composable
fun <T> GroupedCardGrid(
    items: List<T>,
    title: String? = null,
    header: (@Composable () -> Unit)? = null,
    onTap: ((T) -> (() -> Unit)?)? = null,
    backgroundBuilder: ((T) -> (@Composable () -> Unit)?)? = null,
    cardPadding: PaddingValues = kGroupedCardPadding,
    maxCrossAxisExtent: Dp = kGroupedGridMaxExtent,
    mainAxisExtent: Dp? = kGroupedGridTileHeight,
    crossAxisCount: Int? = null,
    spacing: Dp = kGroupedGap,
    itemBuilder: @Composable (T) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = kGroupedHorizontalPadding),
    ) {
        if (header != null || title != null) GroupTitle(title, header)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxWidth
            val columns = crossAxisCount
                ?: max(1, ceil((width / (maxCrossAxisExtent + spacing)).toDouble()).toInt())

            Column(Modifier.fillMaxWidth()) {
                var start = 0
                var rowIndex = 0
                while (start < items.size) {
                    if (rowIndex > 0) Spacer(Modifier.height(spacing))
                    val rowModifier =
                        if (mainAxisExtent == null) {
                            Modifier.fillMaxWidth().height(IntrinsicSize.Max)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                        for (c in 0 until columns) {
                            if (c > 0) Spacer(Modifier.width(spacing))
                            val index = start + c
                            Box(Modifier.weight(1f)) {
                                if (index < items.size) {
                                    val shape = gridShape(index, columns, items.size)
                                    val cellModifier = if (mainAxisExtent != null) {
                                        Modifier.fillMaxWidth().height(mainAxisExtent)
                                    } else {
                                        Modifier.fillMaxWidth()
                                    }
                                    GroupedCard(
                                        shape = shape,
                                        onTap = onTap?.invoke(items[index]),
                                        padding = cardPadding,
                                        background = backgroundBuilder?.invoke(items[index]),
                                        contentAlignment = if (mainAxisExtent == null) {
                                            Alignment.TopStart
                                        } else {
                                            Alignment.CenterStart
                                        },
                                        modifier = cellModifier,
                                    ) {
                                        itemBuilder(items[index])
                                    }
                                }
                            }
                        }
                    }
                    start += columns
                    rowIndex++
                }
            }
        }
    }
}

private fun gridShape(index: Int, columns: Int, total: Int): RoundedCornerShape {
    val col = index % columns
    val top = index - columns < 0
    val bottom = index + columns >= total
    val left = col == 0
    val right = col == columns - 1 || index + 1 >= total
    fun corner(a: Boolean, b: Boolean): Dp =
        if (a && b) kGroupedOuterRadius else kGroupedInnerRadius
    return RoundedCornerShape(
        topStart = corner(top, left),
        topEnd = corner(top, right),
        bottomStart = corner(bottom, left),
        bottomEnd = corner(bottom, right),
    )
}
