package com.seqn.timewatcher.data.repo

import androidx.room.withTransaction
import com.seqn.timewatcher.data.db.AppDatabase
import com.seqn.timewatcher.data.db.entity.SettingEntity
import com.seqn.timewatcher.data.db.entity.StateDefEntity
import com.seqn.timewatcher.data.db.entity.StateEventEntity
import com.seqn.timewatcher.data.db.entity.toEntity
import com.seqn.timewatcher.data.db.entity.toModel
import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.data.model.DefaultConfig
import com.seqn.timewatcher.data.model.ExportBundle
import com.seqn.timewatcher.data.model.ExportConfig
import com.seqn.timewatcher.data.model.ExportEvent
import com.seqn.timewatcher.data.model.ExportStateDef
import com.seqn.timewatcher.data.model.JsonCodec
import com.seqn.timewatcher.data.model.StateDef
import com.seqn.timewatcher.data.model.StateEvent
import com.seqn.timewatcher.data.model.defaultStateDefs
import com.seqn.timewatcher.data.model.fromString
import com.seqn.timewatcher.pomodoro.PomodoroAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Single source of truth for state defs, switch events, settings, targets, tips, and the pomodoro lifecycle. */
class Repository(
    db: AppDatabase,
    private val alarm: PomodoroAlarm,
) {
    private val stateDefDao = db.stateDefDao()
    private val eventDao = db.eventDao()
    private val settingDao = db.settingDao()
    private val dbRef = db

    // ---- observation ----
    fun observeStateDefs(): Flow<List<StateDef>> =
        stateDefDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeEvents(): Flow<List<StateEvent>> =
        eventDao.observeAll().map { list -> list.map { it.toModel() } }

    /** Events for a [start, end) window plus the single boundary event running across `start`,
     *  ascending by timestamp. Bounds the stats computation to the visible window instead of
     *  re-scanning the whole event log every tick. */
    fun observeEventsInWindow(start: Long, end: Long): Flow<List<StateEvent>> =
        combine(eventDao.observeBetween(start, end), eventDao.observeLatestBefore(start)) { inWindow, boundary ->
            if (boundary == null) {
                inWindow.map { it.toModel() }
            } else {
                ArrayList<StateEvent>(inWindow.size + 1).apply {
                    add(boundary.toModel())
                    inWindow.forEach { add(it.toModel()) }
                }
            }
        }

    fun observeLatestEvent(): Flow<StateEvent?> =
        eventDao.observeLatest().map { it?.toModel() }

    fun observeSettings(): Flow<Map<String, String>> =
        settingDao.observeAll().map { list -> list.associate { it.key to it.value } }

    suspend fun getStateDefs(): List<StateDef> = stateDefDao.getAll().map { it.toModel() }
    suspend fun getEvents(): List<StateEvent> = eventDao.getAll().map { it.toModel() }
    suspend fun getLatestEvent(): StateEvent? = eventDao.getLatest()?.toModel()

    // ---- exit state setting ----
    private suspend fun exitStateId(): String =
        settingDao.get(DefaultConfig.SETTING_EXIT_STATE) ?: DefaultConfig.DEFAULT_EXIT_STATE_ID

    suspend fun getExitStateId(): String = exitStateId()

    suspend fun resolveExitState(): StateDef {
        val id = exitStateId()
        stateDefDao.getById(id)?.let { return it.toModel() }
        stateDefDao.getById(DefaultConfig.DEFAULT_EXIT_STATE_ID)?.let { return it.toModel() }
        val all = stateDefDao.getAll()
        if (all.isEmpty()) return defaultStateDefs().first()
        all.firstOrNull { Category.fromString(it.category) == Category.WASTE }?.let { return it.toModel() }
        return all.first().toModel()
    }

    suspend fun setExitState(id: String) {
        settingDao.set(SettingEntity(DefaultConfig.SETTING_EXIT_STATE, id))
    }

    // ---- targets (configurable 优秀 threshold) ----
    suspend fun getTargetRatio(): Double =
        settingDao.get(DefaultConfig.SETTING_TARGET_RATIO)?.toDoubleOrNull() ?: DefaultConfig.DEFAULT_TARGET_RATIO

    suspend fun setTargetRatio(ratio: Double) {
        settingDao.set(SettingEntity(DefaultConfig.SETTING_TARGET_RATIO, ratio.toString()))
    }

    suspend fun getTargetMs(): Long =
        settingDao.get(DefaultConfig.SETTING_TARGET_MS)?.toLongOrNull() ?: DefaultConfig.DEFAULT_TARGET_MS

    suspend fun setTargetMs(ms: Long) {
        settingDao.set(SettingEntity(DefaultConfig.SETTING_TARGET_MS, ms.toString()))
    }

    // ---- tip enable/disable ----
    suspend fun getTipEnabled(tipId: String): Boolean =
        settingDao.get(DefaultConfig.SETTING_TIP_PREFIX + tipId) != "0" // default enabled

    suspend fun setTipEnabled(tipId: String, enabled: Boolean) {
        settingDao.set(SettingEntity(DefaultConfig.SETTING_TIP_PREFIX + tipId, if (enabled) "1" else "0"))
    }

    // ---- changelog ----
    suspend fun getLastChangelogVersion(): String? =
        settingDao.get(DefaultConfig.SETTING_LAST_CHANGELOG_VERSION)

    suspend fun setLastChangelogVersion(version: String) {
        settingDao.set(SettingEntity(DefaultConfig.SETTING_LAST_CHANGELOG_VERSION, version))
    }

    // ---- state switching (core) ----
    /** Records a switch to [stateId] at [now]; arms a pomodoro if that state has one configured.
     *  No-op (keeps elapsed time) if already in [stateId]. */
    suspend fun switchState(stateId: String, now: Long): StateEvent? {
        val latest = eventDao.getLatest()
        if (latest != null && latest.stateId == stateId) {
            // Disallow re-entering the current state — would otherwise reset the running timer.
            return latest.toModel()
        }
        val def = stateDefDao.getById(stateId) ?: return null
        alarm.cancel() // any previously-armed pomodoro is voided by the new switch
        val event = StateEventEntity(
            stateId = def.id,
            stateName = def.name,
            category = def.category,
            timestamp = now,
            pomodoroEndTs = null,
            pomodoroApplied = false,
        )
        val newId = eventDao.insert(event)
        if (def.pomodoroEnabled && def.pomodoroMinutes > 0) {
            val endTs = now + def.pomodoroMinutes * 60_000L
            eventDao.updatePomodoro(newId, endTs, applied = true)
            alarm.schedule(endTs)
            return event.copy(id = newId, pomodoroEndTs = endTs, pomodoroApplied = true).toModel()
        }
        return event.copy(id = newId).toModel()
    }

    /** If the active pomodoro has already ended (and nothing newer exists), switch to the exit
     *  state. Returns the exit StateDef when it fired, else null. */
    suspend fun reconcilePomodoro(now: Long): StateDef? {
        val latest = eventDao.getLatest() ?: return null
        val endTs = latest.pomodoroEndTs ?: return null
        if (endTs > now) return null // still running
        val exit = resolveExitState()
        eventDao.insert(
            StateEventEntity(
                stateId = exit.id,
                stateName = exit.name,
                category = exit.category.name,
                timestamp = endTs, // attribute the time since the scheduled end to the exit state
                pomodoroEndTs = null,
                pomodoroApplied = false,
            ),
        )
        alarm.cancel()
        return exit
    }

    /** Re-arm a still-pending pomodoro (e.g. after reboot/import), or reconcile if it ended.
     *  Returns the exit StateDef when a reconcile fired, else null. */
    suspend fun reconcileAndArm(now: Long): StateDef? {        val latest = eventDao.getLatest()
        if (latest == null) {
            alarm.cancel()
            return null
        }
        val endTs = latest.pomodoroEndTs
        if (endTs == null) {
            alarm.cancel()
            return null
        }
        return if (endTs > now) {
            alarm.schedule(endTs)
            null
        } else {
            reconcilePomodoro(now)
        }
    }

    // ---- config editing ----
    /** Debug-only: arms a one-shot test alarm [delayMs] from now via the real alarm pipeline.
     *  Exercises the exact setAlarmClock → RingerService path so a customer can verify ringing
     *  (and capture a trace) without a real pomodoro. */
    fun testAlarm(delayMs: Long) {
        alarm.cancel()
        alarm.schedule(System.currentTimeMillis() + delayMs, isTest = true)
    }

    suspend fun upsertStateDef(def: StateDef) = stateDefDao.upsert(def.toEntity())
    suspend fun deleteStateDef(id: String) = stateDefDao.delete(id)

    suspend fun addStateDef(name: String): StateDef {
        val count = stateDefDao.count()
        val id = "state_${count + 1}_${System.currentTimeMillis()}"
        val def = StateDef(
            id = id,
            name = name,
            category = Category.WASTE, // new states default to WASTE so they never inflate utilization
            pomodoroEnabled = false,
            pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES,
            sortOrder = count,
        )
        stateDefDao.upsert(def.toEntity())
        return def
    }

    /** Swap two adjacent states' sort order (reorder). */
    suspend fun moveState(id: String, delta: Int) {
        val all = stateDefDao.getAll().sortedBy { it.sortOrder }
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = index + delta
        if (target < 0 || target >= all.size) return
        val a = all[index]
        val b = all[target]
        stateDefDao.upsert(a.copy(sortOrder = b.sortOrder))
        stateDefDao.upsert(b.copy(sortOrder = a.sortOrder))
    }

    suspend fun resetToDefaultConfig() {
        dbRef.withTransaction {
            stateDefDao.deleteAll()
            stateDefDao.upsertAll(defaultStateDefs().map { it.toEntity() })
            settingDao.deleteAll()
            settingDao.set(SettingEntity(DefaultConfig.SETTING_EXIT_STATE, DefaultConfig.DEFAULT_EXIT_STATE_ID))
            // targets + tips fall back to defaults by absence of keys
        }
        reconcileAndArm(System.currentTimeMillis())
    }

    suspend fun seedDefaultsIfEmpty() {
        if (stateDefDao.count() == 0) {
            dbRef.withTransaction {
                stateDefDao.upsertAll(defaultStateDefs().map { it.toEntity() })
                settingDao.set(SettingEntity(DefaultConfig.SETTING_EXIT_STATE, DefaultConfig.DEFAULT_EXIT_STATE_ID))
            }
        }
    }

    /** Item 3: ensure 「生活」 carries its default note if missing (idempotent; runs on first open of v1.2). */
    suspend fun ensureLifeNote() {
        val life = stateDefDao.getById(DefaultConfig.ID_LIFE) ?: return
        if (life.note.isNullOrEmpty()) {
            stateDefDao.upsert(life.copy(note = DefaultConfig.DEFAULT_LIFE_NOTE))
        }
    }

    /** Item 14: if the configured pomodoro exit state is an EFFECTIVE state and 摆烂 exists,
     *  switch the exit to 摆烂 (effective exit states violate the design philosophy). */
    suspend fun migrateExitToSlackIfEffective() {
        val exitId = settingDao.get(DefaultConfig.SETTING_EXIT_STATE) ?: return
        val exitDef = stateDefDao.getById(exitId) ?: return
        if (Category.fromString(exitDef.category) != Category.EFFECTIVE) return
        if (stateDefDao.getById(DefaultConfig.ID_SLACK) != null) {
            settingDao.set(SettingEntity(DefaultConfig.SETTING_EXIT_STATE, DefaultConfig.ID_SLACK))
        }
    }

    // ---- export / import ----
    suspend fun exportJson(now: Long): String = JsonCodec.toJson(buildBundle(now))

    private suspend fun buildBundle(now: Long): ExportBundle {
        val defs = stateDefDao.getAll().map { it.toModel() }
        val events = eventDao.getAll().map { it.toModel() }
        return ExportBundle(
            schemaVersion = DefaultConfig.SCHEMA_VERSION,
            exportedAt = now,
            config = ExportConfig(
                states = defs.map {
                    ExportStateDef(
                        id = it.id,
                        name = it.name,
                        category = it.category.name,
                        pomodoroEnabled = it.pomodoroEnabled,
                        pomodoroMinutes = it.pomodoroMinutes,
                        sortOrder = it.sortOrder,
                        note = it.note,
                    )
                },
                pomodoroExitStateId = exitStateId(),
            ),
            events = events.map {
                ExportEvent(
                    id = it.id,
                    stateId = it.stateId,
                    stateName = it.stateName,
                    category = it.category.name,
                    timestamp = it.timestamp,
                    pomodoroEndTs = it.pomodoroEndTs,
                    pomodoroApplied = it.pomodoroApplied,
                )
            },
        )
    }

    /** Replaces config + events atomically. Returns true on success. */
    suspend fun importBundle(bundle: ExportBundle): Boolean {
        if (bundle.schemaVersion != DefaultConfig.SCHEMA_VERSION) return false
        dbRef.withTransaction {
            stateDefDao.deleteAll()
            eventDao.deleteAll()
            settingDao.deleteAll()
            stateDefDao.upsertAll(
                bundle.config.states.map {
                    StateDefEntity(
                        id = it.id,
                        name = it.name,
                        category = it.category,
                        pomodoroEnabled = it.pomodoroEnabled,
                        pomodoroMinutes = it.pomodoroMinutes,
                        sortOrder = it.sortOrder,
                        note = it.note,
                    )
                },
            )
            eventDao.insertAll(
                bundle.events.map {
                    StateEventEntity(
                        stateId = it.stateId,
                        stateName = it.stateName,
                        category = it.category,
                        timestamp = it.timestamp,
                        pomodoroEndTs = it.pomodoroEndTs,
                        pomodoroApplied = it.pomodoroApplied,
                    )
                },
            )
            settingDao.set(SettingEntity(DefaultConfig.SETTING_EXIT_STATE, bundle.config.pomodoroExitStateId))
        }
        reconcileAndArm(System.currentTimeMillis())
        return true
    }
}
