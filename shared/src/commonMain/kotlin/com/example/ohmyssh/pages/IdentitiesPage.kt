package com.example.ohmyssh.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.ohmyssh.components.BrowseGrid
import com.example.ohmyssh.components.BrowseHeader
import com.example.ohmyssh.components.HeaderAction
import com.example.ohmyssh.components.ItemCard
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.data.AuthKind
import com.example.ohmyssh.data.Identity
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.data.newId
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.CredentialsEditor
import com.example.ohmyssh.widgets.EditorScaffold
import com.example.ohmyssh.widgets.EditorSection
import com.example.ohmyssh.widgets.FieldGap
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.QTextField
import com.example.ohmyssh.widgets.confirmDestructive
import com.example.ohmyssh.widgets.rememberCredentialsState
import kotlinx.coroutines.launch

private val kOneRowHeader = 720.dp

@Composable
fun IdentitiesPage() {
    val colors = appColors
    val navigator = LocalNavigator.current
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim().lowercase()
    val identities = VaultStore.identities.filter {
        needle.isEmpty() || it.label.lowercase().contains(needle) || it.username.lowercase().contains(needle)
    }

    QScaffold {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= kOneRowHeader
            Column(Modifier.fillMaxSize()) {
                BrowseHeader(
                    title = "Users",
                    wide = wide,
                    query = query,
                    onQueryChange = { query = it },
                    actions = {
                        HeaderAction(
                            label = "New user",
                            icon = Icons.Filled.Add,
                            wide = wide,
                            primary = true,
                            onClick = { navigator.push { IdentityEditorPage(null) } },
                        )
                    },
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        VaultStore.identities.isEmpty() -> QEmptyView(
                            icon = Icons.Outlined.Person,
                            title = "No users yet",
                            message = "A user holds a login and its password or private key. " +
                                "Systems point at one.",
                            action = {
                                Button(
                                    onClick = { navigator.push { IdentityEditorPage(null) } },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.onAccent,
                                    ),
                                ) { Text("Add user") }
                            },
                        )
                        identities.isEmpty() -> QEmptyView(
                            icon = Icons.Filled.Search,
                            title = "Nothing matches",
                            message = "No user matches “${query.trim()}”.",
                        )
                        else -> BrowseGrid(wide) {
                            items(identities, key = { it.id }) { identity ->
                                UserCard(identity) { navigator.push { IdentityEditorPage(identity) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(identity: Identity, onOpen: () -> Unit) {
    val colors = appColors
    val isKey = identity.kind == AuthKind.PRIVATE_KEY
    val usedBy = VaultStore.hosts.count { it.identityId == identity.id }

    ItemCard(
        onClick = onOpen,
        title = identity.label.ifEmpty { identity.username },
        subtitle = AnnotatedString("${identity.username} · ${if (isKey) "private key" else "password"}"),
        detail = AnnotatedString(
            when (usedBy) {
                0 -> "Not used by any system"
                1 -> "1 system"
                else -> "$usedBy systems"
            },
        ),
        leading = {
            QIconBadge(
                icon = if (isKey) Icons.Outlined.VpnKey else Icons.Filled.Password,
                color = if (isKey) colors.accent else colors.info,
                size = 34.dp,
                iconSize = 19.dp,
            )
        },
    )
}

@Composable
fun IdentityEditorPage(identity: Identity?) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val isNew = identity == null

    var label by rememberSaveable(identity?.id) { mutableStateOf(identity?.label ?: "") }
    val credentials = rememberCredentialsState(identity)
    val original = remember(identity?.id) { label to credentials.snapshot() }
    val dirty = (label to credentials.snapshot()) != original
    val usedBy = if (identity == null) 0 else VaultStore.hosts.count { it.identityId == identity.id }

    EditorScaffold(
        title = if (isNew) "New user" else label.trim().ifEmpty { identity!!.username },
        subtitle = when {
            isNew -> null
            usedBy == 0 -> identity!!.username
            usedBy == 1 -> "${identity!!.username} · 1 system"
            else -> "${identity!!.username} · $usedBy systems"
        },
        sections = listOf(EditorSection("credentials", "Credentials", Icons.Outlined.VpnKey, "")),
        selected = "credentials",
        onSelect = {},
        dirty = dirty,
        onSave = {
            val problem = credentials.validate()
            if (problem != null) {
                AppToasts.show(problem)
            } else {
                scope.launch {
                    val built = credentials.build(id = identity?.id ?: newId(), label = label)
                    VaultStore.saveIdentity(built)
                    navigator.pop(built)
                }
            }
        },
        onDelete = if (isNew) {
            null
        } else {
            {
                scope.launch {
                    val confirmed = confirmDestructive(
                        title = "Delete user?",
                        message = if (usedBy == 0) {
                            "${identity!!.label} will be removed from the vault."
                        } else {
                            "${identity!!.label} is used by $usedBy system" +
                                "${if (usedBy == 1) "" else "s"}. They will be left without a user."
                        },
                    )
                    if (confirmed) {
                        VaultStore.deleteIdentity(identity.id)
                        navigator.pop()
                    }
                }
            }
        },
    ) {
        QTextField(
            value = label,
            onValueChange = { label = it },
            label = "Name",
            hint = "Defaults to the username",
            autofocus = isNew,
        )
        FieldGap()
        CredentialsEditor(credentials)
    }
}
