package app.ofiofi.docxdocx.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.ofiofi.docxdocx.ui.theme.OfficeTokens
import java.util.Locale
import uniffi.word_mobile_ffi.RulerMetrics
import kotlin.math.abs

data class RulerTick(
    val positionPt: Float,
    val label: String?,
    val isMajor: Boolean,
)

data class RulerViewportMetrics(
    val pageLeftPx: Float = 0f,
    val pageWidthPx: Float = 0f,
    val pageTopPx: Float = 0f,
    val pageHeightPx: Float = 0f,
    val scalePxPerPt: Float = 1f,
)

private enum class HorizontalDragTarget {
    LEFT_MARGIN,
    RIGHT_MARGIN,
    FIRST_LINE_INDENT,
    LEFT_INDENT,
    RIGHT_INDENT,
}

private enum class VerticalDragTarget {
    TOP_MARGIN,
    BOTTOM_MARGIN,
}

/**
 * Pure-Kotlin ruler scale math (centimeters at 28.3465 pt/cm for Word es-CL default)
 * and point clamping helpers for interactive margin/indent handles.
 */
object RulerMath {
    const val POINTS_PER_CM: Float = 28.3465f

    fun buildTicks(
        totalPoints: Float,
        marginStartPt: Float = 72f,
    ): List<RulerTick> {
        if (totalPoints <= 0f) return emptyList()
        val halfCmPt = POINTS_PER_CM / 2f
        val maxCmSteps = ((totalPoints / halfCmPt).toInt()).coerceAtMost(120)
        return buildList {
            for (step in 0..maxCmSteps) {
                val pt = step * halfCmPt
                if (pt > totalPoints) break
                val isMajor = step % 2 == 0
                val cmRelative = kotlin.math.round((pt - marginStartPt) / POINTS_PER_CM).toInt()
                val label = if (isMajor && cmRelative != 0) kotlin.math.abs(cmRelative).toString() else null
                add(RulerTick(positionPt = pt, label = label, isMajor = isMajor))
            }
        }
    }

    fun buildMarginsPayload(
        topPt: Float? = null,
        bottomPt: Float? = null,
        leftPt: Float? = null,
        rightPt: Float? = null,
    ): String {
        val parts = buildList {
            topPt?.let { add(String.format(Locale.US, """"top":%.1f""", it.coerceIn(14f, 280f))) }
            bottomPt?.let { add(String.format(Locale.US, """"bottom":%.1f""", it.coerceIn(14f, 280f))) }
            leftPt?.let { add(String.format(Locale.US, """"left":%.1f""", it.coerceIn(14f, 280f))) }
            rightPt?.let { add(String.format(Locale.US, """"right":%.1f""", it.coerceIn(14f, 280f))) }
        }
        return "{${parts.joinToString(",")}}"
    }

    fun buildIndentsPayload(leftPt: Float, rightPt: Float, firstLinePt: Float): String =
        String.format(
            Locale.US,
            """"{"left":%.1f,"right":%.1f,"firstLine":%.1f}"""".removeSurrounding("\""),
            leftPt.coerceIn(0f, 360f),
            rightPt.coerceIn(0f, 360f),
            firstLinePt.coerceIn(-180f, 240f),
        )
}

