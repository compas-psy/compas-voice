package ru.cmpas.voice.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Загрузка каталога из `assets/catalog.json` (манифест v2 — источник истины, не
 * хардкод). `parse()` — чистая (для юнит-тестов), `load()` — с ассетами и
 * скрытием опции «20» (`tail:true`), пока в бандле нет музыкального хвоста семьи.
 */
object CatalogLoader {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class RawCatalog(val practices: List<RawPractice> = emptyList())

    @Serializable
    private data class RawPractice(
        val id: String,
        val title: String,
        val group: String,
        val state: String = "",
        val soundFamily: String,
        val isFree: Boolean = false,
        val isSOS: Boolean = false,
        val sleepFade: Boolean = false,
        val durations: List<RawDuration> = emptyList(),
    )

    @Serializable
    private data class RawDuration(
        val label: String,
        val sec: Int,
        val voiceFile: String,
        val tail: Boolean = false,
    )

    /** Чистый парсинг манифеста; [tailAvailable] решает, показывать ли опцию «20». */
    fun parse(jsonText: String, tailAvailable: (family: String) -> Boolean): List<Practice> {
        val raw = json.decodeFromString<RawCatalog>(jsonText)
        return raw.practices.map { rp ->
            val options = rp.durations
                .filter { !it.tail || tailAvailable(rp.soundFamily) }
                .map { DurationOption(it.label, it.sec, it.voiceFile, it.tail) }
            Practice(
                id = rp.id,
                title = rp.title,
                group = groupOf(rp.group),
                stateCaption = rp.state,
                soundFamily = familyOf(rp.soundFamily),
                durations = options,
                isSleep = rp.sleepFade,
                isFree = rp.isFree,
                isSos = rp.isSOS,
            )
        }
    }

    fun load(context: Context): List<Practice> {
        val text = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        return parse(text) { family -> assetExists(context, "background/bg_${family}_tail.opus") }
    }

    private fun assetExists(context: Context, path: String): Boolean =
        runCatching { context.assets.open(path).close(); true }.getOrDefault(false)

    fun groupOf(s: String): PracticeGroup = when (s) {
        "Сон" -> PracticeGroup.SLEEP
        "Выход из дня" -> PracticeGroup.EXIT_DAY
        "Тревога и перегрузка" -> PracticeGroup.ANXIETY
        "Разговоры и решения" -> PracticeGroup.TALKS
        "Опора" -> PracticeGroup.SUPPORT
        else -> PracticeGroup.ANXIETY
    }

    fun familyOf(s: String): SoundFamily = when (s) {
        "sleep" -> SoundFamily.SLEEP
        "transition" -> SoundFamily.TRANSITION
        "grounding" -> SoundFamily.GROUNDING
        "anchor" -> SoundFamily.ANCHOR
        else -> SoundFamily.GROUNDING
    }
}
