package ru.cmpas.voice.audio

import ru.cmpas.voice.R
import ru.cmpas.voice.data.SoundFamily

/**
 * Пулы фоновых петель и бинауральных стемов по семьям.
 * Петли — нормализованные OGG (−30 LUFS, бесшовный кроссфейд в стык, 48 кГц).
 * Бинауралка — FLAC (без обработки, бесшовные синусы), играет только за флагом
 * spatialAudio (OFF в MVP).
 *
 * ⚠️ anchor: прислано 3 петли (ANCHOR_4 отсутствует) — LRU крутит по 3.
 */
object SoundLibrary {

    /** Пул петель «Мягкого фона» по семье (LRU-выбор при старте). */
    fun loops(family: SoundFamily): List<Int> = when (family) {
        SoundFamily.SLEEP -> listOf(R.raw.bg_sleep_1, R.raw.bg_sleep_2, R.raw.bg_sleep_3, R.raw.bg_sleep_4)
        SoundFamily.GROUNDING -> listOf(R.raw.bg_grounding_1, R.raw.bg_grounding_2, R.raw.bg_grounding_3, R.raw.bg_grounding_4)
        SoundFamily.TRANSITION -> listOf(R.raw.bg_transition_1, R.raw.bg_transition_2, R.raw.bg_transition_3, R.raw.bg_transition_4)
        SoundFamily.ANCHOR -> listOf(R.raw.bg_anchor_1, R.raw.bg_anchor_2, R.raw.bg_anchor_3)
    }

    /** Сонные «хвосты» — для угасания после голоса (пока — задел, см. ROADMAP). */
    fun sleepTails(): List<Int> = listOf(
        R.raw.bg_sleeptail_1, R.raw.bg_sleeptail_2, R.raw.bg_sleeptail_3, R.raw.bg_sleeptail_4,
    )

    /** Бинауральный стем семьи (за флагом spatialAudio). */
    fun binaural(family: SoundFamily): Int = when (family) {
        SoundFamily.SLEEP -> R.raw.bin_sleep_theta
        SoundFamily.GROUNDING -> R.raw.bin_grounding_alpha
        SoundFamily.TRANSITION -> R.raw.bin_transition_alpha
        SoundFamily.ANCHOR -> R.raw.bin_anchor_theta
    }

    /** Дельта-хвост для сонного перехода тета→дельта (за флагом spatialAudio). */
    fun binauralSleepTail(): Int = R.raw.bin_sleeptail_delta
}
