package io.github.yingqiu0871.evolune.history.pk

import io.github.yingqiu0871.evolune.core.adapter.DomainDoseEventToPkAdapter
import io.github.yingqiu0871.evolune.core.model.DoseEvent as CoreDoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.presentation.toRecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.HistoricalMedicationExtraKey
import io.github.yingqiu0871.evolune.experience.HistoricalProjection
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchKey
import io.github.yingqiu0871.evolune.experience.RecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.history.AllAvailableHistory
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * V17-C-01 §13.2: extraction, eligibility, route/ester parsing order, extras parity.
 */
class RetrospectivePkExtractorTest {

    private val utc: ZoneOffset = ZoneOffset.UTC
    private val base: Instant = Instant.parse("2025-01-01T00:00:00Z")
    private val window = RetrospectivePkWindow(base.minus(Duration.ofDays(1)), base)

    // ---------- E1: route x ester eligibility matrix ----------

    @Test
    fun `E1 route and ester eligibility matrix matches the frozen contract`() {
        val supported = listOf(
            Ester.EB to Route.INJECTION,
            Ester.EV to Route.INJECTION,
            Ester.EC to Route.INJECTION,
            Ester.EN to Route.INJECTION,
            Ester.E2 to Route.ORAL,
            Ester.EB to Route.ORAL,
            Ester.EV to Route.ORAL,
            Ester.EC to Route.ORAL,
            Ester.EN to Route.ORAL,
            Ester.E2 to Route.SUBLINGUAL,
            Ester.EB to Route.SUBLINGUAL,
            Ester.EV to Route.SUBLINGUAL,
            Ester.EC to Route.SUBLINGUAL,
            Ester.EN to Route.SUBLINGUAL,
            Ester.E2 to Route.GEL,
            Ester.EV to Route.GEL
        )
        supported.forEachIndexed { index, (ester, route) ->
            val event = recordedEvent(UUID(0L, index.toLong() + 1), route, ester, doseMG = 2.0)
            val extraction = RetrospectivePkExtractor.extract(sourceOf(event), window)
            assertTrue(
                "$route x $ester must be a supported engine input",
                extraction.engineEvents.size == 1
            )
            assertTrue("$route x $ester must not be excluded", extraction.exclusions.isEmpty())
            assertTrue(
                "$route x $ester must be concentration-producing",
                extraction.concentrationProducingEventIds == listOf(event.eventId)
            )
        }

        val injectionE2 = recordedEvent(UUID(0L, 101L), Route.INJECTION, Ester.E2, doseMG = 5.0)
        val injectionE2Extraction = RetrospectivePkExtractor.extract(sourceOf(injectionE2), window)
        assertEquals(emptyList<Any>(), injectionE2Extraction.engineEvents)
        assertEquals(
            RetrospectivePkExclusionReason.UNSUPPORTED_CURRENT_MODEL_COMBINATION,
            injectionE2Extraction.exclusions.single().reason
        )

        val antiAndrogen = recordedEvent(UUID(0L, 102L), Route.ANTIANDROGEN, Ester.E2, doseMG = 25.0)
        val antiAndrogenExtraction = RetrospectivePkExtractor.extract(sourceOf(antiAndrogen), window)
        assertEquals(emptyList<Any>(), antiAndrogenExtraction.engineEvents)
        assertEquals(
            RetrospectivePkExclusionReason.ANTIANDROGEN_IDENTITY_UNAVAILABLE,
            antiAndrogenExtraction.exclusions.single().reason
        )
    }

    // ---------- E2: INJECTION x E2 numeric equivalence ----------

    @Test
    fun `E2 excluding INJECTION x E2 changes no engine input`() {
        val supported = recordedEvent(UUID(0L, 201L), Route.INJECTION, Ester.EV, doseMG = 5.0)
        val unsupported = recordedEvent(UUID(0L, 202L), Route.INJECTION, Ester.E2, doseMG = 5.0)

        val without = RetrospectivePkExtractor.extract(sourceOf(supported), window)
        val with = RetrospectivePkExtractor.extract(sourceOf(supported, unsupported), window)

        assertEquals(without.engineInputEventIds, with.engineInputEventIds)
        assertEquals(without.engineEvents, with.engineEvents)
    }

    // ---------- E3: parse order and parse-failure classification ----------

