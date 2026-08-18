package ru.cmpas.voice.analytics

import kotlinx.serialization.json.JsonPrimitive
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
        assertNull(buildAnalyticsEvent("something_else", emptyMap(), ts = 0L, deviceId = "dev"))
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
            )
        )
        val props = event["props"]!!.jsonObject
        assertEquals(setOf("practice_id", "group", "is_sleep"), props.keys)
        assertFalse(event.toString().contains("развод"))
    }

    @Test
    fun appInstalled_hasNoProps() {
        val event = requireNotNull(buildAnalyticsEvent("app_installed", emptyMap(), 0L, "dev"))
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
            )
        )
        assertEquals("practice", event["props"]!!.jsonObject["target_product"]!!.jsonPrimitive.content)
    }

    @Test
    fun envelope_hasNoAccountId_momentyHasNoAccounts() {
        val event = requireNotNull(buildAnalyticsEvent("app_installed", emptyMap(), 0L, "dev"))
        assertFalse(event.toString().contains("account_id"))
    }

    /**
     * О-260817-14: приёмник ПРАКТИКИ (`cmpas.ru/analytics/schema/events.yaml`)
     * знает продукт как "moments", а не "momenty" — иначе конверт отвергается
     * валидатором как неизвестный продукт ещё до проверки имени события.
     */
    @Test
    fun envelope_productMatchesReceiverRegistry() {
        val event = requireNotNull(buildAnalyticsEvent("app_installed", emptyMap(), 0L, "dev"))
        assertEquals("moments", event["product"]!!.jsonPrimitive.content)
    }
}
