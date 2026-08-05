package io.github.yuninggu.evolune.application

import io.github.yuninggu.evolune.core.dataapi.DoseEventRepository
import io.github.yuninggu.evolune.core.dataapi.InsertResult
import io.github.yuninggu.evolune.core.model.DoseEvent as DomainDoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.model.ExtraKey as DomainExtraKey
import io.github.yuninggu.evolune.core.time.LegacyTimeAdapter
import io.github.yuninggu.evolune.core.time.LegacyTimeResult
import io.github.yuninggu.evolune.pk.DoseEvent as PkDoseEvent
import io.github.yuninggu.evolune.utils.MahiroJsonFormat

data class Batch6MahiroImportOutcome(
    val weight: Double?,
    val insertedCount: Int,
    val idempotentCount: Int,
    val conflictCount: Int,
    val invalidCount: Int
) {
    val acceptedCount: Int = insertedCount + idempotentCount
}

internal class Batch6MahiroJsonBridge(
    private val repository: DoseEventRepository
) {
    suspend fun import(jsonContent: String): Batch6MahiroImportOutcome {
        val parsed = MahiroJsonFormat.parseImport(jsonContent)
        var insertedCount = 0
        var idempotentCount = 0
        var conflictCount = 0
        var invalidCount = 0

        parsed.events.forEach { legacyEvent ->
            val event = legacyEvent.toJsonV1DomainEvent()
            if (event == null) {
                invalidCount += 1
            } else {
                when (repository.insert(event)) {
                    InsertResult.Inserted -> insertedCount += 1
                    InsertResult.Idempotent -> idempotentCount += 1
                    InsertResult.Conflict -> conflictCount += 1
                    InsertResult.Invalid -> invalidCount += 1
                }
            }
        }

        return Batch6MahiroImportOutcome(
            weight = parsed.weight,
            insertedCount = insertedCount,
            idempotentCount = idempotentCount,
            conflictCount = conflictCount,
            invalidCount = invalidCount
        )
    }

    fun export(weight: Double, events: List<DomainDoseEvent>): String =
        MahiroJsonFormat.generateExport(weight, events.map { it.toJsonV1PkEvent() })
}

internal object Batch6HrtPkProjection {
    fun project(events: List<DomainDoseEvent>): List<PkDoseEvent> = events.map(::project)

    fun project(event: DomainDoseEvent): PkDoseEvent {
        val timeH = when (val result = LegacyTimeAdapter.instantToTimeH(event.occurredAt)) {
            is LegacyTimeResult.Success -> result.value
            is LegacyTimeResult.Failure -> throw IllegalArgumentException(
                "Dose event time cannot be represented by the temporary PK projection"
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
}

private fun PkDoseEvent.toJsonV1DomainEvent(): DomainDoseEvent? {
    val occurredAt = when (val result = LegacyTimeAdapter.timeHToInstant(timeH)) {
        is LegacyTimeResult.Success -> result.value
        is LegacyTimeResult.Failure -> return null
    }
    return DomainDoseEvent(
        id = id,
        route = route,
        occurredAt = occurredAt,
        zoneId = null,
        localDate = null,
        doseMG = doseMG,
        ester = ester,
        extras = extras.mapKeys { (key, _) -> key.toDomainExtraKey() },
        slotId = null,
        source = DoseEventSource.JSON_V1,
        status = DoseEventStatus.RECORDED,
        revision = 1L
    )
}

private fun DomainDoseEvent.toJsonV1PkEvent(): PkDoseEvent {
    val timeH = when (val result = LegacyTimeAdapter.instantToTimeH(occurredAt)) {
        is LegacyTimeResult.Success -> result.value
        is LegacyTimeResult.Failure -> throw IllegalArgumentException(
            "Dose event time cannot be represented by JSON v1"
        )
    }
    return PkDoseEvent(
        id = id,
        route = route,
        timeH = timeH,
        doseMG = doseMG,
        ester = ester,
        extras = extras.mapKeys { (key, _) -> key.toPkExtraKey() }
    )
}

private fun DomainExtraKey.toPkExtraKey(): PkDoseEvent.ExtraKey = when (this) {
    DomainExtraKey.CONCENTRATION_MG_ML -> PkDoseEvent.ExtraKey.CONCENTRATION_MG_ML
    DomainExtraKey.AREA_CM2 -> PkDoseEvent.ExtraKey.AREA_CM2
    DomainExtraKey.RELEASE_RATE_UG_PER_DAY -> PkDoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY
    DomainExtraKey.SUBLINGUAL_THETA -> PkDoseEvent.ExtraKey.SUBLINGUAL_THETA
    DomainExtraKey.SUBLINGUAL_TIER -> PkDoseEvent.ExtraKey.SUBLINGUAL_TIER
    DomainExtraKey.ANTI_ANDROGEN_TYPE -> PkDoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE
}

private fun PkDoseEvent.ExtraKey.toDomainExtraKey(): DomainExtraKey = when (this) {
    PkDoseEvent.ExtraKey.CONCENTRATION_MG_ML -> DomainExtraKey.CONCENTRATION_MG_ML
    PkDoseEvent.ExtraKey.AREA_CM2 -> DomainExtraKey.AREA_CM2
    PkDoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY -> DomainExtraKey.RELEASE_RATE_UG_PER_DAY
    PkDoseEvent.ExtraKey.SUBLINGUAL_THETA -> DomainExtraKey.SUBLINGUAL_THETA
    PkDoseEvent.ExtraKey.SUBLINGUAL_TIER -> DomainExtraKey.SUBLINGUAL_TIER
    PkDoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE -> DomainExtraKey.ANTI_ANDROGEN_TYPE
}
