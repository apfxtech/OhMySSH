package com.example.ohmyssh.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.ai.AppTools
import com.example.ohmyssh.components.nameNetwork
import com.example.ohmyssh.components.networkIcon
import com.example.ohmyssh.components.networkLabel
import com.example.ohmyssh.data.AuthKind
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.HostGroup
import com.example.ohmyssh.data.Identity
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.data.newId
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.net.NetworkWatcher
import com.example.ohmyssh.net.networkKind
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.CredentialsEditor
import com.example.ohmyssh.widgets.DropdownAction
import com.example.ohmyssh.widgets.DropdownField
import com.example.ohmyssh.widgets.DropdownOption
import com.example.ohmyssh.widgets.EditorScaffold
import com.example.ohmyssh.widgets.EditorSection
import com.example.ohmyssh.widgets.FieldGap
import com.example.ohmyssh.widgets.FieldGroupTitle
import com.example.ohmyssh.widgets.FieldRow
import com.example.ohmyssh.widgets.QSecretText
import com.example.ohmyssh.widgets.QTextField
import com.example.ohmyssh.widgets.SegmentOption
import com.example.ohmyssh.widgets.SegmentedChoice
import com.example.ohmyssh.widgets.SwitchSetting
import com.example.ohmyssh.widgets.confirmDestructive
import com.example.ohmyssh.widgets.promptForText
import com.example.ohmyssh.widgets.rememberCredentialsState
import kotlinx.coroutines.launch

private const val CONNECTION = "connection"
private const val AUTH = "auth"
private const val NETWORKS = "networks"
private const val AGENT = "agent"
private const val HOST_KEY = "hostKey"

private enum class UserSource { SAVED, INLINE }

