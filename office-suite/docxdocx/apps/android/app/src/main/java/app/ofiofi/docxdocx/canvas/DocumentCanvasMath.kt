package app.ofiofi.docxdocx.canvas

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure geometry model for virtualized multi-page tile rendering and pinch-to-zoom math.
 */
data class PageSizeModel(
    val pageIndex: Int,
    val widthPt: Float,
    val heightPt: Float,
)

data class PlacedPage(
    val pageIndex: Int,
    val widthPt: Float,
    val heightPt: Float,
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Int,
    val heightPx: Int,
) {
    val rightPx: Float get() = leftPx + widthPx
    val bottomPx: Float get() = topPx + heightPx
}

data class VisibleTileSpec(
    val pageIndex: Int,
    val tileCol: Int,
    val tileRow: Int,
    val tileXPx: Int,
    val tileYPx: Int,
    val tileWPx: Int,
    val tileHPx: Int,
)

data class PageHitPt(
    val pageIndex: Int,
    val xPt: Float,
    val yPt: Float,
)

enum class DocumentLayoutMode {
    PRINT_LAYOUT,
    MOBILE_REFLOW,
}

object DocumentCanvasMath {
    const val MIN_ZOOM: Float = 0.50f
    const val MAX_ZOOM: Float = 4.00f
    const val DEFAULT_TILE_PX: Int = 512

    fun clampZoom(zoom: Float): Float =
        if (zoom.isNaN()) 1.0f else zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /**
     * Computes base scale (px per PDF/layout point) so a page fits comfortably inside
     * the viewport width while leaving margin padding on Print Layout, or filling the
     * width cleanly on Mobile Reflow mode.
     */
    fun computeBaseScalePxPerPt(
        viewportWidthPx: Float,
        referencePageWidthPt: Float,
        horizontalPaddingPx: Float,
    ): Float {
        if (viewportWidthPx <= 0f || referencePageWidthPt <= 0f) return 1.0f
        val available = (viewportWidthPx - horizontalPaddingPx * 2f).coerceAtLeast(120f)
        return (available / referencePageWidthPt).coerceIn(0.4f, 4.0f)
    }

    /**
     * Lays out pages vertically centered inside [viewportWidthPx] (or shifted smoothly when zoomed > viewport).
     */
    fun layoutPages(
        pages: List<PageSizeModel>,
        scalePxPerPt: Float,
        viewportWidthPx: Float,
        pageGapPx: Float,
        topPaddingPx: Float,
    ): List<PlacedPage> {
        if (pages.isEmpty()) return emptyList()
        val result = ArrayList<PlacedPage>(pages.size)
        var cursorY = topPaddingPx
        for (page in pages) {
            val wPx = max(1, (page.widthPt * scalePxPerPt).toInt())
            val hPx = max(1, (page.heightPt * scalePxPerPt).toInt())
            val leftPx = ((viewportWidthPx - wPx) / 2f).coerceAtLeast(topPaddingPx)
            result.add(
                PlacedPage(
                    pageIndex = page.pageIndex,
                    widthPt = page.widthPt,
                    heightPt = page.heightPt,
                    leftPx = leftPx,
                    topPx = cursorY,
                    widthPx = wPx,
                    heightPx = hPx,
                ),
            )
            cursorY += hPx + pageGapPx
        }
        return result
    }

    fun totalContentHeightPx(placedPages: List<PlacedPage>, bottomPaddingPx: Float): Float {
        val last = placedPages.lastOrNull() ?: return 0f
        return last.bottomPx + bottomPaddingPx
    }

    fun maxContentWidthPx(placedPages: List<PlacedPage>, sidePaddingPx: Float): Float {
        val widest = placedPages.maxOfOrNull { it.leftPx + it.widthPx + sidePaddingPx } ?: 0f
        return widest
    }

    /**
     * Computes only the tiles intersecting the visible viewport `[scrollX, scrollX + viewportW]`
     * x `[scrollY, scrollY + viewportH]`.
     */
    fun computeVisibleTiles(
        placedPages: List<PlacedPage>,
        scrollX: Float,
        scrollY: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        tileSizePx: Int = DEFAULT_TILE_PX,
    ): List<VisibleTileSpec> {
        if (viewportWidthPx <= 0f || viewportHeightPx <= 0f || tileSizePx <= 0) return emptyList()
        val viewLeft = scrollX
        val viewTop = scrollY
        val viewRight = scrollX + viewportWidthPx
        val viewBottom = scrollY + viewportHeightPx

        val visible = mutableListOf<VisibleTileSpec>()
        for (page in placedPages) {
            if (page.bottomPx < viewTop || page.topPx > viewBottom) continue
            if (page.rightPx < viewLeft || page.leftPx > viewRight) continue

            val relLeft = max(0f, viewLeft - page.leftPx)
            val relTop = max(0f, viewTop - page.topPx)
            val relRight = min(page.widthPx.toFloat(), viewRight - page.leftPx)
            val relBottom = min(page.heightPx.toFloat(), viewBottom - page.topPx)
            if (relRight <= relLeft || relBottom <= relTop) continue

            val colStart = max(0, floor(relLeft / tileSizePx).toInt())
            val rowStart = max(0, floor(relTop / tileSizePx).toInt())
            val colEnd = max(colStart, ceil(relRight / tileSizePx).toInt() - 1)
            val rowEnd = max(rowStart, ceil(relBottom / tileSizePx).toInt() - 1)

            for (row in rowStart..rowEnd) {
                val tileY = row * tileSizePx
                if (tileY >= page.heightPx) continue
                val tileH = min(tileSizePx, page.heightPx - tileY)
                for (col in colStart..colEnd) {
                    val tileX = col * tileSizePx
                    if (tileX >= page.widthPx) continue
                    val tileW = min(tileSizePx, page.widthPx - tileX)
                    visible.add(
                        VisibleTileSpec(
                            pageIndex = page.pageIndex,
                            tileCol = col,
                            tileRow = row,
                            tileXPx = tileX,
                            tileYPx = tileY,
                            tileWPx = tileW,
                            tileHPx = tileH,
                        ),
                    )
                }
            }
        }
        return visible
    }

    /**
     * Converts a viewport tap coordinate `(tapX, tapY)` into `(pageIndex, xPt, yPt)` in layout points.
     */
    fun hitTestPagePt(
        placedPages: List<PlacedPage>,
        tapXPx: Float,
        tapYPx: Float,
        scrollX: Float,
        scrollY: Float,
        scalePxPerPt: Float,
    ): PageHitPt? {
        if (scalePxPerPt <= 0f) return null
        val contentX = tapXPx + scrollX
        val contentY = tapYPx + scrollY
        for (page in placedPages) {
            if (contentX in page.leftPx..page.rightPx && contentY in page.topPx..page.bottomPx) {
                val xPt = ((contentX - page.leftPx) / scalePxPerPt).coerceIn(0f, page.widthPt)
                val yPt = ((contentY - page.topPx) / scalePxPerPt).coerceIn(0f, page.heightPt)
                return PageHitPt(page.pageIndex, xPt, yPt)
            }
        }
        return null
    }
}
