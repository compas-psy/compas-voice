package ru.cmpas.voice.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.cmpas.voice.ui.components.Blob
import ru.cmpas.voice.ui.components.BreathingBackground
import ru.cmpas.voice.ui.components.GhostButton
import ru.cmpas.voice.ui.components.LogoMark
import ru.cmpas.voice.ui.components.PageDots
import ru.cmpas.voice.ui.components.PrimaryButton
import ru.cmpas.voice.ui.components.pressClickable
import ru.cmpas.voice.ui.theme.BgNight
import ru.cmpas.voice.ui.theme.LogoGreen
import ru.cmpas.voice.ui.theme.Smoky
import ru.cmpas.voice.ui.theme.Terracotta
import ru.cmpas.voice.ui.theme.TextPrimary
import ru.cmpas.voice.ui.theme.TextSecondary
import ru.cmpas.voice.ui.theme.WhiteAlpha08

private data class StateOption(val key: String, val label: String)

private val startStates = listOf(
    StateOption("sleep", "Не могу уснуть"),
    StateOption("work", "Не могу выключить работу"),
    StateOption("anxiety", "Тревожно, всё сжимается"),
    StateOption("support", "Потерял опору"),
)

@Composable
fun OnboardingScreen(onDone: (String) -> Unit) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var selected by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgNight),
    ) {
        BreathingBackground(
            blobs = listOf(
                Blob(Terracotta, 0.28f, 0.30f, 0.7f, 0.30f),
                Blob(Smoky, 0.78f, 0.62f, 0.6f, 0.22f),
                Blob(LogoGreen, 0.5f, 0.9f, 0.5f, 0.20f),
            ),
            modifier = Modifier.fillMaxSize(),
        )

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 26.dp)
                    .padding(bottom = 160.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                when (page) {
                    0 -> {
                        LogoMark(size = 46.dp)
                        Spacer(Modifier.height(28.dp))
                        Text(
                            "Голос, который помогает перейти из одного состояния в другое.",
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Не музыка и не курс. Спокойный проводник — от того, что есть сейчас, к тому, что нужно.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                        )
                    }
                    1 -> {
                        Text(
                            "Без обещаний исцеления и магических частот.",
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Работает не потому что «лечит частотами», а потому что помогает переключиться. Голос и точные сценарии — это всё.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                        )
                    }
                    else -> {
                        Text(
                            "С чего начнём?",
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Можно поменять в любой момент.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(22.dp))
                        startStates.forEachIndexed { i, opt ->
                            StateRow(
                                label = opt.label,
                                selected = i == selected,
                                onClick = { selected = i },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 26.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageDots(count = 3, active = pager.currentPage)
            Spacer(Modifier.height(20.dp))
            if (pager.currentPage < 2) {
                GhostButton("Далее") {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            } else {
                PrimaryButton("Начать") { onDone(startStates[selected].key) }
            }
        }
    }
}

@Composable
private fun StateRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(WhiteAlpha08)
            .then(
                if (selected) Modifier.border(1.5.dp, Terracotta, RoundedCornerShape(18.dp))
                else Modifier
            )
            .pressClickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(end = 34.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) Terracotta else Color.Transparent)
                .then(
                    if (!selected) Modifier.border(1.5.dp, TextSecondary, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text("✓", color = BgNight, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}
