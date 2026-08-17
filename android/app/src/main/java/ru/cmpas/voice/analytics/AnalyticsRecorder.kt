package ru.cmpas.voice.analytics

import kotlinx.serialization.json.JsonElement

/**
 * Единственная точка записи продуктовых событий МОМЕНТОВ (О-260817-06).
 *
 * Зависимости — простые suspend-лямбды, а не LocalStore напрямую: так класс
 * проверяется юнит-тестами без Android/DataStore, а вызывающий код (AppContainer)
 * сам решает, откуда брать согласие/очередь/device_id.
 *
 * До согласия ([isConsentGranted] == false) ничего не строится и не уходит в
 * очередь — ни один вызов record* не имеет побочного эффекта.
 *
 * Отправка за пределы устройства не реализована: у МОМЕНТОВ в MVP нет
 * бэкенда вообще (`docs/PRIVACY-DPO.md §1`, `CLAUDE.md`: «MVP работает без
 * бэкенда и без аккаунтов»). [enqueue] складывает событие в локальную очередь
 * (`LocalStore.enqueueAnalyticsEvent`), готовую к отправке, когда/если у
 * продукта появится собственный `POST /ingest` (`12_ANALYTICS.md §3`).
 */
class AnalyticsRecorder(
    private val isConsentGranted: suspend () -> Boolean,
    private val enqueue: suspend (String) -> Unit,
    private val deviceId: suspend () -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun recordAppInstalled(installedAtEpochMs: Long) =
        record("app_installed", emptyMap(), installedAtEpochMs)

    suspend fun recordPracticeStarted(practiceId: String, group: String, isSleep: Boolean) = record(
        "practice_started",
        mapOf(
            "practice_id" to jsonOf(practiceId),
            "group" to jsonOf(group),
            "is_sleep" to jsonOf(isSleep),
        ),
    )

    suspend fun recordPracticeFinished(practiceId: String, group: String, isSleep: Boolean, completionPct: Int) = record(
        "practice_finished",
        mapOf(
            "practice_id" to jsonOf(practiceId),
            "group" to jsonOf(group),
            "is_sleep" to jsonOf(isSleep),
            "completion_pct" to jsonOf(completionPct.coerceIn(0, 100)),
        ),
    )

    suspend fun recordCrossedToProduct(targetProduct: String) = record(
        "crossed_to_product",
        mapOf("target_product" to jsonOf(targetProduct)),
    )

    private suspend fun record(name: String, props: Map<String, JsonElement>, ts: Long = now()) {
        if (!isConsentGranted()) return
        val event = buildAnalyticsEvent(name, props, ts, deviceId()) ?: return
        enqueue(event.toString())
    }
}
