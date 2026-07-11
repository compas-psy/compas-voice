package ru.cmpas.voice.ui.theme

import androidx.compose.ui.graphics.Color

// ════════════════════════════════════════════════════════════════
// КОМПАС · голосовые практики — палитра.
// Источник истины: design_handoff_kompas_mvp/README.md § Design Tokens.
// Тёмная тема — единственная в MVP. Акцент действий — ТОЛЬКО terracotta.
// ════════════════════════════════════════════════════════════════

// ── Фоны ────────────────────────────────────────────────────────
val BgGraphite   = Color(0xFF14171F) // основной фон (дом/каталог/профиль/итог)
val BgNight      = Color(0xFF0E1014) // плеер (играет/пауза), онбординг, пейволл
val BgNightDeep  = Color(0xFF0A0C10) // дом · ночь
val BgPlayerDim  = Color(0xFF07080B) // плеер · ночной режим
val BgFade       = Color(0xFF050609) // плеер · сонное угасание

// ── Поверхности ─────────────────────────────────────────────────
val Surface       = Color(0xFF1C202A) // плитки, карточки
val SurfaceAlt    = Color(0xFF1A1E27) // шит, карточки настроек, тренд
val SurfaceInset  = Color(0xFF12151C) // треки слайдеров/сегментов
val SurfaceActive = Color(0xFF2A3040) // активный сегмент, выбранный чип
val SurfaceNight  = Color(0xFF12151D) // плитки на ночной главной

// ── Акцент (единственный — terracotta) ──────────────────────────
val Terracotta      = Color(0xFFC98A6B) // CTA, активный таб, галочки, точки
val TerracottaDeep  = Color(0xFFB4744F) // прессы/градиенты
val TerracottaMuted = Color(0xFF8B7A6A) // terracotta на ночных экранах

// ── Вторичные / фоновые оттенки состояний ──────────────────────
val LogoGreen = Color(0xFF2F6152) // зелёный круг логотипа, зелёные пятна
val Smoky     = Color(0xFF5D6F84) // дымчато-серо-синий
val Indigo    = Color(0xFF4C5678) // фон практик про сон
val Ochre     = Color(0xFFB4744F) // фон «разговоры и решения»

// ── Текст ───────────────────────────────────────────────────────
val TextPrimary      = Color(0xFFF1EEE8) // тёплый оффвайт
val TextPrimaryNight = Color(0xFFE6E3DC) // текст на ночных экранах
val TextSecondary    = Color(0xFF9AA0AB) // подписи
val TextTertiary     = Color(0xFF7F8590) // мелкие подписи, неактивные табы

// Полупрозрачные нейтральные поверхности (нейтральные кнопки/пилюли/оверлеи)
val WhiteAlpha06 = Color(0x0FFFFFFF) // ~6%
val WhiteAlpha08 = Color(0x14FFFFFF) // ~8%
val WhiteAlpha10 = Color(0x1AFFFFFF) // ~10%
val WhiteAlpha14 = Color(0x24FFFFFF) // ~14%
