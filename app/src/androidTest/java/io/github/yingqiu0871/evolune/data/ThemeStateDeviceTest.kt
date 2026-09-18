package io.github.yingqiu0871.evolune.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 * v1.7.2 Slice B — canonical theme state on the real settings DataStore.
 *
 * Verifies the writer/read round-trips and the legacy compatibility projection of the ONE
 * persisted authority (no parallel store). Legacy-storage derivation (keys absent) is covered
 * by the pure [deriveThemeState] JVM matrix, since raw DataStore key injection would require a
 * second DataStore instance for the same file.
 */
@RunWith(AndroidJUnit4::class)
class ThemeStateDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = SettingsDataStore(context, Clock.systemUTC())
    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    @Before
    fun resetSettings() = runBlocking {
        store.replaceSettings(
            UserSettings(
                bodyWeight = 55.0,
                themeMode = ThemeMode.SYSTEM,
                autoCheckUpdates = true,
                timeFormat = TimeFormat.SYSTEM
            )
        )
        Unit
    }

    @After
    fun close() {
        scope.cancel()
    }

    @Test
    fun canonicalDynamicWriteRoundTrips() = runBlocking {
        store.updateThemeColorSource(ThemeColorSource.DYNAMIC)

        val settings = store.userSettings.first()
        assertEquals(ThemeColorSource.DYNAMIC, settings.themeColorSource)
        assertNull(settings.themePreset)
        assertEquals(ColorTheme.DYNAMIC, settings.colorTheme)
    }

    @Test
    fun everyPresetWriteRoundTripsAndSyncsTheLegacyProjection() = runBlocking {
        PresetPalette.entries.forEach { palette ->
            store.updateThemePreset(ThemePresetSelection.Preset(palette))

            val settings = store.userSettings.first()
            assertEquals(ThemeColorSource.PRESET, settings.themeColorSource)
            assertEquals(ThemePresetSelection.Preset(palette), settings.themePreset)
            assertEquals(ColorTheme.BUILTIN, settings.colorTheme)
        }
    }

    @Test
    fun legacyBuiltinWriteRoundTrips() = runBlocking {
        store.updateThemePreset(ThemePresetSelection.LegacyBuiltin)

        val settings = store.userSettings.first()
        assertEquals(ThemeColorSource.PRESET, settings.themeColorSource)
        assertEquals(ThemePresetSelection.LegacyBuiltin, settings.themePreset)
        assertEquals(ColorTheme.BUILTIN, settings.colorTheme)
    }

    @Test
    fun oldUiColorThemeApiMapsToCanonicalState() = runBlocking {
        store.updateColorTheme(ColorTheme.BUILTIN)
        val builtin = store.userSettings.first()
        assertEquals(ThemeColorSource.PRESET, builtin.themeColorSource)
        assertEquals(ThemePresetSelection.LegacyBuiltin, builtin.themePreset)
        assertEquals(ColorTheme.BUILTIN, builtin.colorTheme)

        store.updateColorTheme(ColorTheme.DYNAMIC)
        val dynamic = store.userSettings.first()
        assertEquals(ThemeColorSource.DYNAMIC, dynamic.themeColorSource)
        assertNull(dynamic.themePreset)
        assertEquals(ColorTheme.DYNAMIC, dynamic.colorTheme)
    }

    @Test
    fun switchingBackToDynamicClearsTheActivePreset() = runBlocking {
        store.updateThemePreset(ThemePresetSelection.Preset(PresetPalette.MONET_SAKURA))
        store.updateThemeColorSource(ThemeColorSource.DYNAMIC)

        val settings = store.userSettings.first()
        assertEquals(ThemeColorSource.DYNAMIC, settings.themeColorSource)
        assertNull(settings.themePreset)
        assertEquals(ColorTheme.DYNAMIC, settings.colorTheme)
    }

    @Test
    fun replaceSettingsWritesTheCanonicalIdentityAtomically() = runBlocking {
        store.replaceSettings(
            UserSettings(
                bodyWeight = 61.5,
                themeMode = ThemeMode.DARK,
                themeColorSource = ThemeColorSource.PRESET,
                themePreset = ThemePresetSelection.Preset(PresetPalette.MONET_AMBER),
                autoCheckUpdates = false,
                timeFormat = TimeFormat.HOUR_24
            )
        )

        val settings = store.userSettings.first()
        assertEquals(61.5, settings.bodyWeight, 0.0)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(ThemeColorSource.PRESET, settings.themeColorSource)
        assertEquals(
            ThemePresetSelection.Preset(PresetPalette.MONET_AMBER),
            settings.themePreset
        )
        assertEquals(ColorTheme.BUILTIN, settings.colorTheme)
        assertEquals(false, settings.autoCheckUpdates)
        assertEquals(TimeFormat.HOUR_24, settings.timeFormat)

        store.replaceSettings(
            UserSettings(
                bodyWeight = 55.0,
                themeMode = ThemeMode.SYSTEM,
                themeColorSource = ThemeColorSource.PRESET,
                themePreset = ThemePresetSelection.LegacyBuiltin,
                autoCheckUpdates = true,
                timeFormat = TimeFormat.SYSTEM
            )
        )
        val legacy = store.userSettings.first()
        assertEquals(ThemePresetSelection.LegacyBuiltin, legacy.themePreset)
        assertEquals(ColorTheme.BUILTIN, legacy.colorTheme)
    }
}
