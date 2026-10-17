@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.getbunker.rcon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.getbunker.rcon.core.ConnState
import app.getbunker.rcon.core.ItemInfo
import app.getbunker.rcon.core.ServerSession
import app.getbunker.rcon.data.CommandShortcut
import app.getbunker.rcon.rcon.Cmd
import app.getbunker.rcon.rcon.Fmt
import kotlinx.coroutines.launch
import java.util.Locale

/** The frame every tool screen shares: a title bar with a back button, a snackbar, and a `notify` function for results. */
@Composable
fun ToolScreen(
    title: String,
    nav: NavController,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues, (String) -> Unit) -> Unit,
) {
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { msg -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(msg) } }
    Scaffold(
        containerColor = B.c.bg,
        topBar = { BunkerTopBar(title, onBack = { nav.popBackStack() }, actions = actions) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = fab,
    ) { pad -> content(pad, notify) }
}

// -------------------------------------------------------------------------------------------------------- quick actions

@Composable
fun QuickActionsScreen(session: ServerSession, nav: NavController) {
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsState()
    val connected = state == ConnState.Connected
    var confirmRestart by remember { mutableStateOf<Int?>(null) }
    var customTime by remember { mutableStateOf(false) }
    var customRestart by remember { mutableStateOf(false) }

    ToolScreen("Quick actions", nav) { pad, notify ->
        fun run(cmd: String, done: String) = scope.launch { val r = session.run(cmd); notify(r?.lineSequence()?.firstOrNull()?.take(100)?.let { "$done: $it" } ?: done) }

        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (!connected) Text("Connect to a server to use these.", color = B.c.warn, modifier = Modifier.padding(vertical = 8.dp))

            Text("SERVER", style = MaterialTheme.typography.labelMedium, color = B.c.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { run("server.save", "World save requested") }, enabled = connected, colors = ButtonDefaults.buttonColors(containerColor = B.c.accent)) { Text("Save world") }
                OutlinedButton(onClick = { run("server.writecfg", "Config written") }, enabled = connected) { Text("Write config") }
            }

            Text("SET THE TIME OF DAY", style = MaterialTheme.typography.labelMedium, color = B.c.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Morning 8:00" to 8, "Noon 12:00" to 12, "Evening 18:00" to 18, "Midnight 0:00" to 0).forEach { (label, hour) ->
                    AssistChip(onClick = { run("env.time $hour", "Time set to $label") }, enabled = connected, label = { Text(label) })
                }
                AssistChip(onClick = { customTime = true }, enabled = connected, label = { Text("Other hour...") })
            }

            Text("SHUT DOWN WITH A COUNTDOWN", style = MaterialTheme.typography.labelMedium, color = B.c.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 15, 30).forEach { m ->
                    AssistChip(onClick = { confirmRestart = m }, enabled = connected, label = { Text("In $m min") })
                }
                AssistChip(onClick = { customRestart = true }, enabled = connected, label = { Text("Other...") })
            }
            Text(
                "Players are warned first. Rust's restart command stops the server, so your host must start it again, and it cannot be cancelled from here.",
                style = MaterialTheme.typography.bodySmall, color = B.c.muted, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.size(24.dp))
        }

        confirmRestart?.let { m ->
            ConfirmDialog(
                "Restart server", "Shut the server down in $m minute${if (m == 1) "" else "s"}?\n\nPlayers are warned first. Your host must start it again.",
                "Restart", destructive = true,
                onConfirm = { run("restart ${m * 60} \"Server restarting\"", "Restart in $m minute${if (m == 1) "" else "s"} requested") },
                onDismiss = { confirmRestart = null },
            )
        }
        if (customTime) InputDialog("Set the time", "Hour (0 to 24)", "12", numeric = true, allowEmpty = false, confirmText = "Set",
            onConfirm = { h -> h.toIntOrNull()?.takeIf { it in 0..24 }?.let { run("env.time $it", "Time set to $it:00") } ?: notify("Enter an hour from 0 to 24") },
            onDismiss = { customTime = false })
        if (customRestart) InputDialog("Restart countdown", "Minutes (1 to 120)", "5", numeric = true, allowEmpty = false, confirmText = "Next",
            onConfirm = { m -> m.toIntOrNull()?.takeIf { it in 1..120 }?.let { confirmRestart = it } ?: notify("Enter 1 to 120 minutes") },
            onDismiss = { customRestart = false })
    }
}

// -------------------------------------------------------------------------------------------------------- announce

