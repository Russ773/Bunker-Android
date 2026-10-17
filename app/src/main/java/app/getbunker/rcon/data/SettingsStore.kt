package app.getbunker.rcon.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

internal val BunkerJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    isLenient = true
}

/** App settings kept in one JSON file in the app's private storage. Every change is saved straight away. */
class SettingsStore(context: Context) {
    private val file = File(context.filesDir, "settings.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    val current: AppSettings get() = _state.value

    private fun load(): AppSettings = try {
        if (file.exists()) BunkerJson.decodeFromString<AppSettings>(file.readText()) else AppSettings()
    } catch (_: Exception) {
        AppSettings()
    }

    /** Applies a change and saves it. */
    fun update(block: (AppSettings) -> AppSettings) {
        _state.update(block)
        val snapshot = _state.value
        scope.launch {
            writeLock.withLock {
                try {
                    val tmp = File(file.parentFile, "settings.json.tmp")
                    tmp.writeText(BunkerJson.encodeToString(snapshot))
                    if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
                } catch (_: Exception) { /* a failed save is retried on the next change */ }
            }
        }
    }

    // ---- servers

    fun password(server: ServerProfile): String = SecretBox.decrypt(server.passwordEnc)

    fun saveServer(server: ServerProfile, password: String?, makeActive: Boolean = false): ServerProfile {
        val saved = if (password != null) server.copy(passwordEnc = SecretBox.encrypt(password)) else server
        update { s ->
            val i = s.servers.indexOfFirst { it.id == saved.id }
            val list = if (i >= 0) s.servers.toMutableList().also { it[i] = saved } else s.servers + saved
            s.copy(servers = list, activeServerId = if (makeActive || s.activeServerId == null) saved.id else s.activeServerId)
        }
        return saved
    }

    fun deleteServer(id: String) = update { s ->
        val list = s.servers.filterNot { it.id == id }
        s.copy(servers = list, activeServerId = if (s.activeServerId == id) list.firstOrNull()?.id else s.activeServerId)
    }

    fun setActive(id: String) = update { it.copy(activeServerId = id) }

    // ---- console helpers

    fun addHideRule(kind: String, value: String): Boolean {
        val v = ConsoleFilterRules.clean(kind, value)
        if (v.isEmpty()) return false
        if (current.hidden.any { it.kind == kind && it.value.equals(v, ignoreCase = true) }) return false
        update { it.copy(hidden = it.hidden + HideRule(kind, v)) }
        return true
    }

    fun removeHideRule(rule: HideRule) = update { it.copy(hidden = it.hidden - rule) }

    fun rememberCommand(command: String) {
        val c = command.trim()
        if (c.isEmpty()) return
        update { s -> s.copy(commandHistory = (listOf(c) + s.commandHistory.filterNot { it == c }).take(60)) }
    }

    fun confirmCommandWord(word: String) = update { s ->
        if (word in s.confirmedCommands) s else s.copy(confirmedCommands = (s.confirmedCommands + word).takeLast(200))
    }
}

/** Cleaning for hide rules, kept here so the settings store does not depend on the rcon package. */
object ConsoleFilterRules {
    const val MAX_LENGTH = 80

    fun clean(kind: String, value: String): String {
        var v = value.filter { !it.isISOControl() }.trim()
        if (kind == HideRule.KIND_PLUGIN) v = v.trim('[', ']', ' ')
        return if (v.length > MAX_LENGTH) v.take(MAX_LENGTH) else v
    }
}
