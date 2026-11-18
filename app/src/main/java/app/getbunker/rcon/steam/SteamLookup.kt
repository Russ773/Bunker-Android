package app.getbunker.rcon.steam

import app.getbunker.rcon.rcon.Cmd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

/** What Steam says about a player. Anything a private profile hides, or that needs a Steam key, is null. */
data class SteamProfile(
    val steamId: String,
    val personaName: String,
    /** "public", "friendsonly" or "private". */
    val privacy: String = "private",
    val vacBanned: Boolean = false,
    val tradeBan: String = "None",
    val limited: Boolean = false,
    val memberSince: String? = null,
    val location: String? = null,
    val avatarUrl: String? = null,

    // From the Steam Web API, when you have given Bunker a (free) key.
    val hasKeyData: Boolean = false,
    val accountCreatedMillis: Long? = null,
    val countryCode: String? = null,
    val rustHours: Double? = null,
    val rustHoursLast2Weeks: Double? = null,
    /** With a key: the player keeps their game list private, so there is no number to show. */
    val gamesHidden: Boolean = false,
    val vacBans: Int = 0,
    val gameBans: Int = 0,
    val daysSinceLastBan: Int? = null,
    val communityBanned: Boolean = false,
) {
    val isPublic: Boolean get() = privacy == "public"
}

/** A profile, or the reason there is none. [message] can also carry a note such as "Steam did not accept your key". */
data class SteamResult(val profile: SteamProfile?, val message: String = "")

/**
 * Looks a player up on Steam. The public community profile needs no key. With a free Steam Web API key (optional, set in
 * Settings) Bunker also gets their hours in Rust, how old the account is and the full ban record. Only the player's Steam ID
 * (and the key, to Steam) is sent, and only when a player card is opened. Answers are kept in memory for a few minutes so
 * reopening a card is instant. This mirrors the Windows app.
 */
object SteamLookup {
    const val RUST_APP_ID = 252490
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val KEEP_MILLIS = 10 * 60_000L
    private val cache = ConcurrentHashMap<String, Pair<Long, SteamResult>>()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** A Steam ID is 17 digits. Anything else is never sent anywhere. */
    fun isSteamId(id: String?): Boolean = Cmd.isSteamId(id)

    /** A Steam Web API key is 32 hex digits. */
    fun looksLikeKey(key: String?): Boolean = key != null && Regex("^[0-9A-Fa-f]{32}$").matches(key.trim())

    fun clearCache() = cache.clear()

    /** Test hook, only ever set in debug builds (see BunkerApp): answers with a made-up profile so a card can be shown without looking anyone up. */
    @Volatile var testHook: ((steamId: String, playerName: String?) -> SteamResult?)? = null

    /** A made-up profile for the test hook. [mode] is "plain" (public profile only), "key" (with hours and bans data) or "banned". */
    fun fake(steamId: String, mode: String, playerName: String? = null): SteamResult {
        val base = SteamProfile(
            steamId = steamId, personaName = playerName ?: "Ironclad", privacy = "public", memberSince = "March 3, 2014", location = "Netherlands",
        )
        return SteamResult(
            when (mode) {
                "key" -> base.copy(hasKeyData = true, accountCreatedMillis = 1393804800_000L, countryCode = "NL", rustHours = 2184.0, rustHoursLast2Weeks = 36.0)
                "banned" -> base.copy(
                    hasKeyData = true, accountCreatedMillis = 1530403200_000L, countryCode = "NL", rustHours = 312.0, vacBanned = true, vacBans = 1,
                    daysSinceLastBan = 420, limited = false, privacy = "public",
                )
                else -> base
            },
        )
    }

