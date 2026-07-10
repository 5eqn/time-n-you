package com.seqn.timewatcher.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seqn.timewatcher.data.model.DefaultConfig
import com.seqn.timewatcher.data.repo.AppGraph
import com.seqn.timewatcher.data.model.LambdaLevel
import com.seqn.timewatcher.stats.StatsCalculator
import com.seqn.timewatcher.stats.StatsResult
import com.seqn.timewatcher.util.StatPeriod
import com.seqn.timewatcher.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppGraph.repository(app)

    private val _period = MutableStateFlow(StatPeriod.DAY)
    val period: StateFlow<StatPeriod> = _period

    private val _start = MutableStateFlow(TimeUtil.periodStart(StatPeriod.DAY, System.currentTimeMillis()))
    val start: StateFlow<Long> = _start

    private val ticker: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val lambdaLevel: StateFlow<LambdaLevel> = repo.observeSettings().map { s ->
        LambdaLevel.fromValue(s[DefaultConfig.SETTING_LAMBDA]?.toIntOrNull())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultConfig.DEFAULT_LAMBDA)

    data class StatsUi(
        val result: StatsResult,
        val label: String,
        val period: StatPeriod,
        val start: Long,
        val isCurrentPeriod: Boolean,
        val lambdaLevel: LambdaLevel,
    )

    private data class Request(val period: StatPeriod, val start: Long, val now: Long, val level: LambdaLevel)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<StatsUi> = combine(_period, _start, ticker, lambdaLevel) { p, s, now, level ->
        Request(p, s, now, level)
    }.flatMapLatest { req ->
        val end = TimeUtil.periodEnd(req.period, req.start)
        // Bounded window: only the selected period's events + the boundary event.
        // flowOn(Default) keeps the Room query + mapping + StatsCalculator off the UI thread.
        repo.observeEventsInWindow(req.start, end).map { evs ->
            StatsUi(
                result = StatsCalculator.compute(
                    events = evs,
                    periodStart = req.start,
                    periodEnd = end,
                    now = req.now,
                    period = req.period,
                    targetRatio = req.level.targetRatio,
                ),
                label = TimeUtil.periodLabel(req.period, req.start, minOf(req.now, end)),
                period = req.period,
                start = req.start,
                isCurrentPeriod = req.now < end,
                lambdaLevel = req.level,
            )
        }.flowOn(Dispatchers.Default)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        StatsUi(StatsCalculator.compute(emptyList(), 0, 0, 0), "", StatPeriod.DAY, 0, true, DefaultConfig.DEFAULT_LAMBDA),
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
