package ru.cmpas.voice.analytics

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * О-260817-06: реестр разметки МОМЕНТОВ — строгий allowlist. Любое поле, не
 * объявленное в `analytics/schema/events.yaml` (здесь — в EVENT_SCHEMA),
 * должно отбрасываться молча, а не проходить в событие.
 */
class AnalyticsSchemaTest {

    @Test
    fun unknownEventName_isRejected() {
        assertNull(buildAnalyticsEvent("something_else", emptyMap(), ts = 0L, deviceId = "dev", eventId = "eid"))
    }

    @Test
    fun practiceStarted_keepsOnlyDeclaredProps_andDropsFreeText() {
        val event = requireNotNull(
            buildAnalyticsEvent(
                "practice_started",
                mapOf(
                    "practice_id" to JsonPrimitive("sleep_deep_dive"),
                    "group" to JsonPrimitive("SLEEP"),
                    "is_sleep" to JsonPrimitive(true),
                    "note" to JsonPrimitive("клиент рассказал про развод"),
                ),
                ts = 1_000L,
                deviceId = "dev-1",
                eventId = "eid-1",
            )
        )
        val props = event["props"]!!.jsonObject
        assertEquals(setOf("practice_id", "group", "is_sleep"), props.keys)
        assertFalse(event.toString().contains("развод"))
    }

    @Test
    fun appInstalled_hasNoProps() {
        val event = requireNotNull(buildAnalyticsEvent("app_installed", emptyMap(), 0L, "dev", "eid"))
        assertTrue(event["props"]!!.jsonObject.isEmpty())
    }

    @Test
    fun practiceFinished_includesCompletionPct() {
        val event = requireNotNull(
            buildAnalyticsEvent(
                "practice_finished",
                mapOf(
                    "practice_id" to JsonPrimitive("sleep_deep_dive"),
                    "group" to JsonPrimitive("SLEEP"),
                    "is_sleep" to JsonPrimitive(true),
                    "completion_pct" to JsonPrimitive(87),
                ),
                ts = 2_000L,
                deviceId = "dev-1",
                eventId = "eid-2",
            )
        )
        val props = event["props"]!!.jsonObject
        assertEquals(87, props["completion_pct"]!!.jsonPrimitive.int)
    }

    @Test
    fun crossedToProduct_keepsTargetProduct() {
        val event = requireNotNull(
            buildAnalyticsEvent(
                "crossed_to_product",
                mapOf("target_product" to JsonPrimitive("practice")),
                ts = 3_000L,
                deviceId = "dev-1",
                eventId = "eid-3",
            )
        )
        assertEquals("practice", event["props"]!!.jsonObject["target_product"]!!.jsonPrimitive.content)
    }

    @Test
    fun envelope_hasNoAccountId_momentyHasNoAccounts() {
        val event = requireNotNull(buildAnalyticsEvent("app_installed", emptyMap(), 0L, "dev", "eid"))
        assertFalse(event.toString().contains("account_id"))
    }

    /**
     * О-260817-14: приёмник ПРАКТИКИ (`cmpas.ru/analytics/schema/events.yaml`)
     * знает продукт как "moments", а не "momenty" — иначе конверт отвергается
     * валидатором как неизвестный продукт ещё до проверки имени события.
     */
    @Test
    fun envelope_productMatchesReceiverRegistry() {
        val event = requireNotNull(buildAnalyticsEvent("app_installed", emptyMap(), 0L, "dev", "eid"))
        assertEquals("moments", event["product"]!!.jsonPrimitive.content)
    }

    /**
     * Поток D, дефект найден сверкой с `src/lib/analytics/schema.ts`
     * (`validateEvent`): тот требует `typeof raw.ts === 'string'` и
     * `Date.parse(raw.ts)` — число (epoch-millis) отклонялось валидатором
     * целиком, ещё до проверки имени события. `Instant.ofEpochMilli(...).
     * toString()` — тот же формат, что `Date.toISOString()` в JS без
     * дробных миллисекунд, когда их нет.
     */
    @Test
    fun envelope_tsIsIsoUtcString_notEpochMillisNumber() {
        val event = requireNotNull(buildAnalyticsEvent("app_installed", emptyMap(), ts = 1_000L, deviceId = "dev", eventId = "eid"))
        val tsField = event["ts"]!!.jsonPrimitive
        assertTrue("ts должен сериализоваться как JSON-строка, а не число", tsField.isString)
        assertEquals("1970-01-01T00:00:01Z", tsField.content)
    }

    /**
     * Поток D: `event_id` раньше не попадал в конверт вовсе — приёмник не
     * может дедуплицировать повтор доставки после таймаута без него
     * (`processIngestEvent`, ключ идемпотентности).
     */
    @Test
    fun envelope_includesEventId() {
        val event = requireNotNull(buildAnalyticsEvent("app_installed", emptyMap(), 0L, "dev", eventId = "unique-id-1"))
        assertEquals("unique-id-1", event["event_id"]!!.jsonPrimitive.content)
    }

    /**
     * Поток E: "consent_updated" был отсутствующим в EVENT_SCHEMA именем —
     * до этой записи buildAnalyticsEvent молча возвращал null для него
     * (как unknownEventName_isRejected выше), и AnalyticsRecorder не мог
     * поставить в очередь ни выдачу, ни отзыв согласия ни при каких
     * условиях. Здесь — низкоуровневая проверка самого конверта; правило
     * «сначала выдача/отзыв, потом всё содержательное» проверяет
     * AnalyticsRecorder/LocalStore, не эта функция.
     */
    @Test
    fun consentUpdated_isKnownEvent_carriesGrantedFlag() {
        val granted = requireNotNull(
            buildAnalyticsEvent(
                "consent_updated",
                mapOf("granted" to JsonPrimitive(true)),
                ts = 4_000L,
                deviceId = "dev-1",
                eventId = "eid-4",
            )
        )
        assertEquals("consent_updated", granted["event"]!!.jsonPrimitive.content)
        assertTrue(granted["props"]!!.jsonObject["granted"]!!.jsonPrimitive.boolean)

        val revoked = requireNotNull(
            buildAnalyticsEvent(
                "consent_updated",
                mapOf("granted" to JsonPrimitive(false)),
                ts = 4_001L,
                deviceId = "dev-1",
                eventId = "eid-5",
            )
        )
        assertFalse(revoked["props"]!!.jsonObject["granted"]!!.jsonPrimitive.boolean)
    }

    /** Как practiceStarted_keepsOnlyDeclaredProps_andDropsFreeText — реестр строгий и для этого события тоже. */
    @Test
    fun consentUpdated_dropsUndeclaredProps() {
        val event = requireNotNull(
            buildAnalyticsEvent(
                "consent_updated",
                mapOf("granted" to JsonPrimitive(true), "reason" to JsonPrimitive("свободный текст")),
                ts = 4_002L,
                deviceId = "dev-1",
                eventId = "eid-6",
            )
        )
        assertEquals(setOf("granted"), event["props"]!!.jsonObject.keys)
    }
}
