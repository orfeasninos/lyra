package app.lyra.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import app.lyra.player.data.AppStore.Companion.norm
import app.lyra.player.data.Song
import app.lyra.player.ui.AppContext
import app.lyra.player.ui.Routes
import app.lyra.player.ui.components.Artwork
import app.lyra.player.ui.components.FlipCover
import app.lyra.player.ui.components.RemoveMenuItem
import app.lyra.player.ui.components.SongRow
import app.lyra.player.ui.components.TextInputDialog
import app.lyra.player.ui.components.count
import app.lyra.player.ui.components.formatTime
import app.lyra.player.ui.components.songCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(ctx: AppContext, title: String, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        windowInsets = WindowInsets(0),
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = { ctx.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        actions = { actions() },
    )
}

@Composable
fun PlayButtons(ctx: AppContext, songs: List<Song>, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { ctx.vm.music.play(songs) }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Play")
        }
        FilledTonalButton(onClick = { ctx.vm.music.shuffleAll(songs) }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Shuffle")
        }
        IconButton(onClick = { ctx.addToPlaylist(songs) }, enabled = songs.isNotEmpty()) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist")
        }
    }
}

@Composable
private fun Missing(ctx: AppContext, what: String) {
    Column(Modifier.fillMaxSize()) {
        BackBar(ctx, what)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Not found") }
    }
}

// ------------------------------- Άλμπουμ -------------------------------

@Composable
fun AlbumScreen(ctx: AppContext, key: String) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    val playback by ctx.vm.music.state.collectAsStateWithLifecycle()
    val album = lib.album(key) ?: return Missing(ctx, "Album")
    val total = album.songs.sumOf { it.durationMs }

    Column(Modifier.fillMaxSize()) {
        BackBar(ctx, album.title)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) {
                    // Πάτα το εξώφυλλο για να το γυρίσεις, κράτα το πατημένο για zoom
                    FlipCover(ctx.vm.artwork, album.cover)
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(album.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        album.artistName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        listOfNotNull(
                            album.year.takeIf { it > 0 }?.toString(),
                            songCount(album.songs.size),
                            formatTime(total),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PlayButtons(ctx, album.songs)
            }
            itemsIndexed(album.songs, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    s, lib, ctx.vm.artwork, ctx.songActions,
                    onClick = { ctx.vm.music.play(album.songs, i) },
                    isCurrent = playback.currentSongId == s.id,
                    leadingNumber = s.track,
                )
            }
        }
    }
}

// ------------------------------- Καλλιτέχνης -------------------------------

@Composable
fun ArtistScreen(ctx: AppContext, key: String, initialName: String) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    val playback by ctx.vm.music.state.collectAsStateWithLifecycle()
    // Όταν αλλάζουν τα ψευδώνυμα αλλάζει και το key, οπότε κρατάμε και το όνομα ως "άγκυρα"
    var anchor by remember { mutableStateOf(initialName) }
    var editing by remember { mutableStateOf(false) }

    val artist = lib.artist(key) ?: lib.artists.firstOrNull { a ->
        norm(a.name) == norm(anchor) || a.aliases.any { norm(it) == norm(anchor) }
    }
    if (artist == null) return Missing(ctx, anchor.ifEmpty { "Artist" })
    SideEffect { if (artist.name != anchor) anchor = artist.name }

    val albums = remember(artist) {
        lib.albums.filter { al -> al.songs.any { s -> artist.songs.any { it.id == s.id } } }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(ctx, artist.name) {
            IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Artist names") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(artist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    if (artist.aliases.isNotEmpty()) {
                        Text(
                            "Also known as: ${artist.aliases.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        "${songCount(artist.songs.size)} · ${count(albums.size, "album")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PlayButtons(ctx, artist.songs)
                if (albums.isNotEmpty()) {
                    Text(
                        "Albums", Modifier.padding(start = 16.dp, top = 8.dp),
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(albums.size) { idx ->
                            val al = albums[idx]
                            Column(
                                Modifier
                                    .width(120.dp)
                                    .clickable { ctx.nav.navigate(Routes.album(al.key)) },
                            ) {
                                Artwork(ctx.vm.artwork, al.cover, size = 120.dp, loadSize = 300, corner = 8.dp)
                                Text(al.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Text(
                    "Songs", Modifier.padding(start = 16.dp, top = 8.dp),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(artist.songs, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    s, lib, ctx.vm.artwork, ctx.songActions,
                    onClick = { ctx.vm.music.play(artist.songs, i) },
                    isCurrent = playback.currentSongId == s.id,
                )
            }
        }
    }

    if (editing) {
        AliasEditor(ctx, artist, lib) { editing = false }
    }
}

// ------------------------------- Φάκελος -------------------------------

@Composable
fun FolderScreen(ctx: AppContext, path: String) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    val playback by ctx.vm.music.state.collectAsStateWithLifecycle()
    val folder = lib.folder(path) ?: return Missing(ctx, "Folder")

    Column(Modifier.fillMaxSize()) {
        BackBar(ctx, folder.name)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    folder.path, Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlayButtons(ctx, folder.songs)
            }
            itemsIndexed(folder.songs, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    s, lib, ctx.vm.artwork, ctx.songActions,
                    onClick = { ctx.vm.music.play(folder.songs, i) },
                    isCurrent = playback.currentSongId == s.id,
                )
            }
        }
    }
}

// ------------------------------- Playlist -------------------------------

@Composable
fun PlaylistScreen(ctx: AppContext, id: String) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    val playback by ctx.vm.music.state.collectAsStateWithLifecycle()
    val playlists by ctx.vm.store.playlists.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == id } ?: return Missing(ctx, "Playlist")
    // Κρατάμε τη θέση στη λίστα, γιατί το ίδιο τραγούδι μπορεί να υπάρχει δύο φορές
    val entries = playlist.songIds.mapIndexedNotNull { pos, sid -> lib.songsById[sid]?.let { pos to it } }
    val songs = entries.map { it.second }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackBar(ctx, playlist.name) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Delete playlist") }, onClick = {
                        menu = false
                        ctx.vm.store.deletePlaylist(playlist.id)
                        ctx.nav.popBackStack()
                    })
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "${songCount(songs.size)} · ${formatTime(songs.sumOf { it.durationMs })}",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlayButtons(ctx, songs)
            }
            itemsIndexed(entries, key = { _, e -> e.first }) { i, (pos, s) ->
                SongRow(
                    s, lib, ctx.vm.artwork, ctx.songActions,
                    onClick = { ctx.vm.music.play(songs, i) },
                    isCurrent = playback.currentSongId == s.id,
                    extraMenu = { close ->
                        if (pos > 0) DropdownMenuItem(
                            text = { Text("Move up") },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                            onClick = { close(); ctx.vm.store.moveInPlaylist(playlist.id, pos, pos - 1) },
                        )
                        if (pos < playlist.songIds.lastIndex) DropdownMenuItem(
                            text = { Text("Move down") },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                            onClick = { close(); ctx.vm.store.moveInPlaylist(playlist.id, pos, pos + 1) },
                        )
                        RemoveMenuItem("Remove from playlist", close) { ctx.vm.store.removeFromPlaylist(playlist.id, pos) }
                    },
                )
            }
        }
    }
    if (renaming) {
        TextInputDialog("Rename", playlist.name, "Save", { renaming = false }) {
            ctx.vm.store.renamePlaylist(playlist.id, it)
        }
    }
}
