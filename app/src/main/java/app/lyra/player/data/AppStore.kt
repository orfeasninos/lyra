package app.lyra.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Απλή αποθήκευση σε αρχεία JSON: ψευδώνυμα καλλιτεχνών, playlists, ουρές, ρυθμίσεις.
 * Δεν χρειάζεται βάση δεδομένων για αυτόν τον όγκο δεδομένων.
 */
class AppStore(context: Context) {

    private val dir = File(context.filesDir, "store").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    // ---------- Ψευδώνυμα καλλιτεχνών ----------

    private val _artistGroups = MutableStateFlow(loadGroups())
    val artistGroups: StateFlow<List<ArtistGroup>> = _artistGroups.asStateFlow()

    /** Διαχωριστικά για tags με πολλούς καλλιτέχνες, π.χ. "A feat. B". */
    private val _separators = MutableStateFlow(loadSeparators())
    val separators: StateFlow<List<String>> = _separators.asStateFlow()

    /**
     * Ενώνει τα ονόματα σε έναν καλλιτέχνη. Αν κάποιο όνομα ανήκει ήδη σε ομάδα,
     * οι ομάδες συγχωνεύονται.
     */
    fun linkNames(displayName: String, names: Collection<String>) {
        val normalized = (names + displayName).map(::norm).filter { it.isNotEmpty() }.toSet()
        _artistGroups.update { groups ->
            val touching = groups.filter { g -> g.names.any { it in normalized } }
            val rest = groups - touching.toSet()
            val merged = ArtistGroup(
                id = touching.firstOrNull()?.id ?: UUID.randomUUID().toString(),
                displayName = displayName.trim(),
                names = touching.flatMap { it.names }.toSet() + normalized,
            )
            rest + merged
        }
        saveGroups()
    }

    /** Αφαιρεί ένα όνομα από την ομάδα του (ξαναγίνεται ξεχωριστός καλλιτέχνης). */
    fun unlinkName(name: String) {
        val n = norm(name)
        _artistGroups.update { groups ->
            groups.mapNotNull { g ->
                if (n !in g.names) g
                else {
                    val left = g.names - n
                    if (left.size <= 1) null
                    else {
                        val newDisplay = if (norm(g.displayName) == n) left.first() else g.displayName
                        g.copy(names = left, displayName = newDisplay)
                    }
                }
            }
        }
        saveGroups()
    }

    fun renameGroup(anyName: String, newDisplay: String) {
        val n = norm(anyName)
        val existing = _artistGroups.value.any { n in it.names }
        if (!existing) {
            linkNames(newDisplay, listOf(anyName))
            return
        }
        _artistGroups.update { groups ->
            groups.map { g -> if (n in g.names) g.copy(displayName = newDisplay.trim(), names = g.names + norm(newDisplay)) else g }
        }
        saveGroups()
    }

    fun setSeparators(list: List<String>) {
        _separators.value = list.filter { it.isNotEmpty() }
        val arr = JSONArray().apply { _separators.value.forEach { put(it) } }
        write("separators.json", arr.toString())
    }

