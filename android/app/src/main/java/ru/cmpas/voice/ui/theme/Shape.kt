package ru.cmpas.voice.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Скругления из ТЗ: плитки/карточки 22; CTA/поля 18; шит 30; чипы/сегменты 11–14;
// пилюли 20.
val KompasShapes = Shapes(
    extraSmall = RoundedCornerShape(11.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

// Отступы и размеры — единые константы (Dimens).
object Dimens {
    val screenPadding = 26.dp   // горизонтальные паддинги контента
    val tileRadius = 22.dp
    val ctaRadius = 18.dp
    val sheetRadius = 30.dp
    val pillRadius = 20.dp
    val chipRadius = 12.dp
    val minTouch = 48.dp        // минимальная тач-цель
    val tileHeightDay = 138.dp
    val tileHeightNight = 104.dp
}
