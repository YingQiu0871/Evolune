package io.github.yuninggu.evolune.data.mapper

import io.github.yuninggu.evolune.core.model.DoseEvent as DomainDoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.time.LegacyTimeAdapter
import io.github.yuninggu.evolune.core.time.LegacyTimeResult
import io.github.yuninggu.evolune.data.DoseEventEntity
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

fun DoseEventEntity.toV3DomainDoseEvent(): MappingResult<DomainDoseEvent> {
    val mappedRoute = when (val result = routeFromLegacyStorage(route)) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedEster = when (val result = esterFromLegacyStorage(ester)) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val legacyEpochMillis = when (val result = LegacyTimeAdapter.timeHToEpochMillis(timeH)) {
        is LegacyTimeResult.Success -> result.value
        is LegacyTimeResult.Failure -> {
            return MappingResult.Failure(MappingError.InvalidTimeH(timeH, result.error))
        }
    }
    if (legacyEpochMillis != occurredAtEpochMillis) {
        return MappingResult.Failure(MappingError.InconsistentEventTime(id))
    }
    val mappedZoneId = if (zoneId == null) {
        null
    } else {
        try {
            ZoneId.of(zoneId)
        } catch (_: DateTimeException) {
            return MappingResult.Failure(MappingError.InvalidZoneId(zoneId))
        }
    }
    val mappedLocalDate = if (localDate == null) {
        null
    } else {
        try {
            LocalDate.parse(localDate)
        } catch (_: DateTimeException) {
            return MappingResult.Failure(MappingError.InvalidLocalDate(localDate))
        }
    }
    val mappedExtras = when (val result = extras.toDomainExtras()) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedSource = when (source) {
        "LEGACY" -> DoseEventSource.LEGACY
        "MANUAL" -> DoseEventSource.MANUAL
        "JSON_V1" -> DoseEventSource.JSON_V1
        "REMINDER" -> DoseEventSource.REMINDER
        "WIDGET" -> DoseEventSource.WIDGET
        "WEAR" -> DoseEventSource.WEAR
        else -> return MappingResult.Failure(MappingError.InvalidSource(source))
    }
    val mappedStatus = when (status) {
        "RECORDED" -> DoseEventStatus.RECORDED
        else -> return MappingResult.Failure(MappingError.InvalidStatus(status))
    }

    return try {
        MappingResult.Success(
            DomainDoseEvent(
                id = id,
                route = mappedRoute,
                occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
                zoneId = mappedZoneId,
                localDate = mappedLocalDate,
                doseMG = doseMG,
                ester = mappedEster,
                extras = mappedExtras,
                slotId = slotId,
                source = mappedSource,
                status = mappedStatus,
                revision = revision
            )
        )
    } catch (_: IllegalArgumentException) {
        MappingResult.Failure(MappingError.InvalidDoseEventInvariant(revision))
    }
}

fun DomainDoseEvent.toV3Entity(): MappingResult<DoseEventEntity> {
    val epochMillis = when (val result = instantToEpochMillisForPersistence(occurredAt)) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    if (Instant.ofEpochMilli(epochMillis) != occurredAt) {
        return MappingResult.Failure(MappingError.InvalidOccurredAtPrecision(occurredAt))
    }
    val legacyTimeH = when (val result = LegacyTimeAdapter.epochMillisToTimeH(epochMillis)) {
        is LegacyTimeResult.Success -> result.value
        is LegacyTimeResult.Failure -> {
            return MappingResult.Failure(
                MappingError.InvalidTimeH(Double.NaN, result.error)
            )
        }
    }
    val roundTripMillis = when (val result = LegacyTimeAdapter.timeHToEpochMillis(legacyTimeH)) {
        is LegacyTimeResult.Success -> result.value
        is LegacyTimeResult.Failure -> {
            return MappingResult.Failure(MappingError.InvalidTimeH(legacyTimeH, result.error))
        }
    }
    if (roundTripMillis != epochMillis) {
        return MappingResult.Failure(MappingError.InconsistentEventTime(id))
    }

    return MappingResult.Success(
        DoseEventEntity(
            id = id,
            route = route.toLegacyStorageRoute(),
            timeH = legacyTimeH,
            doseMG = doseMG,
            ester = ester.toLegacyStorageEster(),
            extras = extras.mapKeys { (key, _) -> key.toLegacyStorageKey() },
            occurredAtEpochMillis = epochMillis,
            zoneId = zoneId?.id,
            localDate = localDate?.toString(),
            slotId = slotId,
            source = source.toLegacyStorageSource(),
            status = status.toLegacyStorageStatus(),
            revision = revision
        )
    )
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

internal fun Route.toLegacyStorageRoute(): String = when (this) {
    Route.INJECTION -> "INJECTION"
    Route.ORAL -> "ORAL"
    Route.SUBLINGUAL -> "SUBLINGUAL"
    Route.GEL -> "GEL"
    Route.PATCH_APPLY -> "PATCH_APPLY"
    Route.PATCH_REMOVE -> "PATCH_REMOVE"
    Route.ANTIANDROGEN -> "ANTIANDROGEN"
}

internal fun Ester.toLegacyStorageEster(): String = when (this) {
    Ester.E2 -> "E2"
    Ester.EB -> "EB"
    Ester.EV -> "EV"
    Ester.EC -> "EC"
    Ester.EN -> "EN"
}

private fun DoseEventSource.toLegacyStorageSource(): String = when (this) {
    DoseEventSource.LEGACY -> "LEGACY"
    DoseEventSource.MANUAL -> "MANUAL"
    DoseEventSource.JSON_V1 -> "JSON_V1"
    DoseEventSource.REMINDER -> "REMINDER"
    DoseEventSource.WIDGET -> "WIDGET"
    DoseEventSource.WEAR -> "WEAR"
}

private fun DoseEventStatus.toLegacyStorageStatus(): String = when (this) {
    DoseEventStatus.RECORDED -> "RECORDED"
}
