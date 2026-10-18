package app.getbunker.rcon.rcon

import java.util.Locale

/** A plugin that failed to compile: the file, where, and what the compiler said. */
data class PluginProblem(val file: String, val line: String, val message: String)

/** A plugin list read out of a server reply, ready to show as cards. */
class PluginList(
    val framework: String,
    val columns: List<String>,
    /** Which column holds the plugin's name (what you type into Reload, Load and Unload). */
    val nameColumn: Int,
    val rows: List<List<String>>,
    val unloaded: List<String>,
    val failed: List<PluginProblem>,
) {
    val summary: String
        get() {
            val parts = ArrayList<String>()
            parts += if (rows.isEmpty()) "No plugins loaded" else "${rows.size} plugin${if (rows.size == 1) "" else "s"} loaded"
            if (unloaded.isNotEmpty()) parts += "${unloaded.size} not loaded"
            val files = failed.map { it.file }.distinct().size
            if (files > 0) parts += "$files failed to compile"
            return parts.joinToString("  ·  ") + if (framework.isNotEmpty()) "  ·  $framework" else ""
        }

    fun nameOf(row: List<String>): String = row[nameColumn]
}

/**
 * Turns the plain-text plugin lists that Carbon (`c.plugins`) and Oxide (`oxide.plugins`) print into a table. Carbon's layout
 * was taken from a real server reply (see the test sample). Oxide's is the commonly documented one. A reply that is not
 * recognised returns null so the caller can show the text as it came.
 */
object PluginListParser {
    private val cell = Regex("\\S+(?: \\S+)*")
    private val groupMarker = Regex("^(\\d+|\\*)\\s{2,}\\S")
    private val count = Regex("\\s*\\(\\d+\\)$")
    private val oxideRow = Regex("^\\s*(\\d+)\\s+\"(.+?)\"\\s+\\(([^)]*)\\)\\s+by\\s+(.+?)\\s+\\(([^)]*)\\)(?:\\s+-\\s+(.+?))?\\s*$")

    fun tryParse(reply: String): PluginList? {
        if (reply.isBlank() || reply.length > 400_000) return null
        val lines = reply.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        return carbon(lines) ?: oxide(lines)
    }

    private val carbonNames = mapOf(
        "package" to "Plugin", "author" to "Author", "version" to "Version", "hook time" to "Hook time", "hook fires" to "Hook fires",
        "hook memory" to "Memory", "hook lag" to "Hook lag", "hook exceptions" to "Exceptions", "compile time" to "Compile time", "uptime" to "Uptime",
    )

    /**
     * Carbon prints one aligned table: a header row, then groups (a number and a name, such as "2  Scripts (105)") each followed
     * by its plugins indented by three spaces, then "*" groups for plugins that were not loaded or failed to compile. Every
     * column starts under its header, so cells are cut out by the header's positions.
     */
    private fun carbon(lines: List<String>): PluginList? {
        val headerAt = lines.indexOfFirst { it.trimStart().startsWith('#') && it.contains("package") && it.contains("author") }
        if (headerAt < 0) return null

        val columns = cell.findAll(lines[headerAt]).map { it.value to it.range.first }.drop(1).toList() // skip the "#"
        if (columns.size < 2) return null

        val rows = ArrayList<Array<String>>()
        val unloaded = ArrayList<String>()
        val failed = ArrayList<PluginProblem>()
        var group = ""
        var mode = "plugins"
        var lineStart = 0
        var messageStart = 0

        for (i in headerAt + 1 until lines.size) {
            val line = lines[i].trimEnd()
            if (line.isEmpty()) continue

            if (groupMarker.containsMatchIn(line)) {
                val tokens = cell.findAll(line).toList()
                val marker = tokens[0].value
                val title = tokens.getOrNull(1)?.value ?: ""
                if (marker == "*") {
                    mode = when {
                        title.startsWith("unloaded", ignoreCase = true) -> "unloaded"
                        title.startsWith("failed", ignoreCase = true) -> "failed"
                        else -> "other"
                    }
                    if (mode == "failed" && tokens.size >= 4) { lineStart = tokens[2].range.first; messageStart = tokens[3].range.first }
                } else { mode = "plugins"; group = count.replace(title, "") }
                continue
            }

            if (!line.startsWith("   ") || line.isBlank()) continue // plugin rows are indented by three spaces

            when {
                mode == "plugins" -> {
                    val cells = Array(columns.size + 1) { "" }
                    cells[0] = group
                    for (c in columns.indices) cells[c + 1] = slice(line, columns[c].second, if (c + 1 < columns.size) columns[c + 1].second else -1)
                    if (cells[1].isNotEmpty()) rows += cells
                }
                mode == "unloaded" -> unloaded += line.trim()
                mode == "failed" && lineStart > 0 && messageStart > lineStart ->
                    failed += PluginProblem(slice(line, 3, lineStart), slice(line, lineStart, messageStart), slice(line, messageStart, -1))
            }
        }

        // Columns that are empty for every plugin (for example "exceptions" on a healthy server) are not worth showing.
        val keep = ArrayList<Int>().apply { add(0) }
        for (c in 1..columns.size) if (c == 1 || rows.any { it[c].isNotEmpty() }) keep += c

        return PluginList(
            framework = "Carbon",
            nameColumn = 1,
            columns = keep.map { c -> if (c == 0) "Type" else carbonNames[columns[c - 1].first.lowercase()] ?: capitalise(columns[c - 1].first) },
            rows = rows.map { r -> keep.map { r[it] } },
            unloaded = unloaded,
            failed = failed,
        )
    }

