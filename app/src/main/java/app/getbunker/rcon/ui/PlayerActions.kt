@file:OptIn(ExperimentalMaterial3Api::class)

package app.getbunker.rcon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PersonPinCircle
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getbunker.rcon.core.ConnState
import app.getbunker.rcon.core.ServerSession
import app.getbunker.rcon.rcon.Cmd
import kotlinx.coroutines.launch

/** Who the player menu is for. Players who are not online can still be copied, looked up, banned or given a note. */
data class PlayerTarget(val steamId: String, val name: String, val ping: Int? = null, val online: Boolean = true)

private enum class PlayerDialog { Kick, Ban, Mute, Unmute, TeleportTo, Bring, Give, Note, Permissions }

/**
 * The menu that opens when you tap a player: copy, Steam profile, kick, ban, mute, teleport, give items, notes and
 * admin permissions. [notify] is called with a short result ("Kick sent") for a snackbar.
 */
@Composable
fun PlayerActionsHost(session: ServerSession, target: PlayerTarget?, onDismiss: () -> Unit, notify: (String) -> Unit) {
    if (target == null) return
    val context = LocalContext.current
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsState()
    val players by session.players.collectAsState()
    val connected = state == ConnState.Connected
    val onlineNow = connected && target.online
    val safeId = target.steamId.isNotEmpty() && target.steamId.all(Char::isDigit)

    var sheetOpen by remember(target) { mutableStateOf(true) }
    var dialog by remember(target) { mutableStateOf<PlayerDialog?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun send(command: String, done: String, alsoSave: Boolean = false) {
        // The app's scope, not this menu's: the menu closes as soon as you confirm, but the reply should still be reported.
        app.scope.launch {
            val reply = session.run(command)
            if (alsoSave) session.command("server.writecfg")
            notify(reply?.lineSequence()?.firstOrNull()?.take(120)?.let { "$done: $it" } ?: done)
        }
    }

    fun closeDialog() { dialog = null; onDismiss() }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = B.c.panel) {
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(target.name.ifBlank { target.steamId }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    val sub = buildString {
                        append(target.steamId)
                        append("  ·  ")
                        append(if (target.online && target.ping != null) "${target.ping} ms" else if (target.online) "online" else "offline")
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = B.c.muted)
                    val note = app.sessions.session.collectAsState().value?.tracker?.records?.collectAsState()?.value?.get(target.steamId)?.note.orEmpty()
                    if (note.isNotEmpty()) { Spacer(Modifier.size(6.dp)); Text("Note: $note", style = MaterialTheme.typography.bodySmall, color = B.c.warn) }
                }
                Spacer(Modifier.size(8.dp))
                HorizontalDivider(color = B.c.border)

                ActionRow(Icons.Filled.ContentCopy, "Copy name") { copyToClipboard(context, "Name", target.name); notify("Name copied"); onDismiss() }
                ActionRow(Icons.Filled.ContentCopy, "Copy Steam ID") { copyToClipboard(context, "Steam ID", target.steamId); notify("Steam ID copied"); onDismiss() }
                ActionRow(Icons.Filled.OpenInBrowser, "Open Steam profile", enabled = safeId) { openUrl(context, "https://steamcommunity.com/profiles/${target.steamId}"); onDismiss() }
                HorizontalDivider(color = B.c.border)

                ActionRow(Icons.Filled.ExitToApp, "Kick...", enabled = onlineNow && safeId) { sheetOpen = false; dialog = PlayerDialog.Kick }
                ActionRow(Icons.Filled.Block, "Ban...", color = B.c.err, enabled = connected && safeId) { sheetOpen = false; dialog = PlayerDialog.Ban }
                ActionRow(Icons.Filled.VolumeOff, "Mute", enabled = onlineNow && safeId) { sheetOpen = false; dialog = PlayerDialog.Mute }
                ActionRow(Icons.Filled.VolumeUp, "Unmute", enabled = onlineNow && safeId) { sheetOpen = false; dialog = PlayerDialog.Unmute }
                HorizontalDivider(color = B.c.border)

                val others = players.filter { it.steamId != target.steamId && it.steamId.all(Char::isDigit) }
                ActionRow(Icons.Filled.NearMe, "Teleport ${target.name.short()} to a player...", enabled = onlineNow && safeId && others.isNotEmpty()) { sheetOpen = false; dialog = PlayerDialog.Bring }
                ActionRow(Icons.Filled.PersonPinCircle, "Teleport to ${target.name.short()}...", enabled = onlineNow && safeId && others.isNotEmpty()) { sheetOpen = false; dialog = PlayerDialog.TeleportTo }
                ActionRow(Icons.Filled.CardGiftcard, "Give item...", enabled = onlineNow && safeId) { sheetOpen = false; dialog = PlayerDialog.Give }
                HorizontalDivider(color = B.c.border)

                ActionRow(Icons.Filled.EditNote, "Add or edit note...") { sheetOpen = false; dialog = PlayerDialog.Note }
                ActionRow(Icons.Filled.AdminPanelSettings, "Admin permissions...", enabled = connected && safeId) { sheetOpen = false; dialog = PlayerDialog.Permissions }
                Spacer(Modifier.size(16.dp))
            }
        }
    }

    val name = target.name.ifBlank { target.steamId }
    when (dialog) {
        PlayerDialog.Kick -> InputDialog(
            title = "Kick $name", label = "Reason (the player sees this)", initial = "Kicked by admin", confirmText = "Kick",
            onConfirm = { r -> send("kick \"${target.steamId}\" ${Cmd.quote(r.ifBlank { "Kicked by admin" })}", "Kick sent for $name") },
            onDismiss = ::closeDialog,
        )
        PlayerDialog.Ban -> InputDialog(
            title = "Ban $name", message = "${target.steamId}\nThis bans the player permanently.", label = "Reason (the player sees this)",
            initial = "Banned by admin", confirmText = "Ban",
            onConfirm = { r ->
                send("banid ${target.steamId} ${Cmd.quote(target.name.ifBlank { target.steamId })} ${Cmd.quote(r.ifBlank { "Banned by admin" })}", "Ban sent for $name", alsoSave = true)
            },
            onDismiss = ::closeDialog,
        )
        PlayerDialog.Mute -> ConfirmDialog("Mute player", "Mute $name in chat?", "Mute", onConfirm = { send("mute \"${target.steamId}\"", "Mute sent for $name") }, onDismiss = ::closeDialog)
        PlayerDialog.Unmute -> ConfirmDialog("Unmute player", "Unmute $name?", "Unmute", onConfirm = { send("unmute \"${target.steamId}\"", "Unmute sent for $name") }, onDismiss = ::closeDialog)
        PlayerDialog.Bring, PlayerDialog.TeleportTo -> {
            val bring = dialog == PlayerDialog.Bring
            val others = players.filter { it.steamId != target.steamId && it.steamId.all(Char::isDigit) }.sortedBy { it.name.lowercase() }
            AlertDialog(
                onDismissRequest = ::closeDialog,
                title = { Text(if (bring) "Teleport $name to..." else "Teleport to $name from...") },
                text = {
                    LazyColumn {
                        items(others, key = { it.steamId }) { o ->
                            Text(
                                o.name.ifBlank { o.steamId },
                                Modifier.fillMaxWidth().clickable {
                                    val (who, to) = if (bring) target.steamId to o.steamId else o.steamId to target.steamId
                                    send("teleport \"$who\" \"$to\"", "Teleport sent")
                                    closeDialog()
                                }.padding(vertical = 12.dp),
                            )
                            HorizontalDivider(color = B.c.border)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = ::closeDialog) { Text("Cancel", color = B.c.muted) } },
                containerColor = B.c.panel,
            )
        }
        PlayerDialog.Give -> GiveItemDialog(
            title = "Give item to $name",
            onGive = { item, amount -> send("inventory.giveto \"${target.steamId}\" \"$item\" $amount", "Gave $amount x $item to $name") },
            onDismiss = ::closeDialog,
        )
        PlayerDialog.Note -> InputDialog(
            title = "Note about $name", message = "A private note, saved on this phone and shown in the player database.",
            label = "Note", initial = session.tracker.noteFor(target.steamId), confirmText = "Save", singleLine = false,
            onConfirm = { session.tracker.setNote(target.steamId, target.name, it); notify("Note saved") },
            onDismiss = ::closeDialog,
        )
        PlayerDialog.Permissions -> {
            AlertDialog(
                onDismissRequest = ::closeDialog,
                title = { Text("Admin permissions") },
                text = {
                    Column {
                        Text("Changes what $name can do on the server. The server config is saved afterwards.", color = B.c.muted)
                        Spacer(Modifier.size(8.dp))
                        val q = Cmd.quote(target.name.ifBlank { target.steamId })
                        PermRow("Make moderator") { send("moderatorid ${target.steamId} $q \"Bunker\"", "Moderator added", true); closeDialog() }
                        PermRow("Remove moderator") { send("removemoderator ${target.steamId}", "Moderator removed", true); closeDialog() }
                        PermRow("Make owner (full admin)", B.c.err) { send("ownerid ${target.steamId} $q \"Bunker\"", "Owner added", true); closeDialog() }
                        PermRow("Remove owner") { send("removeowner ${target.steamId}", "Owner removed", true); closeDialog() }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = ::closeDialog) { Text("Cancel", color = B.c.muted) } },
                containerColor = B.c.panel,
            )
        }
        null -> {}
    }
}

@Composable
private fun PermRow(text: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), color = if (color == Color.Unspecified) B.c.text else color)
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, color: Color = Color.Unspecified, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = when {
        !enabled -> B.c.dim
        color == Color.Unspecified -> B.c.text
        else -> color
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) (if (color == Color.Unspecified) B.c.muted else color) else B.c.dim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(16.dp))
        Text(text, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun String.short(): String = if (length <= 18) this else take(17) + "…"
