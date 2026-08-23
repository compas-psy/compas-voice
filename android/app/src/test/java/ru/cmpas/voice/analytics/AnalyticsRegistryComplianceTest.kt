package ru.cmpas.voice.analytics

import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Поток D, задача D2: доказывает, что конверт, который РЕАЛЬНО строит код
 * МОМЕНТОВ ([AnalyticsRecorder] → [buildAnalyticsEvent]), проходит валидатор
 * приёмника ПРАКТИКИ (`compas-psy/cmpas.ru`, `src/lib/analytics/schema.ts`
 * `validateEvent` + `analytics/schema/events.yaml`) — а не переписывает эту
 * логику заново и не сверяет её саму с собой.
 *
 * Реестр ПРАКТИКИ сюда не тянется по сети (устав, О-260817-17): вместо этого
 * ниже — снимок нужных фактов о продукте "moments" из `events.yaml`,
 * зафиксированный чтением `/tmp/audit/practice-b` 23.08.2026. Тот же приём
 * уже применяется в обратную сторону в самом `cmpas.ru`
 * (`tests/registry-sync.test.ts`, `MOMENTS_SNAPSHOT`) — там снимок клиента
 * МОМЕНТОВ хранится в реестре ПРАКТИКИ, здесь — снимок реестра ПРАКТИКИ
 * хранится в клиенте МОМЕНТОВ. Если реестр ПРАКТИКИ изменится (новое
 * обязательное поле конверта, другое имя продукта, другой набор props у
 * события) — этот снимок надо обновить вручную вместе с ним; расхождение
 * снимка с реальным `events.yaml` этот тест обнаружить не может, только
 * расхождение конверта МОМЕНТОВ со снимком.
 */
class AnalyticsRegistryComplianceTest {

    /** `events.yaml`: `products:` — список продуктов, которым вообще разрешено слать в `/ingest`. */
    private val registryProducts = setOf("practice", "zapiski", "moments")

    private data class RegistryEventDef(val product: String, val optionalProps: Set<String>)

    /**
     * `events.yaml`, события с `product: moments` — практика этого снимка,
     * не "required" props: у всех четырёх событий МОМЕНТОВ `required: []`,
     * все объявленные поля — `optional`.
     */
    private val momentsRegistry: Map<String, RegistryEventDef> = mapOf(
        "app_installed" to RegistryEventDef("moments", emptySet()),
        "practice_started" to RegistryEventDef("moments", setOf("practice_id", "group", "is_sleep")),
        "practice_finished" to RegistryEventDef(
            "moments",
            setOf("practice_id", "group", "is_sleep", "completion_pct"),
        ),
        "crossed_to_product" to RegistryEventDef("moments", setOf("target_product")),
    )

    /** Прогоняет один вызов [AnalyticsRecorder] через реальный код и возвращает разобранный конверт. */
    private fun captureEnqueuedEnvelope(
        fixedEventId: String = "event-id-fixture",
        fixedDeviceId: String = "device-id-fixture",
        block: suspend (AnalyticsRecorder) -> Unit,
    ): JsonObject = runBlocking {
        var captured: String? = null
        val recorder = AnalyticsRecorder(
            isConsentGranted = { true },
            enqueue = { captured = it },
            deviceId = { fixedDeviceId },
            eventId = { fixedEventId },
        )
        block(recorder)
        Json.parseToJsonElement(requireNotNull(captured) { "recorder не поставил событие в очередь" }).jsonObject
    }

