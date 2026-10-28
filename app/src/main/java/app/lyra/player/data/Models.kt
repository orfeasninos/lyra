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
    val artistName: String,
    val year: Int,
    val folder: String,
    val songs: List<Song>,
) {
    val cover: Song get() = songs.first()
}

data class Folder(
    val path: String,
    val songs: List<Song>,
) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifEmpty { path }
}

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
