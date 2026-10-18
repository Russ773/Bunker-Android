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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.getbunker.rcon.core.ConnState
import app.getbunker.rcon.core.ServerSession
import app.getbunker.rcon.rcon.Cmd
import app.getbunker.rcon.rcon.PluginList
import app.getbunker.rcon.rcon.PluginListParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Carbon and Oxide plugin lists as cards you can sort, with reload, unload and load. */
@Composable
fun PluginsScreen(session: ServerSession, nav: NavController) {
    val state by session.state.collectAsState()
    val connected = state == ConnState.Connected
    val scope = rememberCoroutineScope()

    var list by remember { mutableStateOf<PluginList?>(null) }
    var raw by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var sortColumn by remember { mutableIntStateOf(-1) } // -1 = the order the server printed
    var descending by remember { mutableStateOf(true) }
    var sortMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<List<String>?>(null) }
    var confirmUnload by remember { mutableStateOf<String?>(null) }
    var askLoad by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        error = null
        try {
            // Carbon first, then Oxide. Anything else is shown as the server's own text.
            val carbon = session.query("c.plugins", 10_000)
            val parsed = PluginListParser.tryParse(carbon)
            if (parsed != null) { list = parsed; raw = null }
            else {
                val oxide = session.query("oxide.plugins", 10_000)
                val p2 = PluginListParser.tryParse(oxide)
                if (p2 != null) { list = p2; raw = null }
                else { list = null; raw = oxide.ifBlank { carbon }.ifBlank { "The server did not answer with a plugin list. Is Carbon or Oxide installed?" } }
            }
        } catch (e: Exception) { error = e.message ?: "The server did not answer" }
        loading = false
    }
    LaunchedEffect(connected) { if (connected) reload() }

    ToolScreen(
        "Plugins", nav,
        actions = {
            val l = list
            if (l != null) Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort") }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    DropdownMenuItem(text = { Text("Server order", color = if (sortColumn < 0) B.c.accent else B.c.text) }, onClick = { sortColumn = -1; sortMenu = false })
                    l.columns.forEachIndexed { i, name ->
                        DropdownMenuItem(
                            text = { Text(name + if (sortColumn == i) (if (descending) "  (high to low)" else "  (low to high)") else "", color = if (sortColumn == i) B.c.accent else B.c.text) },
                            onClick = { if (sortColumn == i) descending = !descending else { sortColumn = i; descending = i != l.nameColumn && i != 0 }; sortMenu = false },
                        )
                    }
                }
            }
            IconButton(onClick = { scope.launch { reload() } }, enabled = connected && !loading) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
        },
        fab = { if (list != null) FloatingActionButton(onClick = { askLoad = true }, containerColor = B.c.accent, contentColor = Color.White) { Icon(Icons.Filled.Add, contentDescription = "Load a plugin") } },
    ) { pad, notify ->
        val prefix = if (list?.framework == "Oxide") "oxide" else "c"

        fun act(verb: String, name: String, done: String) {
            session.let { s ->
                scope.launch {
                    val reply = s.run("$prefix.$verb ${Cmd.quote(name)}", 6000)
                    notify(reply?.lineSequence()?.firstOrNull()?.take(100) ?: done)
                    delay(1500)
                    reload()
                }
            }
        }

        Column(Modifier.padding(pad).fillMaxSize()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = B.c.accent)
            val l = list
            when {
                !connected -> EmptyState(Icons.Filled.Extension, "Not connected", "Connect to a server to see its plugins.")
                error != null -> EmptyState(Icons.Filled.Extension, "Could not load plugins", error.orEmpty())
                raw != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("This server did not return a Carbon or Oxide plugin list. It said:", color = B.c.muted)
                    Spacer(Modifier.size(8.dp))
                    Text(raw.orEmpty(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                l == null -> if (!loading) Box(Modifier.fillMaxSize())
                else -> {
                    val rows = remember(l, sortColumn, descending) { if (sortColumn < 0) l.rows else PluginListParser.sorted(l, sortColumn, descending) }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Text(l.summary, color = B.c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp)) }
                        items(rows, key = { it.joinToString("|") }) { row -> PluginCard(l, row) { selected = row } }

                        if (l.unloaded.isNotEmpty()) {
                            item { SectionHeader("Not loaded", Modifier.padding(start = 0.dp)) }
                            items(l.unloaded, key = { "u$it" }) { file ->
                                Panel(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(file, Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                                        TextButton(onClick = { act("load", file.removeSuffix(".cs"), "Load sent for $file") }) { Text("Load", color = B.c.accent) }
                                    }
                                }
                            }
                        }
                        if (l.failed.isNotEmpty()) {
                            item { SectionHeader("Failed to compile", Modifier.padding(start = 0.dp)) }
                            items(l.failed, key = { "f${it.file}${it.line}${it.message}" }) { f ->
                                Panel(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row {
                                            Text(f.file, fontWeight = FontWeight.SemiBold, color = B.c.err, modifier = Modifier.weight(1f))
                                            if (f.line.isNotEmpty()) Text("line ${f.line}", style = MaterialTheme.typography.bodySmall, color = B.c.muted)
                                        }
                                        Text(f.message, style = MaterialTheme.typography.bodySmall, color = B.c.muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        selected?.let { row ->
            val l = list!!
            val name = l.nameOf(row)
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text(name) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        l.columns.forEachIndexed { i, col ->
                            if (i != l.nameColumn && row[i].isNotEmpty()) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text(col, Modifier.weight(0.4f), color = B.c.muted, style = MaterialTheme.typography.bodySmall)
                                    Text(row[i], Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { selected = null; act("reload", name, "Reload sent for $name") }) { Text("Reload", color = B.c.accent, fontWeight = FontWeight.SemiBold) } },
                dismissButton = {
                    Row {
                        TextButton(onClick = { selected = null; confirmUnload = name }) { Text("Unload", color = B.c.err) }
                        TextButton(onClick = { selected = null }) { Text("Close", color = B.c.muted) }
                    }
                },
                containerColor = B.c.panel,
            )
        }
        confirmUnload?.let { n ->
            ConfirmDialog("Unload plugin", "Unload $n? Its features stop working until it is loaded again.", "Unload", destructive = true, onConfirm = { act("unload", n, "Unload sent for $n") }, onDismiss = { confirmUnload = null })
        }
        if (askLoad) InputDialog(
            "Load a plugin", "Plugin name", message = "Names are case sensitive.", confirmText = "Load", allowEmpty = false,
            onConfirm = { act("load", it, "Load sent for $it") }, onDismiss = { askLoad = false },
        )
    }
}

@Composable
private fun PluginCard(list: PluginList, row: List<String>, onClick: () -> Unit) {
    val col = { name: String -> list.columns.indexOf(name).let { if (it >= 0) row[it] else "" } }
    val version = col("Version")
    val author = col("Author")
    val type = col("Type")
    val extras = list.columns.mapIndexedNotNull { i, c ->
        if (i == list.nameColumn || c in setOf("Type", "Author", "Version", "File") || row[i].isEmpty()) null else c to row[i]
    }
    Panel(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(list.nameOf(row), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (version.isNotEmpty()) { Spacer(Modifier.size(8.dp)); Pill(version, B.c.chat) }
            }
            if (author.isNotEmpty() || type.isNotEmpty()) {
                Text(listOf(author, type).filter { it.isNotEmpty() }.distinct().joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = B.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (extras.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    extras.take(4).forEach { (c, v) ->
                        Text(buildLabel(c, v), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun buildLabel(name: String, value: String): AnnotatedString {
    val dim = B.c.dim
    return buildAnnotatedString {
        withStyle(SpanStyle(color = dim)) { append("$name ") }
        append(value)
    }
}
