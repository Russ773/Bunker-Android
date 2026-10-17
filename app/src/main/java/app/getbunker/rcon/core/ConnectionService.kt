package app.getbunker.rcon.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.getbunker.rcon.BunkerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Keeps the process (and so the RCON connection) alive while the screen is off or another app is in front. It only shows a
 * quiet ongoing notification; it is started when you open a server and stopped when you leave it.
 */
class ConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = (application as BunkerApp).container
        if (intent?.action == ACTION_STOP) {
            app.sessions.close()
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this, Notifier.ID_CONNECTION,
            app.notifier.connectionNotification("Bunker", "Starting..."),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )

        scope.launch {
            app.sessions.session.flatMapLatest { s ->
                if (s == null) flowOf(null)
                else combine(s.state, s.status, s.info) { state, status, info ->
                    val title = s.profile.displayName
                    val text = when (state) {
                        ConnState.Connected -> info?.let { "${it.players}/${it.maxPlayers} players  ·  ${it.fps.toInt()} FPS" } ?: "Connected"
                        ConnState.Connecting -> "Connecting..."
                        ConnState.Disconnected -> status
                    }
                    title to text
                }
            }.collect { pair ->
                if (pair == null) stopSelf() else app.notifier.updateConnection(pair.first, pair.second)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "app.getbunker.rcon.STOP"

        fun start(context: Context) {
            try { context.startForegroundService(Intent(context, ConnectionService::class.java)) } catch (_: Exception) { /* not allowed to start from here */ }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }
}
