package io.github.yingqiu0871.evolune.history.timeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * V17-D-03 §24 — focused architecture guards (F1–F16) over the D-03 orchestration production.
 *
 * A source reference to `HistoryRangeSource` is REQUIRED and allowed; referencing the closed
 * D-01 projector is required; everything else on the forbidden list must be absent.
 */
class TimelineRangeArchitectureGuardTest {

    private val productionFiles = listOf(
        "src/main/java/io/github/yingqiu0871/evolune/history/timeline/TimelineRangeState.kt",
        "src/main/java/io/github/yingqiu0871/evolune/history/timeline/TimelineRangeCoordinator.kt"
    )

    private val blockComment = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val lineComment = Regex("//[^\\n]*")

    private fun code(path: String): String = Files.readString(Path.of(path))
        .replace(blockComment, "")
        .replace(lineComment, "")

    @Test
    fun `F1 F3 F4 F6 the only read seam is HistoryRangeSource and no forbidden machinery exists`() {
        val all = productionFiles.joinToString("\n") { code(it) }
        assertTrue("the required seam reference must exist", all.contains("HistoryRangeSource"))

        val forbidden = listOf(
            // F3: never the concrete reader
            "HistoryReadService",
            // F1/F4: no new seam, matcher or generator
            "fun interface",
            "AllAvailableHistorySource",
            "MedicationOccurrenceMatcher",
            "MedicationOccurrenceGenerator",
            // F6: no Phase-C / PK coupling
            "history.retrospective",
            "RetrospectivePk",
            "SimulationEngine",
            "ThreeCompartmentModel",
            "ParameterResolver",
            "io.github.yingqiu0871.evolune.pk."
        )
        productionFiles.forEach { path ->
            val fileCode = code(path)
            forbidden.forEach { term ->
                assertFalse("$path must not reference '$term'", fileCode.contains(term))
            }
        }
    }

    @Test
    fun `F2 no repository DAO or Room references`() {
        val forbidden = listOf("DoseEventDao", "DoseEventRepository", "AppDatabase", "Room", "data.repository")
        productionFiles.forEach { path ->
            forbidden.forEach { term ->
                assertFalse("$path must not reference '$term'", code(path).contains(term))
            }
        }
    }

    @Test
    fun `F5 D-01 is consumed but never reimplemented or re-declared`() {
        productionFiles.forEach { path ->
            val fileCode = code(path)
            assertFalse("$path must not redeclare D-01 types", fileCode.contains("object TimelineProjectionBuilder"))
            assertFalse("$path must not redeclare D-01 types", fileCode.contains("data class TimelineReadModel"))
            assertFalse("$path must not redeclare D-01 types", fileCode.contains("data class TimelineRow"))
        }
        val coordinator = code(productionFiles[1])
        assertEqualsOneOccurrence(coordinator, "TimelineProjectionBuilder.build(")
    }

    @Test
    fun `F7 no future rows or future source reads are reachable`() {
        productionFiles.forEach { path ->
            val fileCode = code(path)
            assertFalse(fileCode.contains("futureOccurrences"))
            assertFalse(fileCode.contains("FutureOccurrence"))
            assertFalse("no arbitrary date arithmetic", fileCode.contains("plusDays("))
        }
    }

    @Test
    fun `F8 no timing or adherence semantics`() {
        val forbidden = listOf(
            "timingDelta", "onTime", "overdue", "missed", "skipped",
            "adherence", "compliance", "coverage", "Duration.between"
        )
        productionFiles.forEach { path ->
            forbidden.forEach { term ->
                assertFalse("$path must not contain '$term'", code(path).contains(term))
            }
        }
    }

    @Test
    fun `F9 F13 no writes polling or live subscriptions`() {
        val forbidden = listOf(
            "insert", "upsert", "delete", "persist", "@dao", "@entity", "@database",
            "delay(", "Timer(", "repeat(", "while (true)", "collect(", "kotlinx.coroutines.flow.Flow"
        )
        productionFiles.forEach { path ->
            val fileCode = code(path).lowercase()
            forbidden.forEach { term ->
                assertFalse("$path must not contain '$term'", fileCode.contains(term.lowercase()))
            }
        }
    }

    @Test
    fun `F10 F11 no UI navigation view model android or lifecycle references`() {
        val forbidden = listOf(
            "androidx.", "android.", "Compose", "Screen", "navigate", "ViewModel",
            "Lifecycle", "R.string"
        )
        productionFiles.forEach { path ->
            forbidden.forEach { term ->
                assertFalse("$path must not reference '$term'", code(path).contains(term))
            }
        }
    }

    @Test
    fun `F12 no localized or formatted strings`() {
        val cjk = Regex("[\\u4e00-\\u9fff]")
        productionFiles.forEach { path ->
            assertFalse("$path must not contain user-visible copy", cjk.containsMatchIn(code(path)))
        }
    }

    @Test
    fun `F14 no global or internally created coroutine scope`() {
        val forbidden = listOf("GlobalScope", "CoroutineScope(", "runBlocking")
        productionFiles.forEach { path ->
            forbidden.forEach { term ->
                assertFalse("$path must not contain '$term'", code(path).contains(term))
            }
        }
    }

    @Test
    fun `F15 cancellation is caught before any generic failure mapping`() {
        val coordinator = code(productionFiles[1])
        val cancellationIndex = coordinator.indexOf("catch (cancellation: CancellationException)")
        val genericIndex = coordinator.indexOf("catch (error: Throwable)")
        assertTrue(cancellationIndex >= 0)
        assertTrue(genericIndex >= 0)
        assertTrue("cancellation handling must precede generic handling", cancellationIndex < genericIndex)
        assertTrue("cancellation must be rethrown", coordinator.contains("throw cancellation"))
    }

    private fun assertEqualsOneOccurrence(source: String, needle: String) {
        var count = 0
        var index = source.indexOf(needle)
        while (index >= 0) {
            count++
            index = source.indexOf(needle, index + 1)
        }
        org.junit.Assert.assertEquals("'$needle' occurrence count", 1, count)
    }
}