@Composable
fun HorizontalPageRuler(
    rulerMetrics: RulerMetrics,
    viewportMetrics: RulerViewportMetrics,
    zoom: Float = 1f,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onCommitMargins: (leftPt: Float?, rightPt: Float?) -> Unit = { _, _ -> },
    onCommitIndents: (leftPt: Float, rightPt: Float, firstLinePt: Float) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val pageWidthPt = rulerMetrics.widthPt.coerceAtLeast(200f)
    val ticks = remember(pageWidthPt, rulerMetrics.marginLeftPt) {
        RulerMath.buildTicks(totalPoints = pageWidthPt, marginStartPt = rulerMetrics.marginLeftPt)
    }

    var dragTarget by remember { mutableStateOf<HorizontalDragTarget?>(null) }
    var draftLeftMarginPt by remember(rulerMetrics.marginLeftPt) { mutableStateOf(rulerMetrics.marginLeftPt) }
    var draftRightMarginPt by remember(rulerMetrics.marginRightPt) { mutableStateOf(rulerMetrics.marginRightPt) }
    var draftParaLeftPt by remember(rulerMetrics.paraLeftPt) { mutableStateOf(rulerMetrics.paraLeftPt) }
    var draftParaRightPt by remember(rulerMetrics.paraRightPt) { mutableStateOf(rulerMetrics.paraRightPt) }
    var draftFirstLinePt by remember(rulerMetrics.paraFirstLinePt) { mutableStateOf(rulerMetrics.paraFirstLinePt) }

    val lineColor = surfaces.textDim
    val textColor = surfaces.text

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(surfaces.panel)
            .pointerInput(rulerMetrics, viewportMetrics, zoom) {
                detectDragGestures(
                    onDragStart = { start ->
                        val pxPerPt = if (viewportMetrics.pageWidthPx > 10f && viewportMetrics.scalePxPerPt > 0f) {
                            viewportMetrics.scalePxPerPt
                        } else {
                            (size.width / pageWidthPt).coerceAtMost(2.5f * zoom.coerceAtLeast(0.5f))
                        }
                        val rulerWidthPx = pageWidthPt * pxPerPt
                        val startX = if (viewportMetrics.pageWidthPx > 10f) {
                            viewportMetrics.pageLeftPx
                        } else {
                            ((size.width - rulerWidthPx) / 2f).coerceAtLeast(0f)
                        }
                        val leftMarginX = startX + draftLeftMarginPt * pxPerPt
                        val rightMarginX = startX + (pageWidthPt - draftRightMarginPt) * pxPerPt
                        val firstLineX = startX + (draftLeftMarginPt + draftParaLeftPt + draftFirstLinePt) * pxPerPt
                        val leftIndentX = startX + (draftLeftMarginPt + draftParaLeftPt) * pxPerPt
                        val rightIndentX = startX + (pageWidthPt - draftRightMarginPt - draftParaRightPt) * pxPerPt

                        val slop = 18f
                        dragTarget = when {
                            start.y < size.height * 0.45f && abs(start.x - firstLineX) <= slop -> HorizontalDragTarget.FIRST_LINE_INDENT
                            start.y >= size.height * 0.45f && abs(start.x - leftIndentX) <= slop -> HorizontalDragTarget.LEFT_INDENT
                            abs(start.x - rightIndentX) <= slop -> HorizontalDragTarget.RIGHT_INDENT
                            abs(start.x - leftMarginX) <= slop -> HorizontalDragTarget.LEFT_MARGIN
                            abs(start.x - rightMarginX) <= slop -> HorizontalDragTarget.RIGHT_MARGIN
                            else -> null
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val target = dragTarget ?: return@detectDragGestures
                        change.consume()
                        val pxPerPt = if (viewportMetrics.pageWidthPx > 10f && viewportMetrics.scalePxPerPt > 0f) {
                            viewportMetrics.scalePxPerPt
                        } else {
                            (size.width / pageWidthPt).coerceAtMost(2.5f * zoom.coerceAtLeast(0.5f))
                        }
                        val deltaPt = dragAmount.x / pxPerPt.coerceAtLeast(0.1f)
                        when (target) {
                            HorizontalDragTarget.LEFT_MARGIN -> {
                                draftLeftMarginPt = (draftLeftMarginPt + deltaPt).coerceIn(14f, pageWidthPt * 0.4f)
                            }
                            HorizontalDragTarget.RIGHT_MARGIN -> {
                                draftRightMarginPt = (draftRightMarginPt - deltaPt).coerceIn(14f, pageWidthPt * 0.4f)
                            }
                            HorizontalDragTarget.FIRST_LINE_INDENT -> {
                                val next = (draftFirstLinePt + deltaPt).coerceIn(-draftParaLeftPt, pageWidthPt * 0.45f)
                                draftFirstLinePt = next
                            }
                            HorizontalDragTarget.LEFT_INDENT -> {
                                draftParaLeftPt = (draftParaLeftPt + deltaPt).coerceIn(0f, pageWidthPt * 0.45f)
                            }
                            HorizontalDragTarget.RIGHT_INDENT -> {
                                draftParaRightPt = (draftParaRightPt - deltaPt).coerceIn(0f, pageWidthPt * 0.45f)
                            }
                        }
                    },
                    onDragEnd = {
                        when (dragTarget) {
                            HorizontalDragTarget.LEFT_MARGIN -> onCommitMargins(draftLeftMarginPt, null)
                            HorizontalDragTarget.RIGHT_MARGIN -> onCommitMargins(null, draftRightMarginPt)
                            HorizontalDragTarget.FIRST_LINE_INDENT,
                            HorizontalDragTarget.LEFT_INDENT,
                            HorizontalDragTarget.RIGHT_INDENT,
                            -> onCommitIndents(draftParaLeftPt, draftParaRightPt, draftFirstLinePt)
                            null -> Unit
                        }
                        dragTarget = null
                    },
                    onDragCancel = {
                        dragTarget = null
                    },
                )
            },
    ) {
        val pxPerPt = if (viewportMetrics.pageWidthPx > 10f && viewportMetrics.scalePxPerPt > 0f) {
            viewportMetrics.scalePxPerPt
        } else {
            (size.width / pageWidthPt).coerceAtMost(2.5f * zoom.coerceAtLeast(0.5f))
        }
        val rulerWidthPx = pageWidthPt * pxPerPt
        val startX = if (viewportMetrics.pageWidthPx > 10f) {
            viewportMetrics.pageLeftPx
        } else {
            ((size.width - rulerWidthPx) / 2f).coerceAtLeast(0f)
        }

        val leftMarginPx = (draftLeftMarginPt * pxPerPt).coerceAtMost(rulerWidthPx / 2.2f)
        val rightMarginPx = (draftRightMarginPt * pxPerPt).coerceAtMost(rulerWidthPx / 2.2f)

        // Editable printable white track + grey margin tracks
        drawRect(
            color = surfaces.page,
            topLeft = Offset(startX, 2f),
            size = Size(rulerWidthPx, size.height - 4f),
        )
        drawRect(
            color = Color.Gray.copy(alpha = 0.22f),
            topLeft = Offset(startX, 2f),
            size = Size(leftMarginPx, size.height - 4f),
        )
        drawRect(
            color = Color.Gray.copy(alpha = 0.22f),
            topLeft = Offset(startX + rulerWidthPx - rightMarginPx, 2f),
            size = Size(rightMarginPx, size.height - 4f),
        )

        val paint = android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 20f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        ticks.forEach { tick ->
            val x = startX + tick.positionPt * pxPerPt
            val tickTop = if (tick.isMajor) size.height * 0.56f else size.height * 0.74f
            drawLine(
                color = lineColor,
                start = Offset(x, tickTop),
                end = Offset(x, size.height - 2f),
                strokeWidth = if (tick.isMajor) 1.5f else 1f,
            )
            tick.label?.let { lbl ->
                drawContext.canvas.nativeCanvas.drawText(lbl, x, size.height * 0.48f, paint)
            }
        }

        // First line indent (top downward triangle)
        val firstLineX = startX + (draftLeftMarginPt + draftFirstLinePt + draftParaLeftPt) * pxPerPt
        val firstLinePath = Path().apply {
            moveTo(firstLineX - 6f, 2f)
            lineTo(firstLineX + 6f, 2f)
            lineTo(firstLineX, 10f)
            close()
        }
        drawPath(firstLinePath, color = accent)

        // Left indent (bottom upward triangle + base box)
        val leftIndentX = startX + (draftLeftMarginPt + draftParaLeftPt) * pxPerPt
        val leftIndentPath = Path().apply {
            moveTo(leftIndentX, size.height - 10f)
            lineTo(leftIndentX - 6f, size.height - 3f)
            lineTo(leftIndentX + 6f, size.height - 3f)
            close()
        }
        drawPath(leftIndentPath, color = accent)

        // Right indent (bottom upward triangle)
        val rightIndentX = startX + (pageWidthPt - draftRightMarginPt - draftParaRightPt) * pxPerPt
        val rightIndentPath = Path().apply {
            moveTo(rightIndentX, size.height - 10f)
            lineTo(rightIndentX - 6f, size.height - 3f)
            lineTo(rightIndentX + 6f, size.height - 3f)
            close()
        }
        drawPath(rightIndentPath, color = accent)
    }
}

