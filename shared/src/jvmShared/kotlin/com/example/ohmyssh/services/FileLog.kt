package com.example.ohmyssh.services

import com.example.ohmyssh.platform.AppFiles
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/// Big enough to hold a long working session, small enough that an agent can
/// still read the whole file when grep is not the right tool.
private const val MAX_BYTES = 8L * 1024 * 1024

private val STAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/**
 * The app's log, written as a file meant to be grepped.
 *
 * One event is one line, always. A stack trace has its newlines escaped rather
 * than wrapped, so a grep hit is a whole event, a line count is an event count,
 * and `grep -c` answers "how many times" without lying. The fields before the
 * message are positional — UTC timestamp, level, scope — and the message itself
 * is key=value wherever there is anything to key, so `grep 'session=k3f9'` and
 * `grep ' mcp '` both work without a parser.
 *
 * Until this is installed the app logs to stderr only, which a bundled desktop
 * build throws away — which is how a session can end with no record of what an
 * agent actually sent.
 */
object FileLog {
    private val lock = Any()

    private var file: File? = null
    private var stream: FileOutputStream? = null

    val path: String? get() = file?.path

    /// Returns the path it will write to, or null if it could not open one.
    /// [directory] is for tests, which must not write into the real app data.
    fun install(banner: String? = null, directory: String? = null): String? = synchronized(lock) {
        file?.let { return it.path }

        val target = try {
            val dir = directory?.let(::File) ?: File(AppFiles.appSupportDirectory(), "logs")
            dir.mkdirs()
            File(dir, "ohmyssh.log")
        } catch (error: Exception) {
            Log.warn("log", "no log file: ${error.message ?: error}")
            return null
        }

        file = target
        Log.sink = ::append
        Log.location = target.path
        Log.info("log", "logging to ${target.path}${banner?.let { " $it" } ?: ""}")
        target.path
    }

    private fun append(level: String, scope: String, message: String) {
        val line = StringBuilder()
            .append(STAMP.format(Instant.now()))
            .append(' ')
            // Padded so the scope and the message start in the same column on
            // every line, which is what makes cut and awk usable on this.
            .append(level.padEnd(5))
            .append(' ')
            .append(scope)
            .append(' ')
            .append(oneLine(message))
            .append('\n')
            .toString()

        synchronized(lock) {
            try {
                val target = file ?: return
                rotateIfFull(target)
                val out = stream ?: FileOutputStream(target, true).also { stream = it }
                out.write(line.toByteArray())
                // Flushed per line: the events worth having are usually the
                // last ones before whatever went wrong.
                out.flush()
            } catch (_: Exception) {
                // A log that throws is worse than a log that drops a line.
            }
        }
    }

    /// Keeps one previous file. Two generations is enough to survive a rotation
    /// in the middle of the session someone is asking about.
    private fun rotateIfFull(target: File) {
        if (target.length() < MAX_BYTES) return
        stream?.runCatching { close() }
        stream = null
        val previous = File(target.path + ".1")
        previous.delete()
        target.renameTo(previous)
    }
}

/**
 * Folds a message onto one line.
 *
 * A stack trace is many lines and would otherwise turn one event into forty,
 * breaking every count and leaving grep to return a fragment with no timestamp
 * on it.
 *
 * Only the line breaks are escaped. Doubling backslashes as well would make an
 * escaped newline unambiguous, at the price of rewriting every command that
 * contains one — and a log of what ran is worth more read verbatim than parsed
 * exactly.
 */
private fun oneLine(text: String): String = buildString(text.length) {
    for (ch in text) {
        when {
            ch == '\n' -> append("\\n")
            ch == '\r' -> append("\\r")
            ch == '\t' -> append("\\t")
            ch.code < 0x20 || ch.code == 0x7F -> append(' ')
            else -> append(ch)
        }
    }
}
