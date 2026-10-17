@file:OptIn(ExperimentalMaterial3Api::class)

package app.getbunker.rcon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getbunker.rcon.core.ConnState
import app.getbunker.rcon.core.ServerSession
import app.getbunker.rcon.rcon.Fmt
import app.getbunker.rcon.rcon.PlayerInfo
import kotlinx.coroutines.launch

private enum class PlayerSort(val label: String) { Name("Name"), Ping("Ping (lowest first)"), Time("Time online (longest first)"), Health("Health (lowest first)") }

@Composable
fun PlayersTab(session: ServerSession, notify: (String) -> Unit) {
    val players by session.players.collectAsState()
    val state by session.state.collectAsState()
    val notes by session.tracker.records.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(PlayerSort.Name) }
    var sortMenu by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<PlayerTarget?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val shown = remember(players, query, sort) {
        val q = query.trim()
        players.filter { q.isEmpty() || it.name.contains(q, true) || it.steamId.contains(q) }.let { list ->
            when (sort) {
                PlayerSort.Name -> list.sortedBy { it.name.lowercase() }
                PlayerSort.Ping -> list.sortedBy { it.ping }
                PlayerSort.Time -> list.sortedByDescending { it.connectedSeconds }
                PlayerSort.Health -> list.sortedBy { it.health }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search ${players.size} players") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") } },
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    PlayerSort.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label, color = if (s == sort) B.c.accent else B.c.text) }, onClick = { sort = s; sortMenu = false })
                    }
                }
            }
        }

        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; scope.launch { session.refresh(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
            if (shown.isEmpty()) {
                EmptyState(
                    Icons.Filled.People,
                    if (state != ConnState.Connected) "Not connected" else if (players.isEmpty()) "Nobody is online" else "No matches",
                    if (state != ConnState.Connected) "Connect to a server to see who is online." else if (players.isEmpty()) "Players appear here as soon as they join. Pull down to refresh." else "Try a different name or Steam ID.",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(shown, key = { it.steamId.ifEmpty { it.name } }) { p ->
                        PlayerRow(p, hasNote = notes[p.steamId]?.note?.isNotEmpty() == true) { target = PlayerTarget(p.steamId, p.name, p.ping, true) }
                        HorizontalDivider(color = B.c.border, modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }

    PlayerActionsHost(session, target, onDismiss = { target = null }, notify = notify)
}

@Composable
private fun PlayerRow(p: PlayerInfo, hasNote: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.name)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name.ifBlank { "(no name)" }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (hasNote) { Spacer(Modifier.size(8.dp)); Pill("note", B.c.warn) }
            }
            Text("${p.steamId}  ·  ${Fmt.duration(p.connectedSeconds)} online", style = MaterialTheme.typography.bodySmall, color = B.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${p.ping} ms", color = pingTint(p.ping), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("${p.health.toInt()} hp", style = MaterialTheme.typography.bodySmall, color = B.c.muted)
        }
    }
}

@Composable
fun pingTint(ping: Int) = when {
    ping < 100 -> B.c.ok
    ping < 200 -> B.c.warn
    else -> B.c.err
}

@Composable
fun Avatar(name: String, size: Int = 42, online: Boolean? = null) {
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    Box(Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().clip(CircleShape).background(B.c.selection)) {
            Text(letter, color = B.c.accent, fontWeight = FontWeight.Bold)
        }
        if (online != null) Box(
            Modifier.align(Alignment.BottomEnd).size(13.dp).clip(CircleShape).background(B.c.bg).padding(2.dp).clip(CircleShape).background(if (online) B.c.ok else B.c.dim),
        )
    }
}
