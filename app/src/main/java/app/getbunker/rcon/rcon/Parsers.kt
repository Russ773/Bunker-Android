package app.getbunker.rcon.rcon

import app.getbunker.rcon.data.HideRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.util.Locale

data class ServerInfo(
    val hostname: String,
    val players: Int,
    val maxPlayers: Int,
    val queued: Int,
    val joining: Int,
    val entities: Int,
    val fps: Double,
    val uptimeSeconds: Long,
    val memoryMb: Long,
    val map: String,
)

data class PlayerInfo(
    val steamId: String,
    val name: String,
    val ping: Int,
    val address: String,
    val connectedSeconds: Long,
    val health: Double,
)

/** One reading of the server (used for the health charts). */
data class HealthSample(
    val timeMillis: Long,
    val players: Int,
    val fps: Double,
    val entities: Int,
    val memoryMb: Long,
    val uptimeSeconds: Long,
)

enum class LogKind { Info, Normal, Chat, Warning, Error, Command }

/** One console line. For chat, [userId], [userName] and [message] say who said what. */
data class LogEntry(
    val id: Long,
    val timeMillis: Long,
    val text: String,
    val kind: LogKind,
    val userId: String? = null,
    val userName: String? = null,
    val message: String? = null,
    val color: String? = null,
    val channel: Int = 0,
    val tags: String? = null,
) {
    val isTeam: Boolean get() = channel == 1
}

object RconParsers {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.str(name: String): String {
        val v = this[name] as? JsonPrimitive ?: return ""
        return v.contentOrNull ?: ""
    }

    private fun JsonObject.num(name: String): Double = (this[name] as? JsonPrimitive)?.doubleOrNull ?: 0.0

    fun parseInfo(text: String): ServerInfo? = try {
        val r = json.parseToJsonElement(text).jsonObject
        ServerInfo(
            hostname = r.str("Hostname"),
            players = r.num("Players").toInt(),
            maxPlayers = r.num("MaxPlayers").toInt(),
            queued = r.num("Queued").toInt(),
            joining = r.num("Joining").toInt(),
            entities = r.num("EntityCount").toInt(),
            fps = r.num("Framerate"),
            uptimeSeconds = r.num("Uptime").toLong(),
            memoryMb = r.num("Memory").toLong(),
            map = r.str("Map"),
        )
    } catch (_: Exception) {
        null
    }

    fun parsePlayers(text: String): List<PlayerInfo>? = try {
        val arr: JsonArray = json.parseToJsonElement(text) as JsonArray
        arr.map { it.jsonObject }.map { p ->
            PlayerInfo(
                steamId = p.str("SteamID"),
                name = p.str("DisplayName"),
                ping = p.num("Ping").toInt(),
                address = p.str("Address"),
                connectedSeconds = p.num("ConnectedSeconds").toLong(),
                health = p.num("Health"),
            )
        }
    } catch (_: Exception) {
        null
    }

    data class ChatParsed(
        val text: String,
        val userId: String?,
        val name: String?,
        val message: String?,
        val color: String?,
        val channel: Int,
        val tags: String?,
    )

    /**
     * Newer Rust versions send chat as JSON: who said it (name, Steam ID, the name colour the game shows), the message
     * and the channel (0 = global, 1 = team). Turns it into "Name: message" plus those details.
     */
    fun parseChat(raw: String): ChatParsed {
        try {
            val r = json.parseToJsonElement(raw).jsonObject
            if (r.containsKey("Username") && r.containsKey("Message")) {
                val id = r.str("UserId").ifEmpty { null }
                val color = r.str("Color").ifEmpty { null }
                val channel = (r["Channel"] as? JsonPrimitive)?.intOrNull ?: 0
                val who = ChatText.stripMarkup(r.str("Username"))
                val (tags, said) = ChatText.splitTags(who, ChatText.stripMarkup(r.str("Message")))
                return ChatParsed("${if (tags.isNotEmpty()) "$tags " else ""}$who: $said", id, who, said, color, channel, tags.ifEmpty { null })
            }
        } catch (_: Exception) { }
        val plain = raw.trimEnd('\n')
        return ChatParsed(plain, null, null, plain, null, 0, null)
    }
}

/** Tidies chat text that chat plugins have already formatted, so it reads the way it does in the game. */
object ChatText {
    private val markup = Regex("\u001B\\[[0-9;]*[A-Za-z]|</?(?:color|size|b|i)(?:=[^>]*)?>", RegexOption.IGNORE_CASE)

    /** Removes colour and size codes and control characters. */
    fun stripMarkup(text: String): String =
        markup.replace(text, "").filter { !it.isISOControl() || it == '\n' }.trim()

