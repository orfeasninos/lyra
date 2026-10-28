package app.lyra.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lyra.player.data.Library
import app.lyra.player.data.PlayStat
import app.lyra.player.data.Song
import app.lyra.player.ui.AppContext
import app.lyra.player.ui.components.SongRow
import app.lyra.player.ui.components.formatTime
import app.lyra.player.ui.components.songCount

/** Λίστες που φτιάχνονται αυτόματα από τα αγαπημένα και το ιστορικό αναπαραγωγής. */
enum class SmartList(val title: String, val icon: ImageVector, val empty: String) {
    FAVORITES(
        "Favorites", Icons.Default.Favorite,
        "No favorites yet. Tap the heart on the player, or use “Add to favorites” in a song's menu.",
    ),
    RECENTLY_PLAYED(
        "Recently played", Icons.Default.History,
        "Nothing here yet. A song counts as played after 30 seconds (or half of it, if it's shorter).",
    ),
    MOST_PLAYED(
        "Most played", Icons.Default.TrendingUp,
        "Nothing here yet. A song counts as played after 30 seconds (or half of it, if it's shorter).",
    ),
    NOT_PLAYED("Not played", Icons.Default.NewReleases, "You've played every song in your library."),
    ;

    fun songs(lib: Library, favorites: List<Long>, stats: Map<Long, PlayStat>): List<Song> = when (this) {
        // Τα πιο πρόσφατα αγαπημένα πρώτα
        FAVORITES -> favorites.asReversed().mapNotNull { lib.songsById[it] }
        RECENTLY_PLAYED -> stats.entries.sortedByDescending { it.value.lastPlayed }
            .mapNotNull { lib.songsById[it.key] }.take(LIMIT)
        MOST_PLAYED -> stats.entries
            .sortedWith(compareByDescending<Map.Entry<Long, PlayStat>> { it.value.count }.thenByDescending { it.value.lastPlayed })
            .mapNotNull { lib.songsById[it.key] }.take(LIMIT)
        // Τα πιο πρόσφατα προστεθειμένα πρώτα: εκεί είναι συνήθως ό,τι δεν έχεις ακούσει ακόμα
        NOT_PLAYED -> lib.songs.filter { it.id !in stats }.sortedByDescending { it.dateAdded }
    }

    companion object {
        const val LIMIT = 100
    }
}

@Composable
fun SmartListScreen(ctx: AppContext, list: SmartList) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    val playback by ctx.vm.music.state.collectAsStateWithLifecycle()
    val favorites by ctx.vm.store.favorites.collectAsStateWithLifecycle()
    val stats by ctx.vm.store.playStats.collectAsStateWithLifecycle()
    val songs = remember(list, lib, favorites, stats) { list.songs(lib, favorites, stats) }

    Column(Modifier.fillMaxSize()) {
        BackBar(ctx, list.title)
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    list.empty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
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
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    s, lib, ctx.vm.artwork, ctx.songActions,
                    onClick = { ctx.vm.music.play(songs, i) },
                    isCurrent = playback.currentSongId == s.id,
                )
            }
        }
    }
}
