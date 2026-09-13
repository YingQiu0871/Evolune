package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.application.DoseEventEditSessionFactory
import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * A-04 §1.3: the modern quick-record inferred match must be covered with the **production**
 * shape, not with a hand-set provenance.
 *
 * `DoseEventEditSessionFactory.createQuickEvent(plan)` is the real Phone quick-record writer: it
 * stores `slotId = null`, `source = MANUAL` and the recording day as `localDate`, so it reaches
 * the occurrence matcher through a null-slot phase — exactly the shape that must not be
 * described as a "legacy record".
 */
class HistoryQuickRecordInferredTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val day: LocalDate = LocalDate.of(2025, 1, 5)
    private val plan = syntheticPlan(slots = listOf(LocalTime.of(23, 0)))

    /** Now is after the quick record, so the occurrence is history, not upcoming context. */
    private val now = day.atTime(23, 30).atZone(paris).toInstant()

    private fun quickRecordFactory(recordedAt: java.time.Instant) = DoseEventEditSessionFactory(
        idSupplier = { UUID(0L, 4242L) },
        clock = Clock.fixed(recordedAt, paris),
        zoneIdSupplier = { paris }
    )

    @Test
    fun `a production quick record matched by the time window gets the neutral inferred wording`() {
        val quickEvent = quickRecordFactory(day.atTime(23, 5).atZone(paris).toInstant())
            .createQuickEvent(plan)

        // Production shape of a quick record.
        assertNull(quickEvent.slotId)
        assertEquals(DoseEventSource.MANUAL, quickEvent.source)
        assertEquals(day, quickEvent.localDate)
        assertEquals(paris, quickEvent.zoneId)

        val entry = matchedEntryFor(quickEvent)

        // It is matched, but through a null-slot phase — never an exact slot/date match.
        assertEquals(day, entry.displayDate)
        assertNotEquals(
            MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE,
            entry.matchProvenance
        )
        assertEquals(MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW, entry.matchProvenance)

        val model = HistoryPresentation.entry(entry, paris)
        assertTrue(model.isInferredMatch)
        assertEquals(
            "a quick record must carry the provenance-neutral inferred note",
            R.string.history_note_inferred_match,
            model.noteRes
        )
        // Same local day: the actual time stays a short time.
        assertFalse(model.actualTime!!.needsFullDate)
        assertEquals(
            "23:05",
            HistoryFormatting.actualIntakeText(
                model.actualTime!!.instant,
                model.actualTime!!.zone,
                model.actualTime!!.needsFullDate,
                is24Hour = true
            )
        )
    }

    @Test
    fun `a quick record on the same day is never described as a legacy record`() {
        val quickEvent = quickRecordFactory(day.atTime(23, 5).atZone(paris).toInstant())
            .createQuickEvent(plan)

        val model = HistoryPresentation.entry(matchedEntryFor(quickEvent), paris)

        // The wording guard proves the neutral text itself; here we pin that the presentation
        // picks the neutral key for a modern quick record instead of any legacy-specific label.
        assertEquals(R.string.history_note_inferred_match, model.noteRes)
        val strings = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/res/values/strings.xml")
        )
        val note = Regex("<string name=\"history_note_inferred_match\">([^<]*)</string>")
            .find(strings)!!
            .groupValues[1]
        assertFalse("the neutral note must not claim a legacy origin: $note", note.contains("旧版"))
    }

    private fun matchedEntryFor(event: io.github.yingqiu0871.evolune.core.model.DoseEvent): MatchedHistoricalOccurrence =
        runBlocking {
            val range = HistoryReadService(
                FakeMedicationPlanRepository(listOf(plan)),
                FakeDoseEventRepository(listOf(event))
            ).readRange(day, day, paris, now)
            range.days.single().entries.single() as MatchedHistoricalOccurrence
        }
}
