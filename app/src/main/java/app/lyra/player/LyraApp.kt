package app.lyra.player

import android.app.Application
import android.content.res.Configuration
import android.os.Build
import app.lyra.player.data.AppStore
import app.lyra.player.data.ArtworkLoader
import app.lyra.player.playback.MusicController
import app.lyra.player.widget.WidgetUpdater

class LyraApp : Application() {
    lateinit var store: AppStore
        private set
    lateinit var artwork: ArtworkLoader
        private set
    lateinit var music: MusicController
        private set

    override fun onCreate() {
        super.onCreate()
        store = AppStore(this)
        artwork = ArtworkLoader(this)
        music = MusicController(this, store)
        WidgetUpdater(this, music, store, artwork).start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Πριν το Android 12 το widget "Match with system" δεν αλλάζει μόνο του με το σκοτεινό θέμα
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) WidgetUpdater.render(this)
    }
}
