package io.github.yingqiu0871.evolune.history.retrospective

import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/**
 * V17-C-04 §8/§D-3 policy invariant.
 *
 * C-04 captures the canonical current policy once per load and shares that value with Read 1/2;
 * Read 3 keeps its frozen signature and uses the production default. The invariant is
 * value/semantics equality, so this test fails if the frozen default values ever drift.
 */
class RetrospectivePolicyInvariantTest {

    @Test
    fun `the frozen canonical policy values are unchanged`() {
        val frozen = MedicationOccurrencePolicy(
            dueBefore = Duration.ofHours(1),
            dueAfter = Duration.ofHours(1),
            matchBefore = Duration.ofHours(1),
            matchAfter = Duration.ofHours(1),
            doseTolerance = 0.000_001
        )
        assertEquals(frozen, MedicationOccurrencePolicy())
        assertEquals(frozen.hashCode(), MedicationOccurrencePolicy().hashCode())
    }

    @Test
    fun `equality is by value so seam value-sharing is well defined`() {
        assertEquals(MedicationOccurrencePolicy(), MedicationOccurrencePolicy())
        assertEquals(
            MedicationOccurrencePolicy(doseTolerance = 0.000_001),
            MedicationOccurrencePolicy()
        )
    }
}
