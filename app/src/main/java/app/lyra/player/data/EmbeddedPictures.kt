package app.lyra.player.data

import java.io.DataInputStream
import java.io.InputStream

/**
 * Διαβάζει εικόνες ενσωματωμένες στα tags, μαζί με τον τύπο τους.
 * Το Android δίνει μόνο την "κύρια" εικόνα (συνήθως το εξώφυλλο),
 * οπότε για το οπισθόφυλλο (τύπος 4 στο ID3/FLAC) διαβάζουμε τα tags μόνοι μας.
 *
 * Υποστηρίζονται: MP3 (ID3v2.2/2.3/2.4) και FLAC.
 */
object EmbeddedPictures {

    const val TYPE_FRONT = 3
    const val TYPE_BACK = 4

    data class Picture(val type: Int, val mime: String, val data: ByteArray)

    private const val MAX_TAG = 40 * 1024 * 1024

    fun read(input: InputStream): List<Picture> {
        val stream = DataInputStream(input.buffered())
        val head = ByteArray(4)
        if (!readFully(stream, head, 3)) return emptyList()

        if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
            val pics = readId3(stream)
            if (pics.isNotEmpty()) return pics
            // Μερικά FLAC έχουν ID3 μπροστά. Μετά το ID3 ψάχνουμε για fLaC.
            if (!readFully(stream, head, 4)) return emptyList()
            return if (String(head, Charsets.ISO_8859_1) == "fLaC") readFlacBlocks(stream) else emptyList()
        }

