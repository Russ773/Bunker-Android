package app.getbunker.rcon.core

import android.content.Context
import app.getbunker.rcon.data.BunkerJson
import app.getbunker.rcon.data.PlayerRecord
import app.getbunker.rcon.rcon.PlayerInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import java.io.File

data class ActivityEvent(val timeMillis: Long, val joined: Boolean, val name: String, val steamId: String)

/**
 * Remembers every player seen on a server (first/last seen, time played, sessions, your notes) and notices joins and
 * leaves by comparing each player list with the last one. Saved on the phone, one file per server.
 */
class PlayerTracker(private val serverId: String, context: Context) {
    private val file = File(File(context.filesDir, "players"), "$serverId.json")

    private val _records = MutableStateFlow(load())
    val records: StateFlow<Map<String, PlayerRecord>> = _records

    private val _online = MutableStateFlow<Set<String>>(emptySet())
    val online: StateFlow<Set<String>> = _online

    private val _events = MutableStateFlow<List<ActivityEvent>>(emptyList())
    val events: StateFlow<List<ActivityEvent>> = _events

    private val _joins = MutableSharedFlow<ActivityEvent>(extraBufferCapacity = 32)
    val activity: SharedFlow<ActivityEvent> = _joins

    private var baseline = true
    private var lastPollMillis = System.currentTimeMillis()
    private var lastSaveMillis = System.currentTimeMillis()
    private var dirty = false

    private fun load(): Map<String, PlayerRecord> = try {
        if (file.exists()) BunkerJson.decodeFromString<List<PlayerRecord>>(file.readText()).filter { it.steamId.isNotEmpty() }.associateBy { it.steamId }
        else emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }

    fun save() {
        if (!dirty) return
        try {
            file.parentFile?.mkdirs()
            file.writeText(BunkerJson.encodeToString(_records.value.values.sortedBy { it.name.lowercase() }))
            dirty = false
            lastSaveMillis = System.currentTimeMillis()
        } catch (_: Exception) { }
    }

    fun onDisconnected() {
        _online.value = emptySet()
        baseline = true
        save()
    }

    @Synchronized
    fun update(players: List<PlayerInfo>) {
        val now = System.currentTimeMillis()
        val elapsed = if (baseline) 0.0 else minOf(30.0, (now - lastPollMillis) / 1000.0)
        lastPollMillis = now

        val current = players.filter { it.steamId.isNotEmpty() }
        val currentIds = current.map { it.steamId }.toSet()
        val records = _records.value.toMutableMap()
        val online = _online.value.toMutableSet()
        val newEvents = ArrayList<ActivityEvent>()

        for (p in current) {
            val isNew = p.steamId !in records
            var r = records[p.steamId] ?: PlayerRecord(steamId = p.steamId, firstSeenMillis = now)
            r = r.copy(
                name = p.name.ifBlank { r.name },
                lastSeenMillis = now,
                totalSeconds = r.totalSeconds + elapsed,
            )
            if (p.steamId !in online) {
                // Players already online when we connected are not "joins"; they just become known.
                if (!baseline) newEvents += ActivityEvent(now, true, r.name, p.steamId)
                if (!baseline || isNew) r = r.copy(sessions = r.sessions + 1)
                online += p.steamId
            }
            records[p.steamId] = r
            dirty = true
        }

        for (gone in online.filter { it !in currentIds }) {
            online -= gone
            records[gone]?.let { newEvents += ActivityEvent(now, false, it.name, gone) }
        }

        baseline = false
        _records.value = records
        _online.value = online
        if (newEvents.isNotEmpty()) {
            _events.update { (it + newEvents).takeLast(MAX_EVENTS) }
            newEvents.forEach { _joins.tryEmit(it) }
        }
        if (now - lastSaveMillis > 60_000) save()
    }

    /** Saves a note, creating the player's record if they have not been seen yet. */
    fun setNote(steamId: String, name: String, note: String) {
        val records = _records.value.toMutableMap()
        val r = records[steamId] ?: PlayerRecord(steamId = steamId, name = name, firstSeenMillis = System.currentTimeMillis())
        if (r.note == note && steamId in records) return
        records[steamId] = r.copy(note = note)
        _records.value = records
        dirty = true
        save()
    }

    fun noteFor(steamId: String): String = _records.value[steamId]?.note.orEmpty()

    companion object {
        const val MAX_EVENTS = 500
    }
}
