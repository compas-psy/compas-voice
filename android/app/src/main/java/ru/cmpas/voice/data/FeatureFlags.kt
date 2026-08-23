package ru.cmpas.voice.data

/**
 * Фиче-флаги аудио-слоя (см. инструкцию аудио §1) и служебные флаги.
 *
 * - [softBackground] — «Мягкий фон» (фоновые петли). Часть MVP → ON.
 * - [spatialAudio]   — бинауральный слой + опция «Бинауральный» в UI.
 *   ON: показывается третий сегмент «Бинауральный» (шит) и опция в профиле;
 *   бинауральный слой играет поверх фона ТОЛЬКО в наушниках/BT-стерео.
 * - [analyticsTransportEnabled] — реальная отправка очереди аналитики в
 *   `POST /ingest` (О-260817-14). Было выключено, пока не было теста,
 *   доказывающего, что конверт МОМЕНТОВ (`AnalyticsSchema.buildAnalyticsEvent`)
 *   действительно проходит валидатор приёмника ПРАКТИКИ (`events.yaml` +
 *   `src/lib/analytics/schema.ts`) — флаг был честным «неизвестно, дойдёт ли»,
 *   а не «дойдёт, но пока рано». Теперь конверт сверен построчно
 *   (`AnalyticsRegistryComplianceTest`) и два реальных дефекта конверта,
 *   которые сверка нашла, устранены: `ts` был числом (epoch-millis), приёмник
 *   ждёт ISO-8601 строку («missing or invalid ts» — отказ всех событий);
 *   `event_id` не отправлялся вовсе (ключ идемпотентности приёмника — без
 *   него повтор доставки после таймаута плодит дубли).
 *
 *   Согласие пользователя флаг не подменяет и подменить не может: и
 *   [ru.cmpas.voice.analytics.AnalyticsRecorder.record], и
 *   [ru.cmpas.voice.analytics.AnalyticsTransport.flush] независимо проверяют
 *   согласие каждый на своей стороне (см. их тесты) — этот флаг только решает,
 *   пробует ли [ru.cmpas.voice.AppContainer] довезти уже согласованную и уже
 *   поставленную в очередь запись до сети, поверх адреса и секрета из сборки
 *   (`ANALYTICS_INGEST_URL`/`ANALYTICS_INGEST_SECRET_MOMENTS`, `app/build.gradle.kts`);
 *   без них, даже при ON, транспорт явно молчит (лог), а не пытается слать.
 */
object FeatureFlags {
    const val softBackground: Boolean = true
    const val spatialAudio: Boolean = true
    const val analyticsTransportEnabled: Boolean = true
}
