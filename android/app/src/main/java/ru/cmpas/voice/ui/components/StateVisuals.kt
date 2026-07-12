package ru.cmpas.voice.ui.components

import androidx.compose.ui.graphics.Color
import ru.cmpas.voice.data.PracticeGroup
import ru.cmpas.voice.ui.theme.Indigo
import ru.cmpas.voice.ui.theme.LogoGreen
import ru.cmpas.voice.ui.theme.Ochre
import ru.cmpas.voice.ui.theme.Smoky
import ru.cmpas.voice.ui.theme.Teal
import ru.cmpas.voice.ui.theme.Terracotta

/**
 * Оттенок абстрактного пятна по состоянию (ТЗ § Фоны практик):
 * Сон → indigo, Выход из дня → terracotta, Тревога → green, Разговоры → ochre,
 * Опора → green.
 */
fun PracticeGroup.tint(): Color = when (this) {
    PracticeGroup.SLEEP -> Indigo
    PracticeGroup.EXIT_DAY -> Terracotta
    PracticeGroup.ANXIETY -> Teal
    PracticeGroup.TALKS -> Ochre
    PracticeGroup.SUPPORT -> LogoGreen
}

/**
 * Тёмный фон-градиент карточки/плитки в оттенке семейства (ТЗ §6 «свой
 * градиентный фон», значения из прототипа): синеватый — Сон, зеленоватый —
 * Тревога/Опора, тёплый — Выход/Разговоры. Поверх идёт радиальное пятно.
 */
fun familyCardColors(group: PracticeGroup): List<Color> = when (group) {
    PracticeGroup.SLEEP -> listOf(Color(0xFF1A2233), Color(0xFF161B28))
    PracticeGroup.EXIT_DAY -> listOf(Color(0xFF2B2530), Color(0xFF1D222C))
    PracticeGroup.TALKS -> listOf(Color(0xFF2B2530), Color(0xFF1D222C))
    PracticeGroup.ANXIETY -> listOf(Color(0xFF182A26), Color(0xFF141F1D))
    PracticeGroup.SUPPORT -> listOf(Color(0xFF182A26), Color(0xFF141F1D))
}

/**
 * Дышащие пятна поверх фона плитки (Дом) — выраженное пятно из нижнего-правого
 * угла + лёгкая противоположная подсветка, чтобы оттенок семейства читался.
 */
fun tileBlobs(group: PracticeGroup): List<Blob> = listOf(
    Blob(color = group.tint(), cx = 0.84f, cy = 0.88f, radius = 0.72f, alpha = 0.78f),
    Blob(color = group.tint(), cx = 0.18f, cy = 0.14f, radius = 0.50f, alpha = 0.16f),
)

/**
 * Пятна для широкой карточки каталога «Практики». Радиусы крупнее (в долях
 * высоты, а карточка широкая) и три слоя — чтобы дыхание было отчётливо
 * видно по всей ширине, как на плитках «Сейчас», а не только в углу.
 */
fun cardBlobs(group: PracticeGroup): List<Blob> = listOf(
    Blob(color = group.tint(), cx = 0.86f, cy = 0.60f, radius = 1.30f, alpha = 0.58f),
    Blob(color = group.tint(), cx = 0.10f, cy = 1.02f, radius = 0.98f, alpha = 0.30f),
    Blob(color = group.tint(), cx = 0.42f, cy = 0.02f, radius = 0.58f, alpha = 0.14f),
)

/** Многослойный дышащий фон плеера: terracotta + smoky + green. */
fun playerBlobs(group: PracticeGroup?): List<Blob> = listOf(
    Blob(color = (group?.tint() ?: Terracotta), cx = 0.30f, cy = 0.34f, radius = 0.75f, alpha = 0.42f),
    Blob(color = Smoky, cx = 0.74f, cy = 0.58f, radius = 0.70f, alpha = 0.32f),
    Blob(color = LogoGreen, cx = 0.50f, cy = 0.86f, radius = 0.62f, alpha = 0.28f),
)
