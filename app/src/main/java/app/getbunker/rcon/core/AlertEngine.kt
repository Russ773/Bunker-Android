package app.getbunker.rcon.core

import app.getbunker.rcon.rcon.ServerInfo

/** Which alerts are switched on, taken from Settings each time a reading arrives. */
data class AlertConfig(
    val restart: Boolean = true,
    val lowFps: Boolean = false,
    val fpsBelow: Int = 20,
    val fpsMinutes: Int = 2,
    val playerDrop: Boolean = false,
)

/**
 * Watches the readings a session takes of the server and says when something is worth a tap on the shoulder: the server
 * restarted, FPS has stayed low, or a lot of players left at once. It only decides; showing it (a notification, or a message
 * when the app is open) is somebody else's job. All times are passed in, so it can be tested without waiting.
 */
class AlertEngine {
    private var lastUptime: Long? = null
    private var lowSince: Long? = null
    private var lowFired = false
    private var lastLowFpsAt = NEVER
    private val recent = ArrayList<Pair<Long, Int>>() // (time, players)
    private var lastDropAt = NEVER

    fun onReading(nowMillis: Long, info: ServerInfo, cfg: AlertConfig): List<SessionEvent> {
        val out = ArrayList<SessionEvent>()

        // ---- restart: the server's uptime went backwards (also true across a reconnect, because the last uptime is kept)
        val previous = lastUptime
        lastUptime = info.uptimeSeconds
        if (previous != null && info.uptimeSeconds + RESTART_SLACK_SECONDS < previous) {
            if (cfg.restart) out += SessionEvent.Restarted(previous)
            lowSince = null; lowFired = false
            recent.clear()
            lastDropAt = nowMillis // players always vanish in a restart: that is not a separate alert
        }

        // ---- low FPS for a while
        if (cfg.lowFps) {
            if (info.fps < cfg.fpsBelow) {
                if (lowSince == null) lowSince = nowMillis
                val since = lowSince!!
                if (!lowFired && nowMillis - since >= cfg.fpsMinutes * 60_000L && nowMillis - lastLowFpsAt >= COOLDOWN_MILLIS) {
                    lowFired = true
                    lastLowFpsAt = nowMillis
                    out += SessionEvent.LowFps(info.fps.toInt(), cfg.fpsMinutes)
                }
            } else if (info.fps >= cfg.fpsBelow + FPS_RECOVERY_MARGIN) {
                lowSince = null
                lowFired = false
            }
        } else {
            lowSince = null; lowFired = false
        }

        // ---- a sudden drop in players, compared with the busiest moment in the last few minutes
        recent += nowMillis to info.players
        recent.removeAll { nowMillis - it.first > DROP_WINDOW_MILLIS }
        if (cfg.playerDrop) {
            val peak = recent.maxOfOrNull { it.second } ?: info.players
            if (peak >= DROP_MIN_PEAK && info.players <= peak * 0.6 && peak - info.players >= DROP_MIN_LOST && nowMillis - lastDropAt >= COOLDOWN_MILLIS) {
                lastDropAt = nowMillis
                out += SessionEvent.PlayerDrop(peak, info.players)
            }
        }
        return out
    }

    companion object {
        /** "Has never alerted", far enough back that the cooldown is long over. */
        private const val NEVER = Long.MIN_VALUE / 2
        const val RESTART_SLACK_SECONDS = 30L
        const val COOLDOWN_MILLIS = 15 * 60_000L
        const val DROP_WINDOW_MILLIS = 3 * 60_000L
        const val DROP_MIN_PEAK = 10
        const val DROP_MIN_LOST = 5
        const val FPS_RECOVERY_MARGIN = 3
    }
}