    private fun loadGroups(): List<ArtistGroup> {
        val arr = readArray("artists.json") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val names = o.getJSONArray("names")
            ArtistGroup(
                id = o.getString("id"),
                displayName = o.getString("display"),
                names = (0 until names.length()).map { names.getString(it) }.toSet(),
            )
        }
    }

    private fun saveGroups() {
        val arr = JSONArray()
        _artistGroups.value.forEach { g ->
            arr.put(JSONObject().apply {
                put("id", g.id)
                put("display", g.displayName)
                put("names", JSONArray(g.names.toList()))
            })
        }
        write("artists.json", arr.toString())
    }

    private fun loadSeparators(): List<String> {
        val arr = readArray("separators.json") ?: return DEFAULT_SEPARATORS
        return (0 until arr.length()).map { arr.getString(it) }
    }

    // ---------- Playlists ----------

    private val _playlists = MutableStateFlow(loadPlaylists())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun createPlaylist(name: String, songIds: List<Long> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        _playlists.update { it + Playlist(id, name.trim(), songIds) }
        savePlaylists()
        return id
    }

    fun addToPlaylist(id: String, songIds: List<Long>) {
        _playlists.update { list -> list.map { if (it.id == id) it.copy(songIds = it.songIds + songIds) else it } }
        savePlaylists()
    }

    fun removeFromPlaylist(id: String, position: Int) {
        _playlists.update { list ->
            list.map { p ->
                if (p.id == id && position in p.songIds.indices)
                    p.copy(songIds = p.songIds.toMutableList().apply { removeAt(position) })
                else p
            }
        }
        savePlaylists()
    }

    fun moveInPlaylist(id: String, from: Int, to: Int) {
        _playlists.update { list ->
            list.map { p ->
                if (p.id == id && from in p.songIds.indices && to in p.songIds.indices)
                    p.copy(songIds = p.songIds.toMutableList().apply { add(to, removeAt(from)) })
                else p
            }
        }
        savePlaylists()
    }

    fun renamePlaylist(id: String, name: String) {
        _playlists.update { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }
        savePlaylists()
    }

    fun deletePlaylist(id: String) {
        _playlists.update { list -> list.filterNot { it.id == id } }
        savePlaylists()
    }

    private fun loadPlaylists(): List<Playlist> {
        val arr = readArray("playlists.json") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Playlist(o.getString("id"), o.getString("name"), o.getJSONArray("songs").toLongList())
        }
    }

    private fun savePlaylists() {
        val arr = JSONArray()
        _playlists.value.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("songs", JSONArray(p.songIds))
            })
        }
        write("playlists.json", arr.toString())
    }

    // ---------- Αγαπημένα ----------

    /** Με τη σειρά που προστέθηκαν (το πιο πρόσφατο στο τέλος). */
    private val _favorites = MutableStateFlow(readArray("favorites.json")?.toLongList().orEmpty())
    val favorites: StateFlow<List<Long>> = _favorites.asStateFlow()

    fun toggleFavorite(songId: Long) {
        val list = _favorites.updateAndGet { if (songId in it) it - songId else it + songId }
        write("favorites.json", JSONArray(list).toString())
    }

    // ---------- Ιστορικό αναπαραγωγής ----------

    private val _playStats = MutableStateFlow(loadPlayStats())
    val playStats: StateFlow<Map<Long, PlayStat>> = _playStats.asStateFlow()

    fun recordPlay(songId: Long) {
        val now = System.currentTimeMillis()
        val stats = _playStats.updateAndGet { m ->
            m + (songId to PlayStat((m[songId]?.count ?: 0) + 1, now))
        }
        val o = JSONObject()
        stats.forEach { (id, s) -> o.put(id.toString(), JSONArray().put(s.count).put(s.lastPlayed)) }
        write("play_stats.json", o.toString())
    }

    private fun loadPlayStats(): Map<Long, PlayStat> {
        val o = readText("play_stats.json")?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyMap()
        return o.keys().asSequence().mapNotNull { k ->
            val a = o.optJSONArray(k) ?: return@mapNotNull null
            k.toLongOrNull()?.let { it to PlayStat(a.optInt(0), a.optLong(1)) }
        }.toMap()
    }

    // ---------- Ουρές (όπως στο Musicolet, πολλές ουρές ταυτόχρονα) ----------

    private val _queues = MutableStateFlow(loadQueues())
    val queues: StateFlow<List<PlayQueue>> = _queues.asStateFlow()

    private val _activeQueueId = MutableStateFlow(readText("active_queue.txt")?.trim())
    val activeQueueId: StateFlow<String?> = _activeQueueId.asStateFlow()

    fun upsertQueue(queue: PlayQueue) {
        _queues.update { list ->
            if (list.any { it.id == queue.id }) list.map { if (it.id == queue.id) queue else it } else list + queue
        }
        saveQueues()
    }

    fun deleteQueue(id: String) {
        _queues.update { list -> list.filterNot { it.id == id } }
        if (_activeQueueId.value == id) setActiveQueue(null)
        saveQueues()
    }

    fun setActiveQueue(id: String?) {
        _activeQueueId.value = id
        write("active_queue.txt", id.orEmpty())
    }

    private fun loadQueues(): List<PlayQueue> {
        val arr = readArray("queues.json") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PlayQueue(
                id = o.getString("id"),
                name = o.getString("name"),
                songIds = o.getJSONArray("songs").toLongList(),
                index = o.optInt("index", 0),
                positionMs = o.optLong("pos", 0L),
            )
        }
    }

    private fun saveQueues() {
        val arr = JSONArray()
        _queues.value.forEach { q ->
            arr.put(JSONObject().apply {
                put("id", q.id); put("name", q.name); put("songs", JSONArray(q.songIds))
                put("index", q.index); put("pos", q.positionMs)
            })
        }
        write("queues.json", arr.toString())
    }

    // ---------- Ρυθμίσεις καρτέλας άλμπουμ ----------

    private val _albumPrefs = MutableStateFlow(loadAlbumPrefs())
    val albumPrefs: StateFlow<AlbumPrefs> = _albumPrefs.asStateFlow()

    fun updateAlbumPrefs(change: (AlbumPrefs) -> AlbumPrefs) {
        val p = _albumPrefs.updateAndGet(change)
        write("album_prefs.json", JSONObject().apply {
            put("sort", p.sort.name); put("desc", p.descending)
            put("view", p.viewMode.name); put("grid", p.gridSize.name)
            put("extra", p.extraInfo.name); put("quickSearch", p.quickSearch)
        }.toString())
    }

    private fun loadAlbumPrefs(): AlbumPrefs {
        val o = readText("album_prefs.json")?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return AlbumPrefs()
        val d = AlbumPrefs()
        return AlbumPrefs(
            sort = enumOr(o.optString("sort"), d.sort),
            descending = o.optBoolean("desc", d.descending),
            viewMode = enumOr(o.optString("view"), d.viewMode),
            gridSize = enumOr(o.optString("grid"), d.gridSize),
            extraInfo = enumOr(o.optString("extra"), d.extraInfo),
            quickSearch = o.optBoolean("quickSearch", d.quickSearch),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    // ---------- Βοηθητικά ----------

    private fun readText(name: String): String? =
        File(dir, name).takeIf { it.exists() }?.runCatching { readText() }?.getOrNull()

    private fun readArray(name: String): JSONArray? =
        readText(name)?.let { runCatching { JSONArray(it) }.getOrNull() }

    private fun write(name: String, content: String) {
        scope.launch {
            writeLock.withLock {
                val tmp = File(dir, "$name.tmp")
                tmp.writeText(content)
                tmp.renameTo(File(dir, name))
            }
        }
    }

    private fun JSONArray.toLongList(): List<Long> = (0 until length()).map { getLong(it) }

    companion object {
        val DEFAULT_SEPARATORS = listOf(";", " / ", " feat. ", " feat ", " ft. ", " featuring ", " x ")
        fun norm(s: String) = s.trim().lowercase()
    }
}
