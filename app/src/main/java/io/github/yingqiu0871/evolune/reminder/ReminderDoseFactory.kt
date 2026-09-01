package io.github.yingqiu0871.evolune.reminder

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Creates the dose record written by the notification's "confirm dose" action.
 *
 * The identifier is derived from the plan and the scheduled occurrence. This
 * makes the operation idempotent if Android delivers the action more than once.
 */
internal fun createReminderDoseEvent(
    plan: MedicationPlan,
    targetOccurrence: MedicationOccurrence,
    recordedAtMillis: Long,
    zoneId: ZoneId
): DoseEvent {
    require(recordedAtMillis > 0)
    require(targetOccurrence.planId == plan.id)
    require(targetOccurrence.zoneId == zoneId)

    val occurredAt = Instant.ofEpochMilli(recordedAtMillis)
    return DoseEvent(
        id = reminderDoseEventId(plan.id, targetOccurrence.scheduledAt.toEpochMilli()),
        route = plan.route,
        occurredAt = occurredAt,
        zoneId = zoneId,
        localDate = targetOccurrence.scheduledLocalDateTime.toLocalDate(),
        doseMG = plan.doseMG,
        ester = plan.ester,
        extras = plan.extras,
        slotId = targetOccurrence.slotId,
        source = DoseEventSource.REMINDER,
        status = DoseEventStatus.RECORDED,
        revision = 1L
    )
}

internal fun reminderDoseEventId(
    planId: UUID,
    scheduledAtMillis: Long
): UUID = UUID.nameUUIDFromBytes(
    "reminder:$planId:$scheduledAtMillis".toByteArray(StandardCharsets.UTF_8)
)
