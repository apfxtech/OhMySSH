package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QPageAppBarAction
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.data.AuthKind
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.HostGroup
import com.example.ohmyssh.data.Identity
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.data.newId
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.CredentialsEditor
import com.example.ohmyssh.widgets.PickOption
import com.example.ohmyssh.widgets.QFormLabel
import com.example.ohmyssh.widgets.QTextField
import com.example.ohmyssh.widgets.confirmDestructive
import com.example.ohmyssh.widgets.pickFromList
import com.example.ohmyssh.widgets.promptForText
import com.example.ohmyssh.widgets.rememberCredentialsState
import kotlinx.coroutines.launch

private const val CREATE_SENTINEL = " new"

/**
 * Edits [host], or creates a system when it is null. [draft] pre-fills that new
 * system without saving anything — the network scan hands over a discovered
 * address this way, and the user still has to press save.
 */
@Composable
fun HostEditorPage(host: Host?, draft: Host? = null) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val isNew = host == null
    val initial = host ?: draft

    var label by rememberSaveable(host?.id) { mutableStateOf(initial?.label ?: "") }
    var hostname by rememberSaveable(host?.id) { mutableStateOf(initial?.hostname ?: "") }
    var port by rememberSaveable(host?.id) { mutableStateOf("${initial?.port ?: 22}") }
    var note by rememberSaveable(host?.id) { mutableStateOf(initial?.note ?: "") }
    var identityId by rememberSaveable(host?.id) { mutableStateOf(host?.identityId) }
    var groupId by rememberSaveable(host?.id) { mutableStateOf(host?.groupId) }
    var knownHostKey by rememberSaveable(host?.id) { mutableStateOf(host?.knownHostKey) }
    val credentials = rememberCredentialsState(host?.inlineIdentity)

    suspend fun save() {
        val trimmedHost = hostname.trim()
        if (trimmedHost.isEmpty()) {
            AppToasts.show("Hostname or IP is required")
            return
        }
        val parsedPort = port.trim().toIntOrNull() ?: 22
        if (parsedPort < 1 || parsedPort > 65535) {
            AppToasts.show("Port must be 1–65535")
            return
        }

        var inline: Identity? = null
        if (identityId == null && !credentials.isEmpty) {
            val problem = credentials.validate()
            if (problem != null) {
                AppToasts.show(problem)
                return
            }
            inline = credentials.build(id = host?.inlineIdentity?.id ?: newId())
        }

        VaultStore.saveHost(
            Host(
                id = host?.id ?: newId(),
                label = label.trim(),
                hostname = trimmedHost,
                port = parsedPort,
                identityId = identityId,
                inlineIdentity = inline,
                groupId = groupId,
                note = note.trim().ifEmpty { null },
                knownHostKey = knownHostKey,
                osId = host?.osId,
                osPretty = host?.osPretty,
            ),
        )
        navigator.pop()
    }

    QScaffold(
        appBar = {
            QPageAppBar(
                title = if (isNew) "New system" else "Edit system",
                subtitle = if (isNew) null else host!!.endpoint,
                actions = {
                    if (!isNew) {
                        QPageAppBarAction(
                            tooltip = "Delete",
                            icon = Icons.Outlined.Delete,
                            onPressed = {
                                scope.launch {
                                    val confirmed = confirmDestructive(
                                        title = "Delete system?",
                                        message = "${host!!.displayLabel} will be removed from " +
                                            "the vault.",
                                    )
                                    if (confirmed) {
                                        VaultStore.deleteHost(host.id)
                                        navigator.pop()
                                    }
                                }
                            },
                        )
                    }
                    QPageAppBarAction(
                        tooltip = "Save",
                        icon = Icons.Filled.Check,
                        iconSize = 22.dp,
                        onPressed = { scope.launch { save() } },
                    )
                },
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, top = 6.dp, end = 14.dp, bottom = 28.dp),
        ) {
            QFormLabel("Connection")
            QTextField(
                value = label,
                onValueChange = { label = it },
                label = "Name",
                hint = "Shown on the card",
                autofocus = isNew,
            )
            Spacer(Modifier.height(10.dp))
            QTextField(
                value = hostname,
                onValueChange = { hostname = it },
                label = "Hostname or IP",
                hint = "10.0.0.5 or box.local",
            )
            Spacer(Modifier.height(10.dp))
            QTextField(
                value = port,
                onValueChange = { port = it },
                label = "Port",
                digitsOnly = true,
            )

            QFormLabel("User")
            PickerCard(
                icon = Icons.Outlined.Person,
                title = identityLabel(identityId),
                subtitle = when {
                    identityId != null -> "Tap to change, or clear it to type a login here"
                    VaultStore.identities.isEmpty() ->
                        "No saved users yet — type a login below, or tap to create one"
                    else -> "Tap to reuse a saved user, or type a login below"
                },
                onTap = {
                    scope.launch {
                        val selected = pickFromList(
                            title = "Assign user",
                            current = identityId,
                            options = buildList {
                                add(
                                    PickOption(
                                        CREATE_SENTINEL,
                                        "New user…",
                                        icon = Icons.Filled.PersonAddAlt,
                                        isAction = true,
                                    ),
                                )
                                add(
                                    PickOption(
                                        null,
                                        "No saved user",
                                        subtitle = "Type the login on this system",
                                        icon = Icons.Outlined.Edit,
                                    ),
                                )
                                for (identity in VaultStore.identities) {
                                    add(
                                        PickOption(
                                            identity.id,
                                            identity.label,
                                            subtitle = "${identity.username} · " +
                                                if (identity.kind == AuthKind.PRIVATE_KEY) {
                                                    "private key"
                                                } else {
                                                    "password"
                                                },
                                            icon = if (identity.kind == AuthKind.PRIVATE_KEY) {
                                                Icons.Outlined.VpnKey
                                            } else {
                                                Icons.Filled.Password
                                            },
                                        ),
                                    )
                                }
                            },
                        ) ?: return@launch

                        if (selected.value == CREATE_SENTINEL) {
                            val created = navigator.pushForResult<Identity> {
                                IdentityEditorPage(null)
                            }
                            if (created != null) identityId = created.id
                            return@launch
                        }
                        identityId = selected.value
                    }
                },
            )
            if (identityId == null) {
                Spacer(Modifier.height(12.dp))
                CredentialsEditor(credentials)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val problem = credentials.validate()
                                if (problem != null) {
                                    AppToasts.show(problem)
                                    return@launch
                                }
                                val name = promptForText(
                                    title = "Save as a reusable user",
                                    label = "Name",
                                    initial = credentials.username.trim(),
                                    actionLabel = "Save",
                                ) ?: return@launch

                                val identity = credentials.build(id = newId(), label = name)
                                VaultStore.saveIdentity(identity)
                                identityId = identity.id
                                AppToasts.show("${identity.label} added to Users")
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
                    ) {
                        Icon(
                            Icons.Outlined.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Also save to Users")
                    }
                }
            }

            QFormLabel("Group")
            PickerCard(
                icon = Icons.Outlined.Folder,
                title = VaultStore.groupById(groupId)?.name ?: "Ungrouped",
                subtitle = "Groups become sections on the systems list",
                onTap = {
                    scope.launch {
                        val selected = pickFromList(
                            title = "Assign group",
                            current = groupId,
                            options = buildList {
                                add(
                                    PickOption(
                                        CREATE_SENTINEL,
                                        "New group…",
                                        icon = Icons.Outlined.CreateNewFolder,
                                        isAction = true,
                                    ),
                                )
                                add(PickOption(null, "Ungrouped", icon = Icons.Filled.Block))
                                for (group in VaultStore.groups) {
                                    add(
                                        PickOption(
                                            group.id,
                                            group.name,
                                            icon = Icons.Outlined.Folder,
                                        ),
                                    )
                                }
                            },
                        ) ?: return@launch

                        if (selected.value == CREATE_SENTINEL) {
                            val name = promptForText(
                                title = "New group",
                                label = "Group name",
                                actionLabel = "Create",
                            )
                            if (name.isNullOrEmpty()) return@launch
                            val group = HostGroup(id = newId(), name = name)
                            VaultStore.saveGroup(group)
                            groupId = group.id
                            return@launch
                        }
                        groupId = selected.value
                    }
                },
            )

            QFormLabel("Notes")
            QTextField(
                value = note,
                onValueChange = { note = it },
                label = "Notes",
                maxLines = 3,
            )

            val pinned = knownHostKey
            if (pinned != null) {
                QFormLabel("Host key")
                HostKeyCard(fingerprint = pinned, onForget = { knownHostKey = null })
            }
        }
    }
}

private fun identityLabel(identityId: String?): String {
    if (identityId == null) return "No saved user"
    val identity = VaultStore.identityById(identityId) ?: return "Missing user"
    return "${identity.label} (${identity.username})"
}

@Composable
internal fun PickerCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onTap: () -> Unit,
) {
    val colors = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(start = 12.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QIconBadge(icon = icon, color = colors.info)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = TextStyle(color = colors.textMuted, fontSize = 12.sp))
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun HostKeyCard(fingerprint: String, onForget: () -> Unit) {
    val colors = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Pinned on first connect",
                style = TextStyle(color = colors.textMuted, fontSize = 12.sp),
            )
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    fingerprint,
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
        TextButton(
            onClick = onForget,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.danger),
        ) { Text("Forget") }
    }
}
