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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.getbunker.rcon.core.ConnState
import app.getbunker.rcon.core.ScheduleMath
import app.getbunker.rcon.core.ServerSession
import app.getbunker.rcon.data.HideRule
import app.getbunker.rcon.data.PlayerRecord
import app.getbunker.rcon.data.ScheduledTask
import app.getbunker.rcon.rcon.BanEntry
import app.getbunker.rcon.rcon.BanListParser
import app.getbunker.rcon.rcon.Cmd
import app.getbunker.rcon.rcon.ConsoleFilter
import app.getbunker.rcon.rcon.Fmt
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun relativeTime(thenMillis: Long, now: Long = System.currentTimeMillis()): String {
    if (thenMillis <= 0) return "never"
    val s = (now - thenMillis) / 1000
    return when {
        s < 45 -> "just now"
        s < 3600 -> "${s / 60 + 1} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
}

private val dateTime = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

// -------------------------------------------------------------------------------------------------------- bans

@Composable
fun BansScreen(session: ServerSession, nav: NavController) {
    val state by session.state.collectAsState()
    val connected = state == ConnState.Connected
    val scope = rememberCoroutineScope()
    var bans by remember { mutableStateOf<List<BanEntry>?>(null) }
    var raw by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<BanEntry?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun load() {
        if (!connected) return
        loading = true
        scope.launch {
            try {
                val reply = session.query("banlistex")
                val parsed = BanListParser.tryParse(reply)
                bans = parsed ?: emptyList(); raw = if (parsed == null && reply.isNotBlank()) reply else null; error = null
            } catch (e: Exception) { error = e.message }
            loading = false
        }
    }
    LaunchedEffect(connected) { load() }

    ToolScreen("Ban list", nav, fab = {
        FloatingActionButton(onClick = { adding = true }, containerColor = B.c.accent, contentColor = androidx.compose.ui.graphics.Color.White) { Icon(Icons.Filled.Add, contentDescription = "Ban a Steam ID") }
    }) { pad, notify ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Search bans") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") } },
            )
            PullToRefreshBox(isRefreshing = loading, onRefresh = ::load, modifier = Modifier.fillMaxSize()) {
                val list = bans
                when {
                    !connected -> EmptyState(Icons.Filled.Block, "Not connected", "Connect to a server to see its ban list.")
                    error != null -> EmptyState(Icons.Filled.Block, "Could not load the ban list", error.orEmpty())
                    list == null -> Box(Modifier.fillMaxSize())
                    raw != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text("The server answered in a format Bunker does not recognise:", color = B.c.muted)
                        Spacer(Modifier.size(8.dp))
                        Text(raw.orEmpty(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    list.isEmpty() -> EmptyState(Icons.Filled.Block, "Nobody is banned", "Banned players will be listed here.")
                    else -> {
                        val q = query.trim()
                        val shown = list.filter { q.isEmpty() || it.name.contains(q, true) || it.steamId.contains(q) || it.reason.contains(q, true) }
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                            item { Text("${list.size} ban${if (list.size == 1) "" else "s"}  ·  ${list.count { it.permanent }} permanent", Modifier.padding(16.dp, 12.dp), color = B.c.muted, style = MaterialTheme.typography.bodySmall) }
                            items(shown, key = { it.steamId + it.index }) { b ->
                                Column(Modifier.fillMaxWidth().clickable { selected = b }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(b.name.ifBlank { "(no name)" }, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Pill(BanListParser.describeExpiry(b.expiry), if (b.permanent) B.c.err else B.c.warn)
                                    }
                                    Text(b.steamId, style = MaterialTheme.typography.bodySmall, color = B.c.muted)
                                    if (b.reason.isNotBlank()) Text(b.reason, style = MaterialTheme.typography.bodySmall, color = B.c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                HorizontalDivider(color = B.c.border)
                            }
                        }
                    }
                }
            }
        }

        selected?.let { b ->
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text(b.name.ifBlank { b.steamId }) },
                text = { Column { Text(b.steamId, color = B.c.muted); if (b.reason.isNotBlank()) Text("Reason: ${b.reason}"); Text("Expires: ${BanListParser.describeExpiry(b.expiry)}") } },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            session.run("unban ${b.steamId}"); session.command("server.writecfg")
                            notify("Unban sent for ${b.name.ifBlank { b.steamId }}"); load()
                        }
                        selected = null
                    }) { Text("Unban", color = B.c.ok, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { copyToClipboard(context, "Steam ID", b.steamId); notify("Steam ID copied"); selected = null }) { Text("Copy ID", color = B.c.muted) }
                        TextButton(onClick = { selected = null }) { Text("Close", color = B.c.muted) }
                    }
                },
                containerColor = B.c.panel,
            )
        }
        if (adding) BanByIdDialog(
            onBan = { id, name, reason ->
                scope.launch {
                    session.run("banid $id ${Cmd.quote(name.ifBlank { id })} ${Cmd.quote(reason.ifBlank { "Banned by admin" })}")
                    session.command("server.writecfg")
                    notify("Ban sent for $id"); load()
                }
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun BanByIdDialog(onBan: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Banned by admin") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ban a Steam ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(id, { id = it.filter(Char::isDigit).take(17) }, label = { Text("Steam ID (17 digits)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = Cmd.isSteamId(id), onClick = { onBan(id, name.trim(), reason.trim()); onDismiss() }) { Text("Ban", color = B.c.err, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = B.c.muted) } },
        containerColor = B.c.panel,
    )
}

