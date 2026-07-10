package com.seqn.timewatcher

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.seqn.timewatcher.data.db.AppDatabase
import com.seqn.timewatcher.data.db.entity.SettingEntity
import com.seqn.timewatcher.data.model.DefaultConfig
import com.seqn.timewatcher.data.model.ExportBundle
import com.seqn.timewatcher.data.model.ExportConfig
import com.seqn.timewatcher.data.model.ExportStateDef
import com.seqn.timewatcher.data.model.JsonCodec
import com.seqn.timewatcher.data.model.LambdaLevel
import com.seqn.timewatcher.data.repo.SwitchStateResult
import com.seqn.timewatcher.data.repo.Repository
import com.seqn.timewatcher.pomodoro.PomodoroAlarm
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private val scheduled = mutableListOf<Long>()
    private var cancelCount = 0
    private var exactAlarmAvailable = true
    private var schedulingSucceeds = true

    private val fakeAlarm = object : PomodoroAlarm {
        override fun canScheduleExactAlarms() = exactAlarmAvailable
        override fun schedule(endTs: Long, isTest: Boolean): Boolean {
            if (!exactAlarmAvailable || !schedulingSucceeds) return false
            scheduled += endTs
            return true
        }
        override fun cancel() { cancelCount++ }
    }

    @Before
    fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = Repository(db, fakeAlarm)
        repo.initialize()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seeding_creates_seven_default_states() = runBlocking {
        assertThat(repo.getStateDefs()).hasSize(7)
        assertThat(repo.getExitStateId()).isEqualTo(DefaultConfig.DEFAULT_EXIT_STATE_ID)
    }

    @Test
    fun initialized_empty_state_list_is_not_mistaken_for_a_new_database() = runBlocking {
        db.stateDefDao().deleteAll()

        repo = Repository(db, fakeAlarm)
        repo.initialize()

        assertThat(repo.getStateDefs()).isEmpty()
    }

    @Test
    fun migrate_dog_state_removes_chores_and_rewrites_history() = runBlocking {
        repo.upsertStateDef(
            com.seqn.timewatcher.data.model.StateDef(
                id = "chores",
                name = "家务杂活",
                category = com.seqn.timewatcher.data.model.Category.WASTE,
                pomodoroEnabled = false,
                pomodoroMinutes = 15,
                sortOrder = 99,
            ),
        )
        repo.addManualEvent("chores", 1_000L)

        db.settingDao().set(SettingEntity(DefaultConfig.SETTING_DATA_VERSION, "1"))
        repo = Repository(db, fakeAlarm)
        repo.initialize()

        val defs = repo.getStateDefs()
        assertThat(defs.any { it.name.contains("杂活") }).isFalse()
        assertThat(defs.any { it.id == DefaultConfig.ID_DOG && it.name == "喂狗" }).isTrue()

        val events = repo.getEvents()
        assertThat(events.single().stateId).isEqualTo(DefaultConfig.ID_DOG)
        assertThat(events.single().stateName).isEqualTo("喂狗")
        assertThat(events.single().category).isEqualTo(com.seqn.timewatcher.data.model.Category.REST)
    }

    @Test
    fun migrate_dog_to_rest_updates_definition_history_and_current_event() = runBlocking {
        repo.upsertStateDef(
            repo.getStateDefs().single { it.id == DefaultConfig.ID_DOG }
                .copy(category = com.seqn.timewatcher.data.model.Category.EFFECTIVE),
        )
        repo.addManualEvent(DefaultConfig.ID_DOG, 1_000L)

        db.settingDao().set(SettingEntity(DefaultConfig.SETTING_DATA_VERSION, "2"))
        repo = Repository(db, fakeAlarm)
        repo.initialize()

        assertThat(repo.getStateDefs().single { it.id == DefaultConfig.ID_DOG }.category)
            .isEqualTo(com.seqn.timewatcher.data.model.Category.REST)
        assertThat(repo.getEvents().single().category)
            .isEqualTo(com.seqn.timewatcher.data.model.Category.REST)
        assertThat(repo.getLatestEvent()!!.category)
            .isEqualTo(com.seqn.timewatcher.data.model.Category.REST)
    }

    @Test
    fun migrate_legacy_target_ratio_to_nearest_lambda_level() = runBlocking {
        db.settingDao().delete(DefaultConfig.SETTING_LAMBDA)
        db.settingDao().set(SettingEntity(DefaultConfig.SETTING_TARGET_RATIO, "0.35"))
        db.settingDao().set(SettingEntity(DefaultConfig.SETTING_DATA_VERSION, "3"))

        repo = Repository(db, fakeAlarm)
        repo.initialize()

        assertThat(repo.getLambda()).isEqualTo(LambdaLevel.AMBITIOUS)
        assertThat(db.settingDao().get(DefaultConfig.SETTING_TARGET_RATIO)).isNull()
    }

    @Test
    fun missing_legacy_target_migrates_to_recommended_lambda_five() = runBlocking {
        db.settingDao().delete(DefaultConfig.SETTING_LAMBDA)
        db.settingDao().set(SettingEntity(DefaultConfig.SETTING_DATA_VERSION, "3"))

        repo = Repository(db, fakeAlarm)
        repo.initialize()

        assertThat(repo.getLambda()).isEqualTo(LambdaLevel.STEADY)
    }

    @Test
    fun switching_to_pomodoro_state_arms_alarm() = runBlocking {
        val now = 1_000_000L
        val ev = (repo.switchState(DefaultConfig.ID_WORK, now) as SwitchStateResult.Success).event
        assertThat(ev.pomodoroEndTs).isEqualTo(now + DefaultConfig.DEFAULT_POMODORO_MINUTES * 60_000L)
        assertThat(ev.pomodoroApplied).isTrue()
        assertThat(scheduled).contains(now + DefaultConfig.DEFAULT_POMODORO_MINUTES * 60_000L)
    }

    @Test
    fun switching_to_non_pomodoro_state_does_not_arm() = runBlocking {
        val ev = (repo.switchState(DefaultConfig.ID_SLACK, 1_000L) as SwitchStateResult.Success).event
        assertThat(ev.pomodoroEndTs).isNull()
        assertThat(scheduled).isEmpty()
    }

    @Test
    fun switching_into_current_state_is_a_noop_and_keeps_elapsed_time() = runBlocking {
        val first = (repo.switchState(DefaultConfig.ID_SLACK, 1_000L) as SwitchStateResult.Success).event
        val second = repo.switchState(DefaultConfig.ID_SLACK, 5_000L) // same state again
        assertThat((second as SwitchStateResult.Success).event.id).isEqualTo(first.id) // no new event inserted
        assertThat(repo.getEvents()).hasSize(1)
    }

    @Test
    fun pomodoro_state_is_rejected_when_exact_alarm_is_unavailable() = runBlocking {
        exactAlarmAvailable = false

        val result = repo.switchState(DefaultConfig.ID_WORK, 1_000L)

        assertThat(result).isEqualTo(SwitchStateResult.ExactAlarmUnavailable)
        assertThat(repo.getEvents()).isEmpty()
        assertThat(scheduled).isEmpty()
    }

    @Test
    fun pomodoro_state_is_rejected_when_exact_alarm_cannot_be_confirmed() = runBlocking {
        schedulingSucceeds = false

        val result = repo.switchState(DefaultConfig.ID_WORK, 1_000L)

        assertThat(result).isEqualTo(SwitchStateResult.ExactAlarmUnavailable)
        assertThat(repo.getEvents()).isEmpty()
    }

    @Test
    fun reconcile_after_end_switches_to_exit_state() = runBlocking {
        repo.switchState(DefaultConfig.ID_WORK, 0L)
        val exit = repo.reconcilePomodoro(100_000_000L) // long after the 25-min end
        assertThat(exit).isNotNull()
        val latest = repo.getLatestEvent()
        assertThat(latest!!.stateId).isEqualTo(DefaultConfig.ID_SLACK)
    }

    @Test
    fun reconcile_before_end_does_nothing() = runBlocking {
        repo.switchState(DefaultConfig.ID_WORK, 0L)
        val exit = repo.reconcilePomodoro(60_000L) // 1 min in, well before 25-min end
        assertThat(exit).isNull()
        val latest = repo.getLatestEvent()
        assertThat(latest!!.stateId).isEqualTo(DefaultConfig.ID_WORK)
    }

    @Test
    fun export_then_import_round_trips_data() = runBlocking {
        // Use non-pomodoro states so import's reconcile doesn't append an exit event.
        repo.switchState(DefaultConfig.ID_SLEEP, 1_000L)
        repo.switchState(DefaultConfig.ID_SLACK, 2_000L)
        val originalDefs = repo.getStateDefs().size
        val originalEvents = repo.getEvents().size
        repo.setLambda(LambdaLevel.AMBITIOUS)

        val json = repo.exportJson(3_000L)
        assertThat(json).contains("schemaVersion")
        val bundle = com.seqn.timewatcher.data.model.JsonCodec.fromJson(json)
        assertThat(bundle).isNotNull()
        assertThat(bundle!!.config.lambda).isEqualTo(3)

        // Importing the bundle replaces tables; data must survive the round trip.
        assertThat(repo.importBundle(bundle)).isTrue()
        assertThat(repo.getStateDefs()).hasSize(originalDefs)
        assertThat(repo.getEvents()).hasSize(originalEvents)
        assertThat(repo.getLambda()).isEqualTo(LambdaLevel.AMBITIOUS)
    }

    @Test
    fun schema_two_backup_imports_with_recommended_lambda() = runBlocking {
        val current = JsonCodec.fromJson(repo.exportJson(3_000L))!!
        val oldBundle = current.copy(
            schemaVersion = 2,
            config = current.config.copy(lambda = null),
        )

        repo.setLambda(LambdaLevel.SPRINT)
        assertThat(repo.importBundle(oldBundle)).isTrue()

        assertThat(repo.getLambda()).isEqualTo(LambdaLevel.STEADY)
    }

    @Test
    fun invalid_import_is_rejected_without_replacing_existing_data() = runBlocking {
        repo.switchState(DefaultConfig.ID_SLACK, 1_000L)
        val invalid = ExportBundle(
            schemaVersion = DefaultConfig.SCHEMA_VERSION,
            exportedAt = 2_000L,
            config = ExportConfig(
                states = listOf(
                    ExportStateDef("bad", "Bad", "UNKNOWN", false, 30, 0),
                ),
                pomodoroExitStateId = "missing",
            ),
            events = emptyList(),
        )

        assertThat(repo.importBundle(invalid)).isFalse()
        assertThat(repo.getStateDefs()).hasSize(7)
        assertThat(repo.getEvents()).hasSize(1)
    }

    @Test
    fun structurally_incomplete_json_is_rejected_without_throwing() = runBlocking {
        val malformed = JsonCodec.fromJson("""{"schemaVersion":2}""")

        assertThat(malformed).isNotNull()
        assertThat(repo.importBundle(malformed!!)).isFalse()
        assertThat(repo.getStateDefs()).hasSize(7)
    }
}
