@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.getbunker.rcon.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.getbunker.rcon.core.ConnState
import app.getbunker.rcon.core.ServerSession
import app.getbunker.rcon.data.HideRule
import app.getbunker.rcon.rcon.CommandGuess
import app.getbunker.rcon.rcon.ConsoleFilter
import app.getbunker.rcon.rcon.LogEntry
import app.getbunker.rcon.rcon.LogKind
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

fun kindColor(kind: LogKind, c: BunkerColors): Color = when (kind) {
    LogKind.Warning -> c.warn
    LogKind.Error -> c.err
    LogKind.Command -> c.accent
    LogKind.Info -> c.muted
    LogKind.Chat -> c.chat
    LogKind.Normal -> c.text
}

/** One console line as styled text. Chat lines show the player's name in the colour the game uses. */
@Composable
fun rememberLogText(e: LogEntry, showTime: Boolean = true): AnnotatedString {
    val c = B.c
    val app = LocalApp.current
    val tagColor = parseHex(app.settings.current.chatTagColor) ?: c.chat
    return remember(e.id, c.isLight) {
        buildAnnotatedString {
            if (showTime) withStyle(SpanStyle(color = c.dim)) { append(timeFormat.format(Date(e.timeMillis))); append("  ") }
            if (e.kind == LogKind.Chat && e.userName != null) {
                if (e.isTeam) withStyle(SpanStyle(color = c.ok, fontWeight = FontWeight.Bold)) { append("[TEAM] ") }
                if (!e.tags.isNullOrEmpty()) withStyle(SpanStyle(color = c.readable(tagColor), fontWeight = FontWeight.Bold)) { append(e.tags); append(" ") }
                val nameColor = parseHex(e.color)?.let { c.readable(it) } ?: c.chat
                withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold)) { append(e.userName); append(":") }
                append(" ")
                withStyle(SpanStyle(color = c.text)) { append(e.message.orEmpty()) }
            } else {
                withStyle(SpanStyle(color = kindColor(e.kind, c))) { append(e.text) }
            }
        }
    }
}

