package ru.cmpas.voice.analytics

/**
 * Отзыв согласия стирает очередь немедленно (О-260817-14) — иначе события,
 * накопленные до отзыва, всё равно ушли бы следующим [AnalyticsTransport.flush].
 * Чистая функция: [LocalStore.setAnalyticsConsent] применяет её внутри одной
 * транзакции DataStore, здесь же она проверяется юнит-тестом без Android.
 */
fun queueAfterConsentChange(currentQueue: List<String>, granted: Boolean): List<String> =
    if (granted) currentQueue else emptyList()

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

    companion object {
        const val BATCH_SIZE = 20
        const val INITIAL_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 30 * 60_000L
    }
}
