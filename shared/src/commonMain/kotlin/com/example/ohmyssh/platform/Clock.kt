package com.example.ohmyssh.platform

expect fun localOffsetSeconds(epochMillis: Long): Int

fun epochMillis(): Long = epochMicros() / 1000

private val monthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private class CivilTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val epochDay: Long,
)

private fun civil(epochMillis: Long): CivilTime {
    val local = epochMillis / 1000 + localOffsetSeconds(epochMillis)
    var day = local / 86400
    var secondOfDay = local % 86400
    if (secondOfDay < 0) {
        secondOfDay += 86400
        day -= 1
    }

    // Days-from-civil, run backwards: shift the epoch to 0000-03-01 so leap days
    // land at the end of a 400-year era and the month arithmetic stays integral.
    var z = day + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val dayOfEra = z - era * 146097
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
    val year = yearOfEra + era * 400
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val shiftedMonth = (5 * dayOfYear + 2) / 153
    val dayOfMonth = dayOfYear - (153 * shiftedMonth + 2) / 5 + 1
    val month = if (shiftedMonth < 10) shiftedMonth + 3 else shiftedMonth - 9

    return CivilTime(
        year = (if (month <= 2) year + 1 else year).toInt(),
        month = month.toInt(),
        day = dayOfMonth.toInt(),
        hour = (secondOfDay / 3600).toInt(),
        minute = (secondOfDay % 3600 / 60).toInt(),
        epochDay = day,
    )
}

private fun Int.pad2(): String = if (this < 10) "0$this" else "$this"

fun formatWallClock(epochMillis: Long, nowMillis: Long = epochMillis()): String {
    val then = civil(epochMillis)
    val today = civil(nowMillis)
    val time = "${then.hour.pad2()}:${then.minute.pad2()}"
    val month = monthNames.getOrElse(then.month - 1) { "?" }

    return when {
        then.epochDay == today.epochDay -> time
        then.epochDay == today.epochDay - 1 -> "Yesterday · $time"
        then.year == today.year -> "$month ${then.day} · $time"
        else -> "${then.day} $month ${then.year} · $time"
    }
}

fun formatRelative(epochMillis: Long, nowMillis: Long = epochMillis()): String {
    val seconds = (nowMillis - epochMillis) / 1000
    return when {
        seconds < 0 -> formatWallClock(epochMillis, nowMillis)
        seconds < 45 -> "just now"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86400 -> "${seconds / 3600} h ago"
        seconds < 7 * 86400 -> "${seconds / 86400} d ago"
        else -> formatWallClock(epochMillis, nowMillis)
    }
}

fun formatSpan(millis: Long): String {
    val seconds = millis / 1000
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    if (minutes < 60) {
        val rest = seconds % 60
        return if (rest == 0L) "${minutes}m" else "${minutes}m ${rest}s"
    }
    val hours = minutes / 60
    val rest = minutes % 60
    if (hours < 24) return if (rest == 0L) "${hours}h" else "${hours}h ${rest}m"
    val days = hours / 24
    return "${days}d ${hours % 24}h"
}