    @Test
    fun `E3 ANTIANDROGEN short-circuits before ester parsing`() {
        val event = RecordedMedicationEvent(
            eventId = UUID(0L, 301L),
            occurredAt = base.minus(Duration.ofHours(1)),
            slotId = null,
            matchKey = MedicationMatchKey(Route.ANTIANDROGEN.name, "BOGUS-ESTER", 25.0),
            source = MedicationIntakeSource.MANUAL
        )
        val extraction = RetrospectivePkExtractor.extract(sourceOf(event), window)

        assertEquals(
            RetrospectivePkExclusionReason.ANTIANDROGEN_IDENTITY_UNAVAILABLE,
            extraction.exclusions.single().reason
        )
        assertTrue(extraction.engineEvents.isEmpty())
    }

    @Test
    fun `E3 unmapped ester on an E2 PK route is a contract violation`() {
        val event = RecordedMedicationEvent(
            eventId = UUID(0L, 302L),
            occurredAt = base.minus(Duration.ofHours(1)),
            slotId = null,
            matchKey = MedicationMatchKey(Route.ORAL.name, "BOGUS-ESTER", 2.0),
            source = MedicationIntakeSource.MANUAL
        )
        assertContractViolation { RetrospectivePkExtractor.extract(sourceOf(event), window) }
    }

    @Test
    fun `E3 unmapped route is a contract violation`() {
        val event = RecordedMedicationEvent(
            eventId = UUID(0L, 303L),
            occurredAt = base.minus(Duration.ofHours(1)),
            slotId = null,
            matchKey = MedicationMatchKey("BOGUS-ROUTE", Ester.E2.name, 2.0),
            source = MedicationIntakeSource.MANUAL
        )
        assertContractViolation { RetrospectivePkExtractor.extract(sourceOf(event), window) }
    }

    // ---------- E4: six-key extras parity through the derived layer ----------

    @Test
    fun `E4 six-key extras pass through core, historical and pk mapping identically`() {
        val extras = mapOf(
            ExtraKey.CONCENTRATION_MG_ML to 20.0,
            ExtraKey.AREA_CM2 to 4.0,
            ExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0,
            ExtraKey.SUBLINGUAL_THETA to 0.4,
            ExtraKey.SUBLINGUAL_TIER to 2.0,
            ExtraKey.ANTI_ANDROGEN_TYPE to 1.0
        )
        val coreEvent = CoreDoseEvent(
            id = UUID(0L, 401L),
            route = Route.GEL,
            occurredAt = base.minus(Duration.ofHours(1)),
            doseMG = 1.5,
            ester = Ester.E2,
            extras = extras,
            source = DoseEventSource.MANUAL
        )
        val recorded = requireNotNull(coreEvent.toRecordedMedicationEvent())
        assertEquals(6, recorded.extras.size)
        assertEquals(
            HistoricalMedicationExtraKey.RELEASE_RATE_UG_PER_DAY,
            HistoricalMedicationExtraKey.valueOf("RELEASE_RATE_UG_PER_DAY")
        )

        val extraction = RetrospectivePkExtractor.extract(sourceOf(recorded), window)
        val expectedPkExtras = DomainDoseEventToPkAdapter.adapt(coreEvent).extras
        assertEquals(expectedPkExtras, extraction.engineEvents.single().extras)
    }

    // ---------- E5a: non-finite model-driving values never reach the engine ----------

    @Test
    fun `E5a non-finite model-driving extras are excluded before the engine`() {
        val nanRate = recordedEvent(
            UUID(0L, 501L), Route.PATCH_APPLY, Ester.E2, doseMG = 0.0,
            extras = mapOf(HistoricalMedicationExtraKey.RELEASE_RATE_UG_PER_DAY to Double.NaN)
        )
        val infiniteTheta = recordedEvent(
            UUID(0L, 502L), Route.SUBLINGUAL, Ester.E2, doseMG = 2.0,
            extras = mapOf(HistoricalMedicationExtraKey.SUBLINGUAL_THETA to Double.POSITIVE_INFINITY)
        )
        val infiniteTier = recordedEvent(
            UUID(0L, 503L), Route.ORAL, Ester.E2, doseMG = 2.0,
            extras = mapOf(HistoricalMedicationExtraKey.SUBLINGUAL_TIER to Double.NEGATIVE_INFINITY)
        )

        val extraction = RetrospectivePkExtractor.extract(
            sourceOf(nanRate, infiniteTheta, infiniteTier),
            window
        )

        assertTrue(extraction.engineEvents.isEmpty())
        assertEquals(3, extraction.exclusions.size)
        assertTrue(
            extraction.exclusions.all {
                it.reason == RetrospectivePkExclusionReason.UNSUPPORTED_OR_INCOMPLETE_EVENT
            }
        )
    }