@Composable
fun VerticalPageRuler(
    rulerMetrics: RulerMetrics,
    viewportMetrics: RulerViewportMetrics,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onCommitMargins: (topPt: Float?, bottomPt: Float?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val pageHeightPt = rulerMetrics.heightPt.coerceAtLeast(300f)
    val ticks = remember(pageHeightPt, rulerMetrics.marginTopPt) {
        RulerMath.buildTicks(totalPoints = pageHeightPt, marginStartPt = rulerMetrics.marginTopPt)
    }

    var dragTarget by remember { mutableStateOf<VerticalDragTarget?>(null) }
    var draftTopMarginPt by remember(rulerMetrics.marginTopPt) { mutableStateOf(rulerMetrics.marginTopPt) }
    var draftBottomMarginPt by remember(rulerMetrics.marginBottomPt) { mutableStateOf(rulerMetrics.marginBottomPt) }

    val lineColor = surfaces.textDim

    Canvas(
        modifier = modifier
            .width(22.dp)
            .fillMaxHeight()
            .background(surfaces.panel)
            .pointerInput(rulerMetrics, viewportMetrics) {
                detectDragGestures(
                    onDragStart = { start ->
                        val pxPerPt = if (viewportMetrics.pageHeightPx > 10f && viewportMetrics.scalePxPerPt > 0f) {
                            viewportMetrics.scalePxPerPt
                        } else {
                            size.height / pageHeightPt
                        }
                        val startY = if (viewportMetrics.pageHeightPx > 10f) viewportMetrics.pageTopPx else 0f
                        val topBoundaryY = startY + draftTopMarginPt * pxPerPt
                        val bottomBoundaryY = startY + (pageHeightPt - draftBottomMarginPt) * pxPerPt
                        val slop = 18f
                        dragTarget = when {
                            abs(start.y - topBoundaryY) <= slop -> VerticalDragTarget.TOP_MARGIN
                            abs(start.y - bottomBoundaryY) <= slop -> VerticalDragTarget.BOTTOM_MARGIN
                            else -> null
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val target = dragTarget ?: return@detectDragGestures
                        change.consume()
                        val pxPerPt = if (viewportMetrics.pageHeightPx > 10f && viewportMetrics.scalePxPerPt > 0f) {
                            viewportMetrics.scalePxPerPt
                        } else {
                            size.height / pageHeightPt
                        }
                        val deltaPt = dragAmount.y / pxPerPt.coerceAtLeast(0.1f)
                        when (target) {
                            VerticalDragTarget.TOP_MARGIN -> {
                                draftTopMarginPt = (draftTopMarginPt + deltaPt).coerceIn(14f, pageHeightPt * 0.4f)
                            }
                            VerticalDragTarget.BOTTOM_MARGIN -> {
                                draftBottomMarginPt = (draftBottomMarginPt - deltaPt).coerceIn(14f, pageHeightPt * 0.4f)
                            }
                        }
                    },
                    onDragEnd = {
                        when (dragTarget) {
                            VerticalDragTarget.TOP_MARGIN -> onCommitMargins(draftTopMarginPt, null)
                            VerticalDragTarget.BOTTOM_MARGIN -> onCommitMargins(null, draftBottomMarginPt)
                            null -> Unit
                        }
                        dragTarget = null
                    },
                    onDragCancel = { dragTarget = null },
                )
            },
    ) {
        val pxPerPt = if (viewportMetrics.pageHeightPx > 10f && viewportMetrics.scalePxPerPt > 0f) {
            viewportMetrics.scalePxPerPt
        } else {
            size.height / pageHeightPt
        }
        val rulerHeightPx = pageHeightPt * pxPerPt
        val startY = if (viewportMetrics.pageHeightPx > 10f) viewportMetrics.pageTopPx else 0f

        val topMarginPx = (draftTopMarginPt * pxPerPt).coerceAtMost(rulerHeightPx / 2.2f)
        val bottomMarginPx = (draftBottomMarginPt * pxPerPt).coerceAtMost(rulerHeightPx / 2.2f)

        drawRect(
            color = surfaces.page,
            topLeft = Offset(2f, startY),
            size = Size(size.width - 4f, rulerHeightPx),
        )
        drawRect(
            color = Color.Gray.copy(alpha = 0.22f),
            topLeft = Offset(2f, startY),
            size = Size(size.width - 4f, topMarginPx),
        )
        drawRect(
            color = Color.Gray.copy(alpha = 0.22f),
            topLeft = Offset(2f, startY + rulerHeightPx - bottomMarginPx),
            size = Size(size.width - 4f, bottomMarginPx),
        )

        // Subtle accent handles at top & bottom margin edges
        drawLine(
            color = accent,
            start = Offset(2f, startY + topMarginPx),
            end = Offset(size.width - 2f, startY + topMarginPx),
            strokeWidth = 2f,
        )
        drawLine(
            color = accent,
            start = Offset(2f, startY + rulerHeightPx - bottomMarginPx),
            end = Offset(size.width - 2f, startY + rulerHeightPx - bottomMarginPx),
            strokeWidth = 2f,
        )

        ticks.forEach { tick ->
            val y = startY + tick.positionPt * pxPerPt
            if (y < -20f || y > size.height + 20f) return@forEach
            val tickLeft = if (tick.isMajor) size.width * 0.42f else size.width * 0.68f
            drawLine(
                color = lineColor,
                start = Offset(tickLeft, y),
                end = Offset(size.width - 2f, y),
                strokeWidth = if (tick.isMajor) 1.5f else 1f,
            )
        }
    }
}
