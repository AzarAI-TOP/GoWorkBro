package com.azarai.goworkbro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.LogicalDay
import com.azarai.goworkbro.core.Rollover
import com.azarai.goworkbro.core.ShortcutActions
import com.azarai.goworkbro.core.Updater
import com.azarai.goworkbro.ui.habit.HabitScreen
import com.azarai.goworkbro.ui.home.HomeScreen
import com.azarai.goworkbro.ui.overview.OverviewScreen
import com.azarai.goworkbro.ui.settings.SettingsScreen
import com.azarai.goworkbro.ui.countdown.CountdownScreen
import com.azarai.goworkbro.ui.sleep.SleepScreen
import com.azarai.goworkbro.ui.theme.GoWorkBroTheme
import com.azarai.goworkbro.ui.todo.TodoScreen
import com.azarai.goworkbro.ui.water.WaterScreen
import com.azarai.goworkbro.ui.components.StartupReveal
import com.azarai.goworkbro.ui.timer.TimerScreen
import com.azarai.goworkbro.ui.timer.TimerViewModel

/** Route ids for the tiny hand-rolled navigation stack. */
object Routes {
    const val HOME = "home"
    const val TODOS = "todos"
    const val HABITS = "habits"
    const val WATER = "water"
    const val SLEEP = "sleep"
    const val COUNTDOWN = "countdown"
    const val OVERVIEW = "overview"
    const val SETTINGS = "settings"
}

/** Single-activity app root: one home page, no tabs, pushed detail pages. */
@Composable
fun AppRoot(
    pendingShortcut: String? = null,
    onShortcutHandled: () -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Coming back to the foreground re-reads the logical day; the day flow
    // itself re-emits at the 04:00/midnight boundary while the app stays open.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) LogicalDay.notifyResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        LogicalDay.flow.collect { Rollover.ensure(Graph.db, Graph.store) }
    }
    // throttled daily GitHub-release check; rescans any downloaded APK first
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Updater.maybeAutoCheck(Graph.store, context)
    }

    GoWorkBroTheme {
        var stack by rememberSaveable { mutableStateOf(listOf(Routes.HOME)) }
        var showTimer by rememberSaveable { mutableStateOf(false) }
        // cold-start reveal: the big sprout flies into the home header.
        // It only plays on the day's FIRST launch — the app is opened many
        // times a day and a 1.4s flourish every time is a tax, not a treat.
        var showReveal by rememberSaveable { mutableStateOf(false) }
        // one-shot launcher-shortcut state (consumed where it is used)
        var pendingAddTodo by rememberSaveable { mutableStateOf(false) }
        var pendingAutoRecord by rememberSaveable { mutableStateOf(false) }
        var sproutCenter by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
        val timerVm: TimerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        val open: (String) -> Unit = { stack = stack + it }
        val back: () -> Unit = { stack = stack.dropLast(1) }
        val openTimer: (com.azarai.goworkbro.core.db.Todo) -> Unit = { todo ->
            timerVm.start(todo)
            showTimer = true
        }

        val activeTimer by timerVm.active.collectAsState()
        BackHandler(enabled = showTimer && activeTimer != null) { showTimer = false }
        // the nav handler must not steal back while the focus overlay is up
        BackHandler(enabled = !showTimer && stack.size > 1) { back() }

        // A round that ends while the focus page is open drops the user on the
        // todo list; otherwise the page stays put (the home mini-card just goes).
        // Keyed on the TIMER ONLY: showTimer flips a frame before the StateFlow's
        // value reaches composition, and keying on it would close the page it
        // just opened.
        LaunchedEffect(activeTimer) {
            if (activeTimer == null && showTimer) {
                showTimer = false
                if (stack.last() != Routes.TODOS) stack = listOf(Routes.HOME, Routes.TODOS)
            }
        }

        LaunchedEffect(Unit) {
            val today = com.azarai.goworkbro.core.util.Dates.todayKey()
            if (Graph.store.get(com.azarai.goworkbro.core.Store.Keys.LAST_REVEAL_DATE) != today) {
                Graph.store.set(com.azarai.goworkbro.core.Store.Keys.LAST_REVEAL_DATE, today)
                showReveal = true
            }
        }
        // Launcher shortcuts (long-press the app icon). LOG_SLEEP opens the
        // sleep page with the two-step record sheet already up.
        LaunchedEffect(pendingShortcut) {
            when (pendingShortcut) {
                ShortcutActions.NEW_TODO -> {
                    stack = listOf(Routes.HOME, Routes.TODOS)
                    pendingAddTodo = true
                }
                ShortcutActions.LOG_SLEEP -> {
                    stack = listOf(Routes.HOME, Routes.SLEEP)
                    pendingAutoRecord = true
                }
            }
            if (pendingShortcut != null) onShortcutHandled()
        }

        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = stack.last(),
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
                transitionSpec = {
                    if (targetState == Routes.HOME) {
                        (slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 3 } + fadeOut(tween(200)))
                    }
                },
                label = "nav",
            ) { top ->
                when (top) {
                    Routes.TODOS -> TodoScreen(
                        onBack = back,
                        onOpenTimer = openTimer,
                        autoOpenAdd = pendingAddTodo,
                        onAutoAddConsumed = { pendingAddTodo = false },
                    )
                    Routes.HABITS -> HabitScreen(onBack = back)
                    Routes.WATER -> WaterScreen(onBack = back)
                    Routes.SLEEP -> SleepScreen(
                        onBack = back,
                        autoOpenRecord = pendingAutoRecord,
                        onAutoRecordConsumed = { pendingAutoRecord = false },
                    )
                    Routes.COUNTDOWN -> CountdownScreen(onBack = back)
                    Routes.OVERVIEW -> OverviewScreen(
                        onBack = back,
                        onOpenSettings = { open(Routes.SETTINGS) },
                    )
                    Routes.SETTINGS -> SettingsScreen(onBack = back)
                    else -> HomeScreen(
                        openRoute = open,
                        onOpenActiveTimer = { showTimer = true },
                        onOpenOverview = { open(Routes.OVERVIEW) },
                        onSproutMeasured = { sproutCenter = it },
                    )
                }
            }

            // full-screen focus overlay, lives above the nav stack
            if (showTimer && activeTimer != null) {
                TimerScreen(vm = timerVm, onClose = { showTimer = false })
            }

            // cold-start sprout flight, above everything
            if (showReveal) {
                StartupReveal(targetCenter = sproutCenter) { showReveal = false }
            }
        }
    }
}
