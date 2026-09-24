package com.azarai.goworkbro.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.Rollover
import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.core.util.Dates
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitViewModel : ViewModel() {
    private val db = Graph.db

    val habits = db.habitDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** +1, or -1 to undo when already complete. */
    fun checkIn(habit: Habit) {
        viewModelScope.launch {
            val next = if (habit.isCompleted) habit.currentCount - 1 else habit.currentCount + 1
            db.habitDao().upsert(
                habit.copy(
                    currentCount = next.coerceAtLeast(0),
                    lastResetDate = Rollover.logicalToday(Graph.store),
                ),
            )
        }
    }

    fun add(title: String, target: Int, unit: String, icon: String, colorIndex: Int) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            db.habitDao().upsert(
                Habit(
                    id = UUID.randomUUID().toString(),
                    title = clean,
                    icon = icon,
                    colorIndex = colorIndex,
                    targetCount = target.coerceIn(1, 99),
                    unit = unit.ifBlank { "次" },
                    sortOrder = (System.currentTimeMillis() / 1000).toInt(),
                    createdDate = Dates.todayKey(),
                ),
            )
        }
    }

    fun update(habit: Habit, title: String, target: Int, unit: String, icon: String, colorIndex: Int) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            db.habitDao().upsert(
                habit.copy(
                    title = clean,
                    targetCount = target.coerceIn(1, 99),
                    unit = unit.ifBlank { "次" },
                    icon = icon,
                    colorIndex = colorIndex,
                ),
            )
        }
    }

    fun delete(habit: Habit) {
        viewModelScope.launch { db.habitDao().deleteById(habit.id) }
    }
}
