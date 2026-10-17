@file:OptIn(ExperimentalMaterial3Api::class)

package app.getbunker.rcon.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import app.getbunker.rcon.core.ConnState
import app.getbunker.rcon.core.ServerSession
import kotlinx.coroutines.launch

internal enum class Tab(val label: String) { Dashboard("Dashboard"), Console("Console"), Players("Players"), Chat("Chat"), More("More") }

@Composable
fun SessionScreen(session: ServerSession, nav: NavController) {
    val context = LocalContext.current
    val app = LocalApp.current
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]
    val state by session.state.collectAsState()
    val status by session.status.collectAsState()
    val info by session.info.collectAsState()
    val players by session.players.collectAsState()
    val reconnecting by session.reconnecting.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val notify: (String) -> Unit = { msg -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(msg) } }

    // Ask once for permission to show the notifications (needed on Android 13 and newer).
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = tab != Tab.Dashboard) { tabIndex = 0 }

    Scaffold(
        containerColor = B.c.bg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            BunkerTopBar(
                title = session.profile.displayName,
                subtitle = when (state) {
                    ConnState.Connected -> info?.let { "${it.players}/${it.maxPlayers} players  ·  ${it.fps.toInt()} FPS" } ?: "Connected"
                    ConnState.Connecting -> "Connecting..."
                    ConnState.Disconnected -> if (reconnecting) "Reconnecting..." else "Not connected"
                },
                onBack = { nav.popBackStack() },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (state == ConnState.Connected || reconnecting || state == ConnState.Connecting) {
                            DropdownMenuItem(text = { Text("Disconnect") }, onClick = { menu = false; session.disconnect() })
                        } else {
                            DropdownMenuItem(text = { Text("Connect") }, onClick = { menu = false; session.connect() })
                        }
                        DropdownMenuItem(text = { Text("Refresh now") }, onClick = { menu = false; scope.launch { session.refresh() } })
                        DropdownMenuItem(text = { Text("Switch server") }, onClick = { menu = false; nav.popBackStack() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; nav.navigate(Routes.SETTINGS) })
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = B.c.panel) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = i == tabIndex,
                        onClick = { tabIndex = i },
                        icon = {
                            val icon = when (t) {
                                Tab.Dashboard -> Icons.Filled.Dashboard
                                Tab.Console -> Icons.Filled.Terminal
                                Tab.Players -> Icons.Filled.People
                                Tab.Chat -> Icons.AutoMirrored.Filled.Chat
                                Tab.More -> Icons.Filled.MoreHoriz
                            }
                            if (t == Tab.Players && players.isNotEmpty()) BadgedBox(badge = { Badge { Text("${players.size}") } }) { Icon(icon, contentDescription = t.label) }
                            else Icon(icon, contentDescription = t.label)
                        },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = B.c.accent, selectedTextColor = B.c.accent,
                            indicatorColor = B.c.selection, unselectedIconColor = B.c.muted, unselectedTextColor = B.c.muted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ConnectionBanner(session)
            when (tab) {
                Tab.Dashboard -> DashboardTab(session, nav, notify, goTo = { tabIndex = it.ordinal })
                Tab.Console -> ConsoleTab(session, nav, notify)
                Tab.Players -> PlayersTab(session, notify)
                Tab.Chat -> ChatTab(session, notify)
                Tab.More -> MoreTab(nav)
            }
        }
    }
}

/** A strip under the title that says when the server is not connected, with a button to fix it. */
@Composable
fun ConnectionBanner(session: ServerSession) {
    val state by session.state.collectAsState()
    val status by session.status.collectAsState()
    val reconnecting by session.reconnecting.collectAsState()
    if (state == ConnState.Connected) return
    val connecting = state == ConnState.Connecting
    val color = if (connecting) B.c.warn else B.c.err
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color)
        Spacer(Modifier.size(10.dp))
        Text(status, color = B.c.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 3)
        if (reconnecting || connecting) TextButton(onClick = { session.disconnect() }) { Text("Stop", color = B.c.muted) }
        else TextButton(onClick = { session.connect() }) { Text("Connect", color = B.c.accent) }
    }
}
