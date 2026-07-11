package ru.cmpas.voice.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Системный шрифт (нативный) — как рекомендует ТЗ: SF Pro на iOS, системный на
// Android. Размеры в sp — полная поддержка Dynamic Type.
private val SystemFont = FontFamily.Default

val KompasTypography = Typography(
    // Large Title — «Что сейчас?»
    displaySmall = TextStyle(
        fontFamily = SystemFont, fontWeight = FontWeight.Bold,
        fontSize = 33.sp, lineHeight = 37.sp,
    ),
    // Заголовок плеера / онбординга
    headlineLarge = TextStyle(
        fontFamily = SystemFont, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 36.sp,
    ),
    // Заголовок экрана (шит, итог)
    headlineMedium = TextStyle(
        fontFamily = SystemFont, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 32.sp,
    ),
    // Заголовок группы каталога
    titleLarge = TextStyle(
        fontFamily = SystemFont, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    // Название плитки/карточки
    titleMedium = TextStyle(
        fontFamily = SystemFont, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp,
    ),
    // Body / описание
    bodyLarge = TextStyle(
        fontFamily = SystemFont, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SystemFont, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    // Подпись состояния
    bodySmall = TextStyle(
        fontFamily = SystemFont, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    // Таб-бар подпись
    labelSmall = TextStyle(
        fontFamily = SystemFont, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp,
    ),
)

// Eyebrow (СЕКЦИЯ, ВЫХОД ИЗ ДНЯ) — 12 / SemiBold / letter-spacing / UPPERCASE.
val EyebrowStyle = TextStyle(
    fontFamily = SystemFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.13.em,
)
