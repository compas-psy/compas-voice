package ru.cmpas.voice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

// Тёмная тема — единственная в MVP. Материальную схему настраиваем в тёмных
// тонах graphite/terracotta; большинство экранов рисуют цвета из токенов Color.kt
// напрямую, а MaterialTheme даёт дефолты для системных компонентов.
private val KompasDarkColors = darkColorScheme(
    primary = Terracotta,
    onPrimary = BgNight,
    secondary = Smoky,
    background = BgGraphite,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceAlt,
    onSurfaceVariant = TextSecondary,
    outline = WhiteAlpha14,
)

/** Ночной режим экрана (дом · ночь, плеер · ночь) — чуть приглушённее. */
val LocalIsNight = staticCompositionLocalOf { false }

@Composable
fun KompasTheme(
    isNight: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalIsNight provides isNight) {
        MaterialTheme(
            colorScheme = KompasDarkColors,
            typography = KompasTypography,
            shapes = KompasShapes,
            content = content,
        )
    }
}