    /** [playerName] is only used by the debug test hook (so a made-up profile can carry the player's own name); real lookups ignore it. */
    suspend fun get(http: OkHttpClient, steamId: String, apiKey: String?, playerName: String? = null): SteamResult = withContext(Dispatchers.IO) {
        if (!isSteamId(steamId)) return@withContext SteamResult(null, "This player has no Steam ID to look up.")
        testHook?.invoke(steamId, playerName)?.let { return@withContext it }
        val useKey = looksLikeKey(apiKey)
        val cacheKey = steamId + if (useKey) "+key" else ""
        cache[cacheKey]?.let { (at, result) -> if (System.currentTimeMillis() - at < KEEP_MILLIS) return@withContext result }

        val result: SteamResult
        try {
            val xml = getText(http, "https://steamcommunity.com/profiles/$steamId/?xml=1")
            var profile = parseProfile(xml, steamId)
            if (profile == null) {
                result = SteamResult(null, "Steam has no public profile for this player.")
            } else {
                var note = ""
                if (useKey) {
                    try {
                        profile = withKeyData(http, profile, apiKey!!.trim())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: SteamHttpException) {
                        note = if (e.code == 401 || e.code == 403) "Steam did not accept your Steam key, so only the public profile is shown. Check it in Settings."
                        else "Steam's key lookups did not answer, so only the public profile is shown."
                    } catch (_: Exception) {
                        note = "Steam's key lookups did not answer, so only the public profile is shown."
                    }
                }
                result = SteamResult(profile, note)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext SteamResult(null, "Could not reach Steam: ${e.message ?: e.javaClass.simpleName}") // not kept, so the next try asks again
        }
        cache[cacheKey] = System.currentTimeMillis() to result
        result
    }

    private suspend fun withKeyData(http: OkHttpClient, profile: SteamProfile, key: String): SteamProfile = coroutineScope {
        val id = profile.steamId
        val k = URLEncoder.encode(key, "UTF-8")
        val summaries = async { getText(http, "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?key=$k&steamids=$id") }
        val bans = async { getText(http, "https://api.steampowered.com/ISteamUser/GetPlayerBans/v1/?key=$k&steamids=$id") }
        val games = async { getText(http, "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/?key=$k&steamid=$id&include_played_free_games=1&appids_filter%5B0%5D=$RUST_APP_ID") }
        applyKeyData(profile, summaries.await(), bans.await(), games.await())
    }

    class SteamHttpException(val code: Int) : Exception("HTTP $code")

    private fun getText(http: OkHttpClient, url: String): String {
        val client = http.newBuilder().callTimeout(12, java.util.concurrent.TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url(url).header("User-Agent", "Bunker-PlayerCard").build()).execute().use { r ->
            if (!r.isSuccessful) throw SteamHttpException(r.code)
            val source = r.body.source()
            source.request(MAX_BYTES + 1L)
            if (source.buffer.size > MAX_BYTES) throw IllegalStateException("The reply was too large.")
            return source.buffer.readUtf8()
        }
    }

    /** Adds what the three Steam Web API answers say to a profile read from the community page. */
    fun applyKeyData(p: SteamProfile, summariesJson: String, bansJson: String, gamesJson: String): SteamProfile {
        var created: Long? = null
        var country: String? = null
        var vac = 0
        var gameBans = 0
        var days: Int? = null
        var community = false
        var vacBanned = p.vacBanned
        var trade = p.tradeBan
        var hours: Double? = null
        var twoWeeks: Double? = null
        var hidden = false

        try {
            val player = json.parseToJsonElement(summariesJson).jsonObject["response"]?.jsonObject?.get("players")?.jsonArray?.firstOrNull() as? JsonObject
            if (player != null) {
                (player["timecreated"] as? JsonPrimitive)?.longOrNull?.let { created = it * 1000 }
                country = (player["loccountrycode"] as? JsonPrimitive)?.contentOrNull
            }
        } catch (_: Exception) { }

        try {
            val b = json.parseToJsonElement(bansJson).jsonObject["players"]?.jsonArray?.firstOrNull() as? JsonObject
            if (b != null) {
                community = (b["CommunityBanned"] as? JsonPrimitive)?.booleanOrNull == true
                vacBanned = (b["VACBanned"] as? JsonPrimitive)?.booleanOrNull ?: vacBanned
                vac = (b["NumberOfVACBans"] as? JsonPrimitive)?.intOrNull ?: 0
                gameBans = (b["NumberOfGameBans"] as? JsonPrimitive)?.intOrNull ?: 0
                days = (b["DaysSinceLastBan"] as? JsonPrimitive)?.intOrNull
                (b["EconomyBan"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { trade = it }
            }
        } catch (_: Exception) { }

        try {
            val response = json.parseToJsonElement(gamesJson).jsonObject["response"]?.jsonObject
            // Steam answers with an empty response when the player keeps their game list private.
            if (response == null || !response.containsKey("game_count")) hidden = true
            else (response["games"] as? JsonArray)?.forEach { g ->
                val o = g as? JsonObject ?: return@forEach
                if ((o["appid"] as? JsonPrimitive)?.intOrNull == RUST_APP_ID) {
                    (o["playtime_forever"] as? JsonPrimitive)?.doubleOrNull?.let { hours = it / 60.0 }
                    (o["playtime_2weeks"] as? JsonPrimitive)?.doubleOrNull?.let { twoWeeks = it / 60.0 }
                }
            }
        } catch (_: Exception) { }

        return p.copy(
            vacBanned = vacBanned, tradeBan = trade, hasKeyData = true,
            accountCreatedMillis = created, countryCode = country, rustHours = hours, rustHoursLast2Weeks = twoWeeks, gamesHidden = hidden,
            vacBans = vac, gameBans = gameBans, daysSinceLastBan = days, communityBanned = community,
        )
    }

    /** Only pictures from Steam's own picture servers are fetched, whatever the profile says. */
    fun isAvatarAddress(url: String?): Boolean {
        if (url == null) return false
        val uri = try { URI(url) } catch (_: Exception) { return false }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return uri.scheme == "https" && (host.endsWith(".steamstatic.com") || host.endsWith(".akamaihd.net"))
    }

    /** Reads the profile page Steam serves with ?xml=1. Returns null for a profile that does not exist or text that is not a profile. */
    fun parseProfile(xml: String, steamId: String): SteamProfile? {
        if (xml.length > MAX_BYTES || xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) return null
        val root: Element = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isExpandEntityReferences = false
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
        } catch (_: Exception) {
            return null
        } ?: return null
        if (root.tagName != "profile") return null

        fun text(name: String): String? {
            var n: Node? = root.firstChild
            while (n != null) {
                if (n is Element && n.tagName == name) return n.textContent.trim().ifEmpty { null }
                n = n.nextSibling
            }
            return null
        }

        val persona = decodeEntities(text("steamID") ?: return null)
        return SteamProfile(
            steamId = text("steamID64") ?: steamId,
            personaName = persona,
            privacy = text("privacyState") ?: "private",
            vacBanned = text("vacBanned") == "1",
            tradeBan = text("tradeBanState") ?: "None",
            limited = text("isLimitedAccount") == "1",
            memberSince = text("memberSince"),
            location = text("location")?.let { decodeEntities(it) },
            avatarUrl = text("avatarFull") ?: text("avatarMedium") ?: text("avatarIcon"),
        )
    }

    private val entity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|lt|gt|amp|quot|apos);")

    /**
     * Steam sends names with HTML codes inside the XML ("Kirby &lt;3" for "Kirby <3"), which the XML reader leaves as they are.
     * One pass only, so "&amp;lt;" becomes "&lt;" and not "<".
     */
    fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        return entity.replace(s) { m ->
            when (val v = m.groupValues[1]) {
                "lt" -> "<"
                "gt" -> ">"
                "amp" -> "&"
                "quot" -> "\""
                "apos" -> "'"
                else -> try {
                    val cp = if (v.startsWith("#x")) v.substring(2).toInt(16) else v.substring(1).toInt()
                    if (Character.isValidCodePoint(cp) && cp > 0) String(Character.toChars(cp)) else m.value
                } catch (_: NumberFormatException) {
                    m.value
                }
            }
        }
    }

