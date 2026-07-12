package ru.cmpas.voice.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * Текст, который сам уменьшает кегль, чтобы уместиться в [maxLines] по ширине
 * контейнера. Нужен на узких экранах: названия практик на плитках «Сейчас» не
 * должны некрасиво переноситься/обрезаться — на маленькой ширине шрифт мельче.
 *
 * Пошагово ужимает размер при переполнении (visual overflow) до [minScale];
 * сбрасывается при смене текста или базового стиля.
 */
@Composable
fun AutoResizeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    minScale: Float = 0.70f,
) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    Text(
        text = text,
        style = style.copy(fontSize = style.fontSize * scale),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && scale > minScale) {
                scale = (scale - 0.06f).coerceAtLeast(minScale)
            }
        },
    )
}
