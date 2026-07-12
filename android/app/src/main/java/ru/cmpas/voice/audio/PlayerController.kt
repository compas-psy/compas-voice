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
import ru.cmpas.voice.data.FeatureFlags
import ru.cmpas.voice.data.PlayerPhase
import ru.cmpas.voice.data.Practice
import ru.cmpas.voice.data.PracticeGroup
import ru.cmpas.voice.data.SessionConfig
import ru.cmpas.voice.data.SoundFamily

/**
 * Плеер практик. Ведущий слой — ГОЛОС ([voice]); позиция/длительность следуют за
 * его реальным воспроизведением. Под голосом микшируется фон ([background]) и
 * бинаурал (за флагом). Если голос недоступен (NoopVoiceEngine) — таймлайн
 * симулируется по номинальной длительности, флоу остаётся рабочим.
 *
 * «Уснуть под фон» ([State.sleepUnderBg]): после конца голоса фон не
 * останавливается сразу, а тянется и плавно гаснет по длине практики (не
 * фиксированные минуты), чтобы можно было уснуть под звук.
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
        val soundFamily: SoundFamily? = null,
        val isSleep: Boolean = false,
        val background: Background = Background.VOICE,
        val checkInBefore: Int? = null,
        val phase: PlayerPhase = PlayerPhase.PAUSED,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        // «Уснуть под фон»: тумблер + хвост фона после голоса.
        val sleepUnderBg: Boolean = false,
        val tailActive: Boolean = false,
        val tailTotalMs: Long = 0L,
        val tailRemainingMs: Long = 0L,
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
    private val fadeWindowMs = 20_000L // последние 20с голоса — визуальное угасание

    fun start(practice: Practice, config: SessionConfig, checkInBefore: Int?, nowMs: Long) {
        tickJob?.cancel()
        _state.value = State(
            practiceId = practice.id,
            title = practice.title,
            group = practice.group,
            soundFamily = practice.soundFamily,
            isSleep = practice.isSleep,
            background = config.background,
            checkInBefore = checkInBefore,
            phase = if (practice.isSleep) PlayerPhase.NIGHT else PlayerPhase.PLAYING,
            positionMs = 0L,
            durationMs = config.option.sec * 1000L, // номинально до готовности голоса
            sleepUnderBg = practice.isSleep,          // для сонных — включён по умолчанию
            startedAtEpochMs = nowMs,
        )
        voice.load(config.option.voiceFile)
        voice.play()
        background.start(practice.soundFamily, config.background)
        tickJob = scope.launch { runClock() }
    }

    private suspend fun runClock() {
        while (scope.isActive) {
            delay(tickMs)
            val s = _state.value
            if (s.practiceId == null || s.finished) continue

            // ── Хвост «уснуть под фон»: голос закончился, ведём фон по длине практики ──
            if (s.tailActive) {
                if (s.phase == PlayerPhase.PAUSED) continue
                val rem = (s.tailRemainingMs - tickMs).coerceAtLeast(0L)
                val done = rem <= 0L
                _state.update { it.copy(tailRemainingMs = rem, finished = done) }
                if (done) { background.stop(); voice.stop() }
                continue
            }

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

            val voiceEnded = voice.ended || (!real && dur > 0L && pos >= dur)

            // Голос закончился.
            if (voiceEnded) {
                if (s.sleepUnderBg && dur > 0L) {
                    beginTail(s, dur)          // тянем фон, не завершаем
                } else {
                    _state.update {
                        it.copy(positionMs = dur, durationMs = dur, finished = true)
                    }
                    background.stop()
                    voice.pause()
                }
                continue
            }

            // Визуальное угасание в конце голоса на сонных практиках.
            var phase = s.phase
            if (s.isSleep && dur > 0L && dur - pos <= fadeWindowMs) {
                phase = PlayerPhase.FADING
            }

            _state.update {
                it.copy(
                    positionMs = pos,
                    durationMs = if (dur > 0L) dur else it.durationMs,
                    phase = phase,
                )
            }
        }
    }

    /** Начать хвост «уснуть под фон»: фон тянется и гаснет за [tailMs] (= длина практики). */
    private fun beginTail(s: State, tailMs: Long) {
        voice.pause()
        // Нужен звук для засыпания: если был «только голос» — включаем мягкий фон семьи.
        val fam = s.soundFamily
        if (s.background == Background.VOICE && fam != null) {
            background.start(fam, Background.SOFT)
        }
        background.enterSleepFade(tailMs)
        _state.update {
            it.copy(
                positionMs = it.durationMs,
                phase = PlayerPhase.FADING,
                tailActive = true,
                tailTotalMs = tailMs,
                tailRemainingMs = tailMs,
            )
        }
    }

    fun togglePlayPause() {
        val s = _state.value
        if (!s.isActive || s.finished) return

        // Во время хвоста play/pause управляет фоном и паузой отсчёта.
        if (s.tailActive) {
            val paused = s.phase == PlayerPhase.PAUSED
            _state.update { it.copy(phase = if (paused) PlayerPhase.FADING else PlayerPhase.PAUSED) }
            if (paused) background.resume() else background.pause()
            return
        }

        _state.update { st ->
            when (st.phase) {
                PlayerPhase.PAUSED -> st.copy(
                    phase = if (st.isSleep) PlayerPhase.NIGHT else PlayerPhase.PLAYING
                )
                else -> st.copy(phase = PlayerPhase.PAUSED)
            }
        }
        val p = _state.value
        if (p.phase == PlayerPhase.PAUSED) {
            voice.pause(); background.pause()
        } else {
            voice.play(); background.resume()
        }
    }

    /** Циклическое переключение фона прямо во время практики (Голос → Мягкий → Объём). */
    fun cycleBackground() {
        val s = _state.value
        if (!s.isActive || s.finished || s.tailActive) return
        val options = if (FeatureFlags.spatialAudio) {
            listOf(Background.VOICE, Background.SOFT, Background.BINAURAL)
        } else {
            listOf(Background.VOICE, Background.SOFT)
        }
        val idx = options.indexOf(s.background).let { if (it < 0) 0 else it }
        val next = options[(idx + 1) % options.size]
        _state.update { it.copy(background = next) }
        val fam = s.soundFamily
        if (fam != null) {
            background.start(fam, next) // start() сам делает stop() при VOICE
            if (s.phase == PlayerPhase.PAUSED) background.pause()
        }
    }

    fun seekBack15() {
        val s = _state.value
        if (!s.isActive || s.tailActive) return
        voice.seekBy(-15_000L)
        _state.update { st -> st.copy(positionMs = (st.positionMs - 15_000L).coerceAtLeast(0L)) }
    }

    // ── Перемотка по полосе прогресса (ТЗ 1.1 §4) ───────────
    fun beginScrub() {
        val s = _state.value
        if (s.isActive && !s.finished && !s.tailActive) {
            voice.pause()
            _state.update { it.copy(scrubbing = true) }
        }
    }

    fun scrubTo(fraction: Float) {
        val s = _state.value
        if (!s.isActive || s.tailActive || s.durationMs <= 0L) return
        val target = (fraction.coerceIn(0f, 1f) * s.durationMs).toLong()
        voice.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun endScrub() {
        _state.update { it.copy(scrubbing = false) }
        val p = _state.value
        if (p.isActive && p.phase != PlayerPhase.PAUSED) voice.play()
    }

    /** Тумблер «Уснуть под фон». Применяется при завершении голоса. */
    fun setSleepUnderBackground(on: Boolean) {
        _state.update { if (it.tailActive) it else it.copy(sleepUnderBg = on) }
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
