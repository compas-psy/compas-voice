package ru.cmpas.voice.analytics

import java.time.Instant
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
 *
 * `ts` — ISO-8601 UTC строка, не epoch-millis число: приёмник ПРАКТИКИ
 * (`src/lib/analytics/schema.ts`, `validateEvent`) требует
 * `typeof raw.ts === 'string'` и парсит `Date.parse(raw.ts)`; число здесь
 * раньше отклонялось валидатором целиком («missing or invalid ts») —
 * найдено и исправлено сверкой конверта с реестром ПРАКТИКИ (поток D).
 *
 * `eventId` — ключ идемпотентности приёмника (`event_id`, необязателен для
 * валидатора, но без него POST /ingest не может отличить повтор доставки от
 * нового события — `processIngestEvent` дедуплицирует по нему только когда
 * он есть). UUID, не ULID: ULID обсуждался ради монотонной сортировки
 * (`06_ANALYTICS.md §4.1`), но это отдельная библиотечная зависимость, не
 * проверенная и не подключаемая в этой среде без сети до Maven; приёмнику
 * достаточно строки, стабильной при повторной отправке одного и того же
 * события, чему UUID уже удовлетворяет.
 */
fun buildAnalyticsEvent(
    name: String,
    props: Map<String, JsonElement>,
    ts: Long,
    deviceId: String,
    eventId: String,
): kotlinx.serialization.json.JsonObject? {
    val allowed = EVENT_SCHEMA[name] ?: return null
    return buildJsonObject {
        put("event", name)
        put("ts", Instant.ofEpochMilli(ts).toString())
        put("product", "moments")
        put("device_id", deviceId)
        put("event_id", eventId)
        put("schema_version", 1)
        putJsonObject("props") {
            props.filterKeys { it in allowed }.forEach { (key, value) -> put(key, value) }
        }
    }
}

internal fun jsonOf(value: String) = JsonPrimitive(value)
internal fun jsonOf(value: Boolean) = JsonPrimitive(value)
internal fun jsonOf(value: Int) = JsonPrimitive(value)
