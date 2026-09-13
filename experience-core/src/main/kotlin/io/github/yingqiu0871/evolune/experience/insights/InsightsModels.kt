package io.github.yingqiu0871.evolune.experience.insights

import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import java.time.LocalDate

/**
 * Stable, non-localized identity of a medication whose identity is provable from a frozen
 * historical fact (v1.7-B-00 section 13.1).
 *
 * The values mirror the production medication key vocabulary that the plan/event mapper derives
 * from the ester slot. A key outside this set is never invented: it degrades to
 * [MedicationIdentityStatus.PARTIAL] (v1.7-B-00 section 16).
 */
enum class MedicationIdentityKey {
    E2,
    EB,
    EV,
    EC,
    EN
}

/** How well the medication identity behind an authoritative intake is provable. */
enum class MedicationIdentityStatus {
    /** The route is not an anti-androgen route and the medication key is a known one. */
    KNOWN,

    /** The medication key is unknown/foreign/unmappable: never rendered as a drug name. */
    PARTIAL,

    /**
     * The authoritative drug cannot be recovered from the frozen projection (anti-androgen routes
     * carry the ester slot as a placeholder): never guessed, never mapped to an ester.
     */
    UNAVAILABLE
}

/** Outcome of [MedicationIdentityClassifier]: [key] is non-null exactly for [KNOWN]. */
data class MedicationIdentity(
    val status: MedicationIdentityStatus,
    val key: MedicationIdentityKey? = null
) {
    init {
        require((status == MedicationIdentityStatus.KNOWN) == (key != null)) {
            "a known medication identity must carry a key and no other status may carry one"
        }
    }
}

/**
 * Binding confidence of a **recorded intake** (v1.7-B-00 sections 4 and 25).
 *
 * This is projection-linking confidence, never historical prescription authenticity, and it never
 * changes the fact that an authoritative intake happened.
 */
enum class InsightsBindingConfidence {
    HIGH,
    MEDIUM,
    LOW,
    NONE
}

/**
 * Read-only, deterministic aggregate of one [io.github.yingqiu0871.evolune.experience.HistoricalRange]
 * (v1.7-B-00 section 17: factual metrics only).
 *
 * Only counts, distributions and per-medication dose totals are exposed: the model deliberately
 * defines no derived judgement field by contract (v1.7-B-00 sections 12 and 18).
 *
 * Every map is complete: [sourceCounts] always carries all [MedicationIntakeSource] values and
 * [bindingConfidenceCounts] always carries all [InsightsBindingConfidence] values, including zeros,
 * so a caller never has to interpret a missing key.
 */
data class MedicationInsightsSummary(
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** Authoritative recorded intakes: matched entries plus unmatched actual intakes. */
    val recordedIntakeCount: Int,
    /** Distinct display dates carrying at least one recorded intake. */
    val recordedDayCount: Int,
    /** Every matched occurrence, regardless of binding confidence (projection-linking count). */
    val matchedOccurrenceCount: Int,
    /** Occurrences for which no matching intake record exists in the available data. */
    val unrecordedOccurrenceCount: Int,
    /** Authoritative intakes that no occurrence claimed. */
    val unmatchedActualIntakeCount: Int,
    val sourceCounts: Map<MedicationIntakeSource, Int>,
    val bindingConfidenceCounts: Map<InsightsBindingConfidence, Int>,
    /** Per-medication dose totals in mg; known identities only (v1.7-B-00 section 13.2). */
    val perMedicationDoseTotalsMg: Map<MedicationIdentityKey, Double>,
    /** Recorded intakes whose medication identity is not provable (count only, never a dose). */
    val unknownIdentityRecordedIntakeCount: Int,
    /** True when any in-range entry carries a display date derived from the current time zone. */
    val containsCurrentTimezoneDerivedDates: Boolean
) {
    init {
        require(!endDate.isBefore(startDate)) {
            "insights range end $endDate must not be before start $startDate"
        }
        require(recordedIntakeCount >= 0 && recordedDayCount >= 0) {
            "recorded counts must not be negative"
        }
        require(matchedOccurrenceCount >= 0 && unrecordedOccurrenceCount >= 0) {
            "occurrence counts must not be negative"
        }
        require(unmatchedActualIntakeCount >= 0 && unknownIdentityRecordedIntakeCount >= 0) {
            "intake counts must not be negative"
        }
        require(recordedIntakeCount == matchedOccurrenceCount + unmatchedActualIntakeCount) {
            "recorded intakes must equal matched plus unmatched actual intakes"
        }
        require(recordedDayCount <= recordedIntakeCount) {
            "recorded days can never exceed recorded intakes"
        }
        require(unknownIdentityRecordedIntakeCount <= recordedIntakeCount) {
            "unknown-identity intakes can never exceed recorded intakes"
        }
        require(sourceCounts.keys == MedicationIntakeSource.entries.toSet()) {
            "source counts must cover every authoritative source exactly once"
        }
        require(sourceCounts.values.all { it >= 0 }) { "source counts must not be negative" }
        require(sourceCounts.values.sum() == recordedIntakeCount) {
            "source counts must sum to the recorded intake count"
        }
        require(bindingConfidenceCounts.keys == InsightsBindingConfidence.entries.toSet()) {
            "binding confidence counts must cover every confidence exactly once"
        }
        require(bindingConfidenceCounts.values.all { it >= 0 }) {
            "binding confidence counts must not be negative"
        }
        require(bindingConfidenceCounts.values.sum() == recordedIntakeCount) {
            "binding confidence counts must sum to the recorded intake count"
        }
        require(
            matchedOccurrenceCount ==
                bindingConfidenceCounts.getValue(InsightsBindingConfidence.HIGH) +
                bindingConfidenceCounts.getValue(InsightsBindingConfidence.MEDIUM) +
                bindingConfidenceCounts.getValue(InsightsBindingConfidence.LOW)
        ) {
            "matched occurrences must be exactly the high/medium/low confidence intakes"
        }
        require(bindingConfidenceCounts.getValue(InsightsBindingConfidence.NONE) == unmatchedActualIntakeCount) {
            "no-binding confidence may only come from unmatched actual intakes"
        }
        require(perMedicationDoseTotalsMg.values.all { it.isFinite() && it >= 0.0 }) {
            "dose totals must be finite and non-negative"
        }
    }
}
