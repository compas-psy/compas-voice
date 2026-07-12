package ru.cmpas.voice.data

import android.content.Context

/**
 * Каталог практик — наполняется из `assets/catalog.json` при старте
 * (`init`, см. KompasApp). Плитки Дома — UI-слой, ссылаются на id манифеста.
 */
object PracticeCatalog {

    private var loaded: List<Practice> = emptyList()
    val practices: List<Practice> get() = loaded

    fun init(context: Context) {
        if (loaded.isEmpty()) loaded = CatalogLoader.load(context)
    }

    fun byId(id: String): Practice? = loaded.firstOrNull { it.id == id }

    /** Практики, сгруппированные по состояниям (порядок групп — как в ТЗ). */
    fun grouped(): List<Pair<PracticeGroup, List<Practice>>> =
        PracticeGroup.entries
            .map { g -> g to loaded.filter { it.group == g } }
            .filter { it.second.isNotEmpty() }

    /** Каноническое соответствие группа отображения → звуковая семья (сверка §3.5). */
    fun familyOf(group: PracticeGroup): SoundFamily = when (group) {
        PracticeGroup.SLEEP -> SoundFamily.SLEEP
        PracticeGroup.EXIT_DAY -> SoundFamily.TRANSITION
        PracticeGroup.ANXIETY -> SoundFamily.GROUNDING
        PracticeGroup.TALKS -> SoundFamily.GROUNDING
        PracticeGroup.SUPPORT -> SoundFamily.ANCHOR
    }

    // ── Плитки Дома по времени суток (id из catalog.json) ────
    val dayTiles = listOf(
        HomeTile("слишком много всего", "Перегружен(а)", "not-now"),
        HomeTile("нет начала", "Не знаю, за что взяться", "inner-anchor"),
        HomeTile("предстоит", "Трудный разговор", "hard-conversation"),
        HomeTile("как будто без почвы", "Потерял опору", "inner-anchor"),
    )

    val eveningTiles = listOf(
        HomeTile("мысли про работу", "Работа не отпускает", "workday-over"),
        HomeTile("по кругу", "Прокручиваю мысли", "looping-thoughts"),
        HomeTile("не выходит", "Не могу уснуть", "night-meetings"),
        HomeTile("слишком много всего", "Перегружен(а)", "not-now"),
        HomeTile("предстоит", "Трудный разговор", "hard-conversation", wide = true),
    )

    val nightTiles = listOf(
        HomeTile("", "Не могу уснуть", "night-meetings"),
        HomeTile("", "Проснулся среди ночи", "woke-up-night"),
        HomeTile("", "Тревожно в темноте", "anxious-dark"),
    )

    fun tilesFor(time: TimeOfDay): List<HomeTile> = when (time) {
        TimeOfDay.DAY -> dayTiles
        TimeOfDay.EVENING -> eveningTiles
        TimeOfDay.NIGHT -> nightTiles
    }
}
