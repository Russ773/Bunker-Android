package app.getbunker.rcon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun MoreTab(nav: NavController) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader("Server")
        Entry(Icons.Filled.Speed, "Quick actions", "Save the world, set the time, restart with a countdown") { nav.navigate(Routes.QUICK) }
        Entry(Icons.Filled.Campaign, "Announce", "Send a message to everyone, with saved messages") { nav.navigate(Routes.ANNOUNCE) }
        Entry(Icons.AutoMirrored.Filled.ShowChart, "Server health", "Charts of players, FPS, entities and memory") { nav.navigate(Routes.HEALTH) }
        Entry(Icons.Filled.Schedule, "Scheduler", "Run commands on a timer or at a time of day") { nav.navigate(Routes.SCHEDULES) }
        Entry(Icons.Filled.Extension, "Plugins", "Carbon and Oxide plugins: sort, reload, load and unload") { nav.navigate(Routes.PLUGINS) }
        Entry(Icons.Filled.TouchApp, "Shortcuts", "Your own buttons for any console command") { nav.navigate(Routes.SHORTCUTS) }

        SectionHeader("Players")
        Entry(Icons.Filled.Storage, "Player database", "Everyone who has joined, with your notes and a join/leave feed") { nav.navigate(Routes.PLAYER_DB) }
        Entry(Icons.Filled.Block, "Ban list", "See who is banned and unban them") { nav.navigate(Routes.BANS) }
        Entry(Icons.Filled.CardGiftcard, "Give items", "Search every Rust item with pictures and give it to a player or everyone") { nav.navigate(Routes.GIVE) }

        SectionHeader("Console")
        Entry(Icons.Filled.VisibilityOff, "Hide console messages", "Hide noisy plugins or words from the console") { nav.navigate(Routes.HIDDEN) }

        SectionHeader("App")
        Entry(Icons.Filled.Settings, "Settings", "Theme, notifications, chat name, security") { nav.navigate(Routes.SETTINGS) }
        Entry(Icons.Filled.Info, "About Bunker", "Version, links and privacy") { nav.navigate(Routes.ABOUT) }
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
fun Entry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = B.c.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = B.c.muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = B.c.dim)
    }
    HorizontalDivider(color = B.c.border, modifier = Modifier.padding(start = 56.dp))
}
