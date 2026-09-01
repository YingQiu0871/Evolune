package io.github.yingqiu0871.evolune.brand

import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconResourceTest {
    @Test
    fun `phone manifest points to the complete launcher icon family`() {
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
    fun `wear manifest points to the same launcher icon family`() {
        val application = parse("wear/src/main/AndroidManifest.xml")
            .getElementsByTagName("application")
            .item(0)
        assertNotNull(application)
        assertEquals(
            "@mipmap/ic_launcher",
            application.attributes.getNamedItemNS(ANDROID_NS, "icon").nodeValue
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
        DENSITIES.forEach { (density, scale) ->
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
            val wearIcon = imageFrom("wear/src/main/res/mipmap-$density/ic_launcher.png")
            assertEquals(legacySize, wearIcon.width)
        }
    }

    @Test
    fun `final adaptive render matches the website crescent geometry`() {
        val adaptiveRatios = DENSITIES.keys.map { density ->
            val rendered = renderAdaptive(
                image("src/main/res/mipmap-$density/ic_launcher_background.png"),
                image("src/main/res/mipmap-$density/ic_launcher_foreground.png")
            )
            assertWebsiteGeometry("phone adaptive $density", rendered, ::inFinalLauncherMask)
            val monochrome = renderAdaptive(
                image("src/main/res/mipmap-$density/ic_launcher_background.png"),
                image("src/main/res/mipmap-$density/ic_launcher_monochrome.png")
            )
            assertWebsiteGeometry("phone monochrome $density", monochrome, ::inFinalLauncherMask)
            geometry(rendered, ::inFinalLauncherMask).heightRatio
        }
        assertTrue(
            "phone adaptive density drift was $adaptiveRatios",
            adaptiveRatios.maxOrNull()!! - adaptiveRatios.minOrNull()!! <= DENSITY_DRIFT_TOLERANCE
        )
    }

    @Test
    fun `legacy phone and wear renders match the website crescent geometry`() {
        val phoneRatios = DENSITIES.keys.map { density ->
            val phone = image("src/main/res/mipmap-$density/ic_launcher.png")
            val wear = imageFrom("wear/src/main/res/mipmap-$density/ic_launcher.png")
            assertWebsiteGeometry("phone legacy $density", phone)
            assertWebsiteGeometry("wear launcher $density", wear)
            val phoneGeometry = geometry(phone)
            val wearGeometry = geometry(wear)
            assertTrue(
                "$density phone/wear height geometry differed: ${phoneGeometry.heightRatio} vs ${wearGeometry.heightRatio}",
                kotlin.math.abs(phoneGeometry.heightRatio - wearGeometry.heightRatio) <= CROSS_TARGET_TOLERANCE
            )
            assertTrue(
                "$density phone/wear width geometry differed: ${phoneGeometry.widthRatio} vs ${wearGeometry.widthRatio}",
                kotlin.math.abs(phoneGeometry.widthRatio - wearGeometry.widthRatio) <= CROSS_TARGET_TOLERANCE
            )
            phoneGeometry.heightRatio
        }
        assertTrue(
            "phone legacy density drift was $phoneRatios",
            phoneRatios.maxOrNull()!! - phoneRatios.minOrNull()!! <= DENSITY_DRIFT_TOLERANCE
        )
    }

    private fun assertWebsiteGeometry(
        label: String,
        image: BufferedImage,
        mask: ((Int, Int, Int) -> Boolean)? = null
    ) {
        val measured = geometry(image, mask)
        assertTrue(
            "$label height ratio ${measured.heightRatio} was outside target $WEBSITE_HEIGHT_RATIO",
            kotlin.math.abs(measured.heightRatio - WEBSITE_HEIGHT_RATIO) <= GEOMETRY_TOLERANCE
        )
        assertTrue(
            "$label width ratio ${measured.widthRatio} was outside target $WEBSITE_WIDTH_RATIO",
            kotlin.math.abs(measured.widthRatio - WEBSITE_WIDTH_RATIO) <= GEOMETRY_TOLERANCE
        )
        assertTrue(
            "$label center x ${measured.centerX} was outside target $WEBSITE_CENTER_X",
            kotlin.math.abs(measured.centerX - WEBSITE_CENTER_X) <= GEOMETRY_TOLERANCE
        )
        assertTrue(
            "$label center y ${measured.centerY} was outside target $WEBSITE_CENTER_Y",
            kotlin.math.abs(measured.centerY - WEBSITE_CENTER_Y) <= GEOMETRY_TOLERANCE
        )
    }

    private fun assertForegroundIsNonEmptyAndInsideSafeZone(image: BufferedImage) {
        val inset = ceil(image.width * 21.0 / 108.0).toInt()
        val maximum = image.width - inset - 1
        var nonTransparent = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (alpha(image.getRGB(x, y)) >= ARTWORK_ALPHA_THRESHOLD) {
                    nonTransparent += 1
                    assertTrue("foreground x outside safe zone", x in inset..maximum)
                    assertTrue("foreground y outside safe zone", y in inset..maximum)
                }
            }
        }
        assertTrue("foreground must contain artwork", nonTransparent > 0)
    }

    private fun geometry(
        image: BufferedImage,
        mask: ((Int, Int, Int) -> Boolean)? = null
    ): Geometry {
        val background = requireNotNull(bounds(image, mask) { alpha(it) >= ARTWORK_ALPHA_THRESHOLD })
        val crescent = requireNotNull(
            largestComponentBounds(image, mask) { rgb ->
                alpha(rgb) >= ARTWORK_ALPHA_THRESHOLD &&
                    red(rgb) >= CRESCENT_PIXEL_THRESHOLD &&
                    green(rgb) >= CRESCENT_PIXEL_THRESHOLD &&
                    blue(rgb) >= CRESCENT_PIXEL_THRESHOLD
            }
        )
        return Geometry(
            heightRatio = crescent.height.toDouble() / background.height,
            widthRatio = crescent.width.toDouble() / background.width,
            centerX = (crescent.centerX + 0.5 - background.minX) / background.width,
            centerY = (crescent.centerY + 0.5 - background.minY) / background.height
        )
    }

    private fun renderAdaptive(
        background: BufferedImage,
        foreground: BufferedImage
    ): BufferedImage = BufferedImage(background.width, background.height, BufferedImage.TYPE_INT_ARGB).also { result ->
        val graphics = result.createGraphics()
        graphics.drawImage(background, 0, 0, null)
        graphics.composite = AlphaComposite.SrcOver
        graphics.drawImage(foreground, 0, 0, null)
        graphics.dispose()
    }

    private fun largestComponentBounds(
        image: BufferedImage,
        mask: ((Int, Int, Int) -> Boolean)?,
        pixel: (Int) -> Boolean
    ): Bounds? {
        val visited = Array(image.height) { BooleanArray(image.width) }
        var largest: Bounds? = null
        var largestArea = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (visited[y][x] || !matches(image, x, y, mask, pixel)) continue
                val queue = java.util.ArrayDeque<Pair<Int, Int>>()
                queue.add(x to y)
                visited[y][x] = true
                var area = 0
                var minX = x
                var minY = y
                var maxX = x
                var maxY = y
                while (queue.isNotEmpty()) {
                    val (currentX, currentY) = queue.removeFirst()
                    area += 1
                    minX = minOf(minX, currentX)
                    minY = minOf(minY, currentY)
                    maxX = maxOf(maxX, currentX)
                    maxY = maxOf(maxY, currentY)
                    listOf(
                        currentX - 1 to currentY,
                        currentX + 1 to currentY,
                        currentX to currentY - 1,
                        currentX to currentY + 1
                    ).forEach { (nextX, nextY) ->
                        if (
                            nextX in 0 until image.width &&
                            nextY in 0 until image.height &&
                            !visited[nextY][nextX] &&
                            matches(image, nextX, nextY, mask, pixel)
                        ) {
                            visited[nextY][nextX] = true
                            queue.add(nextX to nextY)
                        }
                    }
                }
                if (area > largestArea) {
                    largestArea = area
                    largest = Bounds(minX, minY, maxX, maxY)
                }
            }
        }
        return largest
    }

    private fun matches(
        image: BufferedImage,
        x: Int,
        y: Int,
        mask: ((Int, Int, Int) -> Boolean)?,
        pixel: (Int) -> Boolean
    ): Boolean = (mask?.invoke(x, y, image.width) ?: true) && pixel(image.getRGB(x, y))

    private fun bounds(
        image: BufferedImage,
        mask: ((Int, Int, Int) -> Boolean)?,
        pixel: (Int) -> Boolean
    ): Bounds? {
        var minX = image.width
        var minY = image.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (matches(image, x, y, mask, pixel)) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        return if (maxX < minX || maxY < minY) null else Bounds(minX, minY, maxX, maxY)
    }

    private fun inFinalLauncherMask(x: Int, y: Int, size: Int): Boolean {
        val inset = (size * FINAL_MASK_INSET / 108.0).roundToInt()
        val radius = (size * FINAL_MASK_RADIUS / 108.0).roundToInt()
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

    private fun alpha(rgb: Int): Int = (rgb ushr 24) and 0xff
    private fun red(rgb: Int): Int = (rgb ushr 16) and 0xff
    private fun green(rgb: Int): Int = (rgb ushr 8) and 0xff
    private fun blue(rgb: Int): Int = rgb and 0xff

    private data class Geometry(
        val heightRatio: Double,
        val widthRatio: Double,
        val centerX: Double,
        val centerY: Double
    )

    private data class Bounds(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int
    ) {
        val width: Int get() = maxX - minX + 1
        val height: Int get() = maxY - minY + 1
        val centerX: Double get() = (minX + maxX) / 2.0
        val centerY: Double get() = (minY + maxY) / 2.0
    }

    private fun image(path: String): BufferedImage = imageFrom(path)

    private fun imageFrom(relativePath: String): BufferedImage {
        val file = resource(relativePath)
        return requireNotNull(ImageIO.read(file)) { "image could not be decoded: $relativePath" }
    }

    private fun parse(path: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(resource(path))

    private fun resource(relativePath: String): File {
        val normalized = relativePath.removePrefix("src/main/")
        val roots = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .toList()
        val candidates = roots.flatMap { root ->
            listOf(
                File(root, relativePath),
                File(root, "app/src/main/$normalized")
            )
        }
        return candidates.firstOrNull(File::isFile)
            ?: error("resource not found: $relativePath")
    }

    private companion object {
        // Measured from https://evolune.yingqiu.me/assets/evolune-logo.png
        // (SHA-256 cba4ea1f84cb77977cb781a0e5d4ca4c172709fa4dbf5efa5ba5ba65c2e89159):
        // visible artwork bounds and the largest connected near-white component.
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val FINAL_MASK_INSET = 6
        const val FINAL_MASK_RADIUS = 18
        const val ARTWORK_ALPHA_THRESHOLD = 128
        const val CRESCENT_PIXEL_THRESHOLD = 230
        const val GEOMETRY_TOLERANCE = 0.01
        const val DENSITY_DRIFT_TOLERANCE = 0.01
        const val CROSS_TARGET_TOLERANCE = 0.01
        const val WEBSITE_HEIGHT_RATIO = 0.6086248983
        const val WEBSITE_WIDTH_RATIO = 0.5642915643
        const val WEBSITE_CENTER_X = 0.5356265356
        const val WEBSITE_CENTER_Y = 0.5065093572
        val DENSITIES = linkedMapOf(
            "mdpi" to 1.0,
            "hdpi" to 1.5,
            "xhdpi" to 2.0,
            "xxhdpi" to 3.0,
            "xxxhdpi" to 4.0
        )
    }
}
