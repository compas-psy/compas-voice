package ru.cmpas.voice.data

/**
 * Фиче-флаги аудио-слоя (см. инструкцию аудио §1).
 *
 * - [softBackground] — «Мягкий фон» (фоновые петли). Часть MVP → ON.
 * - [spatialAudio]   — бинауральный слой + опция «Бинауральный» в UI.
 *   Инфраструктура готова, но в MVP **OFF**: третий сегмент/строка настроек
 *   не показываются, бинауральный плеер не создаётся. Включаем в V1.5.
 */
object FeatureFlags {
    const val softBackground: Boolean = true
    const val spatialAudio: Boolean = false
}
