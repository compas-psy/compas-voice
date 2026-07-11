package ru.cmpas.voice.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.cmpas.voice.ui.components.pressClickable
import ru.cmpas.voice.ui.theme.SurfaceActive
import ru.cmpas.voice.ui.theme.SurfaceAlt
import ru.cmpas.voice.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerPicker(
    current: Int?,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SurfaceAlt) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 26.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Таймер сна", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            val options = listOf(10, 20, 30, 45)
            options.forEach { min ->
                TimerOption(selected = current == min, label = "$min минут") { onPick(min) }
                Spacer(Modifier.height(8.dp))
            }
            TimerOption(selected = current == null, label = "Выключить") { onPick(null) }
        }
    }
}

@Composable
private fun TimerOption(selected: Boolean, label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) SurfaceActive else Color.Transparent)
            .pressClickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}
