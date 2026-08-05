package io.github.yuninggu.evolune.application

import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduleType
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class DoseEventEditorTest {
    @Test
    fun `new session captures identity time zone date and metadata once`() {
        var idCalls = 0
        val factory = DoseEventEditSessionFactory(
            idSupplier = {
                idCalls += 1
                EVENT_ID
            },
            clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC),
            zoneIdSupplier = { TEST_ZONE }
        )

        val session = factory.createNew()

        assertEquals(1, idCalls)
        assertEquals(EVENT_ID, session.original.id)
        assertEquals(CREATED_AT, session.original.occurredAt)
        assertEquals(TEST_ZONE, session.original.zoneId)
        assertEquals(CREATED_AT.atZone(TEST_ZONE).toLocalDate(), session.original.localDate)
        assertEquals(DoseEventSource.MANUAL, session.original.source)
        assertEquals(DoseEventStatus.RECORDED, session.original.status)
        assertNull(session.original.slotId)
        assertEquals(1L, session.original.revision)
        assertNull(session.expectedRevision)
    }

    @Test
    fun `create command reuses session identity and time across repeated mapping`() {
        val session = factory().createNew()
        val input = input(occurredAt = CREATED_AT.plusSeconds(30), occurredAtEdited = false)

        val first = valid(input.toDoseEventCommand(session)).command as DoseEventEditCommand.Create
        val second = valid(input.toDoseEventCommand(session)).command as DoseEventEditCommand.Create

        assertEquals(EVENT_ID, first.event.id)
        assertEquals(CREATED_AT, first.event.occurredAt)
        assertEquals(first, second)
    }

    @Test
    fun `edit command preserves complete metadata extras and expected revision`() {
        val original = event(
            extras = mapOf(
                ExtraKey.AREA_CM2 to 10.0,
                ExtraKey.SUBLINGUAL_THETA to 0.25
            )
        )
        val session = factory().edit(original)

        val command = valid(
            input(
                route = Route.GEL,
                doseMG = 3.0,
                extras = mapOf(ExtraKey.AREA_CM2 to 20.0)
            ).toDoseEventCommand(session)
        ).command as DoseEventEditCommand.Update

        assertEquals(original.id, command.event.id)
        assertEquals(original.occurredAt, command.event.occurredAt)
        assertEquals(original.zoneId, command.event.zoneId)
        assertEquals(original.localDate, command.event.localDate)
        assertEquals(original.slotId, command.event.slotId)
        assertEquals(original.source, command.event.source)
        assertEquals(original.status, command.event.status)
        assertEquals(original.revision, command.event.revision)
        assertEquals(original.revision, command.expectedRevision)
        assertEquals(20.0, command.event.extras[ExtraKey.AREA_CM2])
        assertEquals(0.25, command.event.extras[ExtraKey.SUBLINGUAL_THETA])
    }

    @Test
    fun `explicit time edit updates time context but preserves provenance and slot`() {
        val original = event()
        val editedAt = Instant.parse("2026-04-05T00:30:00Z")
        val command = valid(
            input(occurredAt = editedAt, occurredAtEdited = true)
                .toDoseEventCommand(factory().edit(original))
        ).command as DoseEventEditCommand.Update

        assertEquals(editedAt, command.event.occurredAt)
        assertEquals(TEST_ZONE, command.event.zoneId)
        assertEquals(editedAt.atZone(TEST_ZONE).toLocalDate(), command.event.localDate)
        assertEquals(original.source, command.event.source)
        assertEquals(original.slotId, command.event.slotId)
        assertEquals(original.revision, command.event.revision)
    }

    @Test
    fun `invalid input is structured and does not create a command`() {
        val invalid = input(
            doseMG = Double.NaN,
            extras = mapOf(ExtraKey.AREA_CM2 to Double.POSITIVE_INFINITY),
            occurredAt = Instant.ofEpochSecond(1, 1),
            occurredAtEdited = true
        ).toDoseEventCommand(factory().createNew()) as DoseEventEditorResult.Invalid

        assertTrue(DoseEventInputIssue.InvalidDose in invalid.issues)
        assertTrue(DoseEventInputIssue.InvalidOccurredAtPrecision in invalid.issues)
        assertTrue(
            DoseEventInputIssue.InvalidExtra(ExtraKey.AREA_CM2) in invalid.issues
        )
    }

    @Test
    fun `out of epoch millis range is a structured time precision issue`() {
        val invalid = input(
            occurredAt = Instant.MAX,
            occurredAtEdited = true
        ).toDoseEventCommand(factory().createNew()) as DoseEventEditorResult.Invalid

        assertEquals(
            listOf(DoseEventInputIssue.InvalidOccurredAtPrecision),
            invalid.issues
        )
    }

    @Test
    fun `patch removal and rate mode permit zero dose while ordinary routes do not`() {
        val session = factory().createNew()
        assertTrue(
            input(route = Route.PATCH_REMOVE, doseMG = 0.0)
                .toDoseEventCommand(session) is DoseEventEditorResult.Valid
        )
        assertTrue(
            input(
                route = Route.PATCH_APPLY,
                doseMG = 0.0,
                extras = mapOf(ExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0)
            ).toDoseEventCommand(session) is DoseEventEditorResult.Valid
        )
        val invalid = input(route = Route.ORAL, doseMG = 0.0)
            .toDoseEventCommand(session) as DoseEventEditorResult.Invalid
        assertTrue(DoseEventInputIssue.NonPositiveDose in invalid.issues)
    }

    @Test
    fun `quick event uses one id minute precision and complete manual metadata`() {
        var nextId = 1L
        val factory = DoseEventEditSessionFactory(
            idSupplier = { UUID(0L, nextId++) },
            clock = Clock.fixed(Instant.parse("2026-01-02T03:04:59.999Z"), ZoneOffset.UTC),
            zoneIdSupplier = { TEST_ZONE }
        )

        val first = factory.createQuickEvent(plan())
        val second = factory.createQuickEvent(plan())

        assertEquals(UUID(0L, 1L), first.id)
        assertEquals(UUID(0L, 2L), second.id)
        assertEquals(Instant.parse("2026-01-02T03:04:00Z"), first.occurredAt)
        assertEquals(TEST_ZONE, first.zoneId)
        assertEquals(first.occurredAt.atZone(TEST_ZONE).toLocalDate(), first.localDate)
        assertEquals(DoseEventSource.MANUAL, first.source)
        assertEquals(DoseEventStatus.RECORDED, first.status)
        assertEquals(1L, first.revision)
        assertNull(first.slotId)
        assertEquals(plan().extras, first.extras)
    }

    private fun factory(): DoseEventEditSessionFactory = DoseEventEditSessionFactory(
        idSupplier = { EVENT_ID },
        clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC),
        zoneIdSupplier = { TEST_ZONE }
    )

    private fun input(
        occurredAt: Instant = CREATED_AT,
        occurredAtEdited: Boolean = false,
        route: Route = Route.ORAL,
        doseMG: Double = 2.0,
        extras: Map<ExtraKey, Double> = emptyMap()
    ) = DoseEventEditorInput(
        occurredAt = occurredAt,
        occurredAtEdited = occurredAtEdited,
        route = route,
        doseMG = doseMG,
        ester = Ester.E2,
        extras = extras
    )

    private fun event(
        extras: Map<ExtraKey, Double> = emptyMap()
    ): DoseEvent = DoseEvent(
        id = EVENT_ID,
        route = Route.ORAL,
        occurredAt = CREATED_AT,
        zoneId = ZoneId.of("Europe/Berlin"),
        localDate = CREATED_AT.atZone(ZoneId.of("Europe/Berlin")).toLocalDate(),
        doseMG = 2.0,
        ester = Ester.E2,
        extras = extras,
        slotId = SLOT_ID,
        source = DoseEventSource.WEAR,
        status = DoseEventStatus.RECORDED,
        revision = 7L
    )

    private fun plan(): MedicationPlan = MedicationPlan(
        id = UUID(0L, 20L),
        name = "Synthetic quick plan",
        route = Route.SUBLINGUAL,
        ester = Ester.E2,
        doseMG = 1.0,
        scheduleType = ScheduleType.DAILY,
        slots = emptyList(),
        daysOfWeek = emptySet(),
        intervalDays = 1,
        isEnabled = true,
        extras = mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0),
        createdAt = Instant.EPOCH
    )

    private fun valid(result: DoseEventEditorResult): DoseEventEditorResult.Valid {
        assertTrue(result is DoseEventEditorResult.Valid)
        return result as DoseEventEditorResult.Valid
    }

    private companion object {
        val EVENT_ID: UUID = UUID(0L, 10L)
        val SLOT_ID: UUID = UUID(0L, 11L)
        val CREATED_AT: Instant = Instant.parse("2026-01-02T03:04:05.678Z")
        val TEST_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
