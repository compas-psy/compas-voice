package ru.cmpas.voice.analytics

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Реестр допустимых событий и полей — зеркало `analytics/schema/events.yaml`
 * (О-260817-06). Единственное место, которое решает, что может попасть в
 * событие: любое необъявленное поле отбрасывается молча, любое необъявленное
 * имя события — всё событие отбрасывается (null). Это структурная защита
 * правила «содержание не измеряется никогда» (`12_ANALYTICS.md §1`), а не
 * договорённость, которую можно случайно нарушить в месте вызова.
 */
private val EVENT_SCHEMA: Map<String, Set<String>> = mapOf(
    "app_installed" to emptySet(),
    "practice_started" to setOf("practice_id", "group", "is_sleep"),
    "practice_finished" to setOf("practice_id", "group", "is_sleep", "completion_pct"),
    "crossed_to_product" to setOf("target_product"),
)

/**
 * Собирает событие в общем конверте (`12_ANALYTICS.md §3`). `accountId` нет —
 * у МОМЕНТОВ в MVP нет аккаунтов (`docs/PRIVACY-DPO.md §1`). Возвращает null
 * для необъявленного имени события — вызывающий код обязан не отправлять его.
 */
fun buildAnalyticsEvent(
    name: String,
    props: Map<String, JsonElement>,
    ts: Long,
    deviceId: String,
): kotlinx.serialization.json.JsonObject? {
    val allowed = EVENT_SCHEMA[name] ?: return null
    return buildJsonObject {
        put("event", name)
        put("ts", ts)
        put("product", "moments")
        put("device_id", deviceId)
        put("schema_version", 1)
        putJsonObject("props") {
            props.filterKeys { it in allowed }.forEach { (key, value) -> put(key, value) }
        }
    }
}

internal fun jsonOf(value: String) = JsonPrimitive(value)
internal fun jsonOf(value: Boolean) = JsonPrimitive(value)
internal fun jsonOf(value: Int) = JsonPrimitive(value)
