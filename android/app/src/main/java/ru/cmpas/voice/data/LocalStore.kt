package ru.cmpas.voice.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.cmpas.voice.analytics.queueAfterConsentChange

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kompas")

/**
 * Локальное хранилище (DataStore). Все данные — на устройстве, без бэкенда
 * (см. docs/PRIVACY-DPO.md). Чек-ины и историю не логируем.
 */
class LocalStore(context: Context) {

    private val ds = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val selectedState = stringPreferencesKey("selected_state")
        val defaultBackground = stringPreferencesKey("default_background")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val subscription = stringPreferencesKey("subscription_status")
        val firstPracticeDone = booleanPreferencesKey("first_practice_done")
        val paywallSeen = booleanPreferencesKey("paywall_seen")
        val binauralExplainerSeen = booleanPreferencesKey("binaural_explainer_seen")
        val history = stringPreferencesKey("history_json")
        val analyticsConsent = booleanPreferencesKey("analytics_consent")
        val analyticsConsentAsked = booleanPreferencesKey("analytics_consent_asked")
        val installedAtEpochMs = longPreferencesKey("installed_at_epoch_ms")
        val analyticsDeviceId = stringPreferencesKey("analytics_device_id")
        val analyticsQueue = stringPreferencesKey("analytics_queue_json")
    }

    private val prefs: Flow<Preferences> = ds.data.catch { emit(emptyPreferences()) }

    // ── Онбординг ───────────────────────────────────────────
    val onboardingCompleted: Flow<Boolean> = prefs.map { it[Keys.onboarding] ?: false }

    suspend fun completeOnboarding(selectedState: String) {
        ds.edit {
            it[Keys.onboarding] = true
            it[Keys.selectedState] = selectedState
        }
    }

    // ── Настройки ───────────────────────────────────────────
    val settings: Flow<Settings> = prefs.map { p ->
        Settings(
            defaultBackground = p[Keys.defaultBackground]
                ?.let { runCatching { Background.valueOf(it) }.getOrNull() }
                ?: Background.VOICE,
            keepScreenOn = p[Keys.keepScreenOn] ?: false,
            remindersEnabled = p[Keys.remindersEnabled] ?: false,
            subscriptionStatus = p[Keys.subscription]
                ?.let { runCatching { SubscriptionStatus.valueOf(it) }.getOrNull() }
                ?: SubscriptionStatus.FREE,
        )
    }

    suspend fun setDefaultBackground(background: Background) {
        ds.edit { it[Keys.defaultBackground] = background.name }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        ds.edit { it[Keys.keepScreenOn] = enabled }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        ds.edit { it[Keys.remindersEnabled] = enabled }
    }

    suspend fun setSubscription(status: SubscriptionStatus) {
        ds.edit { it[Keys.subscription] = status.name }
    }

    // ── Пейволл ─────────────────────────────────────────────
    val firstPracticeDone: Flow<Boolean> = prefs.map { it[Keys.firstPracticeDone] ?: false }
    val paywallSeen: Flow<Boolean> = prefs.map { it[Keys.paywallSeen] ?: false }

    suspend fun markPaywallSeen() {
        ds.edit { it[Keys.paywallSeen] = true }
    }

    // ── Объяснитель «Объёмного» фона (7a) — один раз ────────
    val binauralExplainerSeen: Flow<Boolean> = prefs.map { it[Keys.binauralExplainerSeen] ?: false }

    suspend fun markBinauralExplainerSeen() {
        ds.edit { it[Keys.binauralExplainerSeen] = true }
    }

    // ── История / чек-ины ───────────────────────────────────
    val history: Flow<List<HistoryEntry>> = prefs.map { p ->
        p[Keys.history]?.let {
            runCatching { json.decodeFromString<List<HistoryEntry>>(it) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun addHistory(entry: HistoryEntry) {
        ds.edit { p ->
            val current = p[Keys.history]
                ?.let { runCatching { json.decodeFromString<List<HistoryEntry>>(it) }.getOrNull() }
                ?: emptyList()
            val updated = (listOf(entry) + current).take(200)
            p[Keys.history] = json.encodeToString(updated)
            if (!(p[Keys.firstPracticeDone] ?: false)) p[Keys.firstPracticeDone] = true
        }
    }

    // ── LRU фоновых петель (persist на диск) ────────────────
    private fun loopKey(family: SoundFamily) = intPreferencesKey("loop_idx_${family.name}")

    /** Текущие сохранённые индексы последних петель по семьям (−1 = ещё не играли). */
    val loopIndices: Flow<Map<SoundFamily, Int>> = prefs.map { p ->
        SoundFamily.entries.associateWith { p[loopKey(it)] ?: -1 }
    }

    suspend fun setLoopIndex(family: SoundFamily, index: Int) {
        ds.edit { it[loopKey(family)] = index }
    }

    // ── Аналитика (О-260817-06) — только с явного отдельного согласия ──
    // Согласие спрашивается один раз, отдельно от чек-ина самочувствия; до
    // согласия ничего из этого блока не уходит в очередь (AnalyticsRecorder).
    val analyticsConsent: Flow<Boolean> = prefs.map { it[Keys.analyticsConsent] ?: false }
    val analyticsConsentAsked: Flow<Boolean> = prefs.map { it[Keys.analyticsConsentAsked] ?: false }

    suspend fun setAnalyticsConsent(granted: Boolean) {
        ds.edit { p ->
            p[Keys.analyticsConsent] = granted
            val current = p[Keys.analyticsQueue]
                ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                ?: emptyList()
            p[Keys.analyticsQueue] = json.encodeToString(queueAfterConsentChange(current, granted))
        }
    }

    suspend fun markAnalyticsConsentAsked() {
        ds.edit { it[Keys.analyticsConsentAsked] = true }
    }

    /**
     * Момент первого запуска — пишется локально всегда, независимо от согласия
     * (это не аналитическое событие, никуда не уходит). Идемпотентно: второй
     * вызов возвращает уже сохранённое значение. Нужен, чтобы `app_installed`,
     * если согласие дадут не сразу, ушло с реальным временем установки, а не
     * временем согласия — это то, что делает возможной связку «установка +
     * первая практика в тот же день» на стороне витрины.
     */
    suspend fun ensureInstalledAt(nowMs: Long): Long {
        var result = nowMs
        ds.edit { p ->
            val existing = p[Keys.installedAtEpochMs]
            if (existing != null) {
                result = existing
            } else {
                p[Keys.installedAtEpochMs] = nowMs
            }
        }
        return result
    }

    /** device_id для конверта события — создаётся один раз, живёт до «Очистить мои данные». */
    suspend fun analyticsDeviceId(): String {
        var id = ""
        ds.edit { p ->
            id = p[Keys.analyticsDeviceId] ?: java.util.UUID.randomUUID().toString().also {
                p[Keys.analyticsDeviceId] = it
            }
        }
        return id
    }

    /** Локальная очередь готовых к отправке событий, ждущих [AnalyticsTransport]. */
    suspend fun enqueueAnalyticsEvent(eventJson: String) {
        ds.edit { p ->
            val current = p[Keys.analyticsQueue]
                ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                ?: emptyList()
            val updated = (current + eventJson).takeLast(500)
            p[Keys.analyticsQueue] = json.encodeToString(updated)
        }
    }

    /** Старейшие [limit] событий очереди — [AnalyticsTransport] шлёт их по порядку (FIFO). */
    suspend fun peekAnalyticsQueue(limit: Int): List<String> {
        val current = ds.data.first()[Keys.analyticsQueue]
            ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            ?: emptyList()
        return current.take(limit)
    }

    /** Снимает [count] самых старых событий — вызывается после успешной отправки. */
    suspend fun removeAnalyticsEvents(count: Int) {
        if (count <= 0) return
        ds.edit { p ->
            val current = p[Keys.analyticsQueue]
                ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                ?: emptyList()
            p[Keys.analyticsQueue] = json.encodeToString(current.drop(count))
        }
    }

    /** Удаление всех пользовательских данных (право пользователя, DPO §6).
     *  Стирает и очередь аналитики, device_id и согласие — не только историю. */
    suspend fun clearAll() {
        ds.edit { it.clear() }
    }
}