        if (!readFully(stream, head, 1, offset = 3)) return emptyList()
        if (String(head, Charsets.ISO_8859_1) == "fLaC") return readFlacBlocks(stream)
        return emptyList()
    }

    // ----------------------------- ID3v2 -----------------------------

    private fun readId3(s: DataInputStream): List<Picture> {
        val major = s.readUnsignedByte()
        s.readUnsignedByte() // revision
        val flags = s.readUnsignedByte()
        val size = syncsafe(s.readInt())
        if (size <= 0 || size > MAX_TAG) return emptyList()
        var tag = ByteArray(size)
        if (!readFully(s, tag, size)) return emptyList()

        // Unsynchronisation σε όλο το tag (v2.2/v2.3)
        if (flags and 0x80 != 0 && major < 4) tag = deUnsync(tag)

        var pos = 0
        if (flags and 0x40 != 0 && major >= 3) {
            // extended header
            val extSize = if (major == 4) syncsafe(int(tag, 0)) else int(tag, 0) + 4
            pos += extSize
        }

        val out = ArrayList<Picture>()
        while (pos < tag.size) {
            if (major == 2) {
                if (pos + 6 > tag.size) break
                val id = String(tag, pos, 3, Charsets.ISO_8859_1)
                val fsize = ((tag[pos + 3].toInt() and 0xFF) shl 16) or
                    ((tag[pos + 4].toInt() and 0xFF) shl 8) or (tag[pos + 5].toInt() and 0xFF)
                pos += 6
                if (id[0] == '\u0000' || fsize <= 0 || pos + fsize > tag.size) break
                if (id == "PIC") parsePic22(tag, pos, fsize)?.let { out += it }
                pos += fsize
            } else {
                if (pos + 10 > tag.size) break
                val id = String(tag, pos, 4, Charsets.ISO_8859_1)
                val rawSize = int(tag, pos + 4)
                val fsize = if (major == 4) syncsafe(rawSize) else rawSize
                val fflags = ((tag[pos + 8].toInt() and 0xFF) shl 8) or (tag[pos + 9].toInt() and 0xFF)
                pos += 10
                if (id[0] == '\u0000' || fsize <= 0 || pos + fsize > tag.size) break
                if (id == "APIC") {
                    var frame = tag.copyOfRange(pos, pos + fsize)
                    if (major == 4) {
                        if (fflags and 0x0001 != 0 && frame.size > 4) frame = frame.copyOfRange(4, frame.size)
                        if (fflags and 0x0002 != 0) frame = deUnsync(frame)
                    }
                    parseApic(frame)?.let { out += it }
                }
                pos += fsize
            }
        }
        return out
    }

    private fun parseApic(f: ByteArray): Picture? {
        if (f.size < 4) return null
        val enc = f[0].toInt()
        var p = 1
        val mimeEnd = indexOfZero(f, p, 1) ?: return null
        val mime = String(f, p, mimeEnd - p, Charsets.ISO_8859_1)
        p = mimeEnd + 1
        if (p >= f.size) return null
        val type = f[p].toInt() and 0xFF
        p++
        val wide = enc == 1 || enc == 2
        val descEnd = indexOfZero(f, p, if (wide) 2 else 1) ?: return null
        p = descEnd + if (wide) 2 else 1
        if (p >= f.size) return null
        return Picture(type, mime, f.copyOfRange(p, f.size))
    }

    private fun parsePic22(tag: ByteArray, start: Int, len: Int): Picture? {
        val f = tag.copyOfRange(start, start + len)
        if (f.size < 6) return null
        val enc = f[0].toInt()
        val fmt = String(f, 1, 3, Charsets.ISO_8859_1)
        val type = f[4].toInt() and 0xFF
        val wide = enc == 1 || enc == 2
        val descEnd = indexOfZero(f, 5, if (wide) 2 else 1) ?: return null
        val p = descEnd + if (wide) 2 else 1
        if (p >= f.size) return null
        val mime = if (fmt.equals("PNG", true)) "image/png" else "image/jpeg"
        return Picture(type, mime, f.copyOfRange(p, f.size))
    }

    /** Βρίσκει τον τερματισμό συμβολοσειράς (1 ή 2 μηδενικά bytes). */
    private fun indexOfZero(b: ByteArray, from: Int, width: Int): Int? {
        var i = from
        while (i + width - 1 < b.size) {
            if (width == 1) {
                if (b[i].toInt() == 0) return i
                i++
            } else {
                if (b[i].toInt() == 0 && b[i + 1].toInt() == 0) return i
                i += 2
            }
        }
        return null
    }

    private fun deUnsync(b: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if ((b[i].toInt() and 0xFF) == 0xFF && i + 1 < b.size && b[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    private fun syncsafe(v: Int): Int =
        ((v shr 24 and 0x7F) shl 21) or ((v shr 16 and 0x7F) shl 14) or ((v shr 8 and 0x7F) shl 7) or (v and 0x7F)

    private fun int(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    // ----------------------------- FLAC -----------------------------

    private fun readFlacBlocks(s: DataInputStream): List<Picture> {
        val out = ArrayList<Picture>()
        while (true) {
            val header = s.read()
            if (header < 0) break
            val last = header and 0x80 != 0
            val type = header and 0x7F
            val len = (s.readUnsignedByte() shl 16) or (s.readUnsignedByte() shl 8) or s.readUnsignedByte()
            if (type == 6 && len in 32..MAX_TAG) {
                val block = ByteArray(len)
                if (!readFully(s, block, len)) break
                parseFlacPicture(block)?.let { out += it }
            } else {
                skipFully(s, len.toLong())
            }
            if (last) break
        }
        return out
    }

    private fun parseFlacPicture(b: ByteArray): Picture? {
        var p = 0
        fun u32(): Int { val v = int(b, p); p += 4; return v }
        val type = u32()
        val mimeLen = u32()
        if (mimeLen < 0 || p + mimeLen > b.size) return null
        val mime = String(b, p, mimeLen, Charsets.US_ASCII); p += mimeLen
        val descLen = u32()
        if (descLen < 0 || p + descLen > b.size) return null
        p += descLen
        p += 16 // width, height, depth, colors
        if (p + 4 > b.size) return null
        val dataLen = u32()
        if (dataLen <= 0 || p + dataLen > b.size) return null
        return Picture(type, mime, b.copyOfRange(p, p + dataLen))
    }

    // ----------------------------- I/O -----------------------------

    private fun readFully(s: InputStream, buf: ByteArray, len: Int, offset: Int = 0): Boolean {
        var read = 0
        while (read < len) {
            val n = s.read(buf, offset + read, len - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun skipFully(s: InputStream, n: Long) {
        var left = n
        while (left > 0) {
            val k = s.skip(left)
            if (k <= 0) { if (s.read() < 0) return; left-- } else left -= k
        }
    }
}
