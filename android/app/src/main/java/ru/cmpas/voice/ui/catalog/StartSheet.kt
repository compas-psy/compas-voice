package ru.cmpas.voice.ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.cmpas.voice.data.Background
import ru.cmpas.voice.data.FeatureFlags
import ru.cmpas.voice.data.Practice
import ru.cmpas.voice.data.SessionConfig
import ru.cmpas.voice.ui.components.CheckInSlider
import ru.cmpas.voice.ui.components.Eyebrow
import ru.cmpas.voice.ui.components.PrimaryButton
import ru.cmpas.voice.ui.components.Segmented
import ru.cmpas.voice.ui.components.TextLink
import ru.cmpas.voice.ui.components.rememberHeadphonesConnected
import ru.cmpas.voice.ui.theme.SurfaceAlt
import ru.cmpas.voice.ui.theme.TextPrimary
import ru.cmpas.voice.ui.theme.TextSecondary
import ru.cmpas.voice.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSheet(
    practice: Practice,
    defaultBackground: Background,
    explainerSeen: Boolean,
    onMarkExplainerSeen: () -> Unit,
    onDismiss: () -> Unit,
    onStart: (SessionConfig, Int?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val headphones = rememberHeadphonesConnected()
    var showExplainer by remember { mutableStateOf(false) }
    var option by remember { mutableStateOf(practice.durations.first()) }
    var background by remember { mutableStateOf(defaultBackground) }
    var checkIn by remember { mutableIntStateOf(5) }
    var touched by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceAlt,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 26.dp)
                .padding(bottom = 20.dp),
        ) {
            Eyebrow(practice.group.title)
            Spacer(Modifier.height(8.dp))
            Text(practice.title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)

            Spacer(Modifier.height(22.dp))
            Text("Длительность", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            if (practice.durations.size <= 1) {
                // Одна версия — просто подпись, не селектор (ТЗ аудио v2 §1).
                Text(
                    "${practice.durations.first().label} мин",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            } else {
                Segmented(
                    options = practice.durations,
                    selected = option,
                    onSelect = { option = it },
                    label = { "${it.label} мин" },
                )
            }

            Spacer(Modifier.height(18.dp))
            Text("Фон", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            // «Объёмный» появляется только за флагом spatialAudio.
            val bgOptions = if (FeatureFlags.spatialAudio) {
                listOf(Background.VOICE, Background.SOFT, Background.BINAURAL)
            } else {
                listOf(Background.VOICE, Background.SOFT)
            }
            Segmented(
                options = bgOptions,
                selected = background,
                onSelect = { sel ->
                    background = sel
                    // 7a — объяснитель при первом выборе «Объёмного».
                    if (sel == Background.BINAURAL && !explainerSeen) showExplainer = true
                },
                label = { it.title },
            )
            if (background == Background.BINAURAL) {
                Spacer(Modifier.height(10.dp))
                BinauralStatusPlaque(headphones) // 7b/7c по маршруту наушников
            }

            if (!skipped) {
                Spacer(Modifier.height(22.dp))
                CheckInSlider(
                    question = "Насколько сейчас напряжённо?",
                    value = checkIn,
                    onValueChange = { checkIn = it; touched = true },
                )
            }

            Spacer(Modifier.height(24.dp))
            PrimaryButton("Начать") {
                onStart(SessionConfig(option, background), if (touched && !skipped) checkIn else null)
            }

            if (!skipped) {
                Spacer(Modifier.height(6.dp))
                TextLink(
                    "Пропустить чек-ин",
                    onClick = { skipped = true; touched = false },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showExplainer) {
        BinauralExplainerDialog(
            onOk = { onMarkExplainerSeen(); showExplainer = false },
            onNotNow = {
                onMarkExplainerSeen()
                background = Background.SOFT
                showExplainer = false
            },
        )
    }
}
