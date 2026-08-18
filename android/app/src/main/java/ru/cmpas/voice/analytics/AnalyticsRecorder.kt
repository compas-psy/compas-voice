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
 * У МОМЕНТОВ по-прежнему нет собственного бэкенда (`docs/PRIVACY-DPO.md §1`).
 * [enqueue] складывает событие в локальную очередь (`LocalStore.enqueueAnalyticsEvent`);
 * довозит её до существующего `POST /ingest` ПРАКТИКИ [AnalyticsTransport]
 * (О-260817-14), за отдельным флагом, выключенным по умолчанию.
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
