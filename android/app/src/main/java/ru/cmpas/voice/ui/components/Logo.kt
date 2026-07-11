package ru.cmpas.voice.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.cmpas.voice.R
import ru.cmpas.voice.ui.theme.TextPrimary

/**
 * Знак бренда внутри экранов — **плоское белое дерево** (kompas-tree).
 * По правилу дизайна: тёплое свечение и кольца — только у иконки приложения и
 * сплэша; внутри экранов знак идёт плоским белым, чтобы не конкурировать с
 * терракотовой кнопкой действия (единственный акцент).
 */
@Composable
fun LogoMark(
    size: Dp = 38.dp,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
) {
    Icon(
        painter = painterResource(R.drawable.ic_tree),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}
