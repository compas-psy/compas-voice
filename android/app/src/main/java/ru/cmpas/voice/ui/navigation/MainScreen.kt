package ru.cmpas.voice.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ru.cmpas.voice.AppContainer
import ru.cmpas.voice.data.Background
import ru.cmpas.voice.data.Practice
import ru.cmpas.voice.data.PracticeCatalog
import ru.cmpas.voice.data.SessionConfig
import ru.cmpas.voice.data.Settings
import ru.cmpas.voice.data.TimeOfDay
import ru.cmpas.voice.ui.catalog.CatalogScreen
import ru.cmpas.voice.ui.catalog.StartSheet
import ru.cmpas.voice.ui.components.TabBar
import ru.cmpas.voice.ui.home.HomeScreen
import ru.cmpas.voice.ui.profile.ProfileScreen
import ru.cmpas.voice.ui.theme.BgGraphite
import ru.cmpas.voice.ui.theme.BgNightDeep
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek

@Composable
fun MainScreen(
    container: AppContainer,
    onStartPractice: (Practice, SessionConfig, Int?) -> Unit,
    onOpenPaywall: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheetPracticeId by remember { mutableStateOf<String?>(null) }
    val settings by container.store.settings.collectAsState(initial = Settings())

    // Время суток пересчитывается при возврате в приложение (docs/PRODUCT.md §3).
    var time by remember { mutableStateOf(currentTimeOfDay()) }
    var dayLabel by remember { mutableStateOf(currentDayLabel()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        time = currentTimeOfDay()
        dayLabel = currentDayLabel()
    }

    val night = tab == 0 && time == TimeOfDay.NIGHT
    val barBg = if (night) BgNightDeep else BgGraphite

    Box(Modifier.fillMaxSize()) {
        when (tab) {
            0 -> HomeScreen(time = time, dayLabel = dayLabel, onOpenPractice = { sheetPracticeId = it })
            1 -> CatalogScreen(onOpenPractice = { sheetPracticeId = it })
            else -> ProfileScreen(container = container, nowMs = System.currentTimeMillis(), onOpenPaywall = onOpenPaywall)
        }

        TabBar(
            selected = tab,
            onSelect = { tab = it },
            night = night,
            background = barBg,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val sheetPractice: Practice? = sheetPracticeId?.let { PracticeCatalog.byId(it) }
    if (sheetPractice != null) {
        StartSheet(
            practice = sheetPractice,
            defaultBackground = settings.defaultBackground,
            onDismiss = { sheetPracticeId = null },
            onStart = { config, checkIn ->
                sheetPracticeId = null
                onStartPractice(sheetPractice, config, checkIn)
            },
        )
    }
}

private fun currentTimeOfDay(): TimeOfDay = TimeOfDay.of(LocalTime.now().hour)

private fun currentDayLabel(): String = when (LocalDate.now().dayOfWeek) {
    DayOfWeek.MONDAY -> "Понедельник"
    DayOfWeek.TUESDAY -> "Вторник"
    DayOfWeek.WEDNESDAY -> "Среда"
    DayOfWeek.THURSDAY -> "Четверг"
    DayOfWeek.FRIDAY -> "Пятница"
    DayOfWeek.SATURDAY -> "Суббота"
    DayOfWeek.SUNDAY -> "Воскресенье"
}
