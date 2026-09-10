package io.github.yingqiu0871.evolune.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class ReminderSkipStorePolicyTest {
    @Test
    fun `skip key preserves exact plan slot and scheduled occurrence`() {
        val key = reminderSkipKey(UUID(0L, 1L), UUID(0L, 2L), 123456789L)
        assertEquals(123456789L, reminderSkipScheduledAt(key))
        assertNull(reminderSkipScheduledAt("invalid"))
    }

    @Test
    fun `update keeps current occurrences and removes expired or malformed entries`() {
        val currentKey = reminderSkipKey(UUID(0L, 1L), UUID(0L, 2L), 2_000L)
        val expiredKey = reminderSkipKey(UUID(0L, 3L), UUID(0L, 4L), 999L)
        val newKey = reminderSkipKey(UUID(0L, 5L), UUID(0L, 6L), 3_000L)

        assertEquals(
            setOf(currentKey, newKey),
            updateReminderSkipKeys(
                current = setOf(currentKey, expiredKey, "malformed"),
                newKey = newKey,
                expiryFloorMillis = 1_000L
            )
        )
    }

    @Test
    fun `updating the same occurrence is idempotent`() {
        val key = reminderSkipKey(UUID(0L, 7L), UUID(0L, 8L), 4_000L)

        assertEquals(
            setOf(key),
            updateReminderSkipKeys(setOf(key), key, expiryFloorMillis = 1_000L)
        )
    }
}