// -------------------------------------------------------------------------------------------------------- player database

private enum class DbSort(val label: String) { LastSeen("Last seen"), Name("Name"), Time("Time played"), Sessions("Sessions") }

@Composable
fun PlayerDbScreen(session: ServerSession, nav: NavController) {
    val records by session.tracker.records.collectAsState()
    val online by session.tracker.online.collectAsState()
    val events by session.tracker.events.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(DbSort.LastSeen) }
    var target by remember { mutableStateOf<PlayerTarget?>(null) }
    val context = LocalContext.current

    val shown = remember(records, query, sort, online) {
        val q = query.trim()
        records.values.filter { q.isEmpty() || it.name.contains(q, true) || it.steamId.contains(q) || it.note.contains(q, true) }.let { l ->
            when (sort) {
                DbSort.LastSeen -> l.sortedByDescending { if (it.steamId in online) Long.MAX_VALUE else it.lastSeenMillis }
                DbSort.Name -> l.sortedBy { it.name.lowercase() }
                DbSort.Time -> l.sortedByDescending { it.totalSeconds }
                DbSort.Sessions -> l.sortedByDescending { it.sessions }
            }
        }
    }

    ToolScreen("Player database", nav, actions = {
        IconButton(onClick = {
            val csv = buildString {
                append("Steam ID,Name,First seen,Last seen,Hours played,Sessions,Note\n")
                records.values.sortedBy { it.name.lowercase() }.forEach { r ->
                    fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
                    append("${r.steamId},${esc(r.name)},${esc(dateTime.format(Date(r.firstSeenMillis)))},${esc(dateTime.format(Date(r.lastSeenMillis)))},${"%.1f".format(Locale.US, r.totalSeconds / 3600)},${r.sessions},${esc(r.note)}\n")
                }
            }
            shareTextFile(context, "bunker-players.csv", csv, "text/csv")
        }) { Icon(Icons.Filled.FileDownload, contentDescription = "Export CSV") }
    }) { pad, notify ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab, containerColor = B.c.bg, contentColor = B.c.accent) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Players (${records.size})") }, selectedContentColor = B.c.accent, unselectedContentColor = B.c.muted)
                Tab(tab == 1, { tab = 1 }, text = { Text("Activity") }, selectedContentColor = B.c.accent, unselectedContentColor = B.c.muted)
            }
            if (tab == 0) {
                if (records.isEmpty()) {
                    EmptyState(Icons.Filled.Storage, "No players yet", "Bunker remembers everyone it sees online, along with your notes about them.")
                } else {
                    OutlinedTextField(
                        query, { query = it }, singleLine = true, placeholder = { Text("Search name, Steam ID or note") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") } },
                    )
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DbSort.entries.forEach { s -> FilterChip(sort == s, { sort = s }, label = { Text(s.label) }) }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.steamId }) { r ->
                            DbRow(r, r.steamId in online) { target = PlayerTarget(r.steamId, r.name, null, r.steamId in online) }
                            HorizontalDivider(color = B.c.border, modifier = Modifier.padding(start = 72.dp))
                        }
                    }
                }
            } else {
                if (events.isEmpty()) {
                    EmptyState(Icons.AutoMirrored.Filled.Login, "No activity yet", "Joins and leaves show here while you are connected.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(events.reversed(), key = { "${it.timeMillis}${it.steamId}${it.joined}" }) { e ->
                            Row(Modifier.fillMaxWidth().clickable { target = PlayerTarget(e.steamId, e.name, null, session.players.value.any { it.steamId == e.steamId }) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (e.joined) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = if (e.joined) B.c.ok else B.c.muted)
                                Spacer(Modifier.size(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.name.ifBlank { e.steamId }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (e.joined) "joined" else "left", style = MaterialTheme.typography.bodySmall, color = B.c.muted)
                                }
                                Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(e.timeMillis)), color = B.c.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider(color = B.c.border)
                        }
                    }
                }
            }
        }
        PlayerActionsHost(session, target, onDismiss = { target = null }, notify = notify)
    }
}

