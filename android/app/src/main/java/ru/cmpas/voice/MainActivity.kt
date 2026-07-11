package ru.cmpas.voice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ru.cmpas.voice.ui.navigation.KompasRoot
import ru.cmpas.voice.ui.theme.KompasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KompasTheme {
                KompasRoot(container = appContainer)
            }
        }
    }
}
