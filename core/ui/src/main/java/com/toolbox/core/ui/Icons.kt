package com.toolbox.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.toolbox.core.model.Glyph
import kotlin.math.cos
import kotlin.math.sin

/**
 * 自绘图标：1.3dp 描边、24 网格的比例、圆头圆角。
 * 不引 material-icons-extended，避免为 12 个图标背 10MB 依赖。
 */

@Composable
fun ToolGlyph(glyph: Glyph, tint: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        drawToolGlyph(glyph, tint, this.size.minDimension)
    }
}

enum class NavKind { GRID, TASKS, SETTINGS }

@Composable
fun NavGlyph(kind: NavKind, tint: Color, size: Dp = 17.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val sw = 1.6.dp.toPx()
        val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            NavKind.GRID -> {
                val r = CornerRadius(s * 0.05f)
                listOf(0.12f to 0.12f, 0.56f to 0.12f, 0.12f to 0.56f, 0.56f to 0.56f).forEach {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(s * it.first, s * it.second),
                        size = Size(s * 0.30f, s * 0.30f),
                        cornerRadius = r,
                        style = stroke
                    )
                }
            }
            NavKind.TASKS -> listOf(0.26f, 0.50f, 0.74f).forEach { y ->
                drawLine(
                    tint,
                    Offset(s * 0.14f, s * y),
                    Offset(s * 0.86f, s * y),
                    sw,
                    StrokeCap.Round
                )
            }
            NavKind.SETTINGS -> {
                drawCircle(tint, s * 0.16f, Offset(s * 0.5f, s * 0.5f), style = stroke)
                repeat(8) { i ->
                    val a = Math.toRadians(i * 45.0)
                    drawLine(
                        tint,
                        Offset(s * 0.5f + s * 0.26f * cos(a).toFloat(), s * 0.5f + s * 0.26f * sin(a).toFloat()),
                        Offset(s * 0.5f + s * 0.36f * cos(a).toFloat(), s * 0.5f + s * 0.36f * sin(a).toFloat()),
                        sw,
                        StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun BackIcon(tint: Color, size: Dp = 15.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val sw = 1.6.dp.toPx()
        drawLine(tint, Offset(s * 0.62f, s * 0.16f), Offset(s * 0.32f, s * 0.50f), sw, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.32f, s * 0.50f), Offset(s * 0.62f, s * 0.84f), sw, StrokeCap.Round)
    }
}

@Composable
fun ChevronRightIcon(tint: Color, size: Dp = 11.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val sw = 1.4.dp.toPx()
        drawLine(tint, Offset(s * 0.40f, s * 0.20f), Offset(s * 0.62f, s * 0.50f), sw, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.62f, s * 0.50f), Offset(s * 0.40f, s * 0.80f), sw, StrokeCap.Round)
    }
}

@Composable
fun MoreIcon(tint: Color, size: Dp = 15.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        listOf(0.20f, 0.50f, 0.80f).forEach { x ->
            drawCircle(tint, s * 0.055f, Offset(s * x, s * 0.5f))
        }
    }
}

@Composable
fun CheckIcon(tint: Color, size: Dp = 11.dp, strokeWidth: Dp = 2.2.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val sw = strokeWidth.toPx()
        drawLine(tint, Offset(s * 0.20f, s * 0.52f), Offset(s * 0.42f, s * 0.74f), sw, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.42f, s * 0.74f), Offset(s * 0.80f, s * 0.28f), sw, StrokeCap.Round)
    }
}

@Composable
fun SearchIcon(tint: Color, size: Dp = 13.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val sw = 1.5.dp.toPx()
        drawCircle(tint, s * 0.28f, Offset(s * 0.44f, s * 0.44f), style = Stroke(sw))
        drawLine(tint, Offset(s * 0.64f, s * 0.64f), Offset(s * 0.86f, s * 0.86f), sw, StrokeCap.Round)
    }
}