    /**
     * Общие проверки конверта (`12_ANALYTICS.md §3` + `validateEvent`):
     * имя события известно реестру под правильным продуктом, `ts` —
     * ISO-8601 UTC строка (не epoch-millis число), `schema_version` — целое
     * число, `event_id` и `device_id` — непустые строки, `account_id`
     * отсутствует вовсе (у МОМЕНТОВ нет аккаунтов, но нужен хотя бы один из
     * `device_id`/`account_id` — device_id есть), `props` не содержит ключей
     * сверх объявленных в реестре для этого события.
     */
    private fun assertCompliesWithReceiverRegistry(event: JsonObject, expectedEventName: String) {
        assertTrue("moments отсутствует в products реестра — снимок устарел?", "moments" in registryProducts)

        val def = requireNotNull(momentsRegistry[expectedEventName]) {
            "$expectedEventName отсутствует в снимке реестра ПРАКТИКИ для moments — " +
                "либо клиент шлёт незарегистрированное имя, либо снимок в этом тесте устарел"
        }

        assertEquals(expectedEventName, event["event"]!!.jsonPrimitive.content)
        assertEquals(
            "событие $expectedEventName зарегистрировано под другим продуктом в events.yaml",
            def.product,
            event["product"]!!.jsonPrimitive.content,
        )

        // ts — тот же формат, что требует Date.parse на сервере (validateEvent: typeof === 'string').
        val tsField = event["ts"]!!.jsonPrimitive
        assertTrue("ts должен быть JSON-строкой, приёмник отклоняет число как «missing or invalid ts»", tsField.isString)
        Instant.parse(tsField.content) // бросит исключение, если формат не ISO-8601 — тест упадёт явно

        // schema_version — число, не строка (validateEvent: typeof === 'number').
        val schemaVersionField = event["schema_version"]!!.jsonPrimitive
        assertFalse("schema_version должен быть числом, не строкой", schemaVersionField.isString)
        schemaVersionField.content.toInt() // бросит, если не целое

        // event_id — ключ идемпотентности приёмника; непустой, иначе бессмысленен.
        val eventIdField = event["event_id"]!!.jsonPrimitive
        assertTrue(eventIdField.isString)
        assertTrue("event_id пуст — дедупликация на приёмнике не сработает", eventIdField.content.isNotBlank())

        // device_id непуст; account_id не должен присутствовать вовсе — у МОМЕНТОВ нет аккаунтов.
        val deviceIdField = event["device_id"]!!.jsonPrimitive
        assertTrue(deviceIdField.content.isNotBlank())
        assertFalse(
            "у МОМЕНТОВ нет аккаунтов (docs/PRIVACY-DPO.md §1) — account_id не должен попадать в конверт",
            event.containsKey("account_id"),
        )

        // props — множество ключей не шире того, что реестр объявил для этого события.
        val propsKeys = event["props"]!!.jsonObject.keys
        assertTrue(
            "props содержит ключ(и), не объявленные в events.yaml для $expectedEventName: " +
                "${propsKeys - def.optionalProps}",
            def.optionalProps.containsAll(propsKeys),
        )
    }

    @Test
    fun appInstalled_matchesReceiverRegistry() {
        val event = captureEnqueuedEnvelope { it.recordAppInstalled(1_724_400_000_000L) }
        assertCompliesWithReceiverRegistry(event, "app_installed")
        assertTrue("app_installed не несёт props по реестру", event["props"]!!.jsonObject.isEmpty())
    }

    @Test
    fun practiceStarted_matchesReceiverRegistry_withAllRegisteredProps() {
        val event = captureEnqueuedEnvelope { it.recordPracticeStarted("sleep_deep_dive", "SLEEP", true) }
        assertCompliesWithReceiverRegistry(event, "practice_started")
        // Клиент действительно шлёт ВЕСЬ набор, объявленный реестром — не подмножество и не надмножество.
        assertEquals(momentsRegistry.getValue("practice_started").optionalProps, event["props"]!!.jsonObject.keys)
    }

    @Test
    fun practiceFinished_matchesReceiverRegistry_withAllRegisteredProps() {
        val event = captureEnqueuedEnvelope { it.recordPracticeFinished("sleep_deep_dive", "SLEEP", true, 87) }
        assertCompliesWithReceiverRegistry(event, "practice_finished")
        assertEquals(momentsRegistry.getValue("practice_finished").optionalProps, event["props"]!!.jsonObject.keys)
    }

    @Test
    fun crossedToProduct_matchesReceiverRegistry_withAllRegisteredProps() {
        val event = captureEnqueuedEnvelope { it.recordCrossedToProduct("practice") }
        assertCompliesWithReceiverRegistry(event, "crossed_to_product")
        assertEquals(momentsRegistry.getValue("crossed_to_product").optionalProps, event["props"]!!.jsonObject.keys)
    }

    /**
     * Обязательные поля конверта (`12_ANALYTICS.md §3`): все присутствуют
     * одновременно в одном настоящем событии, кроме `account_id`, который у
     * МОМЕНТОВ отсутствует намеренно (проверено отдельно выше).
     */
    @Test
    fun envelope_hasAllRequiredFieldsAtOnce() {
        val event = captureEnqueuedEnvelope { it.recordAppInstalled(0L) }
        val required = setOf("event", "ts", "product", "device_id", "props", "schema_version", "event_id")
        assertTrue(
            "в конверте не хватает поля(ей): ${required - event.keys}",
            event.keys.containsAll(required),
        )
    }

    /** Продукт из `events.yaml` — "moments", не "momenty": иначе приёмник отклоняет весь конверт. */
    @Test
    fun envelope_productIsExactlyKnownToRegistry() {
        val event = captureEnqueuedEnvelope { it.recordAppInstalled(0L) }
        assertTrue(event["product"]!!.jsonPrimitive.content in registryProducts)
    }
}