    /**
     * Chat plugins such as Better Chat put their own prefix and the player's name inside the message ("[ADMIN] Russ: Testing"),
     * while the server also sends the name separately. If the message starts with one or more [tags] followed by the player's
     * own name and a colon, the tags are returned separately and the message is what is left.
     */
    fun splitTags(username: String, message: String): Pair<String, String> {
        if (username.isBlank()) return "" to message
        val re = Regex("^(?<tags>(?:\\[[^\\]]*\\]\\s*)+)" + Regex.escape(username.trim()) + ":\\s*(?<rest>.+)$", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val m = re.find(message) ?: return "" to message
        return m.groups["tags"]!!.value.trim() to m.groups["rest"]!!.value.trim()
    }
}

/** Helpers for building RCON commands from user text. */
object Cmd {
    /** Wraps user text in quotes for a console command, removing quotes and control characters so it cannot break out. */
    fun quote(text: String): String {
        var clean = text.filter { !it.isISOControl() }.replace('"', '\'').trim()
        if (clean.length > 200) clean = clean.take(200)
        return "\"$clean\""
    }

    val steamIdRegex = Regex("^\\d{17}$")
    val safeItemRegex = Regex("^[A-Za-z0-9._-]{1,80}$")
    fun isSteamId(text: String?): Boolean = text != null && steamIdRegex.matches(text)
}

/** Tells whether something typed in the console box looks like a server command, or just a message. */
object CommandGuess {
    private val known = setOf(
        "status", "players", "playerlist", "serverinfo", "kick", "kickall", "ban", "banid", "unban", "banlist", "banlistex", "listid", "listip",
        "mute", "unmute", "say", "find", "findtext", "help", "quit", "restart", "save", "writecfg", "readcfg", "count", "version", "echo", "clear",
        "pause", "bind", "teleport", "teleport2me", "teleportany", "teleportpos", "ownerid", "moderatorid", "removeowner", "removemoderator",
        "kill", "respawn", "spawn", "give", "giveid", "givearm", "giveto", "entid", "ent", "sleepingusers", "perf", "debug", "console",
    )

    /** True if the first word is a known command or has a dot in it (a namespaced command like server.save). */
    fun looksLikeCommand(text: String, confirmed: Collection<String> = emptyList()): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        val first = firstWord(t)
        return '.' in first || first.lowercase() in known || first in confirmed
    }

    fun firstWord(text: String): String = text.trim().split(' ', limit = 2)[0]
}

/** Rules for console lines you never want to see (for example a plugin that logs every few seconds). */
object ConsoleFilter {
    private val timePrefix = Regex("^\\d{2}:\\d{2}:\\d{2}\\s+")
    private val levelTags = setOf("error", "warning", "info", "debug", "log", "chat")
    private val leadingTag = Regex("^\\[([^\\]]{1,40})\\]")

    fun isHidden(rules: List<HideRule>, entry: LogEntry): Boolean {
        if (rules.isEmpty() || entry.kind == LogKind.Command || entry.kind == LogKind.Info) return false
        val plugin = pluginOf(entry.text)
        for (rule in rules) {
            if (rule.value.isEmpty()) continue
            if (rule.isPlugin) {
                if (plugin != null && plugin.equals(rule.value, ignoreCase = true)) return true
            } else if (entry.text.contains(rule.value, ignoreCase = true)) return true
        }
        return false
    }

    /** The plugin named at the start of a line, such as "Economics" for "[Economics] Saving balances", or null. */
    fun pluginOf(text: String): String? =
        leadingTag.find(timePrefix.replace(text, "").trim())?.groupValues?.get(1)?.trim()

    /** The plugins that have written to the console, busiest first. */
    fun pluginsSeen(entries: List<LogEntry>): List<Pair<String, Int>> =
        entries.asSequence()
            .filter { it.kind == LogKind.Normal || it.kind == LogKind.Warning || it.kind == LogKind.Error }
            .mapNotNull { pluginOf(it.text) }
            .filter { it.isNotBlank() && it.lowercase() !in levelTags }
            .groupingBy { it.lowercase() }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
            .toList()
}

data class BanEntry(val index: String, val steamId: String, val name: String, val reason: String, val expiry: Long) {
    val permanent: Boolean get() = expiry < 0
}

/** Reads the lines `banlistex` prints: `1 76561198000000000 "name" "reason" -1`. */
object BanListParser {
    private val line = Regex("^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s+)?(\\d+)\\s+(\\d{15,20})\\s+\"(.*?)\"\\s+\"(.*)\"\\s+(-?\\d+)\\s*$")

    /** Returns null if the reply is anything other than a ban list (so the caller can show it as text instead). */
    fun tryParse(reply: String): List<BanEntry>? {
        if (reply.isBlank() || reply.length > 400_000) return null
        val lines = reply.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val out = ArrayList<BanEntry>(lines.size)
        for (l in lines) {
            val m = line.find(l) ?: return null
            val g = m.groupValues
            out += BanEntry(g[1], g[2], g[3], g[4], g[5].toLongOrNull() ?: -1)
        }
        return out
    }

    /** "Permanent", or the date the ban ends. */
    fun describeExpiry(expiry: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (expiry < 0) return "Permanent"
        if (expiry < 1_000_000_000L) return "in " + Fmt.duration(expiry)
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val end = expiry * 1000
        return fmt.format(java.util.Date(end)) + if (end < nowMillis) " (expired)" else ""
    }
}

object Fmt {
    /** 93784 -> "1d 2h", 3700 -> "1h 1m", 59 -> "59s". */
    fun duration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

    fun memory(mb: Long): String = if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024.0) else "$mb MB"
}
