package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Absolute-Instant range resolution (frozen contract §8): no DST, no calendar, no device zone. */
internal data class ResolvedPortableRange(
    val startInclusive: Instant?,
    val endInclusive: Instant
)

private val THIRTY_DAYS: Duration = Duration.ofHours(30L * 24L)
private val NINETY_DAYS: Duration = Duration.ofHours(90L * 24L)

internal fun resolvePortableRange(
    range: PortableExportRange,
    capturedAt: Instant
): ResolvedPortableRange = when (range) {
    PortableExportRange.LAST_30_DAYS ->
        ResolvedPortableRange(capturedAt.minus(THIRTY_DAYS), capturedAt)

    PortableExportRange.LAST_90_DAYS ->
        ResolvedPortableRange(capturedAt.minus(NINETY_DAYS), capturedAt)

    PortableExportRange.ALL ->
        ResolvedPortableRange(null, capturedAt)
}

private val FILE_NAME_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

internal fun portableExportRangeToken(range: PortableExportRange): String = when (range) {
    PortableExportRange.LAST_30_DAYS -> "30d"
    PortableExportRange.LAST_90_DAYS -> "90d"
    PortableExportRange.ALL -> "all"
}

/** `evolune-export-<range>-<yyyyMMdd>.json/.csv` (frozen contract §36); naming only. */
internal fun portableExportFileName(
    range: PortableExportRange,
    format: PortableExportFormat,
    capturedAt: Instant
): String {
    val utcDate = capturedAt.atZone(ZoneOffset.UTC).toLocalDate().format(FILE_NAME_DATE)
    val extension = when (format) {
        PortableExportFormat.JSON -> "json"
        PortableExportFormat.CSV -> "csv"
    }
    return "evolune-export-${portableExportRangeToken(range)}-$utcDate.$extension"
}

/**
 * Canonical 11-field equality rendering (frozen contract §18): two events compare equal when
 * their canonical portable renderings are equal, independent of repository revision.
 */
internal data class PortableEventKey(
    val id: String,
    val actualTime: String,
    val localDate: String?,
    val zoneId: String?,
    val route: String,
    val ester: String,
    val doseMG: String,
    val extras: String,
    val slotId: String?,
    val source: String,
    val status: String
)

internal fun DoseEvent.toPortableEvent(): PortableEvent = PortableEvent(
    id = id,
    occurredAt = occurredAt,
    localDate = localDate,
    zoneId = zoneId,
    route = route,
    ester = ester,
    doseMG = doseMG,
    extras = extras,
    slotId = slotId,
    source = source,
    status = status
)

internal fun PortableEvent.toPortableEventKey(): PortableEventKey = PortableEventKey(
    id = id.toString(),
    actualTime = PortableJsonText.formatInstant(occurredAt) ?: occurredAt.toString(),
    localDate = localDate?.toString(),
    zoneId = zoneId?.id,
    route = route.name,
    ester = ester.name,
    doseMG = PortableJsonText.canonicalNumber(doseMG),
    extras = PortableJsonText.compactExtras(extras),
    slotId = slotId?.toString(),
    source = source.name,
    status = status.name
)

internal fun DoseEvent.toPortableEventKey(): PortableEventKey = toPortableEvent().toPortableEventKey()

/** Serialization preconditions; any false row makes the whole export [PortableExportResult.InvalidData]. */
internal fun PortableEvent.isSerializable(): Boolean =
    PortableJsonText.formatInstant(occurredAt) != null &&
        doseMG.isFinite() &&
        extras.values.all(Double::isFinite)

internal fun PortableEvent.toDomainEvent(): DoseEvent = DoseEvent(
    id = id,
    route = route,
    occurredAt = occurredAt,
    zoneId = zoneId,
    localDate = localDate,
    doseMG = doseMG,
    ester = ester,
    extras = extras,
    slotId = slotId,
    source = source,
    status = status,
    revision = 1L
)
