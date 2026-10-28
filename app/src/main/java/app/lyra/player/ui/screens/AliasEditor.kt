package app.lyra.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.unit.dp
import app.lyra.player.data.AppStore.Companion.norm
import app.lyra.player.data.Artist
import app.lyra.player.data.Library
import app.lyra.player.ui.AppContext
import app.lyra.player.ui.components.songCount
import java.text.Normalizer

/**
 * Επεξεργασία των ονομάτων ενός καλλιτέχνη:
 * - αλλαγή του ονόματος που εμφανίζεται
 * - σύνδεση άλλων ονομάτων (ψευδώνυμα, παλιά ονόματα, λάθος γραφές) στον ίδιο καλλιτέχνη
 * - αποσύνδεση
 * Προτείνει αυτόματα ονόματα που μοιάζουν (π.χ. "The Weeknd" / "Weeknd", "Beyoncé" / "Beyonce").
 */
@Composable
fun AliasEditor(ctx: AppContext, artist: Artist, lib: Library, onDismiss: () -> Unit) {
    val store = ctx.vm.store
    var display by remember(artist.name) { mutableStateOf(artist.name) }
    var query by remember { mutableStateOf("") }
    val isGroup = artist.key.startsWith("g:")

    val ownNames = remember(artist) { (artist.aliases + artist.name).map(::norm).toSet() }

    val suggestions = remember(artist, lib) {
        val me = simplify(artist.name)
        val mine = (artist.aliases + artist.name).map(::simplify).toSet()
        lib.artists
            .filter { it.key != artist.key }
            .filter { other ->
                val o = simplify(other.name)
                o.isNotEmpty() && (o in mine || (me.length >= 6 && levenshtein(me, o) <= 1))
            }
            .map { it.name }
            .take(6)
    }

    val matches = remember(query, lib, ownNames) {
        val q = norm(query)
        if (q.length < 2) emptyList()
        else lib.rawArtistNames
            .filter { norm(it) !in ownNames && norm(it).contains(q) }
            .distinctBy { norm(it) }
            .take(30)
    }

    fun link(name: String) {
        store.linkNames(artist.name, artist.aliases + artist.name + name)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Artist names") },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                item {
                    Text("Display name", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(display, { display = it }, Modifier.weight(1f), singleLine = true)
                        TextButton(
                            enabled = display.isNotBlank() && display.trim() != artist.name,
                            onClick = { store.renameGroup(artist.name, display) },
                        ) { Text("OK") }
                    }
                }

                item {
                    Text(
                        "Also known as",
                        Modifier.padding(top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (artist.aliases.isEmpty()) {
                        Text(
                            "None yet. Add names below, e.g. a former name, alias or alternative spelling.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(artist.aliases) { alias ->
                    val unlink: (@Composable () -> Unit)? = if (isGroup) {
                        {
                            IconButton(onClick = { store.unlinkName(alias) }) {
                                Icon(Icons.Default.Close, "Unlink")
                            }
                        }
                    } else null
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(alias) },
                        trailingIcon = unlink,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }

                if (suggestions.isNotEmpty()) {
                    item {
                        Text(
                            "Same artist?",
                            Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    items(suggestions) { name ->
                        ListItem(
                            headlineContent = { Text(name) },
                            leadingContent = { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(Icons.Default.Add, "Link") },
                            modifier = Modifier.clickable { link(name) },
                        )
                    }
                }

                item {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    OutlinedTextField(
                        query, { query = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Add name") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                    )
                    if (query.isNotBlank() && norm(query) !in ownNames) {
                        TextButton(onClick = { link(query.trim()); query = "" }) {
                            Icon(Icons.Default.Add, null)
                            Text("Add “${query.trim()}”")
                        }
                    }
                }
                items(matches) { name ->
                    val inLib = lib.artists.firstOrNull { a -> norm(a.name) == norm(name) || a.aliases.any { norm(it) == norm(name) } }
                    val sub: (@Composable () -> Unit)? = if (inLib != null) {
                        { Text(songCount(inLib.songs.size)) }
                    } else null
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = sub,
                        trailingContent = { Icon(Icons.Default.Add, "Link") },
                        modifier = Modifier.clickable { link(name); query = "" },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Απλοποίηση για σύγκριση: χωρίς τόνους, "the", σύμβολα και κενά. */
private fun simplify(s: String): String {
    val noAccents = Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    return noAccents
        .removePrefix("the ")
        .replace("&", "and")
        .replace(Regex("[^\\p{L}\\p{N}]"), "")
}

private fun levenshtein(a: String, b: String): Int {
    if (kotlin.math.abs(a.length - b.length) > 1) return 2
    val prev = IntArray(b.length + 1) { it }
    val cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        cur.copyInto(prev)
    }
    return prev[b.length]
}
