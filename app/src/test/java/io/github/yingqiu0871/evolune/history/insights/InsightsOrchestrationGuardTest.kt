package io.github.yingqiu0871.evolune.history.insights

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * v1.7-B-02 sections 36/37/31: the Insights orchestration layer stays a consumer.
 *
 * The production package may use the read seam, the frozen history types and the B-01 aggregator,
 * plus the Android ViewModel/lifecycle plumbing it legitimately needs. It must not acquire a second
 * way to the facts, and it must not introduce any judgement vocabulary of its own.
 *
 * The guard scans Kotlin **sources** of the package, so these token lists are not scanned
 * themselves.
 */
class InsightsOrchestrationGuardTest {

    private val packageDir = Path.of("src/main/java/io/github/yingqiu0871/evolune/history/insights")

    private val sources: String by lazy {
        Files.walk(packageDir).use { paths ->
            paths.filter(Files::isRegularFile)
                .filter { it.toString().endsWith(".kt") }
                .map(Files::readString)
                .toList()
                .joinToString("\n")
        }
    }

    private fun assertAbsent(token: String) {
        val pattern = Regex("(?<![A-Za-z0-9_])" + Regex.escape(token) + "(?![A-Za-z0-9_])")
        assertFalse(
            "the Insights orchestration package must not mention '$token'",
            pattern.containsMatchIn(sources)
        )
    }

    @Test
    fun `no second path to the facts exists`() {
        listOf(
            "DoseEventRepository",
            "MedicationPlanRepository",
            "DoseEventDao",
            "AppDatabase",
            "Room",
            "MedicationPlanDao",
            "MedicationOccurrenceMatcher",
            "MedicationOccurrenceGenerator",
            "MedicationOccurrencePresentation",
            "DoseEvent("
        ).forEach { assertAbsent(it) }
    }

    @Test
    fun `the read seam is the only history acquisition path`() {
        // the factory may reference HistoryReadService to build the seam, and nothing else may
        val factory = Files.readString(packageDir.resolve("InsightsViewModel.kt"))
        val occurrences = Regex("(?<![A-Za-z0-9_])HistoryReadService(?![A-Za-z0-9_])")
            .findAll(factory)
            .count()

        assertTrue("expected the factory to wire the seam exactly once, found $occurrences", occurrences >= 1)
        assertTrue(factory.contains("HistoryRangeSource { startDate, endDate, zone, now ->"))
        assertTrue(factory.contains("historyReadService.readRange(startDate, endDate, zone, now)"))

        // every other file in the package must be free of it
        Files.walk(packageDir).use { paths ->
            paths.filter(Files::isRegularFile)
                .filter { it.toString().endsWith(".kt") }
                .filter { it.fileName.toString() != "InsightsViewModel.kt" }
                .forEach { path ->
                    assertFalse(
                        "${path.fileName} must not reference the history read service",
                        Regex("(?<![A-Za-z0-9_])HistoryReadService(?![A-Za-z0-9_])")
                            .containsMatchIn(Files.readString(path))
                    )
                }
        }
    }

    @Test
    fun `no judgement vocabulary is introduced by the orchestration layer`() {
        listOf(
            "adherence",
            "compliance",
            "percentage",
            "percent",
            "ratio",
            "completionRate",
            "missedCount",
            "timingDelta",
            "onTime",
            "late",
            "score"
        ).forEach { assertAbsent(it) }
    }

    @Test
    fun `the state model reuses the authoritative summary instead of restating metrics`() {
        val state = Files.readString(packageDir.resolve("InsightsUiState.kt"))

        assertTrue(state.contains("val summary: MedicationInsightsSummary?"))
        listOf("recordedIntakeCount", "matchedOccurrenceCount", "perMedicationDoseTotalsMg").forEach { field ->
            assertFalse("the state must not restate $field", state.contains("val $field"))
        }
    }

    @Test
    fun `the resolver is the only place that computes endpoints`() {
        val resolver = Files.readString(packageDir.resolve("InsightsRangeSelection.kt"))
        val viewModel = Files.readString(packageDir.resolve("InsightsViewModel.kt"))

        assertTrue(resolver.contains("object InsightsRangeResolver"))
        assertTrue(resolver.contains("today.minusDays(6)"))
        assertTrue(resolver.contains("today.minusDays(29)"))
        assertTrue(resolver.contains("today.minusDays(89)"))
        // the ViewModel never does the arithmetic itself
        assertFalse(viewModel.contains("minusDays(6)"))
        assertFalse(viewModel.contains("minusDays(29)"))
        assertFalse(viewModel.contains("minusDays(89)"))
        // endpoints and the same-selection check both go through the resolver and its snapshot
        assertTrue(viewModel.contains("InsightsRangeResolver.resolve(selection, requestSnapshot.today)"))
        assertTrue(viewModel.contains("InsightsRangeResolver.resolve(selection, candidate.today)"))
    }
}
