package com.azarai.goworkbro

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.azarai.goworkbro.core.ShortcutActions
import com.azarai.goworkbro.ui.AppRoot

class MainActivity : ComponentActivity() {

    /** Launcher-shortcut action waiting to be handled; AppRoot nulls it out. */
    private val shortcutAction = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        shortcutAction.value = intent?.takeIfShortcut()
        setContent {
            AppRoot(
                pendingShortcut = shortcutAction.value,
                onShortcutHandled = { shortcutAction.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.takeIfShortcut()?.let { shortcutAction.value = it }
    }
}

private fun Intent.takeIfShortcut(): String? =
    action?.takeIf { ShortcutActions.ALL.contains(it) }
