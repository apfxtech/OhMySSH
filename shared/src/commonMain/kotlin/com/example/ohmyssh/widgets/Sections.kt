package com.example.ohmyssh.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.windowWidthFor

private val kSectionRailWidth = 212.dp
private val kFormMaxWidth = 560.dp

class EditorSection(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val summary: String,
    val problem: String? = null,
)

/**
 * One section of a page at a time, chosen from a rail beside it on a wide
 * window and from a tab row above it on a narrow one. A page with a single
 * section shows neither.
 */
@Composable
fun SectionedLayout(
    sections: List<EditorSection>,
    selected: String,
    onSelect: (String) -> Unit,
    content: @Composable ColumnScope.(sectionId: String) -> Unit,
) {
    val colors = appColors
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = !windowWidthFor(maxWidth).isCompact
        val current = sections.firstOrNull { it.id == selected } ?: sections.first()
        when {
            sections.size == 1 -> SectionBody(current, wide, heading = false, content)
            wide -> Row(Modifier.fillMaxSize()) {
                SectionRail(sections, current.id, onSelect)
                VerticalDivider(color = colors.divider)
                SectionBody(current, wide = true, heading = true, content)
            }
            else -> Column(Modifier.fillMaxSize()) {
                SectionTabs(sections, current.id, onSelect)
                HorizontalDivider(color = colors.divider)
                SectionBody(current, wide = false, heading = false, content)
            }
        }
    }
}

@Composable
private fun SectionBody(
    section: EditorSection,
    wide: Boolean,
    heading: Boolean,
    content: @Composable ColumnScope.(sectionId: String) -> Unit,
) {
    val colors = appColors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (wide) 24.dp else 14.dp,
                vertical = if (wide) 18.dp else 14.dp,
            ),
    ) {
        if (heading) {
            Text(
                section.title,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.W700,
                ),
            )
            Spacer(Modifier.height(14.dp))
        }
        Column(Modifier.widthIn(max = kFormMaxWidth).fillMaxWidth()) {
            content(section.id)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionRail(
    sections: List<EditorSection>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = appColors
    Column(
        Modifier
            .width(kSectionRailWidth)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (section in sections) {
            val active = section.id == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) colors.accent.copy(alpha = 0.14f) else colors.transparent)
                    .clickable { onSelect(section.id) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    section.icon,
                    contentDescription = null,
                    tint = if (active) colors.accent else colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        section.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.W600,
                        ),
                    )
                    Text(
                        section.problem ?: section.summary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = if (section.problem != null) colors.danger else colors.textMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 14.sp,
                        ),
                    )
                }
                if (section.problem != null) {
                    Spacer(Modifier.width(6.dp))
                    ProblemDot()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionTabs(
    sections: List<EditorSection>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = appColors
    val index = sections.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    SecondaryScrollableTabRow(
        selectedTabIndex = index,
        containerColor = colors.card,
        edgePadding = 6.dp,
        divider = {},
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(index),
                color = colors.accent,
            )
        },
    ) {
        for (section in sections) {
            Tab(
                selected = section.id == selected,
                onClick = { onSelect(section.id) },
                selectedContentColor = colors.textPrimary,
                unselectedContentColor = colors.textMuted,
                modifier = Modifier.height(42.dp),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(section.title, fontSize = 12.5.sp, fontWeight = FontWeight.W600)
                        if (section.problem != null) {
                            Spacer(Modifier.width(5.dp))
                            ProblemDot()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ProblemDot() {
    Box(Modifier.size(6.dp).clip(CircleShape).background(appColors.danger))
}
