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
import ru.cmpas.voice.ui.theme.SurfaceAlt
import ru.cmpas.voice.ui.theme.TextPrimary
import ru.cmpas.voice.ui.theme.TextSecondary
import ru.cmpas.voice.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSheet(
    practice: Practice,
    defaultBackground: Background,
    onDismiss: () -> Unit,
    onStart: (SessionConfig, Int?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var duration by remember { mutableIntStateOf(if (12 in practice.durations) 12 else practice.durations.first()) }
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
            Segmented(
                options = practice.durations,
                selected = duration,
                onSelect = { duration = it },
                label = { "$it мин" },
            )

            Spacer(Modifier.height(18.dp))
            Text("Фон", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            // «Бинауральный» появляется только за флагом spatialAudio (OFF в MVP).
            val bgOptions = if (FeatureFlags.spatialAudio) {
                listOf(Background.VOICE, Background.SOFT, Background.BINAURAL)
            } else {
                listOf(Background.VOICE, Background.SOFT)
            }
            Segmented(
                options = bgOptions,
                selected = background,
                onSelect = { background = it },
                label = { it.title },
            )
            if (background == Background.BINAURAL) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Headphones,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Мягкий стереозвук под голосом. Нужны наушники — в динамиках эффекта нет.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
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
                onStart(SessionConfig(duration, background), if (touched && !skipped) checkIn else null)
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
}
