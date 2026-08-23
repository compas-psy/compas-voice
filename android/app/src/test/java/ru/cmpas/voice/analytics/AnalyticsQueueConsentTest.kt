package ru.cmpas.voice.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

/**
 * E-M1: [pendingRevocationAfterConsentChange] — сестра [queueAfterConsentChange]
 * для однослотового «кармана» отложенного отзыва (LocalStore.setAnalyticsConsent
 * применяет обе в одной транзакции DataStore, см. следующий коммит).
 */
class AnalyticsPendingRevocationTest {

    @Test
    fun revokingConsent_setsPendingRevocationToTheNewEvent() {
        val result = pendingRevocationAfterConsentChange(granted = false, newRevocationEvent = "revoke-json")
        assertEquals("revoke-json", result)
    }

    @Test
    fun grantingConsent_clearsAnyPendingRevocation() {
        // Даже если вызывающий код по ошибке передаст сюда какой-то JSON —
        // при выдаче согласия карман обязан обнулиться: старый отзыв к
        // этому моменту устарел (см. комментарий функции).
        val result = pendingRevocationAfterConsentChange(granted = true, newRevocationEvent = "stale-revoke-json")
        assertNull(result)
    }

    @Test
    fun grantingConsent_withNoPendingRevocation_staysNull() {
        val result = pendingRevocationAfterConsentChange(granted = true, newRevocationEvent = null)
        assertNull(result)
    }

    /**
     * Оборонительный случай: buildConsentRevokedEvent() возвращает null,
     * только если "consent_updated" вдруг выпадет из EVENT_SCHEMA (не
     * должно случиться, см. AnalyticsSchema) — но если случится, карман не
     * должен получить null вместо реального события молча подменённым на
     * что-то иное; он остаётся null, что корректно отражает "отправлять
     * нечего".
     */
    @Test
    fun revokingConsent_withoutBuiltEvent_pendingStaysNull() {
        val result = pendingRevocationAfterConsentChange(granted = false, newRevocationEvent = null)
        assertNull(result)
    }
}
