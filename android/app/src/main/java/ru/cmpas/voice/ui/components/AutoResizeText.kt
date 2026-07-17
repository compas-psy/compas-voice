package ru.cmpas.voice.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

/**
 * Текст, который подбирает кегль под ширину контейнера. На «широких» экранах —
 * стандартный размер [style]; на узких кегль уменьшается ровно настолько, чтобы:
 *  1) самое длинное слово помещалось в одну строку (не было переносов внутри
 *     слова вроде «Перегружен(\nа)»);
 *  2) весь текст умещался в [maxLines].
 *
 * Меряем реальную раскладку через [rememberTextMeasurer] (не полагаемся на
 * visualOverflow, который не ловит некрасивый перенос внутри 2 строк). Подбор
 * идёт в `remember`, пересчитывается только при смене текста/ширины/стиля.
 */
@Composable
fun AutoResizeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    minScale: Float = 0.60f,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWpx = constraints.maxWidth
        val fontSize = remember(text, maxWpx, style, maxLines) {
            val base = style.fontSize
            if (maxWpx <= 0 || maxWpx == Int.MAX_VALUE) return@remember base
            val words = text.split(' ', '\n').filter { it.isNotEmpty() }
            var scale = 1f
            while (scale > minScale) {
                val s = style.copy(fontSize = base * scale)
                val widestWord = words.maxOfOrNull {
                    measurer.measure(text = it, style = s, softWrap = false, maxLines = 1).size.width
                } ?: 0
                val full = measurer.measure(
                    text = text,
                    style = s,
                    softWrap = true,
                    constraints = Constraints(maxWidth = maxWpx),
                )
                if (widestWord <= maxWpx && full.lineCount <= maxLines) break
                scale -= 0.05f
            }
            base * scale.coerceAtLeast(minScale)
        }
        Text(
            text = text,
            style = style.copy(fontSize = fontSize),
            color = color,
            maxLines = maxLines,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
