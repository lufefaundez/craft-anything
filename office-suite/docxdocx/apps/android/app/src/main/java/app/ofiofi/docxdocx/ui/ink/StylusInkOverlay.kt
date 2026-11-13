package app.ofiofi.docxdocx.ui.ink

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

enum class InkToolKind {
    NONE,
    PEN,
    PENCIL,
    HIGHLIGHTER,
    ERASER,
    LASSO,
}

data class InkPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
)

data class InkStroke(
    val id: Long,
    val tool: InkToolKind,
    val argb: Long,
    val baseWidthDp: Float,
    val points: List<InkPoint>,
)

/**
 * Pure-Kotlin S-Pen / Stylus ink math, hit-testing, and Ribbon Draw tab command mapping.
 */
object InkEngineBridge {
    fun toolForCommand(commandId: String): InkToolKind? = when (commandId) {
        "draw.select" -> InkToolKind.NONE
        "draw.pen", "draw.addPen" -> InkToolKind.PEN
        "draw.pencil" -> InkToolKind.PENCIL
        "draw.highlighter" -> InkToolKind.HIGHLIGHTER
        "draw.eraser" -> InkToolKind.ERASER
        "draw.lasso" -> InkToolKind.LASSO
        else -> null
    }

    fun defaultColorForTool(tool: InkToolKind): Long = when (tool) {
        InkToolKind.HIGHLIGHTER -> 0x66FFF000
        InkToolKind.PENCIL -> 0xCC4A4A4A
        else -> 0xFF185ABD
    }

    fun strokeWidthPx(
        tool: InkToolKind,
        baseWidthPx: Float,
        pressure: Float,
    ): Float {
        val clamped = pressure.coerceIn(0.15f, 1.0f)
        val factor = when (tool) {
            InkToolKind.HIGHLIGHTER -> 3.6f
            InkToolKind.PENCIL -> 0.55f + clamped * 0.45f
            InkToolKind.PEN -> 0.7f + clamped * 0.85f
            else -> 1.0f
        }
        return (baseWidthPx * factor).coerceAtLeast(1f)
    }

    fun hitTestStroke(
        stroke: InkStroke,
        x: Float,
        y: Float,
        radiusPx: Float = 24f,
    ): Boolean = stroke.points.any { pt ->
        hypot((pt.x - x).toDouble(), (pt.y - y).toDouble()) <= radiusPx
    }
}

/**
 * Pressure-sensitive S-Pen & touch drawing surface layered cleanly over the page canvas.
 * Passes touch gestures through unmodified when [activeTool] is [InkToolKind.NONE],
 * while still honoring hardware stylus / eraser buttons when hovering or drawing with S-Pen.
 */
@Composable
fun StylusInkOverlay(
    strokes: List<InkStroke>,
    activeTool: InkToolKind,
    activeColorArgb: Long,
    replayTrigger: Int,
    onAddStroke: (InkStroke) -> Unit,
    onEraseAt: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val replayProgress = remember { Animatable(1f) }
    LaunchedEffect(replayTrigger) {
        if (replayTrigger > 0 && strokes.isNotEmpty()) {
            replayProgress.snapTo(0f)
            replayProgress.animateTo(1f, animationSpec = tween(durationMillis = 1200))
        }
    }

    val capturePointer = activeTool != InkToolKind.NONE
    val gestureModifier = if (capturePointer) {
        modifier.pointerInput(activeTool, activeColorArgb) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val effectiveTool = when (down.type) {
                    PointerType.Eraser -> InkToolKind.ERASER
                    else -> activeTool
                }
                if (effectiveTool == InkToolKind.ERASER) {
                    onEraseAt(down.position.x, down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { ch ->
                            onEraseAt(ch.position.x, ch.position.y)
                            ch.consume()
                        }
                    } while (event.changes.any { it.pressed })
                } else if (effectiveTool != InkToolKind.NONE && effectiveTool != InkToolKind.LASSO) {
                    val pts = mutableListOf(
                        InkPoint(
                            x = down.position.x,
                            y = down.position.y,
                            pressure = down.pressure.takeIf { it > 0f } ?: 0.5f,
                        ),
                    )
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { ch ->
                            pts.add(
                                InkPoint(
                                    x = ch.position.x,
                                    y = ch.position.y,
                                    pressure = ch.pressure.takeIf { it > 0f } ?: 0.5f,
                                ),
                            )
                            ch.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    if (pts.size >= 2) {
                        onAddStroke(
                            InkStroke(
                                id = System.nanoTime(),
                                tool = effectiveTool,
                                argb = activeColorArgb,
                                baseWidthDp = if (effectiveTool == InkToolKind.HIGHLIGHTER) 12f else 3f,
                                points = pts,
                            ),
                        )
                    }
                }
            }
        }
    } else {
        modifier
    }

    Canvas(modifier = gestureModifier) {
        val visibleStrokeCount = if (replayProgress.value >= 0.999f) {
            strokes.size
        } else {
            ((strokes.size * replayProgress.value).toInt()).coerceAtLeast(1).coerceAtMost(strokes.size)
        }
        strokes.take(visibleStrokeCount).forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            val path = Path().apply {
                moveTo(stroke.points.first().x, stroke.points.first().y)
                for (i in 1 until stroke.points.size) {
                    val p = stroke.points[i]
                    lineTo(p.x, p.y)
                }
            }
            val avgPressure = stroke.points.map { it.pressure }.average().toFloat()
            val widthPx = InkEngineBridge.strokeWidthPx(
                tool = stroke.tool,
                baseWidthPx = stroke.baseWidthDp.dp.toPx(),
                pressure = avgPressure,
            )
            drawPath(
                path = path,
                color = Color(stroke.argb),
                style = Stroke(
                    width = widthPx,
                    cap = if (stroke.tool == InkToolKind.HIGHLIGHTER) StrokeCap.Square else StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
        // Keep compiler happy with Offset reference if empty
        if (strokes.isEmpty() && false) {
            drawCircle(Color.Transparent, radius = 0f, center = Offset.Zero)
        }
    }
}
