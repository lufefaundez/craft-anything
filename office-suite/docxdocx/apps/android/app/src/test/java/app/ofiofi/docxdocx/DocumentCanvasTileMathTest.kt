package app.ofiofi.docxdocx.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentCanvasTileMathTest {
    @Test
    fun tileMemoryCacheEvictsLeastRecentlyUsedWhenOverBudget() {
        val cache = TileMemoryCache<ByteArray>(maxBytes = 2500L) { it.size.toLong() }
        val k1 = TileKey(docRevision = 1, pageIndex = 0, dpiBucket = 8, tileCol = 0, tileRow = 0, darkPage = false)
        val k2 = TileKey(docRevision = 1, pageIndex = 0, dpiBucket = 8, tileCol = 1, tileRow = 0, darkPage = false)
        val k3 = TileKey(docRevision = 1, pageIndex = 0, dpiBucket = 8, tileCol = 0, tileRow = 1, darkPage = false)

        cache.put(k1, ByteArray(1000))
        cache.put(k2, ByteArray(1000))
        // Touch k1 so k2 becomes least-recently-used
        assertNotNull(cache.get(k1))

        cache.put(k3, ByteArray(1000))

        assertNotNull(cache.get(k1))
        assertNull(cache.get(k2))
        assertNotNull(cache.get(k3))
        assertEquals(2000L, cache.totalBytes())
    }

    @Test
    fun tileCacheInvalidatesStaleRevisionsCleanly() {
        val cache = TileMemoryCache<ByteArray>(maxBytes = 10_000L) { it.size.toLong() }
        cache.put(TileKey(1, 0, 8, 0, 0, false), ByteArray(512))
        cache.put(TileKey(2, 0, 8, 0, 0, false), ByteArray(512))

        cache.invalidateStaleRevisions(activeRevision = 2)

        assertEquals(1, cache.sizeEntries())
        assertEquals(512L, cache.totalBytes())
    }

    @Test
    fun zoomClampsTo50Through400Percent() {
        assertEquals(0.50f, DocumentCanvasMath.clampZoom(0.10f), 0.0001f)
        assertEquals(4.00f, DocumentCanvasMath.clampZoom(9.50f), 0.0001f)
        assertEquals(1.00f, DocumentCanvasMath.clampZoom(Float.NaN), 0.0001f)
        assertEquals(1.75f, DocumentCanvasMath.clampZoom(1.75f), 0.0001f)
    }

    @Test
    fun virtualizedTileGridOnlySelectsVisiblePagesAndTiles() {
        // US Letter 612x792 pt at 1.0 px/pt -> 2 columns x 2 rows per 512px tile
        val pages = (0 until 5).map { PageSizeModel(it, 612f, 792f) }
        val placed = DocumentCanvasMath.layoutPages(
            pages = pages,
            scalePxPerPt = 1.0f,
            viewportWidthPx = 800f,
            pageGapPx = 20f,
            topPaddingPx = 16f,
        )

        val tilesAtTop = DocumentCanvasMath.computeVisibleTiles(
            placedPages = placed,
            scrollX = 0f,
            scrollY = 0f,
            viewportWidthPx = 800f,
            viewportHeightPx = 450f,
            tileSizePx = 512,
        )
        assertTrue(tilesAtTop.isNotEmpty())
        assertTrue(tilesAtTop.all { it.pageIndex == 0 })
        // Only row 0 intersects y in [0..450]
        assertTrue(tilesAtTop.all { it.tileRow == 0 })
        assertEquals(2, tilesAtTop.size)
        assertEquals(512, tilesAtTop[0].tileWPx)
        assertEquals(100, tilesAtTop[1].tileWPx)
    }

    @Test
    fun hitTestMapsViewportCoordinatesBackToPagePoints() {
        val placed = DocumentCanvasMath.layoutPages(
            pages = listOf(PageSizeModel(0, 612f, 792f)),
            scalePxPerPt = 2.0f,
            viewportWidthPx = 1400f,
            pageGapPx = 16f,
            topPaddingPx = 20f,
        )
        // Page left = (1400 - 1224) / 2 = 88 px, top = 20 px
        val hit = DocumentCanvasMath.hitTestPagePt(
            placedPages = placed,
            tapXPx = 288f, // 200 px inside page -> 100 pt
            tapYPx = 120f, // 100 px inside page -> 50 pt
            scrollX = 0f,
            scrollY = 0f,
            scalePxPerPt = 2.0f,
        )
        assertNotNull(hit)
        assertEquals(0, hit!!.pageIndex)
        assertEquals(100f, hit.xPt, 0.01f)
        assertEquals(50f, hit.yPt, 0.01f)
    }
}
