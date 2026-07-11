package ru.cmpas.voice.data

/**
 * Каталог практик и маппинг плиток Дома → практики (см. docs/PRODUCT.md §10).
 * Вынесен в данные, чтобы менять без правки экранов. Аудио поставляется
 * отдельно — здесь пока метаданные (audioRawResId=0, audioUrl=null).
 */
object PracticeCatalog {

    /** Семья фоновых петель по группе состояния (см. инструкцию аудио §2). */
    fun familyOf(group: PracticeGroup): SoundFamily = when (group) {
        PracticeGroup.SLEEP -> SoundFamily.SLEEP
        PracticeGroup.EXIT_DAY -> SoundFamily.TRANSITION
        PracticeGroup.ANXIETY -> SoundFamily.GROUNDING
        PracticeGroup.TALKS -> SoundFamily.GROUNDING
        PracticeGroup.SUPPORT -> SoundFamily.ANCHOR
    }

    val practices: List<Practice> = listOf(
        // ── Сон ──────────────────────────────────────────────
        Practice(
            id = "sleep_descent",
            title = "Медленное погружение",
            group = PracticeGroup.SLEEP,
            stateCaption = "Не получается уснуть",
            isSleep = true,
        ),
        Practice(
            id = "sleep_return",
            title = "Вернуться в сон",
            group = PracticeGroup.SLEEP,
            stateCaption = "Проснулся среди ночи",
            isSleep = true,
        ),
        // ── Выход из дня ─────────────────────────────────────
        Practice(
            id = "exit_meetings",
            title = "Ночь без внутренних совещаний",
            group = PracticeGroup.EXIT_DAY,
            stateCaption = "Голова продолжает работать дома",
        ),
        Practice(
            id = "exit_slow",
            title = "Сбросить обороты",
            group = PracticeGroup.EXIT_DAY,
            stateCaption = "Слишком много всего за день",
        ),
        // ── Тревога и перегрузка ─────────────────────────────
        Practice(
            id = "anx_carousel",
            title = "Остановить карусель мыслей",
            group = PracticeGroup.ANXIETY,
            stateCaption = "Прокручиваю одно и то же",
        ),
        Practice(
            id = "anx_dark",
            title = "Тихо в темноте",
            group = PracticeGroup.ANXIETY,
            stateCaption = "Тревожно в темноте",
        ),
        // ── Разговоры и решения ──────────────────────────────
        Practice(
            id = "talk_before",
            title = "Перед трудным разговором",
            group = PracticeGroup.TALKS,
            stateCaption = "Нужно поговорить, но тяжело",
        ),
        // ── Опора ────────────────────────────────────────────
        Practice(
            id = "support_ground",
            title = "Найти землю под ногами",
            group = PracticeGroup.SUPPORT,
            stateCaption = "Как будто потерял опору",
        ),
        Practice(
            id = "support_start",
            title = "Вернуть опору",
            group = PracticeGroup.SUPPORT,
            stateCaption = "Не знаю, за что взяться",
        ),
    ).map { it.copy(soundFamily = familyOf(it.group)) }

    private val byId = practices.associateBy { it.id }

    fun byId(id: String): Practice? = byId[id]

    /** Практики, сгруппированные по состояниям (порядок групп — как в ТЗ). */
    fun grouped(): List<Pair<PracticeGroup, List<Practice>>> =
        PracticeGroup.entries
            .map { g -> g to practices.filter { it.group == g } }
            .filter { it.second.isNotEmpty() }

    // ── Плитки Дома по времени суток ────────────────────────
    val dayTiles = listOf(
        HomeTile("слишком много всего", "Перегружен(а)", "exit_slow"),
        HomeTile("нет начала", "Не знаю, за что взяться", "support_start"),
        HomeTile("предстоит", "Трудный разговор", "talk_before"),
        HomeTile("как будто без почвы", "Потерял опору", "support_ground"),
    )

    val eveningTiles = listOf(
        HomeTile("мысли про работу", "Работа не отпускает", "exit_meetings"),
        HomeTile("по кругу", "Прокручиваю мысли", "anx_carousel"),
        HomeTile("не выходит", "Не могу уснуть", "sleep_descent"),
        HomeTile("слишком много всего", "Перегружен(а)", "exit_slow"),
        HomeTile("предстоит", "Трудный разговор", "talk_before", wide = true),
    )

    val nightTiles = listOf(
        HomeTile("", "Не могу уснуть", "sleep_descent"),
        HomeTile("", "Проснулся среди ночи", "sleep_return"),
        HomeTile("", "Тревожно в темноте", "anx_dark"),
    )

    fun tilesFor(time: TimeOfDay): List<HomeTile> = when (time) {
        TimeOfDay.DAY -> dayTiles
        TimeOfDay.EVENING -> eveningTiles
        TimeOfDay.NIGHT -> nightTiles
    }
}
