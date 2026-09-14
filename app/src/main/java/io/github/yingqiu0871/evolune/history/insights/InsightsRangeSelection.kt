package io.github.yingqiu0871.evolune.history.insights

import java.time.LocalDate
import java.time.YearMonth

/**
 * Explicit, typed range selection for the Insights surface (v1.7-B-00 section 19, frozen).
 *
 * The endpoints are never computed here: [InsightsRangeResolver] owns that, so neither the
 * ViewModel nor a future Composable can invent a different interpretation of "last 7 days".
 */
sealed interface InsightsRangeSelection {

    /** `today - 6 .. today` inclusive (exactly 7 dates). */
    data object Last7Days : InsightsRangeSelection

    /** `today - 29 .. today` inclusive (exactly 30 dates). */
    data object Last30Days : InsightsRangeSelection

    /** `today - 89 .. today` inclusive (exactly 90 dates). */
    data object Last90Days : InsightsRangeSelection

    /** First day of the current month .. today, inclusive. */
    data object CurrentMonth : InsightsRangeSelection

    /** Fixed endpoints; valid only while `startDate <= endDate <= today`. */
    data class Custom(val startDate: LocalDate, val endDate: LocalDate) : InsightsRangeSelection

    /** True for the presets whose endpoints follow `today`. */
    val isRelativeToToday: Boolean
        get() = this !is Custom

    companion object {
        /**
         * Product default for the first Insights view (v1.7-B-02 section 8).
         *
         * Deliberately a computed value. This interface declares a default method, so the JVM
         * initializes it before any implementor that is touched first; materializing the default in
         * the companion's static initializer reads `Last30Days.INSTANCE` while that object is still
         * being initialized and leaves `DEFAULT` permanently null in that order
         * (v1.7-B-03 crash fix; regression locked by `InsightsRangeSelectionInitTest`).
         */
        val DEFAULT: InsightsRangeSelection
            get() = Last30Days
    }
}

/** Why a selection cannot be turned into a range. */
enum class InsightsRangeValidationError {
    /** `startDate` lies after `endDate`. */
    START_AFTER_END,

    /** `endDate` lies after today: the future is not history. */
    END_IN_FUTURE
}

/** Result of resolving a selection against a concrete `today`. */
sealed interface InsightsRangeResolution {
    data class Resolved(val startDate: LocalDate, val endDate: LocalDate) : InsightsRangeResolution

    data class Invalid(val error: InsightsRangeValidationError) : InsightsRangeResolution
}

/**
 * The single place that turns a [InsightsRangeSelection] plus `today` into endpoints.
 *
 * Pure and deterministic: the same selection and `today` always produce the same resolution, and
 * no clock, zone or locale is consulted. Both endpoints are inclusive
 * (v1.7-B-00 section 19).
 */
object InsightsRangeResolver {

    fun resolve(selection: InsightsRangeSelection, today: LocalDate): InsightsRangeResolution =
        when (selection) {
            InsightsRangeSelection.Last7Days -> resolved(today.minusDays(6), today)
            InsightsRangeSelection.Last30Days -> resolved(today.minusDays(29), today)
            InsightsRangeSelection.Last90Days -> resolved(today.minusDays(89), today)
            InsightsRangeSelection.CurrentMonth -> resolved(YearMonth.from(today).atDay(1), today)
            is InsightsRangeSelection.Custom -> resolveCustom(selection, today)
        }

    private fun resolveCustom(
        selection: InsightsRangeSelection.Custom,
        today: LocalDate
    ): InsightsRangeResolution = when {
        selection.startDate.isAfter(selection.endDate) ->
            InsightsRangeResolution.Invalid(InsightsRangeValidationError.START_AFTER_END)

        selection.endDate.isAfter(today) ->
            InsightsRangeResolution.Invalid(InsightsRangeValidationError.END_IN_FUTURE)

        else -> resolved(selection.startDate, selection.endDate)
    }

    private fun resolved(startDate: LocalDate, endDate: LocalDate): InsightsRangeResolution =
        InsightsRangeResolution.Resolved(startDate = startDate, endDate = endDate)
}
