package app.lyra.player.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.lyra.player.LyraApp
import app.lyra.player.ui.components.AddToPlaylistDialog
import app.lyra.player.ui.theme.LyraTheme

/** Οι ρυθμίσεις ενός widget. Ανοίγει όταν μπαίνει στην αρχική οθόνη και όταν το ξαναρυθμίζεις. */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        // Αν φύγεις με το πίσω, το launcher δεν κρατάει το widget που πήγαινες να βάλεις
        setResult(Activity.RESULT_CANCELED, result)
        val ours = AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, LyraWidget::class.java))
        if (id !in ours) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            LyraTheme {
                WidgetConfigScreen(WidgetStyle.load(this, id)) { style ->
                    style.save(this, id)
                    WidgetUpdater.render(this, id)
                    setResult(Activity.RESULT_OK, result)
                    finish()
                }
            }
        }
    }
}

@Composable
private fun WidgetConfigScreen(initial: WidgetStyle, onDone: (WidgetStyle) -> Unit) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(initial) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Το πραγματικό widget πάνω στην ταπετσαρία
            AndroidView(
                factory = { FrameLayout(it) },
                update = { frame ->
                    frame.removeAllViews()
                    frame.addView(WidgetUpdater.views(context, style, preview = true).apply(context, frame))
                },
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(16.dp),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Heading("Widget theme")
                WidgetTheme.entries.forEach { theme ->
                    Row(
                        Modifier.fillMaxWidth().clickable { style = style.copy(theme = theme) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(style.theme == theme, { style = style.copy(theme = theme) })
                        Text(theme.label, color = MaterialTheme.colorScheme.onBackground)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Heading("Transparency")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${style.transparency}%",
                        Modifier.width(48.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Slider(
                        value = style.transparency.toFloat(),
                        onValueChange = { style = style.copy(transparency = it.toInt()) },
                        valueRange = 0f..100f,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Heading("Advanced")
                Option("Album art", style.showArt) { style = style.copy(showArt = it) }
                Option("Add to playlists", style.showPlaylist) { style = style.copy(showPlaylist = it) }
                Option("Add/Remove favorite", style.showFavorite) { style = style.copy(showFavorite = it) }
                Option("Fast-forward and rewind buttons", style.showSeek) { style = style.copy(showSeek = it) }
                Spacer(Modifier.height(88.dp)) // να μην κρύβεται κάτι κάτω από το κουμπί
            }
        }
        FloatingActionButton(
            onClick = { onDone(style) },
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(24.dp),
        ) {
            Icon(Icons.Default.Check, "Done")
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun Option(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onChange)
        Text(label, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Ο διάλογος "Add to playlist" πάνω από την αρχική οθόνη, για το κουμπί του widget. */
class WidgetAddToPlaylistActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val songId = WidgetUpdater.currentSongId(this)
        if (songId == null) {
            finish()
            return
        }
        val store = (application as LyraApp).store
        setContent {
            LyraTheme {
                AddToPlaylistDialog(store, listOf(songId)) { finish() }
            }
        }
    }
}
