package io.github.yingqiu0871.evolune.ui.screens.retrospective

import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceId
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkLimitation
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkUnavailableReason
import io.github.yingqiu0871.evolune.history.retrospective.IntakeMarkerProvenance
import io.github.yingqiu0871.evolune.history.retrospective.RecordedIntakeMarker
import io.github.yingqiu0871.evolune.history.retrospective.ScheduleContextMarker
import io.github.yingqiu0871.evolune.history.retrospective.ScheduleMarkerProvenance
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * V17-C-04 §14.5 T3 (JVM part) and §D-11: every marker family, unavailable reason and limitation
 * must resolve to its own frozen resource; the mapping can never silently drift to another family.
 */
class RetrospectivePresentationTest {

    private val scheduleMarker = ScheduleContextMarker(
        occurrenceId = MedicationOccurrenceId(UUID.randomUUID()),
        scheduledAt = Instant.parse("2026-09-01T08:00:00Z"),
        provenance = ScheduleMarkerProvenance.MATCHED_OCCURRENCE
    )
    private val intakeMarker = RecordedIntakeMarker(
        eventId = UUID.randomUUID(),
        occurredAt = Instant.parse("2026-09-01T08:05:00Z"),
        provenance = IntakeMarkerProvenance.MATCHED_INTAKE
    )

    @Test
    fun `each marker family maps to its own mandatory visible label`() {
        assertEquals(
            R.string.retrospective_marker_schedule_context,
            RetrospectivePresentation.markerLabelRes(scheduleMarker)
        )
        assertEquals(
            R.string.retrospective_marker_recorded_intake,
            RetrospectivePresentation.markerLabelRes(intakeMarker)
        )
    }

    @Test
    fun `marker counts are per-family and additive`() {
        val markers = listOf(scheduleMarker, scheduleMarker.copy(), intakeMarker)
        assertEquals(2, RetrospectivePresentation.scheduleMarkerCount(markers))
        assertEquals(1, RetrospectivePresentation.recordedIntakeCount(markers))
    }

    @Test
    fun `all four unavailable reasons map to four distinct frozen resources`() {
        val mapped = RetrospectivePkUnavailableReason.entries.map {
            RetrospectivePresentation.unavailableMessageRes(it)
        }
        assertEquals(4, mapped.size)
        assertEquals(4, mapped.toSet().size)
        assertEquals(
            R.string.retrospective_unavailable_no_eligible_intakes,
            RetrospectivePresentation.unavailableMessageRes(
                RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES
            )
        )
        assertEquals(
            R.string.retrospective_unavailable_invalid_interval,
            RetrospectivePresentation.unavailableMessageRes(
                RetrospectivePkUnavailableReason.INVALID_QUERY_INTERVAL
            )
        )
        assertEquals(
            R.string.retrospective_unavailable_history_unavailable,
            RetrospectivePresentation.unavailableMessageRes(
                RetrospectivePkUnavailableReason.HISTORICAL_INPUT_UNAVAILABLE
            )
        )
        assertEquals(
            "the defensive reason stays generic",
            R.string.retrospective_unavailable_generic,
            RetrospectivePresentation.unavailableMessageRes(
                RetrospectivePkUnavailableReason.QUERY_OUTSIDE_CALCULATED_INTERVAL
            )
        )
    }

    @Test
    fun `all four limitations map to four distinct neutral resources`() {
        val mapped = RetrospectivePkLimitation.entries.map {
            RetrospectivePresentation.limitationMessageRes(it)
        }
        assertEquals(4, mapped.size)
        assertEquals(4, mapped.toSet().size)
    }
}