    private fun slice(line: String, start: Int, end: Int): String {
        if (start >= line.length) return ""
        val stop = if (end < 0 || end > line.length) line.length else end
        return if (stop <= start) "" else line.substring(start, stop).trim()
    }

    private fun capitalise(s: String) = if (s.isEmpty()) s else s[0].uppercaseChar() + s.substring(1)

    /** Oxide prints "Listing N plugins:" then one line per plugin: `01 "Name" (1.0.0) by Author (0.29s / 10 KiB) - File.cs`. */
    private fun oxide(lines: List<String>): PluginList? {
        val rows = ArrayList<List<String>>()
        for (line in lines) {
            val m = oxideRow.find(line) ?: continue
            val g = m.groupValues
            val stats = g[5].split('/', limit = 2)
            rows += listOf(g[2].trim(), g[3].trim(), g[4].trim(), stats[0].trim(), if (stats.size > 1) stats[1].trim() else "", g[6].trim())
        }
        if (rows.isEmpty()) return null
        return PluginList("Oxide", listOf("Plugin", "Version", "Author", "Load time", "Memory", "File"), 0, rows, emptyList(), emptyList())
    }

    // ---------------------------------------------------------------- sorting

    private val duration = Regex("^(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$")
    private val quantity = Regex("^([\\d,]*\\.?\\d+)\\s*(ms|s|b|kb|kib|mb|mib|gb|gib)?\\b", RegexOption.IGNORE_CASE)
    // A version is "v1.2.3" or "1.2.3"; a lone number such as "2" is a count, not a version.
    private val versionText = Regex("^(?:v(\\d+(?:\\.\\d+)*)|(\\d+(?:\\.\\d+)+))$", RegexOption.IGNORE_CASE)

    /** A number to sort a cell by, so "9ms" comes before "1092ms", "40.0kb" before "4.2mb". Cells that are not numbers return null. */
    fun sortValue(cellText: String): Double? {
        val s = cellText.trim()
        if (s.isEmpty()) return null

        val d = duration.find(s)
        if (d != null && d.groups.drop(1).any { it != null }) {
            fun g(i: Int) = d.groups[i]?.value?.toDouble() ?: 0.0
            return ((g(1) * 24 + g(2)) * 60 + g(3)) * 60 + g(4)
        }

        val v = versionText.find(s)
        if (v != null) {
            var total = 0.0
            var scale = 1_000_000.0
            for (part in (v.groups[1]?.value ?: v.groups[2]!!.value).split('.')) { total += part.toDouble() * scale; scale /= 1000 }
            return total
        }

        val q = quantity.find(s) ?: return null
        val n = q.groupValues[1].replace(",", "").toDouble()
        return when (q.groupValues[2].lowercase(Locale.ROOT)) {
            "s" -> n * 1000
            "kb", "kib" -> n * 1024
            "mb", "mib" -> n * 1024 * 1024
            "gb", "gib" -> n * 1024 * 1024 * 1024
            else -> n
        }
    }

    /** Sorts rows by a column: numbers by value, everything else as text. Empty cells go last. */
    fun sorted(list: PluginList, column: Int, descending: Boolean): List<List<String>> {
        val cmp = Comparator<List<String>> { a, b ->
            val x = a[column]
            val y = b[column]
            when {
                x.isEmpty() && y.isEmpty() -> 0
                x.isEmpty() -> 1
                y.isEmpty() -> -1
                else -> {
                    val nx = sortValue(x)
                    val ny = sortValue(y)
                    val r = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y, ignoreCase = true)
                    if (descending) -r else r
                }
            }
        }
        return list.rows.sortedWith(cmp)
    }
}
