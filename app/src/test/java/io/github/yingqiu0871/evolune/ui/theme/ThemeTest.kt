package io.github.yingqiu0871.evolune.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import io.github.yingqiu0871.evolune.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {

    @Test
    fun amoledModeAlwaysUsesDarkSystemBars() {
        assertTrue(ThemeMode.AMOLED.usesDarkColors(systemInDarkTheme = false))
        assertTrue(ThemeMode.AMOLED.usesDarkColors(systemInDarkTheme = true))
        assertFalse(ThemeMode.LIGHT.usesDarkColors(systemInDarkTheme = true))
        assertTrue(ThemeMode.SYSTEM.usesDarkColors(systemInDarkTheme = true))
        assertFalse(ThemeMode.SYSTEM.usesDarkColors(systemInDarkTheme = false))
    }

    @Test
    fun amoledSchemeUsesBlackSurfacesAndPreservesSemanticForegrounds() {
        val base = darkColorScheme(
            primary = Color(0xFF81D5CD),
            onPrimary = Color(0xFF003734),
            onSurface = Color(0xFFDDE4E2),
            onSurfaceVariant = Color(0xFFBEC9C6)
        )

        val amoled = base.withAmoledSurfaces()

        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
        assertEquals(Color.Black, amoled.surfaceDim)
        assertEquals(Color.Black, amoled.surfaceContainerLowest)
        assertEquals(base.primary, amoled.primary)
        assertEquals(base.onPrimary, amoled.onPrimary)
        assertEquals(base.onSurface, amoled.onSurface)
        assertEquals(base.onSurfaceVariant, amoled.onSurfaceVariant)
    }
}
