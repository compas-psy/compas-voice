package ru.cmpas.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.cmpas.voice.data.PracticeCatalog
import ru.cmpas.voice.data.PracticeGroup
import ru.cmpas.voice.data.SoundFamily

/**
 * ТЗ 1.1 §3.5: у каждой практики семейства sleep — сонное угасание и НЕТ экрана
 * «после»; у остальных — есть экран «после» и нет угасания. Плюс сверка всей
 * таблицы family ↔ группа состояния.
 */
class PracticeCatalogTest {

    @Test
    fun sleepFamily_iff_isSleep() {
        PracticeCatalog.practices.forEach { p ->
            val sleep = p.soundFamily == SoundFamily.SLEEP
            assertEquals(
                "«${p.title}»: семья sleep ⇔ isSleep (угасание, без экрана «после»)",
                sleep, p.isSleep,
            )
        }
    }

    @Test
    fun soundFamily_matchesGroup_forAll() {
        PracticeCatalog.practices.forEach { p ->
            assertEquals(
                "«${p.title}»: звуковая семья должна следовать из группы",
                PracticeCatalog.familyOf(p.group), p.soundFamily,
            )
        }
    }

    @Test
    fun nightMeetings_belongsToSleep_notExitDay() {
        val p = requireNotNull(PracticeCatalog.byId("sleep_meetings"))
        assertEquals(PracticeGroup.SLEEP, p.group)
        assertEquals(SoundFamily.SLEEP, p.soundFamily)
        assertTrue(p.isSleep)
    }

    @Test
    fun tilesReferenceExistingPractices() {
        (PracticeCatalog.dayTiles + PracticeCatalog.eveningTiles + PracticeCatalog.nightTiles)
            .forEach { tile ->
                assertTrue(
                    "Плитка «${tile.title}» ссылается на несуществующую практику ${tile.practiceId}",
                    PracticeCatalog.byId(tile.practiceId) != null,
                )
            }
    }

    @Test
    fun sosPractice_isFree() {
        assertTrue(requireNotNull(PracticeCatalog.byId("calm_sos")).isFree)
    }
}
