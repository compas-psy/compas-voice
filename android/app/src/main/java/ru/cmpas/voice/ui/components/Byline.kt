package ru.cmpas.voice.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Байлайн «ЭКОСИСТЕМА КОМПАС» (ТЗ §0.1) — принадлежность к КОМПАС не в названии,
 * а через знак-дерево и эту подпись. Версальный lockup, letter-spacing .22em,
 * text/tertiary #6B7280. Появляется на сплэше, последнем экране онбординга и в
 * «О приложении» профиля — не на основных экранах.
 */
@Composable
fun Byline(modifier: Modifier = Modifier) {
    Text(
        text = "ЭКОСИСТЕМА КОМПАС",
        style = TextStyle(
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.22.em,
            color = Color(0xFF6B7280),
        ),
        modifier = modifier,
    )
}
