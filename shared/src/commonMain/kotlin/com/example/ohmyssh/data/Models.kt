package com.example.ohmyssh.data

import com.example.ohmyssh.platform.epochMicros
import com.example.ohmyssh.serial.formatUsbId
import com.example.ohmyssh.serial.serialPortName
import kotlin.random.Random
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

fun newId(): String {
    val stamp = epochMicros().toString(36)
    val salt = Random.nextLong(1L shl 32).toString(36).padStart(7, '0')
    return "$stamp$salt"
}

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

enum class AuthKind {
    PASSWORD,
    PRIVATE_KEY;

    val wireName: String
        get() = if (this == PASSWORD) "password" else "privateKey"

    companion object {
        fun parse(raw: String?): AuthKind =
            entries.firstOrNull { it.wireName == raw } ?: PASSWORD
    }
}

data class Identity(
    val id: String,
    val label: String,
    val username: String,
    val kind: AuthKind = AuthKind.PASSWORD,
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("label", label)
        put("username", username)
        put("kind", kind.wireName)
        password?.let { put("password", it) }
        privateKey?.let { put("privateKey", it) }
        passphrase?.let { put("passphrase", it) }
    }

    companion object {
        fun fromJson(json: JsonObject): Identity = Identity(
            id = json.str("id") ?: newId(),
            label = json.str("label") ?: "",
            username = json.str("username") ?: "",
            kind = AuthKind.parse(json.str("kind")),
            password = json.str("password"),
            privateKey = json.str("privateKey"),
            passphrase = json.str("passphrase"),
        )
    }
}

data class HostGroup(val id: String, val name: String) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
    }

    companion object {
        fun fromJson(json: JsonObject): HostGroup = HostGroup(
            id = json.str("id") ?: newId(),
            name = json.str("name") ?: "Group",
        )
    }
}

data class Host(
    val id: String,
    val label: String,
    val hostname: String,
    val port: Int = 22,
    val identityId: String? = null,
    val inlineIdentity: Identity? = null,
    val groupId: String? = null,
    val note: String? = null,
    /**
     * TOFU pin: OpenSSH-style SHA256 fingerprint seen on first connect. A
     * mismatch on a later connect is a hard stop, not a warning.
     */
    val knownHostKey: String? = null,
    val osId: String? = null,
    val osPretty: String? = null,
) {
    val hasInlineIdentity: Boolean get() = inlineIdentity != null

    val displayLabel: String get() = label.ifEmpty { hostname }

    val endpoint: String get() = if (port == 22) hostname else "$hostname:$port"

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("label", label)
        put("hostname", hostname)
        put("port", port)
        identityId?.let { put("identityId", it) }
        inlineIdentity?.let { put("inlineIdentity", it.toJson()) }
        groupId?.let { put("groupId", it) }
        note?.let { put("note", it) }
        knownHostKey?.let { put("knownHostKey", it) }
        osId?.let { put("osId", it) }
        osPretty?.let { put("osPretty", it) }
    }

    companion object {
        fun fromJson(json: JsonObject): Host = Host(
            id = json.str("id") ?: newId(),
            label = json.str("label") ?: "",
            hostname = json.str("hostname") ?: "",
            port = json.int("port") ?: 22,
            identityId = json.str("identityId"),
            inlineIdentity = json.obj("inlineIdentity")?.let(Identity::fromJson),
            groupId = json.str("groupId"),
            note = json.str("note"),
            knownHostKey = json.str("knownHostKey"),
            osId = json.str("osId"),
            osPretty = json.str("osPretty"),
        )
    }
}

enum class SerialParity {
    NONE,
    ODD,
    EVEN,
    MARK,
    SPACE;

    val wireName: String
        get() = name.lowercase()

    val label: String
        get() = when (this) {
            NONE -> "None"
            ODD -> "Odd"
            EVEN -> "Even"
            MARK -> "Mark"
            SPACE -> "Space"
        }

    companion object {
        fun parse(raw: String?): SerialParity =
            entries.firstOrNull { it.wireName == raw } ?: NONE
    }
}

