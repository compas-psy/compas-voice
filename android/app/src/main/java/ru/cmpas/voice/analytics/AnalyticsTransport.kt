package ru.cmpas.voice.analytics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Отзыв согласия стирает очередь немедленно (О-260817-14) — иначе события,
 * накопленные до отзыва, всё равно ушли бы следующим [AnalyticsTransport.flush].
 * Чистая функция: [LocalStore.setAnalyticsConsent] применяет её внутри одной
 * транзакции DataStore, здесь же она проверяется юнит-тестом без Android.
 */
fun queueAfterConsentChange(currentQueue: List<String>, granted: Boolean): List<String> =
    if (granted) currentQueue else emptyList()

/**
 * Что должно лежать в однослотовом «кармане» отложенного отзыва после
 * смены согласия (E-M1) — сестра [queueAfterConsentChange], тот же приём:
 * чистая функция, [LocalStore.setAnalyticsConsent] применяет её в той же
 * транзакции DataStore, что и [queueAfterConsentChange], здесь же она
 * проверяется отдельно от Android.
 *
 * При выдаче (`granted = true`) карман всегда пуст: свежий отзыв в этот
 * момент появиться не может (отзывают, только когда согласие ЕСТЬ, то
 * есть до этого вызова оно было true — иначе нечего отзывать), а любой
 * старый недоставленный отзыв уже устарел — сервер вот-вот получит новое
 * "granted: true" (`AnalyticsRecorder.recordConsentUpdated`, вызывается
 * сразу после), и слать следом устаревшее "granted: false" означало бы
 * гонку, где итоговое состояние на сервере зависит от порядка доставки
 * двух отдельных HTTP-запросов, а не от факта, что пользователь в итоге
 * согласие дал.
 *
 * При отзыве (`granted = false`) новый отзыв [newRevocationEvent] —
 * единственное, что там будет: он ЗАМЕНЯЕТ прежний, если тот почему-то
 * ещё не отправился (например, два быстрых переключения тумблера подряд)
 * — актуально только последнее состояние согласия, не оба.
 */
fun pendingRevocationAfterConsentChange(granted: Boolean, newRevocationEvent: String?): String? =
    if (granted) null else newRevocationEvent

/**
 * Транспорт настраивается конфигурацией сборки, а не кодом (поток D):
 * адрес и секрет приёмника нужны оба, иначе слать нечем/некуда. Пустая
 * строка — незаполненное свойство Gradle (`app/build.gradle.kts`), не
 * ошибка сборки: локальная debug-сборка без `-PanalyticsIngestSecretMoments=...`
 * обязана не пытаться слать, а не падать и не бить приёмник запросами,
 * гарантированно получающими 401 (приёмник ПРАКТИКИ fail-closed без
 * секрета — `verifyIngestSecret`, `src/app/api/ingest/route.ts`).
 */
fun isAnalyticsTransportConfigured(ingestUrl: String, ingestSecret: String): Boolean =
    ingestUrl.isNotBlank() && ingestSecret.isNotBlank()

/**
 * Разбирает ответ `POST /ingest`, а не только код состояния (поток D,
 * найдено при сверке с `src/app/api/ingest/route.ts`): при одиночном
 * событии (не массиве) приёмник всегда отвечает HTTP 200 и на успех, и на
 * честный отказ — `{accepted:false, reason:...}` (неверный `ts`, лимит
 * частоты `60/60с` на `device_id`, отсутствие согласия устройства и т.д.);
 * отдельный код состояния для отказа одиночного события route не ставит.
 * Проверка только `responseCode in 200..299` (как было раньше) приняла бы
 * такой отказ за успех и стёрла бы событие из локальной очереди без возврата
 * и без всякого следа — безвозвратная тихая потеря. Тело, которое не
 * разбирается как JSON с `accepted: true`, — не подтверждённый приём.
 */
fun isIngestResponseAccepted(httpStatusCode: Int, responseBody: String): Boolean {
    if (httpStatusCode !in 200..299) return false
    return runCatching {
        val accepted = Json.parseToJsonElement(responseBody).jsonObject["accepted"]
        (accepted as? JsonPrimitive)?.content == "true"
    }.getOrDefault(false)
}

