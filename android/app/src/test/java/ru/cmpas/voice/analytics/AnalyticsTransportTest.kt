package ru.cmpas.voice.analytics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * Поток D, задача D3: приёмник ПРАКТИКИ принимает массив до
     * `MAX_INGEST_BATCH_SIZE = 200` событий за один POST
     * (`src/lib/analytics/ingest.ts` в `compas-psy/cmpas.ru`, число
     * скопировано сюда буквально — сеть до того репозитория здесь не тянем,
     * при расхождении обновлять руками). [AnalyticsTransport] его не
     * приближается к этому пределу структурно: [AnalyticsTransport.BATCH_SIZE]
     * управляет тем, сколько ОТДЕЛЬНЫХ HTTP-запросов (по одному событию
     * каждый, см. сигнатуру [AnalyticsTransport.flush]'а зависимости
     * `sendOne: suspend (String) -> Boolean`) транспорт делает подряд за один
     * проход, а не размером тела одного запроса — тело всегда одно событие.
     */
    @Test
    fun batchSize_staysWithinReceiverServerLimit() {
        val receiverMaxBatchSize = 200
        assertTrue(
            "AnalyticsTransport.BATCH_SIZE (${AnalyticsTransport.BATCH_SIZE}) не должен " +
                "приближаться к лимиту приёмника ($receiverMaxBatchSize) без сознательного решения " +
                "перейти на отправку массивом",
            AnalyticsTransport.BATCH_SIZE <= receiverMaxBatchSize,
        )
    }

    /**
     * sendOne шлёт РОВНО одно событие за вызов — значит тело каждого
     * запроса к приёмнику физически не может превысить лимит в 200 событий
     * за пачку, независимо от размера локальной очереди (500,
     * `LocalStore.enqueueAnalyticsEvent`). Прогоняем полную пачку и считаем
     * вызовы sendOne — каждый получает один JSON-объект, не массив.
     */
    @Test
    fun eachSendOneCall_carriesExactlyOneEvent_neverABatchArray() = runBlocking {
        val queue = FakeQueue((1..AnalyticsTransport.BATCH_SIZE).map { "event-$it" })
        val received = mutableListOf<String>()
        val transport = AnalyticsTransport(
            isConsentGranted = { true },
            peekQueue = { queue.peek(it) },
            removeSent = { queue.remove(it) },
            sendOne = { received.add(it); true },
        )

        transport.flush()

        assertEquals(AnalyticsTransport.BATCH_SIZE, received.size)
        received.forEach { assertFalse("sendOne получил похожее на JSON-массив тело: $it", it.trim().startsWith("[")) }
    }
}

/**
 * [isAnalyticsTransportConfigured] и [isIngestResponseAccepted] — чистые
 * функции конфигурации/разбора ответа транспорта (поток D), вынесены сюда
 * же, чтобы `AppContainer` (Android, без юнит-тестов) оставался тонкой
 * склейкой над уже проверенной логикой.
 */
class AnalyticsTransportConfigTest {

    @Test
    fun configured_whenBothUrlAndSecretPresent() {
        assertTrue(isAnalyticsTransportConfigured("https://cmpas.ru/api/ingest", "topsecret"))
    }

    @Test
    fun notConfigured_whenSecretMissing() {
        assertFalse(isAnalyticsTransportConfigured("https://cmpas.ru/api/ingest", ""))
    }

    @Test
    fun notConfigured_whenUrlMissing() {
        assertFalse(isAnalyticsTransportConfigured("", "topsecret"))
    }

    @Test
    fun notConfigured_whenBothBlank() {
        assertFalse(isAnalyticsTransportConfigured("   ", "   "))
    }
}

class IngestResponseAcceptedTest {

    @Test
    fun accepted_on200WithAcceptedTrue() {
        assertTrue(isIngestResponseAccepted(200, """{"accepted":true}"""))
    }

    /**
     * Дефект, найденный сверкой с `src/app/api/ingest/route.ts` (поток D):
     * одиночное событие приёмник ВСЕГДА отвечает 200, включая честный отказ —
     * `{accepted:false, reason:"rate limited"}` и подобные. Код состояния сам
     * по себе не отличает успех от отказа.
     */
    @Test
    fun notAccepted_on200WithAcceptedFalse_rateLimitedOrRejected() {
        assertFalse(isIngestResponseAccepted(200, """{"accepted":false,"reason":"rate limited"}"""))
        assertFalse(isIngestResponseAccepted(200, """{"accepted":false,"reason":"missing or invalid ts"}"""))
        assertFalse(isIngestResponseAccepted(200, """{"accepted":false,"reason":"consent required for a device without an account"}"""))
    }

    @Test
    fun notAccepted_on401Unauthorized() {
        assertFalse(isIngestResponseAccepted(401, """{"accepted":false,"reason":"unauthorized"}"""))
    }

    @Test
    fun notAccepted_onMalformedOrEmptyBody() {
        assertFalse(isIngestResponseAccepted(200, ""))
        assertFalse(isIngestResponseAccepted(200, "not json"))
        assertFalse(isIngestResponseAccepted(200, "{}"))
    }

    @Test
    fun notAccepted_on5xxEvenWithAcceptedTrueBody() {
        // Оборонительная проверка: код состояния — обязательное условие, тело само по себе не решает.
        assertFalse(isIngestResponseAccepted(500, """{"accepted":true}"""))
    }
}
