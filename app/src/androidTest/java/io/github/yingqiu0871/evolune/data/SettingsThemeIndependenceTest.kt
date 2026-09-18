package io.github.yingqiu0871.evolune.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/**
 * v1.7.2 Slice C — ThemeMode and the canonical color identity are independent in BOTH
 * directions on the real settings DataStore.
 */
@RunWith(AndroidJUnit4::class)
class SettingsThemeIndependenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = SettingsDataStore(context, Clock.systemUTC())

    @Before
    fun reset() {
        runBlocking { store.replaceSettings(UserSettings()) }
    }

    @After
    fun restore() {
        runBlocking { store.replaceSettings(UserSettings()) }
    }

    @Test
    fun changingThemeModeNeverRewritesTheColorIdentity() = runBlocking {
        store.updateThemeMode(ThemeMode.AMOLED)
        val afterMode = store.userSettings.first()
        assertEquals(ThemeMode.AMOLED, afterMode.themeMode)
        assertEquals(ThemeColorSource.DYNAMIC, afterMode.themeColorSource)
        assertNull(afterMode.themePreset)
        assertEquals(ColorTheme.DYNAMIC, afterMode.colorTheme)

        store.updateThemePreset(ThemePresetSelection.Preset(PresetPalette.MONET_AMBER))
        store.updateThemeMode(ThemeMode.SYSTEM)
        val afterSecondMode = store.userSettings.first()
        assertEquals(ThemeMode.SYSTEM, afterSecondMode.themeMode)
        assertEquals(ThemeColorSource.PRESET, afterSecondMode.themeColorSource)
        assertEquals(
            ThemePresetSelection.Preset(PresetPalette.MONET_AMBER),
            afterSecondMode.themePreset
        )
        assertEquals(ColorTheme.BUILTIN, afterSecondMode.colorTheme)
    }

    @Test
    fun changingColorIdentityNeverRewritesThemeMode() = runBlocking {
        store.updateThemeMode(ThemeMode.AMOLED)
        store.updateThemePreset(ThemePresetSelection.Preset(PresetPalette.MONET_BLUE))
        assertEquals(ThemeMode.AMOLED, store.userSettings.first().themeMode)

        store.updateThemeColorSource(ThemeColorSource.DYNAMIC)
        val afterDynamic = store.userSettings.first()
        assertEquals(ThemeMode.AMOLED, afterDynamic.themeMode)
        assertEquals(ThemeColorSource.DYNAMIC, afterDynamic.themeColorSource)
        assertNull(afterDynamic.themePreset)

        store.updateThemePreset(ThemePresetSelection.LegacyBuiltin)
        val afterLegacy = store.userSettings.first()
        assertEquals(ThemeMode.AMOLED, afterLegacy.themeMode)
        assertEquals(ThemePresetSelection.LegacyBuiltin, afterLegacy.themePreset)
    }

    @Test
    fun systemDynamicAndAmoledPresetCombinationsStayIndependent() = runBlocking {
        store.updateThemeMode(ThemeMode.SYSTEM)
        store.updateThemeColorSource(ThemeColorSource.DYNAMIC)
        val systemDynamic = store.userSettings.first()
        assertEquals(ThemeMode.SYSTEM, systemDynamic.themeMode)
        assertEquals(ThemeColorSource.DYNAMIC, systemDynamic.themeColorSource)

        store.updateThemeMode(ThemeMode.AMOLED)
        store.updateThemePreset(ThemePresetSelection.Preset(PresetPalette.MONET_MINT))
        val amoledPreset = store.userSettings.first()
        assertEquals(ThemeMode.AMOLED, amoledPreset.themeMode)
        assertEquals(ThemePresetSelection.Preset(PresetPalette.MONET_MINT), amoledPreset.themePreset)
    }
}
