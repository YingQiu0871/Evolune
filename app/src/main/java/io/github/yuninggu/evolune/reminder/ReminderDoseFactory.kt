package io.github.yuninggu.evolune.reminder

import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.model.MedicationPlan
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
    recordedAtMillis: Long,
    scheduledAtMillis: Long,
    zoneId: ZoneId
): DoseEvent {
    require(recordedAtMillis > 0)
    require(scheduledAtMillis > 0)

    val occurrenceKey = "reminder:${plan.id}:$scheduledAtMillis"
    val occurredAt = Instant.ofEpochMilli(recordedAtMillis)
    return DoseEvent(
        id = UUID.nameUUIDFromBytes(
            occurrenceKey.toByteArray(StandardCharsets.UTF_8)
        ),
        route = plan.route,
        occurredAt = occurredAt,
        zoneId = zoneId,
        localDate = occurredAt.atZone(zoneId).toLocalDate(),
        doseMG = plan.doseMG,
        ester = plan.ester,
        extras = plan.extras,
        slotId = null,
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