    // ---------- E5b / W8 / W9: present-and-non-positive release rate is retained, NOT producing ----------

    @Test
    fun `E5b W8 finite zero release rate with positive dose is retained but not producing`() {
        val zeroRate = recordedEvent(
            UUID(0L, 601L), Route.PATCH_APPLY, Ester.E2, doseMG = 2.0,
            extras = mapOf(HistoricalMedicationExtraKey.RELEASE_RATE_UG_PER_DAY to 0.0)
        )
        val extraction = RetrospectivePkExtractor.extract(sourceOf(zeroRate), window)

        assertTrue(extraction.exclusions.isEmpty())
        assertEquals(listOf(zeroRate.eventId), extraction.engineInputEventIds)
        assertTrue(extraction.concentrationProducingEventIds.isEmpty())
        assertEquals(
            0.0,
            extraction.engineEvents.single()
                .extras[io.github.yingqiu0871.evolune.pk.DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY]!!,
            0.0
        )
        assertEquals(0.0, runEngine(extraction.engineEvents).auc, 0.0)
    }

    @Test
    fun `W9 finite negative release rate with positive dose is retained but not producing`() {
        val negativeRate = recordedEvent(
            UUID(0L, 603L), Route.PATCH_APPLY, Ester.E2, doseMG = 2.0,
            extras = mapOf(HistoricalMedicationExtraKey.RELEASE_RATE_UG_PER_DAY to -5.0)
        )
        val extraction = RetrospectivePkExtractor.extract(sourceOf(negativeRate), window)

        assertTrue(extraction.exclusions.isEmpty())
        assertEquals(listOf(negativeRate.eventId), extraction.engineInputEventIds)
        assertTrue(extraction.concentrationProducingEventIds.isEmpty())
        assertEquals(0.0, runEngine(extraction.engineEvents).auc, 0.0)
    }

    // ---------- W6 / W7: PATCH producing branches ----------

    @Test
    fun `W6 patch first order with positive dose is producing`() {
        val firstOrder = recordedEvent(
            UUID(0L, 604L), Route.PATCH_APPLY, Ester.E2, doseMG = 2.0
        )
        val extraction = RetrospectivePkExtractor.extract(sourceOf(firstOrder), window)

        assertTrue(extraction.exclusions.isEmpty())
        assertEquals(listOf(firstOrder.eventId), extraction.concentrationProducingEventIds)
        assertTrue(runEngine(extraction.engineEvents).auc > 0.0)
    }

    @Test
    fun `W7 zero order patch with positive rate and zero dose is producing`() {
        val zeroOrder = recordedEvent(
            UUID(0L, 605L), Route.PATCH_APPLY, Ester.E2, doseMG = 0.0,
            extras = mapOf(HistoricalMedicationExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0)
        )
        val extraction = RetrospectivePkExtractor.extract(sourceOf(zeroOrder), window)

        assertTrue(extraction.exclusions.isEmpty())
        assertEquals(listOf(zeroOrder.eventId), extraction.concentrationProducingEventIds)
        assertTrue(runEngine(extraction.engineEvents).auc > 0.0)
    }

    // ---------- E5c: finite zero dose is retained, not producing, not excluded ----------

    @Test
    fun `E5c finite zero dose is retained with zero contribution and no exclusion`() {
        val zeroDoseRoutes = listOf(
            recordedEvent(UUID(0L, 701L), Route.ORAL, Ester.E2, doseMG = 0.0),
            recordedEvent(UUID(0L, 702L), Route.GEL, Ester.E2, doseMG = 0.0),
            recordedEvent(UUID(0L, 703L), Route.INJECTION, Ester.EV, doseMG = 0.0),
            recordedEvent(UUID(0L, 704L), Route.SUBLINGUAL, Ester.E2, doseMG = 0.0),
            recordedEvent(UUID(0L, 705L), Route.PATCH_APPLY, Ester.E2, doseMG = 0.0)
        )

        val extraction = RetrospectivePkExtractor.extract(sourceOf(*zeroDoseRoutes.toTypedArray()), window)

        assertEquals(zeroDoseRoutes.map { it.eventId }, extraction.engineInputEventIds)
        assertTrue(extraction.exclusions.isEmpty())
        assertTrue(extraction.concentrationProducingEventIds.isEmpty())
    }

