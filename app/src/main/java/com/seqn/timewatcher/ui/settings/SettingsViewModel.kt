package com.seqn.timewatcher.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seqn.timewatcher.data.model.DefaultConfig
import com.seqn.timewatcher.data.model.JsonCodec
import com.seqn.timewatcher.data.model.StateDef
import com.seqn.timewatcher.data.repo.AppGraph
import com.seqn.timewatcher.pomodoro.AlarmDebugLog
import com.seqn.timewatcher.pomodoro.AlarmLogEntry
import com.seqn.timewatcher.tips.TipId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppGraph.repository(app)

    val stateDefs: StateFlow<List<StateDef>> =
        repo.observeStateDefs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _exitStateId = MutableStateFlow(DefaultConfig.DEFAULT_EXIT_STATE_ID)
    val exitStateId: StateFlow<String> = _exitStateId

    val tipEnabled: StateFlow<Map<TipId, Boolean>> = repo.observeSettings().map { s ->
        TipId.entries.associateWith { s[DefaultConfig.SETTING_TIP_PREFIX + it.name] != "0" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TipId.entries.associateWith { true })

    val targets: StateFlow<Pair<Double, Long>> = repo.observeSettings().map { s ->
        val ratio = s[DefaultConfig.SETTING_TARGET_RATIO]?.toDoubleOrNull() ?: DefaultConfig.DEFAULT_TARGET_RATIO
        val ms = s[DefaultConfig.SETTING_TARGET_MS]?.toLongOrNull() ?: DefaultConfig.DEFAULT_TARGET_MS
        ratio to ms
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultConfig.DEFAULT_TARGET_RATIO to DefaultConfig.DEFAULT_TARGET_MS)

    init {
        viewModelScope.launch(Dispatchers.IO) { _exitStateId.value = repo.getExitStateId() }
    }

    /** Live view of the persistent alarm debug log (schedule → fire → foreground → media). */
    val alarmLog: StateFlow<List<AlarmLogEntry>> = AlarmDebugLog.events

    /** Arms a 5-second test alarm through the real pipeline. Customer should lock the screen /
     *  background the app within 5s to reproduce Vivo conditions. */
    fun testAlarm() = viewModelScope.launch(Dispatchers.IO) {
        AlarmDebugLog.log("TEST", AlarmDebugLog.STATUS_INFO, "=== Test alarm button pressed (5s) ===")
        repo.testAlarm(5_000L)
    }

    fun clearAlarmLog() = AlarmDebugLog.clear()

    fun updateState(def: StateDef) = viewModelScope.launch(Dispatchers.IO) { repo.upsertStateDef(def) }
    fun deleteState(id: String) = viewModelScope.launch(Dispatchers.IO) { repo.deleteStateDef(id) }
    fun addState(name: String) = viewModelScope.launch(Dispatchers.IO) { repo.addStateDef(name) }
    fun moveState(id: String, delta: Int) = viewModelScope.launch(Dispatchers.IO) { repo.moveState(id, delta) }
    fun setExit(id: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.setExitState(id); _exitStateId.value = id
    }
    fun setTipEnabled(id: TipId, enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        repo.setTipEnabled(id.name, enabled)
    }
    fun setTargetRatio(ratio: Double) = viewModelScope.launch(Dispatchers.IO) { repo.setTargetRatio(ratio) }
    fun setTargetMs(ms: Long) = viewModelScope.launch(Dispatchers.IO) { repo.setTargetMs(ms) }

    fun resetDefaults() = viewModelScope.launch(Dispatchers.IO) {
        repo.resetToDefaultConfig()
        _exitStateId.value = DefaultConfig.DEFAULT_EXIT_STATE_ID
    }

    suspend fun exportJson(): String = repo.exportJson(System.currentTimeMillis())

    suspend fun importJson(json: String): Boolean {
        val bundle = JsonCodec.fromJson(json) ?: return false
        return repo.importBundle(bundle)
    }
}
