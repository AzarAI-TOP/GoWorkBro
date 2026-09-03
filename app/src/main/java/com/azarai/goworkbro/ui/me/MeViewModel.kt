package com.azarai.goworkbro.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.Rollover
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.db.Countdown
import com.azarai.goworkbro.core.db.FocusSession
import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.core.db.SleepRecord
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.util.Dates
import androidx.room.withTransaction
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Me tab state: sleep records, lifetime stats, check-in writes. */
class MeViewModel : ViewModel() {

    private val db = Graph.db
    private val store = Graph.store

    val sleepRecords: StateFlow<List<SleepRecord>> = db.sleepDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todos: StateFlow<List<Todo>> = db.todoDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val habits: StateFlow<List<Habit>> = db.habitDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val countdowns: StateFlow<List<Countdown>> = db.countdownDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allSessions: StateFlow<List<FocusSession>> = db.focusDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todaySessions: StateFlow<List<FocusSession>> = db.focusDao()
        .observeByDate(Dates.todayKey())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lifetimeTodosCompleted: StateFlow<Int> = intSetting(Store.Keys.LIFETIME_TODOS_COMPLETED)
    val lifetimeHabitsCompleted: StateFlow<Int> = intSetting(Store.Keys.LIFETIME_HABITS_COMPLETED)
    val firstUsedDate: StateFlow<String> =
        store.observe(Store.Keys.FIRST_USED_DATE)
            .map { it ?: Dates.todayKey() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, Dates.todayKey())

    /** Sleep (23:xx) or wake check-in — v1 row bucketing. */
    fun recordTimeCheckIn(isSleep: Boolean, hour: Int, minute: Int, anchorDate: String? = null) {
        viewModelScope.launch {
            val selectedAt = Dates.resolveCheckInDateTime(LocalDateTime.now(), hour, minute)
            val recordDate = anchorDate ?: if (isSleep) {
                Dates.sleepRecordDateKey(selectedAt)
            } else {
                Dates.wakeRecordDateKey(selectedAt)
            }
            val timeStr = "%02d:%02d".format(hour, minute)
            val existing = db.sleepDao().getByDate(recordDate)
            val updated = if (existing != null) {
                if (isSleep) existing.copy(sleepTime = timeStr) else existing.copy(wakeTime = timeStr)
            } else {
                SleepRecord(
                    id = UUID.randomUUID().toString(),
                    recordDate = recordDate,
                    wakeTime = if (isSleep) null else timeStr,
                    sleepTime = if (isSleep) timeStr else null,
                )
            }
            db.sleepDao().upsert(updated)
        }
    }

    fun recordWorkout(durationMinutes: Int, description: String) {
        viewModelScope.launch {
            val recordDate = Rollover.logicalToday(store)
            val existing = db.sleepDao().getByDate(recordDate)
            val updated = if (existing != null) {
                existing.copy(
                    workoutDurationMinutes = durationMinutes,
                    note = description.ifBlank { null },
                )
            } else {
                SleepRecord(
                    id = UUID.randomUUID().toString(),
                    recordDate = recordDate,
                    workoutDurationMinutes = durationMinutes,
                    note = description.ifBlank { null },
                )
            }
            db.sleepDao().upsert(updated)
        }
    }

    fun deleteAllData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            db.withTransaction {
                db.todoDao().deleteAll()
                db.habitDao().deleteAll()
                db.focusDao().deleteAll()
                db.countdownDao().deleteAll()
                db.sleepDao().deleteAll()
                db.newsCacheDao().deleteAll()
                db.settingsDao().deleteAll()
            }
            Graph.avatars.delete()
            store.set(Store.Keys.LAST_ROLLOVER_DATE, Rollover.logicalToday(Graph.store))
            store.set(Store.Keys.FIRST_USED_DATE, Dates.todayKey())
            onDone()
        }
    }

    private fun intSetting(key: String): StateFlow<Int> = store.observe(key)
        .map { it?.toIntOrNull() ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
}
