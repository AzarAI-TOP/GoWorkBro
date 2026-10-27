package com.azarai.goworkbro.ui.countdown

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.db.Countdown
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Countdown page state: the item flow plus a 1s tick so the remaining time actually moves. */
class CountdownViewModel : ViewModel() {

    val items: StateFlow<List<Countdown>> = Graph.db.countdownDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now())

    fun save(id: String?, title: String, target: LocalDateTime, colorIndex: Int) {
        viewModelScope.launch {
            Graph.db.countdownDao().upsert(
                Countdown(
                    id = id ?: UUID.randomUUID().toString(),
                    title = title,
                    targetAt = Dates.isoDateTime(target),
                    colorIndex = colorIndex,
                    createdAt = id?.let { existing -> items.value.firstOrNull { it.id == id }?.createdAt }
                        ?: Dates.nowIso(),
                ),
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { Graph.db.countdownDao().deleteById(id) }
    }
}
