package com.example.ohmyssh.terminal

/**
 * A pasted block, split into the commands a shell runs one at a time.
 *
 * Splitting is by *command*, not by line: a here-doc, a quoted string or an
 * `if … fi` spans several lines and reaches the shell as one unit, because the
 * shell holds it in its line editor until the construct closes and only then
 * runs it.
 */
class PasteScript(
    val commands: List<String>,
    val droppedComments: Int = 0,
    val droppedNoise: Int = 0,
) {
    val isEmpty: Boolean get() = commands.isEmpty()
}

/// Everything below 0x20 other than tab and newline is dropped along with the
/// escape sequence it belongs to: a clipboard holding copied terminal output
/// would otherwise drive the remote terminal, and a stray ESC is a key binding
/// on the far end.
fun sanitizePasted(raw: String): String {
    val withoutEscapes = raw
        .replace(CSI_SEQUENCE, "")
        .replace(OSC_SEQUENCE, "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    return buildString(withoutEscapes.length) {
        for (ch in withoutEscapes) {
            if (ch == '\n' || ch == '\t' || (ch.code >= 0x20 && ch.code != 0x7F)) append(ch)
        }
    }
}

/**
 * Splits a pasted block into the commands a shell would run one at a time, and
 * drops what a shell cannot run.
 *
 * Whole-line comments go, because an AI answer is half comments and half the
 * shells people sit in — zsh without INTERACTIVE_COMMENTS, dash, busybox ash, a
 * switch CLI — answer `#` with "command not found". Trailing comments stay:
 * telling `echo "a # b"` from a real comment needs the whole quoting context,
 * and getting that wrong eats part of a command.
 *
 * Nothing inside a here-doc body or an open quote is touched — that text is
 * data, and a `#` line in it is a line of the file being written.
 */
fun parsePaste(raw: String): PasteScript {
    val text = normalizeInvisibles(sanitizePasted(raw))
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    // A block that ends with a newline splits into one empty trailing line; a
    // block that does not is the same commands, just selected without it.
    val lines = stripPromptPrefixes(text.split("\n").let { if (text.endsWith("\n")) it.dropLast(1) else it })

    val lexer = ShellLexer()
    val commands = mutableListOf<String>()
    val current = StringBuilder()
    var comments = 0
    var noise = 0

    for (line in lines) {
        if (!lexer.insideLiteral) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                noise++
                continue
            }
            // Markdown that came along with the answer.
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                noise++
                continue
            }
            if (trimmed.startsWith("#")) {
                comments++
                continue
            }
        }
        if (current.isNotEmpty()) current.append('\n')
        current.append(line)
        lexer.feed(line)
        if (lexer.complete) {
            commands.add(current.toString())
            current.clear()
        }
    }
    // An unterminated construct — a here-doc whose delimiter never arrived —
    // still goes out. The shell sits on its continuation prompt, which is the
    // truth about what was pasted; dropping it silently would be worse.
    if (current.isNotEmpty()) commands.add(current.toString())

    return PasteScript(
        commands = commands,
        droppedComments = comments,
        droppedNoise = noise,
    )
}

private val CSI_SEQUENCE = Regex("\u001B\\[[0-9;?<>=!\"' ]*[@-~]")

private val OSC_SEQUENCE = Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)")

/// Text copied out of a browser or a chat carries spaces that are not spaces
/// and marks that take no width; both reach the shell inside a word and break
/// the command with nothing on screen to explain it.
private fun normalizeInvisibles(text: String): String = buildString(text.length) {
    for (ch in text) {
        when (ch) {
            '\u00A0', '\u2007', '\u2009', '\u202F' -> append(' ')
            '\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF' -> {}
            else -> append(ch)
        }
    }
}

/// A block copied out of documentation carries the prompt it was printed
/// behind. Only stripped when *every* command line has it: one line starting
/// with a literal "$ " is far more likely to be real text.
private fun stripPromptPrefixes(lines: List<String>): List<String> {
    val meaningful = lines.filter { it.isNotBlank() }
    if (meaningful.isEmpty()) return lines
    if (!meaningful.all { it.startsWith("$ ") }) return lines
    return lines.map { if (it.startsWith("$ ")) it.substring(2) else it }
}

private class Heredoc(val word: String, val stripTabs: Boolean)

