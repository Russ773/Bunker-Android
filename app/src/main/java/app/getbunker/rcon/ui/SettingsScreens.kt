@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.getbunker.rcon.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import app.getbunker.rcon.R
import app.getbunker.rcon.core.ConnectionService
import app.getbunker.rcon.data.ThemeMode
import app.getbunker.rcon.rcon.Cmd
import kotlinx.coroutines.launch

@Composable
private fun SettingSwitch(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = B.c.muted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = B.c.accent, checkedThumbColor = Color.White))
    }
}

@Composable
fun SettingsScreen(nav: NavController) {
    val app = LocalApp.current
    val context = LocalContext.current
    val s by app.settings.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun say(m: String) { scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } }

    Scaffold(containerColor = B.c.bg, topBar = { BunkerTopBar("Settings", onBack = { nav.popBackStack() }) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {

            SectionHeader("Appearance")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "Follow phone").forEach { (m, label) ->
                    FilterChip(s.theme == m, { app.settings.update { it.copy(theme = m) } }, label = { Text(label) })
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Console text size: ${s.consoleFontSize} sp")
                Slider(
                    value = s.consoleFontSize.toFloat(), onValueChange = { v -> app.settings.update { it.copy(consoleFontSize = v.toInt()) } },
                    valueRange = 9f..22f, steps = 12, colors = SliderDefaults.colors(thumbColor = B.c.accent, activeTrackColor = B.c.accent),
                )
            }
            SettingSwitch("Keep the screen on", "While Bunker is open", s.keepScreenOn) { v -> app.settings.update { it.copy(keepScreenOn = v) } }

            SectionHeader("Connection")
            SettingSwitch("Connect on launch", "Open your last server when Bunker starts", s.autoConnect) { v -> app.settings.update { it.copy(autoConnect = v) } }
            SettingSwitch("Reconnect automatically", "Try again if the connection drops (a restart, a wobble, a tunnel)", s.autoReconnect) { v -> app.settings.update { it.copy(autoReconnect = v) } }
            SettingSwitch("Stay connected in the background", "Shows a quiet notification so Android keeps the connection alive while the screen is off", s.keepConnected) { v ->
                app.settings.update { it.copy(keepConnected = v) }
                if (!v) ConnectionService.stop(app.context)
                else if (app.sessions.session.value != null) ConnectionService.start(app.context)
            }

            SectionHeader("Notifications")
            SettingSwitch("Connection lost", "Tell me when the server connection drops while Bunker is in the background", s.notifyConnectionLost) { v -> app.settings.update { it.copy(notifyConnectionLost = v) } }
            SettingSwitch("Chat watch-words", "Tell me when a chat message contains one of the words below", s.notifyWatchWords) { v -> app.settings.update { it.copy(notifyWatchWords = v) } }
            OutlinedTextField(
                s.watchWords, { v -> app.settings.update { it.copy(watchWords = v) } }, label = { Text("Watch-words, separated by commas") }, placeholder = { Text("hack, cheat, admin") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            SettingSwitch("Player joins", "A notification each time someone joins (can be noisy)", s.notifyJoins) { v -> app.settings.update { it.copy(notifyJoins = v) } }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text("Android notification settings") }

            SectionHeader("Chat as yourself")
            Text(
                "Set a name and Bunker sends your chat messages with that name, colour and Steam picture instead of SERVER. This needs the small Bunker Chat plugin on your Carbon or Oxide server; without it Bunker uses the plain say command with your name in the text.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = B.c.muted,
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(s.chatName, { v -> app.settings.update { it.copy(chatName = v.take(40)) } }, label = { Text("Chat name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    s.chatSteamId, { v -> app.settings.update { it.copy(chatSteamId = v.filter(Char::isDigit).take(17)) } }, label = { Text("Your Steam ID (for your avatar)") }, singleLine = true,
                    isError = s.chatSteamId.isNotEmpty() && !Cmd.isSteamId(s.chatSteamId), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(s.chatColor, { v -> app.settings.update { it.copy(chatColor = v.take(9)) } }, label = { Text("Name colour") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.size(12.dp))
                    Spacer(Modifier.size(36.dp).clip(CircleShape).background(parseHex(s.chatColor) ?: B.c.dim))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(s.chatTagColor, { v -> app.settings.update { it.copy(chatTagColor = v.take(9)) } }, label = { Text("Plugin tag colour, e.g. [ADMIN]") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.size(12.dp))
                    Spacer(Modifier.size(36.dp).clip(CircleShape).background(parseHex(s.chatTagColor) ?: B.c.dim))
                }
            }
            SettingSwitch("Use the Bunker Chat plugin", "Falls back to say automatically if it is not installed", s.chatUsePlugin) { v -> app.settings.update { it.copy(chatUsePlugin = v) } }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    copyToClipboard(context, "BunkerChat.cs", pluginSource(context)); say("Plugin code copied. Save it as BunkerChat.cs in your server's plugins folder.")
                }) { Text("Copy plugin code") }
                OutlinedButton(onClick = { shareTextFile(context, "BunkerChat.cs", pluginSource(context)) }) { Text("Share file") }
            }

            SectionHeader("Items")
            SettingSwitch("Keep the item list up to date", "Downloads Rust's item list and pictures from GitHub (bobosneefdev/rust-items-json), at most once a day", s.useOnlineItemList) { v -> app.settings.update { it.copy(useOnlineItemList = v) } }

            SectionHeader("Security")
            SettingSwitch("Lock Bunker", "Ask for your fingerprint, face or screen lock when opening the app", s.appLock) { v ->
                val activity = context as? FragmentActivity
                if (v && activity != null && !canLockApp(activity)) say("Set up a screen lock or fingerprint in Android settings first.")
                else app.settings.update { it.copy(appLock = v) }
            }
            SettingSwitch("Hide from recent apps and screenshots", "Blocks screenshots and blanks Bunker in the app switcher", s.secureScreen) { v -> app.settings.update { it.copy(secureScreen = v) } }
            Text(
                "RCON passwords are encrypted with a key held in the Android Keystore and never leave your phone, except to your own server.",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = B.c.muted,
            )
            Spacer(Modifier.size(24.dp))
        }
    }
}

private fun pluginSource(context: android.content.Context): String =
    try { context.assets.open("BunkerChat.cs").bufferedReader().use { it.readText() } } catch (_: Exception) { "" }

@Composable
fun AboutScreen(nav: NavController) {
    val context = LocalContext.current
    val version = remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() } catch (_: Exception) { "" } }
    Scaffold(containerColor = B.c.bg, topBar = { BunkerTopBar("About Bunker", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.bunker_wordmark), contentDescription = "Bunker, Rust server admin", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)))
            Spacer(Modifier.size(12.dp))
            Text("Version $version", color = B.c.muted)
            Spacer(Modifier.size(16.dp))
            Text(
                "Bunker is the admin console for your Rust server, now in your pocket. It talks to your server over WebRCON and nowhere else: no accounts, no analytics.",
                color = B.c.text,
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.fillMaxWidth()) {
                LinkRow("Website", "getbunker.app") { openUrl(context, "https://getbunker.app") }
                LinkRow("Source code", "github.com/Russ773/Bunker") { openUrl(context, "https://github.com/Russ773/Bunker") }
                LinkRow("Privacy", "How Bunker handles your data") { openUrl(context, "https://getbunker.app/privacy.html") }
                LinkRow("Item list and pictures", "bobosneefdev/rust-items-json") { openUrl(context, "https://github.com/bobosneefdev/rust-items-json") }
            }
            Spacer(Modifier.size(16.dp))
            Text(
                "Bunker is an independent community tool. Rust is a trademark of Facepunch Studios; Bunker is not affiliated with or endorsed by Facepunch. Item data and pictures belong to Facepunch Studios.",
                style = MaterialTheme.typography.bodySmall, color = B.c.muted,
            )
        }
    }
}

@Composable
private fun LinkRow(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, fontWeight = FontWeight.Medium)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = B.c.accent)
    }
    HorizontalDivider(color = B.c.border)
}
