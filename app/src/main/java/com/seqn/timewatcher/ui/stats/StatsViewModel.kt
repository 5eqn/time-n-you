package com.seqn.timewatcher.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seqn.timewatcher.data.repo.AppGraph
import com.seqn.timewatcher.stats.StatsCalculator
import com.seqn.timewatcher.stats.StatsResult
import com.seqn.timewatcher.util.StatPeriod
import com.seqn.timewatcher.util.TimeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppGraph.repository(app)

    val events = repo.observeEvents().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _period = MutableStateFlow(StatPeriod.DAY)
    val period: StateFlow<StatPeriod> = _period

    private val _start = MutableStateFlow(TimeUtil.periodStart(StatPeriod.DAY, System.currentTimeMillis()))
    val start: StateFlow<Long> = _start

    private val ticker: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    data class StatsUi(
        val result: StatsResult,
        val label: String,
        val period: StatPeriod,
        val start: Long,
        val isCurrentPeriod: Boolean,
    )

    val ui: StateFlow<StatsUi> = combine(events, _period, _start, ticker) { evs, p, s, now ->
        val end = TimeUtil.periodEnd(p, s)
        StatsUi(
            result = StatsCalculator.compute(evs, s, end, now),
            label = TimeUtil.periodLabel(p, s, minOf(now, end)),
            period = p,
            start = s,
            isCurrentPeriod = now < end,
        )
    }.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        StatsUi(StatsCalculator.compute(emptyList(), 0, 0, 0), "", StatPeriod.DAY, 0, true),
    )

    fun setPeriod(p: StatPeriod) {
        _period.value = p
        _start.value = TimeUtil.periodStart(p, System.currentTimeMillis())
    }

    fun prev() {
        _start.value = TimeUtil.shift(_period.value, _start.value, -1)
    }

    fun next() {
        _start.value = TimeUtil.shift(_period.value, _start.value, 1)
    }
}
