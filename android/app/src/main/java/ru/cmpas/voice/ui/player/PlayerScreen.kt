package ru.cmpas.voice.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.cmpas.voice.appContainer
import ru.cmpas.voice.audio.PlayerController
import ru.cmpas.voice.data.Background
import ru.cmpas.voice.data.PlayerPhase
import ru.cmpas.voice.data.Settings
import ru.cmpas.voice.ui.components.BreathingBackground
import ru.cmpas.voice.ui.components.Eyebrow
import ru.cmpas.voice.ui.components.pressClickable
import ru.cmpas.voice.ui.components.playerBlobs
import ru.cmpas.voice.ui.theme.BgFade
import ru.cmpas.voice.ui.theme.BgNight
import ru.cmpas.voice.ui.theme.BgPlayerDim
import ru.cmpas.voice.ui.theme.SurfaceActive
import ru.cmpas.voice.ui.theme.TerracottaMuted
import ru.cmpas.voice.ui.theme.TextPrimary
import ru.cmpas.voice.ui.theme.TextPrimaryNight
import ru.cmpas.voice.ui.theme.TextSecondary
import ru.cmpas.voice.ui.theme.TextTertiary
import ru.cmpas.voice.ui.theme.WhiteAlpha08
import kotlin.math.roundToInt

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun fmt(ms: Long): String {
    val total = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
fun PlayerScreen(
    controller: PlayerController,
    onGoAftercare: () -> Unit,
    onSleepDone: () -> Unit,
    onExit: () -> Unit,
) {
    val state by controller.state.collectAsState()

    // Экран мог открыться раньше, чем стартовала практика — защита.
    if (!state.isActive) {
        Box(Modifier.fillMaxSize().background(BgNight))
        return
    }

    val phase = state.phase
    val night = state.isSleep || phase == PlayerPhase.NIGHT || phase == PlayerPhase.FADING
    val bg = when {
        phase == PlayerPhase.FADING -> BgFade
        night -> BgPlayerDim
        else -> BgNight
    }
    val playing = phase == PlayerPhase.PLAYING || phase == PlayerPhase.NIGHT || phase == PlayerPhase.FADING
    val textPrimary = if (night) TextPrimaryNight else TextPrimary

    // Сонное угасание: к концу трека (и на хвосте «уснуть под фон») весь UI гаснет.
    val fadeAlpha = when {
        state.tailActive && state.tailTotalMs > 0 ->
            (0.06f + 0.42f * (state.tailRemainingMs.toFloat() / state.tailTotalMs)).coerceIn(0.06f, 1f)
        phase == PlayerPhase.FADING -> (1f - state.fraction).coerceIn(0.06f, 1f)
        else -> 1f
    }
    val uiAlpha by animateFloatAsState(fadeAlpha, tween(1200), label = "uiFade")

    // «Не выключать экран» (настройка в разделе «Я»): дышащий фон не гаснет.
    val context = LocalContext.current
    val settings by context.appContainer.store.settings.collectAsState(initial = Settings())
    DisposableEffect(settings.keepScreenOn) {
        val window = context.findActivity()?.window
        if (settings.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Автопонижение яркости на ночных/сонных практиках; восстановление при выходе.
    DisposableEffect(night) {
        val window = context.findActivity()?.window
        val original = window?.attributes?.screenBrightness
        if (window != null && night) {
            val lp = window.attributes
            lp.screenBrightness = 0.04f
            window.attributes = lp
        }
        onDispose {
            if (window != null && original != null) {
                val lp = window.attributes
                lp.screenBrightness = original
                window.attributes = lp
            }
        }
    }

    // Завершение практики. Хвост «уснуть под фон» → без экрана «после».
    LaunchedEffect(state.finished) {
        if (state.finished) {
            if (state.isSleep || state.tailActive) onSleepDone() else onGoAftercare()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            // Тап по всему экрану = пауза/плей.
            .pressClickable { controller.togglePlayPause() },
    ) {
        // Программный фон плеера (ТЗ §8): дымка + геометрия по семейству,
        // дыхание 10с, лёгкая реакция; на угасании затухает вместе со сценой.
        PlayerBackground(
            group = state.group,
            running = playing && phase != PlayerPhase.FADING,
            modifier = Modifier.fillMaxSize().alpha(uiAlpha),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 26.dp)
                .alpha(uiAlpha),
        ) {
            // Верх: eyebrow + крестик.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Eyebrow(state.group?.title ?: "", color = TextTertiary, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(WhiteAlpha08)
                        .pressClickable {
                            val meaningful = !state.isSleep && state.positionMs > 30_000L
                            when {
                                state.isSleep -> onSleepDone()
                                meaningful -> onGoAftercare()
                                else -> onExit()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                state.title,
                style = MaterialTheme.typography.headlineLarge,
                color = textPrimary,
            )

            // Центр: кольца + пауза/плей.
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CenterZone(playing = playing, night = night)
                    Spacer(Modifier.height(28.dp))
                    Text(
                        when {
                            state.tailActive && phase == PlayerPhase.PAUSED -> "Фон на паузе · нажми, чтобы продолжить"
                            state.tailActive -> "Засыпай под фон · звук тихо угасает"
                            phase == PlayerPhase.PAUSED -> "На паузе · нажми, чтобы продолжить"
                            night -> "Медленное погружение"
                            else -> "Нажми в любом месте, чтобы поставить на паузу"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // На хвосте «уснуть под фон» контролы и прогресс скрыты — только тишина.
            if (!state.tailActive) {
                // Контролы.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ControlButton(Icons.Filled.Replay, "−15 сек") { controller.seekBack15() }
                    // «Уснуть под фон»: тумблер — фон тянется по длине практики.
                    ControlButton(
                        if (state.sleepUnderBg) Icons.Filled.Bedtime else Icons.Outlined.Bedtime,
                        "Уснуть под фон",
                        active = state.sleepUnderBg,
                    ) { controller.setSleepUnderBackground(!state.sleepUnderBg) }
                    // «Фон» — переключение звуковой среды прямо во время практики.
                    ControlButton(
                        Icons.Outlined.GraphicEq,
                        when (state.background) {
                            Background.SOFT -> "Мягкий фон"
                            Background.BINAURAL -> "Объём"
                            else -> "Голос"
                        },
                    ) { controller.cycleBackground() }
                }

                // Прогресс + перемотка (зона 56dp, исключена из тап-паузы, ТЗ §4).
                Spacer(Modifier.height(6.dp))
                ProgressZone(state = state, controller = controller)
                Spacer(Modifier.height(10.dp))
            } else {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CenterZone(playing: Boolean, night: Boolean) {
    val transition = rememberInfiniteTransition(label = "rings")
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        if (playing) {
            Canvas(Modifier.fillMaxSize()) {
                val maxR = size.minDimension / 2f
                listOf(pulse, (pulse + 0.5f) % 1f).forEach { p ->
                    val r = maxR * (0.42f + 0.55f * p)
                    val a = (1f - p) * 0.22f
                    drawCircle(
                        color = TerracottaMuted.copy(alpha = a),
                        radius = r,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                    )
                }
            }
        }
        // Стеклянный круг с иконкой паузы/плей.
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(WhiteAlpha08),
            contentAlignment = Alignment.Center,
        ) {
            if (playing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(7.dp).height(30.dp).clip(RoundedCornerShape(3.dp)).background(TextPrimaryNight))
                    Box(Modifier.width(7.dp).height(30.dp).clip(RoundedCornerShape(3.dp)).background(TextPrimaryNight))
                }
            } else {
                // Треугольник плей — приближение через повёрнутый бокс не делаем; иконка.
                Text("▶", color = TextPrimaryNight, style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (onClick != null) Modifier.pressClickable(onClick = onClick) else Modifier)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (active) TerracottaMuted.copy(alpha = 0.28f) else WhiteAlpha08),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) TextPrimary else TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) TextSecondary else TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Зона прогресса 56dp (ТЗ 1.1 §4): исключена из тап-паузы (жест здесь потребляет
 * события). Тап/драг = перемотка; при захвате — тактильная отдача, линия толще
 * (4px) и бабл со временем над пальцем; таймлайн держится, отпускание — commit.
 */
@Composable
private fun ProgressZone(state: PlayerController.State, controller: PlayerController) {
    val hf = LocalHapticFeedback.current
    val density = LocalDensity.current
    var scrubbing by remember { mutableStateOf(false) }
    var scrubX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(state.durationMs) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    hf.performHapticFeedback(HapticFeedbackType.LongPress)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    scrubbing = true
                    controller.beginScrub()
                    scrubX = down.position.x.coerceIn(0f, w)
                    controller.scrubTo(scrubX / w)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) { ch.consume(); break }
                        scrubX = ch.position.x.coerceIn(0f, w)
                        controller.scrubTo(scrubX / w)
                        ch.consume()
                    }
                    scrubbing = false
                    controller.endScrub()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (scrubbing) 4.dp else 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(WhiteAlpha08),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(state.fraction)
                        .background(TerracottaMuted),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(state.positionMs), style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                Text(
                    "−" + fmt((state.durationMs - state.positionMs).coerceAtLeast(0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }
        if (scrubbing) {
            val halfPx = with(density) { 26.dp.toPx() }
            val abovePx = with(density) { 30.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset((scrubX - halfPx).roundToInt(), -abovePx.roundToInt()) }
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceActive)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(fmt(state.positionMs), style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
    }
}
