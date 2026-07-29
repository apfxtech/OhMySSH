package com.example.ohmyssh.ssh

import com.example.ohmyssh.services.Log
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class HostMetrics(
    val load1: Double? = null,
    val load5: Double? = null,
    val load15: Double? = null,
    val cpuPercent: Double? = null,
    val cpuCount: Int? = null,
    val memTotalKb: Long? = null,
    val memAvailableKb: Long? = null,
    val diskTotalKb: Long? = null,
    val diskFreeKb: Long? = null,
    val uptime: Duration? = null,
) {
    val memUsedRatio: Double?
        get() {
            val total = memTotalKb ?: return null
            val available = memAvailableKb ?: return null
            if (total <= 0) return null
            return ((total - available).toDouble() / total).coerceIn(0.0, 1.0)
        }

    val diskUsedRatio: Double?
        get() {
            val total = diskTotalKb ?: return null
            val free = diskFreeKb ?: return null
            if (total <= 0) return null
            return ((total - free).toDouble() / total).coerceIn(0.0, 1.0)
        }
}

class HostProfile(
    val osId: String,
    val osPretty: String,
    val kernel: String? = null,
    val arch: String? = null,
    val hostname: String? = null,
    val metrics: HostMetrics = HostMetrics(),
)

val kKnownOsIds = setOf(
    "linux",
    "ubuntu",
    "debian",
    "arch",
    "fedora",
    "centos",
    "rhel",
    "alpine",
    "opensuse",
    "gentoo",
    "manjaro",
    "mint",
    "rocky",
    "almalinux",
    "kali",
    "raspbian",
    "freebsd",
    "openbsd",
    "netbsd",
    "macos",
    "windows",
    "unknown",
)

fun osIconAsset(osId: String?): String {
    val id = osId ?: "unknown"
    return if (kKnownOsIds.contains(id)) id else "linux"
}

fun osColorValue(osId: String?): Long = when (osId) {
    "ubuntu" -> 0xFFE95420
    "debian", "raspbian" -> 0xFFA80030
    "arch", "manjaro" -> 0xFF1793D1
    "fedora" -> 0xFF294172
    "centos", "rocky", "almalinux", "rhel" -> 0xFFEE0000
    "alpine" -> 0xFF0D597F
    "opensuse" -> 0xFF73BA25
    "mint" -> 0xFF87CF3E
    "kali" -> 0xFF557C94
    "gentoo" -> 0xFF54487A
    "freebsd", "openbsd", "netbsd" -> 0xFFAB2B28
    "macos" -> 0xFF9E9E9E
    "windows" -> 0xFF0078D4
    else -> 0xFF6F6F6F
}

private val probeTimeout = 20.seconds

suspend fun probeHost(connection: SshConnection): HostProfile {
    val kernel = tryRun(connection, "uname -s").trim()

    if (kernel.isEmpty()) {
        // No `uname` — almost certainly Windows OpenSSH on cmd.exe or PowerShell.
        return probeWindows(connection)
    }
    if (kernel == "Darwin") return probeMacos(connection)
    if (kernel.endsWith("BSD")) return probeBsd(connection, kernel)
    return probeLinux(connection)
}

private suspend fun tryRun(connection: SshConnection, command: String): String = try {
    connection.exec(command, probeTimeout.inWholeMilliseconds)
} catch (error: Exception) {
    // A missing command is normal — that is how the family is detected.
    Log.info("probe", "command failed, treating as empty: $command ($error)")
    ""
}

private suspend fun probeLinux(connection: SshConnection): HostProfile {
    val script = """
echo "@@osrelease"; cat /etc/os-release 2>/dev/null
echo "@@kernel"; uname -r 2>/dev/null; uname -m 2>/dev/null; hostname 2>/dev/null
echo "@@load"; cat /proc/loadavg 2>/dev/null
echo "@@cpus"; nproc 2>/dev/null
echo "@@mem"; grep -E '^(MemTotal|MemAvailable):' /proc/meminfo 2>/dev/null
echo "@@uptime"; cut -d. -f1 /proc/uptime 2>/dev/null
echo "@@disk"; df -Pk / 2>/dev/null | tail -1
"""

    val sections = sections(tryRun(connection, script))

    val release = parseOsRelease(sections["osrelease"] ?: emptyList())
    val kernelLines = sections["kernel"] ?: emptyList()
    val load = parseLoadAvg(sections["load"]?.firstOrNull())
    val mem = parseMeminfo(sections["mem"] ?: emptyList())
    val disk = parseDf(sections["disk"]?.firstOrNull())

    return HostProfile(
        osId = release.first,
        osPretty = release.second,
        kernel = kernelLines.getOrNull(0),
        arch = kernelLines.getOrNull(1),
        hostname = kernelLines.getOrNull(2),
        metrics = HostMetrics(
            load1 = load?.getOrNull(0),
            load5 = load?.getOrNull(1),
            load15 = load?.getOrNull(2),
            cpuCount = sections["cpus"]?.firstOrNull()?.trim()?.toIntOrNull(),
            memTotalKb = mem.first,
            memAvailableKb = mem.second,
            diskTotalKb = disk?.first,
            diskFreeKb = disk?.second,
            uptime = sections["uptime"]?.firstOrNull()?.trim()?.toLongOrNull()?.let { it.seconds },
        ),
    )
}

