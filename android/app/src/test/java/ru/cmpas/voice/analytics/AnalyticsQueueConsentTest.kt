package ru.cmpas.voice.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * О-260817-14: «отзыв согласия стирает очередь немедленно». [LocalStore.setAnalyticsConsent]
 * применяет [queueAfterConsentChange] внутри одной транзакции DataStore — здесь
 * проверяется сама чистая функция.
 */
class AnalyticsQueueConsentTest {

    @Test
    fun revokingConsent_wipesQueue() {
        val result = queueAfterConsentChange(listOf("a", "b", "c"), granted = false)
        assertTrue(result.isEmpty())
    }

    @Test
    fun grantingConsent_leavesQueueUntouched() {
        val result = queueAfterConsentChange(listOf("a", "b"), granted = true)
        assertEquals(listOf("a", "b"), result)
    }
}
