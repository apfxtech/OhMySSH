package com.example.ohmyssh.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QPageAppBarAction
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.theme.appColors
import kotlinx.coroutines.launch

private val kSectionRailWidth = 212.dp
private val kFormMaxWidth = 560.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScaffold(
    title: String,
    subtitle: String?,
    sections: List<EditorSection>,
    selected: String,
    onSelect: (String) -> Unit,
    dirty: Boolean,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    deleteTooltip: String = "Delete",
    content: @Composable ColumnScope.(sectionId: String) -> Unit,
) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    fun leave() {
        scope.launch {
            if (!dirty || confirmDiscard()) navigator.pop()
        }
    }

    QScaffold(
        appBar = {
            QPageAppBar(
                title = title,
                subtitle = subtitle,
                leading = {
                    IconButton(onClick = ::leave) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (onDelete != null) {
                        QPageAppBarAction(
                            tooltip = deleteTooltip,
                            icon = Icons.Outlined.Delete,
                            iconSize = 19.dp,
                            onPressed = onDelete,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save", fontSize = 13.sp, fontWeight = FontWeight.W600)
                    }
                    Spacer(Modifier.width(10.dp))
                },
            )
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event -> editorShortcut(event, onSave, ::leave) },
        ) {
            SectionedLayout(sections, selected, onSelect, content)
        }
    }
}

private fun editorShortcut(event: KeyEvent, onSave: () -> Unit, onLeave: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when {
        event.key == Key.S && (event.isCtrlPressed || event.isMetaPressed) -> {
            onSave()
            true
        }
        event.key == Key.Escape -> {
            onLeave()
            true
        }
        else -> false
    }
}

suspend fun confirmDiscard(): Boolean = confirmDestructive(
    title = "Discard changes?",
    message = "What you changed here has not been saved.",
    actionLabel = "Discard",
)

@Composable
fun FieldRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

@Composable
fun FieldGap() {
    Spacer(Modifier.height(10.dp))
}

@Composable
fun FieldGroupTitle(text: String) {
    Text(
        text,
        style = TextStyle(
            color = appColors.textSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.W700,
        ),
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun appTextFieldColors(): TextFieldColors {
    val colors = appColors
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.card,
        unfocusedContainerColor = colors.card,
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.divider,
        focusedLabelColor = colors.textMuted,
        unfocusedLabelColor = colors.textMuted,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        cursorColor = colors.accent,
        focusedTrailingIconColor = colors.textMuted,
        unfocusedTrailingIconColor = colors.textMuted,
    )
}

class DropdownOption<T>(
    val value: T,
    val label: String,
    val detail: String? = null,
    val icon: ImageVector? = null,
)

class DropdownAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    value: T,
    options: List<DropdownOption<T>>,
    onSelect: (T) -> Unit,
    actions: List<DropdownAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == value }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = current?.label ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label, fontSize = 13.sp) },
            leadingIcon = current?.icon?.let {
                { Icon(it, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
            shape = RoundedCornerShape(12.dp),
            colors = appTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.dialogBackground,
        ) {
            for (option in options) {
                val active = option.value == value
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                option.label,
                                color = colors.dialogText,
                                fontSize = 13.5.sp,
                                fontWeight = if (active) FontWeight.W600 else FontWeight.W400,
                            )
                            if (option.detail != null) {
                                Text(option.detail, color = colors.dialogMuted, fontSize = 11.5.sp)
                            }
                        }
                    },
                    leadingIcon = option.icon?.let {
                        { Icon(it, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) }
                    },
                    trailingIcon = if (active) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    },
                )
            }
            if (actions.isNotEmpty()) {
                HorizontalDivider(color = colors.dialogDivider)
                for (action in actions) {
                    DropdownMenuItem(
                        text = { Text(action.label, color = colors.accent, fontSize = 13.5.sp, fontWeight = FontWeight.W600) },
                        leadingIcon = {
                            Icon(action.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            expanded = false
                            action.onClick()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun appSegmentedColors(): SegmentedButtonColors {
    val colors = appColors
    return SegmentedButtonDefaults.colors(
        activeContainerColor = colors.accent.copy(alpha = 0.18f),
        activeContentColor = colors.textPrimary,
        activeBorderColor = colors.divider,
        inactiveContainerColor = colors.card,
        inactiveContentColor = colors.textSecondary,
        inactiveBorderColor = colors.divider,
    )
}

class SegmentOption<T>(val value: T, val label: String, val icon: ImageVector? = null)

@Composable
fun <T> SegmentedChoice(
    options: List<SegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val segmentColors = appSegmentedColors()
    SingleChoiceSegmentedButtonRow(modifier.height(34.dp)) {
        options.forEachIndexed { index, option ->
            val active = option.value == selected
            SegmentedButton(
                selected = active,
                onClick = { onSelect(option.value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = segmentColors,
                icon = {
                    SegmentedButtonDefaults.Icon(active = active) {
                        if (option.icon != null) {
                            Icon(option.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                label = {
                    Text(
                        option.label,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.W600,
                    )
                },
            )
        }
    }
}

@Composable
fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = TextStyle(
                    color = if (enabled) colors.textPrimary else colors.textMuted,
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.W600,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = TextStyle(color = colors.textMuted, fontSize = 12.sp, lineHeight = 15.sp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
            ),
        )
    }
}

@Composable
fun InfoTable(rows: List<Pair<String, String>>) {
    val colors = appColors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        for ((label, value) in rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    label,
                    modifier = Modifier.width(100.dp),
                    style = TextStyle(color = colors.textMuted, fontSize = 12.sp),
                )
                SelectionContainer(Modifier.weight(1f)) {
                    Text(
                        value,
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
        }
    }
}
