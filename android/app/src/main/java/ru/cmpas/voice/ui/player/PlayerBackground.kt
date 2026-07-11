package ru.cmpas.voice.ui.player

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import ru.cmpas.voice.data.PracticeGroup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Программный фон плеера (ТЗ §8): один движок, «дымка» + «геометрия» = «сочетание»,
 * свой оттенок и режим на семейство. Цикл дыхания 10с (4с расширение / 6с сжатие),
 * лёгкая реакция на «звук» (сейчас — синтезированная огибающая-заглушка; сюда же
 * подключается реальный RMS-тап трека). Всё рисуется на Canvas, без картинок.
 * На паузе/в фоне анимация замирает; при reduced-motion — фиксируется на среднем вдохе.
 */

private enum class Geometry { RINGS, SPOKES, WAVES, EDGE }

private data class FamilyBg(
    val tone: Color,     // почти-чёрная база с оттенком
    val accent: Color,   // цвет пятен и линий
    val react: Float,    // коэффициент реакции на звук
    val drift: Float,    // скорость дрейфа
    val geometry: Geometry,
)

private fun familyBg(group: PracticeGroup?): FamilyBg = when (group) {
    PracticeGroup.SLEEP -> FamilyBg(Color(0xFF080A11), Color(0xFF4C5678), 0.28f, 0.85f, Geometry.RINGS)
    PracticeGroup.EXIT_DAY -> FamilyBg(Color(0xFF100C0E), Color(0xFFC98A6B), 0.55f, 1.00f, Geometry.SPOKES)
    PracticeGroup.ANXIETY -> FamilyBg(Color(0xFF080F0D), Color(0xFF3E6E5E), 0.40f, 0.60f, Geometry.WAVES)
    PracticeGroup.TALKS -> FamilyBg(Color(0xFF110C09), Color(0xFFB4744F), 0.70f, 1.25f, Geometry.SPOKES)
    PracticeGroup.SUPPORT -> FamilyBg(Color(0xFF0A0F0C), Color(0xFF2D5F4F), 0.45f, 0.55f, Geometry.EDGE)
    null -> FamilyBg(Color(0xFF0E1014), Color(0xFFC98A6B), 0.45f, 0.9f, Geometry.RINGS)
}

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
fun PlayerBackground(
    group: PracticeGroup?,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val cfg = familyBg(group)

    var timeSec by remember { mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(running, reducedMotion) {
        if (!running || reducedMotion) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) timeSec += ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
            }
        }
    }

    val t = timeSec
    // Дыхание: 10с цикл, пик на 4с. reduced-motion → замереть на 0.6.
    val breath = if (reducedMotion) 0.6f else {
        val tt = t % 10f
        if (tt < 4f) smoothstep(tt / 4f) else smoothstep(1f - (tt - 4f) / 6f)
    }
    // «Уровень звука» — мягкая синтезированная огибающая (заглушка под RMS-тап).
    val level = if (reducedMotion) 0.3f
    else (0.28f + 0.16f * sin(t * 0.8f) + 0.09f * sin(t * 1.7f + 1.3f) + 0.05f * sin(t * 3.1f))
        .coerceIn(0f, 1f)
    // На паузе — фон приглушён.
    val globalAlpha = if (running || reducedMotion) 1f else 0.55f

    Canvas(modifier = modifier) {
        // База — почти-чёрный тон семейства.
        drawRect(color = cfg.tone)

        drawFog(t, breath, level, cfg, globalAlpha)
        when (cfg.geometry) {
            Geometry.RINGS -> drawRings(t, breath, level, cfg, globalAlpha)
            Geometry.SPOKES -> drawSpokes(t, breath, level, cfg, globalAlpha)
            Geometry.WAVES -> drawWaves(t, breath, level, cfg, globalAlpha)
            Geometry.EDGE -> drawEdge(t, breath, level, cfg, globalAlpha)
        }

        // Виньетка для читаемости текста поверх фона.
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color(0x8C06070A),
                0.32f to Color(0x0D06070A),
                0.70f to Color(0x2606070A),
                1.0f to Color(0xB306070A),
            )
        )
    }
}

// ── Режим A: «Дымка» ─────────────────────────────────────────
private fun DrawScope.drawFog(t: Float, breath: Float, level: Float, cfg: FamilyBg, ga: Float) {
    val w = size.width; val h = size.height; val m = min(w, h)
    val xs = floatArrayOf(0.32f, 0.70f, 0.52f)
    val ys = floatArrayOf(0.34f, 0.66f, 0.86f)
    val baseR = floatArrayOf(0.72f, 0.66f, 0.52f)
    val bReact = floatArrayOf(1f, 0.7f, 0.45f)
    for (i in 0..2) {
        val ds = (0.10f + i * 0.05f) * cfg.drift
        val ds2 = (0.23f + i * 0.07f) * cfg.drift
        val ph = i * 2.1f
        val sway = 0.055f + level * 0.03f
        val cx = w * (xs[i] + sin(t * ds + ph) * sway + sin(t * ds2 + ph * 1.7f) * sway * 0.5f)
        val cy = h * (ys[i] + cos(t * ds * 0.85f + ph) * sway + cos(t * ds2 * 1.1f + ph) * sway * 0.45f)
        val wob = 1f + sin(t * ds2 * 1.4f + ph) * 0.06f
        val scale = (0.80f + breath * 0.30f + level * cfg.react * bReact[i] * 0.9f) * wob
        val r = m * baseR[i] * scale
        val alpha = ((0.42f + breath * 0.34f) * (0.7f + level * cfg.react * bReact[i] * 1.1f) * ga)
            .coerceIn(0f, 0.9f)
        if (r <= 0f) continue
        val brush = Brush.radialGradient(
            0.0f to cfg.accent.copy(alpha = alpha),
            0.55f to cfg.accent.copy(alpha = alpha * 0.45f),
            1.0f to cfg.accent.copy(alpha = 0f),
            center = Offset(cx, cy),
            radius = r,
        )
        drawCircle(brush = brush, radius = r, center = Offset(cx, cy), blendMode = BlendMode.Plus)
    }
}

