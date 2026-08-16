package io.github.yingqiu0871.evolune.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class MedicationOccurrenceIdentityTest {
    private val planId = UUID(0L, 1L)
    private val slotId = UUID(1L, 1L)
    private val scheduledLocalDate = LocalDate.of(2025, 1, 2)

    @Test
    fun `same plan slot and local date have stable identity`() {
        assertEquals(
            MedicationOccurrenceIdentity.derive(planId, slotId, scheduledLocalDate),
            MedicationOccurrenceIdentity.derive(planId, slotId, scheduledLocalDate)
        )
    }

    @Test
    fun `different plans do not collide at the same instant`() {
        assertNotEquals(
            MedicationOccurrenceIdentity.derive(planId, slotId, scheduledLocalDate),
            MedicationOccurrenceIdentity.derive(UUID(0L, 2L), slotId, scheduledLocalDate)
        )
    }

    @Test
    fun `different local dates of one plan do not collide`() {
        assertNotEquals(
            MedicationOccurrenceIdentity.derive(planId, slotId, scheduledLocalDate),
            MedicationOccurrenceIdentity.derive(
                planId,
                slotId,
                scheduledLocalDate.plusDays(1)
            )
        )
    }

    @Test
    fun `duplicate local slots remain distinct occurrence keys`() {
        assertNotEquals(
            MedicationOccurrenceIdentity.derive(planId, slotId, scheduledLocalDate),
            MedicationOccurrenceIdentity.derive(planId, UUID(1L, 2L), scheduledLocalDate)
        )
    }
}
