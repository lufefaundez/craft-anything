package app.ofiofi.docxdocx.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.ofiofi.docxdocx.ui.theme.OfficeTokens

data class RulerTick(
    val positionPt: Float,
    val label: String?,
    val isMajor: Boolean,
)

/**
 * Pure-Kotlin ruler scale math (centimeters at 28.3465 pt/cm for Word es-CL default).
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
}

@Composable
fun HorizontalPageRuler(
    pageWidthPt: Float = 612f,
    marginPt: Float = 72f,
    zoom: Float = 1f,
    surfaces: OfficeTokens.SurfacePalette,
    modifier: Modifier = Modifier,
) {
    val ticks = RulerMath.buildTicks(totalPoints = pageWidthPt, marginStartPt = marginPt)
    val bgColor = surfaces.panel
    val lineColor = surfaces.textDim
    val textColor = surfaces.text

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(bgColor),
    ) {
        val pxPerPt = (size.width / pageWidthPt.coerceAtLeast(1f)).coerceAtMost(2.5f * zoom.coerceAtLeast(0.5f))
        val rulerWidthPx = pageWidthPt * pxPerPt
        val startX = ((size.width - rulerWidthPx) / 2f).coerceAtLeast(0f)

        // Margin shade bands
        val marginPx = (marginPt * pxPerPt).coerceAtMost(rulerWidthPx / 3f)
        drawRect(
            color = Color.Gray.copy(alpha = 0.16f),
            topLeft = Offset(startX, 0f),
            size = androidx.compose.ui.geometry.Size(marginPx, size.height),
        )
        drawRect(
            color = Color.Gray.copy(alpha = 0.16f),
            topLeft = Offset(startX + rulerWidthPx - marginPx, 0f),
            size = androidx.compose.ui.geometry.Size(marginPx, size.height),
        )

        val paint = android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 22f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        ticks.forEach { tick ->
            val x = startX + tick.positionPt * pxPerPt
            val tickTop = if (tick.isMajor) size.height * 0.55f else size.height * 0.72f
            drawLine(
                color = lineColor,
                start = Offset(x, tickTop),
                end = Offset(x, size.height),
                strokeWidth = if (tick.isMajor) 1.5f else 1f,
            )
            tick.label?.let { lbl ->
                drawContext.canvas.nativeCanvas.drawText(lbl, x, size.height * 0.48f, paint)
            }
        }
    }
}

@Composable
fun VerticalPageRuler(
    pageHeightPt: Float = 792f,
    marginPt: Float = 72f,
    surfaces: OfficeTokens.SurfacePalette,
    modifier: Modifier = Modifier,
) {
    val ticks = RulerMath.buildTicks(totalPoints = pageHeightPt, marginStartPt = marginPt)
    val lineColor = surfaces.textDim

    Canvas(
        modifier = modifier
            .width(20.dp)
            .fillMaxHeight()
            .background(surfaces.panel),
    ) {
        val pxPerPt = size.height / pageHeightPt.coerceAtLeast(1f)
        val marginPx = (marginPt * pxPerPt).coerceAtMost(size.height / 4f)
        drawRect(
            color = Color.Gray.copy(alpha = 0.16f),
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(size.width, marginPx),
        )
        drawRect(
            color = Color.Gray.copy(alpha = 0.16f),
            topLeft = Offset(0f, size.height - marginPx),
            size = androidx.compose.ui.geometry.Size(size.width, marginPx),
        )
        ticks.forEach { tick ->
            val y = tick.positionPt * pxPerPt
            val tickLeft = if (tick.isMajor) size.width * 0.45f else size.width * 0.68f
            drawLine(
                color = lineColor,
                start = Offset(tickLeft, y),
                end = Offset(size.width, y),
                strokeWidth = if (tick.isMajor) 1.5f else 1f,
            )
        }
    }
}
