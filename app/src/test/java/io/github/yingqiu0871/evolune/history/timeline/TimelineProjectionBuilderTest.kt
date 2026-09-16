package io.github.yingqiu0871.evolune.history.timeline

import io.github.yingqiu0871.evolune.experience.HistoricalEntry
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityKey
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityStatus
import io.github.yingqiu0871.evolune.history.matchedEntry
import io.github.yingqiu0871.evolune.history.testDay
import io.github.yingqiu0871.evolune.history.testEvent
import io.github.yingqiu0871.evolune.history.testOccurrence
import io.github.yingqiu0871.evolune.history.testRange
import io.github.yingqiu0871.evolune.history.unmatchedEntry
import io.github.yingqiu0871.evolune.history.unrecordedEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * V17-D-01 §18 — TLM1–TLM17 and TLM19–TLM25 read-model acceptance (TLM18 lives in
 * [TimelineOrderingGeneratorParityTest] as the live generator-parity drift detector).
 */
class TimelineProjectionBuilderTest {

    private val date: LocalDate = LocalDate.of(2026, 9, 16)
    private val utc = ZoneOffset.UTC

    private fun rangeOf(vararg entries: HistoricalEntry): HistoricalRange = testRange(
        startDate = date.minusDays(1),
        endDate = date.plusDays(1),
        days = listOf(testDay(date = date, entries = entries.toList()))
    )

    private fun rowsOf(range: HistoricalRange): List<TimelineRow> =
        TimelineProjectionBuilder.build(range).days.flatMap { it.rows }

    // ---------- TLM1–TLM4: family mapping and future exclusion ----------

    @Test
    fun `TLM1 matched entry maps to exactly one MATCHED row`() {
        val row = rowsOf(rangeOf(matchedEntry())).single()
        assertEquals(TimelineRowKind.MATCHED, row.rowKind)
        assertNotNull(row.scheduleContext)
        assertNotNull(row.recordedIntake)
    }

    @Test
    fun `TLM2 unrecorded entry maps to exactly one neutral UNRECORDED_SCHEDULE row`() {
        val row = rowsOf(rangeOf(unrecordedEntry())).single()
        assertEquals(TimelineRowKind.UNRECORDED_SCHEDULE, row.rowKind)
        assertNotNull(row.scheduleContext)
        assertNull(row.recordedIntake)
    }

    @Test
    fun `TLM3 unmatched event maps to exactly one UNMATCHED_INTAKE row`() {
        val row = rowsOf(rangeOf(unmatchedEntry())).single()
        assertEquals(TimelineRowKind.UNMATCHED_INTAKE, row.rowKind)
        assertNull(row.scheduleContext)
        assertNotNull(row.recordedIntake)
    }

    @Test
    fun `TLM4 future occurrences are structurally excluded and there is no fourth family`() {
        // The projection input type carries no future collection at all.
        val futureField = HistoricalRange::class.java.declaredFields
            .map { it.name.lowercase() }
            .filter { it.contains("future") }
        assertTrue("HistoricalRange must not expose future occurrences", futureField.isEmpty())

        // Exactly three row families exist.
        assertEquals(3, TimelineRowKind.entries.size)
        assertEquals(
            listOf(
                TimelineRowKind.MATCHED,
                TimelineRowKind.UNRECORDED_SCHEDULE,
                TimelineRowKind.UNMATCHED_INTAKE
            ),
            TimelineRowKind.entries.toList()
        )

        val kinds = rowsOf(rangeOf(matchedEntry(), unrecordedEntry(), unmatchedEntry()))
            .map { it.rowKind }
            .toSet()
        assertTrue(TimelineRowKind.entries.containsAll(kinds))
    }

    // ---------- TLM5–TLM8: typed row identity and lifecycle ----------

    @Test
    fun `TLM5 occurrence-backed rows use Occurrence identity`() {
        val occurrence = testOccurrence(slotId = 7L)
        val matchedRow = rowsOf(rangeOf(matchedEntry(occurrence = occurrence))).single()
        assertEquals(
            TimelineRowId.Occurrence(occurrence.id),
            matchedRow.rowId
        )

        val unrecordedRow = rowsOf(rangeOf(unrecordedEntry(occurrence = occurrence))).single()
        assertEquals(TimelineRowId.Occurrence(occurrence.id), unrecordedRow.rowId)
    }

