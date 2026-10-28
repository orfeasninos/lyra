package app.lyra.player.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaScanner {

    suspend fun scan(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            "album_artist",
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.COMPOSER,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val songs = ArrayList<Song>()

        val cursor = try {
            context.contentResolver.query(collection, projection, selection, null, null)
        } catch (e: IllegalArgumentException) {
            // Κάποιες συσκευές δεν έχουν τη στήλη album_artist
            context.contentResolver.query(
                collection, projection.filter { it != "album_artist" }.toTypedArray(), selection, null, null
            )
        }

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val albumArtistCol = c.getColumnIndex("album_artist")
            val durCol = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            val nameCol = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val composerCol = c.getColumnIndex(MediaStore.Audio.Media.COMPOSER)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val path = if (dataCol >= 0) c.getString(dataCol).orEmpty() else ""
                val fileName = (if (nameCol >= 0) c.getString(nameCol) else null)
                    ?: path.substringAfterLast('/')
                val artist = (if (artistCol >= 0) c.getString(artistCol) else null)
                    ?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING } ?: "Unknown artist"
                songs += Song(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = (if (titleCol >= 0) c.getString(titleCol) else null)?.takeUnless { it.isBlank() }
                        ?: fileName.substringBeforeLast('.'),
                    artistRaw = artist,
                    album = (if (albumCol >= 0) c.getString(albumCol) else null)?.takeUnless { it.isBlank() }
                        ?: "Unknown album",
                    albumId = if (albumIdCol >= 0) c.getLong(albumIdCol) else 0L,
                    albumArtistRaw = if (albumArtistCol >= 0) c.getString(albumArtistCol)?.takeUnless { it.isBlank() } else null,
                    durationMs = if (durCol >= 0) c.getLong(durCol) else 0L,
                    track = if (trackCol >= 0) c.getInt(trackCol) else 0,
                    year = if (yearCol >= 0) c.getInt(yearCol) else 0,
                    path = path,
                    folder = path.substringBeforeLast('/', missingDelimiterValue = ""),
                    fileName = fileName,
                    dateAdded = if (dateCol >= 0) c.getLong(dateCol) else 0L,
                    dateModified = if (modifiedCol >= 0) c.getLong(modifiedCol) else 0L,
                    composer = if (composerCol >= 0) c.getString(composerCol)?.takeUnless { it.isBlank() } else null,
                )
            }
        }
        songs
    }
}
