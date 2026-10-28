package app.lyra.player.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Φορτώνει εξώφυλλα και οπισθόφυλλα.
 *
 * Εξώφυλλο:   ενσωματωμένη εικόνα -> cover/folder/front.jpg στον φάκελο.
 * Οπισθόφυλλο: ενσωματωμένη εικόνα τύπου "Back cover" -> back/rear/inlay/tray.jpg
 *              στον φάκελο του άλμπουμ ή σε υποφάκελο (Scans, Artwork, Covers...).
 */
class ArtworkLoader(private val context: Context) {

    enum class Side { FRONT, BACK }

    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val folderImages = LruCache<String, List<FolderImage>>(64)
    private val locks = Array(8) { Mutex() }

    data class FolderImage(val uri: Uri, val name: String, val relative: String)

    fun cached(song: Song, side: Side, size: Int): Bitmap? = cache.get(key(song, side, size))

    fun isKnownMissing(song: Song, side: Side): Boolean = missing.contains("${side}|${song.albumId}|${song.folder}")

    suspend fun load(song: Song, side: Side, size: Int = 512): Bitmap? {
        val k = key(song, side, size)
        cache.get(k)?.let { return it }
        val missKey = "${side}|${song.albumId}|${song.folder}"
        if (missKey in missing) return null

        return locks[(k.hashCode() and 0x7fffffff) % locks.size].withLock {
            cache.get(k)?.let { return@withLock it }
            val bmp = withContext(Dispatchers.IO) {
                runCatching { if (side == Side.FRONT) loadFront(song, size) else loadBack(song, size) }.getOrNull()
            }
            if (bmp != null) cache.put(k, bmp) else missing += missKey
            bmp
        }
    }

    /** Ξεχνάει ότι κάποιο άλμπουμ δεν είχε εικόνα (π.χ. μετά από νέο σκανάρισμα). */
    fun clearMissing() {
        missing.clear()
        folderImages.evictAll()
    }

    // ---------------------------------------------------------------

    private fun loadFront(song: Song, size: Int): Bitmap? {
        embeddedPrimary(song)?.let { bytes -> decode(bytes, size)?.let { return it } }
        val img = folderImagesOf(song.folder).firstOrNull { FRONT_NAME.containsMatchIn(it.name.lowercase()) && it.relative.isEmpty() }
            ?: folderImagesOf(song.folder).firstOrNull { FRONT_NAME.containsMatchIn(it.name.lowercase()) }
        return img?.let { decodeUri(it.uri, size) }
    }

    private fun loadBack(song: Song, size: Int): Bitmap? {
        // 1. Ενσωματωμένο οπισθόφυλλο
        val embedded = runCatching {
            context.contentResolver.openInputStream(song.uri)?.use { EmbeddedPictures.read(it) }
        }.getOrNull().orEmpty()
        embedded.firstOrNull { it.type == EmbeddedPictures.TYPE_BACK }?.let { pic ->
            decode(pic.data, size)?.let { return it }
        }
        // 2. Εικόνα στον φάκελο
        val candidates = folderImagesOf(song.folder).filter { BACK_NAME.containsMatchIn(it.name.lowercase().substringBeforeLast('.')) }
        val best = candidates.sortedWith(compareBy<FolderImage> { it.relative.length }.thenBy { it.name.length }).firstOrNull()
        return best?.let { decodeUri(it.uri, size) }
    }

    private fun embeddedPrimary(song: Song): ByteArray? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, song.uri)
            mmr.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    /** Εικόνες μέσα στον φάκελο του άλμπουμ και σε έναν επίπεδο υποφακέλων. */
    private fun folderImagesOf(folder: String): List<FolderImage> {
        if (folder.isEmpty()) return emptyList()
        folderImages.get(folder)?.let { return it }
        val out = ArrayList<FolderImage>()
        val prefix = "$folder/"
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME),
                "${MediaStore.Images.Media.DATA} LIKE ?",
                // Τα _ και % στο όνομα του φακέλου μπορεί να φέρουν παραπάνω αποτελέσματα,
                // αλλά τα φιλτράρουμε παρακάτω με startsWith.
                arrayOf("$prefix%"),
                null,
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val data = c.getString(dataCol) ?: continue
                    if (!data.startsWith(prefix)) continue
                    val rel = data.removePrefix(prefix).substringBeforeLast('/', missingDelimiterValue = "")
                    if (rel.contains('/')) continue // μόνο ένα επίπεδο βάθος
                    val name = c.getString(nameCol) ?: data.substringAfterLast('/')
                    out += FolderImage(
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol)),
                        name,
                        rel,
                    )
                }
            }
        }
        folderImages.put(folder, out)
        return out
    }

    private fun decode(bytes: ByteArray, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight, size) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun decodeUri(uri: Uri, size: Int): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight, size) }
        return cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun sample(w: Int, h: Int, target: Int): Int {
        var s = 1
        while (w / (s * 2) >= target && h / (s * 2) >= target) s *= 2
        return s
    }

    private fun key(song: Song, side: Side, size: Int) = "$side|${song.albumId}|${song.folder}|$size"

    companion object {
        private val FRONT_NAME = Regex("(^|[^a-z])(cover|folder|front|albumart[a-z_]*large|albumart)([^a-z]|$)")
        private val BACK_NAME = Regex("(^|[^a-z])(back|rear|inlay|tray|traycard|back ?cover|πίσω|οπισθόφυλλο)([^a-zα-ω]|$)")

        private fun maxCacheBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(64L * 1024 * 1024).toInt()
    }
}
