package app.lyra.player

import android.app.Application
import app.lyra.player.data.AppStore
import app.lyra.player.data.ArtworkLoader
import app.lyra.player.playback.MusicController

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
    }
}