@Composable
fun HostEditorPage(host: Host?, draft: Host? = null) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val isNew = host == null
    val initial = host ?: draft

    val id = remember(host?.id) { host?.id ?: newId() }
    val inlineId = remember(host?.id) { host?.inlineIdentity?.id ?: newId() }

    var section by rememberSaveable(host?.id) { mutableStateOf(CONNECTION) }
    var label by rememberSaveable(host?.id) { mutableStateOf(initial?.label ?: "") }
    var hostname by rememberSaveable(host?.id) { mutableStateOf(initial?.hostname ?: "") }
    var port by rememberSaveable(host?.id) { mutableStateOf("${initial?.port ?: 22}") }
    var note by rememberSaveable(host?.id) { mutableStateOf(initial?.note ?: "") }
    var identityId by rememberSaveable(host?.id) { mutableStateOf(host?.identityId) }
    var groupId by rememberSaveable(host?.id) { mutableStateOf(host?.groupId) }
    var knownHostKey by rememberSaveable(host?.id) { mutableStateOf(host?.knownHostKey) }
    var agentEnabled by rememberSaveable(host?.id) {
        mutableStateOf(initial?.agentEnabled ?: false)
    }
    var agentMayAuthenticate by rememberSaveable(host?.id) {
        mutableStateOf(initial?.agentMayAuthenticate ?: false)
    }
    var source by rememberSaveable(host?.id) {
        mutableStateOf(
            when {
                host?.identityId != null -> UserSource.SAVED
                host != null -> UserSource.INLINE
                VaultStore.identities.isNotEmpty() -> UserSource.SAVED
                else -> UserSource.INLINE
            },
        )
    }
    val networks = remember(host?.id) {
        mutableStateListOf<String>().apply { addAll(initial?.networks ?: emptyList()) }
    }
    val credentials = rememberCredentialsState(host?.inlineIdentity)

    LaunchedEffect(Unit) { NetworkWatcher.refresh() }

    val parsedPort = port.trim().toIntOrNull()
    val savedIdentity = if (source == UserSource.SAVED) VaultStore.identityById(identityId) else null

    fun assemble(): Host = Host(
        id = id,
        label = label.trim(),
        hostname = hostname.trim(),
        port = parsedPort ?: 22,
        identityId = if (source == UserSource.SAVED) identityId else null,
        inlineIdentity = if (source == UserSource.INLINE && !credentials.isEmpty) {
            credentials.build(id = inlineId)
        } else {
            null
        },
        groupId = groupId,
        note = note.trim().ifEmpty { null },
        knownHostKey = knownHostKey,
        osId = host?.osId,
        osPretty = host?.osPretty,
        networks = networks.toList(),
        agentEnabled = agentEnabled,
        agentMayAuthenticate = agentEnabled && agentMayAuthenticate,
    )

    val original = remember(host?.id) { initial ?: Host(id = id, label = "", hostname = "") }
    val dirty = assemble() != original

    fun problemIn(sectionId: String): String? = when (sectionId) {
        CONNECTION -> when {
            hostname.isBlank() -> "Hostname or IP is required"
            parsedPort == null || parsedPort !in 1..65535 -> "Port must be 1–65535"
            else -> null
        }
        AUTH -> when {
            source == UserSource.SAVED && identityId != null && savedIdentity == null ->
                "That user no longer exists"
            source == UserSource.INLINE && !credentials.isEmpty -> credentials.validate()
            else -> null
        }
        else -> null
    }

    val endpoint = if (parsedPort == null || parsedPort == 22) hostname.trim() else "${hostname.trim()}:$parsedPort"
    val userLabel = when (source) {
        UserSource.SAVED -> savedIdentity?.username
        UserSource.INLINE -> credentials.username.trim().ifEmpty { null }
    }
    val authSummary = when {
        source == UserSource.SAVED && savedIdentity != null ->
            "${savedIdentity.label} · ${authWord(savedIdentity.kind)}"
        source == UserSource.SAVED -> "No user chosen"
        credentials.isEmpty -> "No login"
        else -> "${credentials.username.trim()} · ${authWord(credentials.kind)}"
    }

    val sections = listOf(
        EditorSection(
            CONNECTION,
            "Connection",
            Icons.Outlined.Dns,
            summary = endpoint.ifEmpty { "No address" },
            problem = problemIn(CONNECTION),
        ),
        EditorSection(AUTH, "Authentication", Icons.Outlined.Key, authSummary, problemIn(AUTH)),
        EditorSection(
            NETWORKS,
            "Networks",
            Icons.Outlined.Lan,
            summary = when (networks.size) {
                0 -> "None recorded"
                1 -> networkLabel(networks[0])
                else -> "${networks.size} networks"
            },
        ),
        EditorSection(
            AGENT,
            "Agent",
            Icons.Outlined.SmartToy,
            summary = when {
                !agentEnabled -> "No access"
                agentMayAuthenticate -> "${AppTools.specs.size} tools, may request the password"
                else -> "${AppTools.specs.size - 1} tools"
            },
        ),
        EditorSection(
            HOST_KEY,
            "Host key",
            Icons.Outlined.Fingerprint,
            summary = if (knownHostKey == null) "Not pinned" else "Pinned",
        ),
    )

    fun save() {
        for (candidate in sections) {
            val problem = problemIn(candidate.id) ?: continue
            section = candidate.id
            AppToasts.show(problem)
            return
        }
        scope.launch {
            VaultStore.saveHost(assemble())
            navigator.pop()
        }
    }

    EditorScaffold(
        title = if (isNew) "New system" else label.trim().ifEmpty { hostname.trim() }.ifEmpty { "System" },
        subtitle = when {
            endpoint.isEmpty() -> null
            userLabel == null -> endpoint
            else -> "$userLabel@$endpoint"
        },
        sections = sections,
        selected = section,
        onSelect = { section = it },
        dirty = dirty,
        onSave = ::save,
        onDelete = if (isNew) {
            null
        } else {
            {
                scope.launch {
                    val confirmed = confirmDestructive(
                        title = "Delete system?",
                        message = "${host.displayLabel} will be removed from the vault.",
                    )
                    if (confirmed) {
                        VaultStore.deleteHost(host.id)
                        navigator.pop()
                    }
                }
            }
        },
    ) { current ->
        when (current) {
            CONNECTION -> {
                QTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = "Name",
                    hint = "Shown on the card",
                    autofocus = isNew,
                )
                FieldGap()
                FieldRow {
                    QTextField(
                        value = hostname,
                        onValueChange = { hostname = it },
                        label = "Hostname or IP",
                        hint = "10.0.0.5 or box.local",
                        modifier = Modifier.weight(3f),
                    )
                    QTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = "Port",
                        digitsOnly = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                FieldGap()
                DropdownField(
                    label = "Group",
                    value = groupId,
                    options = buildList {
                        add(DropdownOption(null, "Ungrouped", icon = Icons.Filled.Block))
                        for (group in VaultStore.groups) {
                            add(DropdownOption(group.id, group.name, icon = Icons.Outlined.Folder))
                        }
                    },
                    onSelect = { groupId = it },
                    actions = listOf(
                        DropdownAction("New group…", Icons.Outlined.CreateNewFolder) {
                            scope.launch {
                                val name = promptForText(
                                    title = "New group",
                                    label = "Group name",
                                    actionLabel = "Create",
                                )
                                if (name.isNullOrEmpty()) return@launch
                                val group = HostGroup(id = newId(), name = name)
                                VaultStore.saveGroup(group)
                                groupId = group.id
                            }
                        },
                    ),
                )
                FieldGap()
                QTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Notes",
                    maxLines = 4,
                )
            }

            AUTH -> {
                SegmentedChoice(
                    options = listOf(
                        SegmentOption(UserSource.SAVED, "Saved user"),
                        SegmentOption(UserSource.INLINE, "Login on this system"),
                    ),
                    selected = source,
                    onSelect = { source = it },
                )
                FieldGap()
                if (source == UserSource.SAVED) {
                    SavedUserPicker(
                        identityId = identityId,
                        onPick = { identityId = it },
                        onCreate = {
                            scope.launch {
                                val created = navigator.pushForResult<Identity> {
                                    IdentityEditorPage(null)
                                }
                                if (created != null) identityId = created.id
                            }
                        },
                    )
                } else {
                    CredentialsEditor(credentials, autofocusUsername = isNew)
                    Spacer(Modifier.height(4.dp))
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
                                source = UserSource.SAVED
                                AppToasts.show("${identity.label} added to Users")
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
                    ) {
                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Also save to Users", fontSize = 13.sp)
                    }
                }
            }

            NETWORKS -> NetworksSection(
                networks = networks,
                onRename = { key -> scope.launch { nameNetwork(key) } },
                onRemove = { networks.remove(it) },
                onAdd = { key ->
                    networks.add(0, key)
                    scope.launch { NetworkWatcher.currentTag() }
                },
            )

            AGENT -> {
                SwitchSetting(
                    title = "Allow agent access",
                    description = "An AI agent may open this system and run commands on it",
                    checked = agentEnabled,
                    onChange = {
                        agentEnabled = it
                        if (!it) agentMayAuthenticate = false
                    },
                )
                SwitchSetting(
                    title = "Agent may request the password",
                    description = "The agent can ask the app to type this login's password at a " +
                        "sudo prompt. It never receives or sees the password itself.",
                    checked = agentMayAuthenticate,
                    enabled = agentEnabled,
                    onChange = { agentMayAuthenticate = it },
                )
                FieldGroupTitle("Tools")
                AgentToolList(enabled = agentEnabled, mayAuthenticate = agentMayAuthenticate)
            }

            HOST_KEY -> HostKeySection(
                fingerprint = knownHostKey,
                onForget = { knownHostKey = null },
            )
        }
    }
}

