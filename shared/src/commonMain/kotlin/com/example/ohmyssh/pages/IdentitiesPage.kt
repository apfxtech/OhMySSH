package com.example.ohmyssh.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohmyssh.components.GroupedCardList
import com.example.ohmyssh.components.QIconBadge
import com.example.ohmyssh.components.QFloatingAction
import com.example.ohmyssh.components.QPageAppBar
import com.example.ohmyssh.components.QPageAppBarAction
import com.example.ohmyssh.components.QScaffold
import com.example.ohmyssh.data.AuthKind
import com.example.ohmyssh.data.Identity
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.data.newId
import com.example.ohmyssh.navigation.LocalNavigator
import com.example.ohmyssh.theme.appColors
import com.example.ohmyssh.ui.AppToasts
import com.example.ohmyssh.widgets.CredentialsEditor
import com.example.ohmyssh.widgets.QEmptyView
import com.example.ohmyssh.widgets.QFormLabel
import com.example.ohmyssh.widgets.QTextField
import com.example.ohmyssh.widgets.confirmDestructive
import com.example.ohmyssh.widgets.rememberCredentialsState
import kotlinx.coroutines.launch

@Composable
fun IdentitiesPage() {
    val colors = appColors
    val navigator = LocalNavigator.current
    val identities = VaultStore.identities

    QScaffold(
        floatingActions = {
            QFloatingAction(
                tooltip = "New user",
                icon = Icons.Filled.Add,
                onPressed = { navigator.push { IdentityEditorPage(null) } },
                primary = true,
            )
        },
    ) {
        if (identities.isEmpty()) {
            QEmptyView(
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
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 14.dp, bottom = 20.dp),
            ) {
                GroupedCardList(
                    items = identities,
                    onTap = { identity -> { navigator.push { IdentityEditorPage(identity) } } },
                    itemBuilder = { identity -> IdentityRow(identity) },
                )
            }
        }
    }
}

@Composable
private fun IdentityRow(identity: Identity) {
    val colors = appColors
    val isKey = identity.kind == AuthKind.PRIVATE_KEY
    val usedBy = VaultStore.hosts.count { it.identityId == identity.id }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        QIconBadge(
            icon = if (isKey) Icons.Outlined.VpnKey else Icons.Filled.Password,
            color = if (isKey) colors.accent else colors.info,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                identity.label.ifEmpty { identity.username },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.5.sp,
                    lineHeight = 17.4.sp,
                    fontWeight = FontWeight.W600,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${identity.username} · ${if (isKey) "private key" else "password"}" +
                    if (usedBy == 0) {
                        ""
                    } else {
                        " · $usedBy system${if (usedBy == 1) "" else "s"}"
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textMuted, fontSize = 12.sp, lineHeight = 14.4.sp),
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
fun IdentityEditorPage(identity: Identity?) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val isNew = identity == null

    var label by rememberSaveable(identity?.id) { mutableStateOf(identity?.label ?: "") }
    val credentials = rememberCredentialsState(identity)

    QScaffold(
        appBar = {
            QPageAppBar(
                title = if (isNew) "New user" else "Edit user",
                subtitle = if (isNew) null else identity!!.username,
                actions = {
                    if (!isNew) {
                        QPageAppBarAction(
                            tooltip = "Delete",
                            icon = Icons.Outlined.Delete,
                            onPressed = {
                                scope.launch {
                                    val usedBy =
                                        VaultStore.hosts.count { it.identityId == identity!!.id }
                                    val confirmed = confirmDestructive(
                                        title = "Delete user?",
                                        message = if (usedBy == 0) {
                                            "${identity!!.label} will be removed from the vault."
                                        } else {
                                            "${identity!!.label} is used by $usedBy system" +
                                                "${if (usedBy == 1) "" else "s"}. They will be " +
                                                "left without a user."
                                        },
                                    )
                                    if (confirmed) {
                                        VaultStore.deleteIdentity(identity.id)
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
                        onPressed = {
                            scope.launch {
                                val problem = credentials.validate()
                                if (problem != null) {
                                    AppToasts.show(problem)
                                    return@launch
                                }
                                val built = credentials.build(
                                    id = identity?.id ?: newId(),
                                    label = label,
                                )
                                VaultStore.saveIdentity(built)
                                navigator.pop(built)
                            }
                        },
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
            QFormLabel("Identity")
            QTextField(
                value = label,
                onValueChange = { label = it },
                label = "Name",
                hint = "Defaults to the username",
                autofocus = isNew,
            )
            Spacer(Modifier.height(12.dp))
            CredentialsEditor(credentials)
        }
    }
}
