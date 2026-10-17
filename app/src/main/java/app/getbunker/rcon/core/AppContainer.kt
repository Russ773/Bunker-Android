package app.getbunker.rcon.core

import android.content.Context
import app.getbunker.rcon.data.SettingsStore
import app.getbunker.rcon.rcon.RconClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** The few long-lived objects the whole app shares. Created once by [app.getbunker.rcon.BunkerApp]. */
class AppContainer(val context: Context) {
    val http: OkHttpClient = RconClient.defaultHttp
    val settings = SettingsStore(context)
    val items = ItemCatalog(context, http) { settings.current.useOnlineItemList }
    val notifier = Notifier(context, this)
    val sessions = SessionManager(this)

    /** For work that must outlive the screen that started it (such as waiting for the server's reply to a kick). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _inForeground = MutableStateFlow(true)

    /** True while any of the app's screens is visible. Polling slows down and alerts become notifications when it is false. */
    val inForeground: StateFlow<Boolean> = _inForeground

    fun setForeground(value: Boolean) {
        _inForeground.value = value
    }

    fun start() {
        scope.launch { items.refresh() }
    }
}

/** Holds the one open server session and keeps the foreground service in step with it. */
class SessionManager(private val app: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _session = MutableStateFlow<ServerSession?>(null)
    val session: StateFlow<ServerSession?> = _session
    private var alertsJob: kotlinx.coroutines.Job? = null

    /** Opens a session for a saved server and connects. If it is already the open one, it is reused. */
    fun open(serverId: String, connect: Boolean = true): ServerSession? {
        val existing = _session.value
        if (existing != null && existing.profile.id == serverId) {
            val fresh = app.settings.current.servers.firstOrNull { it.id == serverId }
            // The server was edited since: start over so the new address or password is used.
            if (fresh == null || fresh == existing.profile) {
                if (connect && existing.state.value == ConnState.Disconnected && !existing.reconnecting.value) existing.connect()
                return existing
            }
        }
        val profile = app.settings.current.servers.firstOrNull { it.id == serverId } ?: return null
        close()
        app.settings.setActive(serverId)
        val s = ServerSession(profile, app)
        _session.value = s
        alertsJob = scope.launch { s.events.collect { app.notifier.onEvent(s, it) } }
        if (connect) s.connect()
        if (app.settings.current.keepConnected) ConnectionService.start(app.context)
        return s
    }

    fun close() {
        alertsJob?.cancel()
        _session.value?.close()
        _session.value = null
        ConnectionService.stop(app.context)
    }
}
