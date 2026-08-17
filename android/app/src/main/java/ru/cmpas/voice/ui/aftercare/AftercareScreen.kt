package ru.cmpas.voice.ui.aftercare

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.cmpas.voice.audio.PlayerController
import ru.cmpas.voice.ui.components.CheckInSlider
import ru.cmpas.voice.ui.components.PrimaryButton
import ru.cmpas.voice.ui.components.TextLink
import ru.cmpas.voice.ui.components.checkInToRecord
import ru.cmpas.voice.ui.theme.BgGraphite
import ru.cmpas.voice.ui.theme.SurfaceAlt
import ru.cmpas.voice.ui.theme.Terracotta
import ru.cmpas.voice.ui.theme.TextPrimary
import ru.cmpas.voice.ui.theme.TextSecondary

@Composable
fun AftercareScreen(controller: PlayerController, onDone: (Int?) -> Unit) {
    val state by controller.state.collectAsState()
    val before = state.checkInBefore
    var after by remember { mutableIntStateOf(before ?: 5) }
    var touched by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgGraphite),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 26.dp)
                .padding(top = 24.dp, bottom = 20.dp),
        ) {
            Text("Как сейчас?", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Без правильных ответов. Просто отметь.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)

            Spacer(Modifier.height(36.dp))
            CheckInSlider(
                question = "Насколько сейчас напряжённо?",
                value = after,
                onValueChange = { after = it; touched = true },
            )

            // Сравнение показываем, если был чек-ин ДО и слайдер «после» тронули — обновляется
            // вживую при движении. Не выдумываем «после» из дефолта, который не трогали.
            if (before != null && touched) {
                Spacer(Modifier.height(28.dp))
                ComparisonCard(before = before, after = after)
            }

            Spacer(Modifier.weight(1f))
            // «Готово» пишет отметку, только если было касание слайдера; «Пропустить» — без неё.
            PrimaryButton("Готово") { onDone(checkInToRecord(after, touched)) }
            Spacer(Modifier.height(6.dp))
            TextLink("Пропустить", onClick = { onDone(null) }, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun ComparisonCard(before: Int, after: Int) {
    val caption = reliefCaption(before, after)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceAlt)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text("$before", style = MaterialTheme.typography.headlineMedium, color = TextSecondary)
            Text("  →  ", style = MaterialTheme.typography.headlineMedium, color = Terracotta)
            Text("$after", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.width(14.dp))
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

/**
 * Тёплая, не оценивающая фраза по стартовому уровню [before] и разнице [after]−[before].
 * Без gamification и «правильных ответов»: одинаково бережно и когда отпустило, и
 * когда стало ярче (иногда сначала обостряется — это тоже нормально).
 */
private fun reliefCaption(before: Int, after: Int): String {
    val delta = after - before // <0 — стало легче
    return when {
        delta <= -4 -> "с сильного напряжения — заметно отпустило"
        delta == -3 -> "стало ощутимо легче"
        delta == -2 -> "немного отпустило"
        delta == -1 -> "чуть мягче, чем было"
        delta == 0 && before <= 3 -> "спокойно — и осталось спокойно"
        delta == 0 && before >= 7 -> "остаёшься с этим — и это уже смелость"
        delta == 0 -> "побыл с этим. Это тоже важно"
        delta in 1..2 -> "иногда сначала становится ярче, прежде чем отпустит"
        else -> "бывает и так. Ты заметил — и не отвернулся"
    }
}
