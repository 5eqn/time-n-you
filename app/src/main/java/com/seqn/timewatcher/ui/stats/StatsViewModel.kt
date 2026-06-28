package com.seqn.timewatcher.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seqn.timewatcher.data.model.DefaultConfig
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
import kotlinx.coroutines.flow.map
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

    val targets: StateFlow<Pair<Double, Long>> = repo.observeSettings().map { s ->
        val ratio = s[DefaultConfig.SETTING_TARGET_RATIO]?.toDoubleOrNull() ?: DefaultConfig.DEFAULT_TARGET_RATIO
        val ms = s[DefaultConfig.SETTING_TARGET_MS]?.toLongOrNull() ?: DefaultConfig.DEFAULT_TARGET_MS
        ratio to ms
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DefaultConfig.DEFAULT_TARGET_RATIO to DefaultConfig.DEFAULT_TARGET_MS)

    data class StatsUi(
        val result: StatsResult,
        val label: String,
        val period: StatPeriod,
        val start: Long,
        val isCurrentPeriod: Boolean,
        val targetRatio: Double,
        val targetMs: Long,
    )

    val ui: StateFlow<StatsUi> = combine(events, _period, _start, ticker, targets) { evs, p, s, now, (ratio, ms) ->
        val end = TimeUtil.periodEnd(p, s)
        StatsUi(
            result = StatsCalculator.compute(evs, s, end, now, ratio, ms),
            label = TimeUtil.periodLabel(p, s, minOf(now, end)),
            period = p,
            start = s,
            isCurrentPeriod = now < end,
            targetRatio = ratio,
            targetMs = ms,
        )
    }.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        StatsUi(StatsCalculator.compute(emptyList(), 0, 0, 0), "", StatPeriod.DAY, 0, true, 0.5, DefaultConfig.DEFAULT_TARGET_MS),
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
