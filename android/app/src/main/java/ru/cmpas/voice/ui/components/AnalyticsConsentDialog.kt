package ru.cmpas.voice.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import ru.cmpas.voice.ui.theme.SurfaceAlt
import ru.cmpas.voice.ui.theme.Terracotta
import ru.cmpas.voice.ui.theme.TextSecondary

/**
 * Согласие на анонимную статистику использования (О-260817-06). Показывается
 * один раз, после онбординга. Намеренно отдельный экран с другими словами,
 * не путать с мягким чек-ином самочувствия в шите — это разные вещи и разные
 * согласия. Можно поменять решение позже в «Я → Настройки».
 */
@Composable
fun AnalyticsConsentDialog(onAllow: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Можно собирать анонимную статистику?") },
        text = {
            Text(
                "Какие практики запускают и дослушивают — без содержания, без имени, " +
                    "не привязано к тебе. Помогает понимать, что работает. Можно выключить " +
                    "в любой момент в «Я → Настройки».",
            )
        },
        confirmButton = {
            TextButton(onClick = onAllow) { Text("Можно", color = Terracotta) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("Не сейчас", color = TextSecondary) }
        },
        containerColor = SurfaceAlt,
    )
}
