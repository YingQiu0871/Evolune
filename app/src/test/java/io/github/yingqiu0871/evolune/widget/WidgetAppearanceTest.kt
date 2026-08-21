package io.github.yingqiu0871.evolune.widget

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAppearanceTest {
    @Test
    fun `default config is per Widget and normalized to a readable range`() {
        val repository = InMemoryWidgetAppearanceRepository()

        assertEquals(WidgetAppearanceConfig.Default, repository.read(10))
        assertEquals(
            MIN_WIDGET_BACKGROUND_OPACITY,
            WidgetAppearanceConfig(backgroundOpacity = 0f).normalized().backgroundOpacity
        )
        assertEquals(1f, WidgetAppearanceConfig(backgroundOpacity = 2f).normalized().backgroundOpacity)
    }

    @Test
    fun `two Widget configurations remain independent and deletion is isolated`() {
        val repository = InMemoryWidgetAppearanceRepository()
        val first = WidgetAppearanceConfig(
            WidgetThemeMode.DARK,
            WidgetColorScheme.MONET_VIOLET,
            0.4f
        )
        val second = WidgetAppearanceConfig(
            WidgetThemeMode.LIGHT,
            WidgetColorScheme.MATERIAL_YOU_AUTO,
            1f
        )

        repository.write(10, first)
        repository.write(11, second)
        assertEquals(first, repository.read(10))
        assertEquals(second, repository.read(11))

        repository.delete(10)
        assertEquals(WidgetAppearanceConfig.Default, repository.read(10))
        assertEquals(second, repository.read(11))
    }

    @Test
    fun `apply persists exact Widget then invokes canonical refresh`() = runBlocking {
        val repository = InMemoryWidgetAppearanceRepository()
        val refreshed = mutableListOf<Int>()
        val controller = WidgetConfigurationController(repository) { refreshed += it }
        val config = WidgetAppearanceConfig(
            WidgetThemeMode.DARK,
            WidgetColorScheme.MONET_AMBER,
            0.7f
        )

        controller.apply(42, config)

        assertEquals(config, repository.read(42))
        assertEquals(listOf(42), refreshed)
    }

    @Test
    fun `cancel has no write and restore defaults only changes draft intent`() {
        val repository = InMemoryWidgetAppearanceRepository()
        val original = WidgetAppearanceConfig(
            WidgetThemeMode.LIGHT,
            WidgetColorScheme.MONET_BLUE,
            0.6f
        )
        repository.write(7, original)
        val controller = WidgetConfigurationController(repository) {}

        controller.cancel()

        assertEquals(original, repository.read(7))
        assertEquals(WidgetAppearanceConfig.Default, controller.restoreDefaults())
        assertEquals(original, repository.read(7))
    }

    @Test
    fun `theme and preset choices resolve distinct readable light dark palettes`() {
        WidgetColorScheme.entries.forEach { scheme ->
            val config = WidgetAppearanceConfig(colorScheme = scheme)
            val light = WidgetPaletteResolver.resolve(config, dark = false)
            val dark = WidgetPaletteResolver.resolve(config, dark = true)

            assertNotEquals(light.surface, dark.surface)
            assertNotEquals(light.onSurface, light.surface)
            assertNotEquals(dark.onSurface, dark.surface)
            assertEquals(0xFF, light.surface ushr 24)
            assertEquals(0xFF, dark.surface ushr 24)
            assertTrue(light.medicationRailRoles.distinct().size >= 3)
            assertTrue(dark.medicationRailRoles.distinct().size >= 3)
        }
    }

    @Test
    fun `Material You preference remains automatic when dynamic colors are unavailable`() {
        val config = WidgetAppearanceConfig(colorScheme = WidgetColorScheme.MATERIAL_YOU_AUTO)
        val fallback = WidgetPaletteResolver.resolve(config, dark = false)
        val dynamicRoles = mapOf(
            "system_accent1_600" to 0xFF123456.toInt(),
            "system_accent2_600" to 0xFF345678.toInt(),
            "system_accent3_600" to 0xFF56789A.toInt()
        )
        val dynamic = WidgetPaletteResolver.resolve(config, dark = false, dynamicRoles::get)

        assertEquals(WidgetColorScheme.MATERIAL_YOU_AUTO, config.colorScheme)
        assertNotEquals(fallback.primary, dynamic.primary)
        assertEquals(dynamicRoles.values.toList(), dynamic.medicationRailRoles)
        assertFalse(fallback.medicationRailRoles.isEmpty())
    }

    @Test
    fun `medication rails are resolved palette roles with stable cycling`() {
        val palette = WidgetPaletteResolver.resolve(
            WidgetAppearanceConfig(colorScheme = WidgetColorScheme.MONET_VIOLET),
            dark = false
        )

        assertEquals(
            listOf(palette.primary, palette.secondary, palette.tertiary),
            palette.medicationRailRoles
        )
        assertEquals(palette.primary, palette.medicationRailColor(0))
        assertEquals(palette.secondary, palette.medicationRailColor(1))
        assertEquals(palette.tertiary, palette.medicationRailColor(2))
        assertEquals(palette.primary, palette.medicationRailColor(3))
    }

    @Test
    fun `switching presets and theme resolves fresh rail role colors`() {
        val violetLight = WidgetPaletteResolver.resolve(
            WidgetAppearanceConfig(colorScheme = WidgetColorScheme.MONET_VIOLET),
            dark = false
        )
        val mintLight = WidgetPaletteResolver.resolve(
            WidgetAppearanceConfig(colorScheme = WidgetColorScheme.MONET_MINT),
            dark = false
        )
        val mintDark = WidgetPaletteResolver.resolve(
            WidgetAppearanceConfig(colorScheme = WidgetColorScheme.MONET_MINT),
            dark = true
        )

        assertNotEquals(violetLight.medicationRailRoles, mintLight.medicationRailRoles)
        assertNotEquals(mintLight.medicationRailRoles, mintDark.medicationRailRoles)
    }

    @Test
    fun `two Widget configs resolve isolated opaque rail palettes independent of opacity`() {
        val repository = InMemoryWidgetAppearanceRepository()
        repository.write(
            101,
            WidgetAppearanceConfig(
                WidgetThemeMode.LIGHT,
                WidgetColorScheme.MONET_AMBER,
                0.3f
            )
        )
        repository.write(
            202,
            WidgetAppearanceConfig(
                WidgetThemeMode.DARK,
                WidgetColorScheme.MONET_BLUE,
                1f
            )
        )
        val first = WidgetPaletteResolver.resolve(repository.read(101), dark = false)
        val second = WidgetPaletteResolver.resolve(repository.read(202), dark = true)
        val firstOpaque = WidgetPaletteResolver.resolve(
            repository.read(101).copy(backgroundOpacity = 1f),
            dark = false
        )

        assertNotEquals(first.medicationRailRoles, second.medicationRailRoles)
        assertEquals(first.medicationRailRoles, firstOpaque.medicationRailRoles)
        assertTrue(first.medicationRailRoles.all { it ushr 24 == 0xFF })
        assertTrue(second.medicationRailRoles.all { it ushr 24 == 0xFF })
    }

    @Test
    fun `action buttons use opaque palette roles across themes presets and opacity`() {
        WidgetColorScheme.entries.forEach { scheme ->
            listOf(false, true).forEach { dark ->
                val lowOpacity = WidgetAppearanceConfig(
                    themeMode = if (dark) WidgetThemeMode.DARK else WidgetThemeMode.LIGHT,
                    colorScheme = scheme,
                    backgroundOpacity = 0.3f
                )
                val palette = WidgetPaletteResolver.resolve(lowOpacity, dark)
                val available = WidgetRowAction.RECORD.buttonStyle(palette)
                val completed = WidgetRowAction.COMPLETED.buttonStyle(palette)

                assertEquals(WidgetActionButtonShape.CIRCLE, available.shape)
                assertEquals(WidgetActionButtonShape.CIRCLE, completed.shape)
                assertEquals(WidgetActionButtonTreatment.OUTLINED, available.treatment)
                assertEquals(palette.primaryForeground, available.containerColor)
                assertEquals(palette.primaryForeground, available.iconColor)
                assertEquals(WidgetActionButtonTreatment.TONAL, completed.treatment)
                assertEquals(palette.primaryContainer, completed.containerColor)
                assertEquals(palette.onPrimaryContainer, completed.iconColor)
                assertEquals(0xFF, available.containerColor ushr 24)
                assertEquals(0xFF, available.iconColor ushr 24)
                assertEquals(0xFF, completed.containerColor ushr 24)
                assertEquals(0xFF, completed.iconColor ushr 24)
            }
        }
    }

    @Test
    fun `foreground contrast follows the resolved surface rather than requested mode`() {
        val unexpectedlyDarkDynamicSurface = WidgetPaletteResolver.resolve(
            WidgetAppearanceConfig(
                themeMode = WidgetThemeMode.LIGHT,
                colorScheme = WidgetColorScheme.MATERIAL_YOU_AUTO
            ),
            dark = false
        ) { name ->
            when (name) {
                "system_neutral1_10" -> 0xFF101114.toInt()
                "system_neutral1_900" -> 0xFF17181B.toInt()
                "system_neutral2_700" -> 0xFF202124.toInt()
                "system_accent1_600" -> 0xFF24262A.toInt()
                else -> null
            }
        }

        assertTrue(
            WidgetPaletteResolver.contrastRatio(
                unexpectedlyDarkDynamicSurface.onSurface,
                unexpectedlyDarkDynamicSurface.surface
            ) >= 4.5
        )
        assertTrue(
            WidgetPaletteResolver.contrastRatio(
                unexpectedlyDarkDynamicSurface.primaryForeground,
                unexpectedlyDarkDynamicSurface.surface
            ) >= 4.5
        )
    }

    @Test
    fun `all presets resolve readable opaque foregrounds independent of opacity`() {
        WidgetColorScheme.entries.forEach { scheme ->
            listOf(false, true).forEach { dark ->
                val low = WidgetPaletteResolver.resolve(
                    WidgetAppearanceConfig(
                        colorScheme = scheme,
                        backgroundOpacity = 0.3f
                    ),
                    dark
                )
                val opaque = WidgetPaletteResolver.resolve(
                    WidgetAppearanceConfig(
                        colorScheme = scheme,
                        backgroundOpacity = 1f
                    ),
                    dark
                )

                assertEquals(low.onSurface, opaque.onSurface)
                assertEquals(low.onSurfaceVariant, opaque.onSurfaceVariant)
                assertEquals(low.primaryForeground, opaque.primaryForeground)
                listOf(low.onSurface, low.onSurfaceVariant, low.primaryForeground).forEach {
                    assertEquals(0xFF, it ushr 24)
                    assertTrue(WidgetPaletteResolver.contrastRatio(it, low.surface) >= 4.5)
                }
                assertTrue(
                    WidgetPaletteResolver.contrastRatio(
                        low.onPrimaryContainer,
                        low.primaryContainer
                    ) >= 4.5
                )
            }
        }
    }
}

private class InMemoryWidgetAppearanceRepository : WidgetAppearanceRepository {
    private val values = mutableMapOf<Int, WidgetAppearanceConfig>()

    override fun read(appWidgetId: Int) = values[appWidgetId] ?: WidgetAppearanceConfig.Default

    override fun write(appWidgetId: Int, config: WidgetAppearanceConfig) {
        values[appWidgetId] = config.normalized()
    }

    override fun delete(appWidgetId: Int) {
        values.remove(appWidgetId)
    }
}
