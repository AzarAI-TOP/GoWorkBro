package com.azarai.goworkbro.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.Rollover
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.db.FocusSession
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodayViewModel : ViewModel() {
    private val db = Graph.db
    private val store = Graph.store

    /** Recomputed when settings change so the rollover/late-night shift flows through. */
    val logicalDate: StateFlow<String> = store.observeAll()
        .map { rows ->
            val map = rows.associate { it.key to it.value }
            Dates.logicalDateKey(
                LocalDateTime.now(),
                map[Store.Keys.LATE_NIGHT_MODE] == "true",
                map[Store.Keys.LAST_ROLLOVER_DATE] ?: "",
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Dates.todayKey())

    val allSessions: StateFlow<List<FocusSession>> = db.focusDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val todaySessions: StateFlow<List<FocusSession>> = logicalDate
        .flatMapLatest { date -> db.focusDao().observeByDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [6 days ago … today] focus seconds. */
    val weeklySeconds: StateFlow<List<Int>> = combine(logicalDate, allSessions) { date, sessions ->
        val today = Dates.parseKey(date)
        val byDate = sessions.groupBy({ it.sessionDate }, { it.durationSeconds }).mapValues { it.value.sum() }
        (6 downTo 0).map { offset ->
            byDate[Dates.dateKeyOf(today.minusDays(offset.toLong()))] ?: 0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(7) { 0 })

    val news = Graph.news.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val newsLoading = MutableStateFlow(false)
    val newsError = MutableStateFlow<String?>(null)

    init {
        refreshNews(force = false)
    }

    fun refreshNews(force: Boolean) {
        viewModelScope.launch {
            newsLoading.value = true
            newsError.value = null
            Graph.news.refresh(force)
                .onSuccess { newsLoading.value = false }
                .onFailure {
                    newsLoading.value = false
                    if (news.value.isEmpty() || force) newsError.value = it.message
                }
        }
    }
}