/**
 * Довозит очередь [AnalyticsRecorder] до приёмника ПРАКТИКИ (О-260817-14) —
 * второй бэкенд под МОМЕНТЫ не строим, шлём в уже существующий `POST /ingest`.
 *
 * Зависимости — suspend-лямбды, как у [AnalyticsRecorder]: сеть и DataStore
 * здесь не видны вовсе, поэтому вся логика батчей/бэкоффа проверяется юнит-
 * тестами без Android. Реальная отправка (HTTP) собирается в `AppContainer`.
 *
 * До согласия [flush] не делает ничего — ни одного вызова [sendOne]. Отзыв
 * согласия стирает очередь немедленно на стороне [LocalStore.setAnalyticsConsent],
 * а не здесь: так отзыв необратим даже если [flush] в этот момент не запущен.
 * Сам факт отзыва [flush] не касается вовсе — за него отвечает отдельный
 * [flushPendingRevocation] (E-M1), у которого согласие-гейта нет намеренно:
 * он вызывается ровно тогда, когда согласие уже честно false.
 *
 * Пачки и лимит приёмника (поток D, задача D3): [sendOne] — сигнатура на
 * ОДНО событие (`eventJson: String`, не список), приёмник же вызывается по
 * одному событию за POST, а не JSON-массивом. Значит тело каждого запроса
 * физически не может превысить `MAX_INGEST_BATCH_SIZE = 200`
 * (`src/lib/analytics/ingest.ts`) — 1 событие всегда ≤ 200. [BATCH_SIZE]
 * ниже — это не размер тела запроса, а сколько событий подряд [flush]
 * пробует отправить (по одному HTTP-запросу на событие) за один проход,
 * прежде чем переоценить бэкофф; уменьшать его до лимита приёмника не
 * нужно и не имеет смысла, но `AnalyticsTransportTest` фиксирует это
 * инвариантом на случай, если позже транспорт научится слать массивом.
 */
class AnalyticsTransport(
    private val isConsentGranted: suspend () -> Boolean,
    private val peekQueue: suspend (limit: Int) -> List<String>,
    private val removeSent: suspend (count: Int) -> Unit,
    private val sendOne: suspend (eventJson: String) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var nextAttemptAtMs = 0L
    private var backoffMs = INITIAL_BACKOFF_MS

    /** Отправляет пачками, пока очередь не опустеет или не встретится ошибка. */
    suspend fun flush() {
        if (!isConsentGranted()) return
        if (now() < nextAttemptAtMs) return

        while (true) {
            val batch = peekQueue(BATCH_SIZE)
            if (batch.isEmpty()) {
                backoffMs = INITIAL_BACKOFF_MS
                return
            }

            var sentCount = 0
            for (event in batch) {
                if (!sendOne(event)) break
                sentCount++
            }
            if (sentCount > 0) removeSent(sentCount)

            if (sentCount < batch.size) {
                nextAttemptAtMs = now() + backoffMs
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                return
            }

            backoffMs = INITIAL_BACKOFF_MS
            if (batch.size < BATCH_SIZE) return
        }
    }

    /**
     * Доставляет ОТЛОЖЕННЫЙ ОТЗЫВ согласия (E-M1, контракт контура v2) —
     * ЕДИНСТВЕННЫЙ путь в этом классе, который сознательно не проверяет
     * [isConsentGranted]. Он вызывается ровно тогда, когда согласие уже
     * честно false — [flush] выше в этот момент не отправит ничего (его
     * гейт остаётся как был, О-260817-14, `withoutConsent_neverSendsQueuedEvents`
     * это по-прежнему гарантирует), а без этого метода сам отзыв тоже
     * никогда бы не ушёл — приёмник узнал бы об отзыве согласия, только
     * если бы МОМЕНТЫ снова его выдали.
     *
     * Не трогает обычную очередь ([peekQueue]/[removeSent]) — источник
     * события отдельный, однослотовый «карман» LocalStore
     * (`LocalStore.peekPendingAnalyticsRevocation`/`clearPendingAnalyticsRevocation`),
     * переданный сюда параметрами метода, а не конструктора: конструктор
     * существующих тестов (`AnalyticsTransportTest`) менять незачем, эти
     * лямбды не нужны [flush]. Дыры нет: пока согласие false, в этот
     * «карман» ничего, кроме самого факта отзыва, попасть не может — его
     * заполняет только `LocalStore.setAnalyticsConsent(false, ...)`
     * (см. `pendingRevocationAfterConsentChange`), а выдача согласия
     * (`granted = true`) его снова обнуляет.
     *
     * Не батч — тот же [sendOne], одно событие. Очищает «карман» только
     * при подтверждённом приёме ([sendOne] вернул true); при неудаче
     * оставляет как есть — следующий вызов (следующий запуск приложения
     * или следующий [ru.cmpas.voice.AppContainer.flushAnalyticsQueue])
     * попробует снова. Отдельного бэкоффа как у [flush] здесь нет
     * намеренно: событие одно и маленькое, а не пачка — цена лишней
     * попытки при недоступной сети ничтожна по сравнению с риском забыть
     * повторить единственную запись, которую закон обязывает довезти.
     */
    suspend fun flushPendingRevocation(
        peekPendingRevocation: suspend () -> String?,
        clearPendingRevocation: suspend () -> Unit,
    ) {
        val event = peekPendingRevocation() ?: return
        if (sendOne(event)) clearPendingRevocation()
    }

    companion object {
        const val BATCH_SIZE = 20
        const val INITIAL_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 30 * 60_000L
    }
}
