package app.lyra.player.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lyra.player.data.Album
import app.lyra.player.data.AlbumExtraInfo
import app.lyra.player.data.AlbumPrefs
import app.lyra.player.data.AlbumSort
import app.lyra.player.data.AlbumViewMode
import app.lyra.player.data.GridSize
import app.lyra.player.data.searchKey
import app.lyra.player.ui.AppContext
import app.lyra.player.ui.Routes
import app.lyra.player.ui.components.Artwork
import app.lyra.player.ui.components.count
import app.lyra.player.ui.components.formatTime
import app.lyra.player.ui.components.songCount
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun AlbumsTab(ctx: AppContext) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    val prefs by ctx.vm.store.albumPrefs.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sorting by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val sorted = remember(lib, prefs.sort, prefs.descending) { lib.albums.sortedWith(prefs.comparator()) }
    val q = searchKey(query)
    val albums = remember(sorted, q) {
        if (q.isEmpty()) sorted
        else sorted.filter { al ->
            searchKey(al.title).contains(q) || searchKey(al.artistName).contains(q) || searchKey(al.artist).contains(q)
        }
    }
    val extra = prefs.effectiveExtraInfo()
    val open = { al: Album -> ctx.nav.navigate(Routes.album(al.key)) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (prefs.quickSearch) {
                TextField(
                    query, { query = it },
                    Modifier.weight(1f),
                    placeholder = { Text("Search an album…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            } else {
                Text(
                    count(albums.size, "album"),
                    Modifier.weight(1f).padding(start = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { sorting = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
            IconButton(onClick = { ctx.nav.navigate(Routes.ALBUM_SETTINGS) }) { Icon(Icons.Default.Settings, "Album settings") }
        }

        when (prefs.viewMode) {
            AlbumViewMode.GRID -> LazyVerticalGrid(
                GridCells.Adaptive(prefs.gridSize.minCellDp.dp),
                Modifier.fillMaxSize(),
                state = gridState,
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(albums, key = { it.key }) { al -> AlbumTile(ctx, al, extra, prefs.gridSize) { open(al) } }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(albums, key = { it.key }) { al ->
                    val info = extraText(al, extra)
                    ListItem(
                        headlineContent = { Text(al.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = info?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                        leadingContent = if (prefs.viewMode == AlbumViewMode.LIST_THUMB) {
                            { Artwork(ctx.vm.artwork, al.cover, size = 48.dp) }
                        } else null,
                        modifier = Modifier.clickable { open(al) },
                    )
                }
            }
        }
    }

    if (sorting) {
        val options = AlbumSort.entries.flatMap { s -> listOf(s to false, s to true) }
        RadioDialog(
            title = "Sort albums by…",
            options = options.map { (s, desc) -> "${s.label} - ${if (desc) "descending" else "ascending"}" },
            selected = options.indexOf(prefs.sort to prefs.descending),
            onDismiss = { sorting = false },
        ) { i ->
            val (s, desc) = options[i]
            ctx.vm.store.updateAlbumPrefs { it.copy(sort = s, descending = desc) }
            scope.launch { gridState.scrollToItem(0); listState.scrollToItem(0) }
        }
    }
}

/** Κελί πλέγματος: εξώφυλλο με τον τίτλο από κάτω, μέσα σε πλαίσιο. */
@Composable
private fun AlbumTile(ctx: AppContext, al: Album, extra: AlbumExtraInfo, size: GridSize, onClick: () -> Unit) {
    val small = size <= GridSize.SMALL
    val info = extraText(al, extra)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Artwork(ctx.vm.artwork, al.cover, loadSize = if (small) 200 else 360, corner = 0.dp, fill = true)
            Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                Text(
                    al.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (info != null) {
                    Text(
                        info, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun extraText(al: Album, info: AlbumExtraInfo): String? = when (info) {
    AlbumExtraInfo.AUTO, AlbumExtraInfo.NONE -> null
    AlbumExtraInfo.SONGS -> songCount(al.songs.size)
    AlbumExtraInfo.DURATION -> formatTime(al.durationMs)
    AlbumExtraInfo.DATE_UPDATED ->
        al.dateUpdated.takeIf { it > 0 }?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1000)) }
    AlbumExtraInfo.YEAR -> al.year.takeIf { it > 0 }?.toString() ?: "—"
    AlbumExtraInfo.ARTIST -> al.artist
    AlbumExtraInfo.ALBUM_ARTIST -> al.albumArtist ?: al.artistName
    AlbumExtraInfo.COMPOSER -> al.composer ?: "Unknown composer"
}

// ------------------------------- Ρυθμίσεις -------------------------------

@Composable
fun AlbumSettingsScreen(ctx: AppContext) {
    val prefs by ctx.vm.store.albumPrefs.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }
    val update = ctx.vm.store::updateAlbumPrefs

    Column(Modifier.fillMaxSize()) {
        BackBar(ctx, "Albums")
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text("Album view mode") },
                supportingContent = { Text(prefs.viewMode.label) },
                modifier = Modifier.clickable { dialog = "view" },
            )
            if (prefs.viewMode == AlbumViewMode.GRID) {
                ListItem(
                    headlineContent = { Text("Grid size") },
                    supportingContent = { Text(prefs.gridSize.label) },
                    modifier = Modifier.clickable { dialog = "grid" },
                )
            }
            ListItem(
                headlineContent = { Text("Show extra info in the list of albums") },
                supportingContent = { Text(prefs.extraInfo.label) },
                modifier = Modifier.clickable { dialog = "extra" },
            )
            ListItem(
                headlineContent = { Text("Quick search box") },
                trailingContent = { Switch(prefs.quickSearch, { v -> update { it.copy(quickSearch = v) } }) },
                modifier = Modifier.clickable { update { it.copy(quickSearch = !it.quickSearch) } },
            )
        }
    }

    when (dialog) {
        "view" -> EnumDialog("Album view mode", AlbumViewMode.entries, prefs.viewMode, { it.label }, { dialog = null }) { v ->
            update { it.copy(viewMode = v) }
        }
        "grid" -> EnumDialog(
            "Grid size", GridSize.entries, prefs.gridSize, { it.label }, { dialog = null },
            onReset = { update { it.copy(gridSize = AlbumPrefs().gridSize) } },
        ) { v -> update { it.copy(gridSize = v) } }
        "extra" -> EnumDialog(
            "Show extra info in the list of albums", AlbumExtraInfo.entries, prefs.extraInfo, { it.label }, { dialog = null },
        ) { v -> update { it.copy(extraInfo = v) } }
    }
}

@Composable
private fun <E> EnumDialog(
    title: String,
    values: List<E>,
    selected: E,
    label: (E) -> String,
    onDismiss: () -> Unit,
    onReset: (() -> Unit)? = null,
    onSelect: (E) -> Unit,
) = RadioDialog(title, values.map(label), values.indexOf(selected), onDismiss, onReset) { onSelect(values[it]) }

/** Διάλογος επιλογής: πατάς μια επιλογή και κλείνει αμέσως, όπως στο Musicolet. */
@Composable
private fun RadioDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onReset: (() -> Unit)? = null,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                itemsIndexed(options) { i, opt ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(i == selected, role = Role.RadioButton) { onSelect(i); onDismiss() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(i == selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(opt, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissButton = onReset?.let { reset -> { TextButton(onClick = { reset(); onDismiss() }) { Text("Reset") } } },
    )
}
