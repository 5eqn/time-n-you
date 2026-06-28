package com.seqn.timewatcher.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seqn.timewatcher.data.model.StateDef
import com.seqn.timewatcher.data.model.StateEvent
import com.seqn.timewatcher.data.repo.AppGraph
import com.seqn.timewatcher.stats.StatsCalculator
import com.seqn.timewatcher.stats.StatsResult
import com.seqn.timewatcher.util.StatPeriod
import com.seqn.timewatcher.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppGraph.repository(app)

    val stateDefs: StateFlow<List<StateDef>> =
        repo.observeStateDefs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val events: StateFlow<List<StateEvent>> =
        repo.observeEvents().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val ticker: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(20_000)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    val todayStats: StateFlow<StatsResult> = combine(events, ticker) { evs, now ->
        val start = TimeUtil.periodStart(StatPeriod.DAY, now)
        val end = TimeUtil.periodEnd(StatPeriod.DAY, start)
        StatsCalculator.compute(evs, start, end, now)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StatsCalculator.compute(emptyList(), 0, 0, 0))

    private val missedChannel = Channel<String>(Channel.BUFFERED)
    val missed = missedChannel.receiveAsFlow()

    init {
        // Reconcile a pomodoro that ended while the app was closed.
        viewModelScope.launch(Dispatchers.IO) {
            val exit = repo.reconcileAndArm(System.currentTimeMillis())
            if (exit != null) missedChannel.trySend(exit.name)
        }
    }

    fun switchState(stateId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.switchState(stateId, System.currentTimeMillis())
        }
    }
}
