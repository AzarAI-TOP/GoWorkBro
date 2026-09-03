package com.azarai.goworkbro.ui

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import com.azarai.goworkbro.R
import com.azarai.goworkbro.ui.countdown.CountdownRoute
import com.azarai.goworkbro.ui.me.MeRoute
import com.azarai.goworkbro.ui.theme.AppTheme
import com.azarai.goworkbro.ui.theme.GoWorkBroTheme
import com.azarai.goworkbro.ui.theme.ThemeMode
import com.azarai.goworkbro.ui.today.TodayRoute
import com.azarai.goworkbro.ui.timer.TimerScreen
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Full-screen layers shown above the tab shell (v1 pushed routes). */
sealed class Overlay {
    data class Timer(val todoId: String) : Overlay()
    data class NewsReader(val date: String?) : Overlay()
}

class OverlayViewModel : ViewModel() {
    val overlay = MutableStateFlow<Overlay?>(null)

    fun open(value: Overlay) {
        overlay.value = value
    }

    fun close() {
        overlay.value = null
    }
}

/** Context whose resources resolve in the in-app language (zh/en). */
val LocalLocaleContext = compositionLocalOf<Context> {
    error("LocalLocaleContext not provided")
}

@Composable
fun appString(@StringRes id: Int): String = LocalLocaleContext.current.getString(id)

@Composable
fun appString(@StringRes id: Int, vararg args: Any): String =
    LocalLocaleContext.current.getString(id, *args)

@Composable
fun AppRoot(env: EnvViewModel, overlays: OverlayViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val themeMode by env.themeMode.collectAsState()
    val fontChoice by env.fontChoice.collectAsState()
    val locale by env.locale.collectAsState()
    val overlay by overlays.overlay.collectAsState()

    // Rollover boundary checks: on resume + every 30s while foregrounded.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) env.refreshRollover()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            env.refreshRollover()
        }
    }

    GoWorkBroTheme(themeMode = themeMode, fontChoice = fontChoice) {
        val context = LocalContext.current
        val localeContext = remember(locale, context) {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(locale))
            context.createConfigurationContext(config)
        }
        val colors = AppTheme.colors

        BackHandler(enabled = overlay != null) { overlays.close() }

        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalLocaleContext provides localeContext) {
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                val stateHolder = rememberSaveableStateHolder()

                Scaffold(
                    containerColor = colors.scaffold,
                    bottomBar = {
                        NavigationBar(containerColor = colors.card) {
                            TabSpec.entries.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == index) tab.filled else tab.outlined,
                                            contentDescription = appString(tab.label),
                                        )
                                    },
                                    label = { Text(appString(tab.label)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colors.primary,
                                        selectedTextColor = colors.primary,
                                        unselectedIconColor = colors.navUnselected,
                                        unselectedTextColor = colors.navUnselected,
                                        indicatorColor = colors.primary.copy(alpha = 0.12f),
                                    ),
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                        stateHolder.SaveableStateProvider(selectedTab) {
                            when (selectedTab) {
                                0 -> com.azarai.goworkbro.ui.todo.TodoRoute(overlays)
                                1 -> CountdownRoute()
                                2 -> TodayRoute(overlays)
                                else -> MeRoute(env)
                            }
                        }
                    }
                }

                when (val current = overlay) {
                    is Overlay.Timer -> TimerScreen(todoId = current.todoId, onClose = overlays::close)
                    is Overlay.NewsReader -> com.azarai.goworkbro.ui.today.NewsReaderScreen(
                        initialDate = current.date,
                        onClose = overlays::close,
                    )
                    null -> Unit
                }
            }
        }
    }
}

private enum class TabSpec(
    val label: Int,
    val filled: ImageVector,
    val outlined: ImageVector,
) {
    TODO_TAB(R.string.tab_todo, Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    COUNTDOWN_TAB(R.string.tab_countdown, Icons.Filled.HourglassBottom, Icons.Outlined.HourglassBottom),
    TODAY_TAB(R.string.tab_today, Icons.Filled.Today, Icons.Outlined.Today),
    ME_TAB(R.string.tab_me, Icons.Filled.Person, Icons.Outlined.Person),
}
