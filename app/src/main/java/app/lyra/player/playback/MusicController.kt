package app.lyra.player.playback

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.lyra.player.data.AppStore
import app.lyra.player.data.Library
import app.lyra.player.data.PlayQueue
import app.lyra.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class PlaybackState(
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<Long> = emptyList(),
    val index: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/**
 * Συνδέεται στο PlaybackService και διαχειρίζεται τις πολλαπλές ουρές.
 * Η "ενεργή" ουρά είναι αυτή που παίζει τώρα. Οι υπόλοιπες κρατούν
 * τραγούδια, θέση και χρόνο, ώστε να συνεχίζεις από εκεί που τις άφησες.
 */
class MusicController(
    private val context: Context,
    private val store: AppStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var library: Library = Library.EMPTY
    private var restored = false
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refreshState()
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                )
            ) saveActiveQueue()
        }
    }

    fun connect() {
        if (controller != null) return
        scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val c = runCatching { MediaController.Builder(context, token).buildAsync().await() }.getOrNull()
                ?: return@launch
            controller = c
            c.addListener(listener)
            refreshState()
            restoreIfNeeded()
            startTicker()
        }
    }

    fun onLibraryChanged(lib: Library) {
        library = lib
        restoreIfNeeded()
    }

    // ------------------------- Αναπαραγωγή -------------------------

    /** Παίζει μια λίστα στην ενεργή ουρά (την αντικαθιστά), όπως το "tap" στο Musicolet. */
    fun play(songs: List<Song>, startIndex: Int = 0) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        ensureActiveQueue()
        c.setMediaItems(songs.map(::toItem), startIndex.coerceIn(0, songs.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        play(songs.shuffled(), 0)
    }

    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(songs)
        c.addMediaItems((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), songs.map(::toItem))
    }

    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            ensureActiveQueue()
            c.setMediaItems(songs.map(::toItem))
            c.prepare()
            return
        }
        c.addMediaItems(songs.map(::toItem))
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(c.currentMediaItemIndex, 0)
            c.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun skipTo(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0)
            c.play()
        }
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    // ------------------------- Πολλαπλές ουρές -------------------------

    fun newQueue(songs: List<Song>, startIndex: Int = 0) {
        saveActiveQueue()
        val q = PlayQueue(UUID.randomUUID().toString(), nextQueueName(), songs.map { it.id }, startIndex, 0)
        store.upsertQueue(q)
        store.setActiveQueue(q.id)
        load(q, autoPlay = true)
    }

    fun switchQueue(id: String) {
        if (id == store.activeQueueId.value) return
        val target = store.queues.value.firstOrNull { it.id == id } ?: return
        val wasPlaying = controller?.isPlaying == true
        saveActiveQueue()
        store.setActiveQueue(id)
        load(target, autoPlay = wasPlaying)
    }

    fun deleteQueue(id: String) {
        val isActive = id == store.activeQueueId.value
        store.deleteQueue(id)
        if (isActive) {
            val other = store.queues.value.firstOrNull()
            if (other != null) {
                store.setActiveQueue(other.id)
                load(other, autoPlay = false)
            } else {
                controller?.run { stop(); clearMediaItems() }
            }
        }
    }

    fun renameQueue(id: String, name: String) {
        store.queues.value.firstOrNull { it.id == id }?.let { store.upsertQueue(it.copy(name = name.trim())) }
    }

    private fun load(q: PlayQueue, autoPlay: Boolean) {
        val c = controller ?: return
        val items = q.songIds.mapNotNull { library.songsById[it] }.map(::toItem)
        if (items.isEmpty()) {
            c.stop(); c.clearMediaItems(); return
        }
        c.setMediaItems(items, q.index.coerceIn(0, items.lastIndex), q.positionMs)
        c.prepare()
        if (autoPlay) c.play()
    }

    private fun ensureActiveQueue() {
        val active = store.activeQueueId.value
        if (active != null && store.queues.value.any { it.id == active }) return
        val q = PlayQueue(UUID.randomUUID().toString(), nextQueueName(), emptyList(), 0, 0)
        store.upsertQueue(q)
        store.setActiveQueue(q.id)
    }

    private fun nextQueueName(): String {
        val used = store.queues.value.map { it.name }.toSet()
        var n = 1
        while ("Queue $n" in used) n++
        return "Queue $n"
    }

    private fun saveActiveQueue() {
        val c = controller ?: return
        if (!restored) return
        val id = store.activeQueueId.value ?: return
        val existing = store.queues.value.firstOrNull { it.id == id } ?: return
        val ids = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
        store.upsertQueue(existing.copy(songIds = ids, index = c.currentMediaItemIndex, positionMs = c.currentPosition))
    }

    private fun restoreIfNeeded() {
        val c = controller ?: return
        if (restored || library.songs.isEmpty()) return
        restored = true
        if (c.mediaItemCount > 0) return // το service παίζει ήδη
        val active = store.queues.value.firstOrNull { it.id == store.activeQueueId.value } ?: return
        load(active, autoPlay = false)
    }

    // ------------------------- Κατάσταση -------------------------

    private fun refreshState() {
        val c = controller ?: return
        _state.value = PlaybackState(
            currentSongId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            queue = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() },
            index = c.currentMediaItemIndex,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
        )
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var n = 0
            while (isActive) {
                delay(500)
                val c = controller ?: continue
                if (c.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = c.currentPosition,
                        durationMs = c.duration.takeIf { it > 0 } ?: _state.value.durationMs,
                    )
                    if (++n % 20 == 0) saveActiveQueue() // κάθε ~10 δευτερόλεπτα
                }
            }
        }
    }

    private fun toItem(song: Song): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(library.artistLine(song))
                    .setAlbumTitle(song.album)
                    .setArtworkUri(ContentUris.withAppendedId(ALBUM_ART, song.albumId))
                    .build()
            )
            .build()

    companion object {
        private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
