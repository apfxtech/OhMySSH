package com.example.ohmyssh.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.platform.AppFiles
import com.example.ohmyssh.platform.FilePick

class ImportSummary(
    val hostsAdded: Int,
    val hostsUpdated: Int,
    val identitiesAdded: Int,
    val identitiesUpdated: Int,
    val groupsAdded: Int,
    val serialDevicesAdded: Int,
) {
    val total: Int
        get() = hostsAdded + hostsUpdated + identitiesAdded + identitiesUpdated +
            groupsAdded + serialDevicesAdded
}

object VaultStore {
    private var vault: Vault? = null

    var data: VaultData by mutableStateOf(VaultData())
        private set

    var isUnlocked: Boolean by mutableStateOf(false)
        private set

    val hosts: List<Host> get() = data.hosts
    val identities: List<Identity> get() = data.identities
    val groups: List<HostGroup> get() = data.groups
    val serialDevices: List<SerialDevice> get() = data.serialDevices

    fun vaultExists(): Boolean = Vault.exists()

    suspend fun create(password: String) {
        val created = Vault.create(password)
        vault = created
        data = VaultData()
        HistoryStore.open(created)
        isUnlocked = true
    }

    suspend fun unlock(password: String) {
        val opened = Vault.open(password)
        data = opened.read()
        vault = opened
        HistoryStore.open(opened)
        isUnlocked = true
    }

    fun lock() {
        vault = null
        data = VaultData()
        HistoryStore.close()
        isUnlocked = false
    }

    fun deleteVault() {
        val target = requireVault()
        // The history goes first, while its key still exists to name the file.
        HistoryStore.wipe()
        if (AppFiles.exists(target.path)) AppFiles.delete(target.path)
        lock()
    }

    suspend fun verifyPassword(password: String): Boolean = try {
        Vault.open(password, requireVault().path)
        true
    } catch (_: WrongPasswordException) {
        false
    }

    suspend fun changeMasterPassword(newPassword: String) {
        // The history is encrypted under the same password and has to travel
        // with it, or the new password would open the vault and nothing else.
        requireVault().changePassword(
            newPassword,
            sidecars = listOf(kHistoryFileName to kHistoryFormat),
        )
        if (AutoLogin.isEnabled()) AutoLogin.enable(newPassword)
    }

    fun identityFor(host: Host): Identity? {
        host.inlineIdentity?.let { return it }
        val id = host.identityId ?: return null
        return data.identities.firstOrNull { it.id == id }
    }

    fun identityById(id: String?): Identity? =
        id?.let { wanted -> data.identities.firstOrNull { it.id == wanted } }

    fun groupById(id: String?): HostGroup? =
        id?.let { wanted -> data.groups.firstOrNull { it.id == wanted } }

    fun hostsByGroup(): Map<HostGroup?, List<Host>> {
        val buckets = LinkedHashMap<HostGroup?, MutableList<Host>>()
        for (group in data.groups) buckets[group] = mutableListOf()
        val ungrouped = mutableListOf<Host>()
        for (host in data.hosts) {
            val group = groupById(host.groupId)
            if (group == null) ungrouped.add(host) else buckets.getValue(group).add(host)
        }
        buckets.entries.removeAll { it.value.isEmpty() }
        if (ungrouped.isNotEmpty()) buckets[null] = ungrouped
        return buckets
    }

    suspend fun saveHost(host: Host) = mutate {
        data = data.copy(hosts = upsert(data.hosts, host) { it.id })
    }

    suspend fun deleteHost(id: String) = mutate {
        data = data.copy(hosts = data.hosts.filter { it.id != id })
    }

    suspend fun saveIdentity(identity: Identity) = mutate {
        data = data.copy(identities = upsert(data.identities, identity) { it.id })
    }

    suspend fun deleteIdentity(id: String) = mutate {
        data = data.copy(
            hosts = data.hosts.map { if (it.identityId == id) it.copy(identityId = null) else it },
            identities = data.identities.filter { it.id != id },
        )
    }

    suspend fun saveGroup(group: HostGroup) = mutate {
        data = data.copy(groups = upsert(data.groups, group) { it.id })
    }

    suspend fun deleteGroup(id: String) = mutate {
        data = data.copy(
            hosts = data.hosts.map { if (it.groupId == id) it.copy(groupId = null) else it },
            groups = data.groups.filter { it.id != id },
        )
    }

    suspend fun saveSerialDevice(device: SerialDevice) = mutate {
        data = data.copy(serialDevices = upsert(data.serialDevices, device) { it.id })
    }

    suspend fun deleteSerialDevice(id: String) = mutate {
        data = data.copy(serialDevices = data.serialDevices.filter { it.id != id })
    }

    private fun <T> upsert(items: List<T>, item: T, idOf: (T) -> String): List<T> {
        val next = items.toMutableList()
        val index = next.indexOfFirst { idOf(it) == idOf(item) }
        if (index >= 0) next[index] = item else next.add(item)
        return next
    }

    suspend fun exportVault(fileName: String = "ohmyssh"): String? {
        val bytes = requireVault().readFileBytes()
        return FilePick.saveFile(name = fileName, extension = "vault", bytes = bytes)
    }

    suspend fun importVault(fileText: String, password: String): ImportSummary {
        val incoming = Vault.decryptData(fileText, password)

        var hostsAdded = 0
        var hostsUpdated = 0
        var identitiesAdded = 0
        var identitiesUpdated = 0
        var groupsAdded = 0
        var serialDevicesAdded = 0

        val hosts = data.hosts.toMutableList()
        for (host in incoming.hosts) {
            val index = hosts.indexOfFirst { it.id == host.id }
            if (index >= 0) {
                hosts[index] = host
                hostsUpdated++
            } else {
                hosts.add(host)
                hostsAdded++
            }
        }

        val identities = data.identities.toMutableList()
        for (identity in incoming.identities) {
            val index = identities.indexOfFirst { it.id == identity.id }
            if (index >= 0) {
                identities[index] = identity
                identitiesUpdated++
            } else {
                identities.add(identity)
                identitiesAdded++
            }
        }

        val groups = data.groups.toMutableList()
        for (group in incoming.groups) {
            val index = groups.indexOfFirst { it.id == group.id }
            if (index >= 0) groups[index] = group else {
                groups.add(group)
                groupsAdded++
            }
        }

        val serialDevices = data.serialDevices.toMutableList()
        for (device in incoming.serialDevices) {
            val index = serialDevices.indexOfFirst { it.id == device.id }
            if (index >= 0) serialDevices[index] = device else {
                serialDevices.add(device)
                serialDevicesAdded++
            }
        }

        mutate {
            data = VaultData(
                hosts = hosts,
                identities = identities,
                groups = groups,
                serialDevices = serialDevices,
            )
        }

        return ImportSummary(
            hostsAdded = hostsAdded,
            hostsUpdated = hostsUpdated,
            identitiesAdded = identitiesAdded,
            identitiesUpdated = identitiesUpdated,
            groupsAdded = groupsAdded,
            serialDevicesAdded = serialDevicesAdded,
        )
    }

    private fun requireVault(): Vault =
        vault ?: throw VaultException("Vault is locked")

    private suspend fun mutate(change: () -> Unit) {
        val target = requireVault()
        change()
        target.save(data)
    }
}
