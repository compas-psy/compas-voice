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

/**
 * Переполнение очереди (B-260823): `takeLast(cap)` выбрасывал САМОЕ СТАРОЕ
 * событие — то есть `consent_updated`, которым приёмник ставит согласие
 * устройства. Пока приёмник отвечает 401, очередь растёт, и на cap+1 согласие
 * исчезало навсегда: дальше приёмник отвергает всё как «consent required»,
 * flush() упирается в первое событие и очередь не двигается уже никогда.
 */
class AnalyticsQueueOverflowTest {

    private fun consent(granted: Boolean = true) =
        """{"event":"consent_updated","ts":"1970-01-01T00:00:00Z","product":"moments","props":{"granted":$granted}}"""

    private fun practice(i: Int) =
        """{"event":"practice_started","ts":"1970-01-01T00:00:0${i % 10}Z","product":"moments","props":{}}"""

    @Test
    fun belowCap_keepsEverythingInOrder() {
        val q = listOf(consent(), practice(1))
        val result = analyticsQueueAfterEnqueue(q, practice(2), cap = 10)
        assertEquals(listOf(consent(), practice(1), practice(2)), result)
    }

    @Test
    fun overflow_keepsConsentAndDropsOldestContent() {
        val cap = 5
        // очередь уже полна: согласие + 4 содержательных
        val full = listOf(consent()) + (1..4).map { practice(it) }
        val result = analyticsQueueAfterEnqueue(full, practice(9), cap)

        assertEquals(cap, result.size)
        assertTrue("согласие обязано пережить переполнение", result.any { isConsentEventJson(it) })
        assertEquals("согласие обязано остаться первым", true, isConsentEventJson(result.first()))
        assertTrue("новое событие должно попасть в очередь", result.contains(practice(9)))
    }

    /** Старое поведение (takeLast) на этом же входе теряло согласие — фиксируем разницу. */
    @Test
    fun overflow_oldTakeLastWouldHaveLostConsent_newDoesNot() {
        val cap = 3
        val full = listOf(consent(), practice(1), practice(2))
        val oldBehaviour = (full + practice(3)).takeLast(cap)
        assertTrue("контроль: takeLast действительно терял согласие", oldBehaviour.none { isConsentEventJson(it) })

        val result = analyticsQueueAfterEnqueue(full, practice(3), cap)
        assertTrue("новое поведение согласие сохраняет", result.any { isConsentEventJson(it) })
        assertEquals(cap, result.size)
    }

    @Test
    fun overflow_withManyConsentEvents_neverDropsThem() {
        val cap = 2
        val q = listOf(consent(true), consent(false))
        val result = analyticsQueueAfterEnqueue(q, practice(1), cap)
        assertEquals(2, result.count { isConsentEventJson(it) })
    }

    @Test
    fun isConsentEventJson_recognisesOnlyConsentEnvelope() {
        assertTrue(isConsentEventJson(consent()))
        assertTrue(!isConsentEventJson(practice(1)))
    }
}
