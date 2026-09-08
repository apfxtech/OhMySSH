package com.example.ohmyssh.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.theme.appColors

private val kCardMinWidth = 264.dp
private val kControlHeight = 34.dp

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    val colors = appColors
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier
            .height(kControlHeight)
            .background(colors.card, shape)
            .border(1.dp, colors.divider, shape)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(placeholder, color = colors.textMuted, fontSize = 13.sp)
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = colors.textMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
fun HeaderAction(
    label: String,
    icon: ImageVector,
    wide: Boolean,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val colors = appColors
    if (!wide) {
        if (primary) {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(kControlHeight),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) { Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp)) }
        } else {
            IconButton(onClick = onClick, modifier = Modifier.size(kControlHeight)) {
                Icon(icon, contentDescription = label, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
            }
        }
        return
    }
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.W600, maxLines = 1, softWrap = false)
    }
    if (primary) {
        Button(
            onClick = onClick,
            modifier = Modifier.height(kControlHeight),
            contentPadding = PaddingValues(horizontal = 14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ),
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.height(kControlHeight),
            contentPadding = PaddingValues(horizontal = 14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
            content = content,
        )
    }
}

@Composable
fun BrowseHeader(
    title: String?,
    wide: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: (@Composable (wide: Boolean) -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = appColors
    if (wide) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (title != null) {
                Text(
                    title,
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W700,
                    ),
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            filter?.invoke(true)
            SearchField(query, onQueryChange, Modifier.weight(1f))
            actions()
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SearchField(query, onQueryChange, Modifier.weight(1f))
                actions()
            }
            if (filter != null) {
                Spacer(Modifier.height(8.dp))
                filter(false)
            }
        }
    }
}

@Composable
fun BrowseGrid(wide: Boolean, content: LazyGridScope.() -> Unit) {
    val edge = if (wide) 20.dp else 14.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(kCardMinWidth),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = edge, end = edge, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

fun LazyGridScope.gridSection(key: Any, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
fun GridSectionTitle(text: String, count: Int, onClick: (() -> Unit)? = null) {
    val colors = appColors
    Box(Modifier.padding(top = 6.dp)) {
        Row(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = TextStyle(
                    color = colors.textSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.W700,
                ),
            )
            Spacer(Modifier.width(6.dp))
            Text("$count", style = TextStyle(color = colors.textMuted, fontSize = 12.sp, lineHeight = 16.sp))
        }
    }
}

@Composable
fun ItemCard(
    onClick: () -> Unit,
    title: String,
    subtitle: AnnotatedString,
    detail: AnnotatedString,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    titleTrailing: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
) {
    val colors = appColors
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (selected) BorderStroke(1.dp, colors.accent) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.W600,
                        ),
                    )
                    if (titleTrailing != null) {
                        Spacer(Modifier.width(6.dp))
                        titleTrailing()
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = colors.textSecondary, fontSize = 12.sp, lineHeight = 15.sp),
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = colors.textMuted, fontSize = 11.5.sp, lineHeight = 14.sp),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(2.dp))
                trailing()
            }
        }
    }
}

@Composable
fun CardEditButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(
            Icons.Outlined.Edit,
            contentDescription = "Edit",
            tint = appColors.textMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}
