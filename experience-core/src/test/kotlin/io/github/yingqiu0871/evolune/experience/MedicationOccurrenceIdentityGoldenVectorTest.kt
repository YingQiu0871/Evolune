package io.github.yingqiu0871.evolune.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/**
 * Frozen contract for occurrence identity (v1.7-A mandatory backlog item M1).
 *
 * The expected UUID below was derived independently of this codebase
 * (RFC 4122 version-3 / MD5 over the canonical name, reproduced by an external
 * MD5 implementation), not by calling the function under test.
 *
 * Do not regenerate the expectation from the implementation: if the algorithm or
 * the canonical name ever changes, this test must fail so the change is reviewed.
 */
class MedicationOccurrenceIdentityGoldenVectorTest {

    private val planId = UUID(0L, 1L)
    private val slotId = UUID(1L, 1L)
    private val intendedLocalDate = LocalDate.of(2025, 1, 2)

    @Test
    fun `golden vector pins the exact occurrence uuid`() {
        assertEquals(
            UUID.fromString("31d6dd5f-b6f1-3607-8d62-28947fa406fb"),
            MedicationOccurrenceIdentity.derive(planId, slotId, intendedLocalDate).value
        )
    }

    @Test
    fun `occurrence identity is a version 3 uuid`() {
        assertEquals(3, MedicationOccurrenceIdentity.derive(planId, slotId, intendedLocalDate).value.version())
    }

    @Test
    fun `golden vector is stable across repeated derivation`() {
        assertEquals(
            MedicationOccurrenceIdentity.derive(planId, slotId, intendedLocalDate),
            MedicationOccurrenceIdentity.derive(planId, slotId, intendedLocalDate)
        )
    }

    @Test
    fun `golden vector changes when the intended local date changes`() {
        assertNotEquals(
            UUID.fromString("31d6dd5f-b6f1-3607-8d62-28947fa406fb"),
            MedicationOccurrenceIdentity.derive(planId, slotId, intendedLocalDate.plusDays(1)).value
        )
    }

    @Test
    fun `golden vector changes when the slot changes`() {
        assertNotEquals(
            UUID.fromString("31d6dd5f-b6f1-3607-8d62-28947fa406fb"),
            MedicationOccurrenceIdentity.derive(planId, UUID(1L, 2L), intendedLocalDate).value
        )
    }

    @Test
    fun `golden vector changes when the plan changes`() {
        assertNotEquals(
            UUID.fromString("31d6dd5f-b6f1-3607-8d62-28947fa406fb"),
            MedicationOccurrenceIdentity.derive(UUID(0L, 2L), slotId, intendedLocalDate).value
        )
    }
}
