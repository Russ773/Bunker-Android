@file:OptIn(ExperimentalLayoutApi::class)

package app.getbunker.rcon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.getbunker.rcon.core.ConnState
import app.getbunker.rcon.core.ServerSession
import app.getbunker.rcon.data.CommandShortcut
import app.getbunker.rcon.rcon.Fmt
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

fun fpsColor(fps: Double, c: BunkerColors): androidx.compose.ui.graphics.Color = when {
    fps >= 30 -> c.ok
    fps >= 15 -> c.warn
    else -> c.err
}

@Composable
internal fun DashboardTab(session: ServerSession, nav: NavController, notify: (String) -> Unit, goTo: (Tab) -> Unit) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val info by session.info.collectAsState()
    val players by session.players.collectAsState()
    val history by session.history.collectAsState()
    val state by session.state.collectAsState()
    val events by session.tracker.events.collectAsState()
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<PlayerTarget?>(null) }
    var confirmShortcut by remember { mutableStateOf<CommandShortcut?>(null) }
    val connected = state == ConnState.Connected

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- headline
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(info?.hostname?.ifBlank { null } ?: session.profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(2.dp))
                Text(
                    info?.let { "${it.map.ifBlank { "Unknown map" }}  ·  up ${Fmt.duration(it.uptimeSeconds)}" }
                        ?: if (connected) "Waiting for server info..." else "Not connected",
                    style = MaterialTheme.typography.bodySmall, color = B.c.muted,
                )
            }
        }

        // ---- numbers
        val i = info
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                "Players", if (i != null) "${i.players} / ${i.maxPlayers}" else "-", Modifier.weight(1f),
                sub = i?.let { listOfNotNull(if (it.queued > 0) "${it.queued} queued" else null, if (it.joining > 0) "${it.joining} joining" else null).joinToString("  ·  ").ifEmpty { "none waiting" } },
            )
            StatCard("Server FPS", if (i != null) "${i.fps.toInt()}" else "-", Modifier.weight(1f), valueColor = if (i != null) fpsColor(i.fps, B.c) else B.c.text, sub = if (i != null) when { i.fps >= 30 -> "healthy"; i.fps >= 15 -> "a little slow"; else -> "struggling" } else null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Entities", if (i != null) "%,d".format(Locale.US, i.entities) else "-", Modifier.weight(1f))
            StatCard("Memory", if (i != null) Fmt.memory(i.memoryMb) else "-", Modifier.weight(1f))
        }

        // ---- quick buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { scope.launch { val r = session.run("server.save"); notify(r?.lineSequence()?.firstOrNull() ?: "World save requested") } },
                enabled = connected, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = B.c.accent),
            ) { Icon(Icons.Filled.Save, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Save") }
            OutlinedButton(onClick = { nav.navigate(Routes.ANNOUNCE) }, enabled = connected, modifier = Modifier.weight(1.3f), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Filled.Campaign, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Announce", maxLines = 1, softWrap = false)
            }
            OutlinedButton(onClick = { nav.navigate(Routes.QUICK) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Filled.Tune, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Tools", maxLines = 1, softWrap = false)
            }
        }

        if (settings.shortcuts.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                settings.shortcuts.forEach { sc ->
                    AssistChip(
                        onClick = {
                            if (sc.confirm) confirmShortcut = sc
                            else { session.command(sc.command); notify("Ran: ${sc.command}") }
                        },
                        enabled = connected,
                        label = { Text(sc.label.ifBlank { sc.command }, maxLines = 1) },
                    )
                }
            }
        }

        // ---- charts (last 15 minutes)
        val since = remember(history) { System.currentTimeMillis() - 15 * 60_000L }
        val recent = remember(history) { history.filter { it.timeMillis >= since } }
        ChartCard(
            "Players, last 15 min", i?.players?.toString() ?: "-", recent.map { it.timeMillis }, recent.map { it.players.toDouble() }, B.c.chat,
            height = 90,
        )
        ChartCard(
            "Server FPS, last 15 min", i?.fps?.toInt()?.toString() ?: "-", recent.map { it.timeMillis }, recent.map { it.fps },
            if (i != null) fpsColor(i.fps, B.c) else B.c.ok, height = 90,
        )
        TextButton(onClick = { nav.navigate(Routes.HEALTH) }, modifier = Modifier.align(Alignment.End)) { Text("Server health >", color = B.c.accent) }

        // ---- who is on
        if (players.isNotEmpty()) {
            Text("ONLINE NOW", style = MaterialTheme.typography.labelMedium, color = B.c.muted, fontWeight = FontWeight.SemiBold)
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    players.sortedBy { it.name.lowercase() }.take(6).forEach { p ->
                        Row(Modifier.fillMaxWidth().clickable { target = PlayerTarget(p.steamId, p.name, p.ping, true) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name.ifBlank { p.steamId }, Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("${p.ping} ms", color = pingColor(p.ping), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (players.size > 6) TextButton(onClick = { goTo(Tab.Players) }, modifier = Modifier.padding(start = 8.dp)) { Text("See all ${players.size} players", color = B.c.accent) }
                }
            }
        }

        // ---- recent joins and leaves
        val recentEvents = events.takeLast(5).reversed()
        if (recentEvents.isNotEmpty()) {
            Text("RECENT ACTIVITY", style = MaterialTheme.typography.labelMedium, color = B.c.muted, fontWeight = FontWeight.SemiBold)
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    recentEvents.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (e.joined) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = if (e.joined) B.c.ok else B.c.muted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(10.dp))
                            Text("${e.name.ifBlank { e.steamId }} ${if (e.joined) "joined" else "left"}", Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(clock.format(Date(e.timeMillis)), color = B.c.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(12.dp))
    }

    PlayerActionsHost(session, target, onDismiss = { target = null }, notify = notify)
    confirmShortcut?.let { sc ->
        ConfirmDialog(sc.label.ifBlank { "Run command" }, "Run this command?\n\n${sc.command}", "Run", onConfirm = { session.command(sc.command); notify("Ran: ${sc.command}") }, onDismiss = { confirmShortcut = null })
    }
}

fun pingColor(ping: Int): androidx.compose.ui.graphics.Color = when {
    ping < 100 -> DarkBunker.ok
    ping < 200 -> DarkBunker.warn
    else -> DarkBunker.err
}
