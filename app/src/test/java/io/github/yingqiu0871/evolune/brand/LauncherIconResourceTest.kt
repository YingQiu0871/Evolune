package io.github.yingqiu0871.evolune.brand

import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
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
    fun `launcher artwork remains locked to the official master geometry`() {
        assertEquals(64, OFFICIAL_SOURCE_SHA256.length)
        assertTrue(OFFICIAL_SOURCE_SHA256.all { it in "0123456789abcdef" })
        assertEquals(0.6086248983, SOURCE_HEIGHT_RATIO, 0.0000000001)
        assertEquals(0.5642915643, SOURCE_WIDTH_RATIO, 0.0000000001)
        assertEquals(0.5356265356, SOURCE_CENTER_X, 0.0000000001)
        assertEquals(0.5065093572, SOURCE_CENTER_Y, 0.0000000001)
        assertTrue("optical center must be the normalized center", abs(WEBSITE_CENTER_X - 0.5) <= 0.0000000001)
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
    fun `final adaptive render matches the v1_5 phone crescent geometry`() {
        val adaptiveRatios = DENSITIES.keys.map { density ->
            val rendered = renderAdaptive(
                image("src/main/res/mipmap-$density/ic_launcher_background.png"),
                image("src/main/res/mipmap-$density/ic_launcher_foreground.png")
            )
            assertPhoneV15Geometry("phone adaptive $density", rendered, ::inFinalLauncherMask)
            val monochrome = renderAdaptive(
                image("src/main/res/mipmap-$density/ic_launcher_background.png"),
                image("src/main/res/mipmap-$density/ic_launcher_monochrome.png")
            )
            assertPhoneV15Geometry("phone monochrome $density", monochrome, ::inFinalLauncherMask)
            geometry(rendered, ::inFinalLauncherMask).heightRatio
        }
        val adaptiveWidthRatios = DENSITIES.keys.map { density ->
            val rendered = renderAdaptive(
                image("src/main/res/mipmap-$density/ic_launcher_background.png"),
                image("src/main/res/mipmap-$density/ic_launcher_foreground.png")
            )
            geometry(rendered, ::inFinalLauncherMask).widthRatio
        }
        assertTrue(
            "phone adaptive density drift was $adaptiveRatios",
            adaptiveRatios.maxOrNull()!! - adaptiveRatios.minOrNull()!! <= DENSITY_DRIFT_TOLERANCE
        )
        assertTrue(
            "phone adaptive width density drift was $adaptiveWidthRatios",
            adaptiveWidthRatios.maxOrNull()!! - adaptiveWidthRatios.minOrNull()!! <= DENSITY_DRIFT_TOLERANCE
        )
    }

    @Test
    fun `legacy phone and wear renders preserve their target geometry`() {
        val phoneRatios = DENSITIES.keys.map { density ->
            val phone = image("src/main/res/mipmap-$density/ic_launcher.png")
            val wear = imageFrom("wear/src/main/res/mipmap-$density/ic_launcher.png")
            assertPhoneV15LegacyGeometry("phone legacy $density", phone)
            assertWebsiteGeometry("wear launcher $density", wear)
            geometry(phone).heightRatio
        }
        val phoneWidthRatios = DENSITIES.keys.map { density ->
            geometry(image("src/main/res/mipmap-$density/ic_launcher.png")).widthRatio
        }
        assertTrue(
            "phone legacy density drift was $phoneRatios",
            phoneRatios.maxOrNull()!! - phoneRatios.minOrNull()!! <= PHONE_LEGACY_DENSITY_DRIFT_TOLERANCE
        )
        assertTrue(
            "phone legacy width density drift was $phoneWidthRatios",
            phoneWidthRatios.maxOrNull()!! - phoneWidthRatios.minOrNull()!! <= DENSITY_DRIFT_TOLERANCE
        )
    }

    @Test
    fun `wear legacy launcher assets keep the outer corners transparent`() {
        DENSITIES.keys.forEach { density ->
            val wear = imageFrom("wear/src/main/res/mipmap-$density/ic_launcher.png")
            val maxX = wear.width - 1
            val maxY = wear.height - 1
            listOf(
                0 to 0,
                maxX to 0,
                0 to maxY,
                maxX to maxY
            ).forEach { (x, y) ->
                assertEquals(
                    "wear legacy $density corner ($x,$y) must be transparent",
                    0,
                    alpha(wear.getRGB(x, y))
                )
            }

            var opaqueNearBlack = 0
            for (y in 0 until wear.height) {
                for (x in 0 until wear.width) {
                    val rgb = wear.getRGB(x, y)
                    if (
                        alpha(rgb) > 0 &&
                        red(rgb) < 32 &&
                        green(rgb) < 32 &&
                        blue(rgb) < 32
                    ) {
                        opaqueNearBlack += 1
                    }
                }
            }
            assertEquals(
                "wear legacy $density must not contain opaque black outer pixels",
                0,
                opaqueNearBlack
            )
        }
    }

    @Test
    fun `monochrome and color adaptive outlines are identical`() {
        DENSITIES.keys.forEach { density ->
            val color = image("src/main/res/mipmap-$density/ic_launcher_foreground.png")
            val monochrome = image("src/main/res/mipmap-$density/ic_launcher_monochrome.png")
            assertEquals(color.width, monochrome.width)
            assertEquals(color.height, monochrome.height)
            for (y in 0 until color.height) {
                for (x in 0 until color.width) {
                    assertEquals(
                        "foreground outline differed at $density ($x,$y)",
                        alpha(color.getRGB(x, y)) >= ARTWORK_ALPHA_THRESHOLD,
                        alpha(monochrome.getRGB(x, y)) >= ARTWORK_ALPHA_THRESHOLD
                    )
                }
            }
        }
    }

    @Test
    fun `artwork remains inside circle rounded squircle and teardrop masks`() {
        DENSITIES.keys.forEach { density ->
            val rendered = renderAdaptive(
                image("src/main/res/mipmap-$density/ic_launcher_background.png"),
                image("src/main/res/mipmap-$density/ic_launcher_foreground.png")
            )
            MASKS.forEach { (name, mask) ->
                for (y in 0 until rendered.height) {
                    for (x in 0 until rendered.width) {
                        if (alpha(rendered.getRGB(x, y)) >= ARTWORK_ALPHA_THRESHOLD &&
                            red(rendered.getRGB(x, y)) >= CRESCENT_PIXEL_THRESHOLD &&
                            green(rendered.getRGB(x, y)) >= CRESCENT_PIXEL_THRESHOLD &&
                            blue(rendered.getRGB(x, y)) >= CRESCENT_PIXEL_THRESHOLD
                        ) {
                            assertTrue("$density artwork escaped $name mask at ($x,$y)", mask(x, y, rendered.width))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `brightness weighted centroid is centered and stable across densities`() {
        val centers = DENSITIES.keys.map { density ->
            val phone = image("src/main/res/mipmap-$density/ic_launcher.png")
            val wear = imageFrom("wear/src/main/res/mipmap-$density/ic_launcher.png")
            val phoneCentroid = brightnessCentroid(phone)
            val wearCentroid = brightnessCentroid(wear)
            assertTrue(
                "$density phone weighted x was ${phoneCentroid.x}",
                abs(phoneCentroid.x - TARGET_WEIGHTED_CENTER_X) <= WEIGHTED_CENTER_TOLERANCE
            )
            assertTrue(
                "$density wear weighted x was ${wearCentroid.x}",
                abs(wearCentroid.x - TARGET_WEIGHTED_CENTER_X) <= WEIGHTED_CENTER_TOLERANCE
            )
            assertTrue(
                "$density phone weighted y was ${phoneCentroid.y}",
                abs(phoneCentroid.y - TARGET_WEIGHTED_CENTER_Y) <= WEIGHTED_CENTER_TOLERANCE
            )
            assertTrue(
                "$density wear weighted y was ${wearCentroid.y}",
                abs(wearCentroid.y - TARGET_WEIGHTED_CENTER_Y) <= WEIGHTED_CENTER_TOLERANCE
            )
            phoneCentroid.x
        }
        assertTrue(
            "weighted centroid density drift was $centers",
            centers.maxOrNull()!! - centers.minOrNull()!! <= DENSITY_DRIFT_TOLERANCE
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
            abs(measured.heightRatio - WEBSITE_HEIGHT_RATIO) <= SIZE_TOLERANCE
        )
        assertTrue(
            "$label width ratio ${measured.widthRatio} was outside target $WEBSITE_WIDTH_RATIO",
            abs(measured.widthRatio - WEBSITE_WIDTH_RATIO) <= SIZE_TOLERANCE
        )
        assertTrue(
            "$label center x ${measured.centerX} was outside target $WEBSITE_CENTER_X",
            abs(measured.centerX - WEBSITE_CENTER_X) <= CENTER_TOLERANCE
        )
        assertTrue(
            "$label center y ${measured.centerY} was outside target $WEBSITE_CENTER_Y",
            kotlin.math.abs(measured.centerY - WEBSITE_CENTER_Y) <= GEOMETRY_TOLERANCE
        )
    }

    private fun assertPhoneV15Geometry(
        label: String,
        image: BufferedImage,
        mask: ((Int, Int, Int) -> Boolean)? = null
    ) {
        val measured = geometry(image, mask)
        assertTrue(
            "$label height ratio ${measured.heightRatio} was outside v1.5 target $PHONE_ADAPTIVE_HEIGHT_RATIO",
            abs(measured.heightRatio - PHONE_ADAPTIVE_HEIGHT_RATIO) <= PHONE_ADAPTIVE_SIZE_TOLERANCE
        )
        assertTrue(
            "$label width ratio ${measured.widthRatio} was outside v1.5 target $PHONE_ADAPTIVE_WIDTH_RATIO",
            abs(measured.widthRatio - PHONE_ADAPTIVE_WIDTH_RATIO) <= PHONE_ADAPTIVE_SIZE_TOLERANCE
        )
        assertTrue(
            "$label center x ${measured.centerX} was outside v1.5 target $PHONE_ADAPTIVE_CENTER_X",
            abs(measured.centerX - PHONE_ADAPTIVE_CENTER_X) <= PHONE_ADAPTIVE_CENTER_TOLERANCE
        )
        assertTrue(
            "$label center y ${measured.centerY} was outside v1.5 target $PHONE_ADAPTIVE_CENTER_Y",
            kotlin.math.abs(measured.centerY - PHONE_ADAPTIVE_CENTER_Y) <= GEOMETRY_TOLERANCE
        )
    }

    private fun assertPhoneV15LegacyGeometry(label: String, image: BufferedImage) {
        val measured = geometry(image)
        assertTrue(
            "$label height ratio ${measured.heightRatio} was outside v1.5 target $PHONE_LEGACY_HEIGHT_RATIO",
            abs(measured.heightRatio - PHONE_LEGACY_HEIGHT_RATIO) <= PHONE_LEGACY_SIZE_TOLERANCE
        )
        assertTrue(
            "$label width ratio ${measured.widthRatio} was outside v1.5 target $PHONE_LEGACY_WIDTH_RATIO",
            abs(measured.widthRatio - PHONE_LEGACY_WIDTH_RATIO) <= PHONE_LEGACY_SIZE_TOLERANCE
        )
        assertTrue(
            "$label center x ${measured.centerX} was outside v1.5 target $PHONE_LEGACY_CENTER_X",
            abs(measured.centerX - PHONE_LEGACY_CENTER_X) <= PHONE_LEGACY_CENTER_TOLERANCE
        )
        assertTrue(
            "$label center y ${measured.centerY} was outside v1.5 target $PHONE_LEGACY_CENTER_Y",
            abs(measured.centerY - PHONE_LEGACY_CENTER_Y) <= PHONE_LEGACY_CENTER_TOLERANCE
        )
    }

    private fun brightnessCentroid(image: BufferedImage): Centroid {
        val background = requireNotNull(bounds(image, null) { alpha(it) >= ARTWORK_ALPHA_THRESHOLD })
        val crescent = requireNotNull(
            largestComponentBounds(image, null) { rgb ->
                alpha(rgb) >= ARTWORK_ALPHA_THRESHOLD &&
                    red(rgb) >= CRESCENT_PIXEL_THRESHOLD &&
                    green(rgb) >= CRESCENT_PIXEL_THRESHOLD &&
                    blue(rgb) >= CRESCENT_PIXEL_THRESHOLD
            }
        )
        var weightedX = 0.0
        var weightedY = 0.0
        var total = 0.0
        for (y in crescent.minY..crescent.maxY) {
            for (x in crescent.minX..crescent.maxX) {
                val rgb = image.getRGB(x, y)
                if (alpha(rgb) < ARTWORK_ALPHA_THRESHOLD ||
                    red(rgb) < CRESCENT_PIXEL_THRESHOLD ||
                    green(rgb) < CRESCENT_PIXEL_THRESHOLD ||
                    blue(rgb) < CRESCENT_PIXEL_THRESHOLD
                ) continue
                val weight = (red(rgb) + green(rgb) + blue(rgb)).toDouble()
                weightedX += (x + 0.5) * weight
                weightedY += (y + 0.5) * weight
                total += weight
            }
        }
        assertTrue("brightness centroid requires white artwork", total > 0.0)
        return Centroid(
            (weightedX / total - background.minX) / background.width,
            (weightedY / total - background.minY) / background.height
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

    private data class Centroid(val x: Double, val y: Double)

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
        const val OFFICIAL_SOURCE_SHA256 = "cba4ea1f84cb77977cb781a0e5d4ca4c172709fa4dbf5efa5ba5ba65c2e89159"
        const val FINAL_MASK_INSET = 6
        const val FINAL_MASK_RADIUS = 18
        const val ARTWORK_ALPHA_THRESHOLD = 128
        const val CRESCENT_PIXEL_THRESHOLD = 230
        const val GEOMETRY_TOLERANCE = 0.01
        // Legacy mdpi is 48 px; one pixel of source anti-aliasing is 2.08 pp.
        const val SIZE_TOLERANCE = 0.025
        const val PHONE_ADAPTIVE_SIZE_TOLERANCE = 0.01
        const val PHONE_ADAPTIVE_CENTER_TOLERANCE = 0.01
        const val PHONE_LEGACY_SIZE_TOLERANCE = 0.02
        const val PHONE_LEGACY_CENTER_TOLERANCE = 0.015
        const val PHONE_LEGACY_DENSITY_DRIFT_TOLERANCE = 0.025
        const val CENTER_TOLERANCE = 0.005
        const val WEIGHTED_CENTER_TOLERANCE = 0.02
        const val DENSITY_DRIFT_TOLERANCE = 0.0075
        const val SOURCE_HEIGHT_RATIO = 0.6086248983
        const val SOURCE_WIDTH_RATIO = 0.5642915643
        const val SOURCE_CENTER_X = 0.5356265356
        const val SOURCE_CENTER_Y = 0.5065093572
        const val WEBSITE_HEIGHT_RATIO = SOURCE_HEIGHT_RATIO
        const val WEBSITE_WIDTH_RATIO = SOURCE_WIDTH_RATIO
        const val WEBSITE_CENTER_X = 0.5
        const val WEBSITE_CENTER_Y = SOURCE_CENTER_Y
        const val PHONE_ADAPTIVE_HEIGHT_RATIO = 0.535
        const val PHONE_ADAPTIVE_WIDTH_RATIO = 0.494
        const val PHONE_ADAPTIVE_CENTER_X = 0.5
        const val PHONE_ADAPTIVE_CENTER_Y = SOURCE_CENTER_Y
        const val PHONE_LEGACY_HEIGHT_RATIO = 0.47
        const val PHONE_LEGACY_WIDTH_RATIO = 0.4375
        const val PHONE_LEGACY_CENTER_X = 0.5
        const val PHONE_LEGACY_CENTER_Y = 0.505
        const val TARGET_WEIGHTED_CENTER_X = 0.428
        const val TARGET_WEIGHTED_CENTER_Y = 0.546
        val DENSITIES = linkedMapOf(
            "mdpi" to 1.0,
            "hdpi" to 1.5,
            "xhdpi" to 2.0,
            "xxhdpi" to 3.0,
            "xxxhdpi" to 4.0
        )
        val MASKS = linkedMapOf<String, (Int, Int, Int) -> Boolean>(
            "circle" to { x, y, size ->
                val center = size / 2.0
                val radius = size / 2.0
                val dx = x + 0.5 - center
                val dy = y + 0.5 - center
                dx * dx + dy * dy <= radius * radius
            },
            "rounded" to { x, y, size ->
                roundedMask(x, y, size, 7.0, 23.0)
            },
            "squircle" to { x, y, size ->
                val center = size / 2.0
                val half = center - 4.0
                val dx = abs(x + 0.5 - center) / half
                val dy = abs(y + 0.5 - center) / half
                dx * dx * dx + dy * dy * dy <= 1.0
            },
            "teardrop" to { x, y, size ->
                val centerX = size / 2.0
                val centerY = size / 2.0 + 2.0
                val radius = size / 2.0 - 4.0
                val dx = x + 0.5 - centerX
                val dy = y + 0.5 - centerY
                dx * dx + dy * dy <= radius * radius ||
                    (y + 0.5 >= centerY && abs(dx) <= radius && y + 0.5 <= size - 2.0)
            }
        )

        private fun roundedMask(x: Int, y: Int, size: Int, inset: Double, radius: Double): Boolean {
            val left = inset
            val top = inset
            val right = size - inset
            val bottom = size - inset
            val nearestX = (x + 0.5).coerceIn(left + radius, right - radius)
            val nearestY = (y + 0.5).coerceIn(top + radius, bottom - radius)
            val dx = x + 0.5 - nearestX
            val dy = y + 0.5 - nearestY
            return dx * dx + dy * dy <= radius * radius
        }
    }
}
