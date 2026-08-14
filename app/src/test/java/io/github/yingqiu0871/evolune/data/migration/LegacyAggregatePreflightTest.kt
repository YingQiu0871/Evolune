package io.github.yingqiu0871.evolune.data.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class LegacyAggregatePreflightTest {
    private val eventId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val planId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun validPersistedEventMatchesProductionAcceptance() {
        LegacyAggregatePreflight.requireReadable(validEvent())
    }

    @Test
    fun validPersistedPlanMatchesProductionAcceptance() {
        LegacyAggregatePreflight.requireReadable(validPlan())
    }

    @Test
    fun malformedConverterPayloadsAreClassifiedBeforeMapping() {
        val eventFailure = captureFailure {
            LegacyAggregatePreflight.requireReadable(validEvent(extrasPayload = "not-json"))
        }
        val planFailure = captureFailure {
            LegacyAggregatePreflight.requireReadable(
                validPlan(daysOfWeekPayload = "not-json")
            )
        }

        assertEquals("extras", eventFailure.field)
        assertEquals(PersistedValueFailure.CONVERTER_REJECTED, eventFailure.reason)
        assertEquals("daysOfWeek", planFailure.field)
        assertEquals(PersistedValueFailure.CONVERTER_REJECTED, planFailure.reason)
    }

    @Test
    fun mapperRejectionsRetainTheirPersistedFieldCategory() {
        val eventFailure = captureFailure {
            LegacyAggregatePreflight.requireReadable(validEvent(route = "UNKNOWN_ROUTE"))
        }
        val planFailure = captureFailure {
            LegacyAggregatePreflight.requireReadable(validPlan(intervalDays = 0))
        }

        assertEquals("route", eventFailure.field)
        assertEquals(PersistedValueFailure.MAPPER_REJECTED, eventFailure.reason)
        assertEquals("intervalDays", planFailure.field)
        assertEquals(PersistedValueFailure.MAPPER_REJECTED, planFailure.reason)
    }

    @Test
    fun migrationExceptionExposesFingerprintWithoutRawIdentifierOrPayload() {
        val rawId = "10000000-0000-0000-0000-000000000001"
        val failure = LegacyMigrationException(
            tableName = "medication_plans",
            rowId = rawId,
            error = LegacyMigrationError.InvalidPersistedValue(
                field = "extras",
                reason = PersistedValueFailure.CONVERTER_REJECTED
            ),
            operation = "complete legacy preflight"
        )

        val fingerprint = requireNotNull(failure.rowFingerprint)
        assertTrue(fingerprint.matches(Regex("[0-9a-f]{16}")))
        assertEquals(
            "Room migration complete legacy preflight failed for medication_plans " +
                "row fingerprint $fingerprint: invalid persisted extras (converter_rejected)",
            failure.message
        )
        assertFalse(failure.message.orEmpty().contains(rawId))
    }

    private fun validEvent(
        route: String = "ORAL",
        extrasPayload: String = "{}"
    ) = LegacyEventValues(
        id = eventId,
        route = route,
        timeH = 12.5,
        doseMG = 2.0,
        ester = "EV",
        extrasPayload = extrasPayload,
        occurredAtEpochMillis = 45_000_000L
    )

    private fun validPlan(
        daysOfWeekPayload: String = "[]",
        intervalDays: Int = 1
    ) = LegacyPlanValues(
        id = planId,
        name = "Synthetic plan",
        route = "ORAL",
        ester = "EV",
        doseMG = 2.0,
        scheduleType = "DAILY",
        timeOfDayPayload = "[]",
        daysOfWeekPayload = daysOfWeekPayload,
        intervalDays = intervalDays,
        isEnabled = true,
        extrasPayload = "{}",
        createdAt = 1_700_000_000_000L,
        slots = emptyList()
    )

    private fun captureFailure(block: () -> Unit): LegacyAggregatePreflightException {
        try {
            block()
            fail("Expected persisted aggregate rejection")
        } catch (failure: LegacyAggregatePreflightException) {
            return failure
        }
        error("unreachable")
    }
}
