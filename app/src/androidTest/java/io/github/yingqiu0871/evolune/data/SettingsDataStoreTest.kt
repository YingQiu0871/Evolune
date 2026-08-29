package io.github.yingqiu0871.evolune.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDataStoreTest {
    private val localTime = Instant.parse("2026-08-23T10:10:00Z")
    private val observationTime = Instant.parse("2026-08-23T10:00:00Z")
    private val newerObservationTime = Instant.parse("2026-08-23T10:11:00Z")
    private lateinit var store: SettingsDataStore

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        store = SettingsDataStore(
            context = context,
            clock = Clock.fixed(localTime, ZoneOffset.UTC)
        )
    }

    @Test
    fun `manual body weight writes local barrier atomically and keeps HC weight metadata`() =
        runBlocking {
            assertTrue(store.updateBodyWeightFromHealthConnect(61.1, observationTime))

            assertTrue(store.updateBodyWeight(62.2))

            val settings = store.userSettings.first()
            assertEquals(62.2, settings.bodyWeight, 0.0)
            assertEquals(61.1, requireNotNull(settings.lastHealthConnectWeightKg), 0.0)
            assertEquals(localTime, settings.lastHealthConnectWeightAdoptedAt)
        }

    @Test
    fun `restore settings writes local barrier and keeps HC weight metadata`() = runBlocking {
        assertTrue(store.updateBodyWeightFromHealthConnect(61.1, observationTime))
        val restored = UserSettings(
            bodyWeight = 62.2,
            themeMode = ThemeMode.DARK,
            colorTheme = ColorTheme.BUILTIN,
            autoCheckUpdates = false,
            timeFormat = TimeFormat.HOUR_24
        )

        assertTrue(store.replaceSettings(restored))

        val settings = store.userSettings.first()
        assertEquals(restored.bodyWeight, settings.bodyWeight, 0.0)
        assertEquals(restored.themeMode, settings.themeMode)
        assertEquals(restored.colorTheme, settings.colorTheme)
        assertEquals(restored.autoCheckUpdates, settings.autoCheckUpdates)
        assertEquals(restored.timeFormat, settings.timeFormat)
        assertEquals(61.1, requireNotNull(settings.lastHealthConnectWeightKg), 0.0)
        assertEquals(localTime, settings.lastHealthConnectWeightAdoptedAt)
    }

    @Test
    fun `Health Connect adoption uses observation timestamp rather than local clock`() = runBlocking {
        assertTrue(store.updateBodyWeightFromHealthConnect(61.1, observationTime))

        val settings = store.userSettings.first()
        assertEquals(61.1, settings.bodyWeight, 0.0)
        assertEquals(61.1, requireNotNull(settings.lastHealthConnectWeightKg), 0.0)
        assertEquals(observationTime, settings.lastHealthConnectWeightAdoptedAt)
    }

    @Test
    fun `same-value Health Connect metadata update advances observation barrier only`() =
        runBlocking {
            assertTrue(store.updateBodyWeightFromHealthConnect(61.1, observationTime))
            assertTrue(store.updateHealthConnectWeightMetadata(61.1, newerObservationTime))

            val settings = store.userSettings.first()
            assertEquals(61.1, settings.bodyWeight, 0.0)
            assertEquals(61.1, requireNotNull(settings.lastHealthConnectWeightKg), 0.0)
            assertEquals(newerObservationTime, settings.lastHealthConnectWeightAdoptedAt)
        }
}
