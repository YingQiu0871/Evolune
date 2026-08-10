package io.github.yuninggu.evolune.external.mahiro.v1

import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.time.LegacyTimeAdapter
import io.github.yuninggu.evolune.core.time.LegacyTimeError
import io.github.yuninggu.evolune.core.time.LegacyTimeResult
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import java.time.Instant
import java.util.UUID

class MahiroV1DoseEventAdapter(
    private val uuidSupplier: () -> UUID = UUID::randomUUID
) {
    fun toDomain(dto: MahiroV1DoseEventDto): MahiroV1ImportMappingResult {
        val route = ROUTE_FROM_WIRE[dto.route] ?: return MahiroV1ImportMappingResult.Failure(
            MahiroV1DomainMappingError.UnknownRoute(dto.route)
        )
        val ester = ESTER_FROM_WIRE[dto.ester] ?: return MahiroV1ImportMappingResult.Failure(
            MahiroV1DomainMappingError.UnknownEster(dto.ester)
        )
        val occurredAt = when (val result = LegacyTimeAdapter.timeHToInstant(dto.timeH)) {
            is LegacyTimeResult.Success -> result.value
            is LegacyTimeResult.Failure -> return MahiroV1ImportMappingResult.Failure(
                MahiroV1DomainMappingError.InvalidTimeH(dto.timeH, result.error)
            )
        }
        val id = dto.id?.let(::parseUuidOrNull) ?: uuidSupplier()

        return MahiroV1ImportMappingResult.Success(
            DoseEvent(
                id = id,
                route = route,
                occurredAt = occurredAt,
                zoneId = null,
                localDate = null,
                doseMG = dto.doseMG,
                ester = ester,
                extras = extrasToDomain(dto.extras),
                slotId = null,
                source = DoseEventSource.JSON_V1,
                status = DoseEventStatus.RECORDED,
                revision = 1L
            )
        )
    }

    fun fromDomain(event: DoseEvent): MahiroV1ExportMappingResult {
        val timeH = when (val result = LegacyTimeAdapter.instantToTimeH(event.occurredAt)) {
            is LegacyTimeResult.Success -> result.value
            is LegacyTimeResult.Failure -> return MahiroV1ExportMappingResult.Failure(
                MahiroV1DomainMappingError.UnrepresentableInstant(event.occurredAt, result.error)
            )
        }

        return MahiroV1ExportMappingResult.Success(
            MahiroV1DoseEventDto(
                id = event.id.toString(),
                route = ROUTE_TO_WIRE.getValue(event.route),
                ester = ESTER_TO_WIRE.getValue(event.ester),
                timeH = timeH,
                doseMG = event.doseMG,
                extras = extrasFromDomain(event.extras)
            )
        )
    }

    private fun parseUuidOrNull(rawId: String): UUID? = try {
        UUID.fromString(rawId)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun extrasToDomain(extras: Map<String, Double>): Map<ExtraKey, Double> =
        buildMap {
            EXTRA_KEYS.forEach { (wireKey, domainKey) ->
                extras[wireKey]?.let { put(domainKey, it) }
            }
        }

    private fun extrasFromDomain(extras: Map<ExtraKey, Double>): Map<String, Double> =
        buildMap {
            EXTRA_KEYS.forEach { (wireKey, domainKey) ->
                extras[domainKey]?.let { put(wireKey, it) }
            }
        }

    private companion object {
        val ROUTE_FROM_WIRE = mapOf(
            "injection" to Route.INJECTION,
            "oral" to Route.ORAL,
            "sublingual" to Route.SUBLINGUAL,
            "gel" to Route.GEL,
            "patch_apply" to Route.PATCH_APPLY,
            "patch_remove" to Route.PATCH_REMOVE,
            "antiandrogen" to Route.ANTIANDROGEN
        )
        val ROUTE_TO_WIRE = ROUTE_FROM_WIRE.entries.associate { (wire, route) -> route to wire }
        val ESTER_FROM_WIRE = mapOf(
            "E2" to Ester.E2,
            "EB" to Ester.EB,
            "EV" to Ester.EV,
            "EC" to Ester.EC,
            "EN" to Ester.EN
        )
        val ESTER_TO_WIRE = ESTER_FROM_WIRE.entries.associate { (wire, ester) -> ester to wire }
        val EXTRA_KEYS = listOf(
            "sublingualTier" to ExtraKey.SUBLINGUAL_TIER,
            "sublingualTheta" to ExtraKey.SUBLINGUAL_THETA,
            "concentrationMgMl" to ExtraKey.CONCENTRATION_MG_ML,
            "areaCm2" to ExtraKey.AREA_CM2,
            "releaseRateUgPerDay" to ExtraKey.RELEASE_RATE_UG_PER_DAY,
            "antiAndrogenType" to ExtraKey.ANTI_ANDROGEN_TYPE
        )
    }
}

sealed interface MahiroV1ImportMappingResult {
    data class Success(val event: DoseEvent) : MahiroV1ImportMappingResult
    data class Failure(val error: MahiroV1DomainMappingError) : MahiroV1ImportMappingResult
}

sealed interface MahiroV1ExportMappingResult {
    data class Success(val event: MahiroV1DoseEventDto) : MahiroV1ExportMappingResult
    data class Failure(val error: MahiroV1DomainMappingError) : MahiroV1ExportMappingResult
}

sealed interface MahiroV1DomainMappingError {
    data class UnknownRoute(val value: String) : MahiroV1DomainMappingError
    data class UnknownEster(val value: String) : MahiroV1DomainMappingError
    data class InvalidTimeH(
        val value: Double,
        val error: LegacyTimeError
    ) : MahiroV1DomainMappingError

    data class UnrepresentableInstant(
        val value: Instant,
        val error: LegacyTimeError
    ) : MahiroV1DomainMappingError
}
