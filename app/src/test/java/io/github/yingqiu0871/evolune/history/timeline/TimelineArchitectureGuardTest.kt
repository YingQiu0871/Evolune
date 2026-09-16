package io.github.yingqiu0871.evolune.history.timeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * V17-D-01 §19 — static architecture guards for the new `history/timeline/` production package.
 *
 * The read model is a pure derived projection: it must not reach into persistence, the history
 * read services/seams, matchers or occurrence generation, Phase C (retrospective) surfaces, PK
 * logic, Android/Compose/navigation/resources, timing metrics, or any write path. Domain types
 * such as `MedicationOccurrence` remain allowed; the public generator is allowed in TESTS only
 * (TLM18 behavioral parity) and must never be referenced by production.
 */
class TimelineArchitectureGuardTest {

    private val productionFiles = listOf(
        "src/main/java/io/github/yingqiu0871/evolune/history/timeline/TimelineReadModel.kt",
        "src/main/java/io/github/yingqiu0871/evolune/history/timeline/TimelineProjectionBuilder.kt"
    )

    private val blockComment = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val lineComment = Regex("//[^\\n]*")

    private fun code(path: String): String = Files.readString(Path.of(path))
        .replace(blockComment, "")
        .replace(lineComment, "")

    @Test
    fun `production files exist and reference only approved domain types`() {
        productionFiles.forEach { path ->
            assertTrue(Files.isRegularFile(Path.of(path)))
        }
        val all = productionFiles.joinToString("\n") { code(it) }
        assertTrue("the projection consumes HistoricalRange", all.contains("HistoricalRange"))
        assertTrue("the projection consumes domain occurrences", all.contains("MedicationOccurrence"))
        assertTrue(
            "identity classification must reuse the approved classifier",
            all.contains("MedicationIdentityClassifier")
        )
        assertFalse("planName must never be used by the read model", all.contains("planName"))
    }

    @Test
    fun `no persistence, reader, matcher or generator references in production`() {
        val forbidden = listOf(
            "DoseEventDao",
            "DoseEventRepository",
            "AppDatabase",
            "Room",
            "HistoryReadService",
            "HistoryRangeSource",
            "AllAvailableHistorySource",
            "MedicationOccurrenceMatcher",
            "MedicationOccurrenceGenerator",
            "HistoryViewModel",
            "HistoryPresentation"
        )
        productionFiles.forEach { path ->
            val fileCode = code(path)
            forbidden.forEach { term ->
                assertFalse("$path must not reference '$term'", fileCode.contains(term))
            }
        }
    }

    @Test
    fun `no Phase-C, PK, Android, Compose or navigation references in production`() {
        val forbidden = listOf(
            "history.retrospective",
            "RetrospectivePk",
            "RetrospectiveMarker",
            "SimulationEngine",
            "ThreeCompartmentModel",
            "ParameterResolver",
            "io.github.yingqiu0871.evolune.pk",
            "androidx.",
            "import android.content",
            "io.github.yingqiu0871.evolune.navigation",
            "R.string"
        )
        productionFiles.forEach { path ->
            val fileCode = code(path)
            forbidden.forEach { term ->
                assertFalse("$path must not reference '$term'", fileCode.contains(term))
            }
        }
    }

    @Test
    fun `no timing metrics, clocks, coroutines or state in production`() {
        val forbidden = listOf(
            "Duration.between",
            "timingDelta",
            "System.currentTimeMillis",
            "Clock",
            "ViewModel",
            "Flow",
            "CoroutineScope",
            "suspend ",
            "delay(",
            "adherence",
            "compliance",
            "missed",
            "skipped",
            "onTime",
            "late",
            "overdue"
        )
        productionFiles.forEach { path ->
            val fileCode = code(path)
            forbidden.forEach { term ->
                assertFalse("$path must not contain '$term'", fileCode.contains(term))
            }
        }
    }

    @Test
    fun `no write path exists in production`() {
        val writeVocabulary = listOf(
            "insert", "upsert", "delete", "persist", "@Dao", "@Entity", "@Database", "write"
        )
        productionFiles.forEach { path ->
            val fileCode = code(path).lowercase()
            writeVocabulary.forEach { term ->
                assertFalse("$path must not contain write vocabulary '$term'", fileCode.contains(term.lowercase()))
            }
        }
    }

    @Test
    fun `the public generator is referenced only from the TLM18 parity test`() {
        val testFile = "src/test/java/io/github/yingqiu0871/evolune/history/timeline/TimelineOrderingGeneratorParityTest.kt"
        assertTrue(Files.readString(Path.of(testFile)).contains("MedicationOccurrenceGenerator"))
        productionFiles.forEach { path ->
            assertFalse(code(path).contains("MedicationOccurrenceGenerator"))
        }
    }
}
