package ru.cmpas.voice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.cmpas.voice.ui.theme.Terracotta
import ru.cmpas.voice.ui.theme.TerracottaMuted
import ru.cmpas.voice.ui.theme.TextTertiary

private data class Tab(val label: String, val on: ImageVector, val off: ImageVector)

private val tabs = listOf(
    Tab("Сейчас", Icons.Filled.Explore, Icons.Outlined.Explore),
    Tab("Практики", Icons.Filled.GridView, Icons.Outlined.GridView),
    Tab("Я", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun TabBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    night: Boolean = false,
    background: androidx.compose.ui.graphics.Color,
) {
    val accent = if (night) TerracottaMuted else Terracotta
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background.copy(alpha = 0.92f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEachIndexed { i, tab ->
            val isSel = i == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .pressClickable(onClick = { onSelect(i) })
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = if (isSel) tab.on else tab.off,
                    contentDescription = tab.label,
                    tint = if (isSel) accent else TextTertiary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    tab.label,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = if (isSel) accent else TextTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
