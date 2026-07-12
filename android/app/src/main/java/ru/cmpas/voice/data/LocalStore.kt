package ru.cmpas.voice.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val subscription = stringPreferencesKey("subscription_status")
        val firstPracticeDone = booleanPreferencesKey("first_practice_done")
        val paywallSeen = booleanPreferencesKey("paywall_seen")
        val binauralExplainerSeen = booleanPreferencesKey("binaural_explainer_seen")
        val history = stringPreferencesKey("history_json")
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
            remindersEnabled = p[Keys.remindersEnabled] ?: false,
            subscriptionStatus = p[Keys.subscription]
                ?.let { runCatching { SubscriptionStatus.valueOf(it) }.getOrNull() }
                ?: SubscriptionStatus.FREE,
        )
    }

    suspend fun setDefaultBackground(background: Background) {
        ds.edit { it[Keys.defaultBackground] = background.name }
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

    /** Удаление всех пользовательских данных (право пользователя, DPO §6). */
    suspend fun clearAll() {
        ds.edit { it.clear() }
    }
}
