package app.lyra.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lyra.player.data.AppStore
import app.lyra.player.ui.AppContext
import app.lyra.player.ui.Routes
import app.lyra.player.ui.components.SongRow
import app.lyra.player.ui.components.count
import app.lyra.player.ui.components.songCount

@Composable
fun SearchScreen(ctx: AppContext) {
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    val playback by ctx.vm.music.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val result = remember(query, lib) { lib.search(query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        TextField(
            query, { query = it },
            Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = { Text("Songs, albums, artists (and aliases)") },
            leadingIcon = {
                IconButton(onClick = { ctx.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (result.artists.isNotEmpty()) item { Header("Artists") }
            items(result.artists, key = { "a" + it.key }) { a ->
                val aka: (@Composable () -> Unit)? = if (a.aliases.isNotEmpty()) {
                    { Text("aka ${a.aliases.joinToString(", ")}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else null
                ListItem(
                    headlineContent = { Text(a.name) },
                    supportingContent = aka,
                    leadingContent = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.clickable { ctx.nav.navigate(Routes.artist(a.key, a.name)) },
                )
            }
            if (result.albums.isNotEmpty()) item { Header("Albums") }
            items(result.albums, key = { "l" + it.key }) { al ->
                ListItem(
                    headlineContent = { Text(al.title) },
                    supportingContent = { Text(al.artistName) },
                    leadingContent = { Icon(Icons.Default.Album, null) },
                    modifier = Modifier.clickable { ctx.nav.navigate(Routes.album(al.key)) },
                )
            }
            if (result.songs.isNotEmpty()) item { Header("Songs") }
            itemsIndexed(result.songs, key = { _, s -> "s${s.id}" }) { i, s ->
                SongRow(
                    s, lib, ctx.vm.artwork, ctx.songActions,
                    onClick = { ctx.vm.music.play(result.songs, i) },
                    isCurrent = playback.currentSongId == s.id,
                )
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun SettingsScreen(ctx: AppContext) {
    val seps by ctx.vm.store.separators.collectAsStateWithLifecycle()
    val scanning by ctx.vm.scanning.collectAsStateWithLifecycle()
    val lib by ctx.vm.library.collectAsStateWithLifecycle()
    // Τα κενά μετρούν (π.χ. " x "), οπότε τα δείχνουμε μέσα σε εισαγωγικά, ένα ανά γραμμή
    var text by remember(seps) { mutableStateOf(seps.joinToString("\n") { "\"$it\"" }) }

    Column(Modifier.fillMaxSize()) {
        BackBar(ctx, "Settings")
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Library", style = MaterialTheme.typography.titleMedium)
            Text(
                "${songCount(lib.songs.size)} · ${count(lib.albums.size, "album")} · ${count(lib.artists.size, "artist")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { ctx.vm.scan() }, enabled = !scanning) {
                Text(if (scanning) "Scanning…" else "Rescan")
            }

            Spacer(Modifier.height(28.dp))
            Text("Artist separators", style = MaterialTheme.typography.titleMedium)
            Text(
                "When a tag says e.g. “Artist A feat. Artist B”, the song shows up under both artists. " +
                    "One separator per line, in quotes. A name like “Simon & Garfunkel” that you have added " +
                    "to an artist is never split.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 5)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val list = text.lines().map { line ->
                    val t = line.trim()
                    if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) t.substring(1, t.length - 1) else t
                }.filter { it.isNotEmpty() }
                ctx.vm.store.setSeparators(list)
            }) { Text("Save") }
            OutlinedButton(onClick = { ctx.vm.store.setSeparators(AppStore.DEFAULT_SEPARATORS) }) {
                Text("Restore defaults")
            }
        }
    }
}
