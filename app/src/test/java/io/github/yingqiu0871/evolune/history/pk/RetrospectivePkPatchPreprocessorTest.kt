package io.github.yingqiu0871.evolune.history.pk

import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * V17-C-01 §13.3: frozen patch grammar, affected-chain scope and unusable transitions.
 */
class RetrospectivePkPatchPreprocessorTest {

    private val base: Instant = Instant.parse("2025-01-01T00:00:00Z")
    private val apply = Route.PATCH_APPLY
    private val remove = Route.PATCH_REMOVE

    @Test
    fun `P1 single apply stays open`() {
        val a = UUID(0L, 1L)
        val result = preprocess(t(a, base, apply))
        assertEquals(setOf(a), result.keptEventIds)
        assertTrue(result.exclusions.isEmpty())
    }

    @Test
    fun `P1 apply remove pair is kept`() {
        val a = UUID(0L, 2L)
        val r = UUID(0L, 3L)
        val result = preprocess(
            t(a, base, apply),
            t(r, base.plus(Duration.ofDays(3)), remove)
        )
        assertEquals(setOf(a, r), result.keptEventIds)
        assertTrue(result.exclusions.isEmpty())
    }

    @Test
    fun `P1 apply remove apply is kept - final apply may remain open`() {
        val a1 = UUID(0L, 4L)
        val r1 = UUID(0L, 5L)
        val a2 = UUID(0L, 6L)
        val result = preprocess(
            t(a1, base, apply),
            t(r1, base.plus(Duration.ofDays(3)), remove),
            t(a2, base.plus(Duration.ofDays(7)), apply)
        )
        assertEquals(setOf(a1, r1, a2), result.keptEventIds)
        assertTrue(result.exclusions.isEmpty())
    }

    @Test
    fun `P1 orphan remove is ambiguous`() {
        val r = UUID(0L, 7L)
        val result = preprocess(t(r, base, remove))
        assertTrue(result.keptEventIds.isEmpty())
        assertEquals(
            RetrospectivePkExclusionReason.AMBIGUOUS_PATCH_PAIRING,
            result.exclusions.single().reason
        )
    }

    @Test
    fun `P1 duplicate remove only excludes the affected orphan removal`() {
        val a = UUID(0L, 8L)
        val r1 = UUID(0L, 9L)
        val r2 = UUID(0L, 10L)
        val result = preprocess(
            t(a, base, apply),
            t(r1, base.plus(Duration.ofDays(3)), remove),
            t(r2, base.plus(Duration.ofDays(4)), remove)
        )
        assertEquals(setOf(a, r1), result.keptEventIds)
        assertEquals(setOf(r2), result.exclusions.map { it.eventId }.toSet())
    }

    @Test
    fun `P1 same-instant apply and remove are both ambiguous`() {
        val a = UUID(0L, 11L)
        val r = UUID(0L, 12L)
        val result = preprocess(
            t(a, base, apply),
            t(r, base, remove)
        )
        assertTrue(result.keptEventIds.isEmpty())
        assertEquals(setOf(a, r), result.exclusions.map { it.eventId }.toSet())
        assertTrue(result.exclusions.all { it.reason == RetrospectivePkExclusionReason.AMBIGUOUS_PATCH_PAIRING })
    }

    @Test
    fun `P1 two applies at the same instant are ambiguous`() {
        val a1 = UUID(0L, 13L)
        val a2 = UUID(0L, 14L)
        val result = preprocess(
            t(a1, base, apply),
            t(a2, base, apply)
        )
        assertTrue(result.keptEventIds.isEmpty())
        assertEquals(setOf(a1, a2), result.exclusions.map { it.eventId }.toSet())
    }

    @Test
    fun `P1 overlapping applies are ambiguous`() {
        val a1 = UUID(0L, 15L)
        val a2 = UUID(0L, 16L)
        val r = UUID(0L, 17L)
        val result = preprocess(
            t(a1, base, apply),
            t(a2, base.plus(Duration.ofDays(1)), apply),
            t(r, base.plus(Duration.ofDays(3)), remove)
        )
        assertTrue(result.keptEventIds.isEmpty())
        assertEquals(setOf(a1, a2, r), result.exclusions.map { it.eventId }.toSet())
    }

    @Test
    fun `P1 affected chain is scoped - unrelated earlier and later chains survive`() {
        val a1 = UUID(0L, 18L)
        val r1 = UUID(0L, 19L)
        val a2 = UUID(0L, 20L)
        val a3 = UUID(0L, 21L)
        val r2 = UUID(0L, 22L)
        val r3 = UUID(0L, 28L)
        val a4 = UUID(0L, 23L)
        val r4 = UUID(0L, 24L)

        val result = preprocess(
            t(a1, base, apply),
            t(r1, base.plus(Duration.ofDays(2)), remove),
            t(a2, base.plus(Duration.ofDays(10)), apply),
            t(a3, base.plus(Duration.ofDays(11)), apply),
            t(r2, base.plus(Duration.ofDays(13)), remove),
            t(r3, base.plus(Duration.ofDays(14)), remove),
            t(a4, base.plus(Duration.ofDays(20)), apply),
            t(r4, base.plus(Duration.ofDays(22)), remove)
        )

        assertEquals(setOf(a1, r1, a4, r4), result.keptEventIds)
        assertEquals(setOf(a2, a3, r2, r3), result.exclusions.map { it.eventId }.toSet())
    }

