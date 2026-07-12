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
 * Плеер практик. Ведущий слой — ГОЛОС ([voice]); позиция/длительность следуют за
 * его реальным воспроизведением. Под голосом микшируется фон ([background]) и
 * бинаурал (за флагом). Если голос недоступен (NoopVoiceEngine) — таймлайн
 * симулируется по номинальной длительности, флоу остаётся рабочим.
 */
class PlayerController(
    private val scope: CoroutineScope,
    private val background: BackgroundAudio = NoopBackgroundAudio,
    private val voice: VoiceEngine = NoopVoiceEngine,
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
            durationMs = config.option.sec * 1000L, // номинально до готовности голоса
            startedAtEpochMs = nowMs,
        )
        sleepFadeSignaled = false
        voice.load(config.option.voiceFile)
        voice.play()
        background.start(practice.soundFamily, config.background)
        tickJob = scope.launch { runClock() }
    }

    private suspend fun runClock() {
        while (scope.isActive) {
            delay(tickMs)
            val s = _state.value
            if (s.finished || s.practiceId == null) continue
            if (s.scrubbing) continue // во время перемотки позицию держим (scrubTo)

            val playing = s.phase == PlayerPhase.PLAYING ||
                s.phase == PlayerPhase.NIGHT ||
                s.phase == PlayerPhase.FADING

            // Позиция из голоса; фолбэк-симуляция, если голоса нет.
            val voiceDur = voice.durationMs
            val voicePos = voice.positionMs
            val real = voiceDur > 0L || voicePos > 0L
            val dur = if (voiceDur > 0L) voiceDur else s.durationMs
            val pos = if (real) voicePos
            else if (playing) (s.positionMs + tickMs) else s.positionMs

            var sleepRemaining = s.sleepRemainingMs
            if (playing && sleepRemaining != null) sleepRemaining -= tickMs

            var phase = s.phase
            var finished = voice.ended || (!real && dur > 0L && pos >= dur)

            // Таймер сна: за 20с до срабатывания — угасание; по нулю — завершение.
            if (sleepRemaining != null) {
                if (sleepRemaining <= 0L) finished = true
                else if (sleepRemaining <= fadeWindowMs) phase = PlayerPhase.FADING
            }
            // Сонные практики — плавное угасание к концу.
            if (!finished && s.isSleep && dur > 0L && dur - pos <= fadeWindowMs) {
                phase = PlayerPhase.FADING
            }

            _state.update {
                it.copy(
                    positionMs = if (finished && dur > 0L) dur else pos,
                    durationMs = if (dur > 0L) dur else it.durationMs,
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
            if (ns.finished) {
                background.stop()
                voice.pause()
            }
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
            if (p.phase == PlayerPhase.PAUSED) {
                voice.pause(); background.pause()
            } else {
                voice.play(); background.resume()
            }
        }
    }

    fun seekBack15() {
        if (!_state.value.isActive) return
        voice.seekBy(-15_000L)
        _state.update { s -> s.copy(positionMs = (s.positionMs - 15_000L).coerceAtLeast(0L)) }
    }

    // ── Перемотка по полосе прогресса (ТЗ 1.1 §4) ───────────
    fun beginScrub() {
        if (_state.value.isActive && !_state.value.finished) {
            voice.pause()
            _state.update { it.copy(scrubbing = true) }
        }
    }

    fun scrubTo(fraction: Float) {
        val s = _state.value
        if (!s.isActive || s.durationMs <= 0L) return
        val target = (fraction.coerceIn(0f, 1f) * s.durationMs).toLong()
        voice.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun endScrub() {
        _state.update { it.copy(scrubbing = false) }
        val p = _state.value
        if (p.isActive && p.phase != PlayerPhase.PAUSED) voice.play()
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
        voice.stop()
        background.stop()
        _state.value = State()
    }
}
