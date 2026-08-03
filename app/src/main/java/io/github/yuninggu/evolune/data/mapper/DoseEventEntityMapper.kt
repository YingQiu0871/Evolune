package io.github.yuninggu.evolune.data.mapper

import io.github.yuninggu.evolune.core.model.DoseEvent as DomainDoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.time.LegacyTimeAdapter
import io.github.yuninggu.evolune.core.time.LegacyTimeResult
import io.github.yuninggu.evolune.data.DoseEventEntity
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route

fun DoseEventEntity.toDomainDoseEvent(): MappingResult<DomainDoseEvent> {
    val mappedRoute = when (val result = routeFromLegacyStorage(route)) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedEster = when (val result = esterFromLegacyStorage(ester)) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val occurredAt = when (val result = LegacyTimeAdapter.timeHToInstant(timeH)) {
        is LegacyTimeResult.Success -> result.value
        is LegacyTimeResult.Failure -> {
            return MappingResult.Failure(MappingError.InvalidTimeH(timeH, result.error))
        }
    }
    val mappedExtras = when (val result = extras.toDomainExtras()) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }

    return try {
        MappingResult.Success(
            DomainDoseEvent(
                id = id,
                route = mappedRoute,
                occurredAt = occurredAt,
                zoneId = null,
                localDate = null,
                doseMG = doseMG,
                ester = mappedEster,
                extras = mappedExtras,
                slotId = null,
                source = DoseEventSource.LEGACY,
                status = DoseEventStatus.RECORDED,
                revision = 1
            )
        )
    } catch (_: IllegalArgumentException) {
        MappingResult.Failure(MappingError.InvalidDoseEventInvariant(revision = 1))
    }
}

internal fun routeFromLegacyStorage(value: String): MappingResult<Route> = when (value) {
    "INJECTION" -> MappingResult.Success(Route.INJECTION)
    "ORAL" -> MappingResult.Success(Route.ORAL)
    "SUBLINGUAL" -> MappingResult.Success(Route.SUBLINGUAL)
    "GEL" -> MappingResult.Success(Route.GEL)
    "PATCH_APPLY" -> MappingResult.Success(Route.PATCH_APPLY)
    "PATCH_REMOVE" -> MappingResult.Success(Route.PATCH_REMOVE)
    "ANTIANDROGEN" -> MappingResult.Success(Route.ANTIANDROGEN)
    else -> MappingResult.Failure(MappingError.InvalidRoute(value))
}

internal fun esterFromLegacyStorage(value: String): MappingResult<Ester> = when (value) {
    "E2" -> MappingResult.Success(Ester.E2)
    "EB" -> MappingResult.Success(Ester.EB)
    "EV" -> MappingResult.Success(Ester.EV)
    "EC" -> MappingResult.Success(Ester.EC)
    "EN" -> MappingResult.Success(Ester.EN)
    else -> MappingResult.Failure(MappingError.InvalidEster(value))
}
