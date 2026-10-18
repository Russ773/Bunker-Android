@file:OptIn(ExperimentalMaterial3Api::class)

package app.getbunker.rcon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.getbunker.rcon.core.ServerWatcher
import app.getbunker.rcon.core.WatchSnapshot
import app.getbunker.rcon.core.WatchState
import app.getbunker.rcon.rcon.Cmd
import app.getbunker.rcon.rcon.Fmt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

private const val MAX_WATCHED = 8

/** Every saved server at a glance: online or offline (and why), players, FPS, uptime, who is on. Tap one to open it. */
@Composable
fun OverviewScreen(nav: NavController) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val servers = settings.servers.take(MAX_WATCHED)
    val key = servers.map { listOf(it.id, it.host, it.port, it.useTls, it.passwordEnc) }
    val watchers = remember(key) { servers.map { ServerWatcher(it, app.settings.password(it), app.http) } }

    // These extra connections only exist while this page is open.
    DisposableEffect(watchers) {
        watchers.forEach { it.start() }
        onDispose { watchers.forEach { it.stop() } }
    }
    val snaps = watchers.map { it.state.collectAsState().value }
    val online = snaps.count { it.state == WatchState.Online }
    val players = snaps.sumOf { it.info?.players ?: 0 }

    var broadcast by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // (is chat, text)

    ToolScreen("Overview", nav, actions = {
        IconButton(onClick = { broadcast = true }, enabled = online > 0) {
            Icon(Icons.Filled.Campaign, contentDescription = "Send to all online servers", tint = if (online > 0) B.c.text else B.c.dim)
        }
    }) { pad, notify ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(ServerWatcher.summary(settings.servers.size, online, players), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = B.c.muted, style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(watchers, key = { _, w -> w.profile.id }) { i, w ->
                    OverviewCard(w, snaps.getOrElse(i) { WatchSnapshot() }) {
                        app.sessions.open(w.profile.id)
                        nav.navigate(Routes.SESSION)
                    }
                }
                if (settings.servers.size > MAX_WATCHED) item {
                    Text("Showing the first $MAX_WATCHED of ${settings.servers.size} servers.", color = B.c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (broadcast) BroadcastDialog(onSend = { chat, text -> pending = chat to text }, onDismiss = { broadcast = false })

        pending?.let { (chat, text) ->
            val targets = watchers.filterIndexed { i, _ -> snaps.getOrNull(i)?.state == WatchState.Online }
            val names = ServerWatcher.nameList(targets.map { it.profile.displayName })
            ConfirmDialog(
                if (chat) "Say on all online servers" else "Run on all online servers",
                (if (chat) "Send \"$text\" to chat on $names?" else "Run \"$text\" on $names?") + "\n\nThis goes to ${targets.size} server${if (targets.size == 1) "" else "s"} at once.",
                "Send", destructive = !chat,
                onConfirm = {
                    val command = if (chat) "say ${Cmd.quote(text)}" else text
                    app.scope.launch {
                        val results = targets.map { async { it.send(command) } }.awaitAll()
                        val ok = results.count { it != null }
                        notify(if (ok == targets.size) "Sent to $ok server${if (ok == 1) "" else "s"}" else "Sent to $ok of ${targets.size} servers")
                    }
                },
                onDismiss = { pending = null },
            )
        }
    }
}

@Composable
private fun BroadcastDialog(onSend: (Boolean, String) -> Unit, onDismiss: () -> Unit) {
    var chat by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send to all online servers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(chat, { chat = true }, label = { Text("Say in chat") })
                    FilterChip(!chat, { chat = false }, label = { Text("Run a command") })
                }
                OutlinedTextField(
                    text, { text = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (chat) "Message for all players" else "Console command") }, minLines = if (chat) 2 else 1, maxLines = 4,
                )
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSend(chat, text.trim()); onDismiss() }) { Text("Next", color = B.c.accent, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = B.c.muted) } },
        containerColor = B.c.panel,
    )
}

@Composable
private fun OverviewCard(watcher: ServerWatcher, snap: WatchSnapshot, onOpen: () -> Unit) {
    val profile = watcher.profile
    val color = when (snap.state) {
        WatchState.Online -> B.c.ok
        WatchState.Connecting, WatchState.Idle -> B.c.warn
        WatchState.Offline -> B.c.err
    }
    Panel(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(profile.address, style = MaterialTheme.typography.bodySmall, color = B.c.muted)
                }
                Pill(
                    when (snap.state) { WatchState.Online -> "Online"; WatchState.Offline -> "Offline"; else -> "Connecting" },
                    color,
                )
            }
            val info = snap.info
            if (snap.state == WatchState.Online && info != null) {
                Spacer(Modifier.size(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat("Players", "${info.players}/${info.maxPlayers}", Modifier.weight(1f))
                    Stat("FPS", "${info.fps.toInt()}", Modifier.weight(1f), fpsColor(info.fps, B.c))
                    Stat("Uptime", Fmt.duration(info.uptimeSeconds), Modifier.weight(1f))
                }
                if (snap.history.size >= 2) {
                    Spacer(Modifier.size(10.dp))
                    Sparkline(snap.history, B.c.chat)
                }
                val line = ServerWatcher.playerLine(snap.players.map { it.name })
                if (line.isNotEmpty()) {
                    Spacer(Modifier.size(8.dp))
                    Text(line, style = MaterialTheme.typography.bodySmall, color = B.c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else if (snap.state == WatchState.Offline && snap.detail.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Text(snap.detail, style = MaterialTheme.typography.bodySmall, color = B.c.muted)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = B.c.text) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = B.c.muted)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1)
    }
}

/** A tiny line of the recent player count, without axes. */
@Composable
fun Sparkline(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(34.dp)) {
        val max = (values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val min = 0f
        val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / (max - min)) * (size.height - 4f) - 2f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}