@Composable
fun AnnounceScreen(session: ServerSession, nav: NavController) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val state by session.state.collectAsState()
    val connected = state == ConnState.Connected
    var text by remember { mutableStateOf("") }

    ToolScreen("Announce", nav) { pad, notify ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(text, { text = it }, label = { Text("Message for all players") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { session.command("say " + Cmd.quote(text)); notify("Sent to everyone"); },
                    enabled = connected && text.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = B.c.accent),
                ) { Icon(Icons.Filled.Campaign, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Send to everyone") }
                OutlinedButton(
                    onClick = {
                        val t = text.trim()
                        if (t !in settings.savedAnnouncements) app.settings.update { it.copy(savedAnnouncements = it.savedAnnouncements + t) }
                        notify("Message saved")
                    },
                    enabled = text.isNotBlank() && text.trim() !in settings.savedAnnouncements,
                ) { Text("Save") }
            }
            if (!connected) Text("Connect to a server to send messages.", color = B.c.warn, modifier = Modifier.padding(top = 8.dp))

            SectionHeader("Saved messages", Modifier.padding(start = 0.dp))
            if (settings.savedAnnouncements.isEmpty()) Text("Messages you save show up here so you can reuse them with one tap.", color = B.c.muted, style = MaterialTheme.typography.bodySmall)
            settings.savedAnnouncements.forEach { m ->
                Row(Modifier.fillMaxWidth().clickable { text = m }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m, Modifier.weight(1f))
                    IconButton(onClick = { app.settings.update { it.copy(savedAnnouncements = it.savedAnnouncements - m) } }) { Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = B.c.muted) }
                }
                HorizontalDivider(color = B.c.border)
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

// -------------------------------------------------------------------------------------------------------- shortcuts

@Composable
fun ShortcutsScreen(session: ServerSession, nav: NavController) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val state by session.state.collectAsState()
    val connected = state == ConnState.Connected
    var editing by remember { mutableStateOf<CommandShortcut?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<CommandShortcut?>(null) }

    ToolScreen("Shortcuts", nav, fab = {
        FloatingActionButton(onClick = { adding = true }, containerColor = B.c.accent, contentColor = androidx.compose.ui.graphics.Color.White) { Icon(Icons.Filled.Add, contentDescription = "Add shortcut") }
    }) { pad, notify ->
        if (settings.shortcuts.isEmpty()) {
            Box(Modifier.padding(pad)) { EmptyState(Icons.Filled.TouchApp, "No shortcuts yet", "Make a button for any console command. Shortcuts also appear on the Dashboard.") }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(settings.shortcuts, key = { it.id }) { sc ->
                    var menu by remember { mutableStateOf(false) }
                    Panel(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sc.label.ifBlank { sc.command }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sc.command, style = MaterialTheme.typography.bodySmall, color = B.c.muted, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(
                                onClick = { if (sc.confirm) confirm = sc else { session.command(sc.command); notify("Ran: ${sc.command}") } },
                                enabled = connected,
                            ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Run", tint = if (connected) B.c.accent else B.c.dim) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = sc })
                                    DropdownMenuItem(text = { Text("Delete", color = B.c.err) }, onClick = { menu = false; app.settings.update { it.copy(shortcuts = it.shortcuts - sc) } })
                                }
                            }
                        }
                    }
                }
            }
        }
        if (adding || editing != null) {
            ShortcutDialog(editing, onSave = { saved ->
                app.settings.update { s ->
                    val i = s.shortcuts.indexOfFirst { it.id == saved.id }
                    s.copy(shortcuts = if (i >= 0) s.shortcuts.toMutableList().also { it[i] = saved } else s.shortcuts + saved)
                }
            }, onDismiss = { adding = false; editing = null })
        }
        confirm?.let { sc ->
            ConfirmDialog(sc.label.ifBlank { "Run command" }, "Run this command?\n\n${sc.command}", "Run", onConfirm = { session.command(sc.command); notify("Ran: ${sc.command}") }, onDismiss = { confirm = null })
        }
    }
}