enum class SerialFlowControl {
    NONE,
    RTS_CTS,
    DSR_DTR,
    XON_XOFF;

    val wireName: String
        get() = when (this) {
            NONE -> "none"
            RTS_CTS -> "rtsCts"
            DSR_DTR -> "dsrDtr"
            XON_XOFF -> "xonXoff"
        }

    val label: String
        get() = when (this) {
            NONE -> "None"
            RTS_CTS -> "RTS/CTS"
            DSR_DTR -> "DSR/DTR"
            XON_XOFF -> "XON/XOFF"
        }

    companion object {
        fun parse(raw: String?): SerialFlowControl =
            entries.firstOrNull { it.wireName == raw } ?: NONE
    }
}

data class SerialDevice(
    val id: String,
    val label: String = "",
    val path: String,
    val baudRate: Int = 115200,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: SerialParity = SerialParity.NONE,
    val flowControl: SerialFlowControl = SerialFlowControl.NONE,
    val dtr: Boolean = true,
    val rts: Boolean = true,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val serialNumber: String? = null,
    val byId: String? = null,
    val hardware: String? = null,
    val note: String? = null,
) {
    val displayLabel: String
        get() = label.ifEmpty { hardware ?: serialPortName(path) }

    val lineSettings: String
        get() = "$baudRate $dataBits${parity.wireName[0].uppercaseChar()}$stopBits"

    val usbIds: String?
        get() = vendorId?.let { "${formatUsbId(it)}:${formatUsbId(productId ?: 0)}" }

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("label", label)
        put("path", path)
        put("baudRate", baudRate)
        put("dataBits", dataBits)
        put("stopBits", stopBits)
        put("parity", parity.wireName)
        put("flowControl", flowControl.wireName)
        put("dtr", dtr)
        put("rts", rts)
        vendorId?.let { put("vendorId", it) }
        productId?.let { put("productId", it) }
        serialNumber?.let { put("serialNumber", it) }
        byId?.let { put("byId", it) }
        hardware?.let { put("hardware", it) }
        note?.let { put("note", it) }
    }

    companion object {
        fun fromJson(json: JsonObject): SerialDevice = SerialDevice(
            id = json.str("id") ?: newId(),
            label = json.str("label") ?: "",
            path = json.str("path") ?: "",
            baudRate = json.int("baudRate") ?: 115200,
            dataBits = json.int("dataBits") ?: 8,
            stopBits = json.int("stopBits") ?: 1,
            parity = SerialParity.parse(json.str("parity")),
            flowControl = SerialFlowControl.parse(json.str("flowControl")),
            dtr = json.bool("dtr") ?: true,
            rts = json.bool("rts") ?: true,
            vendorId = json.int("vendorId"),
            productId = json.int("productId"),
            serialNumber = json.str("serialNumber"),
            byId = json.str("byId"),
            hardware = json.str("hardware"),
            note = json.str("note"),
        )
    }
}

data class VaultData(
    val hosts: List<Host> = emptyList(),
    val identities: List<Identity> = emptyList(),
    val groups: List<HostGroup> = emptyList(),
    val serialDevices: List<SerialDevice> = emptyList(),
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("hosts", JsonArray(hosts.map { it.toJson() }))
        put("identities", JsonArray(identities.map { it.toJson() }))
        put("groups", JsonArray(groups.map { it.toJson() }))
        put("serialDevices", JsonArray(serialDevices.map { it.toJson() }))
    }

    companion object {
        fun fromJson(json: JsonObject): VaultData {
            fun <T> parse(key: String, build: (JsonObject) -> T): List<T> =
                json.arr(key)?.filterIsInstance<JsonObject>()?.map(build) ?: emptyList()

            return VaultData(
                hosts = parse("hosts", Host::fromJson),
                identities = parse("identities", Identity::fromJson),
                groups = parse("groups", HostGroup::fromJson),
                serialDevices = parse("serialDevices", SerialDevice::fromJson),
            )
        }
    }
}