private fun authWord(kind: AuthKind): String =
    if (kind == AuthKind.PRIVATE_KEY) "private key" else "password"

@Composable
private fun SavedUserPicker(
    identityId: String?,
    onPick: (String?) -> Unit,
    onCreate: () -> Unit,
) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val identities = VaultStore.identities

    if (identities.isEmpty()) {
        Button(
            onClick = onCreate,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ),
        ) {
            Icon(Icons.Filled.PersonAddAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("New user", fontSize = 13.sp)
        }
        return
    }

    DropdownField(
        label = "User",
        value = identityId,
        options = buildList {
            add(DropdownOption<String?>(null, "Choose a user"))
            for (identity in identities) {
                add(
                    DropdownOption(
                        identity.id,
                        identity.label,
                        detail = "${identity.username} · ${authWord(identity.kind)}",
                        icon = if (identity.kind == AuthKind.PRIVATE_KEY) {
                            Icons.Outlined.VpnKey
                        } else {
                            Icons.Filled.Password
                        },
                    ),
                )
            }
        },
        onSelect = onPick,
        actions = listOf(DropdownAction("New user…", Icons.Filled.PersonAddAlt, onCreate)),
    )
    val chosen = VaultStore.identityById(identityId)
    if (chosen != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(
                onClick = { navigator.push { IdentityEditorPage(chosen) } },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit ${chosen.label}", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AgentToolList(enabled: Boolean, mayAuthenticate: Boolean) {
    val colors = appColors
    Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(10.dp))) {
        AppTools.specs.forEachIndexed { index, spec ->
            val available = enabled && (spec.name != "send_password" || mayAuthenticate)
            if (index > 0) HorizontalDivider(color = colors.divider)
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        spec.name,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(
                            color = if (available) colors.textPrimary else colors.textMuted,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.W600,
                        ),
                    )
                    if (!available) {
                        Text("off", style = TextStyle(color = colors.textMuted, fontSize = 11.5.sp))
                    }
                }
                Text(
                    spec.description,
                    style = TextStyle(color = colors.textMuted, fontSize = 12.sp, lineHeight = 15.sp),
                )
            }
        }
    }
}

