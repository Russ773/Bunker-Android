package app.getbunker.rcon.core

import app.getbunker.rcon.data.AppSettings
import app.getbunker.rcon.data.ScheduledTask
import app.getbunker.rcon.data.ServerProfile
import app.getbunker.rcon.rcon.ChatText
import app.getbunker.rcon.rcon.Cmd
import app.getbunker.rcon.rcon.HealthSample
import app.getbunker.rcon.rcon.LogKind
import app.getbunker.rcon.rcon.PlayerInfo
import app.getbunker.rcon.rcon.RconClient
import app.getbunker.rcon.rcon.RconMessage
import app.getbunker.rcon.rcon.RconParsers
import app.getbunker.rcon.rcon.ServerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

enum class ConnState { Disconnected, Connecting, Connected }

sealed interface SessionEvent {
    data object ConnectionLost : SessionEvent
    data object Reconnected : SessionEvent
    data class ChatWatch(val name: String, val message: String) : SessionEvent
    data class PlayerJoined(val name: String) : SessionEvent
}

/** How long to wait between attempts to reconnect after the connection to the server drops. */
object ReconnectPlan {
    private val seconds = intArrayOf(3, 5, 10, 20, 30)
    const val LONGEST = 60
    fun delaySeconds(attempt: Int): Int = if (attempt in 1..seconds.size) seconds[attempt - 1] else LONGEST
    fun shouldLogFailure(attempt: Int): Boolean = attempt <= 3 || attempt % 10 == 0
}

/**
 * One connection to one server, plus everything learned from it: console, player list, server info, health history,
 * the player database, and the scheduler. It lives as long as you stay on that server, including while the app is in the
 * background (the foreground service keeps the process alive).
 */
