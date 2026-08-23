package io.github.yingqiu0871.evolune.data

import androidx.room.withTransaction
import io.github.yingqiu0871.evolune.backup.BackupDoseEventV1
import io.github.yingqiu0871.evolune.backup.BackupMedicationPlanV1
import io.github.yingqiu0871.evolune.backup.BackupScheduledDoseSlotV1
import io.github.yingqiu0871.evolune.backup.BackupSettingsV1
import io.github.yingqiu0871.evolune.backup.RestorePersistence
import io.github.yingqiu0871.evolune.backup.RestoreRoomState
import io.github.yingqiu0871.evolune.backup.toBackupSettings
import io.github.yingqiu0871.evolune.backup.toUserSettings
import io.github.yingqiu0871.evolune.core.time.LegacyTimeAdapter
import io.github.yingqiu0871.evolune.core.time.LegacyTimeResult
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * Production Room/DataStore gateway. Room replacement is one database
 * transaction; settings replacement is one DataStore edit through
 * [AtomicSettingsStore].
 */
internal class RoomRestorePersistence(
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val atomicSettingsStore: AtomicSettingsStore
) : RestorePersistence {
    override suspend fun readRoomState(): RestoreRoomState {
        val plans = database.medicationPlanDao().getAllPlansForRestore()
        val slots = database.scheduledDoseSlotDao().getAllSlotsForRestore()
        val events = database.doseEventDao().getAllEventsForRestore()
        return RestoreRoomState(
            medicationPlans = plans.toBackupPlans(slots),
            scheduledDoseSlots = slots.map { it.toBackupSlot() },
            doseEvents = events.map { it.toBackupEvent() }
        ).canonical()
    }

    override suspend fun replaceRoom(state: RestoreRoomState) {
        val entities = state.toRoomEntities()
        database.withTransaction {
            // Events have no slot FK, slots have a plan FK, and plans are the
            // root. The explicit order keeps the replacement valid on every
            // Room v3 database, including dangling event.slotId values.
            database.doseEventDao().deleteAllEventsIfPresent()
            database.scheduledDoseSlotDao().deleteAllSlotsIfPresent()
            database.medicationPlanDao().deleteAllPlansIfPresent()
            if (entities.plans.isNotEmpty()) {
                database.medicationPlanDao().insertPlansForRestore(entities.plans)
            }
            if (entities.slots.isNotEmpty()) {
                database.scheduledDoseSlotDao().insertSlotsForRestore(entities.slots)
            }
            if (entities.events.isNotEmpty()) {
                database.doseEventDao().insertEventsForRestore(entities.events)
            }
        }
    }

    override suspend fun readSettings(): BackupSettingsV1 =
        settingsStore.userSettings.first().toBackupSettings()

    override suspend fun replaceSettings(settings: BackupSettingsV1): Boolean =
        atomicSettingsStore.replaceSettings(settings.toUserSettings())
}

private data class RoomEntities(
    val plans: List<MedicationPlanEntity>,
    val slots: List<ScheduledDoseSlotEntity>,
    val events: List<DoseEventEntity>
)

private fun RestoreRoomState.toRoomEntities(): RoomEntities {
    val slotsByPlan = scheduledDoseSlots.groupBy { it.planId }
    val plans = medicationPlans.map { plan ->
        val planId = UUID.fromString(plan.id)
        val slots = slotsByPlan[plan.id].orEmpty().sortedBy { it.position }
        MedicationPlanEntity(
            id = planId,
            name = plan.name,
            route = plan.route,
            ester = plan.ester,
            doseMG = plan.doseMG,
            scheduleType = plan.scheduleType,
            timeOfDay = slots.map { it.localTime },
            daysOfWeek = plan.daysOfWeek.toSet(),
            intervalDays = plan.intervalDays,
            isEnabled = plan.isEnabled,
            extras = plan.extras,
            createdAt = Instant.parse(plan.createdAt).toEpochMilli()
        )
    }
    val slots = scheduledDoseSlots.map { slot ->
        ScheduledDoseSlotEntity(
            id = UUID.fromString(slot.id),
            planId = UUID.fromString(slot.planId),
            localTime = slot.localTime,
            position = slot.position
        )
    }
    val events = doseEvents.map { event ->
        val occurredAtMillis = Instant.parse(event.occurredAt).toEpochMilli()
        val timeH = when (val result = LegacyTimeAdapter.epochMillisToTimeH(occurredAtMillis)) {
            is LegacyTimeResult.Success -> result.value
            is LegacyTimeResult.Failure -> error("Unable to map restore event time")
        }
        DoseEventEntity(
            id = UUID.fromString(event.id),
            route = event.route,
            timeH = timeH,
            doseMG = event.doseMG,
            ester = event.ester,
            extras = event.extras,
            occurredAtEpochMillis = occurredAtMillis,
            zoneId = event.zoneId,
            localDate = event.localDate,
            slotId = event.slotId?.let(UUID::fromString),
            source = event.source,
            status = event.status,
            revision = event.revision
        )
    }
    return RoomEntities(plans, slots, events)
}

private fun List<MedicationPlanEntity>.toBackupPlans(
    slots: List<ScheduledDoseSlotEntity>
): List<BackupMedicationPlanV1> = map { plan ->
    val planSlots = slots.filter { it.planId == plan.id }.sortedBy { it.position }
    val canonicalTimes = planSlots.map { it.localTime }
    val storedTimes = plan.timeOfDay.map { canonicalLocalTime(LocalTime.parse(it)) }
    check(storedTimes == canonicalTimes) { "Room plan and slot state disagree: ${plan.id}" }
    BackupMedicationPlanV1(
        id = plan.id.toString(),
        name = plan.name,
        route = plan.route,
        ester = plan.ester,
        doseMG = plan.doseMG,
        scheduleType = plan.scheduleType,
        daysOfWeek = plan.daysOfWeek.sorted(),
        intervalDays = plan.intervalDays,
        isEnabled = plan.isEnabled,
        extras = plan.extras,
        createdAt = Instant.ofEpochMilli(plan.createdAt).toString()
    )
}

private fun ScheduledDoseSlotEntity.toBackupSlot(): BackupScheduledDoseSlotV1 =
    BackupScheduledDoseSlotV1(
        id = id.toString(),
        planId = planId.toString(),
        localTime = localTime,
        position = position
    )

private fun DoseEventEntity.toBackupEvent(): BackupDoseEventV1 {
    when (val result = LegacyTimeAdapter.timeHToEpochMillis(timeH)) {
        is LegacyTimeResult.Success -> check(result.value == occurredAtEpochMillis) {
            "Room event time shadows disagree: $id"
        }
        is LegacyTimeResult.Failure -> error("Invalid Room event time: $id")
    }
    return BackupDoseEventV1(
        id = id.toString(),
        route = route,
        occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis).toString(),
        zoneId = zoneId,
        localDate = localDate,
        doseMG = doseMG,
        ester = ester,
        extras = extras,
        slotId = slotId?.toString(),
        source = source,
        status = status,
        revision = revision
    )
}

private fun canonicalLocalTime(value: LocalTime): String =
    value.hour.toString().padStart(2, '0') + ":" +
        value.minute.toString().padStart(2, '0')