@Composable
private fun ShortcutDialog(existing: CommandShortcut?, onSave: (CommandShortcut) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var command by remember { mutableStateOf(existing?.command.orEmpty()) }
    var confirm by remember { mutableStateOf(existing?.confirm ?: false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add shortcut" else "Edit shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Button label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(command, { command = it }, label = { Text("Console command") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Ask before running", Modifier.weight(1f)); Switch(confirm, { confirm = it }) }
            }
        },
        confirmButton = {
            TextButton(enabled = command.isNotBlank(), onClick = {
                onSave((existing ?: CommandShortcut()).copy(label = label.trim(), command = command.trim(), confirm = confirm)); onDismiss()
            }) { Text("Save", color = B.c.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = B.c.muted) } },
        containerColor = B.c.panel,
    )
}

// -------------------------------------------------------------------------------------------------------- give items

@Composable
fun GiveItemsScreen(session: ServerSession, nav: NavController) {
    val players by session.players.collectAsState()
    val state by session.state.collectAsState()
    val connected = state == ConnState.Connected
    var item by remember { mutableStateOf<ItemInfo?>(null) }
    var amount by remember { mutableStateOf("100") }
    var targetId by remember { mutableStateOf<String?>(null) } // null = everyone
    var menu by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    val n = amount.toIntOrNull()
    val valid = connected && item != null && n != null && n in 1..100000
    val targetName = players.firstOrNull { it.steamId == targetId }?.name

    ToolScreen("Give items", nav) { pad, notify ->
        val scope = rememberCoroutineScope()
        fun give() {
            val it = item ?: return
            val count = n ?: return
            scope.launch {
                val cmd = if (targetId == null) "inventory.giveall \"${it.shortname}\" $count" else "inventory.giveto \"$targetId\" \"${it.shortname}\" $count"
                val reply = session.run(cmd)
                notify(reply?.lineSequence()?.firstOrNull()?.take(100) ?: "Gave $count x ${it.name} to ${targetName ?: "everyone online"}")
            }
        }
        Column(Modifier.padding(pad).fillMaxSize().imePadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("To: " + (targetName ?: if (targetId != null) targetId!! else "Everyone online"), modifier = Modifier.weight(1f))
                    Text("v")
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Everyone online") }, onClick = { targetId = null; menu = false })
                    players.sortedBy { it.name.lowercase() }.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name.ifBlank { p.steamId }) }, onClick = { targetId = p.steamId; menu = false })
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            ItemSearch(item, onPick = { item = it }, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 10, 100, 1000, 10000).forEach { v -> FilterChip(amount == v.toString(), { amount = v.toString() }, label = { Text("$v") }) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    amount, { amount = it.filter(Char::isDigit).take(6) }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
                Spacer(Modifier.size(12.dp))
                Button(onClick = { if (targetId == null) confirmAll = true else give() }, enabled = valid, colors = ButtonDefaults.buttonColors(containerColor = B.c.accent)) { Text("Give") }
            }
        }
        if (confirmAll) ConfirmDialog(
            "Give to everyone", "Give $n x ${item?.name} to EVERY player online?", "Give to all", destructive = true,
            onConfirm = ::give, onDismiss = { confirmAll = false },
        )
    }
}

// -------------------------------------------------------------------------------------------------------- health

@Composable
fun HealthScreen(session: ServerSession, nav: NavController) {
    val history by session.history.collectAsState()
    var minutes by remember { mutableStateOf(15) }
    val since = remember(history, minutes) { System.currentTimeMillis() - minutes * 60_000L }
    val data = remember(history, minutes) { history.filter { it.timeMillis >= since } }
    val last = data.lastOrNull()

    ToolScreen("Server health", nav) { pad, _ ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5 to "5 min", 15 to "15 min", 60 to "1 hour", 120 to "2 hours").forEach { (m, label) ->
                    FilterChip(minutes == m, { minutes = m }, label = { Text(label) })
                }
            }
            val times = data.map { it.timeMillis }
            ChartCard("Players", last?.players?.toString() ?: "-", times, data.map { it.players.toDouble() }, B.c.chat)
            ChartCard("Server FPS", last?.fps?.toInt()?.toString() ?: "-", times, data.map { it.fps }, last?.let { fpsColor(it.fps, B.c) } ?: B.c.ok)
            ChartCard("Entities", last?.entities?.let { String.format(Locale.US, "%,d", it) } ?: "-", times, data.map { it.entities.toDouble() }, B.c.warn, startAtZero = false, format = { String.format(Locale.US, "%,.0f", it) })
            ChartCard("Memory", last?.memoryMb?.let { Fmt.memory(it) } ?: "-", times, data.map { it.memoryMb.toDouble() }, B.c.accent, startAtZero = false, format = { Fmt.memory(it.toLong()) })
            Text("Readings are taken every few seconds while connected, and kept for two hours.", style = MaterialTheme.typography.bodySmall, color = B.c.muted)
            Spacer(Modifier.size(16.dp))
        }
    }
}
