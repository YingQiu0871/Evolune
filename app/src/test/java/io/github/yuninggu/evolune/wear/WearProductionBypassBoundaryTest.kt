package io.github.yuninggu.evolune.wear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class WearProductionBypassBoundaryTest {
    @Test
    fun `Wear and composition production paths have no persistence bypass`() {
        val sourceRoot = Path.of("src/main/java")
        val forbidden = listOf(
            "AppDatabase.getDatabase",
            "doseEventDao(",
            "medicationPlanDao(",
            "scheduledDoseSlotDao(",
            "DoseEventEntity",
            "MedicationPlanEntity",
            "ScheduledDoseSlotEntity",
            "io.github.yuninggu.evolune.data.DoseEventRepository",
            "io.github.yuninggu.evolune.data.MedicationPlanRepository",
            "RoomDoseEventRepository(",
            "RoomMedicationPlanRepository("
        )
        Files.walk(sourceRoot).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }
                .filter { !it.toString().replace('\\', '/').contains("/data/") }
                .forEach { path ->
                    val content = Files.readString(path)
                    forbidden.forEach { token ->
                        assertFalse("$token in $path", content.contains(token))
                    }
                }
        }
    }

    @Test
    fun `Wear listener uses provider handler and controlled service lifecycle`() {
        val source = Files.readString(
            sourceRoot().resolve("io/github/yuninggu/evolune/wear/WearDataLayer.kt")
        )
        assertTrue(source.contains("ProductionRepositoryProvider"))
        assertTrue(source.contains("WearDoseActionHandler"))
        assertTrue(source.contains("SupervisorJob() + Dispatchers.IO"))
        assertTrue(source.contains("serviceScope.cancel()"))
        assertFalse(source.contains("LocalActionRecorder"))
        assertFalse(source.contains("RecordDoseEventEngine"))
        assertFalse(source.contains("runBlocking"))
        assertFalse(source.contains("GlobalScope"))
    }

    @Test
    fun `Wear action handler selects only WearActionRecorder`() {
        val source = Files.readString(
            sourceRoot().resolve(
                "io/github/yuninggu/evolune/application/WearDoseActionHandler.kt"
            )
        )
        assertTrue(source.contains("WearActionRecorder"))
        assertFalse(source.contains("LocalActionRecorder"))
        assertFalse(source.contains("ExistingEventPolicy"))
        assertFalse(source.contains("DoseEventEntity"))
        assertFalse(source.contains("doseEventDao"))
    }

    private fun sourceRoot(): Path = Path.of("src/main/java")
}
