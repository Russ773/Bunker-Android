package app.getbunker.rcon.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.getbunker.rcon.core.ServerSession

object Routes {
    const val SERVERS = "servers"
    const val EDIT = "edit?id={id}"
    const val SESSION = "session"
    const val QUICK = "quick"
    const val ANNOUNCE = "announce"
    const val SHORTCUTS = "shortcuts"
    const val BANS = "bans"
    const val PLAYER_DB = "playerdb"
    const val SCHEDULES = "schedules"
    const val GIVE = "give"
    const val HEALTH = "health"
    const val HIDDEN = "hidden"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun edit(id: String? = null) = if (id == null) "edit" else "edit?id=$id"
}

/** Process-lifetime flag so "connect on launch" happens once, not every time the servers list is shown. */
private var autoConnectHandled = false

@Composable
fun BunkerNav() {
    val nav = rememberNavController()
    val app = LocalApp.current
    val start = remember { if (app.settings.current.servers.isEmpty()) "edit" else Routes.SERVERS }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SERVERS) { ServersScreen(nav) }
        composable(
            Routes.EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { EditServerScreen(nav, it.arguments?.getString("id")) }
        composable(Routes.SESSION) { SessionGate(nav) { s -> SessionScreen(s, nav) } }
        composable(Routes.QUICK) { SessionGate(nav) { s -> QuickActionsScreen(s, nav) } }
        composable(Routes.ANNOUNCE) { SessionGate(nav) { s -> AnnounceScreen(s, nav) } }
        composable(Routes.SHORTCUTS) { SessionGate(nav) { s -> ShortcutsScreen(s, nav) } }
        composable(Routes.BANS) { SessionGate(nav) { s -> BansScreen(s, nav) } }
        composable(Routes.PLAYER_DB) { SessionGate(nav) { s -> PlayerDbScreen(s, nav) } }
        composable(Routes.SCHEDULES) { SessionGate(nav) { s -> SchedulesScreen(s, nav) } }
        composable(Routes.GIVE) { SessionGate(nav) { s -> GiveItemsScreen(s, nav) } }
        composable(Routes.HEALTH) { SessionGate(nav) { s -> HealthScreen(s, nav) } }
        composable(Routes.HIDDEN) { SessionGate(nav) { s -> HiddenRulesScreen(s, nav) } }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable(Routes.ABOUT) { AboutScreen(nav) }
    }

    LaunchedEffect(Unit) {
        if (!autoConnectHandled) {
            autoConnectHandled = true
            val s = app.settings.current
            if (s.autoConnect && s.activeServer != null && app.sessions.session.value == null && start == Routes.SERVERS) {
                app.sessions.open(s.activeServer!!.id)
                nav.navigate(Routes.SESSION)
            }
        }
    }
}

/** Shows a screen that needs an open server session, or goes back to the server list if there is none. */
@Composable
fun SessionGate(nav: NavController, content: @Composable (ServerSession) -> Unit) {
    val app = LocalApp.current
    val session by app.sessions.session.collectAsState()
    val s = session
    if (s == null) {
        LaunchedEffect(Unit) { nav.navigate(Routes.SERVERS) { popUpTo(0) } }
    } else {
        content(s)
    }
}
