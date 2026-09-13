package io.github.yingqiu0871.evolune.experience.insights

import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.HistoricalEntry
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchKey
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import java.time.LocalDate
import java.util.UUID

/**
 * Raised when the input range violates a frozen contract instead of being silently repaired.
 *
 * The aggregator never hides an upstream contract violation: a duplicated authoritative event, a
 * day carrying a foreign entry, or read-model counts that disagree with the entries themselves all
 * fail fast (v1.7-B-01 duplicate policy and range/day invariants).
 */
class InsightsContractViolationException(message: String) : IllegalStateException(message)

/**
 * Read-only aggregation of a frozen [HistoricalRange] into a [MedicationInsightsSummary].
 *
 * Contract (v1.7-B-00 / v1.7-B-01):
 * - consumes only frozen history values; it never obtains history itself, never consults a
 *   persistence layer, never re-runs occurrence matching, never looks at the current plan, and
 *   never re-derives a display date, provenance or identity from instants, sources or schedule
 *   times;
 * - synchronous, pure and deterministic: no clock, no time-zone lookup, no system locale, no
 *   shared mutable state, no caching;
 * - linear in the number of entries.
 */
interface MedicationInsightsAggregator {
    fun aggregate(range: HistoricalRange): MedicationInsightsSummary
}

/** The single production implementation of [MedicationInsightsAggregator]. */
object ReadOnlyMedicationInsightsAggregator : MedicationInsightsAggregator {

    override fun aggregate(range: HistoricalRange): MedicationInsightsSummary =
        Aggregation(range).run()

    private class Aggregation(private val range: HistoricalRange) {

        private val seenEventIds = HashSet<UUID>()
        private val recordedDays = HashSet<LocalDate>()
        private val sourceCounts = LinkedHashMap<MedicationIntakeSource, Int>()
        private val confidenceCounts = LinkedHashMap<InsightsBindingConfidence, Int>()
        private val doseTotals = LinkedHashMap<MedicationIdentityKey, Double>()

        private var recordedIntakes = 0
        private var matchedOccurrences = 0
        private var unrecordedOccurrences = 0
        private var unmatchedActualIntakes = 0
        private var unknownIdentityIntakes = 0
        private var containsCurrentTimezoneDerivedDates = false

        fun run(): MedicationInsightsSummary {
            verifyRangeIntegrity()
            range.days.forEach { day -> consume(day) }
            return MedicationInsightsSummary(
                startDate = range.startDate,
                endDate = range.endDate,
                recordedIntakeCount = recordedIntakes,
                recordedDayCount = recordedDays.size,
                matchedOccurrenceCount = matchedOccurrences,
                unrecordedOccurrenceCount = unrecordedOccurrences,
                unmatchedActualIntakeCount = unmatchedActualIntakes,
                sourceCounts = canonicalSources(),
                bindingConfidenceCounts = canonicalConfidence(),
                perMedicationDoseTotalsMg = doseTotals.toMap(),
                unknownIdentityRecordedIntakeCount = unknownIdentityIntakes,
                containsCurrentTimezoneDerivedDates = containsCurrentTimezoneDerivedDates
            )
        }

        // ---------- input integrity ----------

        private fun verifyRangeIntegrity() {
            if (range.endDate.isBefore(range.startDate)) {
                throw InsightsContractViolationException(
                    "insights range end ${range.endDate} is before start ${range.startDate}"
                )
            }
            val dates = HashSet<LocalDate>()
            range.days.forEach { day ->
                if (!dates.add(day.date)) {
                    throw InsightsContractViolationException("duplicate history day ${day.date}")
                }
                if (day.date.isBefore(range.startDate) || day.date.isAfter(range.endDate)) {
                    throw InsightsContractViolationException(
                        "history day ${day.date} lies outside the range ${range.startDate}..${range.endDate}"
                    )
                }
            }
        }

