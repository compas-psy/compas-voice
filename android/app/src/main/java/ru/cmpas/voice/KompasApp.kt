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

    /** Довозит очередь до приёмника ПРАКТИКИ (О-260817-14), см. AnalyticsTransport. */
    private val analyticsTransport = AnalyticsTransport(
        isConsentGranted = { store.analyticsConsent.first() },
        peekQueue = { store.peekAnalyticsQueue(it) },
        removeSent = { store.removeAnalyticsEvents(it) },
        sendOne = { postAnalyticsEvent(it) },
    )

    /** Разметка МОМЕНТОВ (О-260817-06) — события только с согласия, см. AnalyticsRecorder. */
    val analytics = AnalyticsRecorder(
        isConsentGranted = { store.analyticsConsent.first() },
        enqueue = {
            store.enqueueAnalyticsEvent(it)
            if (FeatureFlags.analyticsTransportEnabled) analyticsTransport.flush()
        },
        deviceId = { store.analyticsDeviceId() },
    )

    /** Отправляет одно событие в существующий приёмник ПРАКТИКИ; неудача не бросает исключение. */
    private suspend fun postAnalyticsEvent(eventJson: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = URL(BuildConfig.ANALYTICS_INGEST_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(eventJson.toByteArray(Charsets.UTF_8)) }
            val accepted = connection.responseCode in 200..299
            connection.disconnect()
            accepted
        } catch (e: IOException) {
            false
        }
    }

    /** Довезти накопленную очередь, если сеть/согласие уже позволяют (например, при старте приложения). */
    fun flushAnalyticsQueue() {
        if (!FeatureFlags.analyticsTransportEnabled) return
        appScope.launch { analyticsTransport.flush() }
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
