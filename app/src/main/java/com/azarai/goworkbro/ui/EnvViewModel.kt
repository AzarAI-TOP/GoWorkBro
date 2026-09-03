package com.azarai.goworkbro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.Rollover
import com.azarai.goworkbro.core.Store
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-wide environment: locale / theme / font / late-night mode / profile,
 * derived from the settings table (with a boot mirror for first-frame
 * correctness). Also owns the daily rollover and one-time legacy cleanup.
 */
class EnvViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Graph.store

    val settings: StateFlow<Map<String, String>> =
        store.observeAll()
            .map { rows -> rows.associate { it.key to it.value } }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                mapOf(
                    Store.Keys.LOCALE to store.bootLocale,
                    Store.Keys.THEME_MODE to store.bootThemeMode,
                    Store.Keys.FONT_FAMILY to store.bootFontFamily,
                ),
            )

    val locale: StateFlow<String> = settings.map { it[Store.Keys.LOCALE] ?: "zh" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.bootLocale)
    val themeMode: StateFlow<String> = settings.map { it[Store.Keys.THEME_MODE] ?: "system" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.bootThemeMode)
    val fontChoice: StateFlow<String> = settings.map { it[Store.Keys.FONT_FAMILY] ?: "proto" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.bootFontFamily)
    val lateNightMode: StateFlow<Boolean> =
        settings.map { it[Store.Keys.LATE_NIGHT_MODE] == "true" }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val userName: StateFlow<String> =
        settings.map { it[Store.Keys.USER_NAME] ?: DEFAULT_USER_NAME }
            .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_USER_NAME)
    val avatarExists: StateFlow<Boolean> =
        settings.map { it[Store.Keys.AVATAR_PATH] == "local" && Graph.avatars.exists() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, Graph.avatars.exists())

    init {
        viewModelScope.launch {
            runCatching { Graph.store.firstUsedDate(Rollover.logicalToday(Graph.store)) }
            runCatching { Rollover.ensure(Graph.db, Graph.store) }
            runCatching { cleanupLegacyDataOnce() }
        }
    }

    /** Re-checks the rollover boundary (resume hook + periodic tick). */
    fun refreshRollover() {
        viewModelScope.launch {
            runCatching { Rollover.ensure(Graph.db, Graph.store) }
        }
    }

    fun set(key: String, value: String) {
        viewModelScope.launch { store.set(key, value) }
    }

    fun setUserName(name: String) {
        if (name.isNotBlank()) set(Store.Keys.USER_NAME, name.trim())
    }

    fun setAvatar(uri: android.net.Uri?) {
        viewModelScope.launch {
            if (uri == null) {
                Graph.avatars.delete()
                store.delete(Store.Keys.AVATAR_PATH)
            } else if (Graph.avatars.save(uri)) {
                store.set(Store.Keys.AVATAR_PATH, "local")
            }
        }
    }

    /**
     * v1 (Flutter) stored its SQLite DB under <data>/app_flutter/. v2 starts
     * clean per the user's wipe request — remove the legacy files once.
     */
    private suspend fun cleanupLegacyDataOnce() {
        if (store.get(Store.Keys.LEGACY_CLEANUP_DONE) != null) return
        val dataDir = getApplication<Application>().dataDir
        listOf(
            File(dataDir, "app_flutter/goworkbro.db"),
            File(dataDir, "app_flutter/goworkbro.db-wal"),
            File(dataDir, "app_flutter/goworkbro.db-shm"),
            File(dataDir, "shared_prefs/FlutterSharedPreferences.xml"),
        ).forEach { runCatching { it.delete() } }
        File(dataDir, "GoWorkBro").deleteRecursively()
        File(dataDir, "files/GoWorkBro").deleteRecursively()
        store.set(Store.Keys.LEGACY_CLEANUP_DONE, "1")
    }

    companion object {
        const val DEFAULT_USER_NAME = "离线用户"
    }
}
