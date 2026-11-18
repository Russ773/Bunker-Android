package app.getbunker.rcon.core

import app.getbunker.rcon.rcon.Fmt

/** The words for the server alerts, shared by notifications (app in the background) and messages (app open). */
object AlertText {
    /** (title, text) for an alert event, or null for events that are not server alerts. */
    fun of(server: String, event: SessionEvent): Pair<String, String>? = when (event) {
        is SessionEvent.Restarted ->
            "Server restarted" to "$server restarted. It had been up for ${Fmt.duration(event.previousUptimeSeconds)}."
        is SessionEvent.LowFps ->
            "Server FPS is low" to "$server is at ${event.fps} FPS and has been low for ${event.minutes} minutes or more."
        is SessionEvent.PlayerDrop ->
            "Players dropped" to "$server went from ${event.from} to ${event.to} players in the last few minutes."
        is SessionEvent.WatchedJoined ->
            "Watched player joined" to "${event.name.ifBlank { event.steamId }} joined $server." +
                if (event.note.isNotBlank()) "\nYour note: ${event.note}" else ""
        else -> null
    }
}
