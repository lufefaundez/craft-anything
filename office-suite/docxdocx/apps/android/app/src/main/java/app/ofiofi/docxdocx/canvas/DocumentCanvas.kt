package app.ofiofi.docxdocx.canvas

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ofiofi.docxdocx.input.SelectionOverlay
import app.ofiofi.docxdocx.ui.theme.OfficeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import uniffi.word_mobile_ffi.MobileDocumentSession
import uniffi.word_mobile_ffi.SelectionGeometry
import uniffi.word_mobile_ffi.TileRequest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Virtualized tile-based document canvas with pinch-to-zoom (50%-400%), inertial pan/scroll,
 * page drop shadow, selection highlight + blinking caret overlay, and Print vs Mobile Reflow mode.
 */
@Composable
fun DocumentCanvas(
    session: MobileDocumentSession,
    docRevision: Int,
    selectionGeometry: SelectionGeometry,
    isDark: Boolean,
    zoom: Float,
    layoutMode: DocumentLayoutMode,
    onZoomChange: (Float) -> Unit,
    onToggleLayoutMode: () -> Unit,
    onTapPage: (PageHitPt, tapCount: Int) -> Unit,
    onSelectionUpdated: (SelectionGeometry) -> Unit = {},
    onQuickCommand: (commandId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val surfaces = if (isDark) OfficeTokens.DarkSurfaces else OfficeTokens.LightSurfaces
    val accent = if (isDark) OfficeTokens.AppAccents.Word.accentDark else OfficeTokens.AppAccents.Word.accentLight
    val tileCache = remember { BitmapTileCache() }
    val density = LocalDensity.current

    var scrollX by remember { mutableFloatStateOf(0f) }
    var scrollY by remember { mutableFloatStateOf(0f) }
    var caretVisible by remember { mutableStateOf(true) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var consecutiveTaps by remember { mutableStateOf(0) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(docRevision) {
        tileCache.invalidateStaleRevisions(docRevision)
    }

    LaunchedEffect(selectionGeometry) {
        caretVisible = true
        while (isActive) {
            delay(530L)
            caretVisible = !caretVisible
        }
    }

    val pageModels = remember(docRevision, layoutMode) {
        val count = session.pageCount().toInt().coerceAtLeast(1)
        (0 until count).map { idx ->
            val sizePt = session.pageSizePt(idx.toUInt())
            PageSizeModel(idx, sizePt.widthPt, sizePt.heightPt)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(surfaces.canvas),
    ) {
        val viewportW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val paddingPx = with(density) { 16.dp.toPx() }
        val pageGapPx = with(density) { 18.dp.toPx() }
        val refWidthPt = pageModels.firstOrNull()?.widthPt ?: 612f

        val baseScale = DocumentCanvasMath.computeBaseScalePxPerPt(
            viewportWidthPx = viewportW,
            referencePageWidthPt = refWidthPt,
            horizontalPaddingPx = if (layoutMode == DocumentLayoutMode.MOBILE_REFLOW) 8f else paddingPx,
        )
        val clampedZoom = DocumentCanvasMath.clampZoom(zoom)
        val scalePxPerPt = baseScale * clampedZoom
        val dpiBucket = BitmapTileCache.quantizeDpiBucket(scalePxPerPt)

        val placedPages = remember(pageModels, scalePxPerPt, viewportW, layoutMode) {
            DocumentCanvasMath.layoutPages(
                pages = pageModels,
                scalePxPerPt = scalePxPerPt,
                viewportWidthPx = viewportW,
                pageGapPx = pageGapPx,
                topPaddingPx = paddingPx,
            )
        }

        val maxScrollY = (DocumentCanvasMath.totalContentHeightPx(placedPages, paddingPx * 2f) - viewportH)
            .coerceAtLeast(0f)
        val maxScrollX = (DocumentCanvasMath.maxContentWidthPx(placedPages, paddingPx) - viewportW)
            .coerceAtLeast(0f)

        scrollX = scrollX.coerceIn(0f, maxScrollX)
        scrollY = scrollY.coerceIn(0f, maxScrollY)

        val visibleTiles = remember(placedPages, scrollX, scrollY, viewportW, viewportH, layoutMode) {
            DocumentCanvasMath.computeVisibleTiles(
                placedPages = placedPages,
                scrollX = scrollX,
                scrollY = scrollY,
                viewportWidthPx = viewportW,
                viewportHeightPx = viewportH,
            )
        }

        val darkPage = false
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(placedPages, scalePxPerPt) {
                    detectTapGestures(
                        onTap = { pos ->
                            val now = System.currentTimeMillis()
                            val nearPrev = (pos - lastTapPos).getDistance() < 42f
                            consecutiveTaps = if (now - lastTapTimeMs < 380L && nearPrev) {
                                (consecutiveTaps % 3) + 1
                            } else {
                                1
                            }
                            lastTapTimeMs = now
                            lastTapPos = pos

                            DocumentCanvasMath.hitTestPagePt(
                                placedPages = placedPages,
                                tapXPx = pos.x,
                                tapYPx = pos.y,
                                scrollX = scrollX,
                                scrollY = scrollY,
                                scalePxPerPt = scalePxPerPt,
                            )?.let { hit ->
                                onTapPage(hit, consecutiveTaps)
                            }
                        },
                    )
                }
                .pointerInput(maxScrollX, maxScrollY) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (abs(zoomChange - 1f) > 0.005f) {
                                onZoomChange(DocumentCanvasMath.clampZoom(clampedZoom * zoomChange))
                            }
                            if (pan != Offset.Zero) {
                                scrollX = (scrollX - pan.x).coerceIn(0f, maxScrollX)
                                scrollY = (scrollY - pan.y).coerceIn(0f, maxScrollY)
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = surfaces.page.toArgb()
                setShadowLayer(
                    with(density) { 8.dp.toPx() },
                    0f,
                    with(density) { 3.dp.toPx() },
                    surfaces.pageShadow.toArgb(),
                )
            }

            // 1. Page shadows, crisp paper backing, and subtle border
            for (page in placedPages) {
                if (page.bottomPx < scrollY || page.topPx > scrollY + viewportH) continue
                val drawLeft = page.leftPx - scrollX
                val drawTop = page.topPx - scrollY
                drawContext.canvas.nativeCanvas.drawRect(
                    RectF(drawLeft, drawTop, drawLeft + page.widthPx, drawTop + page.heightPx),
                    shadowPaint,
                )
                drawRect(
                    color = surfaces.ribbonBorder,
                    topLeft = Offset(drawLeft, drawTop),
                    size = Size(page.widthPx.toFloat(), page.heightPx.toFloat()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                )
            }

            // 2. Rasterized tiles (cached in LRU BitmapTileCache)
            for (tile in visibleTiles) {
                val page = placedPages.getOrNull(tile.pageIndex) ?: continue
                val key = TileKey(
                    docRevision = docRevision,
                    pageIndex = tile.pageIndex,
                    dpiBucket = dpiBucket,
                    tileCol = tile.tileCol,
                    tileRow = tile.tileRow,
                    darkPage = darkPage,
                )
                val bmp = tileCache.get(key) ?: run {
                    val rgba = session.renderTile(
                        TileRequest(
                            pageIndex = tile.pageIndex.toUInt(),
                            dpiScale = scalePxPerPt,
                            tileXPx = tile.tileXPx.toUInt(),
                            tileYPx = tile.tileYPx.toUInt(),
                            tileWPx = tile.tileWPx.toUInt(),
                            tileHPx = tile.tileHPx.toUInt(),
                            darkPage = darkPage,
                        ),
                    )
                    BitmapTileCache.rgbaBytesToBitmap(rgba, tile.tileWPx, tile.tileHPx)?.also {
                        tileCache.put(key, it)
                    }
                } ?: continue

                val dstLeft = (page.leftPx + tile.tileXPx - scrollX).roundToInt()
                val dstTop = (page.topPx + tile.tileYPx - scrollY).roundToInt()
                drawImage(
                    image = bmp.asImageBitmap(),
                    dstOffset = IntOffset(dstLeft, dstTop),
                    dstSize = IntSize(tile.tileWPx, tile.tileHPx),
                )
            }

            // 3. Selection highlight rectangles
            for (selRect in selectionGeometry.selectionRects) {
                val page = placedPages.getOrNull(selRect.pageIndex.toInt()) ?: continue
                drawRect(
                    color = surfaces.selection,
                    topLeft = Offset(
                        x = page.leftPx + selRect.xPt * scalePxPerPt - scrollX,
                        y = page.topPx + selRect.yPt * scalePxPerPt - scrollY,
                    ),
                    size = Size(
                        width = selRect.widthPt * scalePxPerPt,
                        height = selRect.heightPt * scalePxPerPt,
                    ),
                )
            }

            // 4. Blinking text caret
            val caret = selectionGeometry.caret
            if (caret != null && selectionGeometry.isCollapsed && caretVisible) {
                val page = placedPages.getOrNull(caret.pageIndex.toInt())
                if (page != null) {
                    val cx = page.leftPx + caret.xPt * scalePxPerPt - scrollX
                    val cy = page.topPx + caret.topPt * scalePxPerPt - scrollY
                    val ch = (caret.heightPt * scalePxPerPt).coerceAtLeast(14f)
                    drawLine(
                        color = accent,
                        start = Offset(cx, cy),
                        end = Offset(cx, cy + ch),
                        strokeWidth = with(density) { 2.dp.toPx() },
                    )
                }
            }
        }

        // Interactive selection handles, magnifier loupe & floating contextual toolbar
        SelectionOverlay(
            session = session,
            placedPages = placedPages,
            selectionGeometry = selectionGeometry,
            scrollX = scrollX,
            scrollY = scrollY,
            scalePxPerPt = scalePxPerPt,
            isDark = isDark,
            onSelectionUpdated = onSelectionUpdated,
            onQuickCommand = onQuickCommand,
            modifier = Modifier.fillMaxSize(),
        )

        // Floating Zoom + Mobile Reflow / Print Layout switcher pill (bottom-end)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.pill)),
            shape = RoundedCornerShape(OfficeTokens.Radii.pill),
            color = surfaces.ribbonCard.copy(alpha = 0.94f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (layoutMode == DocumentLayoutMode.MOBILE_REFLOW) "Vista Móvil" else "Impresión",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onToggleLayoutMode() },
                )
                Text(
                    text = "${(clampedZoom * 100f).roundToInt()} %",
                    color = surfaces.textDim,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onZoomChange(1.0f) },
                )
            }
        }
    }
}
