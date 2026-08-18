package ru.cmpas.voice.analytics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * О-260817-14: очередь МОМЕНТОВ довозится до приёмника ПРАКТИКИ пачками, с
 * повтором при отказе и растущей паузой. Здесь — чистая логика [AnalyticsTransport]
 * поверх фейковой очереди в памяти, без Android/сети.
 */
class AnalyticsTransportTest {

    private class FakeQueue(initial: List<String>) {
        val items = initial.toMutableList()
        fun peek(limit: Int) = items.take(limit)
        fun remove(count: Int) = repeat(count.coerceAtMost(items.size)) { items.removeAt(0) }
    }

    @Test
    fun withoutConsent_neverSendsQueuedEvents() = runBlocking {
        val queue = FakeQueue(listOf("a", "b"))
        var sendCalls = 0
        val transport = AnalyticsTransport(
            isConsentGranted = { false },
            peekQueue = { queue.peek(it) },
            removeSent = { queue.remove(it) },
            sendOne = { sendCalls++; true },
        )

        transport.flush()

        assertEquals(0, sendCalls)
        assertEquals(listOf("a", "b"), queue.items)
    }

    @Test
    fun withConsent_sendsAndDrainsQueueInOrder() = runBlocking {
        val queue = FakeQueue(listOf("a", "b", "c"))
        val sent = mutableListOf<String>()
        val transport = AnalyticsTransport(
            isConsentGranted = { true },
            peekQueue = { queue.peek(it) },
            removeSent = { queue.remove(it) },
            sendOne = { sent.add(it); true },
        )

        transport.flush()

        assertEquals(listOf("a", "b", "c"), sent)
        assertTrue(queue.items.isEmpty())
    }

    @Test
    fun emptyQueue_neverCallsSendOne() = runBlocking {
        val queue = FakeQueue(emptyList())
        var sendCalls = 0
        val transport = AnalyticsTransport(
            isConsentGranted = { true },
            peekQueue = { queue.peek(it) },
            removeSent = { queue.remove(it) },
            sendOne = { sendCalls++; true },
        )

        transport.flush()

        assertEquals(0, sendCalls)
    }

    @Test
    fun partialBatchFailure_removesOnlySuccessfullySentEvents() = runBlocking {
        val queue = FakeQueue(listOf("a", "b", "c"))
        val transport = AnalyticsTransport(
            isConsentGranted = { true },
            peekQueue = { queue.peek(it) },
            removeSent = { queue.remove(it) },
            sendOne = { it != "b" }, // "a" отправляется, "b" — нет, "c" не пробуется
        )

        transport.flush()

        assertEquals(listOf("b", "c"), queue.items)
    }

    @Test
    fun onFailure_backsOffAndDoesNotRetryImmediately() = runBlocking {
        val queue = FakeQueue(listOf("a"))
        var sendCalls = 0
        var clock = 0L
        val transport = AnalyticsTransport(
            isConsentGranted = { true },
            peekQueue = { queue.peek(it) },
            removeSent = { queue.remove(it) },
            sendOne = { sendCalls++; false },
            now = { clock },
        )

        transport.flush()
        assertEquals(1, sendCalls)

        // Тот же момент времени — ещё в паузе, повторной попытки нет.
        transport.flush()
        assertEquals(1, sendCalls)

        // Пауза истекла — пробует снова.
        clock += AnalyticsTransport.INITIAL_BACKOFF_MS
        transport.flush()
        assertEquals(2, sendCalls)
    }

    @Test
    fun repeatedFailures_growBackoffExponentially_thenResetOnSuccess() = runBlocking {
        val queue = FakeQueue(listOf("a"))
        var shouldSucceed = false
        var clock = 0L
        val transport = AnalyticsTransport(
            isConsentGranted = { true },
            peekQueue = { queue.peek(it) },
            removeSent = { queue.remove(it) },
            sendOne = { shouldSucceed },
            now = { clock },
        )

        // Первый отказ: пауза = INITIAL.
        transport.flush()
        clock += AnalyticsTransport.INITIAL_BACKOFF_MS
        // Второй отказ: пауза должна была удвоиться, поэтому одного INITIAL мало.
        transport.flush()
        clock += AnalyticsTransport.INITIAL_BACKOFF_MS
        transport.flush() // всё ещё в увеличенной паузе — очередь не тронута
        assertEquals(listOf("a"), queue.items)

        // Ждём вторую (удвоенную) паузу целиком и разрешаем успех.
        clock += AnalyticsTransport.INITIAL_BACKOFF_MS
        shouldSucceed = true
        transport.flush()
        assertTrue(queue.items.isEmpty())
    }
}