    @Test
    fun `TLM6 unmatched intake uses Event identity`() {
        val event = testEvent(id = 42L)
        val row = rowsOf(rangeOf(unmatchedEntry(event = event))).single()
        assertEquals(TimelineRowId.Event(event.eventId), row.rowId)
    }

    @Test
    fun `TLM7 unrecorded to matched keeps the occurrence-backed identity`() {
        val occurrence = testOccurrence(slotId = 11L)
        val event = testEvent(id = 11L, slotId = occurrence.slotId)

        val unrecordedId = rowsOf(rangeOf(unrecordedEntry(occurrence = occurrence))).single().rowId
        val matchedId = rowsOf(
            rangeOf(matchedEntry(occurrence = occurrence, event = event))
        ).single().rowId

        assertEquals(TimelineRowId.Occurrence(occurrence.id), unrecordedId)
        assertEquals(unrecordedId, matchedId)
    }

    @Test
    fun `TLM8 matched to unrecorded keeps the occurrence-backed identity`() {
        val occurrence = testOccurrence(slotId = 12L)
        val event = testEvent(id = 12L, slotId = occurrence.slotId)

        val matchedId = rowsOf(
            rangeOf(matchedEntry(occurrence = occurrence, event = event))
        ).single().rowId
        val unrecordedId = rowsOf(rangeOf(unrecordedEntry(occurrence = occurrence))).single().rowId

        assertEquals(TimelineRowId.Occurrence(occurrence.id), matchedId)
        assertEquals(matchedId, unrecordedId)
    }

    // ---------- TLM9–TLM12: schedule-context vs recorded-fact separation ----------

    @Test
    fun `TLM9 matched row keeps both sides distinct even with identical match keys`() {
        val occurrence = testOccurrence(slotId = 13L, medicationKey = "E2", doseAmount = 2.0)
        val event = testEvent(
            id = 13L,
            slotId = occurrence.slotId,
            medicationKey = "E2",
            doseAmount = 2.0
        )
        val row = rowsOf(rangeOf(matchedEntry(occurrence = occurrence, event = event))).single()

        val schedule = requireNotNull(row.scheduleContext)
        val recorded = requireNotNull(row.recordedIntake)
        // Two conceptually distinct sides coexist even when their keys coincide.
        assertEquals(schedule.matchKey, recorded.matchKey)
        assertEquals(occurrence.scheduledAt, schedule.scheduledAt)
        assertEquals(event.occurredAt, recorded.occurredAt)
    }

    @Test
    fun `TLM10 matched actual dose and identity come from the event side`() {
        val occurrence = testOccurrence(
            slotId = 14L,
            medicationKey = "E2",
            doseAmount = 2.0,
            routeKey = "ORAL"
        )
        val event = testEvent(
            id = 14L,
            slotId = occurrence.slotId,
            medicationKey = "EV",
            doseAmount = 3.0,
            routeKey = "SUBLINGUAL"
        )
        val row = rowsOf(rangeOf(matchedEntry(occurrence = occurrence, event = event))).single()

        assertEquals("E2", row.scheduleContext!!.matchKey.medicationKey)
        assertEquals(2.0, row.scheduleContext!!.matchKey.doseAmount, 0.0)

        assertEquals("EV", row.recordedIntake!!.matchKey.medicationKey)
        assertEquals(3.0, row.recordedIntake!!.matchKey.doseAmount, 0.0)
        assertEquals("SUBLINGUAL", row.recordedIntake!!.matchKey.routeKey)
        assertEquals(MedicationIdentityKey.EV, row.recordedIntake!!.identity.key)
        assertEquals(MedicationIdentityKey.E2, row.scheduleContext!!.identity.key)
    }

    @Test
    fun `TLM11 unrecorded carries schedule-context facts only`() {
        val occurrence = testOccurrence(slotId = 15L, medicationKey = "EC", doseAmount = 1.5)
        val row = rowsOf(rangeOf(unrecordedEntry(occurrence = occurrence))).single()

        assertNull(row.recordedIntake)
        assertEquals(occurrence.scheduledAt, row.scheduleContext!!.scheduledAt)
        assertEquals(1.5, row.scheduleContext!!.matchKey.doseAmount, 0.0)
        assertEquals(MedicationIdentityKey.EC, row.scheduleContext!!.identity.key)
    }

