package ru.cmpas.voice.ui.home

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.cmpas.voice.data.HomeTile
import ru.cmpas.voice.data.PracticeCatalog
import ru.cmpas.voice.data.TimeOfDay
import ru.cmpas.voice.ui.components.AutoResizeText
import ru.cmpas.voice.ui.components.BreathingBackground
import ru.cmpas.voice.ui.components.Eyebrow
import ru.cmpas.voice.ui.components.LogoMark
import ru.cmpas.voice.ui.components.familyCardColors
import ru.cmpas.voice.ui.components.pressClickable
import ru.cmpas.voice.ui.components.tileBlobs
import ru.cmpas.voice.ui.theme.BgGraphite
import ru.cmpas.voice.ui.theme.BgNightDeep
import ru.cmpas.voice.ui.theme.Surface
import ru.cmpas.voice.ui.theme.SurfaceNight
import ru.cmpas.voice.ui.theme.TextPrimary
import ru.cmpas.voice.ui.theme.TextPrimaryNight
import ru.cmpas.voice.ui.theme.TextSecondary
import ru.cmpas.voice.ui.theme.TextTertiary

@Composable
fun HomeScreen(
    time: TimeOfDay,
    dayLabel: String,
    onOpenPractice: (String) -> Unit,
) {
    val night = time == TimeOfDay.NIGHT
    val bg = if (night) BgNightDeep else BgGraphite
    val textPrimary = if (night) TextPrimaryNight else TextPrimary

    val title = if (night) "Не спится?" else "Что сейчас?"
    val subtitle = when (time) {
        TimeOfDay.DAY -> "Выбери, из чего хочется выйти."
        TimeOfDay.EVENING -> "Выбери, из чего хочется выйти."
        TimeOfDay.NIGHT -> "Ничего не нужно решать. Просто выбери."
    }
    val period = when (time) {
        TimeOfDay.DAY -> "день"
        TimeOfDay.EVENING -> "вечер"
        TimeOfDay.NIGHT -> "ночь"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 26.dp)
                .padding(top = 12.dp, bottom = 120.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("$dayLabel · $period", color = TextTertiary)
                    Spacer(Modifier.height(10.dp))
                    Text(title, style = MaterialTheme.typography.displaySmall, color = textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }
                LogoMark(size = if (night) 34.dp else 38.dp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(22.dp))

            val tiles = PracticeCatalog.tilesFor(time)
            if (night) {
                tiles.forEach { tile ->
                    StateTileView(tile, height = 104.dp, night = true, big = true, onOpenPractice)
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                // Сетка 2×N + возможная широкая плитка (wide).
                val grid = tiles.filter { !it.wide }
                val wide = tiles.filter { it.wide }
                grid.chunked(2).forEach { rowTiles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowTiles.forEach { tile ->
                            Box(Modifier.weight(1f)) {
                                StateTileView(tile, height = 138.dp, night = false, big = false, onOpenPractice)
                            }
                        }
                        if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                wide.forEach { tile ->
                    StateTileView(tile, height = 96.dp, night = false, big = false, onOpenPractice)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun StateTileView(
    tile: HomeTile,
    height: androidx.compose.ui.unit.Dp,
    night: Boolean,
    big: Boolean,
    onOpenPractice: (String) -> Unit,
) {
    val practice = PracticeCatalog.byId(tile.practiceId)
    val surface = if (night) SurfaceNight else Surface
    val textPrimary = if (night) TextPrimaryNight else TextPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (!night && practice != null)
                    Modifier.background(Brush.linearGradient(familyCardColors(practice.group)))
                else Modifier.background(surface)
            )
            .pressClickable { onOpenPractice(tile.practiceId) },
    ) {
        if (practice != null) {
            BreathingBackground(
                blobs = tileBlobs(practice.group),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (tile.caption.isNotEmpty()) {
                Text(
                    tile.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.height(1.dp))
            }
            AutoResizeText(
                tile.title,
                style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = textPrimary,
                maxLines = 2,
            )
        }
    }
}
