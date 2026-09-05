package io.github.yingqiu0871.evolune.release

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.data.SettingsDataStore
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.repository.RoomDoseEventRepository
import io.github.yingqiu0871.evolune.data.repository.RoomMedicationPlanRepository
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Two-phase device contract for the v1.4 -> v1.5 package upgrade.
 *
 * Run [prepareV14State] against the v1.4 debug APK, install the v1.5 debug APK
 * in place, then run [verifyV15State] against the upgraded package. The test
 * deliberately uses stable synthetic records and does not touch production
 * release signing or user data.
    */
@RunWith(AndroidJUnit4::class)
class V15InPlaceUpgradeDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun prepareV14State() {
        requirePhase(PREPARE_PHASE)
        runBlocking {
            val database = openDatabase()
            try {
                database.clearAllTables()
                val planId = PLAN_ID
                val slotId = when (
                    val result = ScheduledDoseSlotId.generate(planId, 0, LocalTime.of(8, 30))
                ) {
                    is io.github.yingqiu0871.evolune.core.model.SlotIdResult.Success -> result.id
                    is io.github.yingqiu0871.evolune.core.model.SlotIdResult.Failure ->
                        error("failed to create upgrade fixture slot: ${result.error}")
                }
                val plan = MedicationPlan(
                    id = planId,
                    name = "v1.4 upgrade fixture",
                    route = Route.ORAL,
                    ester = Ester.E2,
                    doseMG = 1.25,
                    scheduleType = ScheduleType.DAILY,
                    slots = listOf(ScheduledDoseSlot(slotId, planId, LocalTime.of(8, 30), 0)),
                    daysOfWeek = emptySet(),
                    intervalDays = 1,
                    isEnabled = true,
                    extras = emptyMap(),
                    createdAt = Instant.parse("2026-08-01T12:00:00Z")
                )
                val event = DoseEvent(
                    id = EVENT_ID,
                    route = Route.ORAL,
                    occurredAt = Instant.parse("2026-09-04T08:30:00Z"),
                    zoneId = ZoneId.of("Europe/Paris"),
                    localDate = java.time.LocalDate.of(2026, 9, 4),
                    doseMG = 1.25,
                    ester = Ester.E2,
                    slotId = slotId,
                    source = DoseEventSource.MANUAL
                )

                assertEquals(
                    PlanSaveResult.Created,
                    RoomMedicationPlanRepository(database).save(plan)
                )
                assertEquals(InsertResult.Inserted, RoomDoseEventRepository(database).insert(event))
                assertEquals(plan, RoomMedicationPlanRepository(database).getById(planId))
                assertEquals(event, RoomDoseEventRepository(database).getById(EVENT_ID))
            } finally {
                database.close()
            }

            SettingsDataStore(context).also { settings ->
                settings.updateBodyWeight(62.4)
                settings.updateThemeMode(ThemeMode.DARK)
                settings.updateAutoCheckUpdates(false)
            }
            OnboardingStateStore(context, isExistingInstallation = true).also { onboarding ->
                onboarding.initializeIfNeeded()
                onboarding.acceptTerms()
                onboarding.acknowledgeMedicalPkDisclosure()
                onboarding.completeOnboarding()
                onboarding.markFeatureTutorialHandled()
            }
        }
    }

    @Test
    fun verifyV15State() {
        requirePhase(VERIFY_PHASE)
        runBlocking {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            assertEquals("1.5.0-debug", packageInfo.versionName)
            assertEquals(101_050_000L, packageInfo.longVersionCode)

            val database = openDatabase()
            try {
                assertEquals(3, userVersion(database))
                assertEquals(1, rowCount(database, "medication_plans"))
                assertEquals(1, rowCount(database, "scheduled_dose_slots"))
                assertEquals(1, rowCount(database, "dose_events"))
                assertEquals(
                    EXPECTED_PLAN,
                    RoomMedicationPlanRepository(database).getById(PLAN_ID)
                )
                assertEquals(
                    EXPECTED_EVENT,
                    RoomDoseEventRepository(database).getById(EVENT_ID)
                )
                database.openHelper.readableDatabase.query("PRAGMA integrity_check").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("ok", cursor.getString(0))
                }
            } finally {
                database.close()
            }

            val settings = SettingsDataStore(context).userSettings.first()
            assertEquals(62.4, settings.bodyWeight, 0.0)
            assertEquals(ThemeMode.DARK, settings.themeMode)

            val onboarding = OnboardingStateStore(context, isExistingInstallation = true).state.first()
            assertTrue(onboarding.isComplete)
            assertFalse(onboarding.featureTutorialAutoLaunchPending)
        }
    }

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        DATABASE_NAME
    ).allowMainThreadQueries().build()

    private fun userVersion(database: AppDatabase): Int =
        database.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun rowCount(database: AppDatabase, table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun requirePhase(expected: String) {
        assumeTrue(
            "Run this two-phase test with -e $PHASE_ARGUMENT $expected",
            InstrumentationRegistry.getArguments().getString(PHASE_ARGUMENT) == expected
        )
    }

    private companion object {
        const val PHASE_ARGUMENT = "v15UpgradePhase"
        const val PREPARE_PHASE = "prepare"
        const val VERIFY_PHASE = "verify"
        const val DATABASE_NAME = "evolune_database"
        val PLAN_ID: UUID = UUID.fromString("92000000-0000-0000-0000-000000000001")
        val EVENT_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000001")
        val SLOT_ID: UUID = when (
            val result = ScheduledDoseSlotId.generate(PLAN_ID, 0, LocalTime.of(8, 30))
        ) {
            is io.github.yingqiu0871.evolune.core.model.SlotIdResult.Success -> result.id
            is io.github.yingqiu0871.evolune.core.model.SlotIdResult.Failure ->
                error("failed to create expected upgrade fixture slot: ${result.error}")
        }
        val EXPECTED_PLAN = MedicationPlan(
            id = PLAN_ID,
            name = "v1.4 upgrade fixture",
            route = Route.ORAL,
            ester = Ester.E2,
            doseMG = 1.25,
            scheduleType = ScheduleType.DAILY,
            slots = listOf(ScheduledDoseSlot(SLOT_ID, PLAN_ID, LocalTime.of(8, 30), 0)),
            daysOfWeek = emptySet(),
            intervalDays = 1,
            isEnabled = true,
            extras = emptyMap(),
            createdAt = Instant.parse("2026-08-01T12:00:00Z")
        )
        val EXPECTED_EVENT = DoseEvent(
            id = EVENT_ID,
            route = Route.ORAL,
            occurredAt = Instant.parse("2026-09-04T08:30:00Z"),
            zoneId = ZoneId.of("Europe/Paris"),
            localDate = java.time.LocalDate.of(2026, 9, 4),
            doseMG = 1.25,
            ester = Ester.E2,
            slotId = SLOT_ID,
            source = DoseEventSource.MANUAL
        )
    }
}
