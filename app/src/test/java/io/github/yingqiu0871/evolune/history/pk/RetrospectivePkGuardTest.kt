package io.github.yingqiu0871.evolune.history.pk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * V17-C-01 §13.6: the retrospective package consumes `AllAvailableHistorySource` only.
 *
 * Scope is exactly the newly added `history/pk` production package. The guard
 * deliberately does not scan `HistoryReadService` or the repository/data implementation, where
 * generator/projection/DAO tokens are legitimately required.
 *
 * The scan is non-vacuous: the approved seam token must be present.
 */
class RetrospectivePkGuardTest {

    private val packageDir = Path.of("src/main/java/io/github/yingqiu0871/evolune/history/pk")

    private val sources: String by lazy {
        Files.walk(packageDir).use { paths ->
            paths.filter(Files::isRegularFile)
                .filter { it.toString().endsWith(".kt") }
                .map { stripComments(Files.readString(it)) }
                .toList()
                .joinToString("\n")
        }
    }

    /**
     * Comment-only mentions of the forbidden tokens are permitted ("do not use ..."), so the
     * scan runs over code with block and line comments removed.
     */
    private fun stripComments(text: String): String =
        text
            .replace(Regex("/" + "\\*[\\s\\S]*?\\*/"), " ")
            .replace(Regex("//[^\\n]*"), " ")

    private fun assertAbsent(token: String) {
        val pattern = Regex("(?<![A-Za-z0-9_])" + Regex.escape(token) + "(?![A-Za-z0-9_])")
        assertFalse(
            "the retrospective PK package must not mention '$token'",
            pattern.containsMatchIn(sources)
        )
    }

    @Test
    fun `the retrospective package has no second path to the facts`() {
        listOf(
            "DoseEventRepository",
            "DoseEventDao",
            "AppDatabase",
            "Entity",
            "findAllOccurredUpTo",
            "getEventsForPk",
            "MedicationPlanPredictor",
            "MedicationOccurrenceGenerator",
            "HistoricalProjectionBuilder",
            "MedicationOccurrenceMatcher",
            "withTransaction",
            "insert(",
            "update(",
            "delete",
            "save(",
            "setEnabled("
        ).forEach { assertAbsent(it) }
    }

    @Test
    fun `the retrospective package has no clock, zone, clamp or math shortcut leakage`() {
        listOf(
            "System.currentTimeMillis",
            "SystemClock",
            "ZoneId.systemDefault",
            "ConcentrationChart",
            "calculateVisibleWindowYScale",
            "SimulationResult.concentration",
            "coerceIn("
        ).forEach { assertAbsent(it) }
    }

    @Test
    fun `the approved seam token is present and the scan is non-vacuous`() {
        assertTrue("expected at least one scanned source file", sources.isNotBlank())
        assertTrue(
            "AllAvailableHistorySource must occur in the retrospective package",
            sources.contains("AllAvailableHistorySource")
        )
    }
}
