package app.lyra.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lyra.player.data.AppStore
import app.lyra.player.data.ArtworkLoader
import app.lyra.player.data.Library
import app.lyra.player.data.Song

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Ενέργειες για ένα τραγούδι, κοινές σε όλες τις οθόνες. */
class SongActions(
    val playNext: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
    val newQueueFrom: (Song) -> Unit,
    val addToPlaylist: (Song) -> Unit,
    val goToAlbum: (Song) -> Unit,
    val goToArtist: (Song) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    library: Library,
    loader: ArtworkLoader,
    actions: SongActions,
    onClick: () -> Unit,
    isCurrent: Boolean = false,
    showArt: Boolean = true,
    leadingNumber: Int? = null,
    extraMenu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingNumber != null) {
            Text(
                if (leadingNumber > 0) "${leadingNumber % 1000}" else "–",
                Modifier.width(32.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (showArt) {
            Artwork(loader, song)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${library.artistLine(song)} · ${formatTime(song.durationMs)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Επιλογές") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val close = { menu = false }
                DropdownMenuItem(
                    text = { Text("Παίξε επόμενο") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) },
                    onClick = { close(); actions.playNext(song) },
                )
                DropdownMenuItem(
                    text = { Text("Πρόσθεσε στην ουρά") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    onClick = { close(); actions.addToQueue(song) },
                )
                DropdownMenuItem(
                    text = { Text("Νέα ουρά από εδώ") },
                    leadingIcon = { Icon(Icons.Default.Queue, null) },
                    onClick = { close(); actions.newQueueFrom(song) },
                )
                DropdownMenuItem(
                    text = { Text("Πρόσθεσε σε playlist") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                    onClick = { close(); actions.addToPlaylist(song) },
                )
                DropdownMenuItem(
                    text = { Text("Πήγαινε στο άλμπουμ") },
                    leadingIcon = { Icon(Icons.Default.Album, null) },
                    onClick = { close(); actions.goToAlbum(song) },
                )
                DropdownMenuItem(
                    text = { Text("Πήγαινε στον καλλιτέχνη") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    onClick = { close(); actions.goToArtist(song) },
                )
                extraMenu?.invoke(close)
            }
        }
    }
}

@Composable
fun RemoveMenuItem(label: String, close: () -> Unit, onRemove: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Remove, null) },
        onClick = { close(); onRemove() },
    )
}

@Composable
fun AddToPlaylistDialog(store: AppStore, songs: List<Song>, onDismiss: () -> Unit) {
    val playlists by store.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Νέα playlist" else "Πρόσθεσε σε playlist") },
        text = {
            if (creating) {
                OutlinedTextField(name, { name = it }, label = { Text("Όνομα") }, singleLine = true)
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text("Νέα playlist…") },
                            leadingContent = { Icon(Icons.Default.Add, null) },
                            modifier = Modifier.clickable { creating = true },
                        )
                    }
                    items(playlists, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text("${p.songIds.size} τραγούδια") },
                            modifier = Modifier.clickable {
                                store.addToPlaylist(p.id, songs.map { it.id }); onDismiss()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(
                enabled = name.isNotBlank(),
                onClick = { store.createPlaylist(name, songs.map { it.id }); onDismiss() },
            ) { Text("Δημιουργία") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text); onDismiss() }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } },
    )
}
