package io.github.yingqiu0871.evolune.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * B-03 ownership guard: the Room repository layer must not depend on the Wear
 * namespace, and the shared recent-dose policy must stay neutral and pure.
 */
class RoomRepositoryWearBoundaryTest {

    @Test
    fun `data repository sources never reference the Wear namespace`() {
        val repositoryRoot = Path.of("src/main/java/io/github/yingqiu0871/evolune/data/repository")
        Files.walk(repositoryRoot).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }
                .forEach { path ->
                    val content = Files.readString(path)
                    assertFalse(
                        "Wear namespace reference in $path",
                        content.contains("io.github.yingqiu0871.evolune.wear")
                    )
                }
        }
    }

    @Test
    fun `RoomDoseEventRepository uses the neutral recent dose policy`() {
        val source = Files.readString(
            Path.of("src/main/java/io/github/yingqiu0871/evolune/data/repository/RoomDoseEventRepository.kt")
        )
        assertTrue(source.contains("import io.github.yingqiu0871.evolune.core.dataapi.RecentRecordedDoseSelector"))
        assertTrue(source.contains("RecentRecordedDoseSelector.select("))
        assertFalse(source.contains("WearAppRecentDoseSelector"))
    }

    @Test
    fun `recent dose policy is pure and Wear-free`() {
        val source = Files.readString(
            Path.of("src/main/java/io/github/yingqiu0871/evolune/core/dataapi/RecentRecordedDoseSelector.kt")
        )
        assertTrue(source.contains("package io.github.yingqiu0871.evolune.core.dataapi"))
        val imports = source.lines().filter { it.startsWith("import ") }
        assertEquals(
            listOf(
                "import io.github.yingqiu0871.evolune.core.model.DoseEvent",
                "import io.github.yingqiu0871.evolune.core.model.DoseEventStatus"
            ),
            imports
        )
    }

    @Test
    fun `old Wear-owned selector symbol is gone from production sources`() {
        val sourceRoot = Path.of("src/main/java")
        Files.walk(sourceRoot).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }
                .forEach { path ->
                    assertFalse(
                        "WearAppRecentDoseSelector still present in $path",
                        Files.readString(path).contains("WearAppRecentDoseSelector")
                    )
                }
        }
    }
}
