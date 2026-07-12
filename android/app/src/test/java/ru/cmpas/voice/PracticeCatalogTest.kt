package ru.cmpas.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.cmpas.voice.data.CatalogLoader
import ru.cmpas.voice.data.Practice
import ru.cmpas.voice.data.PracticeCatalog
import ru.cmpas.voice.data.PracticeGroup
import ru.cmpas.voice.data.SoundFamily

/**
 * ТЗ §3.5 + аудио v2: манифест catalog.json — источник истины. Проверяем инвариант
 * sleep⇔угасание/нет «после», соответствие семьи группе и целостность длительностей.
 * Каталог парсится из test-ресурса (JVM, без Android-контекста); tailAvailable=true
 * (в тесте опция «20» видна — фильтрация по ассетам проверяется отдельно на устройстве).
 */
class PracticeCatalogTest {

    private val practices: List<Practice> by lazy {
        val json = requireNotNull(javaClass.classLoader?.getResourceAsStream("catalog.json"))
            .bufferedReader().use { it.readText() }
        CatalogLoader.parse(json) { true }
    }

    @Test
    fun manifest_hasNinePractices() {
        assertEquals(9, practices.size)
    }

    @Test
    fun sleepFamily_iff_isSleep() {
        practices.forEach { p ->
            val sleep = p.soundFamily == SoundFamily.SLEEP
            assertEquals(
                "«${p.title}»: семья sleep ⇔ isSleep (угасание, без экрана «после»)",
                sleep, p.isSleep,
            )
        }
    }

    @Test
    fun soundFamily_matchesGroup_forAll() {
        practices.forEach { p ->
            assertEquals(
                "«${p.title}»: звуковая семья должна следовать из группы отображения",
                PracticeCatalog.familyOf(p.group), p.soundFamily,
            )
        }
    }

    @Test
    fun nightMeetings_belongsToSleep_notExitDay() {
        val p = requireNotNull(practices.firstOrNull { it.id == "night-meetings" })
        assertEquals(PracticeGroup.SLEEP, p.group)
        assertEquals(SoundFamily.SLEEP, p.soundFamily)
        assertTrue(p.isSleep)
    }

    @Test
    fun noFiveMinuteOption_anywhere() {
        practices.flatMap { it.durations }.forEach { d ->
            assertFalse("5-минутной опции быть не должно (label=${d.label})", d.label == "5")
        }
    }

    @Test
    fun everyDuration_hasVoiceFile() {
        practices.flatMap { it.durations }.forEach { d ->
            assertTrue("Длительность ${d.label} без voiceFile", d.voiceFile.endsWith(".opus"))
            assertTrue("sec должен быть > 0 (${d.label})", d.sec > 0)
        }
    }

    @Test
    fun sosPractice_isFree_andSos() {
        val p = requireNotNull(practices.firstOrNull { it.id == "not-now" })
        assertTrue(p.isFree)
        assertTrue(p.isSos)
    }

    @Test
    fun singleAndMultiOption_practices() {
        // 3 практики с блоком ТШ имеют >1 опции; остальные — ровно 1.
        val multi = practices.filter { it.durations.size > 1 }.map { it.id }.toSet()
        assertEquals(setOf("night-meetings", "workday-over", "inner-anchor"), multi)
    }
}
