package app.getbunker.rcon.core

import app.getbunker.rcon.data.ServerProfile
import app.getbunker.rcon.rcon.PlayerInfo
import app.getbunker.rcon.rcon.RconClient
import app.getbunker.rcon.rcon.RconParsers
import app.getbunker.rcon.rcon.ServerInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

enum class WatchState { Idle, Connecting, Online, Offline }

data class WatchSnapshot(
    val state: WatchState = WatchState.Idle,
    /** Why the server is offline, and when the next try is. */
    val detail: String = "",
    val info: ServerInfo? = null,
    val players: List<PlayerInfo> = emptyList(),
    /** The player count at each recent poll, oldest first (for the little chart on a card). */
    val history: List<Int> = emptyList(),
)

/**
 * Keeps one saved server's status up to date over its own connection, separate from the session you are working in, so the
 * overview can show every server at once. It polls `serverinfo` and `playerlist`, and reconnects by itself when a server
 * goes away and comes back. Start it when the overview opens and stop it when it closes.
 */
class ServerWatcher(val profile: ServerProfile, private val password: String, http: OkHttpClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rcon = RconClient(http)
    private val history = ArrayList<Int>()
    private var job: Job? = null

    private val _state = MutableStateFlow(WatchSnapshot())
    val state: StateFlow<WatchSnapshot> = _state

    fun start() {
        if (job != null) return
        job = scope.launch { run() }
    }

    private fun set(state: WatchState, detail: String = "") {
        _state.value = _state.value.copy(state = state, detail = detail)
    }

    private suspend fun run() {
        var attempt = 0
        while (scope.isActive) {
            set(WatchState.Connecting)
            var reason: String
            try {
                rcon.connect(profile.host, profile.port, password, profile.useTls, timeoutMs = 8_000)
                attempt = 0
                var failures = 0
                while (scope.isActive && rcon.isConnected) {
                    if (poll()) failures = 0 else failures++
                    if (failures >= 3) { rcon.disconnect(); break }
                    delay(POLL_MS)
                }
                reason = "Connection lost"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reason = shorten(e.message)
            }

            _state.value = _state.value.copy(info = null, players = emptyList())
            val wait = ReconnectPlan.delaySeconds(++attempt)
            for (left in wait downTo 1) {
                set(WatchState.Offline, withRetry(reason, left))
                delay(1000)
            }
        }
    }

    private suspend fun poll(): Boolean = try {
        val info = RconParsers.parseInfo(rcon.query("serverinfo", 6000))
        val players = RconParsers.parsePlayers(rcon.query("playerlist", 6000)) ?: emptyList()
        if (info == null) false
        else {
            history += info.players
            if (history.size > HISTORY) history.removeAt(0)
            _state.value = WatchSnapshot(WatchState.Online, "", info, players, history.toList())
            true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /** Runs a command on this server. Returns the server's reply, or null if it could not be sent. */
    suspend fun send(command: String): String? {
        if (_state.value.state != WatchState.Online || !rcon.isConnected) return null
        return try { rcon.query(command, 8000) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    fun stop() {
        job?.cancel()
        job = null
        rcon.disconnect()
        scope.cancel()
    }

    companion object {
        const val POLL_MS = 10_000L
        const val HISTORY = 60

        fun shorten(message: String?): String {
            val first = (message ?: "").trim().lineSequence().firstOrNull()?.trim().orEmpty()
            if (first.isEmpty()) return "Could not connect"
            return if (first.length <= 90) first else first.take(89) + "…"
        }

        /** "Connection refused..." plus when the next try is, without doubling up the full stop. */
        fun withRetry(reason: String, seconds: Int): String {
            val r = reason.trimEnd()
            return (if (r.endsWith('?') || r.endsWith('.') || r.endsWith('!')) "$r " else "$r. ") + "Trying again in ${seconds}s"
        }

        /** The line above the cards, such as "3 servers · 2 online · 41 players". */
        fun summary(servers: Int, online: Int, players: Int): String {
            if (servers == 0) return "No servers saved yet"
            val parts = arrayListOf(if (servers == 1) "1 server" else "$servers servers", "$online online")
            if (online > 0) parts += if (players == 1) "1 player" else "$players players"
            return parts.joinToString("  ·  ")
        }

        /** Servers as a short list for a confirmation message: "A, B and C". */
        fun nameList(names: List<String>): String = when (names.size) {
            0 -> ""
            1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        }

        /** Who is on, as a short line for a card: "Ironclad, RustLord and 3 more". */
        fun playerLine(names: List<String>, show: Int = 3): String {
            val clean = names.filter { it.isNotBlank() }
            if (clean.isEmpty()) return ""
            return if (clean.size <= show) clean.joinToString(", ") else clean.take(show).joinToString(", ") + " and ${clean.size - show} more"
        }
    }
}
