package io.github.yingqiu0871.evolune.core.presentation

import io.github.yingqiu0871.evolune.core.adapter.DomainDoseEventToPkAdapter
import io.github.yingqiu0871.evolune.core.adapter.toPkExtraKey
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.experience.HistoricalMedicationExtraKey
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchKey
import io.github.yingqiu0871.evolune.experience.RecordedMedicationEvent
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * V17-C-01 §13.6 (extras): the six approved extras traverse the derived layer exhaustively and the
 * data-class equality change caused by the new field is pinned explicitly (R4.2 P3 absorption).
 */
class HistoricalMedicationExtraMappingTest {

    private val occurredAt: Instant = Instant.parse("2025-06-01T08:00:00Z")

    @Test
    fun `core extras map exhaustively into the derived layer`() {
        val core = coreEvent(
            extras = mapOf(
                ExtraKey.CONCENTRATION_MG_ML to 20.0,
                ExtraKey.AREA_CM2 to 4.0,
                ExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0,
                ExtraKey.SUBLINGUAL_THETA to 0.4,
                ExtraKey.SUBLINGUAL_TIER to 2.0,
                ExtraKey.ANTI_ANDROGEN_TYPE to 1.0
            )
        )
        val recorded = requireNotNull(core.toRecordedMedicationEvent())

        assertEquals(
            mapOf(
                HistoricalMedicationExtraKey.CONCENTRATION_MG_ML to 20.0,
                HistoricalMedicationExtraKey.AREA_CM2 to 4.0,
                HistoricalMedicationExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0,
                HistoricalMedicationExtraKey.SUBLINGUAL_THETA to 0.4,
                HistoricalMedicationExtraKey.SUBLINGUAL_TIER to 2.0,
                HistoricalMedicationExtraKey.ANTI_ANDROGEN_TYPE to 1.0
            ),
            recorded.extras
        )
    }

    @Test
    fun `derived extras map into the pk layer exactly like the direct adapter`() {
        val core = coreEvent(
            extras = mapOf(
                ExtraKey.CONCENTRATION_MG_ML to 20.0,
                ExtraKey.AREA_CM2 to 4.0,
                ExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0,
                ExtraKey.SUBLINGUAL_THETA to 0.4,
                ExtraKey.SUBLINGUAL_TIER to 2.0,
                ExtraKey.ANTI_ANDROGEN_TYPE to 1.0
            )
        )
        val recorded = requireNotNull(core.toRecordedMedicationEvent())

        val viaDerivedLayer = recorded.extras.mapKeys { (key, _) -> key.toPkExtraKey() }
        val viaDirectAdapter = DomainDoseEventToPkAdapter.adapt(core).extras

        assertEquals(viaDirectAdapter, viaDerivedLayer)
    }

    @Test
    fun `missing keys stay absent and never rely on the default map`() {
        val core = coreEvent(extras = mapOf(ExtraKey.SUBLINGUAL_THETA to 0.25))
        val recorded = requireNotNull(core.toRecordedMedicationEvent())

        assertEquals(mapOf(HistoricalMedicationExtraKey.SUBLINGUAL_THETA to 0.25), recorded.extras)
    }

    @Test
    fun `adding extras changes data-class equality and hashCode`() {
        val base = RecordedMedicationEvent(
            eventId = UUID(0L, 501L),
            occurredAt = occurredAt,
            slotId = null,
            matchKey = MedicationMatchKey(Route.ORAL.name, Ester.E2.name, 2.0),
            source = MedicationIntakeSource.MANUAL
        )
        val withExtras = base.copy(
            extras = mapOf(HistoricalMedicationExtraKey.SUBLINGUAL_THETA to 0.4)
        )

        assertNotEquals(base, withExtras)
        assertNotEquals(base.hashCode(), withExtras.hashCode())
        assertEquals(
            withExtras,
            base.copy(extras = mapOf(HistoricalMedicationExtraKey.SUBLINGUAL_THETA to 0.4))
        )
    }

    private fun coreEvent(extras: Map<ExtraKey, Double>): DoseEvent = DoseEvent(
        id = UUID(0L, 601L),
        route = Route.SUBLINGUAL,
        occurredAt = occurredAt,
        doseMG = 2.0,
        ester = Ester.E2,
        extras = extras,
        source = DoseEventSource.MANUAL
    )
}