@Composable
private fun DbRow(r: PlayerRecord, online: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(r.name, online = online)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name.ifBlank { r.steamId }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (online) "online now" else "last seen ${relativeTime(r.lastSeenMillis)}") + "  ·  ${Fmt.duration(r.totalSeconds.toLong())} played  ·  ${r.sessions} session${if (r.sessions == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall, color = B.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall, color = B.c.warn, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// -------------------------------------------------------------------------------------------------------- schedules

@Composable
fun SchedulesScreen(session: ServerSession, nav: NavController) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val running by session.runningSchedules.collectAsState()
    val state by session.state.collectAsState()
    val connected = state == ConnState.Connected
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ScheduledTask?>(null) }
    var adding by remember { mutableStateOf(false) }

    ToolScreen("Scheduler", nav, fab = {
        FloatingActionButton(onClick = { adding = true }, containerColor = B.c.accent, contentColor = androidx.compose.ui.graphics.Color.White) { Icon(Icons.Filled.Add, contentDescription = "Add schedule") }
    }) { pad, notify ->
        if (settings.schedules.isEmpty()) {
            Box(Modifier.padding(pad)) { EmptyState(Icons.Filled.Schedule, "No schedules yet", "Run announcements, world saves or restart warnings on a timer. Schedules run while Bunker is connected to your server.") }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(settings.schedules, key = { it.id }) { t ->
                    Panel(Modifier.fillMaxWidth().clickable { editing = t }) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.name.ifBlank { "Schedule" }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Switch(t.enabled, { on -> app.settings.update { s -> s.copy(schedules = s.schedules.map { if (it.id == t.id) it.copy(enabled = on) else it }) } })
                            }
                            Text(
                                if (t.mode == ScheduledTask.MODE_DAILY) "Every day at ${t.timeOfDay}" else "Every ${t.intervalMinutes} minute${if (t.intervalMinutes == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall, color = B.c.muted,
                            )
                            val next = if (connected) session.nextRun(t) else null
                            Text(
                                when {
                                    t.id in running -> "Running now..."
                                    !t.enabled -> "Switched off"
                                    next != null -> "Next run ${dateTime.format(Date(next))}" + if (t.lastRunMillis > 0) "  ·  last ${relativeTime(t.lastRunMillis)}" else ""
                                    else -> "Waits until you are connected"
                                },
                                style = MaterialTheme.typography.bodySmall, color = if (t.id in running) B.c.ok else B.c.muted,
                            )
                            Text("${t.commands.count { it.isNotBlank() }} step${if (t.commands.count { it.isNotBlank() } == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = B.c.muted)
                            Row(Modifier.padding(top = 6.dp)) {
                                TextButton(onClick = { scope.launch { session.runSchedule(t) } }, enabled = connected && t.id !in running) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text("Run now", color = if (connected) B.c.accent else B.c.dim)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (adding || editing != null) {
            ScheduleDialog(editing, onSave = { saved ->
                app.settings.update { s ->
                    val i = s.schedules.indexOfFirst { it.id == saved.id }
                    s.copy(schedules = if (i >= 0) s.schedules.toMutableList().also { it[i] = saved } else s.schedules + saved)
                }
                notify("Schedule saved")
            }, onDelete = editing?.let { e -> { app.settings.update { s -> s.copy(schedules = s.schedules.filterNot { it.id == e.id }) } } }, onDismiss = { adding = false; editing = null })
        }
    }
}

@Composable
private fun ScheduleDialog(existing: ScheduledTask?, onSave: (ScheduledTask) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var commands by remember { mutableStateOf(existing?.commands?.joinToString("\n").orEmpty()) }
    var daily by remember { mutableStateOf(existing?.mode == ScheduledTask.MODE_DAILY) }
    var interval by remember { mutableStateOf((existing?.intervalMinutes ?: 60).toString()) }
    var time by remember { mutableStateOf(existing?.timeOfDay ?: "04:00") }
    val timeOk = ScheduleMath.parseTime(time) != null
    val valid = commands.isNotBlank() && (daily && timeOk || !daily && (interval.toIntOrNull() ?: 0) >= 1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New schedule" else "Edit schedule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    commands, { commands = it }, label = { Text("Commands, one per line") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8,
                    supportingText = { Text("Use \"wait 30\" to pause for 30 seconds. Example: say \"Restart in 5 minutes\", wait 300, restart 60") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!daily, { daily = false }, label = { Text("Every N minutes") })
                    FilterChip(daily, { daily = true }, label = { Text("Daily at") })
                }
                if (daily) OutlinedTextField(time, { time = it.take(5) }, label = { Text("Time (24 hour, HH:mm)") }, singleLine = true, isError = !timeOk, modifier = Modifier.fillMaxWidth())
                else OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(5) }, label = { Text("Minutes between runs") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Icon(Icons.Filled.Delete, contentDescription = null, tint = B.c.err); Spacer(Modifier.size(6.dp)); Text("Delete this schedule", color = B.c.err) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave((existing ?: ScheduledTask()).copy(
                    name = name.trim(), commands = commands.lines().map { it.trim() }.filter { it.isNotEmpty() },
                    mode = if (daily) ScheduledTask.MODE_DAILY else ScheduledTask.MODE_INTERVAL,
                    intervalMinutes = interval.toIntOrNull()?.coerceAtLeast(1) ?: 60, timeOfDay = time.trim(),
                ))
                onDismiss()
            }) { Text("Save", color = B.c.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = B.c.muted) } },
        containerColor = B.c.panel,
    )
}

