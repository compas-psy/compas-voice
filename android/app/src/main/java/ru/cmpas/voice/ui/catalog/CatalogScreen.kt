package ru.cmpas.voice.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.cmpas.voice.data.Practice
import ru.cmpas.voice.data.PracticeCatalog
import ru.cmpas.voice.ui.components.BreathingBackground
import ru.cmpas.voice.ui.components.Chip
import ru.cmpas.voice.ui.components.Eyebrow
import ru.cmpas.voice.ui.components.pressClickable
import ru.cmpas.voice.ui.components.tileBlobs
import ru.cmpas.voice.ui.theme.BgGraphite
import ru.cmpas.voice.ui.theme.Surface
import ru.cmpas.voice.ui.theme.TextPrimary
import ru.cmpas.voice.ui.theme.TextSecondary
import ru.cmpas.voice.ui.theme.TextTertiary

@Composable
fun CatalogScreen(onOpenPractice: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgGraphite),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 26.dp)
                .padding(top = 12.dp, bottom = 120.dp),
        ) {
            Text("Практики", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            Spacer(Modifier.height(20.dp))

            PracticeCatalog.grouped().forEach { (group, items) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        practiceCountLabel(items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                items.forEach { practice ->
                    PracticeCard(practice, onOpenPractice)
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun PracticeCard(practice: Practice, onOpenPractice: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface)
            .pressClickable { onOpenPractice(practice.id) },
    ) {
        BreathingBackground(blobs = tileBlobs(practice.group), modifier = Modifier.fillMaxSize())
        Column(Modifier.padding(18.dp)) {
            Eyebrow(practice.group.title, color = TextTertiary)
            Spacer(Modifier.height(8.dp))
            Text(practice.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(practice.stateCaption, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                practice.durations.forEach { d -> Chip("$d мин") }
            }
        }
    }
}

private fun practiceCountLabel(n: Int): String {
    val form = when {
        n % 10 == 1 && n % 100 != 11 -> "практика"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "практики"
        else -> "практик"
    }
    return "$n $form"
}
