package ru.cmpas.voice.ui.paywall

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.cmpas.voice.ui.components.Blob
import ru.cmpas.voice.ui.components.BreathingBackground
import ru.cmpas.voice.ui.components.PrimaryButton
import ru.cmpas.voice.ui.components.TextLink
import ru.cmpas.voice.ui.theme.BgNight
import ru.cmpas.voice.ui.theme.LogoGreen
import ru.cmpas.voice.ui.theme.Smoky
import ru.cmpas.voice.ui.theme.Terracotta
import ru.cmpas.voice.ui.theme.TextPrimary
import ru.cmpas.voice.ui.theme.TextSecondary
import ru.cmpas.voice.ui.theme.TextTertiary

@Composable
fun PaywallScreen(onTrial: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgNight),
    ) {
        BreathingBackground(
            blobs = listOf(
                Blob(Terracotta, 0.3f, 0.28f, 0.7f, 0.30f),
                Blob(Smoky, 0.75f, 0.6f, 0.6f, 0.20f),
                Blob(LogoGreen, 0.5f, 0.92f, 0.5f, 0.18f),
            ),
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 26.dp)
                .padding(top = 24.dp, bottom = 20.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Text("Первая практика — за нами.", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Text(
                "Дальше — если захочешь остаться. Без спешки.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )

            Spacer(Modifier.height(28.dp))
            listOf(
                "Весь каталог состояний",
                "Новые практики каждую неделю",
                "Таймер сна и ночной режим",
            ).forEach { benefit ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 7.dp)) {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(Terracotta),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = BgNight, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.height(0.dp))
                    Text(
                        benefit,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            PrimaryButton("Попробовать 7 дней бесплатно") { onTrial() }
            Spacer(Modifier.height(12.dp))
            Text(
                "Потом 399 ₽ в месяц. Отменить можно в любой момент.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            TextLink("Пока не сейчас", onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
