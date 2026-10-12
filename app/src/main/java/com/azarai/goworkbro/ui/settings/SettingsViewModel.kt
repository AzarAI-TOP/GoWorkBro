package com.azarai.goworkbro.ui.settings

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.Backup
import com.azarai.goworkbro.core.LogicalDay
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Data types the user can wipe from the settings page. */
enum class DataKind(val label: String) {
    TODOS("待办（含专注记录）"),
    HABITS("习惯"),
    WATER("喝水记录"),
    FITNESS("健身记录"),
    SLEEP("睡眠记录"),
    COUNTDOWNS("倒计时"),
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    /** Late-night mode: check-ins between 00:00 and 04:00 count as yesterday. */
    val lateNight: StateFlow<Boolean> = Graph.store.observe(Store.Keys.LATE_NIGHT_MODE)
        .map { it != "false" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setLateNight(enabled: Boolean) {
        viewModelScope.launch {
            Graph.store.set(Store.Keys.LATE_NIGHT_MODE, enabled.toString())
            LogicalDay.notifyResumed()
        }
    }

    /** Auto-update: check GitHub for new releases on app start, download them. */
    val autoUpdate: StateFlow<Boolean> = Graph.store.observe(Store.Keys.AUTO_UPDATE)
        .map { it != "false" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch { Graph.store.set(Store.Keys.AUTO_UPDATE, enabled.toString()) }
    }

    private val checking = java.util.concurrent.atomic.AtomicBoolean(false)

    fun checkNow() {
        // one HTTP round-trip at a time; a double tap must not double-download
        if (!checking.compareAndSet(false, true)) return
        viewModelScope.launch {
            try { Updater.checkNow(Graph.appContext) } finally { checking.set(false) }
        }
    }

    fun deleteData(kinds: Set<DataKind>) {
        viewModelScope.launch {
            val dao = Graph.db.maintenanceDao()
            kinds.forEach { kind ->
                when (kind) {
                    DataKind.TODOS -> {
                        dao.clearTodos()
                        dao.clearFocusLogs()
                    }
                    DataKind.HABITS -> dao.clearHabits()
                    DataKind.WATER -> dao.clearWaterLogs()
                    DataKind.FITNESS -> dao.clearFitnessLogs()
                    DataKind.SLEEP -> dao.clearSleepRecords()
                    DataKind.COUNTDOWNS -> dao.clearCountdowns()
                }
            }
        }
    }

    // ---- backup: export / import over a SAF-picked file ----

    /** Write the full backup JSON to the user-picked location. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                val json = Backup.export(Graph.db)
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    } != null
                }
            }.getOrDefault(false)
            toast(if (ok) "备份已导出" else "导出失败，换个位置试试？")
        }
    }

    /** Replace ALL app data with the picked backup file's contents. */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                }
            }.getOrNull()
            if (text == null) {
                toast("读取文件失败")
                return@launch
            }
            val summary = Backup.import(Graph.db, text)
            if (summary == null) {
                toast("导入失败：不是有效的 GoWorkBro 备份文件")
            } else {
                LogicalDay.notifyResumed() // re-read the logical day under restored settings
                toast("导入完成，共恢复 ${summary.total} 条记录")
            }
        }
    }

    private fun toast(text: String) {
        Toast.makeText(getApplication(), text, Toast.LENGTH_LONG).show()
    }
}
