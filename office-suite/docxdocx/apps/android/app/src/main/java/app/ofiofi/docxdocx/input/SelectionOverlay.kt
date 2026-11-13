package app.ofiofi.docxdocx.input

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ofiofi.docxdocx.canvas.BitmapTileCache
import app.ofiofi.docxdocx.canvas.PlacedPage
import app.ofiofi.docxdocx.ui.theme.OfficeTokens
import uniffi.word_mobile_ffi.MobileDocumentSession
import uniffi.word_mobile_ffi.SelectionGeometry
import uniffi.word_mobile_ffi.TileRequest
import kotlin.math.roundToInt

/**
 * Interactive touch selection handles (anchor + focus), magnified loupe popup during drag,
 * and floating quick-action bar (Cortar, Copiar, Pegar, Negrita, Comentario).
 */
@Composable
fun SelectionOverlay(
    session: MobileDocumentSession,
    placedPages: List<PlacedPage>,
    selectionGeometry: SelectionGeometry,
    scrollX: Float,
    scrollY: Float,
    scalePxPerPt: Float,
    isDark: Boolean,
    onSelectionUpdated: (SelectionGeometry) -> Unit,
    onQuickCommand: (commandId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaces = if (isDark) OfficeTokens.DarkSurfaces else OfficeTokens.LightSurfaces
    val accent = if (isDark) OfficeTokens.AppAccents.Word.accentDark else OfficeTokens.AppAccents.Word.accentLight
    val density = LocalDensity.current

    var activeDragHandle by remember { mutableStateOf<DragHandleState?>(null) }

    val rects = selectionGeometry.selectionRects
    if (rects.isEmpty() || placedPages.isEmpty() || scalePxPerPt <= 0f) {
        return
    }

    val firstRect = rects.first()
    val lastRect = rects.last()
    val startPage = placedPages.getOrNull(firstRect.pageIndex.toInt()) ?: return
    val endPage = placedPages.getOrNull(lastRect.pageIndex.toInt()) ?: return

    val startHandleX = startPage.leftPx + firstRect.xPt * scalePxPerPt - scrollX
    val startHandleY = startPage.topPx + (firstRect.yPt + firstRect.heightPt) * scalePxPerPt - scrollY

    val endHandleX = endPage.leftPx + (lastRect.xPt + lastRect.widthPt) * scalePxPerPt - scrollX
    val endHandleY = endPage.topPx + (lastRect.yPt + lastRect.heightPt) * scalePxPerPt - scrollY

    val handleHitPx = with(density) { 44.dp.toPx() }
    val handleRadiusPx = with(density) { 8.dp.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Start (Anchor) Teardrop Handle
        DragHandleTarget(
            centerX = startHandleX,
            centerY = startHandleY + handleRadiusPx * 1.6f,
            hitSizePx = handleHitPx,
            radiusPx = handleRadiusPx,
            accent = accent,
            isStart = true,
            onDragDelta = { dx, dy ->
                val pagePtX = ((startHandleX + dx + scrollX - startPage.leftPx) / scalePxPerPt)
                    .coerceIn(0f, startPage.widthPt)
                val pagePtY = ((startHandleY + dy - firstRect.heightPt * scalePxPerPt * 0.5f + scrollY - startPage.topPx) / scalePxPerPt)
                    .coerceIn(0f, startPage.heightPt)
                val updated = session.dragSelectionHandle(
                    isAnchor = true,
                    pageIndex = startPage.pageIndex.toUInt(),
                    xPt = pagePtX,
                    yPt = pagePtY,
                )
                onSelectionUpdated(updated)
                activeDragHandle = DragHandleState(
                    pageIndex = startPage.pageIndex,
                    xPt = pagePtX,
                    yPt = pagePtY,
                    screenX = startHandleX + dx,
                    screenY = startHandleY + dy,
                )
            },
            onDragEnd = { activeDragHandle = null },
        )

        // End (Focus) Teardrop Handle
        DragHandleTarget(
            centerX = endHandleX,
            centerY = endHandleY + handleRadiusPx * 1.6f,
            hitSizePx = handleHitPx,
            radiusPx = handleRadiusPx,
            accent = accent,
            isStart = false,
            onDragDelta = { dx, dy ->
                val pagePtX = ((endHandleX + dx + scrollX - endPage.leftPx) / scalePxPerPt)
                    .coerceIn(0f, endPage.widthPt)
                val pagePtY = ((endHandleY + dy - lastRect.heightPt * scalePxPerPt * 0.5f + scrollY - endPage.topPx) / scalePxPerPt)
                    .coerceIn(0f, endPage.heightPt)
                val updated = session.dragSelectionHandle(
                    isAnchor = false,
                    pageIndex = endPage.pageIndex.toUInt(),
                    xPt = pagePtX,
                    yPt = pagePtY,
                )
                onSelectionUpdated(updated)
                activeDragHandle = DragHandleState(
                    pageIndex = endPage.pageIndex,
                    xPt = pagePtX,
                    yPt = pagePtY,
                    screenX = endHandleX + dx,
                    screenY = endHandleY + dy,
                )
            },
            onDragEnd = { activeDragHandle = null },
        )

        // Magnifier Loupe when dragging either handle
        val drag = activeDragHandle
        if (drag != null) {
            val loupeBmp = remember(drag) {
                renderMagnifierCrop(
                    session = session,
                    pageIndex = drag.pageIndex,
                    centerXPt = drag.xPt,
                    centerYPt = drag.yPt,
                    zoomScale = (scalePxPerPt * 1.65f).coerceIn(1.2f, 5.0f),
                )
            }
            if (loupeBmp != null) {
                val loupeW = with(density) { 120.dp.roundToPx() }
                val loupeH = with(density) { 54.dp.roundToPx() }
                val xOff = (drag.screenX - loupeW / 2f).roundToInt()
                    .coerceIn(8, (constraints.maxWidth - loupeW - 8).coerceAtLeast(8))
                val yOff = (drag.screenY - loupeH - with(density) { 58.dp.toPx() }).roundToInt()
                    .coerceAtLeast(8)
                Surface(
                    modifier = Modifier
                        .offset { IntOffset(xOff, yOff) }
                        .size(width = 120.dp, height = 54.dp)
                        .border(1.5.dp, accent, RoundedCornerShape(OfficeTokens.Radii.pill))
                        .clip(RoundedCornerShape(OfficeTokens.Radii.pill)),
                    shape = RoundedCornerShape(OfficeTokens.Radii.pill),
                    color = surfaces.ribbonCard,
                    shadowElevation = 6.dp,
                ) {
                    Image(
                        bitmap = loupeBmp.asImageBitmap(),
                        contentDescription = "Lupa de selección",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            // Floating Quick Menu above selection when user lifts finger
            val menuTopPx = (startPage.topPx + firstRect.yPt * scalePxPerPt - scrollY - with(density) { 50.dp.toPx() })
                .roundToInt()
                .coerceAtLeast(8)
            val menuLeftPx = startHandleX.roundToInt()
                .coerceIn(12, (constraints.maxWidth - with(density) { 300.dp.roundToPx() }).coerceAtLeast(12))
            Surface(
                modifier = Modifier
                    .offset { IntOffset(menuLeftPx, menuTopPx) }
                    .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.ribbonCard)),
                shape = RoundedCornerShape(OfficeTokens.Radii.ribbonCard),
                color = surfaces.ribbonCard,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuickActionChip("Cortar", surfaces.text) { onQuickCommand("edit.cut") }
                    QuickActionChip("Copiar", surfaces.text) { onQuickCommand("edit.copy") }
                    QuickActionChip("Pegar", surfaces.text) { onQuickCommand("edit.paste") }
                    QuickActionChip("N", accent, bold = true) { onQuickCommand("format.bold") }
                    QuickActionChip("Comentario", accent) { onQuickCommand("review.newComment") }
                }
            }
        }
    }
}

private data class DragHandleState(
    val pageIndex: Int,
    val xPt: Float,
    val yPt: Float,
    val screenX: Float,
    val screenY: Float,
)

@Composable
private fun DragHandleTarget(
    centerX: Float,
    centerY: Float,
    hitSizePx: Float,
    radiusPx: Float,
    accent: Color,
    isStart: Boolean,
    onDragDelta: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val halfHit = (hitSizePx / 2f).roundToInt()
    Box(
        modifier = Modifier
            .offset { IntOffset(centerX.roundToInt() - halfHit, centerY.roundToInt() - halfHit) }
            .size(44.dp)
            .pointerInput(isStart) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = accent,
                radius = radiusPx,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    textColor: Color,
    bold: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

private fun renderMagnifierCrop(
    session: MobileDocumentSession,
    pageIndex: Int,
    centerXPt: Float,
    centerYPt: Float,
    zoomScale: Float,
): Bitmap? {
    val tileW = 240
    val tileH = 108
    val tileX = ((centerXPt * zoomScale) - tileW / 2f).roundToInt().coerceAtLeast(0)
    val tileY = ((centerYPt * zoomScale) - tileH / 2f).roundToInt().coerceAtLeast(0)
    val rgba = runCatching {
        session.renderTile(
            TileRequest(
                pageIndex = pageIndex.toUInt(),
                dpiScale = zoomScale,
                tileXPx = tileX.toUInt(),
                tileYPx = tileY.toUInt(),
                tileWPx = tileW.toUInt(),
                tileHPx = tileH.toUInt(),
                darkPage = false,
            ),
        )
    }.getOrNull() ?: return null
    return BitmapTileCache.rgbaBytesToBitmap(rgba, tileW, tileH)
}
