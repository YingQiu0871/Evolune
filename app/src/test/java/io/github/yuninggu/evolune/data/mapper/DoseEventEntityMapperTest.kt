package io.github.yuninggu.evolune.data.mapper

import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.time.LegacyTimeError
import io.github.yuninggu.evolune.core.time.NonFiniteKind
import io.github.yuninggu.evolune.data.DoseEventEntity
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class DoseEventEntityMapperTest {
    private val eventId = UUID.fromString("10000000-0000-0000-0000-000000000001")

    @Test
    fun validSyntheticEntityMapsEveryFieldAndLegacyDefault() {
        val event = success(
            entity(
                route = "SUBLINGUAL",
                timeH = 12.5,
                doseMG = 1.25,
                ester = "E2",
                extras = mapOf("SUBLINGUAL_THETA" to 0.42)
            ).toDomainDoseEvent()
        )

        assertEquals(eventId, event.id)
        assertEquals(Route.SUBLINGUAL, event.route)
        assertEquals(Instant.ofEpochMilli(45_000_000L), event.occurredAt)
        assertEquals(1.25, event.doseMG, 0.0)
        assertEquals(Ester.E2, event.ester)
        assertEquals(mapOf(ExtraKey.SUBLINGUAL_THETA to 0.42), event.extras)
        assertNull(event.zoneId)
        assertNull(event.localDate)
        assertNull(event.slotId)
        assertEquals(DoseEventSource.LEGACY, event.source)
        assertEquals(DoseEventStatus.RECORDED, event.status)
        assertEquals(1L, event.revision)
    }

    @Test
    fun nanTimeHReturnsSpecificFailure() {
        assertNonFiniteFailure(Double.NaN, NonFiniteKind.NAN)
    }

    @Test
    fun positiveInfinityTimeHReturnsSpecificFailure() {
        assertNonFiniteFailure(Double.POSITIVE_INFINITY, NonFiniteKind.POSITIVE_INFINITY)
    }

    @Test
    fun negativeInfinityTimeHReturnsSpecificFailure() {
        assertNonFiniteFailure(Double.NEGATIVE_INFINITY, NonFiniteKind.NEGATIVE_INFINITY)
    }

    @Test
    fun positiveTimeHOverflowReturnsFailure() {
        assertOverflowFailure(Double.MAX_VALUE)
    }

    @Test
    fun negativeTimeHOverflowReturnsFailure() {
        assertOverflowFailure(-Double.MAX_VALUE)
    }

    @Test
    fun unknownRouteReturnsExplicitFailure() {
        assertEquals(
            MappingError.InvalidRoute("UNKNOWN_ROUTE"),
            failure(entity(route = "UNKNOWN_ROUTE").toDomainDoseEvent())
        )
    }

    @Test
    fun unknownEsterReturnsExplicitFailure() {
        assertEquals(
            MappingError.InvalidEster("UNKNOWN_ESTER"),
            failure(entity(ester = "UNKNOWN_ESTER").toDomainDoseEvent())
        )
    }

    @Test
    fun unknownExtraKeyReturnsExplicitFailure() {
        assertEquals(
            MappingError.InvalidExtraKey("UNKNOWN_EXTRA"),
            failure(
                entity(extras = mapOf("UNKNOWN_EXTRA" to 1.0)).toDomainDoseEvent()
            )
        )
    }

    @Test
    fun legacyExtraValuesArePreservedWithoutNewValidation() {
        val event = success(
            entity(extras = mapOf("AREA_CM2" to Double.NaN)).toDomainDoseEvent()
        )

        assertTrue(event.extras.getValue(ExtraKey.AREA_CM2).isNaN())
    }

    @Test
    fun defaultLocaleAndTimeZoneDoNotAffectMapping() {
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val first = success(entity(timeH = 123_456.789).toDomainDoseEvent())
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val second = success(entity(timeH = 123_456.789).toDomainDoseEvent())

            assertEquals(first, second)
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun entity(
        route: String = "ORAL",
        timeH: Double = 0.0,
        doseMG: Double = 2.0,
        ester: String = "EV",
        extras: Map<String, Double> = emptyMap()
    ) = DoseEventEntity(
        id = eventId,
        route = route,
        timeH = timeH,
        doseMG = doseMG,
        ester = ester,
        extras = extras
    )

    private fun corruptEntity(timeH: Double) = DoseEventEntity(
        id = eventId,
        route = "ORAL",
        timeH = timeH,
        doseMG = 2.0,
        ester = "EV",
        extras = emptyMap(),
        // v3 normally rejects invalid timeH; explicit persistence fields model a corrupt stored row.
        occurredAtEpochMillis = 0L,
        zoneId = null,
        localDate = null,
        slotId = null,
        source = "LEGACY",
        status = "RECORDED",
        revision = 1L
    )

    private fun success(result: MappingResult<DoseEvent>): DoseEvent {
        assertTrue(result is MappingResult.Success)
        return (result as MappingResult.Success).value
    }

    private fun failure(result: MappingResult<*>): MappingError {
        assertTrue(result is MappingResult.Failure)
        return (result as MappingResult.Failure).error
    }

    private fun assertNonFiniteFailure(value: Double, expectedKind: NonFiniteKind) {
        val error = failure(corruptEntity(timeH = value).toDomainDoseEvent())
        assertTrue(error is MappingError.InvalidTimeH)
        val cause = (error as MappingError.InvalidTimeH).cause
        assertTrue(cause is LegacyTimeError.NonFinite)
        assertEquals(expectedKind, (cause as LegacyTimeError.NonFinite).kind)
    }

    private fun assertOverflowFailure(value: Double) {
        val error = failure(corruptEntity(timeH = value).toDomainDoseEvent())
        assertTrue(error is MappingError.InvalidTimeH)
        assertTrue((error as MappingError.InvalidTimeH).cause is LegacyTimeError.Overflow)
    }
}
