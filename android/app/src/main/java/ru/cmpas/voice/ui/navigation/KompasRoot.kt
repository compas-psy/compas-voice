package ru.cmpas.voice.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.cmpas.voice.AppContainer
import ru.cmpas.voice.data.HistoryEntry
import ru.cmpas.voice.data.SubscriptionStatus
import ru.cmpas.voice.ui.aftercare.AftercareScreen
import ru.cmpas.voice.ui.components.AnalyticsConsentDialog
import ru.cmpas.voice.ui.onboarding.OnboardingScreen
import ru.cmpas.voice.ui.paywall.PaywallScreen
import ru.cmpas.voice.ui.player.PlayerScreen
import ru.cmpas.voice.ui.theme.BgGraphite

private enum class Screen { ONBOARDING, MAIN, PLAYER, AFTERCARE, PAYWALL }

@Composable
fun KompasRoot(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val onboarding by container.store.onboardingCompleted.collectAsState(initial = null)
    val ob = onboarding

    if (ob == null) {
        Box(Modifier.fillMaxSize().background(BgGraphite))
        return
    }

    var screen by remember(ob) {
        mutableStateOf(if (ob) Screen.MAIN else Screen.ONBOARDING)
    }

    // Согласие на аналитику (О-260817-06) — спрашивается один раз, после онбординга.
    val consentAsked by container.store.analyticsConsentAsked.collectAsState(initial = true)

    // Завершение практики: запись истории локально + маршрут (пейволл/дом).
    fun finishAndRoute(checkInAfter: Int?) {
        scope.launch {
            val s = container.player.state.value
            val wasFirst = !container.store.firstPracticeDone.first()
            val paywallSeen = container.store.paywallSeen.first()
            if (s.practiceId != null) {
                container.store.addHistory(
                    HistoryEntry(
                        practiceId = s.practiceId,
                        title = s.title,
                        group = s.group?.name ?: "",
                        durationMin = (s.durationMs / 60_000L).toInt(),
                        startedAtEpochMs = s.startedAtEpochMs,
                        checkInBefore = s.checkInBefore,
                        checkInAfter = checkInAfter,
                        isSleep = s.isSleep,
                    )
                )
                container.analytics.recordPracticeFinished(
                    practiceId = s.practiceId,
                    group = s.group?.name ?: "",
                    isSleep = s.isSleep,
                    completionPct = (s.fraction * 100).toInt(),
                )
            }
            container.player.stop()
            screen = if (wasFirst && !paywallSeen) Screen.PAYWALL else Screen.MAIN
        }
    }

    when (screen) {
        Screen.ONBOARDING -> OnboardingScreen(
            onDone = { key -> scope.launch { container.store.completeOnboarding(key) } },
            // completeOnboarding переключит onboarding→true, что пересоздаст экран в MAIN
        )

        Screen.MAIN -> MainScreen(
            container = container,
            onStartPractice = { practice, config, checkInBefore ->
                container.player.start(practice, config, checkInBefore, System.currentTimeMillis())
                scope.launch {
                    container.analytics.recordPracticeStarted(
                        practiceId = practice.id,
                        group = practice.group.name,
                        isSleep = practice.isSleep,
                    )
                }
                screen = Screen.PLAYER
            },
            onOpenPaywall = { screen = Screen.PAYWALL },
        )

        Screen.PLAYER -> {
            BackHandler {
                val s = container.player.state.value
                val meaningful = !s.isSleep && s.positionMs > 30_000L
                when {
                    s.isSleep -> finishAndRoute(null)
                    meaningful -> screen = Screen.AFTERCARE
                    else -> { container.player.stop(); screen = Screen.MAIN }
                }
            }
            PlayerScreen(
                controller = container.player,
                onGoAftercare = { screen = Screen.AFTERCARE },
                onSleepDone = { finishAndRoute(null) },
                onExit = { container.player.stop(); screen = Screen.MAIN },
            )
        }

        Screen.AFTERCARE -> {
            BackHandler { finishAndRoute(null) }
            AftercareScreen(
                controller = container.player,
                onDone = { after -> finishAndRoute(after) },
            )
        }

        Screen.PAYWALL -> {
            BackHandler {
                scope.launch { container.store.markPaywallSeen() }
                screen = Screen.MAIN
            }
            PaywallScreen(
                onTrial = {
                    scope.launch {
                        container.store.setSubscription(SubscriptionStatus.TRIAL)
                        container.store.markPaywallSeen()
                    }
                    screen = Screen.MAIN
                },
                onDismiss = {
                    scope.launch { container.store.markPaywallSeen() }
                    screen = Screen.MAIN
                },
            )
        }
    }

    if (ob && screen == Screen.MAIN && !consentAsked) {
        AnalyticsConsentDialog(
            onAllow = {
                scope.launch {
                    container.store.setAnalyticsConsent(true)
                    container.store.markAnalyticsConsentAsked()
                    val installedAt = container.store.ensureInstalledAt(System.currentTimeMillis())
                    container.analytics.recordAppInstalled(installedAt)
                }
            },
            onDecline = {
                scope.launch { container.store.markAnalyticsConsentAsked() }
            },
        )
    }
}
