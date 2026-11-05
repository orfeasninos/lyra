package app.lyra.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import app.lyra.player.LyraApp
import app.lyra.player.R
import app.lyra.player.data.AppStore
import app.lyra.player.data.ArtworkLoader
import app.lyra.player.data.Song
import app.lyra.player.playback.MusicController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Κρατάει τα widgets ενημερωμένα με το τρέχον τραγούδι.
 *
 * Τίτλος, καλλιτέχνης και εξώφυλλο γράφονται στον δίσκο, γιατί το launcher ξαναζητάει το widget
 * (π.χ. μετά από επανεκκίνηση) και όταν δεν υπάρχει βιβλιοθήκη στη μνήμη.
 */
class WidgetUpdater(
    private val context: Context,
    private val music: MusicController,
    private val store: AppStore,
    private val artwork: ArtworkLoader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        val playing = music.state.map { it.currentSongId to it.isPlaying }.distinctUntilChanged()
        scope.launch {
            combine(playing, music.library, store.favorites) { (id, _), library, _ ->
                id to id?.let { library.songsById[it] }?.let { it to library.artistLine(it) }
            }.collectLatest { (id, current) ->
                when {
                    current != null -> saveSnapshot(current.first, current.second)
                    // Κανένα τραγούδι και καμία ουρά: το widget γυρίζει στην αρχική του μορφή.
                    // Αν υπάρχει ουρά, απλώς δεν έχει φορτώσει ακόμα και κρατάμε ό,τι δείχνει.
                    id == null && !hasQueuedSongs() -> clearSnapshot(context)
                }
                render(context)
            }
        }
    }

    private fun hasQueuedSongs(): Boolean {
        val active = store.activeQueueId.value
        return store.queues.value.any { it.id == active && it.songIds.isNotEmpty() }
    }

    private suspend fun saveSnapshot(song: Song, artist: String) {
        val prefs = prefs(context)
        val edit = prefs.edit()
        // Τα prefs γράφονται τελευταία: αν ακυρωθεί στη μέση, το εξώφυλλο ξαναφορτώνεται
        if (prefs.getLong(KEY_SONG, -1) != song.id) {
            val cover = artwork.load(song, ArtworkLoader.Side.FRONT, ART_SIZE)?.let(::roundedSquare)
            val file = artFile(context)
            if (cover != null) {
                file.outputStream().use { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }
                edit.putInt(KEY_ART_COLOR, Palette.from(cover).generate().getDominantColor(ARTLESS_COLOR))
            } else {
                file.delete()
                edit.remove(KEY_ART_COLOR)
            }
        }
        edit.putLong(KEY_SONG, song.id)
            .putString(KEY_TITLE, song.title)
            .putString(KEY_ARTIST, artist)
            .apply()
    }

    /** Τετράγωνο με στρογγυλεμένες γωνίες. Το αντίγραφο χρειάζεται γιατί το cache κρατάει hardware bitmaps. */
    private fun roundedSquare(source: Bitmap): Bitmap {
        val src = source.copy(Bitmap.Config.ARGB_8888, false)
        val scale = ART_SIZE.toFloat() / minOf(src.width, src.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((ART_SIZE - src.width * scale) / 2, (ART_SIZE - src.height * scale) / 2)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        }
        return Bitmap.createBitmap(ART_SIZE, ART_SIZE, Bitmap.Config.ARGB_8888).also {
            val size = ART_SIZE.toFloat()
            Canvas(it).drawRoundRect(RectF(0f, 0f, size, size), ART_RADIUS, ART_RADIUS, paint)
        }
    }

    private class Colors(val background: Int, val title: Int, val subtitle: Int, val icon: Int)

    companion object {
        private const val PREFS = "widget"
        private const val KEY_SONG = "song_id"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_ART_COLOR = "art_color"
        private const val ART_SIZE = 192
        private const val ART_RADIUS = 28f
        private val ARTLESS_COLOR = Color.parseColor("#121214")

        private val DARK = Colors(ARTLESS_COLOR, Color.parseColor("#F3E6C4"), Color.parseColor("#B9B2A0"), Color.parseColor("#E8C77A"))
        private val LIGHT = Colors(Color.parseColor("#FBF8F1"), Color.parseColor("#1C1B1F"), Color.parseColor("#5F5B66"), Color.parseColor("#7A5C12"))

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private fun artFile(context: Context) = File(context.cacheDir, "widget_art.png")

        private fun clearSnapshot(context: Context) {
            prefs(context).edit().clear().apply()
            artFile(context).delete()
        }

        /** Το τραγούδι που δείχνουν τα widgets, ή null αν δεν δείχνουν κανένα. */
        fun currentSongId(context: Context): Long? =
            prefs(context).getLong(KEY_SONG, -1).takeIf { it != -1L }

        /** Σχεδιάζει όλα τα widgets από ό,τι έχει γραφτεί στον δίσκο. */
        fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            for (id in manager.getAppWidgetIds(ComponentName(context, LyraWidget::class.java))) {
                render(context, id)
            }
        }

        fun render(context: Context, id: Int) {
            AppWidgetManager.getInstance(context).updateAppWidget(id, views(context, WidgetStyle.load(context, id)))
        }

        private fun artColors(color: Int): Colors {
            val text = if (ColorUtils.calculateLuminance(color) < 0.5) Color.WHITE else LIGHT.title
            return Colors(color, text, ColorUtils.setAlphaComponent(text, 0xB3), text)
        }

        /**
         * @param preview για την οθόνη ρυθμίσεων: δείχνει πάντα τα κουμπιά, που δεν πατιούνται.
         */
        fun views(context: Context, style: WidgetStyle, preview: Boolean = false): RemoteViews {
            val prefs = prefs(context)
            val app = context.applicationContext as LyraApp
            val title = prefs.getString(KEY_TITLE, null) ?: if (preview) "Title" else null
            val artist = prefs.getString(KEY_ARTIST, null) ?: if (preview) "Artist" else null
            val extended = style.extended && title != null
            val views = RemoteViews(
                context.packageName,
                if (extended) R.layout.widget_now_playing_extended else R.layout.widget_now_playing,
            )
            val artColor = if (prefs.contains(KEY_ART_COLOR)) prefs.getInt(KEY_ART_COLOR, 0) else null

            applyColor(context, views, style, artColor, R.id.widget_background, "setColorFilter") { it.background }
            views.setInt(R.id.widget_background, "setImageAlpha", 255 * (100 - style.transparency) / 100)
            applyColor(context, views, style, artColor, R.id.widget_title, "setTextColor") { it.title }
            if (!extended) applyColor(context, views, style, artColor, R.id.widget_artist, "setTextColor") { it.subtitle }
            views.setViewVisibility(R.id.widget_art, if (style.showArt) View.VISIBLE else View.GONE)

            if (!preview) {
                val openApp = PendingIntent.getActivity(
                    context, 0,
                    context.packageManager.getLaunchIntentForPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                views.setOnClickPendingIntent(android.R.id.background, openApp)
            }

            // Χωρίς τραγούδι δεν έχουν τι να κάνουν τα κουμπιά: το widget απλώς ανοίγει την εφαρμογή
            val controls = if (title == null) View.GONE else View.VISIBLE
            views.setViewVisibility(R.id.widget_previous, controls)
            views.setViewVisibility(R.id.widget_toggle, controls)
            views.setViewVisibility(R.id.widget_next, controls)
            if (title == null) return views

            if (extended) {
                views.setTextViewText(R.id.widget_title, if (artist.isNullOrEmpty()) title else "$title · $artist")
            } else {
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_artist, artist.orEmpty())
            }
            val art = BitmapFactory.decodeFile(artFile(context).path)
            if (art != null) views.setImageViewBitmap(R.id.widget_art, art)
            else views.setImageViewResource(R.id.widget_art, R.drawable.ic_launcher_foreground)

            val isPlaying = app.music.state.value.isPlaying
            views.setImageViewResource(R.id.widget_toggle, if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            views.setContentDescription(R.id.widget_toggle, if (isPlaying) "Pause" else "Play")

            fun button(id: Int, action: String) {
                applyColor(context, views, style, artColor, id, "setColorFilter") { it.icon }
                if (!preview) views.setOnClickPendingIntent(id, LyraWidget.actionIntent(context, action))
            }
            button(R.id.widget_toggle, LyraWidget.ACTION_TOGGLE)
            button(R.id.widget_next, LyraWidget.ACTION_NEXT)
            button(R.id.widget_previous, LyraWidget.ACTION_PREVIOUS)
            if (!extended) return views

            fun optional(id: Int, shown: Boolean) = views.setViewVisibility(id, if (shown) View.VISIBLE else View.GONE)
            optional(R.id.widget_rewind, style.showSeek)
            optional(R.id.widget_forward, style.showSeek)
            optional(R.id.widget_favorite, style.showFavorite)
            optional(R.id.widget_playlist, style.showPlaylist)
            button(R.id.widget_rewind, LyraWidget.ACTION_REWIND)
            button(R.id.widget_forward, LyraWidget.ACTION_FORWARD)
            button(R.id.widget_favorite, LyraWidget.ACTION_FAVORITE)

            val favorite = currentSongId(context)?.let { it in app.store.favorites.value } == true
            views.setImageViewResource(R.id.widget_favorite, if (favorite) R.drawable.ic_widget_favorite else R.drawable.ic_widget_favorite_border)
            views.setContentDescription(R.id.widget_favorite, if (favorite) "Remove from favorites" else "Add to favorites")

            applyColor(context, views, style, artColor, R.id.widget_playlist, "setColorFilter") { it.icon }
            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.widget_playlist,
                    PendingIntent.getActivity(
                        context, 0,
                        Intent(context, WidgetAddToPlaylistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            return views
        }

        private fun applyColor(
            context: Context,
            views: RemoteViews,
            style: WidgetStyle,
            artColor: Int?,
            id: Int,
            method: String,
            pick: (Colors) -> Int,
        ) {
            val colors = when (style.theme) {
                WidgetTheme.LIGHT -> LIGHT
                WidgetTheme.DARK -> DARK
                WidgetTheme.ALBUM_ART -> artColor?.let(::artColors) ?: DARK
                WidgetTheme.SYSTEM -> {
                    // Από το Android 12 το launcher αλλάζει μόνο του χρώματα με το σκοτεινό θέμα
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        views.setColorInt(id, method, pick(LIGHT), pick(DARK))
                        return
                    }
                    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (night == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
                }
            }
            views.setInt(id, method, pick(colors))
        }
    }
}