class ServerSession(val profile: ServerProfile, private val app: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rcon = RconClient(app.http)
    private val settings get() = app.settings

    val log = ConsoleLog(scope)
    val tracker = PlayerTracker(profile.id, app.context)

    private val _state = MutableStateFlow(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state

    private val _status = MutableStateFlow("Disconnected")
    val status: StateFlow<String> = _status

    private val _info = MutableStateFlow<ServerInfo?>(null)
    val info: StateFlow<ServerInfo?> = _info

    private val _players = MutableStateFlow<List<PlayerInfo>>(emptyList())
    val players: StateFlow<List<PlayerInfo>> = _players

    private val _history = MutableStateFlow<List<HealthSample>>(emptyList())
    val history: StateFlow<List<HealthSample>> = _history

    /** True while we are waiting to retry after the connection dropped. */
    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<SessionEvent> = _events

    /** Names of schedules that are running right now. */
    private val _runningSchedules = MutableStateFlow<Set<String>>(emptySet())
    val runningSchedules: StateFlow<Set<String>> = _runningSchedules

    private var monitorJob: Job? = null
    private var reconnectJob: Job? = null
    private var schedulerJob: Job? = null
    private var userStopped = false
    private var connectedAtMillis = System.currentTimeMillis()
    private var chatPluginMissing = false
    private val refreshLock = Mutex()
    private val connectLock = Mutex()

    val isConnected: Boolean get() = _state.value == ConnState.Connected

    init {
        rcon.onMessage = { m -> onMessage(m) }
        rcon.onClosed = { reason -> scope.launch { onClosed(reason) } }
        scope.launch { tracker.activity.collect { if (it.joined) _events.tryEmit(SessionEvent.PlayerJoined(it.name)) } }
    }

    // ------------------------------------------------------------------ connection

    /** Connects (or reconnects). Doing this yourself ends any automatic reconnecting. */
    fun connect() {
        scope.launch {
            cancelReconnect()
            userStopped = false
            connectCore(logFailure = true)
        }
    }

    fun disconnect() {
        userStopped = true
        cancelReconnect()
        stopLoops()
        rcon.disconnect()
        tracker.onDisconnected()
        setState(ConnState.Disconnected, "Disconnected")
    }

    /** Ends the session for good (leaving the server screen). */
    fun close() {
        disconnect()
        tracker.save()
        scope.cancel()
    }

    private suspend fun connectCore(logFailure: Boolean): Boolean = connectLock.withLock {
        if (_state.value == ConnState.Connected) return true
        setState(ConnState.Connecting, "Connecting...")
        try {
            rcon.connect(profile.host, profile.port, settings.password(profile), profile.useTls)
            log.add("Connected to ${profile.displayName} (${profile.address})", LogKind.Info)
            connectedAtMillis = System.currentTimeMillis()
            setState(ConnState.Connected, "Connected")
            startLoops()
            true
        } catch (e: Exception) {
            if (logFailure) log.add("Connection failed: ${e.message}", LogKind.Error)
            setState(ConnState.Disconnected, "Connection failed: ${e.message}")
            false
        }
    }

    private suspend fun onClosed(reason: String) {
        val wasConnected = _state.value == ConnState.Connected
        stopLoops()
        tracker.onDisconnected()
        log.add(reason, LogKind.Info)
        setState(ConnState.Disconnected, reason)
        if (wasConnected && !userStopped) {
            _events.tryEmit(SessionEvent.ConnectionLost)
            startReconnect()
        }
    }

    private fun startReconnect() {
        if (!settings.current.autoReconnect || reconnectJob != null) return
        _reconnecting.value = true
        reconnectJob = scope.launch {
            try {
                var attempt = 1
                while (isActive) {
                    var left = ReconnectPlan.delaySeconds(attempt)
                    while (left > 0) {
                        setState(ConnState.Disconnected, "Reconnecting in ${left}s (attempt $attempt)")
                        delay(1000)
                        left--
                    }
                    if (attempt == 1) log.add("Connection lost. Trying to reconnect automatically (tap Disconnect to stop).", LogKind.Warning)
                    connectCore(logFailure = false)
                    if (_state.value == ConnState.Connected) {
                        log.add("Reconnected.", LogKind.Info)
                        _events.tryEmit(SessionEvent.Reconnected)
                        return@launch
                    }
                    if (ReconnectPlan.shouldLogFailure(attempt)) log.add("Still unable to reach the server (attempt $attempt). Trying again...", LogKind.Info)
                    attempt++
                }
            } finally {
                reconnectJob = null
                _reconnecting.value = false
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        _reconnecting.value = false
    }

    private fun setState(state: ConnState, text: String) {
        _state.value = state
        _status.value = text
        if (state != ConnState.Connected) {
            _info.value = null
            _players.value = emptyList()
        }
    }

    // ------------------------------------------------------------------ monitor & scheduler

    private fun startLoops() {
        stopLoops()
        monitorJob = scope.launch {
            var failures = 0
            while (isActive) {
                val ok = refresh()
                failures = if (ok) 0 else failures + 1
                if (failures >= 3 && isConnected) {
                    // Nothing has answered for a while: the connection is dead even though no error was reported.
                    rcon.disconnect()
                    launch { onClosed("Connection lost: the server stopped answering") }
                    return@launch
                }
                delay(if (app.inForeground.value) POLL_FOREGROUND_MS else POLL_BACKGROUND_MS)
            }
        }
        schedulerJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (isConnected) runDueSchedules()
            }
        }
    }

    private fun stopLoops() {
        monitorJob?.cancel(); monitorJob = null
        schedulerJob?.cancel(); schedulerJob = null
    }

    /** Reads server info and the player list now. Returns false if the server did not answer. */
    suspend fun refresh(): Boolean = refreshLock.withLock {
        if (!rcon.isConnected) return false
        try {
            val info = RconParsers.parseInfo(rcon.query("serverinfo"))
            val players = RconParsers.parsePlayers(rcon.query("playerlist"))
            if (info != null) {
                _info.value = info
                _history.value = (_history.value + HealthSample(System.currentTimeMillis(), info.players, info.fps, info.entities, info.memoryMb, info.uptimeSeconds)).takeLast(MAX_SAMPLES)
            }
            if (players != null) {
                _players.value = players
                if (info != null) tracker.update(players)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun runDueSchedules() {
        for (task in settings.current.schedules) {
            if (task.id in _runningSchedules.value || !isDue(task)) continue
            scope.launch { runSchedule(task) }
        }
    }

    fun nextRun(task: ScheduledTask): Long? = ScheduleMath.nextRun(task, connectedAtMillis)

    private fun isDue(t: ScheduledTask): Boolean = ScheduleMath.isDue(t, connectedAtMillis, System.currentTimeMillis())

    suspend fun runSchedule(task: ScheduledTask) {
        if (!_runningSchedules.value.let { it.contains(task.id).not() }) return
        _runningSchedules.value += task.id
        try {
            log.add("Schedule \"${task.name}\" is running...", LogKind.Info)
            for (raw in task.commands) {
                val c = raw.trim()
                if (c.isEmpty()) continue
                val wait = ScheduleMath.waitSeconds(c)
                if (wait != null) { delay(wait * 1000L); continue }
                if (!isConnected) return
                command(c)
            }
        } finally {
            _runningSchedules.value -= task.id
            settings.update { s -> s.copy(schedules = s.schedules.map { if (it.id == task.id) it.copy(lastRunMillis = System.currentTimeMillis()) else it }) }
        }
    }

    // ------------------------------------------------------------------ messages

    private fun onMessage(m: RconMessage) {
        val type = m.type.lowercase()
        if (type == "chat") {
            val c = RconParsers.parseChat(m.message)
            val e = log.add(c.text, LogKind.Chat, c.userId, c.name, c.message, c.color, c.channel, c.tags)
            checkWatchWords(e.userName ?: "", e.message ?: e.text)
            return
        }
        val kind = when (type) { "warning" -> LogKind.Warning; "error" -> LogKind.Error; else -> LogKind.Normal }
        log.add(m.message.trimEnd('\n'), kind)
    }

    private fun checkWatchWords(name: String, message: String) {
        val s = settings.current
        val words = watchWordList(s)
        if (words.isEmpty()) return
        if (words.any { message.contains(it, ignoreCase = true) }) _events.tryEmit(SessionEvent.ChatWatch(name.ifBlank { "SERVER" }, message))
    }

    // ------------------------------------------------------------------ sending

    /** Sends a console command and shows it in the console. The reply arrives in the console. */
    fun command(cmd: String, echo: Boolean = true) {
        if (!rcon.isConnected) return
        if (echo) log.add("> $cmd", LogKind.Command)
        try { rcon.send(cmd) } catch (e: Exception) { log.add("Send failed: ${e.message}", LogKind.Error) }
    }

    /**
     * Runs a command and waits briefly for the server's reply, so the screen can say what happened. The reply is also put in
     * the console. Returns the reply text, or null if the server said nothing in time.
     */
    suspend fun run(cmd: String, waitMs: Long = 2500): String? {
        if (!rcon.isConnected) return null
        log.add("> $cmd", LogKind.Command)
        return try {
            val reply = rcon.query(cmd, waitMs).trim()
            if (reply.isNotEmpty()) log.add(reply, LogKind.Normal)
            reply.ifEmpty { null }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Runs a command and returns the full reply without touching the console (used for lists such as bans). */
    suspend fun query(cmd: String, timeoutMs: Long = 8000): String = rcon.query(cmd, timeoutMs)

    /**
     * Sends a message to chat. With a chat name set it goes through the Bunker Chat plugin (your name, colour and avatar
     * in-game); if the plugin is not installed, or no name is set, the plain `say` command is used.
     */
    suspend fun sendChat(text: String) {
        if (!rcon.isConnected || text.isEmpty()) return
        val s = settings.current
        val name = s.chatName.trim()
        val colour = ChatSender.colourOrDefault(s.chatColor)

        if (name.isNotEmpty() && s.chatUsePlugin && !chatPluginMissing) {
            val command = ChatSender.pluginCommand(s.chatSteamId, name, colour, text)
            try {
                val reply = rcon.query(command, 6000)
                if (!ChatSender.looksUnknown(reply)) { echoChat(s, name, text, colour); return }
                chatPluginMissing = true
                log.add("The Bunker Chat plugin is not installed on this server, so chat is sent with the plain say command (it shows as SERVER). Settings > Chat explains how to install it.", LogKind.Warning)
            } catch (e: Exception) {
                log.add("Chat was not sent: ${e.message}", LogKind.Error)
                return
            }
        }
        command(ChatSender.sayCommand(name, text), echo = false)
        if (name.isNotEmpty()) echoChat(s, name, text, colour)
        else log.add("SERVER: $text", LogKind.Chat, null, "SERVER", text, "#eeeeee", 0)
    }

    private fun echoChat(s: AppSettings, name: String, text: String, colour: String) {
        val id = if (Cmd.isSteamId(s.chatSteamId)) s.chatSteamId else null
        log.add("$name: $text", LogKind.Chat, id, name, text, colour, 0)
    }

    companion object {
        const val POLL_FOREGROUND_MS = 5_000L
        const val POLL_BACKGROUND_MS = 30_000L
        const val MAX_SAMPLES = 1440 // two hours at one reading every 5 seconds

        fun watchWordList(s: AppSettings): List<String> =
            s.watchWords.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** Builds the commands that post chat, as you (name, Steam ID, colour) or as plain `say`. */
object ChatSender {
    const val DEFAULT_COLOUR = "#aaff55"

    fun isHex(c: String?): Boolean = c != null && Regex("^#?[0-9a-fA-F]{3}([0-9a-fA-F]{3})?$").matches(c.trim())

    fun colourOrDefault(colour: String?): String = if (isHex(colour)) "#" + colour!!.trim().trimStart('#') else DEFAULT_COLOUR

    /** `bunker.say steamid "name" "colour" "message"` for the Bunker Chat plugin. */
    fun pluginCommand(steamId: String, name: String, colour: String, message: String): String =
        "bunker.say ${if (Cmd.isSteamId(steamId)) steamId else "0"} ${Cmd.quote(name)} ${Cmd.quote(colourOrDefault(colour))} ${Cmd.quote(message)}"

    /** The plain command Rust has built in. It always posts as SERVER, so a name is added to the text when you have one. */
    fun sayCommand(name: String, message: String): String =
        if (name.isBlank()) "say " + Cmd.quote(ChatText.stripMarkup(message)) else "say " + Cmd.quote("${name.trim()}: $message")

    fun looksUnknown(reply: String): Boolean =
        reply.contains("unknown command", ignoreCase = true) || reply.contains("not found", ignoreCase = true)
}

/** Timing rules for schedules, kept separate so they can be tested. */
object ScheduleMath {
    private val waitRe = Regex("^wait\\s+(\\d{1,4})$", RegexOption.IGNORE_CASE)

    /** Seconds to pause for a `wait N` line, or null if the line is a normal command. */
    fun waitSeconds(command: String): Int? = waitRe.find(command.trim())?.groupValues?.get(1)?.toInt()?.coerceIn(1, 3600)

    fun parseTime(text: String): Pair<Int, Int>? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(text.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h to min else null
    }

    fun nextRun(t: ScheduledTask, connectedAt: Long, now: Long = System.currentTimeMillis()): Long? {
        if (!t.enabled) return null
        if (t.mode == ScheduledTask.MODE_DAILY) {
            val (h, m) = parseTime(t.timeOfDay) ?: return null
            val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
        return maxOf(t.lastRunMillis, connectedAt) + maxOf(1, t.intervalMinutes) * 60_000L
    }

    fun isDue(t: ScheduledTask, connectedAt: Long, now: Long): Boolean {
        if (!t.enabled) return false
        if (t.mode == ScheduledTask.MODE_DAILY) {
            val (h, m) = parseTime(t.timeOfDay) ?: return false
            val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            val target = cal.timeInMillis
            return now >= target && now < target + 15 * 60_000L && t.lastRunMillis < target && connectedAt < target + 15 * 60_000L
        }
        val next = nextRun(t, connectedAt, now) ?: return false
        return now >= next
    }
}
