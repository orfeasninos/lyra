package app.lyra.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lyra.player.LyraApp
import app.lyra.player.data.Library
import app.lyra.player.data.MediaScanner
import app.lyra.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val lyra = app as LyraApp
    val store = lyra.store
    val music = lyra.music
    val artwork = lyra.artwork

    private val songs = MutableStateFlow<List<Song>?>(null)

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val library: StateFlow<Library> =
        combine(songs, store.artistGroups, store.separators) { list, groups, seps ->
            if (list == null) Library.EMPTY else Library(list, groups, seps)
        }
            .flowOn(Dispatchers.Default)
            .onEach { music.onLibraryChanged(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, Library.EMPTY)

    val loaded: StateFlow<Boolean> get() = _loaded
    private val _loaded = MutableStateFlow(false)

    fun scan() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            artwork.clearMissing()
            songs.value = MediaScanner.scan(getApplication())
            _scanning.value = false
            _loaded.value = true
        }
    }
}