    @Test
    fun `P1 unsettled chain stays one affected chain`() {
        val a1 = UUID(0L, 25L)
        val a2 = UUID(0L, 26L)
        val r1 = UUID(0L, 27L)
        val a3 = UUID(0L, 28L)
        val r2 = UUID(0L, 29L)
        val result = preprocess(
            t(a1, base, apply),
            t(a2, base.plus(Duration.ofDays(1)), apply),
            t(r1, base.plus(Duration.ofDays(2)), remove),
            t(a3, base.plus(Duration.ofDays(3)), apply),
            t(r2, base.plus(Duration.ofDays(4)), remove)
        )
        assertTrue(result.keptEventIds.isEmpty())
        assertEquals(setOf(a1, a2, r1, a3, r2), result.exclusions.map { it.eventId }.toSet())
    }

    @Test
    fun `P4 removal and next apply at the same instant entangle the prior chain`() {
        val a1 = UUID(0L, 36L)
        val r1 = UUID(0L, 37L)
        val a2 = UUID(0L, 38L)
        val shared = base.plus(Duration.ofDays(2))
        val result = preprocess(
            t(a1, base, apply),
            t(r1, shared, remove),
            t(a2, shared, apply)
        )
        assertTrue(result.keptEventIds.isEmpty())
        assertEquals(setOf(a1, r1, a2), result.exclusions.map { it.eventId }.toSet())
    }

    @Test
    fun `P4 same-instant ambiguity does not depend on event id ordering`() {
        val earlierId = UUID(0L, 1L)
        val laterId = UUID(9L, 9L)
        val forward = preprocess(
            t(earlierId, base, apply),
            t(laterId, base, remove)
        )
        val reversed = preprocess(
            t(laterId, base, apply),
            t(earlierId, base, remove)
        )
        assertEquals(forward.keptEventIds, reversed.keptEventIds)
        assertEquals(
            forward.exclusions.map { it.eventId }.toSet(),
            reversed.exclusions.map { it.eventId }.toSet()
        )
    }

    @Test
    fun `P7 unusable remove cannot turn a preceding apply into a valid open patch`() {
        val a = UUID(0L, 30L)
        val r = UUID(0L, 31L)
        val result = preprocess(
            t(a, base, apply),
            t(r, base.plus(Duration.ofDays(2)), remove, usable = false)
        )
        assertTrue(result.keptEventIds.isEmpty())
        val byId = result.exclusions.associateBy { it.eventId }
        assertEquals(
            RetrospectivePkExclusionReason.AMBIGUOUS_PATCH_PAIRING,
            byId.getValue(a).reason
        )
        assertEquals(
            RetrospectivePkExclusionReason.UNSUPPORTED_OR_INCOMPLETE_EVENT,
            byId.getValue(r).reason
        )
    }

    @Test
    fun `P7 unusable apply alone is excluded as incomplete`() {
        val a = UUID(0L, 32L)
        val result = preprocess(t(a, base, apply, usable = false))
        assertTrue(result.keptEventIds.isEmpty())
        assertEquals(
            RetrospectivePkExclusionReason.UNSUPPORTED_OR_INCOMPLETE_EVENT,
            result.exclusions.single().reason
        )
    }

    @Test
    fun `P7 unusable orphan remove only affects its own chain`() {
        val r1 = UUID(0L, 33L)
        val a = UUID(0L, 34L)
        val r2 = UUID(0L, 35L)
        val result = preprocess(
            t(r1, base, remove, usable = false),
            t(a, base.plus(Duration.ofDays(1)), apply),
            t(r2, base.plus(Duration.ofDays(3)), remove)
        )
        assertEquals(setOf(a, r2), result.keptEventIds)
        assertEquals(setOf(r1), result.exclusions.map { it.eventId }.toSet())
        assertEquals(
            RetrospectivePkExclusionReason.UNSUPPORTED_OR_INCOMPLETE_EVENT,
            result.exclusions.single().reason
        )
    }

    private fun preprocess(
        vararg transitions: RetrospectivePkPatchTransition
    ): RetrospectivePkPatchPreprocessResult =
        RetrospectivePkPatchPreprocessor.preprocess(transitions.toList())

    private fun t(
        id: UUID,
        at: Instant,
        route: Route,
        usable: Boolean = true
    ): RetrospectivePkPatchTransition = RetrospectivePkPatchTransition(
        eventId = id,
        occurredAt = at,
        route = route,
        usable = usable
    )
}
