@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.getbunker.rcon.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.getbunker.rcon.core.ConnState
import app.getbunker.rcon.core.ServerSession
import app.getbunker.rcon.rcon.LogEntry
import app.getbunker.rcon.rcon.LogKind
import kotlinx.coroutines.launch

/** Chat only: what players say (with their game colours, [TEAM] and plugin tags), and a box to talk back. */
@Composable
fun ChatTab(session: ServerSession, notify: (String) -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    val entries by session.log.entries.collectAsState()
    val state by session.state.collectAsState()
    val connected = state == ConnState.Connected
    var input by rememberSaveable { mutableStateOf("") }
    var target by remember { mutableStateOf<PlayerTarget?>(null) }

    val chat = remember(entries) { entries.filter { it.kind == LogKind.Chat } }
    val words = remember(settings.watchWords) { ServerSession.watchWordList(settings) }
    val listState = rememberLazyListState()
    val (follow, _) = rememberFollow(listState, chat.lastOrNull()?.id, chat.size)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (chat.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Filled.Chat, "No chat yet", "Messages from players appear here. Say something below to talk back" +
                    if (settings.chatName.isBlank()) " (it shows as SERVER until you set a chat name in Settings)." else ".")
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(chat, key = { it.id }) { e ->
                        val hit = words.isNotEmpty() && words.any { (e.message ?: e.text).contains(it, ignoreCase = true) }
                        ChatLine(e, hit, onClick = {
                            val id = e.userId
                            if (id != null && id.length > 5 && id.all(Char::isDigit) && id != "0") {
                                target = PlayerTarget(id, e.userName.orEmpty(), null, session.players.value.any { it.steamId == id })
                            }
                        }, onLongClick = { copyToClipboard(context, "Chat", e.text); notify("Message copied") })
                    }
                }
            }
            if (!follow && chat.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.scrollToItem(chat.size - 1) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), containerColor = B.c.panel, contentColor = B.c.text,
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Jump to latest") }
            }
        }
        HorizontalDivider(color = B.c.border)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, enabled = connected, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text(if (settings.chatName.isBlank()) "Message as SERVER" else "Message as ${settings.chatName}") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank()) { val t = input.trim(); input = ""; scope.launch { session.sendChat(t) } } }),
            )
            IconButton(
                onClick = { val t = input.trim(); input = ""; scope.launch { session.sendChat(t) } },
                enabled = connected && input.isNotBlank(),
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (connected && input.isNotBlank()) B.c.accent else B.c.dim) }
        }
    }
    PlayerActionsHost(session, target, onDismiss = { target = null }, notify = notify)
}

@Composable
private fun ChatLine(e: LogEntry, highlight: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val text = rememberLogText(e)
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (highlight) Modifier.background(B.c.matchBack) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}
