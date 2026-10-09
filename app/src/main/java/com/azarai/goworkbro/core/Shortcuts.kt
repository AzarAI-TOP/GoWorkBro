package com.azarai.goworkbro.core

/**
 * Intent actions of the static launcher shortcuts (res/xml/shortcuts.xml).
 * MainActivity forwards them to AppRoot, which performs the shortcut:
 * NEW_TODO opens the todo add dialog, LOG_SLEEP opens the sleep page with
 * the two-step record sheet already up.
 */
object ShortcutActions {
    const val NEW_TODO = "com.azarai.goworkbro.action.NEW_TODO"
    const val LOG_SLEEP = "com.azarai.goworkbro.action.LOG_SLEEP"

    val ALL = setOf(NEW_TODO, LOG_SLEEP)
}
