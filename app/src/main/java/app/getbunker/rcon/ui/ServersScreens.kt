@file:OptIn(ExperimentalMaterial3Api::class)

package app.getbunker.rcon.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.getbunker.rcon.R
import app.getbunker.rcon.core.ConnState
import app.getbunker.rcon.data.ServerProfile
import app.getbunker.rcon.rcon.RconClient
import app.getbunker.rcon.rcon.RconParsers
import kotlinx.coroutines.launch

@Composable
fun ServersScreen(nav: NavController) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val active by app.sessions.session.collectAsState()
    var deleting by remember { mutableStateOf<ServerProfile?>(null) }

    Scaffold(
        containerColor = B.c.bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.bunker_logo), contentDescription = null, modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.size(10.dp))
                        Text("BUNKER", fontWeight = FontWeight.Black, letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp))
                    }
                },
                actions = { IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = B.c.bg),
            )
        },
        floatingActionButton = {
            if (settings.servers.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.edit()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add server") },
                containerColor = B.c.accent,
                contentColor = androidx.compose.ui.graphics.Color.White,
            )
        },
    ) { pad ->
        if (settings.servers.isEmpty()) {
            Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.bunker_wordmark), contentDescription = "Bunker, Rust server admin", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)))
                Spacer(Modifier.size(20.dp))
                Text("Add your first server", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Bunker talks to your Rust server over WebRCON. Turn it on with these startup parameters, then restart the server:",
                    color = B.c.muted,
                )
                Spacer(Modifier.size(10.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "+rcon.web 1 +rcon.port 28016 +rcon.password \"choose-a-strong-password\"",
                        modifier = Modifier.padding(14.dp),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.size(20.dp))
                Button(onClick = { nav.navigate(Routes.edit()) }, colors = ButtonDefaults.buttonColors(containerColor = B.c.accent)) { Text("Add a server") }
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(settings.servers, key = { it.id }) { server ->
                    ServerCard(
                        server = server,
                        isActive = active?.profile?.id == server.id,
                        onOpen = {
                            app.sessions.open(server.id)
                            nav.navigate(Routes.SESSION)
                        },
                        onEdit = { nav.navigate(Routes.edit(server.id)) },
                        onDelete = { deleting = server },
                        onDisconnect = { app.sessions.close() },
                    )
                }
            }
        }
    }

    deleting?.let { s ->
        ConfirmDialog(
            title = "Delete server",
            text = "Remove ${s.displayName} from this phone? Its saved password and player notes are deleted too.",
            confirmText = "Delete", destructive = true,
            onConfirm = {
                if (active?.profile?.id == s.id) app.sessions.close()
                app.settings.deleteServer(s.id)
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ServerCard(server: ServerProfile, isActive: Boolean, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onDisconnect: () -> Unit) {
    val app = LocalApp.current
    val session by app.sessions.session.collectAsState()
    var menu by remember { mutableStateOf(false) }
    val liveState = if (isActive) session?.state?.collectAsState()?.value else null
    val info = if (isActive) session?.info?.collectAsState()?.value else null

    Panel(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(server.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(server.address + if (server.useTls) "  ·  secure" else "", style = MaterialTheme.typography.bodySmall, color = B.c.muted)
                if (isActive) {
                    Spacer(Modifier.size(8.dp))
                    when (liveState) {
                        ConnState.Connected -> Pill("Connected" + (info?.let { "  ·  ${it.players}/${it.maxPlayers} players" } ?: ""), B.c.ok)
                        ConnState.Connecting -> Pill("Connecting...", B.c.warn)
                        else -> Pill("Not connected", B.c.err)
                    }
                }
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    if (isActive) DropdownMenuItem(text = { Text("Disconnect") }, onClick = { menu = false; onDisconnect() })
                    DropdownMenuItem(text = { Text("Delete", color = B.c.err) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------- add / edit

@Composable
fun EditServerScreen(nav: NavController, id: String?) {
    val app = LocalApp.current
    val existing = remember(id) { id?.let { sid -> app.settings.current.servers.firstOrNull { it.id == sid } } }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var host by remember { mutableStateOf(existing?.host.orEmpty()) }
    var port by remember { mutableStateOf((existing?.port ?: 28016).toString()) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var tls by remember { mutableStateOf(existing?.useTls ?: false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun cleanedHost(): Pair<String, Int?> {
        var h = host.trim().removePrefix("ws://").removePrefix("wss://").removePrefix("http://").removePrefix("https://").trimEnd('/')
        var p: Int? = null
        // "1.2.3.4:28016" typed in the address box
        if (h.count { it == ':' } == 1) {
            val (a, b) = h.split(':')
            b.toIntOrNull()?.let { p = it; h = a }
        }
        return h to p
    }

    val (hostClean, hostPort) = cleanedHost()
    val portNumber = hostPort ?: port.toIntOrNull()
    val valid = hostClean.isNotBlank() && portNumber != null && portNumber in 1..65535 && (existing != null || password.isNotEmpty())

    fun profile(): ServerProfile = (existing ?: ServerProfile()).copy(
        name = name.trim(), host = hostClean, port = portNumber ?: 28016, useTls = tls,
    )

    fun test() {
        testing = true
        result = null
        scope.launch {
            val client = RconClient(app.http)
            val pw = if (password.isNotEmpty()) password else existing?.let { app.settings.password(it) }.orEmpty()
            result = try {
                client.connect(hostClean, portNumber ?: 28016, pw, tls)
                val info = RconParsers.parseInfo(client.query("serverinfo"))
                if (info != null) true to "Connected to ${info.hostname.ifBlank { "the server" }}  ·  ${info.players}/${info.maxPlayers} players"
                else true to "Connected. The server did not return server info, but the password was accepted."
            } catch (e: Exception) {
                false to (e.message ?: "Could not connect")
            } finally {
                client.disconnect()
            }
            testing = false
        }
    }

    fun save(connect: Boolean) {
        val saved = app.settings.saveServer(profile(), password.ifEmpty { null }, makeActive = true)
        if (connect) {
            app.sessions.open(saved.id)
            nav.navigate(Routes.SESSION) { popUpTo(Routes.SERVERS) { inclusive = false } }
        } else if (!nav.popBackStack()) nav.navigate(Routes.SERVERS)
    }

    Scaffold(
        containerColor = B.c.bg,
        topBar = { BunkerTopBar(if (existing == null) "Add server" else "Edit server", onBack = { if (!nav.popBackStack()) nav.navigate(Routes.SERVERS) }) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (existing == null && app.settings.current.servers.isEmpty()) {
                Text("Enter the details from your server's startup parameters (+rcon.web 1, +rcon.port, +rcon.password).", color = B.c.muted, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(name, { name = it }, label = { Text("Server name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                host, { host = it; result = null }, label = { Text("Server address") }, placeholder = { Text("123.45.67.89 or play.example.com") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit).take(5); result = null }, label = { Text("RCON port") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                password, { password = it; result = null },
                label = { Text(if (existing != null) "RCON password (leave blank to keep)" else "RCON password") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Show or hide password")
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Use secure connection (wss)")
                    Text("Only if your server sits behind a TLS proxy. Normal Rust servers use plain WebRCON.", style = MaterialTheme.typography.bodySmall, color = B.c.muted)
                }
                Switch(checked = tls, onCheckedChange = { tls = it; result = null })
            }

            result?.let { (ok, text) ->
                Panel(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error, contentDescription = null, tint = if (ok) B.c.ok else B.c.err)
                        Spacer(Modifier.size(10.dp))
                        Text(text, color = if (ok) B.c.text else B.c.err)
                    }
                }
            }

            OutlinedButton(onClick = ::test, enabled = valid && !testing, modifier = Modifier.fillMaxWidth()) {
                if (testing) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.size(8.dp)) }
                Text(if (testing) "Testing..." else "Test connection")
            }
            Button(onClick = { save(true) }, enabled = valid, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = B.c.accent)) { Text("Save and connect") }
            OutlinedButton(onClick = { save(false) }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("Save without connecting") }
            Spacer(Modifier.size(24.dp))
        }
    }
}
