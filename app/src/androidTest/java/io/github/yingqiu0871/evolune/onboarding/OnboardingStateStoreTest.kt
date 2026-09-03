package io.github.yingqiu0871.evolune.onboarding

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.yingqiu0871.evolune.data.SettingsDataStore
import io.github.yingqiu0871.evolune.data.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class OnboardingStateStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun freshInstallationPersistsAcknowledgementsAndCompletion() = runBlocking {
        val file = testFile()
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        val store = OnboardingStateStore(
            context = context,
            isExistingInstallation = false,
            dataStore = dataStore
        )

        store.initializeIfNeeded()
        assertTrue(store.state.first().initialized)
        assertTrue(store.state.first().needsBeginnerOnboarding)
        assertTrue(store.state.first().featureTutorialAutoLaunchPending)

        store.acceptTerms()
        store.acknowledgeMedicalPkDisclosure()
        store.completeOnboarding()

        val reattachedStore = OnboardingStateStore(
            context = context,
            isExistingInstallation = false,
            dataStore = dataStore
        )
        val persisted = reattachedStore.state.first()
        assertTrue(persisted.isComplete)
        assertFalse(persisted.needsBeginnerOnboarding)

        reattachedStore.markFeatureTutorialHandled()
        assertFalse(reattachedStore.state.first().featureTutorialAutoLaunchPending)
    }

    @Test
    fun existingInstallationSkipsBeginnerTourButRequiresCurrentDisclosures() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile() })
        val store = OnboardingStateStore(
            context = context,
            isExistingInstallation = true,
            dataStore = dataStore
        )

        store.initializeIfNeeded()
        val migrated = store.state.first()

        assertTrue(migrated.initialized)
        assertFalse(migrated.needsBeginnerOnboarding)
        assertFalse(migrated.featureTutorialAutoLaunchPending)
        assertFalse(migrated.isComplete)
        assertFalse(migrated.hasAcceptedTerms)
        assertFalse(migrated.hasAcknowledgedMedicalPkDisclosure)
    }

    @Test
    fun settingsRestoreDoesNotManufactureOnboardingAcknowledgement() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile() })
        val store = OnboardingStateStore(
            context = context,
            isExistingInstallation = false,
            dataStore = dataStore
        )
        store.initializeIfNeeded()

        assertFalse(store.state.first().isComplete)
        assertTrue(SettingsDataStore(context).replaceSettings(UserSettings(bodyWeight = 72.0)))
        assertFalse(store.state.first().isComplete)
        assertTrue(store.state.first().featureTutorialAutoLaunchPending)
    }

    private fun testFile(): File = File(
        context.cacheDir,
        "onboarding-test-${UUID.randomUUID()}.preferences_pb"
    )
}
