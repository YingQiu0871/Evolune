package io.github.yingqiu0871.evolune.brand

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconResourceTest {
    @Test
    fun `manifest points to the complete launcher icon family`() {
        val application = parse("src/main/AndroidManifest.xml")
            .getElementsByTagName("application")
            .item(0)
        assertNotNull(application)
        assertEquals("@mipmap/ic_launcher", application.attributes.getNamedItemNS(ANDROID_NS, "icon").nodeValue)
        assertEquals(
            "@mipmap/ic_launcher",
            application.attributes.getNamedItemNS(ANDROID_NS, "roundIcon").nodeValue
        )
    }

    @Test
    fun `adaptive icon layers are parseable and reference existing resources`() {
        val adaptive = parse("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
            .documentElement
        assertEquals("adaptive-icon", adaptive.localName ?: adaptive.nodeName)
        val expected = mapOf(
            "background" to "@mipmap/ic_launcher_background",
            "foreground" to "@mipmap/ic_launcher_foreground",
            "monochrome" to "@mipmap/ic_launcher_monochrome"
        )
        expected.forEach { (tag, drawable) ->
            val node = adaptive.getElementsByTagName(tag).item(0)
            assertNotNull(node)
            assertEquals(
                drawable,
                node.attributes.getNamedItemNS(ANDROID_NS, "drawable").nodeValue
            )
            assertTrue(resource("src/main/res/mipmap-mdpi/ic_launcher_${tag}.png").isFile)
        }
    }

    @Test
    fun `all launcher densities have matching layers and safe foreground artwork`() {
        val densities = mapOf<String, Double>(
            "mdpi" to 1.0,
            "hdpi" to 1.5,
            "xhdpi" to 2.0,
            "xxhdpi" to 3.0,
            "xxxhdpi" to 4.0
        )
        densities.forEach { (density, scale) ->
            val adaptiveSize = (108 * scale).toInt()
            val legacySize = (48 * scale).toInt()
            assertEquals(adaptiveSize, image("src/main/res/mipmap-$density/ic_launcher_background.png").width)
            assertEquals(adaptiveSize, image("src/main/res/mipmap-$density/ic_launcher_foreground.png").width)
            assertEquals(adaptiveSize, image("src/main/res/mipmap-$density/ic_launcher_monochrome.png").width)
            assertEquals(legacySize, image("src/main/res/mipmap-$density/ic_launcher.png").width)
            assertForegroundIsNonEmptyAndInsideSafeZone(
                image("src/main/res/mipmap-$density/ic_launcher_foreground.png")
            )
            assertForegroundIsNonEmptyAndInsideSafeZone(
                image("src/main/res/mipmap-$density/ic_launcher_monochrome.png")
            )
        }
    }

    @Test
    fun `simulated final adaptive composition keeps website crescent scale`() {
        val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        val ratios = densities.map { density ->
            val background = image("src/main/res/mipmap-$density/ic_launcher_background.png")
            val foreground = image("src/main/res/mipmap-$density/ic_launcher_foreground.png")
            val monochrome = image("src/main/res/mipmap-$density/ic_launcher_monochrome.png")
            val backgroundBounds = compositionBounds(background) { true }
            val foregroundBounds = compositionBounds(foreground, ::isWhitePixel)
            val monochromeBounds = compositionBounds(monochrome, ::isWhitePixel)

            assertEquals(foregroundBounds, monochromeBounds)
            val ratio = foregroundBounds.height.toDouble() / backgroundBounds.height
            assertTrue(
                "$density final composition ratio was $ratio",
                ratio in FINAL_RATIO_RANGE
            )
            ratio
        }
        assertTrue(
            "density ratios diverged: $ratios",
            ratios.maxOrNull()!! - ratios.minOrNull()!! <= MAX_DENSITY_RATIO_DRIFT
        )

        val legacyBounds = requireNotNull(bounds(image("src/main/res/mipmap-mdpi/ic_launcher.png"), ::isWhitePixel))
        val legacyRatio = legacyBounds.height.toDouble() / 48.0
        assertTrue("legacy ratio was $legacyRatio", legacyRatio in LEGACY_RATIO_RANGE)
    }

    private fun assertForegroundIsNonEmptyAndInsideSafeZone(image: BufferedImage) {
        val inset = kotlin.math.ceil(image.width * 21.0 / 108.0).toInt()
        val maximum = image.width - inset - 1
        var nonTransparent = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if ((image.getRGB(x, y) ushr 24) != 0) {
                    nonTransparent += 1
                    assertTrue("foreground x outside safe zone", x in inset..maximum)
                    assertTrue("foreground y outside safe zone", y in inset..maximum)
                }
            }
        }
        assertTrue("foreground must contain artwork", nonTransparent > 0)
    }

    private fun compositionBounds(
        image: BufferedImage,
        pixel: (Int) -> Boolean = { alpha(it) > 0 }
    ): Bounds {
        val result = bounds(image) { rgb ->
            alpha(rgb) > 0 && pixel(rgb)
        }
        return requireNotNull(result)
    }

    private fun bounds(
        image: BufferedImage,
        pixel: (Int) -> Boolean
    ): Bounds? {
        val inset = (image.width * FINAL_MASK_INSET / 108.0).roundToInt()
        val radius = (image.width * FINAL_MASK_RADIUS / 108.0).roundToInt()
        var minX = image.width
        var minY = image.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (inFinalLauncherMask(x, y, image.width, inset, radius) &&
                    pixel(image.getRGB(x, y))
                ) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        return if (maxX < minX || maxY < minY) {
            null
        } else {
            Bounds(minX, minY, maxX, maxY)
        }
    }

    private fun inFinalLauncherMask(
        x: Int,
        y: Int,
        size: Int,
        inset: Int,
        radius: Int
    ): Boolean {
        val left = inset
        val top = inset
        val right = size - inset - 1
        val bottom = size - inset - 1
        val nearestX = x.coerceIn(left + radius, right - radius)
        val nearestY = y.coerceIn(top + radius, bottom - radius)
        val dx = x - nearestX
        val dy = y - nearestY
        return dx * dx + dy * dy <= radius * radius
    }

    private fun isWhitePixel(rgb: Int): Boolean =
        alpha(rgb) > 0 &&
            red(rgb) >= WHITE_THRESHOLD &&
            green(rgb) >= WHITE_THRESHOLD &&
            blue(rgb) >= WHITE_THRESHOLD

    private fun alpha(rgb: Int): Int = (rgb ushr 24) and 0xff

    private fun red(rgb: Int): Int = (rgb ushr 16) and 0xff

    private fun green(rgb: Int): Int = (rgb ushr 8) and 0xff

    private fun blue(rgb: Int): Int = rgb and 0xff

    private data class Bounds(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int
    ) {
        val height: Int get() = maxY - minY + 1
    }

    private fun image(path: String): BufferedImage =
        requireNotNull(ImageIO.read(resource(path))) { "image could not be decoded: $path" }

    private fun parse(path: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(resource(path))

    private fun resource(relativePath: String): File {
        val normalized = relativePath.removePrefix("src/main/")
        val candidates = listOf(
            File(relativePath),
            File("app/src/main/$normalized"),
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "app/src/main/$normalized")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("resource not found: $relativePath")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val FINAL_MASK_INSET = 6
        const val FINAL_MASK_RADIUS = 18
        const val WHITE_THRESHOLD = 245
        val FINAL_RATIO_RANGE = 0.58..0.62
        val LEGACY_RATIO_RANGE = 0.55..0.62
        const val MAX_DENSITY_RATIO_DRIFT = 0.02
    }
}
