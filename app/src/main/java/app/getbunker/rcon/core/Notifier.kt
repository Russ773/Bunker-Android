package app.getbunker.rcon.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.getbunker.rcon.MainActivity
import app.getbunker.rcon.R

/** Notifications: the ongoing "connected" one that keeps the app alive, and alerts while the app is in the background. */
class Notifier(private val context: Context, private val app: AppContainer) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        manager.createNotificationChannel(
            NotificationChannel(CH_CONNECTION, context.getString(R.string.channel_connection), NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while Bunker stays connected to your server in the background."
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CH_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Lost connections, chat watch-words, server restarts, low FPS and sudden drops in players."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CH_WATCH, context.getString(R.string.channel_watchlist), NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When someone on your watch list joins a server."
            }
        )
    }

    private fun servicePending(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        context, requestCode, Intent(context, ConnectionService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The ongoing notification for the foreground service. */
    fun connectionNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(context, CH_CONNECTION)
            .setSmallIcon(R.drawable.ic_stat_bunker)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp())
            .addAction(0, "Save world", servicePending(ConnectionService.ACTION_SAVE, 11))
            .addAction(0, "Disconnect", servicePending(ConnectionService.ACTION_STOP, 12))
            .build()

    fun updateConnection(title: String, text: String) {
        if (!canPost()) return
        manager.notify(ID_CONNECTION, connectionNotification(title, text))
    }

    /** Called for events from the open session. Notifications only appear when the app is not on screen. */
    fun onEvent(session: ServerSession, event: SessionEvent) {
        if (app.inForeground.value || !canPost()) return
        val s = app.settings.current
        val server = session.profile.displayName
        when (event) {
            SessionEvent.ConnectionLost -> if (s.notifyConnectionLost) alert(ID_LOST, "Connection lost", "Lost the connection to $server.")
            SessionEvent.Reconnected -> manager.cancel(ID_LOST)
            is SessionEvent.ChatWatch -> if (s.notifyWatchWords) alert(ID_CHAT, "${event.name} on $server", event.message)
            is SessionEvent.PlayerJoined -> if (s.notifyJoins) alert(ID_JOIN, "$server", "${event.name} joined")
            // The session only sends these when the matching alert is switched on in Settings.
            is SessionEvent.Restarted -> AlertText.of(server, event)?.let { alert(ID_RESTART, it.first, it.second) }
            is SessionEvent.LowFps -> AlertText.of(server, event)?.let { alert(ID_FPS, it.first, it.second) }
            is SessionEvent.PlayerDrop -> AlertText.of(server, event)?.let { alert(ID_DROP, it.first, it.second) }
            is SessionEvent.WatchedJoined -> AlertText.of(server, event)?.let { alert(ID_WATCH + (event.steamId.takeLast(3).toIntOrNull() ?: 0), it.first, it.second, CH_WATCH) }
        }
    }

    private fun alert(id: Int, title: String, text: String, channel: String = CH_ALERTS) {
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_bunker)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        manager.notify(id, n)
    }

    companion object {
        const val CH_CONNECTION = "connection"
        const val CH_ALERTS = "alerts"
        const val CH_WATCH = "watchlist"
        const val ID_CONNECTION = 1
        const val ID_LOST = 2
        const val ID_CHAT = 3
        const val ID_JOIN = 4
        const val ID_RESTART = 5
        const val ID_FPS = 6
        const val ID_DROP = 7
        const val ID_WATCH = 1000 // plus the last three digits of the Steam ID, so two watched players do not replace each other
    }
}
