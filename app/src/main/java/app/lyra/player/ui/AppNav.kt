package app.lyra.player.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lyra.player.data.Song
import app.lyra.player.ui.components.AddToPlaylistDialog
import app.lyra.player.ui.components.SongActions
import app.lyra.player.ui.screens.AlbumScreen
import app.lyra.player.ui.screens.ArtistScreen
import app.lyra.player.ui.screens.FolderScreen
import app.lyra.player.ui.screens.HomeScreen
import app.lyra.player.ui.screens.MiniPlayer
import app.lyra.player.ui.screens.PlayerScreen
import app.lyra.player.ui.screens.PlaylistScreen
import app.lyra.player.ui.screens.SearchScreen
import app.lyra.player.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val PLAYER = "player"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    fun album(key: String) = "album/${Uri.encode(key)}"
    fun artist(key: String, name: String) = "artist/${Uri.encode(key)}?name=${Uri.encode(name)}"
    fun folder(path: String) = "folder/${Uri.encode(path)}"
    fun playlist(id: String) = "playlist/${Uri.encode(id)}"
}

/** Ό,τι χρειάζονται οι οθόνες: ViewModel, πλοήγηση και κοινές ενέργειες. */
class AppContext(
    val vm: LibraryViewModel,
    val nav: NavHostController,
    val songActions: SongActions,
    val addToPlaylist: (List<Song>) -> Unit,
)

@Composable
fun LyraRoot(vm: LibraryViewModel) {
    val nav = rememberNavController()
    val library by vm.library.collectAsStateWithLifecycle()
    val playback by vm.music.state.collectAsStateWithLifecycle()
    var playlistTarget by remember { mutableStateOf<List<Song>?>(null) }

    val actions = remember(nav) {
        SongActions(
            playNext = { vm.music.playNext(listOf(it)) },
            addToQueue = { vm.music.addToQueue(listOf(it)) },
            newQueueFrom = { song ->
                // Νέα ουρά με το τραγούδι και ό,τι ακολουθεί στο άλμπουμ του
                val lib = vm.library.value
                val album = lib.albumOf(song)
                val list = album?.songs?.dropWhile { it.id != song.id } ?: listOf(song)
                vm.music.newQueue(list)
            },
            addToPlaylist = { playlistTarget = listOf(it) },
            goToAlbum = { song -> vm.library.value.albumOf(song)?.let { nav.navigate(Routes.album(it.key)) } },
            goToArtist = { song ->
                vm.library.value.artistsOf(song).firstOrNull()?.let { nav.navigate(Routes.artist(it.key, it.name)) }
            },
        )
    }
    val ctx = remember(nav) { AppContext(vm, nav, actions) { playlistTarget = it } }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val current = playback.currentSongId?.let { library.songsById[it] }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = current != null && route != Routes.PLAYER,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                if (current != null) MiniPlayer(ctx, current, playback) { nav.navigate(Routes.PLAYER) { launchSingleTop = true } }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeScreen(ctx) }
                composable(Routes.PLAYER) { PlayerScreen(ctx) }
                composable(Routes.SEARCH) { SearchScreen(ctx) }
                composable(Routes.SETTINGS) { SettingsScreen(ctx) }
                composable("album/{key}", listOf(navArgument("key") { type = NavType.StringType })) {
                    AlbumScreen(ctx, it.arguments?.getString("key").orEmpty())
                }
                composable(
                    "artist/{key}?name={name}",
                    listOf(
                        navArgument("key") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    ArtistScreen(
                        ctx,
                        it.arguments?.getString("key").orEmpty(),
                        it.arguments?.getString("name").orEmpty(),
                    )
                }
                composable("folder/{path}", listOf(navArgument("path") { type = NavType.StringType })) {
                    FolderScreen(ctx, it.arguments?.getString("path").orEmpty())
                }
                composable("playlist/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                    PlaylistScreen(ctx, it.arguments?.getString("id").orEmpty())
                }
            }
        }
    }

    playlistTarget?.let { songs ->
        AddToPlaylistDialog(vm.store, songs) { playlistTarget = null }
    }
}
