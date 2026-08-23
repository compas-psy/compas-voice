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
 * очередь — ни один вызов record* не имеет побочного эффекта. Единственное
 * исключение во всём классе — [buildConsentRevokedEvent] (E-M1): он не
 * "record*", ничего не ставит в очередь и не проверяет согласие вовсе,
 * потому что сообщает как раз о его отсутствии — см. его собственный
 * комментарий и `AppContainer.revokeAnalyticsConsent`.
 *
 * У МОМЕНТОВ по-прежнему нет собственного бэкенда (`docs/PRIVACY-DPO.md §1`).
 * [enqueue] складывает событие в локальную очередь (`LocalStore.enqueueAnalyticsEvent`);
 * довозит её до существующего `POST /ingest` ПРАКТИКИ [AnalyticsTransport]
 * (О-260817-14), за отдельным флагом [ru.cmpas.voice.data.FeatureFlags.analyticsTransportEnabled]
 * — согласие проверяется здесь независимо от того, включён ли он.
 */
class AnalyticsRecorder(
    private val isConsentGranted: suspend () -> Boolean,
    private val enqueue: suspend (String) -> Unit,
    private val deviceId: suspend () -> String,
    /** Ключ идемпотентности приёмника (`event_id`) — свежий на каждую запись, см. AnalyticsSchema.buildAnalyticsEvent. */
    private val eventId: () -> String = { java.util.UUID.randomUUID().toString() },
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

    /**
     * Выдача согласия (E-M1, контракт контура v2). Идёт через обычный,
     * согласие-проверяющий [record] — к моменту вызова вызывающий код уже
     * записал согласие как true в LocalStore, так что проверка внутри
     * [record] пройдёт. Вызывающий код (KompasRoot/ProfileScreen) обязан
     * позвать это ПЕРВЫМ среди record*-методов после выдачи согласия —
     * приёмник ставит AnalyticsDeviceConsent именно по этому событию
     * (`writeDeviceOnlyEvent`), и до него любое другое событие устройства
     * без аккаунта будет отвергнуто как «consent required for a device
     * without an account». [record] сам этот порядок не гарантирует — он
     * не знает, что это событие особое, очередь просто FIFO.
     */
    suspend fun recordConsentUpdated(granted: Boolean) = record(
        "consent_updated",
        mapOf("granted" to jsonOf(granted)),
    )

    /**
     * Отзыв согласия (E-M1) — единственный намеренный обход проверки в
     * [record] во всём классе. [record] существует, чтобы БЕЗ согласия
     * ничего не измерялось; это же событие не измеряет использование
     * продукта, а СООБЩАЕТ об отсутствии согласия — тому самому приёмнику,
     * которому иначе неоткуда об этом узнать. Наивная отправка через
     * [record] не сработала бы: вызывающий код (AppContainer) обязан
     * позвать это ДО того, как согласие запишется как false в LocalStore
     * (`buildAnalyticsEvent` не спрашивает [isConsentGranted] — только
     * [deviceId] и [eventId], оба доступны независимо от согласия), а
     * результат — строит, не ставит в очередь и не отправляет: доставка
     * отзыва обязана обойти и обычную очередь (которую отзыв стирает,
     * `queueAfterConsentChange`), и её транспорт (`AnalyticsTransport.flush`,
     * который сам по себе тоже не шлёт без согласия) — см.
     * `AppContainer.revokeAnalyticsConsent` и
     * `AnalyticsTransport.flushPendingRevocation`.
     */
    suspend fun buildConsentRevokedEvent(): String? =
        buildAnalyticsEvent("consent_updated", mapOf("granted" to jsonOf(false)), now(), deviceId(), eventId())
            ?.toString()

    private suspend fun record(name: String, props: Map<String, JsonElement>, ts: Long = now()) {
        if (!isConsentGranted()) return
        val event = buildAnalyticsEvent(name, props, ts, deviceId(), eventId()) ?: return
        enqueue(event.toString())
    }
}
