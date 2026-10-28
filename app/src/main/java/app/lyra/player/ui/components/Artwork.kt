package app.lyra.player.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lyra.player.data.ArtworkLoader
import app.lyra.player.data.ArtworkLoader.Side
import app.lyra.player.data.Song

@Composable
fun rememberArtwork(loader: ArtworkLoader, song: Song?, side: Side, size: Int): Bitmap? {
    val initial = song?.let { loader.cached(it, side, size) }
    val state by produceState(initial, song?.albumId, song?.folder, side, size) {
        value = song?.let { loader.load(it, side, size) }
    }
    return state
}

/** Μικρό τετράγωνο εξώφυλλο για λίστες. */
@Composable
fun Artwork(
    loader: ArtworkLoader,
    song: Song?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    loadSize: Int = 160,
    corner: Dp = 6.dp,
    /** Αν true, γεμίζει το πλάτος σε τετράγωνο (για πλέγμα άλμπουμ). */
    fill: Boolean = false,
) {
    val bmp = rememberArtwork(loader, song, Side.FRONT, loadSize)
    Box(
        modifier
            .then(if (fill) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(size))
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Μεγάλο εξώφυλλο που "γυρίζει" για να δείξει το οπισθόφυλλο.
 * Πάτημα: γύρισμα. Παρατεταμένο πάτημα: προβολή σε πλήρη οθόνη με zoom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlipCover(
    loader: ArtworkLoader,
    song: Song?,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
) {
    val front = rememberArtwork(loader, song, Side.FRONT, 900)
    val back = rememberArtwork(loader, song, Side.BACK, 900)
    var showBack by rememberSaveable(song?.albumId, song?.folder) { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Side?>(null) }
    val rotation by animateFloatAsState(if (showBack && back != null) 180f else 0f, tween(450), label = "flip")
    val backVisible = rotation > 90f

    Box(modifier) {
        Box(
            Modifier
                .aspectRatio(1f)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(
                    onClick = { if (back != null) showBack = !showBack },
                    onLongClick = { viewer = if (backVisible) Side.BACK else Side.FRONT },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = if (backVisible) back else front
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), null,
                    Modifier
                        .fillMaxSize()
                        // η πίσω όψη θα φαινόταν καθρεφτισμένη χωρίς αυτό
                        .graphicsLayer { if (backVisible) rotationY = 180f },
                    // Τα οπισθόφυλλα CD (tray inlay) δεν είναι τετράγωνα: τα δείχνουμε ολόκληρα
                    contentScale = if (backVisible) ContentScale.Fit else ContentScale.Crop,
                )
            } else {
                Icon(Icons.Default.MusicNote, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (back != null) {
            AssistChip(
                onClick = { showBack = !showBack },
                label = { Text(if (backVisible) "Front cover" else "Back cover") },
                leadingIcon = {
                    Icon(if (backVisible) Icons.Default.FlipToFront else Icons.Default.FlipToBack, null, Modifier.size(18.dp))
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = 0.55f), labelColor = Color.White, leadingIconContentColor = Color.White),
                border = null,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            )
        }
    }

    viewer?.let { side ->
        CoverViewer(loader, song, side) { viewer = null }
    }
}

/** Πλήρης οθόνη με pinch-to-zoom: χρήσιμο για να διαβάσεις tracklist και credits στο οπισθόφυλλο. */
@Composable
fun CoverViewer(loader: ArtworkLoader, song: Song?, side: Side, onDismiss: () -> Unit) {
    val bmp = rememberArtwork(loader, song, side, 2000)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero })
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), null,
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offset.x; translationY = offset.y
                        }
                        .transformable(state),
                    contentScale = ContentScale.Fit,
                )
            }
            IconButton(onClick = onDismiss, Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }
    }
}