@Composable
private fun NetworksSection(
    networks: List<String>,
    onRename: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    val colors = appColors
    val current = NetworkWatcher.current

    if (networks.isEmpty() && current == null) {
        Text(
            "Recorded on every connect",
            style = TextStyle(color = colors.textMuted, fontSize = 13.sp),
        )
        return
    }
    if (networks.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(10.dp))) {
            for (key in networks) {
                val here = current?.key == key
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onRename(key) }
                        .padding(start = 12.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        networkIcon(networkKind(key)),
                        contentDescription = null,
                        tint = if (here) colors.accent else colors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        networkLabel(key),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(
                            color = if (here) colors.accent else colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = if (here) FontWeight.W600 else FontWeight.W500,
                        ),
                    )
                    IconButton(onClick = { onRemove(key) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove ${networkLabel(key)}",
                            tint = colors.textMuted,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
    if (current != null && networks.none { it == current.key }) {
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { onAdd(current.key) },
            colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
        ) {
            Icon(Icons.Outlined.AddLink, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add ${networkLabel(current.key)}", fontSize = 13.sp)
        }
    }
}

@Composable
private fun HostKeySection(fingerprint: String?, onForget: () -> Unit) {
    val colors = appColors
    val clipboard = LocalClipboardManager.current

    if (fingerprint == null) {
        Text(
            "Pinned on the first connect",
            style = TextStyle(color = colors.textMuted, fontSize = 13.sp),
        )
        return
    }
    QSecretText(
        fingerprint,
        style = TextStyle(
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(10.dp))
            .padding(12.dp),
    )
    Spacer(Modifier.height(2.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(fingerprint))
                AppToasts.show("Host key copied")
            },
            contentPadding = PaddingValues(horizontal = 10.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
        ) { Text("Copy", fontSize = 13.sp) }
        TextButton(
            onClick = onForget,
            contentPadding = PaddingValues(horizontal = 10.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.danger),
        ) { Text("Forget", fontSize = 13.sp) }
    }
}
