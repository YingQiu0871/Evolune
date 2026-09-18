package io.github.yingqiu0871.evolune.theme.palette

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * v1.7.2 Slice A — architecture guard for the shared palette package.
 *
 * `theme.palette` is a pure-Kotlin data authority: it must not import Android/Compose, the
 * widget package or UI packages, and the phone widget must consume it (one-way dependency).
 */
class PaletteArchitectureGuardTest {

    private val paletteDir = "src/main/java/io/github/yingqiu0871/evolune/theme/palette"
    private val widgetFile =
        "src/main/java/io/github/yingqiu0871/evolune/widget/WidgetAppearance.kt"

    private fun paletteFiles(): List<Path> =
        Files.list(Path.of(paletteDir)).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".kt") }.toList()
        }

    @Test
    fun `palette package is pure kotlin`() {
        val files = paletteFiles()
        assertTrue("palette package must contain files", files.isNotEmpty())
        files.forEach { file ->
            val code = Files.readString(file)
            listOf("import android", "import androidx", "import io.github").forEach { forbidden ->
                assertFalse(
                    "${file.fileName} must not contain '$forbidden'",
                    code.lineSequence().any { it.startsWith(forbidden) }
                )
            }
        }
    }

    @Test
    fun `widget consumes the shared palette authority one way`() {
        val widget = Files.readString(Path.of(widgetFile))
        assertTrue(
            "WidgetAppearance must import the shared palette package",
            widget.contains("import io.github.yingqiu0871.evolune.theme.palette.PaletteCatalog")
        )
        assertTrue(
            "WidgetAppearance must import PresetPalette for the stable mapping",
            widget.contains("import io.github.yingqiu0871.evolune.theme.palette.PresetPalette")
        )
        val paletteReverse = paletteFiles().any { file ->
            Files.readString(file).contains("io.github.yingqiu0871.evolune.widget")
        }
        assertFalse("palette package must not depend on the widget package", paletteReverse)
    }

    @Test
    fun `no monet token table remains in the widget package`() {
        val widget = Files.readString(Path.of(widgetFile))
        // A preset seed literal (e.g. the MONET_BLUE light surface 0xFFF8F9FF) must not survive
        // in the widget file; it belongs to the shared catalog only.
        listOf("0xFFF8F9FF", "0xFFFBF8FF", "0xFFFFF8F9", "0xFFF6FCF7", "0xFFF4FBF9", "0xFFFFF9F0", "0xFFFAF9FC", "0xFFFCF8FF")
            .forEach { seed ->
                assertFalse(
                    "widget file must not keep the preset seed $seed",
                    widget.contains(seed)
                )
            }
    }
}