    @Test
    fun `E5c negative dose cannot be constructed through the derived match key`() {
        try {
            MedicationMatchKey(Route.ORAL.name, Ester.E2.name, -1.0)
            throw AssertionError("derived match key must reject negative dose amounts")
        } catch (expected: IllegalArgumentException) {
            assertTrue(requireNotNull(expected.message).isNotBlank())
        }
    }

    // ---------- E5d: zero-order patch with zero dose is producing ----------

    @Test
    fun `E5d zero-order patch with zero dose and positive rate is producing`() {
        val event = recordedEvent(
            UUID(0L, 801L), Route.PATCH_APPLY, Ester.E2, doseMG = 0.0,
            extras = mapOf(HistoricalMedicationExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0)
        )
        val extraction = RetrospectivePkExtractor.extract(sourceOf(event), window)

        assertEquals(listOf(event.eventId), extraction.engineInputEventIds)
        assertEquals(listOf(event.eventId), extraction.concentrationProducingEventIds)
        assertTrue(extraction.exclusions.isEmpty())
        assertTrue(runEngine(extraction.engineEvents).concPGmL.any { it > 0.0 })
    }

    // ---------- E9a / E9b: sublingual tier precision ----------

    @Test
    fun `E9a finite unknown tier codes fall back to STANDARD`() {
        fun curveForTier(tier: Double) = runEngine(
            RetrospectivePkExtractor.extract(
                sourceOf(
                    recordedEvent(
                        UUID(0L, 901L), Route.SUBLINGUAL, Ester.E2, doseMG = 2.0,
                        extras = mapOf(HistoricalMedicationExtraKey.SUBLINGUAL_TIER to tier)
                    )
                ),
                window
            ).engineEvents
        )

        val standard = curveForTier(2.0)
        assertEquals(standard.concPGmL, curveForTier(7.0).concPGmL)
        // Non-integral finite codes truncate through the existing code.toInt() semantics:
        // 1.5 -> CASUAL, which must match tier 1 and differ from STANDARD.
        assertEquals(curveForTier(1.0).concPGmL, curveForTier(1.5).concPGmL)
        assertTrue("tier QUICK must differ from STANDARD", standard.concPGmL != curveForTier(0.0).concPGmL)
    }

    @Test
    fun `E9b non-finite tier is excluded`() {
        val event = recordedEvent(
            UUID(0L, 902L), Route.SUBLINGUAL, Ester.E2, doseMG = 2.0,
            extras = mapOf(HistoricalMedicationExtraKey.SUBLINGUAL_TIER to Double.NaN)
        )
        val extraction = RetrospectivePkExtractor.extract(sourceOf(event), window)
        assertTrue(extraction.engineEvents.isEmpty())
        assertEquals(
            RetrospectivePkExclusionReason.UNSUPPORTED_OR_INCOMPLETE_EVENT,
            extraction.exclusions.single().reason
        )
    }

    // ---------- E6: unrepresentable instants ----------

    @Test
    fun `E6 unrepresentable instant is an exclusion for non-patch and unusable for patch`() {
        val hugeWindow = RetrospectivePkWindow(
            base,
            Instant.ofEpochSecond(Instant.MAX.epochSecond)
        )
        val hugeInstant = Instant.ofEpochSecond(Instant.MAX.epochSecond)

        val oral = recordedEvent(UUID(0L, 1001L), Route.ORAL, Ester.E2, doseMG = 2.0, at = hugeInstant)
        val oralExtraction = RetrospectivePkExtractor.extract(
            sourceOf(oral, bound = hugeWindow.endInclusive, displayDateOverride = base.atZone(utc).toLocalDate()),
            hugeWindow
        )
        assertTrue(oralExtraction.engineEvents.isEmpty())
        assertEquals(
            RetrospectivePkExclusionReason.UNSUPPORTED_OR_INCOMPLETE_EVENT,
            oralExtraction.exclusions.single().reason
        )

        val patch = recordedEvent(UUID(0L, 1002L), Route.PATCH_APPLY, Ester.E2, doseMG = 2.0, at = hugeInstant)
        val patchExtraction = RetrospectivePkExtractor.extract(
            sourceOf(patch, bound = hugeWindow.endInclusive, displayDateOverride = base.atZone(utc).toLocalDate()),
            hugeWindow
        )
        assertTrue(patchExtraction.engineEvents.isEmpty())
        assertEquals(
            RetrospectivePkExclusionReason.UNSUPPORTED_OR_INCOMPLETE_EVENT,
            patchExtraction.exclusions.single().reason
        )
    }

