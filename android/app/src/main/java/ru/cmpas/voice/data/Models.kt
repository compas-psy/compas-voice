package ru.cmpas.voice.data

import kotlinx.serialization.Serializable

/** Время суток — из системных часов. Границы задаём явно (см. QA §3). */
enum class TimeOfDay {
    DAY, EVENING, NIGHT;

    companion object {
        fun of(hour: Int): TimeOfDay = when (hour) {
            in 5..17 -> DAY      // 05:00–17:59
            in 18..22 -> EVENING // 18:00–22:59
            else -> NIGHT        // 23:00–04:59
        }
    }
}

/** Группы каталога — состояния. */
enum class PracticeGroup(val title: String) {
    SLEEP("Сон"),
    EXIT_DAY("Выход из дня"),
    ANXIETY("Тревога и перегрузка"),
    TALKS("Разговоры и решения"),
    SUPPORT("Опора"),
}

/** Фон практики.
 *  VOICE — только голос; SOFT — мягкий фон (петля семьи);
 *  BINAURAL — стерео-фон + бинауральный слой (за флагом spatialAudio, OFF в MVP). */
enum class Background(val title: String) {
    VOICE("Только голос"),
    SOFT("Мягкий фон"),
    BINAURAL("Объёмный"), // ТЗ 1.1 §3.1: в UI «Объёмный»/«Объём», не «бинауральный»
}

/** Семья фоновых петель (пул вариаций одного фона, не выбор пользователю). */
enum class SoundFamily { SLEEP, GROUNDING, TRANSITION, ANCHOR }

/** Практика каталога. */
data class Practice(
    val id: String,
    val title: String,           // название-сцена, напр. «Ночь без внутренних совещаний»
    val group: PracticeGroup,
    val stateCaption: String,    // одна строка состояния, напр. «Голова продолжает работать дома»
    val durations: List<Int> = listOf(5, 12, 20),
    val soundFamily: SoundFamily = SoundFamily.GROUNDING, // семья фоновых петель
    val isSleep: Boolean = false, // сонные практики: ночной режим + угасание, без итога
    val isFree: Boolean = false,  // SOS-практика: открыта без подписки всегда (гейтинг §5)
    /** Ресурс res/raw (0 = нет) или URL. В MVP аудио поставляется отдельно. */
    val audioRawResId: Int = 0,
    val audioUrl: String? = null,
)

/** Плитка на Доме → ведёт к конкретной практике. */
data class HomeTile(
    val caption: String,   // мелкая серая подпись сверху («слишком много всего»)
    val title: String,     // название плитки снизу («Перегружен(а)»)
    val practiceId: String,
    val wide: Boolean = false,
)

/** Конфигурация сессии из шита. */
data class SessionConfig(
    val duration: Int = 12,
    val background: Background = Background.VOICE,
)

/** Одна запись истории прослушивания (хранится локально). */
@Serializable
data class HistoryEntry(
    val practiceId: String,
    val title: String,
    val group: String,          // PracticeGroup.name
    val durationMin: Int,
    val startedAtEpochMs: Long,
    val checkInBefore: Int? = null,
    val checkInAfter: Int? = null,
    val isSleep: Boolean = false,
)

/** Статус подписки (в MVP — локальная эмуляция, без реального биллинга). */
enum class SubscriptionStatus { FREE, TRIAL, ACTIVE }

/** Настройки пользователя. */
data class Settings(
    val defaultBackground: Background = Background.VOICE,
    val remindersEnabled: Boolean = false,           // ВЫКЛ по умолчанию
    val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE,
)

/** Состояние плеера. */
enum class PlayerPhase { PLAYING, PAUSED, NIGHT, FADING }
