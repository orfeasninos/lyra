package app.lyra.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lyra.player.data.ArtistsView
import app.lyra.player.data.HomeTab
import app.lyra.player.data.PlayQueue
import app.lyra.player.data.byName
import app.lyra.player.ui.AppContext
import app.lyra.player.ui.Routes
import app.lyra.player.ui.components.Artwork
import app.lyra.player.ui.components.SongRow
import app.lyra.player.ui.components.TextInputDialog
import app.lyra.player.ui.components.count
import app.lyra.player.ui.components.songCount
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(ctx: AppContext) {
    val tabSettings by ctx.vm.store.tabs.collectAsStateWithLifecycle()
    val tabs = tabSettings.filter { it.visible }.map { it.tab }
    // Ξεκινάμε από τα Albums, αν φαίνονται
    val pager = rememberPagerState(initialPage = tabs.indexOf(HomeTab.ALBUMS).coerceAtLeast(0)) { tabs.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = { Text("Lyra", fontWeight = FontWeight.SemiBold) },
            actions = {
                IconButton(onClick = { ctx.nav.navigate(Routes.SEARCH) }) { Icon(Icons.Default.Search, "Search") }
                IconButton(onClick = { ctx.nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, "Settings") }
            },
        )
        val selected = pager.currentPage.coerceIn(0, tabs.lastIndex)
        if (tabs.size > 1) {
            ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = selected == i,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        text = { Text(t.label) },
                    )
                }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1, key = { tabs[it].name }) { page ->
            when (tabs[page]) {
                HomeTab.SONGS -> SongsTab(ctx)
                HomeTab.FOLDERS -> FoldersTab(ctx)
                HomeTab.ALBUMS -> AlbumsTab(ctx)
                HomeTab.ARTISTS -> ArtistsTab(ctx)
                HomeTab.PLAYLISTS -> PlaylistsTab(ctx)
                HomeTab.QUEUES -> QueuesTab(ctx)
            }
        }
    }
}

@Composable
private fun SongsTab(ctx: AppContext) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    val playback by ctx.vm.music.state.collectAsStateWithLifecycle()
    val songs = remember(lib) { lib.songs.sortedWith(byName { it.title }) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilledTonalButton(onClick = { ctx.vm.music.shuffleAll(songs) }) {
                    Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle all (${songs.size})")
                }
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(
                s, lib, ctx.vm.artwork, ctx.songActions,
                onClick = { ctx.vm.music.play(songs, i) },
                isCurrent = playback.currentSongId == s.id,
            )
        }
    }
}

@Composable
private fun FoldersTab(ctx: AppContext) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize()) {
        items(lib.folders, key = { it.path }) { f ->
            ListItem(
                headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        "${songCount(f.songs.size)} · ${f.path.removePrefix("/storage/emulated/0")}",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { ctx.nav.navigate(Routes.folder(f.path)) },
            )
        }
    }
}

@Composable
private fun ArtistsTab(ctx: AppContext) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    val view by ctx.vm.store.artistsView.collectAsStateWithLifecycle()
    val list = when (view) {
        ArtistsView.ARTISTS -> lib.artists
        ArtistsView.ALBUM_ARTISTS -> lib.albumArtists
        ArtistsView.COMPOSERS -> lib.composers
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "view") {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArtistsView.entries.forEach { v ->
                    FilterChip(
                        selected = v == view,
                        onClick = { ctx.vm.store.setArtistsView(v) },
                        label = { Text(v.label) },
                    )
                }
            }
        }
        if (list.isEmpty() && view == ArtistsView.COMPOSERS) item(key = "empty") {
            Text(
                "None of your songs has a composer tag.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(list, key = { it.key }) { a ->
            ListItem(
                headlineContent = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    val counts = if (view == ArtistsView.ALBUM_ARTISTS) "${count(a.albumCount, "album")} · ${songCount(a.songs.size)}"
                    else songCount(a.songs.size)
                    val aka = if (a.aliases.isNotEmpty()) " · aka ${a.aliases.joinToString(", ")}" else ""
                    Text(
                        "$counts$aka",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            a.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                modifier = Modifier.clickable { ctx.nav.navigate(Routes.artist(a.key, a.name)) },
            )
        }
    }
}

@Composable
private fun PlaylistsTab(ctx: AppContext) {
    val playlists by ctx.vm.store.playlists.collectAsStateWithLifecycle()
    val favorites by ctx.vm.store.favorites.collectAsStateWithLifecycle()
    val stats by ctx.vm.store.playStats.collectAsStateWithLifecycle()
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    val smartCounts = remember(lib, favorites, stats) {
        SmartList.entries.associateWith { it.songs(lib, favorites, stats).size }
    }
    var creating by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text("New playlist") },
                leadingContent = { Icon(Icons.Default.Add, null) },
                modifier = Modifier.clickable { creating = true },
            )
        }
        items(SmartList.entries, key = { "smart:" + it.name }) { list ->
            ListItem(
                headlineContent = { Text(list.title) },
                supportingContent = { Text(songCount(smartCounts[list] ?: 0)) },
                leadingContent = { Icon(list.icon, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { ctx.nav.navigate(Routes.smartList(list)) },
            )
        }
        items(playlists, key = { it.id }) { p ->
            ListItem(
                headlineContent = { Text(p.name) },
                supportingContent = { Text(songCount(p.songIds.size)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { ctx.nav.navigate(Routes.playlist(p.id)) },
            )
        }
    }
    if (creating) {
        TextInputDialog("New playlist", "", "Create", { creating = false }) { ctx.vm.store.createPlaylist(it) }
    }
}

@Composable
private fun QueuesTab(ctx: AppContext) {
    val queues by ctx.vm.store.queues.collectAsStateWithLifecycle()
    val active by ctx.vm.store.activeQueueId.collectAsStateWithLifecycle()
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<PlayQueue?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "Each queue remembers where you left off. Tap to switch.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(queues, key = { it.id }) { q ->
            val isActive = q.id == active
            val current = q.songIds.getOrNull(q.index)?.let { lib.songsById[it] }
            var menu by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = {
                    Text(
                        q.name,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                supportingContent = {
                    Text(
                        songCount(q.songIds.size) + (current?.let { " · ${it.title}" } ?: ""),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    if (isActive) Icon(Icons.Default.Equalizer, null, tint = MaterialTheme.colorScheme.primary)
                    else Artwork(ctx.vm.artwork, current, size = 40.dp)
                },
                trailingContent = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = q })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; ctx.vm.music.deleteQueue(q.id) })
                        }
                    }
                },
                modifier = Modifier.clickable {
                    if (isActive) ctx.nav.navigate(Routes.PLAYER) else ctx.vm.music.switchQueue(q.id)
                },
            )
        }
        item {
            TextButton(onClick = { ctx.vm.music.newQueue(emptyList()) }, Modifier.padding(8.dp)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("New empty queue")
            }
        }
    }
    renaming?.let { q ->
        TextInputDialog("Rename queue", q.name, "Save", { renaming = null }) { ctx.vm.music.renameQueue(q.id, it) }
    }
}
