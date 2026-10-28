package app.lyra.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import app.lyra.player.data.Song
import app.lyra.player.playback.PlaybackState
import app.lyra.player.ui.AppContext
import app.lyra.player.ui.Routes
import app.lyra.player.ui.components.Artwork
import app.lyra.player.ui.components.FlipCover
import app.lyra.player.ui.components.RemoveMenuItem
import app.lyra.player.ui.components.SongRow
import app.lyra.player.ui.components.formatTime

@Composable
fun MiniPlayer(ctx: AppContext, song: Song, state: PlaybackState, onOpen: () -> Unit) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    Surface(tonalElevation = 3.dp) {
        // Ο mini player κάθεται πάνω από τη γραμμή πλοήγησης του συστήματος
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(ctx.vm.artwork, song, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        lib.artistLine(song), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { ctx.vm.music.togglePlay() }) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Αναπαραγωγή/Παύση")
                }
                IconButton(onClick = { ctx.vm.music.next() }) { Icon(Icons.Default.SkipNext, "Επόμενο") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(ctx: AppContext) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    val state by ctx.vm.music.state.collectAsStateWithLifecycle()
    val song = state.currentSongId?.let { lib.songsById[it] }
    var showQueue by remember { mutableStateOf(false) }

    // Κατά το σύρσιμο του slider δεν θέλουμε να "πηδάει" από τις ενημερώσεις
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ctx.nav.popBackStack() }) { Icon(Icons.Default.KeyboardArrowDown, "Κλείσιμο") }
            Text(
                "Παίζει τώρα",
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
            )
            IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Ουρά") }
        }

        Spacer(Modifier.height(16.dp))
        FlipCover(ctx.vm.artwork, song, Modifier.weight(1f, fill = false))
        Spacer(Modifier.height(20.dp))

        if (song != null) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            // Κάθε καλλιτέχνης είναι ξεχωριστό link (π.χ. σε "A feat. B")
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                val refs = lib.artistsOf(song)
                refs.forEachIndexed { i, ref ->
                    Text(
                        ref.name + if (i < refs.lastIndex) ", " else "",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        modifier = Modifier.clickable { ctx.nav.navigate(Routes.artist(ref.key, ref.name)) },
                    )
                }
            }
            Text(
                song.album,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { lib.albumOf(song)?.let { ctx.nav.navigate(Routes.album(it.key)) } },
            )
        }

        Spacer(Modifier.height(12.dp))
        val duration = state.durationMs.coerceAtLeast(1)
        Slider(
            value = if (dragging) dragValue else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                ctx.vm.music.seekTo((dragValue * duration).toLong())
                dragging = false
            },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatTime(if (dragging) (dragValue * duration).toLong() else state.positionMs),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.weight(1f))
            Text(formatTime(state.durationMs), style = MaterialTheme.typography.bodySmall)
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { ctx.vm.music.toggleShuffle() }) {
                Icon(
                    Icons.Default.Shuffle, "Τυχαία σειρά",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { ctx.vm.music.previous() }, Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipPrevious, "Προηγούμενο", Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick = { ctx.vm.music.togglePlay() },
                Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Αναπαραγωγή/Παύση", Modifier.size(40.dp))
            }
            IconButton(onClick = { ctx.vm.music.next() }, Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipNext, "Επόμενο", Modifier.size(36.dp))
            }
            IconButton(onClick = { ctx.vm.music.cycleRepeat() }) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Επανάληψη",
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            QueueList(ctx, state)
        }
    }
}

@Composable
private fun QueueList(ctx: AppContext, state: PlaybackState) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    val queues by ctx.vm.store.queues.collectAsStateWithLifecycle()
    val activeId by ctx.vm.store.activeQueueId.collectAsStateWithLifecycle()
    val name = queues.firstOrNull { it.id == activeId }?.name ?: "Ουρά"
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (state.index > 2) listState.scrollToItem(state.index - 2) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "$name · ${state.queue.size} τραγούδια",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Box(Modifier.fillMaxWidth().height(520.dp)) {
            LazyColumn(state = listState) {
                itemsIndexed(state.queue, key = { i, id -> "$i-$id" }) { i, id ->
                    val s = lib.songsById[id] ?: return@itemsIndexed
                    SongRow(
                        s, lib, ctx.vm.artwork, ctx.songActions,
                        onClick = { ctx.vm.music.skipTo(i) },
                        isCurrent = i == state.index,
                        extraMenu = { close ->
                            if (i > 0) DropdownMenuItem(
                                text = { Text("Μετακίνηση πάνω") },
                                leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                                onClick = { close(); ctx.vm.music.moveInQueue(i, i - 1) },
                            )
                            if (i < state.queue.lastIndex) DropdownMenuItem(
                                text = { Text("Μετακίνηση κάτω") },
                                leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                                onClick = { close(); ctx.vm.music.moveInQueue(i, i + 1) },
                            )
                            RemoveMenuItem("Αφαίρεση από την ουρά", close) { ctx.vm.music.removeFromQueue(i) }
                        },
                    )
                }
            }
        }
    }
}
