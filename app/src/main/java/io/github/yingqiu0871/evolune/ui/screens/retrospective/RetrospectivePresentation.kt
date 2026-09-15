package io.github.yingqiu0871.evolune.ui.screens.retrospective

import androidx.annotation.StringRes
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkLimitation
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkUnavailableReason
import io.github.yingqiu0871.evolune.history.retrospective.RecordedIntakeMarker
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectiveMarker
import io.github.yingqiu0871.evolune.history.retrospective.ScheduleContextMarker

/**
 * V17-C-04 §9/§11/§14.10 — pure resource mapping for the retrospective surface.
 *
 * The screen renders only what this object resolves; JVM tests pin the mapping so a marker
 * family / unavailable reason / limitation can never silently point at another family's copy.
 * No forbidden relationship or adherence wording exists here — every string is a frozen
 * resource (contract §11/§12).
 */
internal object RetrospectivePresentation {

    /** The mandatory visible label of a marker's family (contract §11.1). */
    @StringRes
    fun markerLabelRes(marker: RetrospectiveMarker): Int = when (marker) {
        is ScheduleContextMarker -> R.string.retrospective_marker_schedule_context
        is RecordedIntakeMarker -> R.string.retrospective_marker_recorded_intake
    }

    fun scheduleMarkerCount(markers: List<RetrospectiveMarker>): Int =
        markers.count { it is ScheduleContextMarker }

    fun recordedIntakeCount(markers: List<RetrospectiveMarker>): Int =
        markers.count { it is RecordedIntakeMarker }

    /** Contract §D-11 frozen mapping; QUERY_OUTSIDE_CALCULATED_INTERVAL stays defensive-generic. */
    @StringRes
    fun unavailableMessageRes(reason: RetrospectivePkUnavailableReason): Int = when (reason) {
        RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES ->
            R.string.retrospective_unavailable_no_eligible_intakes

        RetrospectivePkUnavailableReason.INVALID_QUERY_INTERVAL ->
            R.string.retrospective_unavailable_invalid_interval

        RetrospectivePkUnavailableReason.QUERY_OUTSIDE_CALCULATED_INTERVAL ->
            R.string.retrospective_unavailable_generic

        RetrospectivePkUnavailableReason.HISTORICAL_INPUT_UNAVAILABLE ->
            R.string.retrospective_unavailable_history_unavailable
    }

    /** Neutral limitation copy only (contract §10): never an adherence/punctuality judgement. */
    @StringRes
    fun limitationMessageRes(limitation: RetrospectivePkLimitation): Int = when (limitation) {
        RetrospectivePkLimitation.EARLIEST_AVAILABLE_HISTORY_ZERO_BASELINE ->
            R.string.retrospective_limitation_zero_baseline

        RetrospectivePkLimitation.UNRECORDED_OCCURRENCES_PRESENT ->
            R.string.retrospective_limitation_unrecorded_occurrences

        RetrospectivePkLimitation.EXCLUDED_RECORDED_INTAKES ->
            R.string.retrospective_limitation_excluded_intakes

        RetrospectivePkLimitation.AMBIGUOUS_PATCH_PAIRING ->
            R.string.retrospective_limitation_ambiguous_patch
    }
}
