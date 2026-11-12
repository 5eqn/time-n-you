package com.seqn.timewatcher

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.seqn.timewatcher.data.db.AppDatabase
import com.seqn.timewatcher.data.db.entity.SettingEntity
import com.seqn.timewatcher.data.model.DefaultConfig
import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.data.model.ExportBundle
import com.seqn.timewatcher.data.model.ExportConfig
import com.seqn.timewatcher.data.model.ExportStateDef
import com.seqn.timewatcher.data.model.JsonCodec
import com.seqn.timewatcher.data.model.LambdaLevel
import com.seqn.timewatcher.data.model.StateColors
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
        assertThat(repo.getMinimumFocusBreakMinutes()).isEqualTo(3)
        assertThat(repo.getStateColors()).hasSize(7)
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
    fun migrate_v5_overwrites_built_in_rest_state_defaults_after_older_migrations() = runBlocking {
        val changed = repo.getStateDefs().associateBy { it.id }
        repo.upsertStateDef(
            changed.getValue(DefaultConfig.ID_LIFE).copy(
                category = Category.EFFECTIVE,
                pomodoroEnabled = true,
                note = "custom life",
            ),
        )
        repo.upsertStateDef(
            changed.getValue(DefaultConfig.ID_DOG).copy(
                category = Category.WASTE,
                pomodoroEnabled = true,
                note = "custom dog",
            ),
        )
        repo.upsertStateDef(
            changed.getValue(DefaultConfig.ID_SNOOZE).copy(
                category = Category.WASTE,
                pomodoroEnabled = false,
                pomodoroMinutes = 20,
                note = "custom snooze",
            ),
        )
        db.settingDao().set(SettingEntity(DefaultConfig.SETTING_DATA_VERSION, "0"))

        repo = Repository(db, fakeAlarm)
        repo.initialize()

        val migrated = repo.getStateDefs().associateBy { it.id }
        assertThat(migrated.getValue(DefaultConfig.ID_LIFE)).isEqualTo(
            changed.getValue(DefaultConfig.ID_LIFE).copy(
                category = Category.REST,
                pomodoroEnabled = false,
                note = DefaultConfig.DEFAULT_LIFE_NOTE,
            ),
        )
        assertThat(migrated.getValue(DefaultConfig.ID_DOG)).isEqualTo(
            changed.getValue(DefaultConfig.ID_DOG).copy(
                category = Category.REST,
                pomodoroEnabled = false,
                note = DefaultConfig.DEFAULT_DOG_NOTE,
            ),
        )
        assertThat(migrated.getValue(DefaultConfig.ID_SNOOZE)).isEqualTo(
            changed.getValue(DefaultConfig.ID_SNOOZE).copy(
                category = Category.REST,
                pomodoroEnabled = true,
                pomodoroMinutes = DefaultConfig.SNOOZE_POMODORO_MINUTES,
                note = DefaultConfig.DEFAULT_SNOOZE_NOTE,
            ),
        )
        assertThat(db.settingDao().get(DefaultConfig.SETTING_DATA_VERSION))
            .isEqualTo(DefaultConfig.CURRENT_DATA_VERSION.toString())
    }

    @Test
    fun migrate_v6_materializes_legacy_colors_for_definitions_and_history() = runBlocking {
        val custom = repo.addStateDef("历史颜色")
        repo.addManualEvent(custom.id, 1_000L)
        repo.deleteStateDef(custom.id)
        db.stateColorDao().deleteAll()
        db.settingDao().delete(DefaultConfig.SETTING_MIN_FOCUS_BREAK_MINUTES)
        db.settingDao().set(SettingEntity(DefaultConfig.SETTING_DATA_VERSION, "5"))

        repo = Repository(db, fakeAlarm)
        repo.initialize()

        val colors = repo.getStateColors()
        assertThat(colors[DefaultConfig.ID_WORK]).isEqualTo(0)
        assertThat(colors[custom.id]).isEqualTo(StateColors.legacyIndexFor(custom.id))
        assertThat(repo.getMinimumFocusBreakMinutes()).isEqualTo(3)
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
    fun completed_focus_pomodoro_requires_break_before_any_focus_pomodoro() = runBlocking {
        val end = DefaultConfig.DEFAULT_POMODORO_MINUTES * 60_000L
        repo.switchState(DefaultConfig.ID_WORK, 0L)
        repo.reconcilePomodoro(end)

        val blocked = repo.switchState(DefaultConfig.ID_HOBBY, end + 60_000L)

        assertThat(blocked).isEqualTo(SwitchStateResult.RestRequired(end + 180_000L))
        assertThat(repo.getLatestEvent()!!.stateId).isEqualTo(DefaultConfig.ID_SLACK)
        assertThat(repo.switchState(DefaultConfig.ID_SLEEP, end + 60_000L))
            .isInstanceOf(SwitchStateResult.Success::class.java)
    }

    @Test
    fun switching_after_delayed_alarm_reconciles_then_enforces_break() = runBlocking {
        val end = DefaultConfig.DEFAULT_POMODORO_MINUTES * 60_000L
        repo.switchState(DefaultConfig.ID_WORK, 0L)

        val result = repo.switchState(DefaultConfig.ID_HOBBY, end + 1_000L)

        assertThat(result).isEqualTo(SwitchStateResult.RestRequired(end + 180_000L))
        assertThat(repo.getLatestEvent()!!.timestamp).isEqualTo(end)
    }

    @Test
    fun leaving_focus_early_does_not_require_break() = runBlocking {
        repo.switchState(DefaultConfig.ID_WORK, 0L)
        repo.switchState(DefaultConfig.ID_SLACK, 60_000L)

        assertThat(repo.switchState(DefaultConfig.ID_HOBBY, 61_000L))
            .isInstanceOf(SwitchStateResult.Success::class.java)
    }

    @Test
    fun zero_break_setting_disables_block_without_special_case() = runBlocking {
        val end = DefaultConfig.DEFAULT_POMODORO_MINUTES * 60_000L
        repo.setMinimumFocusBreakMinutes(0)
        repo.switchState(DefaultConfig.ID_WORK, 0L)
        repo.reconcilePomodoro(end)

        assertThat(repo.switchState(DefaultConfig.ID_HOBBY, end))
            .isInstanceOf(SwitchStateResult.Success::class.java)
    }

    @Test
    fun active_break_survives_reset_and_v4_export_import() = runBlocking {
        val end = DefaultConfig.DEFAULT_POMODORO_MINUTES * 60_000L
        repo.switchState(DefaultConfig.ID_WORK, 0L)
        repo.reconcilePomodoro(end)
        repo.resetToDefaultConfig()

        assertThat(repo.switchState(DefaultConfig.ID_HOBBY, end + 1_000L))
            .isEqualTo(SwitchStateResult.RestRequired(end + 180_000L))

        val bundle = JsonCodec.fromJson(repo.exportJson(end + 2_000L))!!
        assertThat(bundle.config.lastFocusPomodoroEndTs).isEqualTo(end)
        assertThat(repo.importBundle(bundle)).isTrue()
        assertThat(repo.switchState(DefaultConfig.ID_WORK, end + 2_000L))
            .isEqualTo(SwitchStateResult.RestRequired(end + 180_000L))
    }

    @Test
    fun rest_pomodoro_completion_does_not_start_focus_break() = runBlocking {
        val end = DefaultConfig.SNOOZE_POMODORO_MINUTES * 60_000L
        repo.switchState(DefaultConfig.ID_SNOOZE, 0L)
        repo.reconcilePomodoro(end)

        assertThat(repo.switchState(DefaultConfig.ID_WORK, end))
            .isInstanceOf(SwitchStateResult.Success::class.java)
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
    fun schema_three_backup_without_colors_uses_legacy_assignment() = runBlocking {
        val custom = repo.addStateDef("旧状态")
        repo.addManualEvent(custom.id, 1_000L)
        repo.deleteStateDef(custom.id)
        val current = JsonCodec.fromJson(repo.exportJson(2_000L))!!
        val oldBundle = current.copy(
            schemaVersion = 3,
            config = current.config.copy(
                stateColors = null,
                minimumFocusBreakMinutes = null,
                lastFocusPomodoroEndTs = null,
            ),
        )

        assertThat(repo.importBundle(oldBundle)).isTrue()
        assertThat(repo.getStateColors()[custom.id]).isEqualTo(StateColors.legacyIndexFor(custom.id))
        assertThat(repo.getMinimumFocusBreakMinutes()).isEqualTo(3)
    }

    @Test
    fun chosen_color_survives_state_deletion_and_export_import() = runBlocking {
        val custom = repo.addStateDef("彩色状态")
        repo.setStateColor(custom.id, 15)
        repo.addManualEvent(custom.id, 1_000L)
        repo.deleteStateDef(custom.id)

        val bundle = JsonCodec.fromJson(repo.exportJson(2_000L))!!
        assertThat(bundle.config.stateColors!![custom.id]).isEqualTo(15)
        assertThat(repo.importBundle(bundle)).isTrue()
        assertThat(repo.getStateColors()[custom.id]).isEqualTo(15)
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