/**
 * Just enough shell syntax to know where one command ends.
 *
 * Tracks quoting, here-docs, line continuations and block nesting. Block
 * keywords only count in command position, so `echo done` does not close a
 * `do … done` that is still open.
 */
private class ShellLexer {
    private var inSingle = false
    private var inDouble = false
    private var heredoc: Heredoc? = null
    private val pendingHeredocs = ArrayDeque<Heredoc>()
    private var blocks = 0
    private var parens = 0
    private var continues = false

    /// Whether the next line is here-doc body or the inside of a quote, where
    /// every character is data and nothing may be rewritten.
    val insideLiteral: Boolean get() = heredoc != null || inSingle || inDouble

    val complete: Boolean
        get() = !inSingle && !inDouble && heredoc == null && pendingHeredocs.isEmpty() &&
            blocks == 0 && parens == 0 && !continues

    fun feed(line: String) {
        heredoc?.let { open ->
            val candidate = if (open.stripTabs) line.trimStart('\t') else line
            if (candidate.trimEnd() == open.word) heredoc = pendingHeredocs.removeFirstOrNull()
            return
        }

        continues = false
        var atCommandStart = true
        var token = StringBuilder()
        var codeEnd = line.length

        fun flush() {
            if (token.isEmpty()) return
            val word = token.toString()
            token = StringBuilder()
            if (atCommandStart) {
                when (word) {
                    "if", "case", "do", "{" -> blocks++
                    "fi", "esac", "done", "}" -> blocks = (blocks - 1).coerceAtLeast(0)
                }
            }
            atCommandStart = word in COMMAND_STARTERS
        }

        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inSingle -> {
                    if (ch == '\'') inSingle = false
                    i++
                }

                inDouble -> {
                    if (ch == '\\' && i + 1 < line.length) i++
                    else if (ch == '"') inDouble = false
                    i++
                }

                ch == '\\' -> {
                    if (i == line.lastIndex) continues = true else i++
                    i++
                }

                ch == '\'' -> {
                    inSingle = true
                    i++
                }

                ch == '"' -> {
                    inDouble = true
                    i++
                }

                // A comment only starts where a word could: echo foo#bar is one
                // word and https://host/page#top is a URL.
                ch == '#' && token.isEmpty() -> {
                    codeEnd = i
                    i = line.length
                }

                ch == '<' && line.getOrNull(i + 1) == '<' && line.getOrNull(i + 2) != '<' -> {
                    flush()
                    i = readHeredocWord(line, i + 2)
                }

                ch.isWhitespace() -> {
                    flush()
                    i++
                }

                ch == ';' || ch == '&' || ch == '|' -> {
                    flush()
                    atCommandStart = true
                    i++
                }

                ch == '(' -> {
                    flush()
                    parens++
                    atCommandStart = true
                    i++
                }

                ch == ')' -> {
                    flush()
                    parens = (parens - 1).coerceAtLeast(0)
                    i++
                }

                else -> {
                    token.append(ch)
                    i++
                }
            }
        }
        flush()

        val code = line.substring(0, codeEnd).trimEnd()
        if (code.endsWith("|") || code.endsWith("&&")) continues = true

        if (heredoc == null) heredoc = pendingHeredocs.removeFirstOrNull()
    }

    private fun readHeredocWord(line: String, from: Int): Int {
        var i = from
        var stripTabs = false
        if (line.getOrNull(i) == '-') {
            stripTabs = true
            i++
        }
        while (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
        val word = StringBuilder()
        var quote: Char? = null
        loop@ while (i < line.length) {
            val ch = line[i]
            when {
                quote != null -> {
                    if (ch == quote) quote = null else word.append(ch)
                    i++
                }

                ch == '\'' || ch == '"' -> {
                    quote = ch
                    i++
                }

                ch == '\\' && i + 1 < line.length -> {
                    word.append(line[i + 1])
                    i += 2
                }

                ch.isWhitespace() || ch in ";|&<>()" -> break@loop

                else -> {
                    word.append(ch)
                    i++
                }
            }
        }
        if (word.isNotEmpty()) pendingHeredocs.addLast(Heredoc(word.toString(), stripTabs))
        return i
    }
}

/// Words after which the next word is a command again: `then done` closes a
/// block, `echo done` does not.
private val COMMAND_STARTERS = setOf("then", "else", "elif", "do", "{", "!", "time")