    @Test
    fun `TLM12 unmatched carries recorded facts only`() {
        val event = testEvent(id = 16L, medicationKey = "EB", doseAmount = 4.0)
        val row = rowsOf(rangeOf(unmatchedEntry(event = event))).single()

        assertNull(row.scheduleContext)
        assertEquals(event.occurredAt, row.recordedIntake!!.occurredAt)
        assertEquals(4.0, row.recordedIntake!!.matchKey.doseAmount, 0.0)
        assertEquals(MedicationIdentityKey.EB, row.recordedIntake!!.identity.key)
    }

    // ---------- TLM13–TLM15: identity truthfulness via the existing classifier ----------

    @Test
    fun `TLM13 KNOWN identity is preserved through the existing classifier`() {
        val row = rowsOf(rangeOf(unrecordedEntry(occurrence = testOccurrence(medicationKey = "EN")))).single()
        assertEquals(MedicationIdentityStatus.KNOWN, row.scheduleContext!!.identity.status)
        assertEquals(MedicationIdentityKey.EN, row.scheduleContext!!.identity.key)
    }

    @Test
    fun `TLM14 PARTIAL never becomes a specific medication`() {
        val row = rowsOf(
            rangeOf(unrecordedEntry(occurrence = testOccurrence(medicationKey = "UNKNOWN_ESTER")))
        ).single()
        assertEquals(MedicationIdentityStatus.PARTIAL, row.scheduleContext!!.identity.status)
        assertNull(row.scheduleContext!!.identity.key)
    }

    @Test
    fun `TLM15 UNAVAILABLE antiandrogen identity is not invented`() {
        val row = rowsOf(
            rangeOf(
                unrecordedEntry(
                    occurrence = testOccurrence(routeKey = "ANTIANDROGEN", medicationKey = "E2")
                )
            )
        ).single()
        assertEquals(MedicationIdentityStatus.UNAVAILABLE, row.scheduleContext!!.identity.status)
        assertNull(row.scheduleContext!!.identity.key)
    }

    // ---------- TLM16–TLM17: date preservation and canonical ordering ----------

    @Test
    fun `TLM16 displayDate is preserved exactly from the historical projection`() {
        val displayDate = date.minusDays(2)
        val entry = unrecordedEntry(
            occurrence = testOccurrence(date = date, time = LocalTime.of(9, 0)),
            displayDate = displayDate
        )
        val model = TimelineProjectionBuilder.build(
            testRange(
                startDate = date.minusDays(3),
                endDate = date,
                days = listOf(testDay(date = displayDate, entries = listOf(entry)))
            )
        )

        val row = model.days.single().rows.single()
        assertEquals(displayDate, row.displayDate)
        assertEquals(displayDate, model.days.single().date)
        // The occurrence's own scheduled local date is different; no re-derivation happened.
        assertEquals(date, entry.occurrence.scheduledLocalDateTime.toLocalDate())
    }

    @Test
    fun `TLM17 canonical ascending date and instant ordering`() {
        val dayOne = date
        val dayTwo = date.plusDays(1)
        val lateInstant = testOccurrence(slotId = 21L, date = dayOne, time = LocalTime.of(20, 0))
        val earlyInstant = testOccurrence(slotId = 22L, date = dayOne, time = LocalTime.of(6, 0))
        val dayTwoEntry = unrecordedEntry(
            occurrence = testOccurrence(slotId = 23L, date = dayTwo, time = LocalTime.of(10, 0))
        )

        val model = TimelineProjectionBuilder.build(
            testRange(
                startDate = dayOne,
                endDate = dayTwo,
                days = listOf(
                    testDay(date = dayTwo, entries = listOf(dayTwoEntry)),
                    testDay(
                        date = dayOne,
                        entries = listOf(unrecordedEntry(occurrence = lateInstant), unrecordedEntry(occurrence = earlyInstant))
                    )
                )
            )
        )

        assertEquals(listOf(dayOne, dayTwo), model.days.map { it.date })
        val dayOneInstants = model.days.first().rows.map { it.sortInstant }
        assertEquals(dayOneInstants.sorted(), dayOneInstants)
    }

    // ---------- TLM19–TLM20: same-instant determinism ----------