// ── Режим B: «Геометрия» ─────────────────────────────────────
private fun DrawScope.drawRings(t: Float, breath: Float, level: Float, cfg: FamilyBg, ga: Float) {
    val w = size.width; val h = size.height; val m = min(w, h)
    val cx = w / 2f + sin(t * 0.2f) * w * 0.01f
    val cy = h / 2f + cos(t * 0.17f) * h * 0.01f
    val lw = 1.4f
    for (i in 0..6) {
        val frac = ((t * 0.05f + i / 7f) % 1f)
        val rad = m * 0.66f * frac * (0.9f + breath * 0.12f + level * 0.3f)
        val alpha = ((1f - frac) * (0.32f + level * 0.7f) * 0.6f * ga).coerceIn(0f, 0.85f)
        drawCircle(cfg.accent.copy(alpha = alpha), radius = rad, center = Offset(cx, cy), style = Stroke(lw), blendMode = BlendMode.Plus)
    }
    // «Дышащее» ядро-кольцо.
    val core = m * (0.13f + breath * 0.05f + level * 0.22f)
    drawCircle(cfg.accent.copy(alpha = (0.45f + level * 0.4f) * ga * 0.5f), radius = core, center = Offset(cx, cy), style = Stroke(1.6f), blendMode = BlendMode.Plus)
}

private fun DrawScope.drawSpokes(t: Float, breath: Float, level: Float, cfg: FamilyBg, ga: Float) {
    val w = size.width; val h = size.height; val m = min(w, h)
    val cx = w / 2f; val cy = h / 2f
    val rot = t * 0.045f
    val n = 52
    for (k in 0 until n) {
        val ang = rot + k.toFloat() / n * 2f * PI.toFloat()
        val wob = 0.55f + 0.45f * sin(k * 0.7f + t * 1.3f)
        val len = m * (0.22f + breath * 0.1f + level * 0.42f * wob)
        val alpha = ((0.06f + level * 0.18f) * ga).coerceIn(0f, 0.5f)
        val dx = cos(ang); val dy = sin(ang)
        drawLine(
            color = cfg.accent.copy(alpha = alpha),
            start = Offset(cx + dx * m * 0.06f, cy + dy * m * 0.06f),
            end = Offset(cx + dx * len, cy + dy * len),
            strokeWidth = 1.4f,
            cap = StrokeCap.Round,
            blendMode = BlendMode.Plus,
        )
    }
}

private fun DrawScope.drawWaves(t: Float, breath: Float, level: Float, cfg: FamilyBg, ga: Float) {
    val w = size.width; val h = size.height
    val lines = 6
    for (li in 0 until lines) {
        val yBase = h * (0.30f + li * 0.08f)
        val amp = h * (0.012f + level * 0.075f + breath * 0.014f)
        val phase = t * (0.6f + li * 0.08f) + li
        val alpha = ((0.05f + level * 0.14f) * ga).coerceIn(0f, 0.4f)
        val path = Path()
        val steps = 48
        for (s in 0..steps) {
            val x = w * s / steps
            val env = sin((x / w) * PI.toFloat()) // гаснет по краям
            val y = yBase + sin((x / w) * 6.2831853f * 1.5f + phase) * amp * env
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = cfg.accent.copy(alpha = alpha), style = Stroke(1.3f), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.drawEdge(t: Float, breath: Float, level: Float, cfg: FamilyBg, ga: Float) {
    val w = size.width; val h = size.height; val m = min(w, h)
    val cx = w / 2f; val cy = h / 2f
    val baseR = m * (0.24f + breath * 0.08f + level * 0.28f)
    poly(cx, cy, baseR, t * 0.1f, 0.5f * ga, 1.8f, cfg.accent)
    poly(cx, cy, baseR * 0.6f, -t * 0.15f, 0.3f * ga, 1.3f, cfg.accent)
    poly(cx, cy, baseR * 1.45f, t * 0.06f, (0.12f + level * 0.32f) * ga, 1.1f, cfg.accent)
}

private fun DrawScope.poly(cx: Float, cy: Float, r: Float, rot: Float, alpha: Float, lw: Float, color: Color) {
    val path = Path()
    for (k in 0..3) {
        val a = rot + k / 3f * 2f * PI.toFloat()
        val x = cx + cos(a) * r; val y = cy + sin(a) * r
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color.copy(alpha = alpha.coerceIn(0f, 0.7f)), style = Stroke(lw), blendMode = BlendMode.Plus)
}
