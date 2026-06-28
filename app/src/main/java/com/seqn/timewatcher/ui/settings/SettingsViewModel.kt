package com.seqn.timewatcher.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seqn.timewatcher.data.model.DefaultConfig
import com.seqn.timewatcher.data.model.JsonCodec
import com.seqn.timewatcher.data.model.StateDef
import com.seqn.timewatcher.data.repo.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppGraph.repository(app)

    val stateDefs: StateFlow<List<StateDef>> =
        repo.observeStateDefs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _exitStateId = MutableStateFlow(DefaultConfig.DEFAULT_EXIT_STATE_ID)
    val exitStateId: StateFlow<String> = _exitStateId

    init {
        viewModelScope.launch(Dispatchers.IO) { _exitStateId.value = repo.getExitStateId() }
    }

    fun updateState(def: StateDef) = viewModelScope.launch(Dispatchers.IO) { repo.upsertStateDef(def) }
    fun deleteState(id: String) = viewModelScope.launch(Dispatchers.IO) { repo.deleteStateDef(id) }
    fun addState(name: String) = viewModelScope.launch(Dispatchers.IO) { repo.addStateDef(name) }
    fun setExit(id: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.setExitState(id)
        _exitStateId.value = id
    }
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