@Composable
fun ConsoleTab(session: ServerSession, nav: NavController, notify: (String) -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    val entries by session.log.entries.collectAsState()
    val state by session.state.collectAsState()
    val connected = state == ConnState.Connected

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showChat by rememberSaveable { mutableStateOf(true) }
    var showWarn by rememberSaveable { mutableStateOf(true) }
    var showErr by rememberSaveable { mutableStateOf(true) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var chatMode by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var lineMenu by remember { mutableStateOf<LogEntry?>(null) }
    var askWords by remember { mutableStateOf<LogEntry?>(null) }
    var pendingSay by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<PlayerTarget?>(null) }

    val filtered = remember(entries, query, showChat, showWarn, showErr, showHidden, settings.hidden) {
        val q = query.trim()
        entries.filter { e ->
            when (e.kind) {
                LogKind.Chat -> showChat
                LogKind.Warning -> showWarn
                LogKind.Error -> showErr
                else -> true
            } && (q.isEmpty() || e.text.contains(q, ignoreCase = true)) && (showHidden || !ConsoleFilter.isHidden(settings.hidden, e))
        }
    }
    val hiddenCount = remember(entries, settings.hidden) { if (settings.hidden.isEmpty()) 0 else entries.count { ConsoleFilter.isHidden(settings.hidden, it) } }

    val listState = rememberLazyListState()
    val (follow, _) = rememberFollow(listState, filtered.lastOrNull()?.id, filtered.size)

    fun submit() {
        val text = input.trim()
        if (text.isEmpty() || !connected) return
        if (chatMode) {
            scope.launch { session.sendChat(text) }
            input = ""
        } else if (!CommandGuess.looksLikeCommand(text, settings.confirmedCommands)) {
            pendingSay = text
        } else {
            session.command(text)
            app.settings.rememberCommand(text)
            input = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---- search / filters
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (searching) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, placeholder = { Text("Search the console") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = { IconButton(onClick = { query = ""; searching = false }) { Icon(Icons.Filled.Close, contentDescription = "Close search") } },
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
            } else {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(showChat, { showChat = !showChat }, label = { Text("Chat") }) }
                    item { FilterChip(showWarn, { showWarn = !showWarn }, label = { Text("Warnings") }) }
                    item { FilterChip(showErr, { showErr = !showErr }, label = { Text("Errors") }) }
                    if (hiddenCount > 0 || settings.hidden.isNotEmpty()) item {
                        FilterChip(showHidden, { showHidden = !showHidden }, label = { Text(if (showHidden) "Showing hidden" else "$hiddenCount hidden") })
                    }
                }
                IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Console menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Hide messages...") }, onClick = { menu = false; nav.navigate(Routes.HIDDEN) })
                    DropdownMenuItem(text = { Text("Share console") }, onClick = {
                        menu = false
                        val text = filtered.joinToString("\n") { "${timeFormat.format(Date(it.timeMillis))} ${it.text}" }
                        shareTextFile(context, "bunker-console.txt", text)
                    })
                    DropdownMenuItem(text = { Text("Clear console") }, onClick = { menu = false; session.log.clear() })
                }
            }
        }

        // ---- the log
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (filtered.isEmpty()) {
                EmptyState(Icons.Filled.Terminal, if (entries.isEmpty()) "Console is empty" else "Nothing to show", if (entries.isEmpty()) "Server output appears here as it happens." else "Your search or filters hide every line.")
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(filtered, key = { it.id }) { e ->
                        val text = rememberLogText(e)
                        Text(
                            text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = settings.consoleFontSize.sp,
                            lineHeight = (settings.consoleFontSize * 1.35f).sp,
                            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { lineMenu = e }, onLongClick = { copyToClipboard(context, "Console line", e.text); notify("Line copied") }).padding(vertical = 2.dp),
                        )
                    }
                }
            }
            if (!follow && filtered.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.scrollToItem(filtered.size - 1) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    containerColor = B.c.panel, contentColor = B.c.text,
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Jump to latest") }
            }
        }

        // ---- command history
        val history = settings.commandHistory
        val suggestions = remember(input, history, chatMode) {
            if (chatMode) emptyList() else if (input.isBlank()) history.take(8) else history.filter { it.startsWith(input, ignoreCase = true) && !it.equals(input, true) }.take(8)
        }
        if (suggestions.isNotEmpty()) {
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions) { s -> AssistChip(onClick = { input = s }, label = { Text(s, maxLines = 1, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }) }
            }
        }

        // ---- input
        HorizontalDivider(color = B.c.border)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { chatMode = !chatMode }) {
                Icon(if (chatMode) Icons.AutoMirrored.Filled.Chat else Icons.Filled.Terminal, contentDescription = if (chatMode) "Chat mode (tap for command)" else "Command mode (tap for chat)", tint = if (chatMode) B.c.chat else B.c.accent)
            }
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text(if (chatMode) "Say something in chat" else "Command, e.g. server.save", fontFamily = if (chatMode) FontFamily.Default else FontFamily.Monospace) },
                singleLine = true, enabled = connected, modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (chatMode) FontFamily.Default else FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = chatMode),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            IconButton(onClick = ::submit, enabled = connected && input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (connected && input.isNotBlank()) B.c.accent else B.c.dim)
            }
        }
    }

    // ---- looks like a message, not a command
    pendingSay?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingSay = null },
            title = { Text("Say this in chat?") },
            text = { Text("\"$text\" does not look like a server command. Send it as a chat message instead?") },
            confirmButton = {
                TextButton(onClick = { scope.launch { session.sendChat(text) }; input = ""; pendingSay = null }) { Text("Say in chat", color = B.c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    app.settings.confirmCommandWord(CommandGuess.firstWord(text))
                    session.command(text); app.settings.rememberCommand(text); input = ""; pendingSay = null
                }) { Text("Send as command", color = B.c.muted) }
            },
            containerColor = B.c.panel,
        )
    }

    // ---- tap a line
    lineMenu?.let { e ->
        val plugin = ConsoleFilter.pluginOf(e.text)
        AlertDialog(
            onDismissRequest = { lineMenu = null },
            title = { Text("Console line", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(e.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = B.c.muted, maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.size(8.dp))
                    LineAction("Copy line") { copyToClipboard(context, "Console line", e.text); notify("Line copied"); lineMenu = null }
                    if (e.kind == LogKind.Chat && e.message != null) LineAction("Copy message") { copyToClipboard(context, "Message", e.message); notify("Message copied"); lineMenu = null }
                    if (e.kind == LogKind.Chat && e.userId != null && e.userId.all(Char::isDigit) && e.userId != "0") {
                        LineAction("Player actions for ${e.userName}...") { target = PlayerTarget(e.userId, e.userName.orEmpty(), null, session.players.value.any { it.steamId == e.userId }); lineMenu = null }
                    }
                    if (plugin != null && e.kind != LogKind.Chat) LineAction("Hide everything from [$plugin]") {
                        app.settings.addHideRule(HideRule.KIND_PLUGIN, plugin); notify("Hiding [$plugin]"); lineMenu = null
                    }
                    if (e.kind != LogKind.Command && e.kind != LogKind.Info) LineAction("Hide lines containing...") { askWords = e; lineMenu = null }
                }
            },
            confirmButton = { TextButton(onClick = { lineMenu = null }) { Text("Close", color = B.c.muted) } },
            containerColor = B.c.panel,
        )
    }
    askWords?.let { e ->
        InputDialog(
            title = "Hide lines containing", label = "Words", initial = ConsoleFilter.pluginOf(e.text)?.let { "[$it]" } ?: e.text.take(40), allowEmpty = false, confirmText = "Hide",
            onConfirm = { if (app.settings.addHideRule(HideRule.KIND_WORDS, it)) notify("Hiding lines containing \"$it\"") },
            onDismiss = { askWords = null },
        )
    }
    PlayerActionsHost(session, target, onDismiss = { target = null }, notify = notify)
}

@Composable
private fun LineAction(text: String, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), color = B.c.text)
}
