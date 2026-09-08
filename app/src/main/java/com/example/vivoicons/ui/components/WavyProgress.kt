package com.example.vivoicons.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 波浪形线性进度条（规范 C）：active indicator 为正弦波。
 * 默认静止（仅在 progress 变化时重绘，无常驻动画开销）；
 * [animated] = true 时波浪持续流动（用于不确定进度等场景）。
 */
@Composable
fun WavyLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    strokeWidth: Dp = 7.dp,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 22.dp,
    animated: Boolean = false,
) {
    var phaseNorm = 0f
    if (animated) {
        val transition = rememberInfiniteTransition(label = "wave")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
            label = "wavePhase",
        )
        phaseNorm = phase
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(strokeWidth + amplitude * 2 + 2.dp),
    ) {
        val strokePx = strokeWidth.toPx()
        val ampPx = amplitude.toPx()
        val waveLenPx = wavelength.toPx()
        val y = size.height / 2
        val phasePx = phaseNorm * waveLenPx

        // 轨道：与 active 波浪同相位、同波长的灰色波浪线，避免直线与波浪交叠残影
        drawWavyLine(0f, size.width, y, ampPx, waveLenPx, phasePx, trackColor, strokePx)
        // 波浪 active indicator
        val progressPx = (size.width * progress.coerceIn(0f, 1f))
        if (progressPx > strokePx / 2) {
            drawWavyLine(0f, progressPx, y, ampPx, waveLenPx, phasePx, color, strokePx)
        }
    }
}

/** 静态波浪（用于完成态等，不做流动动画时用 progress 参数区分） */
@Composable
fun WavyLinearProgressIndicatorIndeterminate(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    strokeWidth: Dp = 7.dp,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 22.dp,
) {
    val transition = rememberInfiniteTransition(label = "waveInd")
    val position by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "wavePos",
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(strokeWidth + amplitude * 2 + 2.dp),
    ) {
        val strokePx = strokeWidth.toPx()
        val ampPx = amplitude.toPx()
        val waveLenPx = wavelength.toPx()
        val y = size.height / 2

        // 轨道：与 active 波浪同相位的灰色波浪线
        drawWavyLine(0f, size.width, y, ampPx, waveLenPx, 0f, trackColor, strokePx)
        val head = size.width * position.coerceIn(0f, 1.3f)
        val tail = head - size.width * 0.35f
        if (head > 0 && tail < size.width) {
            drawWavyLine(
                tail.coerceAtLeast(0f),
                head.coerceAtMost(size.width),
                y, ampPx, waveLenPx, 0f, color, strokePx,
            )
        }
    }
}

private fun DrawScope.drawWavyLine(
    fromX: Float,
    toX: Float,
    y: Float,
    ampPx: Float,
    waveLenPx: Float,
    phasePx: Float,
    color: Color,
    strokePx: Float,
) {
    if (toX <= fromX) return
    val step = 3f
    var prevX = fromX
    var prevY = y + ampPx * sin((fromX + phasePx) / waveLenPx * 2f * PI.toFloat())
    var x = fromX + step
    while (x < toX) {
        val yy = y + ampPx * sin((x + phasePx) / waveLenPx * 2f * PI.toFloat())
        drawLine(color, Offset(prevX, prevY), Offset(x, yy), strokePx, StrokeCap.Round)
        prevX = x
        prevY = yy
        x += step
    }
    drawLine(
        color,
        Offset(prevX, prevY),
        Offset(toX, y + ampPx * sin((toX + phasePx) / waveLenPx * 2f * PI.toFloat())),
        strokePx,
        StrokeCap.Round,
    )
}
