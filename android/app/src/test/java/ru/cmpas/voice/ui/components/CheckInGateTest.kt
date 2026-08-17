package ru.cmpas.voice.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `B-260817-03`: чек-ин самочувствия — потенциально данные о здоровье (`docs/PRIVACY-DPO.md`),
 * пишется только по явному касанию слайдера. Падает, если значение записывается без
 * взаимодействия пользователя.
 */
class CheckInGateTest {

    @Test
    fun notTouched_recordsNull_regardlessOfSliderValue() {
        assertNull(checkInToRecord(value = 5, touched = false))
        assertNull(checkInToRecord(value = 0, touched = false))
        assertNull(checkInToRecord(value = 10, touched = false))
    }

    @Test
    fun touched_recordsSliderValue() {
        assertEquals(7, checkInToRecord(value = 7, touched = true))
    }
}
