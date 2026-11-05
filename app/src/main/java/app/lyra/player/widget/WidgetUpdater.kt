package app.lyra.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.widget.RemoteViews
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
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Κρατάει το widget ενημερωμένο με το τρέχον τραγούδι.
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
            combine(playing, music.library) { (id, isPlaying), library ->
                Triple(id, id?.let { library.songsById[it] }?.let { it to library.artistLine(it) }, isPlaying)
            }.collectLatest { (id, current, isPlaying) ->
                when {
                    current != null -> saveSnapshot(current.first, current.second)
                    // Κανένα τραγούδι και καμία ουρά: το widget γυρίζει στην αρχική του μορφή.
                    // Αν υπάρχει ουρά, απλώς δεν έχει φορτώσει ακόμα και κρατάμε ό,τι δείχνει.
                    id == null && !hasQueuedSongs() -> clearSnapshot(context)
                }
                render(context, isPlaying)
            }
        }
    }

    private fun hasQueuedSongs(): Boolean {
        val active = store.activeQueueId.value
        return store.queues.value.any { it.id == active && it.songIds.isNotEmpty() }
    }

    private suspend fun saveSnapshot(song: Song, artist: String) {
        val prefs = prefs(context)
        // Τα prefs γράφονται τελευταία: αν ακυρωθεί στη μέση, το εξώφυλλο ξαναφορτώνεται
        if (prefs.getLong(KEY_SONG, -1) != song.id) {
            val cover = artwork.load(song, ArtworkLoader.Side.FRONT, ART_SIZE)?.let(::roundedSquare)
            val file = artFile(context)
            if (cover != null) file.outputStream().use { cover.compress(Bitmap.CompressFormat.PNG, 100, it) } else file.delete()
        }
        prefs.edit()
            .putLong(KEY_SONG, song.id)
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

    companion object {
        private const val PREFS = "widget"
        private const val KEY_SONG = "song_id"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val ART_SIZE = 192
        private const val ART_RADIUS = 28f

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private fun artFile(context: Context) = File(context.cacheDir, "widget_art.png")

        private fun clearSnapshot(context: Context) {
            prefs(context).edit().clear().apply()
            artFile(context).delete()
        }

        /** Σχεδιάζει όλα τα widgets από ό,τι έχει γραφτεί στον δίσκο. */
        fun render(context: Context, isPlaying: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LyraWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, isPlaying))
        }

        private fun views(context: Context, isPlaying: Boolean): RemoteViews {
            val prefs = prefs(context)
            val title = prefs.getString(KEY_TITLE, null)
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)

            val openApp = PendingIntent.getActivity(
                context, 0,
                context.packageManager.getLaunchIntentForPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(android.R.id.background, openApp)

            // Χωρίς τραγούδι δεν έχουν τι να κάνουν τα κουμπιά: το widget απλώς ανοίγει την εφαρμογή
            val controls = if (title == null) View.GONE else View.VISIBLE
            views.setViewVisibility(R.id.widget_previous, controls)
            views.setViewVisibility(R.id.widget_toggle, controls)
            views.setViewVisibility(R.id.widget_next, controls)
            if (title == null) return views

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, prefs.getString(KEY_ARTIST, ""))
            val art = BitmapFactory.decodeFile(artFile(context).path)
            if (art != null) views.setImageViewBitmap(R.id.widget_art, art)
            else views.setImageViewResource(R.id.widget_art, R.drawable.ic_launcher_foreground)

            views.setImageViewResource(R.id.widget_toggle, if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            views.setContentDescription(R.id.widget_toggle, if (isPlaying) "Pause" else "Play")
            views.setOnClickPendingIntent(R.id.widget_toggle, LyraWidget.actionIntent(context, LyraWidget.ACTION_TOGGLE))
            views.setOnClickPendingIntent(R.id.widget_next, LyraWidget.actionIntent(context, LyraWidget.ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widget_previous, LyraWidget.actionIntent(context, LyraWidget.ACTION_PREVIOUS))
            return views
        }
    }
}
