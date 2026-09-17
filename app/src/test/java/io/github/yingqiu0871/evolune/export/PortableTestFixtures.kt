package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LatestDoseDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Fixed capturedAt used by every Phase-E determinism/golden fixture (contract §31). */
internal val PORTABLE_CAPTURED_AT: Instant = Instant.parse("2026-09-16T08:05:00.000Z")

/**
 * Faithful counting repository double: ranged reads mirror the Room DAO semantics
 * (half-open instants ordered by `(occurredAt, id)`; inclusive `<=` for the all-history
 * channel) and every write API is counted so E1 zero-write assertions are meaningful.
 */
internal class RecordingDoseEventRepository(
    initialEvents: List<DoseEvent> = emptyList()
) : DoseEventRepository {

    val rows = LinkedHashMap<UUID, DoseEvent>()

    init {
        initialEvents.forEach { rows[it.id] = it }
    }

    var observeAllCalls = 0
    var getByIdCalls = 0
    var findBetweenCalls = 0
    var findAllUpToCalls = 0
    var insertCalls = 0
    var updateCalls = 0
    var deleteCalls = 0
    var deleteIfRevisionCalls = 0
    var deleteLatestCalls = 0
    var deleteAllCalls = 0

    var getByIdFailure: Throwable? = null
    var insertFailure: Throwable? = null
    var readFailure: Throwable? = null
    var writeFailure: Throwable? = null
    var beforeInsert: ((DoseEvent) -> Unit)? = null
    var insertResultOverride: ((DoseEvent) -> InsertResult?)? = null
    var onRead: (() -> Unit)? = null

    private fun guardWrite() {
        writeFailure?.let { throw it }
    }

    /** When true the ranged reads preserve insertion order (proves callers never trust DAO order). */
    var preserveInsertionOrderInReads = false

    val writeCalls: Int
        get() = insertCalls + updateCalls + deleteCalls +
            deleteIfRevisionCalls + deleteLatestCalls + deleteAllCalls

    fun snapshot(): List<DoseEvent> = rows.values.toList()

    override fun observeAll(): Flow<List<DoseEvent>> {
        observeAllCalls += 1
        return flowOf(rows.values.toList())
    }

    override suspend fun getById(id: UUID): DoseEvent? {
        getByIdCalls += 1
        getByIdFailure?.let { throw it }
        return rows[id]
    }

    override suspend fun findOccurredBetween(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<DoseEvent> {
        findBetweenCalls += 1
        onRead?.invoke()
        readFailure?.let { throw it }
        val filtered = rows.values
            .filter { !it.occurredAt.isBefore(startInclusive) && it.occurredAt.isBefore(endExclusive) }
        return if (preserveInsertionOrderInReads) filtered
        else filtered.sortedWith(compareBy({ it.occurredAt }, { it.id.toString() }))
    }

    override suspend fun findRecordedLocalDateBetween(
        startInclusive: LocalDate,
        endInclusive: LocalDate
    ): List<DoseEvent> = rows.values
        .filter { event ->
            val date = event.localDate
            date != null && !date.isBefore(startInclusive) && !date.isAfter(endInclusive)
        }
        .sortedWith(compareBy({ it.localDate }, { it.occurredAt }, { it.id.toString() }))

    override suspend fun findAllOccurredUpTo(endInclusive: Instant): List<DoseEvent> {
        findAllUpToCalls += 1
        onRead?.invoke()
        readFailure?.let { throw it }
        val filtered = rows.values.filter { !it.occurredAt.isAfter(endInclusive) }
        return if (preserveInsertionOrderInReads) filtered
        else filtered.sortedWith(compareBy({ it.occurredAt }, { it.id.toString() }))
    }

    override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> =
        rows.values.sortedWith(compareByDescending { it.occurredAt })

    override suspend fun insert(event: DoseEvent): InsertResult {
        guardWrite()
        insertCalls += 1
        beforeInsert?.invoke(event)
        insertFailure?.let { throw it }
        insertResultOverride?.invoke(event)?.let { return it }
        val existing = rows[event.id]
        return when {
            existing == null -> {
                rows[event.id] = event
                InsertResult.Inserted
            }

            existing == event -> InsertResult.Idempotent
            else -> InsertResult.Conflict
        }
    }

    override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult {
        guardWrite()
        updateCalls += 1
        return UpdateResult.Invalid
    }

    override suspend fun delete(id: UUID): DeleteResult {
        guardWrite()
        deleteCalls += 1
        return DeleteResult.NotFound
    }

    override suspend fun deleteIfRevisionMatches(
        id: UUID,
        expectedRevision: Long
    ): ConditionalDeleteResult {
        guardWrite()
        deleteIfRevisionCalls += 1
        return ConditionalDeleteResult.NotFound
    }

    override suspend fun deleteLatestRecordedIfRevisionMatches(
        eventId: UUID,
        eventRevision: Long
    ): LatestDoseDeleteResult {
        guardWrite()
        deleteLatestCalls += 1
        return LatestDoseDeleteResult.EventNotFound
    }

    override suspend fun deleteAll(): DeleteResult {
        guardWrite()
        deleteAllCalls += 1
        return DeleteResult.NotFound
    }
}

internal class FakeStorageException(message: String) : RuntimeException(message)

internal fun eventId(value: Long): UUID = UUID(0L, value)

internal fun syntheticEvent(
    id: UUID = eventId(1L),
    occurredAt: Instant = Instant.parse("2026-09-10T08:00:00Z"),
    localDate: LocalDate? = LocalDate.of(2026, 9, 10),
    zoneId: ZoneId? = ZoneId.of("Asia/Tokyo"),
    route: Route = Route.INJECTION,
    ester: Ester = Ester.EV,
    doseMG: Double = 5.0,
    extras: Map<ExtraKey, Double> = emptyMap(),
    slotId: UUID? = null,
    source: DoseEventSource = DoseEventSource.MANUAL,
    status: DoseEventStatus = DoseEventStatus.RECORDED,
    revision: Long = 1L
): DoseEvent = DoseEvent(
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
    revision = revision
)

/**
 * E7.2 nine event classes (contract §39): normal app-recorded, manual, legacy imported,
 * null slotId, null zoneId, null localDate, partial/unavailable medication identity,
 * populated extras, ambiguous/orphan provenance.
 */
internal fun nineClassFixture(): List<DoseEvent> = listOf(
    syntheticEvent(
        id = eventId(101L),
        occurredAt = Instant.parse("2026-09-15T09:00:00.000Z"),
        localDate = LocalDate.of(2026, 9, 15),
        zoneId = ZoneId.of("Asia/Tokyo"),
        route = Route.INJECTION,
        ester = Ester.EV,
        doseMG = 5.0,
        extras = mapOf(ExtraKey.CONCENTRATION_MG_ML to 20.0),
        slotId = eventId(900L),
        source = DoseEventSource.MANUAL
    ),
    syntheticEvent(
        id = eventId(102L),
        occurredAt = Instant.parse("2026-09-14T01:30:00.000Z"),
        localDate = LocalDate.of(2026, 9, 14),
        zoneId = ZoneId.of("Europe/Paris"),
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        extras = mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0),
        source = DoseEventSource.MANUAL
    ),
    syntheticEvent(
        id = eventId(103L),
        occurredAt = Instant.parse("2026-08-01T12:00:00.000Z"),
        localDate = null,
        zoneId = null,
        route = Route.GEL,
        ester = Ester.E2,
        doseMG = 0.15,
        source = DoseEventSource.LEGACY
    ),
    syntheticEvent(
        id = eventId(104L),
        occurredAt = Instant.parse("2026-09-13T22:00:00.000Z"),
        localDate = LocalDate.of(2026, 9, 14),
        zoneId = ZoneId.of("Asia/Shanghai"),
        route = Route.PATCH_APPLY,
        ester = Ester.E2,
        doseMG = 0.05,
        extras = mapOf(ExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0),
        slotId = null,
        source = DoseEventSource.REMINDER
    ),
    syntheticEvent(
        id = eventId(105L),
        occurredAt = Instant.parse("2026-09-12T10:15:00.000Z"),
        localDate = LocalDate.of(2026, 9, 12),
        zoneId = null,
        route = Route.SUBLINGUAL,
        ester = Ester.E2,
        doseMG = 1.0,
        extras = mapOf(ExtraKey.SUBLINGUAL_THETA to 0.5),
        source = DoseEventSource.WIDGET
    ),
    syntheticEvent(
        id = eventId(106L),
        occurredAt = Instant.parse("2026-09-11T07:45:00.000Z"),
        localDate = null,
        zoneId = ZoneId.of("UTC"),
        route = Route.PATCH_REMOVE,
        ester = Ester.E2,
        doseMG = 0.0,
        source = DoseEventSource.WEAR
    ),
    syntheticEvent(
        id = eventId(107L),
        occurredAt = Instant.parse("2026-09-10T20:00:00.000Z"),
        localDate = LocalDate.of(2026, 9, 10),
        zoneId = ZoneId.of("Asia/Tokyo"),
        route = Route.ANTIANDROGEN,
        ester = Ester.E2,
        doseMG = 50.0,
        extras = emptyMap(),
        source = DoseEventSource.MANUAL
    ),
    syntheticEvent(
        id = eventId(108L),
        occurredAt = Instant.parse("2026-09-09T03:20:00.000Z"),
        localDate = LocalDate.of(2026, 9, 9),
        zoneId = ZoneId.of("America/New_York"),
        route = Route.ANTIANDROGEN,
        ester = Ester.E2,
        doseMG = 25.0,
        extras = mapOf(
            ExtraKey.ANTI_ANDROGEN_TYPE to 2.0,
            ExtraKey.AREA_CM2 to 10.0,
            ExtraKey.CONCENTRATION_MG_ML to 6.0,
            ExtraKey.RELEASE_RATE_UG_PER_DAY to 100.0,
            ExtraKey.SUBLINGUAL_THETA to 1.0,
            ExtraKey.SUBLINGUAL_TIER to 3.0
        ),
        source = DoseEventSource.MANUAL
    ),
    syntheticEvent(
        id = eventId(109L),
        occurredAt = Instant.parse("2026-09-08T18:40:00.000Z"),
        localDate = LocalDate.of(2026, 9, 9),
        zoneId = ZoneId.of("Asia/Tokyo"),
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 7.25,
        slotId = eventId(901L),
        source = DoseEventSource.REMINDER,
        revision = 4L
    )
)

internal fun canonicalJsonOf(events: List<DoseEvent>): String =
    String(
        PortableJsonCodec.encode(
            PortableExportRequest(
                range = PortableExportRange.ALL,
                format = PortableExportFormat.JSON,
                capturedAt = PORTABLE_CAPTURED_AT
            ),
            events.map { it.toPortableEvent() }
        )!!,
        Charsets.UTF_8
    )
