package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceIdentity
import io.github.yingqiu0871.evolune.reminder.reminderOccurrences
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal data class WearSkipNotificationCommand(
    val occurrenceId: UUID,
    val planId: UUID,
    val slotId: UUID,
    val scheduledAt: Instant,
    val notificationId: Int
)

internal data class ValidatedWearSkip(
    val requestOffset: Int
)

internal fun validateWearSkipNotification(
    plan: MedicationPlan,
    command: WearSkipNotificationCommand,
    now: Instant,
    zoneId: ZoneId
): ValidatedWearSkip? {
    if (!plan.isEnabled || command.planId != plan.id || command.notificationId == 0) return null
    val reminder = reminderOccurrences(plan, now.atZone(zoneId).toLocalDateTime())
        .firstOrNull { occurrence ->
            occurrence.slotId == command.slotId &&
                occurrence.dateTime.atZone(zoneId).toInstant() == command.scheduledAt
        } ?: return null
    val expectedOccurrenceId = MedicationOccurrenceIdentity.derive(
        planId = plan.id,
        slotId = reminder.slotId,
        scheduledLocalDate = reminder.dateTime.toLocalDate()
    ).value
    if (command.occurrenceId != expectedOccurrenceId) return null
    if (plan.id.hashCode() + reminder.requestOffset != command.notificationId) return null
    return ValidatedWearSkip(reminder.requestOffset)
}
