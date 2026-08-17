package ru.cmpas.voice

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.cmpas.voice.analytics.AnalyticsRecorder
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

    /** Разметка МОМЕНТОВ (О-260817-06) — события только с согласия, см. AnalyticsRecorder. */
    val analytics = AnalyticsRecorder(
        isConsentGranted = { store.analyticsConsent.first() },
        enqueue = { store.enqueueAnalyticsEvent(it) },
        deviceId = { store.analyticsDeviceId() },
    )
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
    }
}

/** Доступ к контейнеру из любого Context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as KompasApp).container