        private fun consume(day: HistoricalDay) {
            var recorded = 0
            var unrecorded = 0
            var unmatched = 0
            day.entries.forEach { entry ->
                if (entry.displayDate != day.date) {
                    throw InsightsContractViolationException(
                        "entry display date ${entry.displayDate} does not match its history day ${day.date}"
                    )
                }
                if (entry.displayDateProvenance == HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED) {
                    containsCurrentTimezoneDerivedDates = true
                }
                when (entry) {
                    is MatchedHistoricalOccurrence -> {
                        countAuthoritativeEvent(entry.event.eventId, entry.event.source)
                        matchedOccurrences += 1
                        addConfidence(confidenceOf(entry.matchProvenance))
                        consumeIdentityAndDose(entry.event.matchKey)
                        recordedDays += entry.displayDate
                        recorded += 1
                    }

                    is UnmatchedHistoricalIntake -> {
                        countAuthoritativeEvent(entry.event.eventId, entry.source)
                        unmatchedActualIntakes += 1
                        addConfidence(InsightsBindingConfidence.NONE)
                        consumeIdentityAndDose(entry.event.matchKey)
                        recordedDays += entry.displayDate
                        unmatched += 1
                    }

                    is UnrecordedHistoricalOccurrence -> {
                        unrecordedOccurrences += 1
                        unrecorded += 1
                    }
                }
            }
            verifyDayCounts(day, recorded, unrecorded, unmatched)
        }

        /** The read model must agree with its own entries; otherwise the aggregate would be built on a corrupted history. */
        private fun verifyDayCounts(
            day: HistoricalDay,
            recorded: Int,
            unrecorded: Int,
            unmatched: Int
        ) {
            if (day.recordedCount != recorded ||
                day.unrecordedCount != unrecorded ||
                day.unmatchedActualCount != unmatched
            ) {
                throw InsightsContractViolationException(
                    "history day ${day.date} counts disagree with its entries " +
                        "(day=${day.recordedCount}/${day.unrecordedCount}/${day.unmatchedActualCount}, " +
                        "entries=$recorded/$unrecorded/$unmatched)"
                )
            }
        }

        // ---------- counting ----------

        private fun countAuthoritativeEvent(eventId: UUID, source: MedicationIntakeSource) {
            if (!seenEventIds.add(eventId)) {
                throw InsightsContractViolationException(
                    "authoritative event $eventId appears more than once in the range"
                )
            }
            recordedIntakes += 1
            sourceCounts[source] = (sourceCounts[source] ?: 0) + 1
        }

        private fun addConfidence(confidence: InsightsBindingConfidence) {
            confidenceCounts[confidence] = (confidenceCounts[confidence] ?: 0) + 1
        }

        /** Identity and dose come from the authoritative event key, never from the generated schedule. */
        private fun consumeIdentityAndDose(matchKey: MedicationMatchKey) {
            val identity = MedicationIdentityClassifier.classify(matchKey)
            val key = identity.key
            if (key == null) {
                unknownIdentityIntakes += 1
                return
            }
            doseTotals[key] = (doseTotals[key] ?: 0.0) + matchKey.doseAmount
        }

        private fun confidenceOf(provenance: MedicationMatchProvenance): InsightsBindingConfidence =
            when (provenance) {
                MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE -> InsightsBindingConfidence.HIGH
                MedicationMatchProvenance.SLOT_WINDOW_WITHOUT_LOCAL_DATE -> InsightsBindingConfidence.MEDIUM
                MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW -> InsightsBindingConfidence.MEDIUM
                MedicationMatchProvenance.NULL_SLOT_SAME_DAY -> InsightsBindingConfidence.LOW
            }

        private fun canonicalSources(): Map<MedicationIntakeSource, Int> =
            MedicationIntakeSource.entries.associateWith { sourceCounts[it] ?: 0 }

        private fun canonicalConfidence(): Map<InsightsBindingConfidence, Int> =
            InsightsBindingConfidence.entries.associateWith { confidenceCounts[it] ?: 0 }
    }
}

/** Convenience for callers that only need the frozen aggregation behaviour. */
fun HistoricalRange.toInsightsSummary(): MedicationInsightsSummary =
    ReadOnlyMedicationInsightsAggregator.aggregate(this)
