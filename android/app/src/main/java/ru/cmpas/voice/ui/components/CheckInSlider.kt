package ru.cmpas.voice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.cmpas.voice.ui.theme.SurfaceInset
import ru.cmpas.voice.ui.theme.Terracotta
import ru.cmpas.voice.ui.theme.TextPrimary
import ru.cmpas.voice.ui.theme.TextTertiary

/**
 * Мягкий чек-ин 0–10. Значение — Int; onChange вызывается при касании
 * (используем для отметки «чек-ин заполнен», см. docs/PRODUCT.md §5).
 */
@Composable
fun CheckInSlider(
    question: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(question, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = Terracotta,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(6.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..10f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = Terracotta,
                activeTrackColor = Terracotta,
                inactiveTrackColor = SurfaceInset,
                activeTickColor = Terracotta,
                inactiveTickColor = SurfaceInset,
            ),
            modifier = Modifier.fillMaxWidth().height(40.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 · спокойно", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            Text("10 · на пределе", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
    }
}