    @Test
    fun `TLM19 occurrence-backed rows order before unmatched intakes at the same instant`() {
        val instant = date.atTime(12, 0).toInstant(utc)
        val occurrenceEntry = unrecordedEntry(
            occurrence = testOccurrence(date = date, time = LocalTime.of(12, 0), slotId = 31L)
        )
        val unmatched = unmatchedEntry(
            event = testEvent(id = 31L, occurredAt = instant, localDate = date),
            displayDate = date
        )

        val rows = rowsOf(rangeOf(unmatched, occurrenceEntry))

        assertEquals(listOf(TimelineRowKind.UNRECORDED_SCHEDULE, TimelineRowKind.UNMATCHED_INTAKE), rows.map { it.rowKind })
        assertEquals(instant, rows[0].sortInstant)
        assertEquals(instant, rows[1].sortInstant)
    }

    @Test
    fun `TLM20 unmatched same-instant rows order deterministically by eventId`() {
        val instant = date.atTime(13, 0).toInstant(utc)
        val lower = testEvent(id = 1L, occurredAt = instant, localDate = date)
        val higher = testEvent(id = 2L, occurredAt = instant, localDate = date)
        // Supply the inputs in reverse order: sorting must produce the deterministic order.
        val rows = rowsOf(rangeOf(unmatchedEntry(event = higher), unmatchedEntry(event = lower)))

        assertEquals(
            listOf(TimelineRowId.Event(lower.eventId), TimelineRowId.Event(higher.eventId)),
            rows.map { it.rowId }
        )
        assertTrue(lower.eventId.toString() < higher.eventId.toString())
    }

    // ---------- TLM21–TLM25: exclusions, purity, determinism ----------

    @Test
    fun `TLM21 no delta or timing classification fields exist in the model`() {
        val forbidden = listOf(
            "delta", "timing", "late", "early", "ontime", "on_time", "overdue",
            "missed", "skipped", "adherence", "compliance", "lateness", "delay", "offset"
        )
        val types = listOf(
            TimelineRow::class.java,
            TimelineScheduleContext::class.java,
            TimelineRecordedIntake::class.java,
            TimelineDay::class.java,
            TimelineReadModel::class.java,
            TimelineRowId::class.java
        )
        types.forEach { type ->
            type.declaredFields.forEach { field ->
                forbidden.forEach { term ->
                    assertTrue(
                        "${type.simpleName}.${field.name} must not expose '$term'",
                        !field.name.lowercase().contains(term)
                    )
                }
            }
        }
        TimelineRowKind.entries.forEach { kind ->
            forbidden.forEach { term ->
                assertTrue(kind.name.lowercase(), !kind.name.lowercase().contains(term))
            }
        }
    }

    @Test
    fun `TLM22 no synthesized empty sections or rows`() {
        assertEquals(0, TimelineProjectionBuilder.build(testRange(date, date, emptyList())).days.size)

        val model = TimelineProjectionBuilder.build(rangeOf(unrecordedEntry()))
        assertTrue(model.days.isNotEmpty())
        assertTrue(model.days.none { it.rows.isEmpty() })
    }

    @Test
    fun `TLM23 project input can never contain future rows`() {
        // Public surface: exactly one pure entry point accepting an authoritative HistoricalRange.
        val publicMethods = TimelineProjectionBuilder::class.java.declaredMethods
            .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
        assertEquals(1, publicMethods.size)
        assertEquals("build", publicMethods.single().name)
        assertEquals(
            HistoricalRange::class.java,
            publicMethods.single().parameterTypes.single()
        )
        assertTrue(
            TimelineProjectionBuilder::class.java.declaredFields
                .none { it.name.lowercase().contains("future") }
        )
    }

    @Test
    fun `TLM24 projection is pure and carries no mutable instance state`() {
        // The builder is a stateless object: only the singleton INSTANCE field exists, no mutable state.
        val mutableFields = TimelineProjectionBuilder::class.java.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertTrue(
            "unexpected instance state: ${mutableFields.map { it.name }}",
            mutableFields.isEmpty()
        )
    }

    @Test
    fun `TLM25 repeated projection of identical input is deterministic`() {
        val range = rangeOf(
            matchedEntry(),
            unrecordedEntry(occurrence = testOccurrence(slotId = 41L, time = LocalTime.of(10, 0))),
            unmatchedEntry(event = testEvent(id = 41L, occurredAt = date.atTime(11, 0).toInstant(utc)))
        )
        val first = TimelineProjectionBuilder.build(range)
        val second = TimelineProjectionBuilder.build(range)
        assertEquals(first, second)
    }
}
