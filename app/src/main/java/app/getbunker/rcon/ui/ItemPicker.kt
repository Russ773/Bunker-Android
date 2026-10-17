@file:OptIn(ExperimentalMaterial3Api::class)

package app.getbunker.rcon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.getbunker.rcon.core.ItemInfo
import app.getbunker.rcon.rcon.Cmd
import coil3.compose.AsyncImage

@Composable
fun ItemIcon(item: ItemInfo, size: Int = 40) {
    val app = LocalApp.current
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(B.c.input), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = app.items.iconUrl(item.shortname),
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size((size - 6).dp),
        )
    }
}

/** Search the Rust item list (with pictures) and choose an item. */
@Composable
fun ItemSearch(selected: ItemInfo?, onPick: (ItemInfo) -> Unit, modifier: Modifier = Modifier, maxResults: Int = 30) {
    val app = LocalApp.current
    @Suppress("UNUSED_VARIABLE") val itemsState by app.items.items.collectAsState()
    var query by remember { mutableStateOf("") }
    val results = remember(query, itemsState) { app.items.search(query, maxResults) }

    Column(modifier) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search items (try \"wood\" or \"ak\")") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (selected != null) {
            Spacer(Modifier.size(8.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(B.c.selection).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ItemIcon(selected, 36)
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(selected.name, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(selected.shortname, style = MaterialTheme.typography.bodySmall, color = B.c.muted)
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(results, key = { it.shortname }) { item ->
                Row(Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemIcon(item)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(item.shortname + if (item.category.isNotEmpty()) "  ·  ${item.category}" else "", style = MaterialTheme.typography.bodySmall, color = B.c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = B.c.border)
            }
        }
    }
}

/** Item, amount and a Give button. [onGive] gets the item's shortname and the amount. */
@Composable
fun GiveItemDialog(title: String, onGive: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var item by remember { mutableStateOf<ItemInfo?>(null) }
    var amount by remember { mutableStateOf("100") }
    val n = amount.toIntOrNull()
    val valid = item != null && Cmd.safeItemRegex.matches(item!!.shortname) && n != null && n in 1..100000

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(B.c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close") }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(8.dp))
            ItemSearch(item, onPick = { item = it }, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 10, 100, 1000, 10000).forEach { v ->
                    AssistChip(onClick = { amount = v.toString() }, label = { Text("$v") })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    amount, { amount = it.filter(Char::isDigit).take(6) }, label = { Text("Amount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = { onGive(item!!.shortname, n!!); onDismiss() },
                    enabled = valid,
                    colors = ButtonDefaults.buttonColors(containerColor = B.c.accent),
                ) { Text("Give") }
            }
        }
    }
}
