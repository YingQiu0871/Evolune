package io.github.yingqiu0871.evolune.brand

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
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
    }
}
