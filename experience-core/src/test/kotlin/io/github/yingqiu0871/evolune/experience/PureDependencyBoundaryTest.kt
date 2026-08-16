package io.github.yingqiu0871.evolune.experience

import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class PureDependencyBoundaryTest {
    @Test
    fun `production primitives have no Android UI persistence or transport coupling`() {
        val source = Files.walk(Path.of("src/main/kotlin")).use { paths ->
            paths.filter(Files::isRegularFile)
                .map(Files::readString)
                .toList()
                .joinToString("\n")
        }
        listOf(
            "import android.",
            "import androidx.",
            "Compose",
            "Glance",
            "TileService",
            "Room",
            "Dao",
            "Entity",
            "DataClient",
            "SharedPreferences"
        ).forEach { forbidden ->
            assertFalse("production source contains $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `module uses the pure Kotlin JVM plugin`() {
        val build = Files.readString(Path.of("build.gradle.kts"))

        assertFalse(build.contains("android.application"))
        assertFalse(build.contains("android.library"))
    }
}
