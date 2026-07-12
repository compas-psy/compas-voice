package ru.cmpas.voice.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.cmpas.voice.R

/**
 * Байлайн-лок «NAME · ЭКОСИСТЕМА КОМПАС» (ТЗ 1.1 §1). Имя берётся из
 * `R.string.app_name` — переименование приложения = правка одной строки.
 * Версальный lockup, letter-spacing .22em, text/tertiary #6B7280. Появляется на
 * сплэше, последнем экране онбординга и в «О приложении» профиля.
 */
@Composable
fun Byline(modifier: Modifier = Modifier) {
    val name = stringResource(R.string.app_name)
    val ecosystem = stringResource(R.string.ecosystem)
    Text(
        text = "$name · $ecosystem",
        style = TextStyle(
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.22.em,
            color = Color(0xFF6B7280),
        ),
        modifier = modifier,
    )
}
