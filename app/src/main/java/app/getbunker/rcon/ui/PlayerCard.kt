@file:OptIn(ExperimentalLayoutApi::class)

package app.getbunker.rcon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getbunker.rcon.core.ConnState
import app.getbunker.rcon.core.ServerSession
import app.getbunker.rcon.rcon.Fmt
import app.getbunker.rcon.steam.SteamLookup
import app.getbunker.rcon.steam.SteamResult
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface SteamUi {
    data object Off : SteamUi
    data object Loading : SteamUi
    data class Done(val result: SteamResult) : SteamUi
}

private val cardDate = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

/**
 * A player's card, at the top of the menu you get when you tap them: their picture and names, badges that matter (online,
 * banned, private profile, your note, on your watch list), how they are doing on your server, and what Steam says about them.
 * Steam is asked only when the card opens, sends only their Steam ID, and can be switched off in Settings.
 */
@Composable
fun PlayerCard(session: ServerSession, target: PlayerTarget) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val players by session.players.collectAsState()
    val records by session.tracker.records.collectAsState()
    val state by session.state.collectAsState()

    val live = players.firstOrNull { it.steamId == target.steamId }
    val record = records[target.steamId]
    val connected = state == ConnState.Connected
    val watched = settings.watchList.any { it.steamId == target.steamId }
    val key = remember(settings.steamApiKeyEnc) { app.settings.steamApiKey() }
    val haveKey = SteamLookup.looksLikeKey(key)

    var steam by remember(target.steamId) { mutableStateOf<SteamUi>(if (settings.steamLookups) SteamUi.Loading else SteamUi.Off) }
    LaunchedEffect(target.steamId, settings.steamLookups, key) {
        if (!settings.steamLookups) { steam = SteamUi.Off; return@LaunchedEffect }
        steam = SteamUi.Loading
        steam = SteamUi.Done(SteamLookup.get(app.http, target.steamId, if (haveKey) key else null, playerName = target.name))
    }
    val profile = (steam as? SteamUi.Done)?.result?.profile

    val name = live?.name?.ifBlank { null } ?: target.name.ifBlank { target.steamId }
    val avatar = profile?.avatarUrl?.takeIf { settings.steamLookups && SteamLookup.isAvatarAddress(it) }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        // ---- who
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))) {
                if (avatar != null) AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp))
                else Avatar(name, 72)
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val steamName = profile?.personaName
                Text(
                    if (steamName != null && !steamName.equals(name, ignoreCase = true)) "Steam name: $steamName" else target.steamId,
                    style = MaterialTheme.typography.bodySmall, color = B.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ---- badges
        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (live != null) Pill("ONLINE", B.c.ok) else Pill("NOT ON THE SERVER", B.c.muted)
            if (profile != null) {
                val (bans, bad) = SteamLookup.bans(profile)
                if (bad) Pill(bans.uppercase(Locale.ROOT), B.c.err)
                if (!profile.isPublic) Pill("STEAM PROFILE: ${SteamLookup.privacyText(profile).uppercase(Locale.ROOT)}", B.c.warn)
                if (profile.limited) Pill("LIMITED ACCOUNT", B.c.warn)
            }
            if (watched) Pill("ON YOUR WATCH LIST", B.c.warn)
            if (record?.note?.isNotBlank() == true) Pill("HAS A NOTE", B.c.chat)
        }

        // ---- your note
        if (record?.note?.isNotBlank() == true) {
            Text(
                "Your note: ${record.note}", style = MaterialTheme.typography.bodyMedium, color = B.c.warn,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }

        // ---- on your server
        CardHeading("On your server")
        CardRow("Status", if (live != null) "Online now" else if (connected) "Not on the server" else "Not connected to the server", if (live != null) B.c.ok else B.c.muted)
        if (live != null) {
            CardRow("Ping", "${live.ping} ms")
            CardRow("Health", "${live.health.toInt()} HP")
            CardRow("This visit", Fmt.duration(live.connectedSeconds))
            CardRow("IP address", stripPort(live.address).ifEmpty { "-" })
        }
        CardRow("First seen", record?.firstSeenMillis?.takeIf { it > 0 }?.let { cardDate.format(Date(it)) } ?: "-")
        CardRow("Last seen", if (live != null) "Now" else record?.lastSeenMillis?.takeIf { it > 0 }?.let { cardDate.format(Date(it)) } ?: "-")
        CardRow("Total time", record?.let { Fmt.duration(it.totalSeconds.toLong()) } ?: "-")
        CardRow("Visits", record?.sessions?.toString() ?: "-")

        // ---- Steam
        CardHeading("Steam")
        CardRow("Steam ID", target.steamId)
        when (val s = steam) {
            SteamUi.Off -> Text("Steam lookups are switched off in Settings.", style = MaterialTheme.typography.bodySmall, color = B.c.muted, modifier = Modifier.padding(top = 4.dp))
            SteamUi.Loading -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = B.c.accent)
                Spacer(Modifier.size(10.dp))
                Text("Looking up Steam...", style = MaterialTheme.typography.bodySmall, color = B.c.muted)
            }
            is SteamUi.Done -> {
                val p = s.result.profile
                if (p == null) {
                    Text(s.result.message, style = MaterialTheme.typography.bodySmall, color = B.c.muted, modifier = Modifier.padding(top = 4.dp))
                } else {
                    CardRow("Profile", SteamLookup.privacyText(p), if (p.isPublic) Color.Unspecified else B.c.warn)
                    CardRow("Account age", SteamLookup.accountAge(p), if (p.accountCreatedMillis == null && p.memberSince == null) B.c.muted else Color.Unspecified)
                    CardRow("Rust hours", SteamLookup.rustLine(p, haveKey), if (p.rustHours == null) B.c.muted else Color.Unspecified)
                    val (bans, bad) = SteamLookup.bans(p)
                    CardRow("Bans", bans, if (bad) B.c.err else B.c.ok)
                    CardRow("Account", if (p.limited) "Limited (new or unpaid)" else "Normal")
                    CardRow("Location", p.location ?: p.countryCode ?: "Hidden", if (p.location == null && p.countryCode == null) B.c.muted else Color.Unspecified)
                    Text(
                        s.result.message.ifEmpty {
                            if (p.hasKeyData) "From the player's Steam profile and the Steam Web API."
                            else if (p.isPublic) "From the player's public Steam profile. Add a free Steam key in Settings for hours, account age and the full ban record."
                            else "This profile is not public, so Steam shares very little."
                        },
                        style = MaterialTheme.typography.bodySmall, color = B.c.muted, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardHeading(text: String) {
    Text(
        text.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelMedium, color = B.c.muted, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun CardRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(0.38f), color = B.c.muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(0.62f), color = if (color == Color.Unspecified) B.c.text else color, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "1.2.3.4:5678" -> "1.2.3.4". An address with more than one colon (IPv6) is left alone. */
internal fun stripPort(address: String): String {
    val a = address.trim()
    val colon = a.lastIndexOf(':')
    return if (colon > 0 && a.indexOf(':') == colon) a.substring(0, colon) else a
}