private suspend fun probeMacos(connection: SshConnection): HostProfile {
    val script = """
echo "@@sw"; sw_vers -productName 2>/dev/null; sw_vers -productVersion 2>/dev/null
echo "@@kernel"; uname -r 2>/dev/null; uname -m 2>/dev/null; hostname 2>/dev/null
echo "@@load"; sysctl -n vm.loadavg 2>/dev/null
echo "@@cpus"; sysctl -n hw.ncpu 2>/dev/null
echo "@@mem"; sysctl -n hw.memsize 2>/dev/null
echo "@@boot"; sysctl -n kern.boottime 2>/dev/null
echo "@@now"; date +%s 2>/dev/null
echo "@@disk"; df -Pk / 2>/dev/null | tail -1
"""

    val sections = sections(tryRun(connection, script))
    val sw = sections["sw"] ?: emptyList()
    val kernelLines = sections["kernel"] ?: emptyList()
    // `sysctl -n vm.loadavg` prints "{ 1.23 1.45 1.67 }".
    val load = parseLoadAvg(sections["load"]?.firstOrNull()?.replace(Regex("[{}]"), ""))
    val memBytes = sections["mem"]?.firstOrNull()?.trim()?.toLongOrNull()
    val disk = parseDf(sections["disk"]?.firstOrNull())

    val product = sw.getOrNull(0) ?: "macOS"
    val version = sw.getOrNull(1) ?: ""

    return HostProfile(
        osId = "macos",
        osPretty = if (version.isEmpty()) product else "$product $version",
        kernel = kernelLines.getOrNull(0),
        arch = kernelLines.getOrNull(1),
        hostname = kernelLines.getOrNull(2),
        metrics = HostMetrics(
            load1 = load?.getOrNull(0),
            load5 = load?.getOrNull(1),
            load15 = load?.getOrNull(2),
            cpuCount = sections["cpus"]?.firstOrNull()?.trim()?.toIntOrNull(),
            memTotalKb = memBytes?.let { it / 1024 },
            diskTotalKb = disk?.first,
            diskFreeKb = disk?.second,
            uptime = macUptime(sections["boot"]?.firstOrNull(), sections["now"]?.firstOrNull()),
        ),
    )
}

private suspend fun probeBsd(connection: SshConnection, kernelName: String): HostProfile {
    val script = """
echo "@@kernel"; uname -r 2>/dev/null; uname -m 2>/dev/null; hostname 2>/dev/null
echo "@@load"; sysctl -n vm.loadavg 2>/dev/null
echo "@@cpus"; sysctl -n hw.ncpu 2>/dev/null
echo "@@mem"; sysctl -n hw.physmem 2>/dev/null
echo "@@disk"; df -Pk / 2>/dev/null | tail -1
"""

    val sections = sections(tryRun(connection, script))
    val kernelLines = sections["kernel"] ?: emptyList()
    val load = parseLoadAvg(sections["load"]?.firstOrNull()?.replace(Regex("[{}]"), ""))
    val memBytes = sections["mem"]?.firstOrNull()?.trim()?.toLongOrNull()
    val disk = parseDf(sections["disk"]?.firstOrNull())
    val id = kernelName.lowercase()

    return HostProfile(
        osId = if (kKnownOsIds.contains(id)) id else "unknown",
        osPretty = if (kernelLines.isEmpty()) kernelName else "$kernelName ${kernelLines[0]}",
        kernel = kernelLines.getOrNull(0),
        arch = kernelLines.getOrNull(1),
        hostname = kernelLines.getOrNull(2),
        metrics = HostMetrics(
            load1 = load?.getOrNull(0),
            load5 = load?.getOrNull(1),
            load15 = load?.getOrNull(2),
            cpuCount = sections["cpus"]?.firstOrNull()?.trim()?.toIntOrNull(),
            memTotalKb = memBytes?.let { it / 1024 },
            diskTotalKb = disk?.first,
            diskFreeKb = disk?.second,
        ),
    )
}

