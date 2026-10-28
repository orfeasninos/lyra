package app.lyra.player.data

import android.net.Uri

data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    /** Το πεδίο artist όπως είναι γραμμένο στο tag. */
    val artistRaw: String,
    val album: String,
    val albumId: Long,
    val albumArtistRaw: String?,
    val durationMs: Long,
    val track: Int,
    val year: Int,
    /** Πλήρης διαδρομή αρχείου (μπορεί να λείπει σε σπάνιες περιπτώσεις). */
    val path: String,
    /** Ο φάκελος στον οποίο βρίσκεται το αρχείο. */
    val folder: String,
    val fileName: String,
    val dateAdded: Long,
    /** Πότε άλλαξε τελευταία φορά το αρχείο (δευτερόλεπτα). */
    val dateModified: Long,
    val composer: String?,
)

/** Ένας "πραγματικός" καλλιτέχνης, αφού εφαρμοστούν τα ψευδώνυμα. */
data class Artist(
    val key: String,
    val name: String,
    /** Τα άλλα ονόματα με τα οποία εμφανίζεται στη βιβλιοθήκη ή που έχεις ορίσει. */
    val aliases: List<String>,
    val songs: List<Song>,
    val albumCount: Int,
)

data class Album(
    val key: String,
    val title: String,
    /** Ο album artist αν υπάρχει, αλλιώς ο καλλιτέχνης των τραγουδιών. */
    val artistName: String,
    val year: Int,
    val folder: String,
    val songs: List<Song>,
    /** Ο καλλιτέχνης των τραγουδιών ("Various Artists" αν διαφέρουν). */
    val artist: String,
    val albumArtist: String?,
    val composer: String?,
) {
    val cover: Song get() = songs.first()
    val durationMs: Long = songs.sumOf { it.durationMs }
    val dateUpdated: Long = songs.maxOf { it.dateModified }
}

data class Folder(
    val path: String,
    val songs: List<Song>,
) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifEmpty { path }
}

/** Οι καρτέλες της αρχικής οθόνης. */
enum class HomeTab(val label: String) {
    SONGS("Songs"),
    FOLDERS("Folders"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    QUEUES("Queues"),
}

data class TabSetting(val tab: HomeTab, val visible: Boolean)

/** Τι δείχνει η καρτέλα Artists. */
enum class ArtistsView(val label: String) {
    ARTISTS("Artists"),
    ALBUM_ARTISTS("Album-Artists"),
    COMPOSERS("Composers"),
}

/** Πόσες φορές παίχτηκε ένα τραγούδι και πότε τελευταία (ms). */
data class PlayStat(val count: Int, val lastPlayed: Long)

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
)

data class PlayQueue(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val index: Int,
    val positionMs: Long,
)

/**
 * Ομάδα ονομάτων που αντιστοιχούν στον ίδιο καλλιτέχνη.
 * Π.χ. displayName = "Prince", names = {"prince", "the artist formerly known as prince", "tafkap"}
 * Τα names κρατιούνται κανονικοποιημένα (lowercase, trimmed).
 */
data class ArtistGroup(
    val id: String,
    val displayName: String,
    val names: Set<String>,
)
