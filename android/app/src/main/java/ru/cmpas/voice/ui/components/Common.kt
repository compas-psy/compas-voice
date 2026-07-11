package ru.cmpas.voice.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.cmpas.voice.ui.theme.Dimens
import ru.cmpas.voice.ui.theme.EyebrowStyle
import ru.cmpas.voice.ui.theme.SurfaceActive
import ru.cmpas.voice.ui.theme.SurfaceInset
import ru.cmpas.voice.ui.theme.Terracotta
import ru.cmpas.voice.ui.theme.TextPrimary
import ru.cmpas.voice.ui.theme.TextSecondary
import ru.cmpas.voice.ui.theme.TextTertiary
import ru.cmpas.voice.ui.theme.WhiteAlpha08
import ru.cmpas.voice.ui.theme.WhiteAlpha10

/** clickable с лёгким press-scale (0.975), без ripple — для спокойного UI. */
@Composable
fun Modifier.pressClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** Eyebrow — «СЕКЦИЯ · ВЫХОД ИЗ ДНЯ». */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = TextTertiary) {
    Text(text.uppercase(), style = EyebrowStyle, color = color, modifier = modifier)
}

/** Главный CTA — единственный акцент terracotta. С тактильной отдачей. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: Boolean = true,
) {
    val hf = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .clip(RoundedCornerShape(Dimens.ctaRadius))
            .background(if (enabled) Terracotta else Terracotta.copy(alpha = 0.4f))
            .pressClickable(enabled = enabled) {
                if (haptic) hf.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color(0xFF231A14),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
    }
}

/** Нейтральная (призрачная) кнопка — «Далее», «Пока не сейчас». */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .clip(RoundedCornerShape(Dimens.ctaRadius))
            .background(WhiteAlpha10)
            .pressClickable { onClick() }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = TextPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
    }
}

/** Мелкая текстовая ссылка — «Пропустить чек-ин», «Пока не сейчас». */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        color = TextSecondary,
        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pressClickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/** Сегмент-контрол (длительность 5/12/20, фон голос/мягкий). */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceInset)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSel = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSel) SurfaceActive else Color.Transparent)
                    .pressClickable { onSelect(option) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = if (isSel) TextPrimary else TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Пилюля-чип (длительности на карточке каталога — информативные). */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.pillRadius))
            .background(WhiteAlpha08)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = TextSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }
}

/** Индикатор страниц онбординга — активная точка terracotta, шире. */
@Composable
fun PageDots(count: Int, active: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val isActive = i == active
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (isActive) 22.dp else 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isActive) Terracotta else TextTertiary.copy(alpha = 0.4f)),
            )
        }
    }
}
