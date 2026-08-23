package ru.cmpas.voice.analytics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * О-260817-06: «до согласия события не отправляются вовсе» — здесь это
 * означает, что [AnalyticsRecorder] ни разу не вызывает enqueue, пока
 * согласие не дано. Проверяем на всех четырёх событиях сразу, включая
 * crossed_to_product и practice_finished, которые не проходят одну общую
 * тестовую точку.
 */
class AnalyticsRecorderTest {

    @Test
    fun withoutConsent_neverEnqueuesAnyEvent() = runBlocking {
        var enqueueCalls = 0
        val recorder = AnalyticsRecorder(
            isConsentGranted = { false },
            enqueue = { enqueueCalls++ },
            deviceId = { "dev" },
        )

        recorder.recordAppInstalled(0L)
        recorder.recordPracticeStarted("p1", "SLEEP", true)
        recorder.recordPracticeFinished("p1", "SLEEP", true, 100)
        recorder.recordCrossedToProduct("practice")

        assertEquals(0, enqueueCalls)
    }

    @Test
    fun withConsent_enqueuesValidEventJson() = runBlocking {
        val enqueued = mutableListOf<String>()
        val recorder = AnalyticsRecorder(
            isConsentGranted = { true },
            enqueue = { enqueued.add(it) },
            deviceId = { "dev-42" },
        )

        recorder.recordPracticeStarted("sleep_deep_dive", "SLEEP", true)

        assertEquals(1, enqueued.size)
        assertTrue(enqueued[0].contains("\"practice_started\""))
        assertTrue(enqueued[0].contains("dev-42"))
    }

    @Test
    fun completionPct_isClampedTo0_100() = runBlocking {
        val enqueued = mutableListOf<String>()
        val recorder = AnalyticsRecorder(
            isConsentGranted = { true },
            enqueue = { enqueued.add(it) },
            deviceId = { "dev" },
        )

        recorder.recordPracticeFinished("p1", "SLEEP", true, 140)

        assertTrue(enqueued[0].contains("\"completion_pct\":100"))
    }

    /**
     * Поток D: ключ идемпотентности приёмника (`event_id`) должен реально
     * доехать от [AnalyticsRecorder] до конверта — не только существовать в
     * [buildAnalyticsEvent] как параметр, который никто не передаёт.
     */
    @Test
    fun eventId_isThreadedIntoEnqueuedEnvelope() = runBlocking {
        val enqueued = mutableListOf<String>()
        val recorder = AnalyticsRecorder(
            isConsentGranted = { true },
            enqueue = { enqueued.add(it) },
            deviceId = { "dev" },
            eventId = { "fixed-event-id-42" },
        )

        recorder.recordAppInstalled(0L)

        assertTrue(enqueued[0].contains("\"event_id\":\"fixed-event-id-42\""))
    }

    /**
     * Без явно переданного [AnalyticsRecorder.eventId] (продовое поведение,
     * `AppContainer` его не переопределяет) каждая запись получает новый
     * идентификатор — иначе повторные вызовы одного и того же события
     * дедуплицировались бы приёмником как один и тот же `event_id`.
     */
    @Test
    fun eventId_defaultsToDistinctValuePerRecord() = runBlocking {
        val enqueued = mutableListOf<String>()
        val recorder = AnalyticsRecorder(
            isConsentGranted = { true },
            enqueue = { enqueued.add(it) },
            deviceId = { "dev" },
        )

        recorder.recordAppInstalled(0L)
        recorder.recordAppInstalled(0L)

        val ids = enqueued.map { json ->
            Regex("\"event_id\":\"([^\"]+)\"").find(json)!!.groupValues[1]
        }
        assertEquals(2, ids.size)
        assertTrue("два разных вызова получили один и тот же event_id: $ids", ids[0] != ids[1])
    }

