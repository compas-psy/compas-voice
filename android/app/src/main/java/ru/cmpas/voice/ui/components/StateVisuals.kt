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
 * Дышащие пятна для фона карточки/плитки практики — «свой градиентный фон»
 * семейства (ТЗ §6): выраженное пятно из нижнего-правого угла + лёгкая
 * противоположная подсветка, чтобы оттенок семейства читался, а не терялся.
 */
fun tileBlobs(group: PracticeGroup): List<Blob> = listOf(
    Blob(color = group.tint(), cx = 0.84f, cy = 0.88f, radius = 0.72f, alpha = 0.78f),
    Blob(color = group.tint(), cx = 0.18f, cy = 0.14f, radius = 0.50f, alpha = 0.16f),
)

/** Многослойный дышащий фон плеера: terracotta + smoky + green. */
fun playerBlobs(group: PracticeGroup?): List<Blob> = listOf(
    Blob(color = (group?.tint() ?: Terracotta), cx = 0.30f, cy = 0.34f, radius = 0.75f, alpha = 0.42f),
    Blob(color = Smoky, cx = 0.74f, cy = 0.58f, radius = 0.70f, alpha = 0.32f),
    Blob(color = LogoGreen, cx = 0.50f, cy = 0.86f, radius = 0.62f, alpha = 0.28f),
)
