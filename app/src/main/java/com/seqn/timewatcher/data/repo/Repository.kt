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
import com.seqn.timewatcher.data.model.LambdaLevel
import com.seqn.timewatcher.data.model.StateDef
import com.seqn.timewatcher.data.model.StateEvent
import com.seqn.timewatcher.data.model.defaultStateDefs
import com.seqn.timewatcher.data.model.fromString
import com.seqn.timewatcher.pomodoro.PomodoroAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SwitchStateResult {
    data class Success(val event: StateEvent) : SwitchStateResult
    data object StateNotFound : SwitchStateResult
    data object ExactAlarmUnavailable : SwitchStateResult
}

/** Single source of truth for state defs, switch events, settings, targets, tips, and the pomodoro lifecycle. */
class Repository(
    db: AppDatabase,
    private val alarm: PomodoroAlarm,
) {
    private val stateDefDao = db.stateDefDao()
    private val eventDao = db.eventDao()
    private val settingDao = db.settingDao()
    private val dbRef = db
    private val initializationMutex = Mutex()
    @Volatile private var initialized = false

    /** Creates a new database at the current shape or upgrades existing app data in order. */
    suspend fun initialize() {
        if (initialized) return
        initializationMutex.withLock {
            if (initialized) return@withLock
            dbRef.withTransaction {
                val storedVersion = settingDao.get(DefaultConfig.SETTING_DATA_VERSION)?.toIntOrNull()
                if (storedVersion == null && stateDefDao.count() == 0) {
                    stateDefDao.upsertAll(defaultStateDefs().map { it.toEntity() })
                    settingDao.set(SettingEntity(DefaultConfig.SETTING_EXIT_STATE, DefaultConfig.DEFAULT_EXIT_STATE_ID))
                    settingDao.set(SettingEntity(DefaultConfig.SETTING_LAMBDA, DefaultConfig.DEFAULT_LAMBDA.value.toString()))
                } else {
                    val version = storedVersion ?: 0
                    if (version < 1) {
                        ensureLifeNoteMigration()
                        migrateExitToSlackIfEffectiveMigration()
                    }
                    if (version < 2) migrateDogStateMigration()
                    if (version < 3) migrateDogToRestMigration()
                    if (version < 4) migrateLambdaSetting()
                }
                settingDao.set(
                    SettingEntity(DefaultConfig.SETTING_DATA_VERSION, DefaultConfig.CURRENT_DATA_VERSION.toString()),
                )
            }
            initialized = true
        }
    }

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

    suspend fun getStateDefs(): List<StateDef> {
        initialize()
        return stateDefDao.getAll().map { it.toModel() }
    }

    suspend fun getEvents(): List<StateEvent> {
        initialize()
        return eventDao.getAll().map { it.toModel() }
    }

    suspend fun getLatestEvent(): StateEvent? {
        initialize()
        return eventDao.getLatest()?.toModel()
    }

    // ---- exit state setting ----
    private suspend fun exitStateId(): String =
        settingDao.get(DefaultConfig.SETTING_EXIT_STATE) ?: DefaultConfig.DEFAULT_EXIT_STATE_ID

    suspend fun getExitStateId(): String {
        initialize()
        return exitStateId()
    }

    suspend fun resolveExitState(): StateDef {
        initialize()
        val id = exitStateId()
        stateDefDao.getById(id)?.let { return it.toModel() }
        stateDefDao.getById(DefaultConfig.DEFAULT_EXIT_STATE_ID)?.let { return it.toModel() }
        val all = stateDefDao.getAll()
        if (all.isEmpty()) return defaultStateDefs().first()
        all.firstOrNull { Category.fromString(it.category) == Category.WASTE }?.let { return it.toModel() }
        return all.first().toModel()
    }

    suspend fun setExitState(id: String) {
        initialize()
        settingDao.set(SettingEntity(DefaultConfig.SETTING_EXIT_STATE, id))
    }

    // ---- focus standard ----
    suspend fun getLambda(): LambdaLevel {
        initialize()
        return LambdaLevel.fromValue(settingDao.get(DefaultConfig.SETTING_LAMBDA)?.toIntOrNull())
    }

    suspend fun setLambda(level: LambdaLevel) {
        initialize()
        settingDao.set(SettingEntity(DefaultConfig.SETTING_LAMBDA, level.value.toString()))
    }

    // ---- tip enable/disable ----
    suspend fun getTipEnabled(tipId: String): Boolean {
        initialize()
        return settingDao.get(DefaultConfig.SETTING_TIP_PREFIX + tipId) != "0" // default enabled
    }

    suspend fun setTipEnabled(tipId: String, enabled: Boolean) {
        initialize()
        settingDao.set(SettingEntity(DefaultConfig.SETTING_TIP_PREFIX + tipId, if (enabled) "1" else "0"))
    }

    // ---- changelog ----
    suspend fun getLastChangelogVersion(): String? {
        initialize()
        return settingDao.get(DefaultConfig.SETTING_LAST_CHANGELOG_VERSION)
    }

    suspend fun setLastChangelogVersion(version: String) {
        initialize()
        settingDao.set(SettingEntity(DefaultConfig.SETTING_LAST_CHANGELOG_VERSION, version))
    }

    // ---- state switching (core) ----
    /** Records a switch to [stateId] at [now]; arms a pomodoro if that state has one configured.
     *  No-op (keeps elapsed time) if already in [stateId]. */
    suspend fun switchState(stateId: String, now: Long): SwitchStateResult {
        initialize()
        val latest = eventDao.getLatest()
        if (latest != null && latest.stateId == stateId) {
            // Disallow re-entering the current state — would otherwise reset the running timer.
            return SwitchStateResult.Success(latest.toModel())
        }
        val def = stateDefDao.getById(stateId) ?: return SwitchStateResult.StateNotFound
        val endTs = if (def.pomodoroEnabled && def.pomodoroMinutes > 0) {
            if (!alarm.canScheduleExactAlarms()) return SwitchStateResult.ExactAlarmUnavailable
            now + def.pomodoroMinutes * 60_000L
        } else {
            null
        }
        if (endTs != null && !alarm.schedule(endTs)) {
            return SwitchStateResult.ExactAlarmUnavailable
        }
        if (endTs == null) alarm.cancel()
        val event = StateEventEntity(
            stateId = def.id,
            stateName = def.name,
            category = def.category,
            timestamp = now,
            pomodoroEndTs = endTs,
            pomodoroApplied = endTs != null,
        )
        val newId = try {
            eventDao.insert(event)
        } catch (t: Throwable) {
            if (endTs != null) alarm.cancel()
            throw t
        }
        return SwitchStateResult.Success(event.copy(id = newId).toModel())
    }

    /** Adds a manual correction event at [timestamp]. This intentionally does not apply a
     *  pomodoro; it is a history edit used to repair stats. */
    suspend fun addManualEvent(stateId: String, timestamp: Long): StateEvent? {
        initialize()
        val def = stateDefDao.getById(stateId) ?: return null
        val latest = eventDao.getLatest()
        val becomesLatest = latest == null || timestamp >= latest.timestamp
        val event = StateEventEntity(
            stateId = def.id,
            stateName = def.name,
            category = def.category,
            timestamp = timestamp,
            pomodoroEndTs = null,
            pomodoroApplied = false,
        )
        val newId = eventDao.insert(event)
        if (becomesLatest) {
            alarm.cancel()
        }
        return event.copy(id = newId).toModel()
    }

    /** If the active pomodoro has already ended (and nothing newer exists), switch to the exit
     *  state. Returns the exit StateDef when it fired, else null. */
    suspend fun reconcilePomodoro(now: Long): StateDef? {
        initialize()
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
    suspend fun reconcileAndArm(now: Long): StateDef? {
        initialize()
        val latest = eventDao.getLatest()
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
    fun testAlarm(delayMs: Long): Boolean {
        alarm.cancel()
        return alarm.schedule(System.currentTimeMillis() + delayMs, isTest = true)
    }

    suspend fun upsertStateDef(def: StateDef) {
        initialize()
        stateDefDao.upsert(def.toEntity())
    }

    suspend fun deleteStateDef(id: String) {
        initialize()
        stateDefDao.delete(id)
    }

    suspend fun addStateDef(name: String): StateDef {
        initialize()
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
        initialize()
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
        initialize()
        dbRef.withTransaction {
            stateDefDao.deleteAll()
            stateDefDao.upsertAll(defaultStateDefs().map { it.toEntity() })
            settingDao.deleteAll()
            settingDao.set(SettingEntity(DefaultConfig.SETTING_EXIT_STATE, DefaultConfig.DEFAULT_EXIT_STATE_ID))
            settingDao.set(SettingEntity(DefaultConfig.SETTING_LAMBDA, DefaultConfig.DEFAULT_LAMBDA.value.toString()))
            settingDao.set(SettingEntity(DefaultConfig.SETTING_DATA_VERSION, DefaultConfig.CURRENT_DATA_VERSION.toString()))
            // targets + tips fall back to defaults by absence of keys
        }
        reconcileAndArm(System.currentTimeMillis())
    }

    private suspend fun ensureLifeNoteMigration() {
        val life = stateDefDao.getById(DefaultConfig.ID_LIFE) ?: return
        if (life.note.isNullOrEmpty()) {
            stateDefDao.upsert(life.copy(note = DefaultConfig.DEFAULT_LIFE_NOTE))
        }
    }

    /** v1.3.1: replace old 「杂活」 states/history with the built-in 「喂狗」 state. */
    private suspend fun migrateDogStateMigration() {
        val all = stateDefDao.getAll()
        val existingDog = all.firstOrNull { it.name == "喂狗" }
        val dogDef = (existingDog ?: stateDefDao.getById(DefaultConfig.ID_DOG) ?: StateDefEntity(
            id = DefaultConfig.ID_DOG,
            name = "喂狗",
            category = Category.EFFECTIVE.name,
            pomodoroEnabled = true,
            pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES,
            sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            note = DefaultConfig.DEFAULT_DOG_NOTE,
        )).copy(
            name = "喂狗",
            category = Category.EFFECTIVE.name,
            pomodoroEnabled = true,
            pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES,
            note = DefaultConfig.DEFAULT_DOG_NOTE,
        )
        stateDefDao.upsert(dogDef)
        eventDao.replaceStateByNameContaining(
            fragment = "杂活",
            stateId = dogDef.id,
            stateName = dogDef.name,
            category = dogDef.category,
        )
        stateDefDao.getByNameContaining("杂活")
            .filterNot { it.id == DefaultConfig.ID_DOG }
            .forEach { stateDefDao.delete(it.id) }
    }

    /** v1.4: 「喂狗」 is rest time; rewrite snapshots so all historic and active time agrees. */
    private suspend fun migrateDogToRestMigration() {
        stateDefDao.getAll()
            .filter { it.id == DefaultConfig.ID_DOG || it.name == "喂狗" }
            .forEach { dog -> stateDefDao.upsert(dog.copy(category = Category.REST.name)) }
        eventDao.updateCategoryByState(
            stateId = DefaultConfig.ID_DOG,
            stateName = "喂狗",
            category = Category.REST.name,
        )
    }

    /** v1.4: collapse the former arbitrary utilization target into one of three λ standards. */
    private suspend fun migrateLambdaSetting() {
        val existing = settingDao.get(DefaultConfig.SETTING_LAMBDA)?.toIntOrNull()
        val level = if (existing != null) {
            LambdaLevel.fromValue(existing)
        } else {
            val legacyRatio = settingDao.get(DefaultConfig.SETTING_TARGET_RATIO)?.toDoubleOrNull()
            LambdaLevel.nearestToRatio(legacyRatio)
        }
        settingDao.set(SettingEntity(DefaultConfig.SETTING_LAMBDA, level.value.toString()))
        settingDao.delete(DefaultConfig.SETTING_TARGET_RATIO)
    }

    /** Item 14: if the configured pomodoro exit state is an EFFECTIVE state and 摆烂 exists,
     *  switch the exit to 摆烂 (effective exit states violate the design philosophy). */
    private suspend fun migrateExitToSlackIfEffectiveMigration() {
        val exitId = settingDao.get(DefaultConfig.SETTING_EXIT_STATE) ?: return
        val exitDef = stateDefDao.getById(exitId) ?: return
        if (Category.fromString(exitDef.category) != Category.EFFECTIVE) return
        if (stateDefDao.getById(DefaultConfig.ID_SLACK) != null) {
            settingDao.set(SettingEntity(DefaultConfig.SETTING_EXIT_STATE, DefaultConfig.ID_SLACK))
        }
    }

    // ---- export / import ----
    suspend fun exportJson(now: Long): String {
        initialize()
        return JsonCodec.toJson(buildBundle(now))
    }

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
                lambda = getLambda().value,
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
        initialize()
        if (!isValidImport(bundle)) return false
        return try {
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
                // Imports can come from a 1.3.1 backup, so enforce current classification too.
                migrateDogToRestMigration()
                settingDao.set(SettingEntity(DefaultConfig.SETTING_EXIT_STATE, bundle.config.pomodoroExitStateId))
                val importedLambda = if (bundle.schemaVersion >= 3) {
                    LambdaLevel.fromValue(bundle.config.lambda)
                } else {
                    DefaultConfig.DEFAULT_LAMBDA
                }
                settingDao.set(SettingEntity(DefaultConfig.SETTING_LAMBDA, importedLambda.value.toString()))
                settingDao.set(
                    SettingEntity(DefaultConfig.SETTING_DATA_VERSION, DefaultConfig.CURRENT_DATA_VERSION.toString()),
                )
            }
            reconcileAndArm(System.currentTimeMillis())
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isValidImport(bundle: ExportBundle): Boolean {
        return try {
            if (bundle.schemaVersion !in 2..DefaultConfig.SCHEMA_VERSION) return false
            if (bundle.schemaVersion >= 3 && bundle.config.lambda !in setOf(2, 3, 5)) return false
            val states = bundle.config.states
            if (states.isEmpty() || states.any {
                    it.id.isBlank() || it.name.isBlank() ||
                        Category.entries.none { category -> category.name == it.category } ||
                        it.pomodoroMinutes <= 0
                }
            ) return false
            val stateIds = states.map { it.id }.toSet()
            if (stateIds.size != states.size) return false
            if (bundle.config.pomodoroExitStateId !in stateIds) return false
            bundle.events.all {
                it.stateId.isNotBlank() && it.stateName.isNotBlank() && it.timestamp >= 0 &&
                    Category.entries.any { category -> category.name == it.category } &&
                    (it.pomodoroEndTs == null || it.pomodoroEndTs >= it.timestamp) &&
                    it.pomodoroApplied == (it.pomodoroEndTs != null)
            }
        } catch (_: RuntimeException) {
            false
        }
    }
}
