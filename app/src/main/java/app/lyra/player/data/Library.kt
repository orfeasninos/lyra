package app.lyra.player.data

import app.lyra.player.data.AppStore.Companion.norm

/** Αναφορά σε καλλιτέχνη όπως εμφανίζεται σε ένα τραγούδι. */
data class ArtistRef(val key: String, val name: String)

/**
 * Η βιβλιοθήκη όπως τη βλέπει ο χρήστης, αφού εφαρμοστούν τα ψευδώνυμα.
 * Ξαναχτίζεται κάθε φορά που αλλάζουν τα τραγούδια, τα ψευδώνυμα ή τα διαχωριστικά.
 */
class Library(
    val songs: List<Song>,
    groups: List<ArtistGroup>,
    separators: List<String>,
) {
    val songsById: Map<Long, Song> = songs.associateBy { it.id }

    private val groupByName: Map<String, ArtistGroup> =
        groups.flatMap { g -> g.names.map { it to g } }.toMap()

    /** Όλα τα ονόματα (όπως είναι γραμμένα στα tags) που υπάρχουν στη βιβλιοθήκη. */
    val rawArtistNames: List<String>

    private val songArtists: Map<Long, List<ArtistRef>>

    val artists: List<Artist>
    val albums: List<Album>
    val folders: List<Folder>

    init {
        // 1. Κάθε τραγούδι -> λίστα καλλιτεχνών
        val spellings = HashMap<String, MutableMap<String, Int>>() // key -> (spelling -> count)
        val rawNames = LinkedHashSet<String>()
        val perSong = HashMap<Long, List<ArtistRef>>(songs.size)

        for (s in songs) {
            val parts = split(s.artistRaw, separators)
            val refs = parts.map { part ->
                rawNames += part
                val g = groupByName[norm(part)]
                val key = g?.let { "g:${it.id}" } ?: "n:${norm(part)}"
                spellings.getOrPut(key) { HashMap() }.merge(part, 1, Int::plus)
                ArtistRef(key, g?.displayName ?: part)
            }.distinctBy { it.key }
            perSong[s.id] = refs
        }
        songArtists = perSong
        rawArtistNames = rawNames.sortedWith(byName { it })

        // Για καλλιτέχνες χωρίς ομάδα, η πιο συχνή γραφή γίνεται το όνομα
        fun displayFor(key: String): String {
            if (key.startsWith("g:")) {
                return groups.first { "g:${it.id}" == key }.displayName
            }
            return spellings[key]?.maxByOrNull { it.value }?.key ?: key.removePrefix("n:")
        }

        // 2. Άλμπουμ
        val albumMap = LinkedHashMap<String, MutableList<Song>>()
        for (s in songs) {
            val aa = s.albumArtistRaw?.let { resolveName(it) }
            val key = if (aa != null) "${norm(s.album)}|${norm(aa)}" else "${norm(s.album)}|${s.folder}"
            albumMap.getOrPut(key) { ArrayList() } += s
        }
        albums = albumMap.map { (key, list) ->
            val sorted = list.sortedWith(compareBy<Song> { it.track }.then(byName { it.fileName }))
            val first = sorted.first()
            val albumArtist = first.albumArtistRaw?.let { resolveName(it) }
            val artist = perSong[first.id]?.firstOrNull()?.key?.let { k ->
                if (sorted.all { perSong[it.id]?.firstOrNull()?.key == k }) displayFor(k) else "Various Artists"
            } ?: first.artistRaw
            val composers = sorted.mapNotNull { it.composer?.trim() }.distinctBy { norm(it) }
            Album(
                key = key,
                title = first.album,
                artistName = albumArtist ?: artist,
                year = sorted.maxOf { it.year },
                folder = first.folder,
                songs = sorted,
                artist = artist,
                albumArtist = albumArtist,
                composer = composers.takeIf { it.isNotEmpty() }?.joinToString(", "),
            )
        }.sortedWith(byName { it.title })

        // 3. Καλλιτέχνες
        val artistSongs = LinkedHashMap<String, MutableList<Song>>()
        for (s in songs) perSong[s.id]?.forEach { ref -> artistSongs.getOrPut(ref.key) { ArrayList() } += s }
        // Συμπεριλαμβάνουμε και τους album artists
        for (al in albums) {
            al.songs.first().albumArtistRaw?.let { raw ->
                val g = groupByName[norm(raw)]
                val key = g?.let { "g:${it.id}" } ?: "n:${norm(raw)}"
                spellings.getOrPut(key) { HashMap() }.merge(raw, 1, Int::plus)
                val list = artistSongs.getOrPut(key) { ArrayList() }
                al.songs.forEach { if (it !in list) list += it }
            }
        }

        artists = artistSongs.map { (key, list) ->
            val name = displayFor(key)
            val group = if (key.startsWith("g:")) groups.first { "g:${it.id}" == key } else null
            val seen = spellings[key]?.keys.orEmpty()
            val aliasSet = LinkedHashSet<String>()
            seen.filter { norm(it) != norm(name) }.forEach { aliasSet += it }
            group?.names?.filter { n -> n != norm(name) && seen.none { norm(it) == n } }?.forEach { aliasSet += it }
            val distinctAliases = aliasSet.distinctBy { norm(it) }
            Artist(
                key = key,
                name = name,
                aliases = distinctAliases,
                songs = list.sortedWith(byName<Song> { it.album }.thenBy { it.track }),
                albumCount = list.map { it.album.lowercase() }.distinct().size,
            )
        }.sortedWith(byName { it.name })

        // 4. Φάκελοι
        folders = songs.groupBy { it.folder }
            .map { (path, list) -> Folder(path, list.sortedWith(byName { it.fileName })) }
            .sortedWith(byName { it.path })
    }

    fun artistsOf(song: Song): List<ArtistRef> = songArtists[song.id].orEmpty()

    fun artistLine(song: Song): String =
        artistsOf(song).joinToString(", ") { it.name }.ifEmpty { song.artistRaw }

    fun artist(key: String): Artist? = artists.firstOrNull { it.key == key }
    fun album(key: String): Album? = albums.firstOrNull { it.key == key }
    fun folder(path: String): Folder? = folders.firstOrNull { it.path == path }
    fun albumOf(song: Song): Album? = albums.firstOrNull { al -> al.songs.any { it.id == song.id } }

    private fun resolveName(raw: String): String = groupByName[norm(raw)]?.displayName ?: raw.trim()

    /** Αναζήτηση που "καταλαβαίνει" και τα ψευδώνυμα. */
    fun search(query: String): SearchResult {
        val q = searchKey(query)
        if (q.isEmpty()) return SearchResult(emptyList(), emptyList(), emptyList())
        val matchedArtists = artists.filter { a ->
            searchKey(a.name).contains(q) || a.aliases.any { searchKey(it).contains(q) }
        }
        val artistKeys = matchedArtists.map { it.key }.toSet()
        val matchedSongs = songs.filter { s ->
            searchKey(s.title).contains(q) || searchKey(s.album).contains(q) ||
                searchKey(s.artistRaw).contains(q) || artistsOf(s).any { it.key in artistKeys }
        }
        val matchedAlbums = albums.filter { searchKey(it.title).contains(q) || searchKey(it.artistName).contains(q) }
        return SearchResult(matchedSongs.take(200), matchedAlbums.take(50), matchedArtists.take(50))
    }

    private fun split(raw: String, separators: List<String>): List<String> {
        // Αν ολόκληρο το όνομα έχει δηλωθεί ως ψευδώνυμο, δεν το σπάμε (π.χ. "Simon & Garfunkel")
        if (groupByName.containsKey(norm(raw))) return listOf(raw.trim())
        var parts = listOf(raw)
        for (sep in separators) {
            parts = parts.flatMap { p -> splitIgnoreCase(p, sep) }
        }
        return parts.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(raw.trim()) }
    }

    private fun splitIgnoreCase(text: String, sep: String): List<String> {
        if (sep.isEmpty()) return listOf(text)
        val out = ArrayList<String>()
        var start = 0
        while (true) {
            val i = text.indexOf(sep, start, ignoreCase = true)
            if (i < 0) break
            out += text.substring(start, i)
            start = i + sep.length
        }
        out += text.substring(start)
        return out
    }

    companion object {
        val EMPTY = Library(emptyList(), emptyList(), emptyList())
    }
}

data class SearchResult(val songs: List<Song>, val albums: List<Album>, val artists: List<Artist>)
