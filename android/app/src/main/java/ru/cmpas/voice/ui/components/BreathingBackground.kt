package ru.cmpas.voice.ui.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.lerp
import kotlin.math.min

/**
 * Абстрактное «дышащее» пятно. Мягкие края — за счёт радиального градиента,
 * уходящего в прозрачность (без Modifier.blur, чтобы работать на minSdk 26).
 *
 * @param cx,cy  центр в долях размера (0..1)
 * @param radius радиус в долях меньшей стороны (0..1)
 */
data class Blob(
    val color: Color,
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val alpha: Float = 0.85f,
)

// Кривая дыхания из ТЗ: цикл 10с — 4с мягкое расширение, 6с сжатие, ease-in-out.
private val EaseInOut: Easing = Easing { t -> // приближение cubic-bezier(.42,0,.58,1)
    val x = t.coerceIn(0f, 1f)
    x * x * (3f - 2f * x)
}

/**
 * Фон из нескольких дышащих пятен. Синхронизирован со звуковым пейсингом:
 * 10-секундный цикл. На паузе (`running=false`) дыхание останавливается,
 * прозрачность занижается.
 */
@Composable
fun BreathingBackground(
    blobs: List<Blob>,
    modifier: Modifier = Modifier,
    running: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "breathe")

    // Фаза дыхания 0..1: пик расширения на 4с, сжатие к 10с.
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 10_000
                0f at 0 using EaseInOut
                1f at 4_000 using EaseInOut
                0f at 10_000 using EaseInOut
            },
        ),
        label = "breath",
    )

    // Медленный дрейф для живости (translate).
    val driftX by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(29_000), RepeatMode.Reverse),
        label = "driftX",
    )
    val driftY by transition.animateFloat(
        initialValue = 1f, targetValue = -1f,
        animationSpec = infiniteRepeatable(tween(34_000), RepeatMode.Reverse),
        label = "driftY",
    )

    // На паузе — статичное, приглушённое состояние.
    val phase = if (running) breath else 0.35f
    val dx = if (running) driftX else 0f
    val dy = if (running) driftY else 0f

    val scale = lerp(0.78f, 1.14f, phase)
    val alphaMul = if (running) lerp(0.45f, 0.85f, phase) else 0.30f

    Canvas(modifier = modifier) {
        val minDim = min(size.width, size.height)
        val driftPx = minDim * 0.04f
        blobs.forEach { blob ->
            val r = blob.radius * minDim * scale
            if (r <= 0f) return@forEach
            val center = Offset(
                x = blob.cx * size.width + dx * driftPx,
                y = blob.cy * size.height + dy * driftPx,
            )
            val brush = Brush.radialGradient(
                colors = listOf(
                    blob.color.copy(alpha = blob.alpha * alphaMul),
                    blob.color.copy(alpha = 0f),
                ),
                center = center,
                radius = r,
            )
            drawRect(brush = brush)
        }
    }
}
