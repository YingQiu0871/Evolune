package io.github.yingqiu0871.evolune.history.insights

import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsAggregator
import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsSummary
import io.github.yingqiu0871.evolune.experience.insights.ReadOnlyMedicationInsightsAggregator
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Test clock whose instant and zone can be moved to drive rollover and timezone changes. */
internal class MutableTestClock(
    var instant: Instant,
    var zoneId: ZoneId
) : Clock() {

    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = MutableTestClock(instant, zone)

    override fun instant(): Instant = instant
}

/** Recording read seam: counts reads, records the exact arguments, and can gate/fail/ignore cancellation. */
internal class RecordingRangeSource(
    private val ignoreCancellation: Boolean = false
) : HistoryRangeSource {

    data class Call(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val displayZone: ZoneId,
        val now: Instant
    )

    val calls = mutableListOf<Call>()
    /** Ranges actually handed back, so tests can assert the aggregator saw the same instance. */
    val returned = mutableListOf<HistoricalRange>()
    var failure: Throwable? = null

    /** Fails only the given 1-based call index, so a stale request can fail after a newer one succeeded. */
    var failureForCall: Int? = null
    var gate: CompletableDeferred<Unit>? = null

    /** When set, only that 1-based call index waits on [gate]; other calls proceed immediately. */
    var gateOnlyForCall: Int? = null

    /** Produces the range for a call; the default is an empty range covering the request. */
    var result: (Call) -> HistoricalRange = { call ->
        HistoricalRange(startDate = call.startDate, endDate = call.endDate, days = emptyList())
    }

    override suspend fun read(
        startDate: LocalDate,
        endDate: LocalDate,
        displayZone: ZoneId,
        now: Instant
    ): HistoricalRange {
        val call = Call(startDate, endDate, displayZone, now)
        calls += call
        val callIndex = calls.size
        val gateApplies = gateOnlyForCall == null || gateOnlyForCall == callIndex
        if (gateApplies) {
            gate?.let { pending ->
                try {
                    pending.await()
                } catch (cancellation: CancellationException) {
                    if (!ignoreCancellation) throw cancellation
                }
            }
        }
        if (failureForCall == callIndex) throw IllegalStateException("stale failure")
        failure?.let { throw it }
        val range = result(call)
        returned += range
        return range
    }

    /** A range with a single day carrying no entry, so the summary has zero recorded intakes. */
    fun emptyDays(days: Int): (Call) -> HistoricalRange = { call ->
        HistoricalRange(
            startDate = call.startDate,
            endDate = call.endDate,
            days = (0 until days).map { offset ->
                HistoricalDay(date = call.startDate.plusDays(offset.toLong()), entries = emptyList())
            }
        )
    }
}

/**
 * Recording aggregator: counts calls and either delegates to the real B-01 aggregator (integration
 * mode) or returns a canned summary / throws.
 */
internal class RecordingAggregator(
    private val delegate: MedicationInsightsAggregator = ReadOnlyMedicationInsightsAggregator
) : MedicationInsightsAggregator {

    var calls = 0
        private set
    val receivedRanges = mutableListOf<HistoricalRange>()
    var failure: Throwable? = null
    var summaryOverride: MedicationInsightsSummary? = null

    override fun aggregate(range: HistoricalRange): MedicationInsightsSummary {
        calls += 1
        receivedRanges += range
        failure?.let { throw it }
        summaryOverride?.let { return it }
        return delegate.aggregate(range)
    }
}
