package ru.cmpas.voice

import android.app.Application
import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.cmpas.voice.analytics.AnalyticsRecorder
import ru.cmpas.voice.analytics.AnalyticsTransport
import ru.cmpas.voice.analytics.isAnalyticsTransportConfigured
import ru.cmpas.voice.analytics.isIngestResponseAccepted
import ru.cmpas.voice.audio.BackgroundAudio
import ru.cmpas.voice.audio.ExoBackgroundAudio
import ru.cmpas.voice.audio.ExoVoiceEngine
import ru.cmpas.voice.audio.NoopBackgroundAudio
import ru.cmpas.voice.audio.PlayerController
import ru.cmpas.voice.data.FeatureFlags
import ru.cmpas.voice.data.LocalStore

/** Простой контейнер зависимостей (service locator) — без Hilt для лёгкости. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store = LocalStore(context)
    private val background: BackgroundAudio =
        if (FeatureFlags.softBackground) ExoBackgroundAudio(context.applicationContext, appScope, store)
        else NoopBackgroundAudio
    private val voice = ExoVoiceEngine(context.applicationContext)
    val player = PlayerController(appScope, background, voice)

    /**
     * Настроен ли транспорт конфигурацией сборки (адрес + секрет — оба нужны,
     * [isAnalyticsTransportConfigured]). Проверяется один раз при создании
     * контейнера, а не на каждой отправке: при отсутствии конфигурации
     * транспорт должен молчать явно (один лог здесь), а не пытаться слать и
     * гарантированно получать 401 (приёмник fail-closed без секрета) на
     * каждое событие очереди.
     */
    private val analyticsTransportConfigured =
        isAnalyticsTransportConfigured(BuildConfig.ANALYTICS_INGEST_URL, BuildConfig.ANALYTICS_INGEST_SECRET_MOMENTS)

    /** Довозит очередь до приёмника ПРАКТИКИ (О-260817-14), см. AnalyticsTransport. */
    private val analyticsTransport = AnalyticsTransport(
        isConsentGranted = { store.analyticsConsent.first() },
        peekQueue = { store.peekAnalyticsQueue(it) },
        removeSent = { store.removeAnalyticsEvents(it) },
        sendOne = { postAnalyticsEvent(it) },
    )

    /** Довозит отложенный отзыв согласия (E-M1) — мимо согласие-гейта flush(), см. AnalyticsTransport.flushPendingRevocation. */
    private suspend fun flushPendingAnalyticsRevocation() {
        if (!FeatureFlags.analyticsTransportEnabled || !analyticsTransportConfigured) return
        analyticsTransport.flushPendingRevocation(
            peekPendingRevocation = { store.peekPendingAnalyticsRevocation() },
            clearPendingRevocation = { store.clearPendingAnalyticsRevocation() },
        )
    }

    /** Разметка МОМЕНТОВ (О-260817-06) — события только с согласия, см. AnalyticsRecorder. */
    val analytics = AnalyticsRecorder(
        isConsentGranted = { store.analyticsConsent.first() },
        enqueue = {
            store.enqueueAnalyticsEvent(it)
            if (FeatureFlags.analyticsTransportEnabled && analyticsTransportConfigured) analyticsTransport.flush()
        },
        deviceId = { store.analyticsDeviceId() },
    )

    init {
        if (FeatureFlags.analyticsTransportEnabled && !analyticsTransportConfigured) {
            android.util.Log.w(
                "AnalyticsTransport",
                "analyticsTransportEnabled=true, но ANALYTICS_INGEST_URL/ANALYTICS_INGEST_SECRET_MOMENTS не " +
                    "заданы сборкой (свойства Gradle analyticsIngestUrl/analyticsIngestSecretMoments) — " +
                    "события копятся в локальной очереди, но наружу не уходят.",
            )
        }
    }

    /**
     * Отзыв согласия (E-M1, контракт контура v2) — единственная точка входа
     * для UI (KompasRoot/ProfileScreen), которую вызывающий код обязан
     * звать вместо голого `store.setAnalyticsConsent(false)`.
     *
     * Порядок здесь и есть решение ловушки: [analytics].buildConsentRevokedEvent
     * строит конверт ПЕРВЫМ, пока согласие ещё true — не потому, что ему
     * это нужно (он согласие не спрашивает вовсе, см. его комментарий), а
     * потому, что результат нужен как аргумент [LocalStore.setAnalyticsConsent],
     * которая кладёт его в «карман» в той же транзакции, что стирает
     * обычную очередь и переключает флаг. Затем [flushPendingAnalyticsRevocation]
     * пробует отправить немедленно, тем же путём, которым отправился бы
     * непослушанный при прошлом запуске отзыв (не через [analyticsTransport]
     * .flush(), который сам откажется — согласие уже false).
     *
     * Дыры нет: обычная очередь стирается как и раньше
     * (`queueAfterConsentChange`, поток D), а карман — единственное, что
     * способно пережить это переключение флага — содержит ровно то, что в
     * него положил этот вызов, ничего содержательного (гарантия
     * `pendingRevocationAfterConsentChange` + сам факт, что записать что-то
     * ещё в очередь при согласии false не может никто — `AnalyticsRecorder.record`).
     */
    suspend fun revokeAnalyticsConsent() {
        val revocationEvent = analytics.buildConsentRevokedEvent()
        store.setAnalyticsConsent(granted = false, revocationEvent = revocationEvent)
        flushPendingAnalyticsRevocation()
    }

    /**
     * Отправляет одно событие в существующий приёмник ПРАКТИКИ; неудача не
     * бросает исключение. Секрет — заголовком `Authorization` (О-260817-17,
     * `verifyIngestSecret`), не только адрес: без заголовка приёмник отвечает
     * 401 всегда, независимо от валидности события. Принятым считается не
     * код состояния 2xx сам по себе, а тело ответа с `accepted: true`
     * ([isIngestResponseAccepted]) — при одиночном событии приёмник отвечает
     * 200 и на честный отказ тоже.
     */
    private suspend fun postAnalyticsEvent(eventJson: String): Boolean = withContext(Dispatchers.IO) {
        if (!analyticsTransportConfigured) return@withContext false
        try {
            val connection = URL(BuildConfig.ANALYTICS_INGEST_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.ANALYTICS_INGEST_SECRET_MOMENTS}")
            connection.outputStream.use { it.write(eventJson.toByteArray(Charsets.UTF_8)) }
            val statusCode = connection.responseCode
            val body = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: ""
            connection.disconnect()
            isIngestResponseAccepted(statusCode, body)
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Довезти накопленную очередь, если сеть/согласие уже позволяют
     * (например, при старте приложения). Сначала — неотправленный отзыв
     * прошлой сессии (E-M1: устройство было офлайн в момент отзыва), он не
     * зависит от согласия; потом обычная очередь, которая от него зависит.
     * Порядок не влияет на корректность (обе части независимы), только на
     * то, что уходит раньше при восстановлении сети.
     */
    fun flushAnalyticsQueue() {
        if (!FeatureFlags.analyticsTransportEnabled || !analyticsTransportConfigured) return
        appScope.launch {
            flushPendingAnalyticsRevocation()
            analyticsTransport.flush()
        }
    }
}

class KompasApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        ru.cmpas.voice.data.PracticeCatalog.init(this)
        container = AppContainer(this)
        // Момент установки — только локальная метка времени, не отправляется
        // нигде сама по себе; нужна, чтобы app_installed, если согласие дадут
        // позже, ушло с реальным временем первого запуска.
        container.appScope.launch { container.store.ensureInstalledAt(System.currentTimeMillis()) }
        // Довезти то, что не ушло из-за отсутствия сети в прошлый раз (О-260817-14).
        container.flushAnalyticsQueue()
    }
}

/** Доступ к контейнеру из любого Context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as KompasApp).container
