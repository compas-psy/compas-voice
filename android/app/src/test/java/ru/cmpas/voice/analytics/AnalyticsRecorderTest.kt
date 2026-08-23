package ru.cmpas.voice.analytics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
