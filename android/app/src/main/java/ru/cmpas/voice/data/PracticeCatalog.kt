package ru.cmpas.voice.data

/**
 * Каталог практик и маппинг плиток Дома → практики.
 * Семья (`soundFamily`) выводится из группы состояния и задаёт КАСКАД поведения:
 * оттенок и реакция фона плеера, сонное угасание vs финальный экран, звуковая
 * семья, ночной режим (ТЗ 1.1 §3.5). Эталонная таблица из 9 практик.
 * Аудио голоса поставляется отдельно — здесь метаданные.
 */
object PracticeCatalog {

    /** Семья фоновых петель/поведения по группе состояния (ТЗ §3.5). */
    fun familyOf(group: PracticeGroup): SoundFamily = when (group) {
        PracticeGroup.SLEEP -> SoundFamily.SLEEP
        PracticeGroup.EXIT_DAY -> SoundFamily.TRANSITION
        PracticeGroup.ANXIETY -> SoundFamily.GROUNDING
        PracticeGroup.TALKS -> SoundFamily.GROUNDING   // Разговоры: показ ochre, звук grounding
        PracticeGroup.SUPPORT -> SoundFamily.ANCHOR
    }

    val practices: List<Practice> = listOf(
        // ── Сон (family sleep: угасание, ночной режим, indigo, НЕТ «после») ──
        Practice("sleep_meetings", "Ночь без внутренних совещаний", PracticeGroup.SLEEP,
            "Голова продолжает работать дома", isSleep = true),
        Practice("sleep_wake", "Проснулся среди ночи", PracticeGroup.SLEEP,
            "Проснулся и не выходит заснуть", isSleep = true),
        Practice("sleep_dark", "Тревожно в темноте", PracticeGroup.SLEEP,
            "Тревожно засыпать в темноте", isSleep = true),

        // ── Выход из дня (family transition) ──
        Practice("exit_workday", "Рабочий день закончен", PracticeGroup.EXIT_DAY,
            "Работа не отпускает вечером"),
        Practice("exit_sunday", "Воскресенье, около семи", PracticeGroup.EXIT_DAY,
            "Вечер перед новой неделей"),

        // ── Тревога и перегрузка (family grounding) ──
        Practice("calm_sos", "Не нужно решать всё сейчас", PracticeGroup.ANXIETY,
            "Слишком много всего", durations = listOf(5, 12, 20), isFree = true), // SOS-слот
        Practice("calm_spin", "Прокручиваю мысли", PracticeGroup.ANXIETY,
            "Мысли крутятся по кругу"),

        // ── Разговоры и решения (показ ochre, звук grounding) ──
        Practice("talk_before", "Перед сложным разговором", PracticeGroup.TALKS,
            "Нужно поговорить, но тяжело"),

        // ── Опора (family anchor) ──
        Practice("anchor_inner", "Внутренняя опора", PracticeGroup.SUPPORT,
            "Как будто потерял опору"),
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
        HomeTile("слишком много всего", "Перегружен(а)", "calm_sos"),
        HomeTile("нет начала", "Не знаю, за что взяться", "anchor_inner"),
        HomeTile("предстоит", "Трудный разговор", "talk_before"),
        HomeTile("как будто без почвы", "Потерял опору", "anchor_inner"),
    )

    val eveningTiles = listOf(
        HomeTile("мысли про работу", "Работа не отпускает", "exit_workday"),
        HomeTile("по кругу", "Прокручиваю мысли", "calm_spin"),
        HomeTile("не выходит", "Не могу уснуть", "sleep_meetings"),
        HomeTile("слишком много всего", "Перегружен(а)", "calm_sos"),
        HomeTile("предстоит", "Трудный разговор", "talk_before", wide = true),
    )

    val nightTiles = listOf(
        HomeTile("", "Не могу уснуть", "sleep_meetings"),
        HomeTile("", "Проснулся среди ночи", "sleep_wake"),
        HomeTile("", "Тревожно в темноте", "sleep_dark"),
    )

    fun tilesFor(time: TimeOfDay): List<HomeTile> = when (time) {
        TimeOfDay.DAY -> dayTiles
        TimeOfDay.EVENING -> eveningTiles
        TimeOfDay.NIGHT -> nightTiles
    }
}
