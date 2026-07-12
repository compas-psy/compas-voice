package ru.cmpas.voice.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.cmpas.voice.ui.components.PrimaryButton
import ru.cmpas.voice.ui.components.TextLink
import ru.cmpas.voice.ui.theme.SurfaceAlt
import ru.cmpas.voice.ui.theme.SurfaceInset
import ru.cmpas.voice.ui.theme.Terracotta
import ru.cmpas.voice.ui.theme.TextPrimary
import ru.cmpas.voice.ui.theme.TextSecondary

private val GreenPlaqueBg = Color(0x242F6152)   // rgba(47,97,82,.14)
private val GreenPlaqueBorder = Color(0x4D2F6152) // rgba(47,97,82,.3)
private val GreenPlaqueIcon = Color(0xFF7FBFA3)
private val TerraPlaqueBg = Color(0x1FC98A6B)   // rgba(201,138,107,.12)
private val TerraPlaqueBorder = Color(0x4DC98A6B) // rgba(201,138,107,.3)
private val TerraSquareBg = Color(0x29C98A6B)   // rgba(201,138,107,.16)

/** 7b/7c: плашка статуса наушников под рядом «Фон» при выборе «Объёмный». */
@Composable
fun BinauralStatusPlaque(headphones: Boolean, modifier: Modifier = Modifier) {
    val bg = if (headphones) GreenPlaqueBg else TerraPlaqueBg
    val border = if (headphones) GreenPlaqueBorder else TerraPlaqueBorder
    val iconTint = if (headphones) GreenPlaqueIcon else Terracotta
    val text = if (headphones) {
        "Наушники подключены — фон будет объёмным."
    } else {
        "Подключи наушники — включу объём автоматически. Пока играет мягкий фон."
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Headphones, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

/** 7a: фокус-объяснитель «Объёмного» фона (показывается один раз). */
@Composable
fun BinauralExplainerDialog(onOk: () -> Unit, onNotNow: () -> Unit) {
    Dialog(onDismissRequest = onNotNow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceAlt)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TerraSquareBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Headphones, contentDescription = null, tint = Terracotta, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Объёмный фон", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Text(
                "Мягкий стереозвук, который тихо течёт под голосом и создаёт объём. " +
                    "Не лечебные частоты и не магия — просто атмосфера. Работает в наушниках.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceInset)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Headphones, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Нужны наушники — в динамиках эффекта нет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Хорошо") { onOk() }
            Spacer(Modifier.height(4.dp))
            TextLink("Не сейчас", onClick = onNotNow, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