    /**
     * E-M1: выдача согласия обязана оказаться в очереди раньше любого
     * содержательного события — приёмник (`writeDeviceOnlyEvent`) отвергает
     * события устройства без предварительно поставленного
     * AnalyticsDeviceConsent, а ставит его именно обработчик
     * "consent_updated". AnalyticsRecorder сам порядок не гарантирует и не
     * обязан — каждый record* синхронно (относительно enqueue) добавляет
     * ровно одно событие, так что порядок вызовов = порядок в очереди; это
     * доказывает, что вызов recordConsentUpdated(true) перед остальными
     * record*-вызовами (как это делают KompasRoot/ProfileScreen) даёт
     * нужный порядок в самой очереди.
     */
    @Test
    fun consentUpdated_calledFirst_precedesSubsequentContentEvents() = runBlocking {
        val enqueued = mutableListOf<String>()
        val recorder = AnalyticsRecorder(
            isConsentGranted = { true },
            enqueue = { enqueued.add(it) },
            deviceId = { "dev" },
        )

        recorder.recordConsentUpdated(true)
        recorder.recordAppInstalled(0L)

        assertEquals(2, enqueued.size)
        assertTrue("consent_updated должен быть первым в очереди", enqueued[0].contains("\"consent_updated\""))
        assertTrue(enqueued[0].contains("\"granted\":true"))
        assertTrue("app_installed должен идти после согласия", enqueued[1].contains("\"app_installed\""))
    }

    @Test
    fun recordConsentUpdated_grant_enqueuesGrantedTrue() = runBlocking {
        val enqueued = mutableListOf<String>()
        val recorder = AnalyticsRecorder(
            isConsentGranted = { true },
            enqueue = { enqueued.add(it) },
            deviceId = { "dev" },
        )

        recorder.recordConsentUpdated(true)

        assertEquals(1, enqueued.size)
        assertTrue(enqueued[0].contains("\"consent_updated\""))
        assertTrue(enqueued[0].contains("\"granted\":true"))
    }

    /**
     * Оборонительный случай, не продовый путь: для отзыва есть отдельный
     * [AnalyticsRecorder.buildConsentRevokedEvent], вызывающий код никогда
     * не зовёт recordConsentUpdated(false). Если бы кто-то всё же позвал —
     * [record] отказывает ему так же, как любому другому событию; обход
     * проверки согласия есть только в buildConsentRevokedEvent, не здесь.
     */
    @Test
    fun recordConsentUpdated_withoutConsent_isNoOp() = runBlocking {
        var enqueueCalls = 0
        val recorder = AnalyticsRecorder(
            isConsentGranted = { false },
            enqueue = { enqueueCalls++ },
            deviceId = { "dev" },
        )

        recorder.recordConsentUpdated(false)

        assertEquals(0, enqueueCalls)
    }

    /**
     * E-M1, ловушка отзыва: buildConsentRevokedEvent строит конверт мимо
     * [record] — ровно при isConsentGranted == false (момент, когда
     * согласие уже отозвано локально) [record] отказал бы всему, включая
     * само сообщение об отзыве. Метод обязан вернуть непустой JSON именно в
     * этой ситуации и не звать enqueue сам — доставка не через обычную
     * очередь (см. AppContainer.revokeAnalyticsConsent).
     */
    @Test
    fun buildConsentRevokedEvent_bypassesConsentGate_andNeverEnqueuesItself() = runBlocking {
        var enqueueCalls = 0
        val recorder = AnalyticsRecorder(
            isConsentGranted = { false },
            enqueue = { enqueueCalls++ },
            deviceId = { "dev-99" },
            eventId = { "revoke-event-id" },
        )

        val json = recorder.buildConsentRevokedEvent()

        assertNotNull("отзыв обязан построиться, даже когда isConsentGranted лжёт «нет»", json)
        assertTrue(json!!.contains("\"consent_updated\""))
        assertTrue(json.contains("\"granted\":false"))
        assertTrue(json.contains("dev-99"))
        assertTrue(json.contains("revoke-event-id"))
        assertEquals("buildConsentRevokedEvent не должен ставить событие в очередь сам", 0, enqueueCalls)
    }
}