// -------------------------------------------------------------------------------------------------------- hidden console rules

@Composable
fun HiddenRulesScreen(session: ServerSession, nav: NavController) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val entries by session.log.entries.collectAsState()
    var plugin by remember { mutableStateOf(true) }
    var value by remember { mutableStateOf("") }
    val seen = remember(entries) { ConsoleFilter.pluginsSeen(entries).take(12) }

    ToolScreen("Hide console messages", nav) { pad, notify ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Hidden lines are still received. Show them again by removing the rule, or use the \"hidden\" chip on the Console to peek.", color = B.c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            SectionHeader("Add a rule", Modifier.padding(start = 0.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(plugin, { plugin = true }, label = { Text("A plugin") })
                FilterChip(!plugin, { plugin = false }, label = { Text("Words in the message") })
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.weight(1f), label = { Text(if (plugin) "Plugin name, e.g. Economics" else "Words to hide") })
                Spacer(Modifier.size(8.dp))
                Button(onClick = { if (app.settings.addHideRule(if (plugin) HideRule.KIND_PLUGIN else HideRule.KIND_WORDS, value)) { notify("Rule added"); value = "" } }, enabled = value.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = B.c.accent)) { Text("Add") }
            }
            if (plugin && seen.isNotEmpty()) {
                Text("Plugins seen in this console (tap to hide):", color = B.c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    seen.forEach { (name, count) -> androidx.compose.material3.AssistChip(onClick = { app.settings.addHideRule(HideRule.KIND_PLUGIN, name); notify("Hiding [$name]") }, label = { Text("$name ($count)") }) }
                }
            }
            SectionHeader("Current rules", Modifier.padding(start = 0.dp))
            if (settings.hidden.isEmpty()) Text("Nothing is hidden.", color = B.c.muted)
            settings.hidden.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = B.c.muted)
                    Spacer(Modifier.size(12.dp))
                    Text(if (r.isPlugin) "Plugin  [${r.value}]" else "Words  \"${r.value}\"", Modifier.weight(1f))
                    IconButton(onClick = { app.settings.removeHideRule(r) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = B.c.muted) }
                }
                HorizontalDivider(color = B.c.border)
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}
