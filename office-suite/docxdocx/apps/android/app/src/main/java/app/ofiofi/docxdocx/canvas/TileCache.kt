package app.ofiofi.docxdocx.canvas

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * Unique cache key for a single rasterized document tile.
 */
data class TileKey(
    val docRevision: Int,
    val pageIndex: Int,
    val dpiBucket: Int,
    val tileCol: Int,
    val tileRow: Int,
    val darkPage: Boolean,
)

/**
 * Pure-Kotlin byte-bounded LRU tile cache so eviction logic is 100% deterministic
 * and testable on the JVM without Android framework mocks.
 */
class TileMemoryCache<T : Any>(
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val sizeBytesOf: (T) -> Long,
) {
    private val map = object : LinkedHashMap<TileKey, T>(64, 0.75f, true) {}
    private var currentBytes: Long = 0L

    @Synchronized
    fun get(key: TileKey): T? = map[key]

    @Synchronized
    fun put(key: TileKey, value: T) {
        val prev = map.remove(key)
        if (prev != null) {
            currentBytes -= sizeBytesOf(prev)
        }
        val entryBytes = sizeBytesOf(value).coerceAtLeast(1L)
        if (entryBytes > maxBytes) {
            return
        }
        map[key] = value
        currentBytes += entryBytes
        trimToBudget()
    }

    @Synchronized
    fun evictPage(pageIndex: Int) {
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.key.pageIndex == pageIndex) {
                currentBytes -= sizeBytesOf(entry.value)
                it.remove()
            }
        }
    }

    @Synchronized
    fun invalidateStaleRevisions(activeRevision: Int) {
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.key.docRevision != activeRevision) {
                currentBytes -= sizeBytesOf(entry.value)
                it.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        map.clear()
        currentBytes = 0L
    }

    @Synchronized
    fun sizeEntries(): Int = map.size

    @Synchronized
    fun totalBytes(): Long = currentBytes

    private fun trimToBudget() {
        val it = map.entries.iterator()
        while (currentBytes > maxBytes && it.hasNext()) {
            val oldest = it.next()
            currentBytes -= sizeBytesOf(oldest.value)
            it.remove()
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 48L * 1024L * 1024L
    }
}

/**
 * Android Bitmap-backed tile cache + RGBA8888 conversion helper.
 */
class BitmapTileCache(
    maxBytes: Long = TileMemoryCache.DEFAULT_MAX_BYTES,
) {
    private val cache = TileMemoryCache<Bitmap>(maxBytes) { bmp ->
        bmp.allocationByteCount.toLong()
    }

    fun get(key: TileKey): Bitmap? = cache.get(key)

    fun put(key: TileKey, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun evictPage(pageIndex: Int) = cache.evictPage(pageIndex)

    fun invalidateStaleRevisions(activeRevision: Int) = cache.invalidateStaleRevisions(activeRevision)

    fun clear() = cache.clear()

    fun sizeEntries(): Int = cache.sizeEntries()

    companion object {
        fun rgbaBytesToBitmap(rgba: ByteArray, width: Int, height: Int): Bitmap? {
            if (width <= 0 || height <= 0 || rgba.size != width * height * 4) return null
            val pixels = IntArray(width * height)
            var byteIndex = 0
            var pixelIndex = 0
            while (pixelIndex < pixels.size) {
                val r = rgba[byteIndex].toInt() and 0xFF
                val g = rgba[byteIndex + 1].toInt() and 0xFF
                val b = rgba[byteIndex + 2].toInt() and 0xFF
                val a = rgba[byteIndex + 3].toInt() and 0xFF
                pixels[pixelIndex] = (a shl 24) or (r shl 16) or (g shl 8) or b
                byteIndex += 4
                pixelIndex += 1
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }

        fun quantizeDpiBucket(scalePxPerPt: Float): Int =
            ((scalePxPerPt * 100f) / 12.5f).roundToInt().coerceIn(4, 64)
    }
}
