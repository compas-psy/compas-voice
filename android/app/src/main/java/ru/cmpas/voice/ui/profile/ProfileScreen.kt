package ru.cmpas.voice.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.cmpas.voice.AppContainer
import ru.cmpas.voice.BuildConfig
import ru.cmpas.voice.ui.components.Byline
import ru.cmpas.voice.ui.components.LogoMark
import ru.cmpas.voice.ui.components.pressClickable
import ru.cmpas.voice.data.Background
import ru.cmpas.voice.data.FeatureFlags
import ru.cmpas.voice.data.HistoryEntry
import ru.cmpas.voice.data.Settings
import ru.cmpas.voice.ui.theme.BgGraphite
import ru.cmpas.voice.ui.theme.SurfaceAlt
import ru.cmpas.voice.ui.theme.Terracotta
import ru.cmpas.voice.ui.theme.TextPrimary
import ru.cmpas.voice.ui.theme.TextSecondary
import ru.cmpas.voice.ui.theme.TextTertiary

@Composable
fun ProfileScreen(container: AppContainer, nowMs: Long, onOpenPaywall: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by container.store.settings.collectAsState(initial = Settings())
    val history by container.store.history.collectAsState(initial = emptyList())
    var showClearDialog by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* отказ не ломает: напоминание просто не сработает (DPO §3) */ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgGraphite),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 26.dp)
                .padding(top = 12.dp, bottom = 120.dp),
        ) {
            Text("Я", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            Spacer(Modifier.height(20.dp))

            TendencyCard(history, nowMs)

            Spacer(Modifier.height(22.dp))
            Text("Недавнее", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            if (history.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LogoMark(size = 72.dp, tint = TextTertiary.copy(alpha = 0.16f))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Здесь появятся практики, которые ты слушал.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                }
            } else {
                history.take(8).forEach { entry ->
                    RecentRow(entry, nowMs)
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("Настройки", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                SettingRow(
                    title = "Фон по умолчанию",
                    value = settings.defaultBackground.title,
                    onClick = {
                        scope.launch { container.store.setDefaultBackground(nextBackground(settings.defaultBackground)) }
                    },
                )
                Divider()
                SettingToggleRow(
                    title = "Напоминания",
                    subtitle = "Могу напомнить вечером. Если будет удобно.",
                    checked = settings.remindersEnabled,
                    onCheckedChange = { checked ->
                        scope.launch { container.store.setRemindersEnabled(checked) }
                        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
                Divider()
                SettingRow(
                    title = "Подписка",
                    value = when (settings.subscriptionStatus) {
                        ru.cmpas.voice.data.SubscriptionStatus.FREE -> "Бесплатный доступ"
                        ru.cmpas.voice.data.SubscriptionStatus.TRIAL -> "Пробный период"
                        ru.cmpas.voice.data.SubscriptionStatus.ACTIVE -> "Активна"
                    },
                    onClick = onOpenPaywall,
                )
            }

            Spacer(Modifier.height(18.dp))
            SettingsCard {
                SettingRow(title = "Очистить мои данные", value = "", onClick = { showClearDialog = true })
            }

            // «О приложении» (ТЗ 1.1 §6): версия, политика, поддержка + байлайн.
            Spacer(Modifier.height(22.dp))
            Text("О приложении", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                SettingRow(title = "Политика конфиденциальности", value = "", onClick = { openUrl(context, PRIVACY_URL) })
                Divider()
                SettingRow(title = "Поддержка", value = "", onClick = { openUrl(context, "mailto:$SUPPORT_EMAIL") })
            }
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Byline()
                Spacer(Modifier.height(6.dp))
                Text(
                    "Версия ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить мои данные?") },
            text = { Text("История и отметки состояния будут удалены с устройства. Отменить нельзя.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.store.clearAll() }
                    showClearDialog = false
                }) { Text("Очистить", color = Terracotta) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена", color = TextSecondary) }
            },
            containerColor = SurfaceAlt,
        )
    }
}

@Composable
private fun TendencyCard(history: List<HistoryEntry>, nowMs: Long) {
    val twoWeeks = 14L * 24 * 3600 * 1000
    val recent = history.filter { it.checkInBefore != null && nowMs - it.startedAtEpochMs <= twoWeeks }
        .sortedBy { it.startedAtEpochMs }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceAlt)
            .padding(18.dp),
    ) {
        if (recent.size < 3) {
            Text(
                "Здесь появится спокойная сводка, когда наберётся немного практик.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        } else {
            val values = recent.mapNotNull { it.checkInBefore }
            val half = values.size / 2
            val olderAvg = values.take(half).ifEmpty { listOf(0) }.average()
            val newerAvg = values.drop(half).average()
            val dir = when {
                newerAvg <= olderAvg - 0.5 -> "чуть ниже"
                newerAvg >= olderAvg + 0.5 -> "чуть выше"
                else -> "примерно так же"
            }
            Column {
                Text(
                    "За последние две недели напряжение перед практикой в среднем $dir.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(14.dp))
                Sparkline(values)
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Int>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        if (values.size < 2) return@Canvas
        val maxV = 10f
        val stepX = size.width / (values.size - 1)
        var prev: Offset? = null
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height * (1f - (v / maxV))
            val point = Offset(x, y)
            prev?.let { drawLine(color = Terracotta, start = it, end = point, strokeWidth = 3f) }
            prev = point
        }
    }
}

@Composable
private fun RecentRow(entry: HistoryEntry, nowMs: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                "${relativeDate(entry.startedAtEpochMs, nowMs)} · ${entry.durationMin} мин",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
        if (entry.checkInBefore != null && entry.checkInAfter != null) {
            Text(
                "${entry.checkInBefore} → ${entry.checkInAfter}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceAlt),
    ) { content() }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .pressClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(0.dp))
            Text("  ›", style = MaterialTheme.typography.bodyLarge, color = TextTertiary)
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = Terracotta,
            ),
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 18.dp)
            .background(TextTertiary.copy(alpha = 0.12f))
    )
}

/** Циклирование значения «Фон по умолчанию». «Бинауральный» — только за флагом. */
private fun nextBackground(current: Background): Background {
    val options = if (FeatureFlags.spatialAudio) {
        listOf(Background.VOICE, Background.SOFT, Background.BINAURAL)
    } else {
        listOf(Background.VOICE, Background.SOFT)
    }
    val i = options.indexOf(current).let { if (it < 0) 0 else it }
    return options[(i + 1) % options.size]
}

private const val PRIVACY_URL = "https://cmpas.ru/tish/privacy" // хостит владелец
private const val SUPPORT_EMAIL = "support@cmpas.ru"

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun relativeDate(epochMs: Long, nowMs: Long): String {
    val dayMs = 24L * 3600 * 1000
    val days = ((nowMs - epochMs) / dayMs).toInt()
    return when {
        days <= 0 -> "сегодня"
        days == 1 -> "вчера"
        days in 2..4 -> "$days дня назад"
        else -> "$days дней назад"
    }
}
