package app.lyra.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import app.lyra.player.LyraApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Το home screen widget. Τα κουμπιά περνάνε από το [app.lyra.player.playback.MusicController],
 * όπως και της εφαρμογής, ακόμα κι αν η εφαρμογή δεν τρέχει όταν πατηθούν.
 */
class LyraWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as LyraApp
        WidgetUpdater.render(context, app.music.state.value.isPlaying)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action != ACTION_TOGGLE && action != ACTION_NEXT && action != ACTION_PREVIOUS) return

        val music = (context.applicationContext as LyraApp).music
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                if (withTimeoutOrNull(READY_TIMEOUT_MS) { music.awaitReady() } != true) return@launch
                when (action) {
                    ACTION_NEXT -> music.next()
                    ACTION_PREVIOUS -> music.previous()
                    ACTION_TOGGLE -> {
                        val starting = !music.state.value.isPlaying
                        music.togglePlay()
                        // Το σύστημα επιτρέπει το foreground service από το background όσο τρέχει
                        // αυτός ο receiver, άρα μένουμε ζωντανοί μέχρι να βγει η ειδοποίηση.
                        if (starting) {
                            withTimeoutOrNull(START_TIMEOUT_MS) { music.state.first { it.isPlaying } }
                            delay(NOTIFICATION_GRACE_MS)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "app.lyra.player.widget.TOGGLE"
        const val ACTION_NEXT = "app.lyra.player.widget.NEXT"
        const val ACTION_PREVIOUS = "app.lyra.player.widget.PREVIOUS"

        private const val READY_TIMEOUT_MS = 6_000L
        private const val START_TIMEOUT_MS = 2_000L
        private const val NOTIFICATION_GRACE_MS = 500L

        fun actionIntent(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, LyraWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