private suspend fun probeWindows(connection: SshConnection): HostProfile {
    // Single quotes only inside the -Command string: the outer double quotes
    // have to survive cmd.exe, the default OpenSSH shell on Windows.
    val command = "powershell -NoProfile -NonInteractive -Command " +
        "\"\$o=Get-CimInstance Win32_OperatingSystem;" +
        "\$c=Get-CimInstance Win32_ComputerSystem;" +
        "\$d=Get-CimInstance Win32_LogicalDisk|Where-Object {\$_.DeviceID -eq 'C:'};" +
        "\$p=(Get-CimInstance Win32_Processor|Measure-Object -Property LoadPercentage -Average).Average;" +
        "@{caption=\$o.Caption;version=\$o.Version;arch=\$o.OSArchitecture;" +
        "host=\$c.Name;cpus=\$c.NumberOfLogicalProcessors;cpuPercent=\$p;" +
        "memTotalKb=\$o.TotalVisibleMemorySize;memFreeKb=\$o.FreePhysicalMemory;" +
        "uptimeSec=[int]((Get-Date)-\$o.LastBootUpTime).TotalSeconds;" +
        "diskTotalB=\$d.Size;diskFreeB=\$d.FreeSpace}" +
        "|ConvertTo-Json -Compress\""

    val raw = tryRun(connection, command).trim()
    val json = if (raw.startsWith("{")) {
        runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(raw)
                as? kotlinx.serialization.json.JsonObject
        }.getOrNull()
    } else {
        null
    } ?: return HostProfile(osId = "windows", osPretty = "Windows")

    fun num(key: String): Double? =
        (json[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()

    fun text(key: String): String? =
        (json[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

    val caption = text("caption")?.trim()
    return HostProfile(
        osId = "windows",
        osPretty = if (caption.isNullOrEmpty()) "Windows" else caption,
        kernel = text("version"),
        arch = text("arch"),
        hostname = text("host"),
        metrics = HostMetrics(
            cpuPercent = num("cpuPercent"),
            cpuCount = num("cpus")?.toInt(),
            memTotalKb = num("memTotalKb")?.toLong(),
            memAvailableKb = num("memFreeKb")?.toLong(),
            diskTotalKb = num("diskTotalB")?.let { (it / 1024).toLong() },
            diskFreeKb = num("diskFreeB")?.let { (it / 1024).toLong() },
            uptime = num("uptimeSec")?.toLong()?.seconds,
        ),
    )
}

private fun sections(output: String): Map<String, List<String>> {
    val result = LinkedHashMap<String, MutableList<String>>()
    var current: String? = null
    for (rawLine in output.lineSequence()) {
        val line = rawLine.trim()
        if (line.startsWith("@@")) {
            current = line.substring(2)
            result[current] = mutableListOf()
            continue
        }
        if (current == null || line.isEmpty()) continue
        result.getValue(current).add(line)
    }
    return result
}

private fun parseOsRelease(lines: List<String>): Pair<String, String> {
    val values = HashMap<String, String>()
    for (line in lines) {
        val split = line.indexOf('=')
        if (split <= 0) continue
        var value = line.substring(split + 1).trim()
        if (value.length >= 2 &&
            ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'")))
        ) {
            value = value.substring(1, value.length - 1)
        }
        values[line.substring(0, split).trim()] = value
    }

    val id = values["ID"]?.lowercase() ?: ""
    val pretty = values["PRETTY_NAME"] ?: values["NAME"] ?: "Linux"

    if (kKnownOsIds.contains(id)) return id to pretty

    for (like in (values["ID_LIKE"] ?: "").lowercase().split(Regex("\\s+"))) {
        if (kKnownOsIds.contains(like)) return like to pretty
    }
    return "linux" to pretty
}

private fun parseLoadAvg(line: String?): List<Double>? {
    if (line == null) return null
    val parts = line.trim().split(Regex("\\s+"))
    if (parts.size < 3) return null
    val values = parts.take(3).map { it.toDoubleOrNull() }
    if (values.any { it == null }) return null
    return values.filterNotNull()
}

private fun parseMeminfo(lines: List<String>): Pair<Long?, Long?> {
    var total: Long? = null
    var available: Long? = null
    val pattern = Regex("^(\\w+):\\s+(\\d+)")
    for (line in lines) {
        val match = pattern.find(line) ?: continue
        val value = match.groupValues[2].toLongOrNull()
        when (match.groupValues[1]) {
            "MemTotal" -> total = value
            "MemAvailable" -> available = value
        }
    }
    return total to available
}

private fun parseDf(line: String?): Pair<Long?, Long?>? {
    if (line == null) return null
    val parts = line.trim().split(Regex("\\s+"))
    if (parts.size < 4) return null
    return parts[1].toLongOrNull() to parts[3].toLongOrNull()
}

/// `sysctl -n kern.boottime` prints "{ sec = 1690000000, usec = 0 } ...".
private fun macUptime(boottime: String?, now: String?): Duration? {
    if (boottime == null || now == null) return null
    val boot = Regex("sec\\s*=\\s*(\\d+)").find(boottime)?.groupValues?.get(1)?.toLongOrNull()
    val current = now.trim().toLongOrNull()
    if (boot == null || current == null || current < boot) return null
    return (current - boot).seconds
}
