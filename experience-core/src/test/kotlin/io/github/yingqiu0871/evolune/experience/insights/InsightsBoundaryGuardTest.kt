package io.github.yingqiu0871.evolune.experience.insights

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * v1.7-B-01 architecture guards.
 *
 * The Insights domain must stay a pure consumer of frozen history: no persistence, no transport,
 * no Android/UI toolkit, no second matching implementation, and no ratio/ranking/clinical
 * judgement surface. These guards read the production sources so the boundary is machine-checked
 * instead of merely documented.
 */
class InsightsBoundaryGuardTest {

    private val productionSources: String by lazy {
        val root = Path.of("src/main/kotlin/io/github/yingqiu0871/evolune/experience/insights")
        Files.walk(root).use { paths ->
            paths.filter(Files::isRegularFile)
                .filter { it.toString().endsWith(".kt") }
                .map(Files::readString)
                .toList()
                .joinToString("\n")
        }
    }

    /** Matches whole identifiers/words so unrelated words such as "related" cannot trip the guard. */
    private fun assertAbsent(token: String) {
        val pattern = Regex("(?<![A-Za-z0-9_])" + Regex.escape(token) + "(?![A-Za-z0-9_])")
        assertFalse(
            "the Insights production package must not mention '$token'",
            pattern.containsMatchIn(productionSources)
        )
    }

    @Test
    fun `no forbidden metric or judgement vocabulary exists in production insights code`() {
        listOf(
            "adherence",
            "compliance",
            "completionRate",
            "completion",
            "coverageRatio",
            "percentage",
            "percent",
            "ratio",
            "score",
            "missed",
            "skipped",
            "punctuality",
            "onTime",
            "overdue",
            "timingDelta",
            "delay"
        ).forEach { assertAbsent(it) }
    }

    @Test
    fun `no persistence transport or ui dependency is imported`() {
        listOf(
            "import android.",
            "import androidx.",
            "Compose",
            "Room",
            "Dao",
            "Entity",
            "Repository",
            "DataClient",
            "SharedPreferences"
        ).forEach { assertAbsent(it) }
    }

    @Test
    fun `no second truth derivation is reachable from the aggregator`() {
        listOf(
            // date re-derivation
            "atZone",
            "toLocalDate()",
            "systemDefault",
            "ZoneId.systemDefault",
            // time / locale / randomness would break determinism
            "Clock",
            "Instant.now",
            "LocalDate.now",
            "Locale",
            "Random",
            // second matching implementation / history acquisition
            "Matcher",
            "Generator",
            "HistoryReadService",
            "OccurrenceGenerator",
            "FutureOccurrenceContext"
        ).forEach { assertAbsent(it) }
    }

    @Test
    fun `the aggregator consumes only frozen history entry types`() {
        val aggregator = Files.readString(
            Path.of("src/main/kotlin/io/github/yingqiu0871/evolune/experience/insights/MedicationInsightsAggregator.kt")
        )

        assertTrue(aggregator.contains("fun aggregate(range: HistoricalRange): MedicationInsightsSummary"))
        listOf(
            "is MatchedHistoricalOccurrence",
            "is UnmatchedHistoricalIntake",
            "is UnrecordedHistoricalOccurrence"
        ).forEach { assertTrue("missing branch for $it", aggregator.contains(it)) }
    }

    @Test
    fun `the summary model exposes counts and distributions but no derived judgement field`() {
        val models = Files.readString(
            Path.of("src/main/kotlin/io/github/yingqiu0871/evolune/experience/insights/InsightsModels.kt")
        )

        listOf(
            "val recordedIntakeCount",
            "val recordedDayCount",
            "val matchedOccurrenceCount",
            "val unrecordedOccurrenceCount",
            "val unmatchedActualIntakeCount",
            "val sourceCounts",
            "val bindingConfidenceCounts",
            "val perMedicationDoseTotalsMg",
            "val unknownIdentityRecordedIntakeCount",
            "val containsCurrentTimezoneDerivedDates"
        ).forEach { assertTrue("missing field $it", models.contains(it)) }
    }
}
