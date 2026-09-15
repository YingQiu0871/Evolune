package io.github.yingqiu0871.evolune.core.adapter

import io.github.yingqiu0871.evolune.core.model.DoseEvent as DomainDoseEvent
import io.github.yingqiu0871.evolune.core.model.ExtraKey as DomainExtraKey
import io.github.yingqiu0871.evolune.core.time.LegacyTimeAdapter
import io.github.yingqiu0871.evolune.core.time.LegacyTimeResult
import io.github.yingqiu0871.evolune.experience.HistoricalMedicationExtraKey
import io.github.yingqiu0871.evolune.pk.DoseEvent as PkDoseEvent

object DomainDoseEventToPkAdapter {
    fun adapt(event: DomainDoseEvent): PkDoseEvent {
        val timeH = when (val result = LegacyTimeAdapter.instantToTimeH(event.occurredAt)) {
            is LegacyTimeResult.Success -> result.value
            is LegacyTimeResult.Failure -> throw IllegalArgumentException(
                "Dose event time cannot be represented by the PK compatibility model"
            )
        }
        return PkDoseEvent(
            id = event.id,
            route = event.route,
            timeH = timeH,
            doseMG = event.doseMG,
            ester = event.ester,
            extras = event.extras.mapKeys { (key, _) -> key.toPkExtraKey() }
        )
    }

    fun adapt(events: List<DomainDoseEvent>): List<PkDoseEvent> =
        events.map { event -> adapt(event) }
}

fun DomainExtraKey.toPkExtraKey(): PkDoseEvent.ExtraKey = when (this) {
    DomainExtraKey.CONCENTRATION_MG_ML -> PkDoseEvent.ExtraKey.CONCENTRATION_MG_ML
    DomainExtraKey.AREA_CM2 -> PkDoseEvent.ExtraKey.AREA_CM2
    DomainExtraKey.RELEASE_RATE_UG_PER_DAY -> PkDoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY
    DomainExtraKey.SUBLINGUAL_THETA -> PkDoseEvent.ExtraKey.SUBLINGUAL_THETA
    DomainExtraKey.SUBLINGUAL_TIER -> PkDoseEvent.ExtraKey.SUBLINGUAL_TIER
    DomainExtraKey.ANTI_ANDROGEN_TYPE -> PkDoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE
}

/**
 * Exhaustive derived-layer to pk compatibility mapping (V17-C-01 §7.1). No `else`
 * branch: a future key cannot be dropped silently.
 */
fun HistoricalMedicationExtraKey.toPkExtraKey(): PkDoseEvent.ExtraKey = when (this) {
    HistoricalMedicationExtraKey.CONCENTRATION_MG_ML -> PkDoseEvent.ExtraKey.CONCENTRATION_MG_ML
    HistoricalMedicationExtraKey.AREA_CM2 -> PkDoseEvent.ExtraKey.AREA_CM2
    HistoricalMedicationExtraKey.RELEASE_RATE_UG_PER_DAY ->
        PkDoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY
    HistoricalMedicationExtraKey.SUBLINGUAL_THETA -> PkDoseEvent.ExtraKey.SUBLINGUAL_THETA
    HistoricalMedicationExtraKey.SUBLINGUAL_TIER -> PkDoseEvent.ExtraKey.SUBLINGUAL_TIER
    HistoricalMedicationExtraKey.ANTI_ANDROGEN_TYPE -> PkDoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE
}
