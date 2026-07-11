package ru.cmpas.voice.data

/**
 * Фиче-флаги аудио-слоя (см. инструкцию аудио §1).
 *
 * - [softBackground] — «Мягкий фон» (фоновые петли). Часть MVP → ON.
 * - [spatialAudio]   — бинауральный слой + опция «Бинауральный» в UI.
 *   ON: показывается третий сегмент «Бинауральный» (шит) и опция в профиле;
 *   бинауральный слой играет поверх фона ТОЛЬКО в наушниках/BT-стерео.
 */
object FeatureFlags {
    const val softBackground: Boolean = true
    const val spatialAudio: Boolean = true
}
