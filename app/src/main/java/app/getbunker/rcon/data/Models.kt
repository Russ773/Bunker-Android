package app.getbunker.rcon.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** A saved server. The RCON password is stored encrypted ([passwordEnc]) with a key held in the Android Keystore. */
@Serializable
data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val port: Int = 28016,
    /** Connect with wss:// (only for servers that sit behind a TLS proxy). Plain WebRCON is ws://. */
    val useTls: Boolean = false,
    val passwordEnc: String = "",
) {
    val displayName: String get() = name.ifBlank { host }
    val address: String get() = "$host:$port"
}

/** A user-defined button that runs a console command. */
@Serializable
data class CommandShortcut(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val command: String = "",
    val confirm: Boolean = false,
)

/** One rule for hiding console lines: everything from a plugin, or any line containing some words. */
@Serializable
data class HideRule(val kind: String = KIND_WORDS, val value: String = "") {
    val isPlugin: Boolean get() = kind == KIND_PLUGIN

    companion object {
        const val KIND_PLUGIN = "plugin"
        const val KIND_WORDS = "words"
    }
}

/** A list of commands run on a timer while connected. `wait N` lines pause for N seconds. */
@Serializable
data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val commands: List<String> = emptyList(),
    /** "interval" (every N minutes) or "daily" (at a time of day). */
    val mode: String = MODE_INTERVAL,
    val intervalMinutes: Int = 60,
    val timeOfDay: String = "04:00",
    val enabled: Boolean = true,
    val lastRunMillis: Long = 0,
) {
    companion object {
        const val MODE_INTERVAL = "interval"
        const val MODE_DAILY = "daily"
    }
}

object ThemeMode {
    const val DARK = "dark"
    const val LIGHT = "light"
    const val SYSTEM = "system"
}

@Serializable
data class AppSettings(
    val servers: List<ServerProfile> = emptyList(),
    val activeServerId: String? = null,

    // Appearance & console
    val theme: String = ThemeMode.DARK,
    val consoleFontSize: Int = 12,
    val keepScreenOn: Boolean = false,
    val hidden: List<HideRule> = emptyList(),
    val commandHistory: List<String> = emptyList(),
    val confirmedCommands: List<String> = emptyList(),

    // Connection
    val autoConnect: Boolean = false,
    val autoReconnect: Boolean = true,
    val keepConnected: Boolean = true,

    // Notifications
    val notifyConnectionLost: Boolean = true,
    val notifyWatchWords: Boolean = true,
    val watchWords: String = "",
    val notifyJoins: Boolean = false,

    // Alerts about the server itself (each can show as a notification, or as a message when the app is open)
    val alertRestart: Boolean = true,
    val alertLowFps: Boolean = false,
    val alertFpsBelow: Int = 20,
    val alertPlayerDrop: Boolean = false,
    val alertWatchList: Boolean = true,
    /** Players to be told about when they join, whichever server you are on. */
    val watchList: List<WatchEntry> = emptyList(),

    // Steam lookups on the player card (see SteamLookup). Only a Steam ID goes out, and only when a card is opened.
    val steamLookups: Boolean = true,
    /** An optional free Steam Web API key, encrypted like the RCON passwords. Adds Rust hours, account age and the full ban record. */
    val steamApiKeyEnc: String = "",

    // Chat identity (see the BunkerChat plugin)
    val chatName: String = "",
    val chatSteamId: String = "",
    val chatColor: String = "#aaff55",
    val chatTagColor: String = "#55aaff",
    val chatUsePlugin: Boolean = true,

    // Admin helpers
    val shortcuts: List<CommandShortcut> = emptyList(),
    val schedules: List<ScheduledTask> = emptyList(),
    val savedAnnouncements: List<String> = emptyList(),

    // Items
    val useOnlineItemList: Boolean = true,

    // Security
    val appLock: Boolean = false,
    val secureScreen: Boolean = false,

    val firstRunDone: Boolean = false,
) {
    val activeServer: ServerProfile?
        get() = servers.firstOrNull { it.id == activeServerId } ?: servers.firstOrNull()
}

/** A player on the watch list. The name is only for showing; the Steam ID is what is matched. */
@Serializable
data class WatchEntry(val steamId: String, val name: String = "", val addedMillis: Long = 0)

/** One player Bunker has seen on a server, with your own note about them. */
@Serializable
data class PlayerRecord(
    val steamId: String,
    val name: String = "",
    val firstSeenMillis: Long = 0,
    val lastSeenMillis: Long = 0,
    val totalSeconds: Double = 0.0,
    val sessions: Int = 0,
    val note: String = "",
)