    // ---------- E7: uniqueness and upper-bound contract ----------

    @Test
    fun `E7 duplicate event ids are a contract violation`() {
        val event = recordedEvent(UUID(0L, 1101L), Route.ORAL, Ester.E2, doseMG = 2.0)
        val duplicate = sourceOf(event, event)
        assertContractViolation { RetrospectivePkExtractor.extract(duplicate, window) }
    }

    @Test
    fun `E7 entries after the consumed upper bound are a contract violation`() {
        val afterBound = recordedEvent(UUID(0L, 1102L), Route.ORAL, Ester.E2, doseMG = 2.0, at = base.plus(Duration.ofHours(1)))
        assertContractViolation { RetrospectivePkExtractor.extract(sourceOf(afterBound), window) }
    }

    // ---------- E8 / V5: deterministic ordering and pk id identity ----------

    @Test
    fun `E8 engine input is ordered by instant then id and keeps authoritative ids`() {
        val late = recordedEvent(UUID(0L, 1201L), Route.ORAL, Ester.E2, doseMG = 2.0, at = base.minus(Duration.ofHours(1)))
        val earlyB = recordedEvent(UUID(0L, 1203L), Route.ORAL, Ester.E2, doseMG = 3.0, at = base.minus(Duration.ofHours(5)))
        val earlyA = recordedEvent(UUID(0L, 1202L), Route.ORAL, Ester.E2, doseMG = 4.0, at = base.minus(Duration.ofHours(5)))

        // Hand-built projection order is deliberately reversed.
        val extraction = RetrospectivePkExtractor.extract(sourceOf(late, earlyB, earlyA), window)

        assertEquals(
            listOf(earlyA.eventId, earlyB.eventId, late.eventId),
            extraction.engineInputEventIds
        )
        assertEquals(extraction.engineInputEventIds, extraction.engineEvents.map { it.id })
    }

    // ---------- helpers ----------

    private fun assertContractViolation(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected RetrospectivePkContractViolationException")
        } catch (expected: RetrospectivePkContractViolationException) {
            assertTrue(requireNotNull(expected.message).isNotBlank())
        }
    }

    private fun runEngine(events: List<io.github.yingqiu0871.evolune.pk.DoseEvent>): io.github.yingqiu0871.evolune.pk.SimulationResult {
        val times = events.map { it.timeH }
        val start = (times.minOrNull() ?: 0.0) - 24.0
        val end = (times.maxOrNull() ?: 24.0) + 24.0 * 14
        return SimulationEngine(
            events = events,
            bodyWeightKG = 60.0,
            startTimeH = start,
            endTimeH = end,
            numberOfSteps = 1000
        ).run()
    }

    private fun recordedEvent(
        id: UUID,
        route: Route,
        ester: Ester,
        doseMG: Double,
        at: Instant = base.minus(Duration.ofHours(1)),
        extras: Map<HistoricalMedicationExtraKey, Double> = emptyMap()
    ): RecordedMedicationEvent = RecordedMedicationEvent(
        eventId = id,
        occurredAt = at,
        slotId = null,
        matchKey = MedicationMatchKey(route.name, ester.name, doseMG),
        source = MedicationIntakeSource.MANUAL,
        extras = extras
    )

    private fun sourceOf(
        vararg events: RecordedMedicationEvent,
        bound: Instant = base,
        displayDateOverride: java.time.LocalDate? = null
    ): AllAvailableHistory = AllAvailableHistory(
        upperBoundInclusive = bound,
        lookbackStart = events.minOfOrNull { it.occurredAt },
        projection = HistoricalProjection(
            entries = events.map { event ->
                UnmatchedHistoricalIntake(
                    event = event,
                    source = event.source,
                    displayDate = displayDateOverride
                        ?: event.localDate
                        ?: event.occurredAt.atZone(utc).toLocalDate(),
                    displayDateProvenance =
                        HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE
                )
            }
        )
    )
}