    // ---------------------------------------------------------------- the words on the card

    /** "Public", "Friends only" or "Private". */
    fun privacyText(p: SteamProfile): String = when (p.privacy) { "public" -> "Public"; "friendsonly" -> "Friends only"; else -> "Private" }

    /** The bans line: what Steam says, in plain words, and whether it is bad news. */
    fun bans(p: SteamProfile): Pair<String, Boolean> {
        val parts = ArrayList<String>()
        if (p.vacBanned || p.vacBans > 0) parts += if (p.vacBans > 1) "${p.vacBans} VAC bans" else "VAC ban"
        if (p.gameBans > 0) parts += if (p.gameBans > 1) "${p.gameBans} game bans" else "Game ban"
        if (p.communityBanned) parts += "Community ban"
        if (p.tradeBan.isNotBlank() && !p.tradeBan.equals("None", ignoreCase = true)) parts += "Trade ban: ${p.tradeBan}"
        if (parts.isEmpty()) return "None on record" to false
        var text = parts.joinToString(", ")
        val d = p.daysSinceLastBan
        if (d != null && (p.vacBanned || p.gameBans > 0 || p.vacBans > 0)) text += " (${String.format(Locale.US, "%,d", d)} days ago)"
        return text to true
    }

    /** Hours in Rust as a card line, or why there are none. */
    fun rustLine(p: SteamProfile, haveKey: Boolean): String {
        p.rustHours?.let { h ->
            val w = p.rustHoursLast2Weeks
            return String.format(Locale.US, "%,.0f hours", h) + if (w != null && w >= 0.5) String.format(Locale.US, "  (%,.0f in last 2 weeks)", w) else ""
        }
        if (!haveKey) return "Needs a Steam key (Settings)"
        if (p.gamesHidden) return "Hidden (the player keeps this private)"
        return "None on record"
    }

    /** When the Steam account was made: the exact date with a key, or the date Steam's public page shows. */
    fun accountAge(p: SteamProfile, nowMillis: Long = System.currentTimeMillis()): String {
        p.accountCreatedMillis?.let { ms ->
            val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
            return fmt.format(Date(ms)) + "  (" + yearsText(nowMillis - ms) + ")"
        }
        return p.memberSince ?: "Hidden"
    }

    fun yearsText(ageMillis: Long): String {
        val days = ageMillis / 86_400_000.0
        val years = (days / 365.25).toInt()
        if (years >= 1) return if (years == 1) "1 year old" else "$years years old"
        val months = (days / 30.44).toInt()
        return if (months >= 1) (if (months == 1) "1 month old" else "$months months old") else "under a month old"
    }
}
