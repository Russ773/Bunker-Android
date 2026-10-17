package app.getbunker.rcon.core

import android.content.Context
import app.getbunker.rcon.data.BunkerJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class ItemInfo(val shortname: String, val name: String, val category: String)

/**
 * The list of Rust items (name, shortname, category) and their pictures. It comes from the open
 * bobosneefdev/rust-items-json project, which re-reads the game's own files whenever Rust updates. A copy is kept on the
 * phone and refreshed at most once a day; a list built into the app is used when offline.
 */
class ItemCatalog(private val context: Context, private val http: OkHttpClient, private val online: () -> Boolean) {
    private val folder = File(context.filesDir, "items")
    private val safe = Regex("^[A-Za-z0-9._-]{1,80}$")

    private val _items = MutableStateFlow(loadCached() ?: loadBuiltIn())
    val items: StateFlow<List<ItemInfo>> = _items

    fun iconUrl(shortname: String): String? = if (safe.matches(shortname)) "${DATA_URL}icons/$shortname.webp" else null

    private fun parse(json: String): List<ItemInfo>? = try {
        val arr = BunkerJson.parseToJsonElement(json) as? JsonArray ?: return null
        val list = arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            if ((o["hidden"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
            val short = (o["shortname"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (!safe.matches(short)) return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            ItemInfo(short, name.ifBlank { short }, (o["category"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }
        list.ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    private fun loadCached(): List<ItemInfo>? = try {
        val f = File(folder, "items.json")
        if (f.exists()) parse(f.readText()) else null
    } catch (_: Exception) {
        null
    }

    private fun loadBuiltIn(): List<ItemInfo> = try {
        parse(context.assets.open("items-fallback.json").bufferedReader().use { it.readText() }) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** Checks the project's small build file; if Rust has been updated since the saved copy, downloads the new list. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (!online()) return@withContext
        try {
            val stamp = File(folder, "checked.txt")
            if (stamp.exists() && System.currentTimeMillis() - stamp.lastModified() < 20 * 3600_000L) return@withContext
            folder.mkdirs()
            val meta = get(DATA_URL + "meta.json")
            val latest = (BunkerJson.parseToJsonElement(meta).jsonObject["build"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val buildFile = File(folder, "build.txt")
            val have = if (buildFile.exists()) buildFile.readText().trim() else ""
            if (latest.isNotEmpty() && (latest != have || !File(folder, "items.json").exists())) {
                val json = get(DATA_URL + "items.json")
                parse(json)?.let {
                    File(folder, "items.json").writeText(json)
                    buildFile.writeText(latest)
                    _items.value = it
                }
            }
            stamp.writeText(System.currentTimeMillis().toString())
        } catch (_: Exception) { /* offline or GitHub unreachable: keep using what we have */ }
    }

    private fun get(url: String): String {
        http.newCall(Request.Builder().url(url).header("User-Agent", "Bunker-Android-Items").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return r.body.string()
        }
    }

    fun find(shortname: String): ItemInfo? = _items.value.firstOrNull { it.shortname.equals(shortname.trim(), ignoreCase = true) }

    /** Items matching what was typed, best matches first: exact, then names that start with it, then names or shortnames that contain it. */
    fun search(query: String, take: Int = 30): List<ItemInfo> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        fun score(i: ItemInfo): Int = when {
            i.shortname.equals(q, true) || i.name.equals(q, true) -> 0
            i.name.startsWith(q, true) -> 1
            i.shortname.startsWith(q, true) -> 2
            // "ak" finds rifle.ak: a whole piece of the shortname (between dots) matches
            i.shortname.split('.').any { it.equals(q, true) } -> 2
            i.name.contains(q, true) -> 3
            i.shortname.contains(q, true) -> 4
            i.category.contains(q, true) -> 5
            else -> -1
        }
        return _items.value.map { it to score(it) }.filter { it.second >= 0 }
            .sortedWith(compareBy({ it.second }, { it.first.name.length }, { it.first.name.lowercase() }))
            .take(take).map { it.first }
    }

    companion object {
        const val DATA_URL = "https://raw.githubusercontent.com/bobosneefdev/rust-items-json/master/data/"
    }
}
