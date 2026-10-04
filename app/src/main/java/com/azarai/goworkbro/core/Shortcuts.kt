package com.azarai.goworkbro.core

/**
 * Intent actions of the static launcher shortcuts (res/xml/shortcuts.xml).
 * MainActivity forwards them to AppRoot, which performs the shortcut:
 * NEW_TODO opens the todo add dialog, the check-ins act like the home
 * cards' quick 打卡 buttons (with a duplicate check for the current
 * logical day).
 */
object ShortcutActions {
    const val NEW_TODO = "com.azarai.goworkbro.action.NEW_TODO"
    const val CHECKIN_WAKE = "com.azarai.goworkbro.action.CHECKIN_WAKE"
    const val CHECKIN_SLEEP = "com.azarai.goworkbro.action.CHECKIN_SLEEP"

    val ALL = setOf(NEW_TODO, CHECKIN_WAKE, CHECKIN_SLEEP)
}
