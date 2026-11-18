@file:OptIn(ExperimentalMaterial3Api::class)

package app.getbunker.rcon.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.getbunker.rcon.rcon.Cmd

/** Players you want to be told about when they join. Add one from any player's menu, or by Steam ID here. */
@Composable
fun WatchListScreen(nav: NavController) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    var adding by remember { mutableStateOf(false) }

    ToolScreen(
        "Watch list", nav,
        fab = { FloatingActionButton(onClick = { adding = true }, containerColor = B.c.accent, contentColor = Color.White) { Icon(Icons.Filled.Add, contentDescription = "Add a Steam ID") } },
    ) { pad, notify ->
        if (settings.watchList.isEmpty()) {
            Box(Modifier.padding(pad)) {
                EmptyState(
                    Icons.Filled.Visibility, "Nobody on the watch list",
                    "Tap a player anywhere in Bunker and choose \"Add to watch list\" (or add a Steam ID with the + button). You are told when they join any of your servers.",
                )
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!settings.alertWatchList) item {
                    Text("Watch list alerts are switched off in Settings, so nothing will be shown when these players join.", color = B.c.warn, style = MaterialTheme.typography.bodySmall)
                }
                items(settings.watchList, key = { it.steamId }) { w ->
                    Panel(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(w.name.ifBlank { w.steamId }, 40)
                            Spacer(Modifier.size(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(w.name.ifBlank { "(no name yet)" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(w.steamId + if (w.addedMillis > 0) "  ·  added ${relativeTime(w.addedMillis)}" else "", style = MaterialTheme.typography.bodySmall, color = B.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { app.settings.unwatch(w.steamId); notify("${w.name.ifBlank { w.steamId }} removed") }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = B.c.muted)
                            }
                        }
                    }
                }
            }
        }

        if (adding) {
            var id by remember { mutableStateOf("") }
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { adding = false },
                title = { Text("Watch a player") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            id, { id = it.filter(Char::isDigit).take(17) }, label = { Text("Steam ID (17 digits)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name (optional, for your reference)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(enabled = Cmd.isSteamId(id), onClick = { app.settings.watch(id, name.trim()); notify("Watching ${name.trim().ifBlank { id }}"); adding = false }) {
                        Text("Watch", color = B.c.accent, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel", color = B.c.muted) } },
                containerColor = B.c.panel,
            )
        }
    }
}
