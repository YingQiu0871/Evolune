package io.github.yingqiu0871.evolune.history.retrospective

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * V17-C-04 §14.11 regression and §13 forbidden surfaces — static architecture guards for the new
 * C-04 production scope.
 *
 * The new retrospective surfaces may consume only the approved seams. They must not reach into
 * repositories/DAOs/Room, the concrete history reader, the C-01 extractor, parameter resolution,
 * the simulation engine, the live chart, or any write path.
 */
class RetrospectiveArchitectureGuardTest {

    private val newProductionFiles = listOf(
        "src/main/java/io/github/yingqiu0871/evolune/history/retrospective/RetrospectiveMarkerModels.kt",
        "src/main/java/io/github/yingqiu0871/evolune/history/retrospective/RetrospectivePkUiState.kt",
        "src/main/java/io/github/yingqiu0871/evolune/history/retrospective/RetrospectivePkSurfaceCoordinator.kt",
        "src/main/java/io/github/yingqiu0871/evolune/history/retrospective/RetrospectivePkViewModel.kt",
        "src/main/java/io/github/yingqiu0871/evolune/ui/screens/retrospective/RetrospectivePresentation.kt",
        "src/main/java/io/github/yingqiu0871/evolune/ui/screens/retrospective/RetrospectiveSurfaceLifecycle.kt",
        "src/main/java/io/github/yingqiu0871/evolune/ui/screens/retrospective/RetrospectiveConcentrationChart.kt",
        "src/main/java/io/github/yingqiu0871/evolune/ui/screens/retrospective/RetrospectivePkScreen.kt"
    )

    private val blockComment = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val lineComment = Regex("//[^\\n]*")

    private fun source(path: String): String = Files.readString(Path.of(path))

    private fun code(path: String): String = source(path)
        .replace(blockComment, "")
        .replace(lineComment, "")

    @Test
    fun `no new C-04 production file touches forbidden infrastructure`() {
        val forbidden = listOf(
            "DoseEventRepository",
            "DoseEventDao",
            "AppDatabase",
            "MedicationPlanRepository",
            "HistoryReadService",
            "RetrospectivePkExtractor",
            "ParameterResolver",
            "SimulationEngine",
            "ThreeCompartmentModel",
            "PKParameters",
            "import io.github.yingqiu0871.evolune.pk.",
            "import io.github.yingqiu0871.evolune.data.repository"
        )
        newProductionFiles.forEach { path ->
            val fileCode = code(path)
            forbidden.forEach { term ->
                assertFalse("$path must not reference '$term'", fileCode.contains(term))
            }
            assertFalse(
                "$path must not call the live chart (RetrospectiveConcentrationChart is the thin component)",
                Regex("(?<!Retrospective)ConcentrationChart\\(").containsMatchIn(fileCode)
            )
        }
    }

    @Test
    fun `no new C-04 production file performs or declares writes`() {
        val forbidden = listOf(
            "insert", "upsert", "delete", "persist", "@Dao", "@Entity", "@Database",
            "SharedPreferences", "DataStore"
        )
        newProductionFiles.forEach { path ->
            val fileCode = code(path).lowercase()
            forbidden.forEach { term ->
                assertFalse(
                    "$path must stay on the read-only seam boundary ('$term')",
                    fileCode.contains(term.lowercase())
                )
            }
        }
    }

    @Test
    fun `the single approved identity classifier is reused and no second classifier exists`() {
        val coordinator = source(newProductionFiles[2])
        assertTrue(
            "the coordinator must reuse the B-00 classifier",
            coordinator.contains("import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityClassifier")
        )
        newProductionFiles.forEach { path ->
            val fileCode = code(path)
            assertFalse(
                "$path must not declare a second classifier",
                Regex("(class|object)\\s+\\w*Classifier").containsMatchIn(fileCode)
            )
            assertFalse(
                "$path must not use a foreign classifier",
                fileCode.contains("IdentityClassifier") &&
                    !fileCode.contains("MedicationIdentityClassifier")
            )
        }
    }

    @Test
    fun `the composition-root-only concrete reader stays at the composition root`() {
        // MainFeatureServices owns the single wiring point; the C-04 new surfaces never mention it.
        val main = code("src/main/java/io/github/yingqiu0871/evolune/MainActivity.kt")
        assertTrue(main.contains("MainFeatureServices.create("))
        assertFalse(main.contains("HistoryReadService("))

        newProductionFiles.forEach { path ->
            assertFalse(
                "$path must not consume the concrete history reader",
                code(path).contains("HistoryReadService")
            )
        }
    }

    @Test
    fun `read discipline guards - no live clock, no timer, no polling in the new surfaces`() {
        val forbidden = listOf(
            "System.currentTimeMillis",
            "delay(",
            "repeat(",
            "while (true)",
            "Timer("
        )
        newProductionFiles.forEach { path ->
            val fileCode = code(path)
            forbidden.forEach { term ->
                assertFalse("$path must not contain '$term'", fileCode.contains(term))
            }
        }
    }

    @Test
    fun `the ui state and marker models expose no judgement fields`() {
        val forbidden = listOf(
            "adherence", "compliance", "missed", "skipped", "late", "ontime", "on_time",
            "punctual", "punctuality", "delta", "confidence", "score"
        )

        val classes = listOf(
            RetrospectivePkUiState::class.java,
            ScheduleContextMarker::class.java,
            RecordedIntakeMarker::class.java,
            ScheduleMarkerProvenance::class.java,
            IntakeMarkerProvenance::class.java,
            RetrospectivePhase::class.java,
            RetrospectiveLoadFailure::class.java
        )
        classes.forEach { type ->
            val names = mutableListOf<String>()
            type.declaredFields.forEach { names += it.name }
            type.enumConstantsOrNull()?.forEach { names += it.toString() }
            names.forEach { field ->
                forbidden.forEach { term ->
                    assertFalse(
                        "${type.simpleName}.$field must not expose judgement semantics",
                        field.lowercase().contains(term)
                    )
                }
            }
        }
    }

    @Test
    fun `the stale-result guard pattern is present in the view model`() {
        val viewModel = source(newProductionFiles[3])
        assertTrue(viewModel.contains("private fun publish("))
        assertTrue(viewModel.contains("if (token != generation) return"))
        assertTrue(viewModel.contains("val token = ++generation"))
        assertTrue(viewModel.contains("loadJob?.cancel()"))
        assertTrue(viewModel.contains("if (token == generation) runPendingRefresh()"))
    }

    private fun Class<*>.enumConstantsOrNull(): List<Any>? = enumConstants?.toList()
}