@Composable
fun FolderIcon(tint: Color, size: Dp = 13.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val sw = 1.4.dp.toPx()
        val path = Path().apply {
            moveTo(s * 0.10f, s * 0.30f)
            lineTo(s * 0.40f, s * 0.30f)
            lineTo(s * 0.48f, s * 0.40f)
            lineTo(s * 0.90f, s * 0.40f)
            lineTo(s * 0.90f, s * 0.82f)
            lineTo(s * 0.10f, s * 0.82f)
            close()
        }
        drawPath(path, tint, style = Stroke(sw, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawToolGlyph(glyph: Glyph, tint: Color, s: Float) {
    val sw = 1.3.dp.toPx()
    val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (glyph) {
        Glyph.CONVERT -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(s * 0.10f, s * 0.12f),
                size = Size(s * 0.38f, s * 0.76f),
                cornerRadius = CornerRadius(s * 0.09f),
                style = stroke
            )
            drawLine(tint, Offset(s * 0.50f, s * 0.62f), Offset(s * 0.90f, s * 0.62f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.76f, s * 0.48f), Offset(s * 0.90f, s * 0.62f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.76f, s * 0.76f), Offset(s * 0.90f, s * 0.62f), sw, StrokeCap.Round)
        }

        Glyph.MUSIC -> {
            drawCircle(tint, s * 0.095f, Offset(s * 0.28f, s * 0.78f), style = stroke)
            drawCircle(tint, s * 0.095f, Offset(s * 0.70f, s * 0.68f), style = stroke)
            drawLine(tint, Offset(s * 0.375f, s * 0.78f), Offset(s * 0.375f, s * 0.22f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.795f, s * 0.68f), Offset(s * 0.795f, s * 0.14f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.375f, s * 0.22f), Offset(s * 0.795f, s * 0.14f), sw, StrokeCap.Round)
        }

        Glyph.FILM -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(s * 0.08f, s * 0.22f),
                size = Size(s * 0.84f, s * 0.56f),
                cornerRadius = CornerRadius(s * 0.10f),
                style = stroke
            )
            val play = Path().apply {
                moveTo(s * 0.43f, s * 0.38f)
                lineTo(s * 0.65f, s * 0.50f)
                lineTo(s * 0.43f, s * 0.62f)
                close()
            }
            drawPath(play, tint)
        }

        Glyph.SCISSORS -> {
            drawCircle(tint, s * 0.085f, Offset(s * 0.22f, s * 0.80f), style = stroke)
            drawCircle(tint, s * 0.085f, Offset(s * 0.78f, s * 0.80f), style = stroke)
            drawLine(tint, Offset(s * 0.28f, s * 0.74f), Offset(s * 0.76f, s * 0.14f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.72f, s * 0.74f), Offset(s * 0.24f, s * 0.14f), sw, StrokeCap.Round)
        }

        Glyph.CORNERS -> {
            drawLine(tint, Offset(s * 0.12f, s * 0.40f), Offset(s * 0.12f, s * 0.20f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.12f, s * 0.20f), Offset(s * 0.36f, s * 0.20f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.64f, s * 0.20f), Offset(s * 0.88f, s * 0.20f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.88f, s * 0.20f), Offset(s * 0.88f, s * 0.40f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.88f, s * 0.60f), Offset(s * 0.88f, s * 0.80f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.88f, s * 0.80f), Offset(s * 0.64f, s * 0.80f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.36f, s * 0.80f), Offset(s * 0.12f, s * 0.80f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.12f, s * 0.80f), Offset(s * 0.12f, s * 0.60f), sw, StrokeCap.Round)
        }

        Glyph.PHOTO -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(s * 0.10f, s * 0.18f),
                size = Size(s * 0.80f, s * 0.64f),
                cornerRadius = CornerRadius(s * 0.08f),
                style = stroke
            )
            drawCircle(tint, s * 0.065f, Offset(s * 0.30f, s * 0.36f), style = stroke)
            val mountain = Path().apply {
                moveTo(s * 0.16f, s * 0.74f)
                lineTo(s * 0.40f, s * 0.48f)
                lineTo(s * 0.55f, s * 0.64f)
                lineTo(s * 0.67f, s * 0.52f)
                lineTo(s * 0.84f, s * 0.74f)
            }
            drawPath(mountain, tint, style = stroke)
        }

        Glyph.FRAME -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(s * 0.08f, s * 0.20f),
                size = Size(s * 0.84f, s * 0.60f),
                cornerRadius = CornerRadius(s * 0.08f),
                style = stroke
            )
            drawLine(tint, Offset(s * 0.40f, s * 0.20f), Offset(s * 0.40f, s * 0.80f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.40f, s * 0.38f), Offset(s * 0.92f, s * 0.38f), sw, StrokeCap.Round)
        }
    }
}