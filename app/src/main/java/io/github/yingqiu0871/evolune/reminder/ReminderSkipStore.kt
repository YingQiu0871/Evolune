package io.github.yingqiu0871.evolune.reminder

import android.content.Context
import java.util.UUID

/** Occurrence-scoped suppression for reminder delivery; it never records a dose. */
internal class ReminderSkipStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun markSkipped(planId: UUID, slotId: UUID, scheduledAtMillis: Long): Boolean {
        if (scheduledAtMillis <= 0L) return false
        return synchronized(WRITE_LOCK) {
            val current = preferences.getStringSet(KEY_OCCURRENCES, emptySet()).orEmpty()
            val updated = updateReminderSkipKeys(
                current = current,
                newKey = reminderSkipKey(planId, slotId, scheduledAtMillis),
                expiryFloorMillis = expiryFloor()
            )
            preferences.edit().putStringSet(KEY_OCCURRENCES, updated).commit()
        }
    }

    fun isSkipped(planId: UUID, slotId: UUID, scheduledAtMillis: Long): Boolean =
        reminderSkipKey(planId, slotId, scheduledAtMillis) in
            preferences.getStringSet(KEY_OCCURRENCES, emptySet()).orEmpty()

    private fun expiryFloor(): Long = System.currentTimeMillis() - RETENTION_MILLIS

    private companion object {
        const val PREFERENCES_NAME = "reminder_skipped_occurrences"
        const val KEY_OCCURRENCES = "occurrences"
        const val RETENTION_MILLIS = 48L * 60L * 60L * 1000L
        val WRITE_LOCK = Any()
    }
}

internal fun reminderSkipKey(planId: UUID, slotId: UUID, scheduledAtMillis: Long): String =
    "$planId|$slotId|$scheduledAtMillis"

internal fun reminderSkipScheduledAt(key: String): Long? = key.substringAfterLast('|').toLongOrNull()

internal fun updateReminderSkipKeys(
    current: Set<String>,
    newKey: String,
    expiryFloorMillis: Long
): Set<String> = current
    .asSequence()
    .filter { key ->
        reminderSkipScheduledAt(key)?.let { scheduledAt -> scheduledAt >= expiryFloorMillis } == true
    }
    .plus(newKey)
    .toSet()
