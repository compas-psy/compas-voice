package ru.cmpas.voice.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.cmpas.voice.data.Background
import ru.cmpas.voice.data.PlayerPhase
import ru.cmpas.voice.data.Practice
import ru.cmpas.voice.data.PracticeGroup
import ru.cmpas.voice.data.SessionConfig

/**
 * Плеер практик.
 *
 * MVP: **симулированный таймлайн** — двигает виртуальную позицию, чтобы полностью
 * проиграть флоу (играет → пауза → −15с → таймер сна → угасание → завершение)
 * без реальных аудиофайлов, которых пока нет. Интеграция реального звука
 * (Media3/ExoPlayer + MediaSession + фоновое воспроизведение) — следующий шаг,
 * см. docs/ROADMAP.md. Публичный контракт (`state`, методы) при этом не меняется.
 */
class PlayerController(
    private val scope: CoroutineScope,
    private val background: BackgroundAudio = NoopBackgroundAudio,
) {

    data class State(
        val practiceId: String? = null,
        val title: String = "",
        val group: PracticeGroup? = null,
        val isSleep: Boolean = false,
        val background: Background = Background.VOICE,
        val checkInBefore: Int? = null,
        val phase: PlayerPhase = PlayerPhase.PAUSED,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val sleepTimerMin: Int? = null,
        val sleepRemainingMs: Long? = null,
        val finished: Boolean = false,
        val scrubbing: Boolean = false,
        val startedAtEpochMs: Long = 0L,
    ) {
        val isActive: Boolean get() = practiceId != null
        val fraction: Float
            get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var tickJob: Job? = null
    private val tickMs = 200L
    private val fadeWindowMs = 20_000L // последние 20с — угасание
    private var sleepFadeSignaled = false

    fun start(practice: Practice, config: SessionConfig, checkInBefore: Int?, nowMs: Long) {
        tickJob?.cancel()
        _state.value = State(
            practiceId = practice.id,
            title = practice.title,
            group = practice.group,
            isSleep = practice.isSleep,
            background = config.background,
            checkInBefore = checkInBefore,
            phase = if (practice.isSleep) PlayerPhase.NIGHT else PlayerPhase.PLAYING,
            positionMs = 0L,
            durationMs = config.duration * 60_000L,
            startedAtEpochMs = nowMs,
        )
        sleepFadeSignaled = false
        // Ведущий слой (голос) появится позже; сейчас стартует только фон.
        background.start(practice.soundFamily, config.background)
        tickJob = scope.launch { runClock() }
    }

    private suspend fun runClock() {
        while (scope.isActive) {
            delay(tickMs)
            val s = _state.value
            if (s.finished || s.practiceId == null) continue
            val playing = s.phase == PlayerPhase.PLAYING ||
                s.phase == PlayerPhase.NIGHT ||
                s.phase == PlayerPhase.FADING
            if (!playing) continue
            if (s.scrubbing) continue // во время перемотки таймлайн держим

            var pos = s.positionMs + tickMs
            var sleepRemaining = s.sleepRemainingMs?.let { it - tickMs }
            var phase = s.phase
            var finished = false

            // Таймер сна: за 20с до срабатывания — угасание; по нулю — завершение.
            if (sleepRemaining != null) {
                if (sleepRemaining <= 0L) finished = true
                else if (sleepRemaining <= fadeWindowMs) phase = PlayerPhase.FADING
            }

            // Естественный конец трека.
            if (pos >= s.durationMs && s.durationMs > 0) {
                pos = s.durationMs
                finished = true
            } else if (s.durationMs > 0 && s.durationMs - pos <= fadeWindowMs && s.isSleep) {
                // У сонных практик — плавное угасание к концу.
                phase = PlayerPhase.FADING
            }

            _state.update {
                it.copy(
                    positionMs = pos,
                    sleepRemainingMs = sleepRemaining,
                    phase = if (finished) it.phase else phase,
                    finished = finished,
                )
            }

            val ns = _state.value
            if (ns.phase == PlayerPhase.FADING && !sleepFadeSignaled) {
                sleepFadeSignaled = true
                background.enterSleepFade((ns.durationMs - ns.positionMs).coerceAtLeast(2_000L))
            }
            if (ns.finished) background.stop()
        }
    }

    fun togglePlayPause() {
        _state.update { s ->
            if (!s.isActive || s.finished) return@update s
            when (s.phase) {
                PlayerPhase.PAUSED -> s.copy(
                    phase = if (s.isSleep) PlayerPhase.NIGHT else PlayerPhase.PLAYING
                )
                else -> s.copy(phase = PlayerPhase.PAUSED)
            }
        }
        val p = _state.value
        if (p.isActive && !p.finished) {
            if (p.phase == PlayerPhase.PAUSED) background.pause() else background.resume()
        }
    }

    fun seekBack15() {
        _state.update { s ->
            if (!s.isActive) return@update s
            s.copy(positionMs = (s.positionMs - 15_000L).coerceAtLeast(0L))
        }
    }

    // ── Перемотка по полосе прогресса (ТЗ 1.1 §4) ───────────
    fun beginScrub() {
        _state.update { if (it.isActive && !it.finished) it.copy(scrubbing = true) else it }
    }

    fun scrubTo(fraction: Float) {
        _state.update { s ->
            if (!s.isActive || s.durationMs <= 0L) return@update s
            s.copy(positionMs = (fraction.coerceIn(0f, 1f) * s.durationMs).toLong())
        }
    }

    fun endScrub() {
        _state.update { it.copy(scrubbing = false) }
    }

    fun setSleepTimer(minutes: Int?) {
        _state.update { s ->
            s.copy(
                sleepTimerMin = minutes,
                sleepRemainingMs = minutes?.let { it * 60_000L },
            )
        }
    }

    fun setBackground(background: Background) {
        _state.update { it.copy(background = background) }
    }

    /** Закрыть практику; UI решает, показывать ли экран «После практики». */
    fun stop() {
        tickJob?.cancel()
        tickJob = null
        background.stop()
        _state.value = State()
    }
}
