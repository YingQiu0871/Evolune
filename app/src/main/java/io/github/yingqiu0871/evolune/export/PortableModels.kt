package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** V17 Phase E export ranges (frozen contract §8). */
enum class PortableExportRange {
    LAST_30_DAYS,
    LAST_90_DAYS,
    ALL
}

/** V17 Phase E canonical export formats (frozen contract §24/§29). */
enum class PortableExportFormat {
    JSON,
    CSV
}

/** One canonical export request: range + format + single normalized capturedAt (§7). */
data class PortableExportRequest(
    val range: PortableExportRange,
    val format: PortableExportFormat,
    val capturedAt: Instant
)

/** The 11 portable medication-event truth fields (frozen contract §2/§10). */
data class PortableEvent(
    val id: UUID,
    val occurredAt: Instant,
    val localDate: LocalDate?,
    val zoneId: ZoneId?,
    val route: Route,
    val ester: Ester,
    val doseMG: Double,
    val extras: Map<ExtraKey, Double>,
    val slotId: UUID?,
    val source: DoseEventSource,
    val status: DoseEventStatus
)

/** A fully validated canonical document (import side). */
data class PortableDocument(
    val capturedAt: Instant,
    val range: PortableExportRange,
    val events: List<PortableEvent>
)

/**
 * Typed export result (frozen contract §33): no production UI callback may receive an
 * uncaught serializer/domain exception; no stack trace/path leaks into UX.
 */
sealed interface PortableExportResult {
    data class Success(
        val bytes: ByteArray,
        val fileName: String
    ) : PortableExportResult

    data object InvalidData : PortableExportResult

    data object IoFailure : PortableExportResult

    data object UnexpectedFailure : PortableExportResult
}

/**
 * Typed canonical import outcomes (frozen contract §34): success / conflict-containing /
 * partial success / invalid document / unsupported version / too large / storage failure.
 */
sealed interface PortableImportOutcome {
    data class Completed(
        val insertedCount: Int,
        val idempotentCount: Int,
        val conflictCount: Int
    ) : PortableImportOutcome {
        val totalCount: Int = insertedCount + idempotentCount + conflictCount
        val hasConflicts: Boolean = conflictCount > 0
    }

    data class PartialFailure(
        val insertedCount: Int,
        val idempotentCount: Int,
        val conflictCount: Int,
        val processedCount: Int,
        val totalCount: Int
    ) : PortableImportOutcome

    data class StorageFailure(
        val totalCount: Int
    ) : PortableImportOutcome

    data object InvalidDocument : PortableImportOutcome

    data object UnsupportedVersion : PortableImportOutcome

    data object TooLarge : PortableImportOutcome
}
