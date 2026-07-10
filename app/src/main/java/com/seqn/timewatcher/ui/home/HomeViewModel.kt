package com.seqn.timewatcher.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seqn.timewatcher.BuildConfig
import com.seqn.timewatcher.data.model.DefaultConfig
import com.seqn.timewatcher.data.model.StateDef
import com.seqn.timewatcher.data.model.StateEvent
import com.seqn.timewatcher.data.repo.AppGraph
import com.seqn.timewatcher.data.repo.SwitchStateResult
import com.seqn.timewatcher.stats.StatsCalculator
import com.seqn.timewatcher.stats.StatsResult
import com.seqn.timewatcher.tips.TipId
import com.seqn.timewatcher.util.StatPeriod
import com.seqn.timewatcher.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppGraph.repository(app)

    val stateDefs: StateFlow<List<StateDef>> =
        repo.observeStateDefs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Latest event only — drives the current-state card without loading the full event log. */
    val latestEvent: StateFlow<StateEvent?> =
        repo.observeLatestEvent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val ticker: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(20_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val targets: StateFlow<Pair<Double, Long>> = repo.observeSettings().map { s ->
        val ratio = s[DefaultConfig.SETTING_TARGET_RATIO]?.toDoubleOrNull() ?: DefaultConfig.DEFAULT_TARGET_RATIO
        val ms = s[DefaultConfig.SETTING_TARGET_MS]?.toLongOrNull() ?: DefaultConfig.DEFAULT_TARGET_MS
        ratio to ms
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultConfig.DEFAULT_TARGET_RATIO to DefaultConfig.DEFAULT_TARGET_MS)

    val tipEnabled: StateFlow<Map<TipId, Boolean>> = repo.observeSettings().map { s ->
        TipId.entries.associateWith { s[DefaultConfig.SETTING_TIP_PREFIX + it.name] != "0" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TipId.entries.associateWith { true })

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayStats: StateFlow<StatsResult> = combine(ticker, targets) { now, t -> now to t }
        .flatMapLatest { (now, t) ->
            val (ratio, ms) = t
            val start = TimeUtil.periodStart(StatPeriod.DAY, now)
            val end = TimeUtil.periodEnd(StatPeriod.DAY, start)
            // Bounded window: only today's events + the boundary event, not the whole log.
            // flowOn(Default) keeps the Room query + mapping + StatsCalculator off the UI thread.
            repo.observeEventsInWindow(start, end).map { evs ->
                StatsCalculator.compute(
                    events = evs,
                    periodStart = start,
                    periodEnd = end,
                    now = now,
                    period = StatPeriod.DAY,
                    targetRatio = ratio,
                    targetMs = ms,
                )
            }.flowOn(Dispatchers.Default)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsCalculator.compute(emptyList(), 0, 0, 0))

    private val missedChannel = Channel<String>(Channel.BUFFERED)
    val missed = missedChannel.receiveAsFlow()

    private val changelogChannel = Channel<String>(Channel.BUFFERED)
    val changelog = changelogChannel.receiveAsFlow()

    private val alarmUnavailableChannel = Channel<Unit>(Channel.BUFFERED)
    val alarmUnavailable = alarmUnavailableChannel.receiveAsFlow()

    init {
        // Reconcile a pomodoro that ended while the app was closed, and show changelog on version bump.
        viewModelScope.launch(Dispatchers.IO) {
            val exit = repo.reconcileAndArm(System.currentTimeMillis())
            if (exit != null) missedChannel.trySend(exit.name)
            val version = BuildConfig.VERSION_NAME
            if (repo.getLastChangelogVersion() != version) {
                repo.setLastChangelogVersion(version)
                changelogChannel.trySend(version)
            }
        }
    }

    fun switchState(stateId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (repo.switchState(stateId, System.currentTimeMillis()) == SwitchStateResult.ExactAlarmUnavailable) {
                alarmUnavailableChannel.trySend(Unit)
            }
        }
    }
}
