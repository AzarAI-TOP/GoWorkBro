package com.azarai.goworkbro.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.LogicalDay
import com.azarai.goworkbro.core.Store
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Data types the user can wipe from the settings page. */
enum class DataKind(val label: String) {
    TODOS("待办（含专注记录）"),
    HABITS("习惯"),
    WATER("喝水记录"),
    FITNESS("健身记录"),
    ROUTINE("起床与睡觉记录"),
}

class SettingsViewModel : ViewModel() {

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
                    DataKind.ROUTINE -> dao.clearRoutineEvents()
                }
            }
        }
    }
}
