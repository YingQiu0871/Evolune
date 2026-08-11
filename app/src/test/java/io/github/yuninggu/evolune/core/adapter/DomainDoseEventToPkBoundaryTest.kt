package io.github.yuninggu.evolune.core.adapter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DomainDoseEventToPkBoundaryTest {
    @Test
    fun `production has one formal event projection and no Batch 6 PK bridge`() {
        val sourceRoot = Path.of("src/main/java")
        val forbiddenBridgeNames = listOf(
            "Batch6HrtPkProjection",
            "Batch6MahiroJsonBridge",
            "toWidgetPkEvent",
            "toWidgetPkExtraKey"
        )
        val productionHits = mutableListOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths.filter { path -> Files.isRegularFile(path) }
                .forEach { path ->
                    val source = Files.readString(path)
                    forbiddenBridgeNames.forEach { token ->
                        if (source.contains(token)) {
                            productionHits += "$token:$path"
                        }
                    }
                }
        }
        assertTrue("unexpected production bridge references: $productionHits", productionHits.isEmpty())

        val hrtSource = Files.readString(
            Path.of("src/main/java/io/github/yuninggu/evolune/viewmodel/HRTViewModel.kt")
        )
        val widgetSource = Files.readString(
            Path.of("src/main/java/io/github/yuninggu/evolune/widget/WidgetWork.kt")
        )
        assertTrue(hrtSource.contains("DomainDoseEventToPkAdapter"))
        assertTrue(widgetSource.contains("DomainDoseEventToPkAdapter"))
        assertFalse(hrtSource.contains("PkDoseEvent("))
        assertFalse(widgetSource.contains("PkDoseEvent("))
        assertFalse(Files.exists(
            Path.of("src/main/java/io/github/yuninggu/evolune/application/Batch6DoseEventCompatibility.kt")
        ))
    }

    @Test
    fun `formal adapter is pure structural Kotlin boundary`() {
        val source = Files.readString(
            Path.of(
                "src/main/java/io/github/yuninggu/evolune/core/adapter/" +
                    "DomainDoseEventToPkAdapter.kt"
            )
        )
        listOf(
            "android.",
            "androidx.",
            "Repository",
            "Context",
            "SimulationEngine",
            "Widget",
            "Wear",
            "sorted",
            "filter",
            "UUID.randomUUID",
            "systemDefault"
        ).forEach { forbidden ->
            assertFalse("adapter contains $forbidden", source.contains(forbidden))
        }
    }
}
