package io.github.yingqiu0871.evolune.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "onboarding")

const val CURRENT_ONBOARDING_VERSION = 1
const val CURRENT_TERMS_VERSION = 1
const val CURRENT_MEDICAL_PK_DISCLOSURE_VERSION = 1

data class OnboardingState(
    val initialized: Boolean = true,
    val beginnerOnboardingVersion: Int = 0,
    val completedOnboardingVersion: Int = 0,
    val acceptedTermsVersion: Int = 0,
    val acknowledgedMedicalPkDisclosureVersion: Int = 0,
    val featureTutorialAutoLaunchPending: Boolean = false
) {
    val needsBeginnerOnboarding: Boolean
        get() = beginnerOnboardingVersion < CURRENT_ONBOARDING_VERSION

    val isComplete: Boolean
        get() = completedOnboardingVersion >= CURRENT_ONBOARDING_VERSION &&
            acceptedTermsVersion >= CURRENT_TERMS_VERSION &&
            acknowledgedMedicalPkDisclosureVersion >= CURRENT_MEDICAL_PK_DISCLOSURE_VERSION

    val hasAcceptedTerms: Boolean
        get() = acceptedTermsVersion >= CURRENT_TERMS_VERSION

    val hasAcknowledgedMedicalPkDisclosure: Boolean
        get() = acknowledgedMedicalPkDisclosureVersion >= CURRENT_MEDICAL_PK_DISCLOSURE_VERSION
}

/**
 * Device-local UI state for the v1.4 trust and permission foundation.
 *
 * This store is intentionally separate from UserSettings and the backup
 * persistence model. Restoring medication data must never manufacture legal
 * acknowledgement on another installation.
 */
class OnboardingStateStore(
    private val context: Context,
    private val isExistingInstallation: Boolean = false,
    private val dataStore: DataStore<Preferences> = context.onboardingDataStore
) {
    private object Keys {
        val completedOnboardingVersion = intPreferencesKey("completed_onboarding_version")
        val beginnerOnboardingVersion = intPreferencesKey("beginner_onboarding_version")
        val acceptedTermsVersion = intPreferencesKey("accepted_terms_version")
        val acknowledgedMedicalPkDisclosureVersion =
            intPreferencesKey("acknowledged_medical_pk_disclosure_version")
        val featureTutorialAutoLaunchPending =
            booleanPreferencesKey("feature_tutorial_auto_launch_pending")
        val initialized = intPreferencesKey("initialized_for_v14")
    }

    val state: Flow<OnboardingState> = dataStore.data.map { preferences ->
        OnboardingState(
            initialized = preferences[Keys.initialized] == 1,
            beginnerOnboardingVersion = preferences[Keys.beginnerOnboardingVersion] ?: 0,
            completedOnboardingVersion = preferences[Keys.completedOnboardingVersion] ?: 0,
            acceptedTermsVersion = preferences[Keys.acceptedTermsVersion] ?: 0,
            acknowledgedMedicalPkDisclosureVersion =
                preferences[Keys.acknowledgedMedicalPkDisclosureVersion] ?: 0,
            featureTutorialAutoLaunchPending =
                preferences[Keys.featureTutorialAutoLaunchPending] ?: false
        )
    }

    /**
     * Initializes the new v1.4 state exactly once. Existing installations
     * inherit the beginner-tour completion boundary while still reviewing
     * the current Terms/Privacy and Medical/PK disclosures.
     */
    suspend fun initializeIfNeeded() {
        dataStore.edit { preferences ->
            if (preferences[Keys.initialized] != 1) {
                preferences[Keys.initialized] = 1
                preferences[Keys.beginnerOnboardingVersion] =
                    if (isExistingInstallation) CURRENT_ONBOARDING_VERSION else 0
                preferences[Keys.featureTutorialAutoLaunchPending] = !isExistingInstallation
            }
        }
    }

    suspend fun acceptTerms() {
        dataStore.edit { preferences ->
            preferences[Keys.acceptedTermsVersion] = CURRENT_TERMS_VERSION
        }
    }

    suspend fun acknowledgeMedicalPkDisclosure() {
        dataStore.edit { preferences ->
            preferences[Keys.acknowledgedMedicalPkDisclosureVersion] =
                CURRENT_MEDICAL_PK_DISCLOSURE_VERSION
        }
    }

    suspend fun completeOnboarding() {
        dataStore.edit { preferences ->
            preferences[Keys.beginnerOnboardingVersion] = CURRENT_ONBOARDING_VERSION
            preferences[Keys.completedOnboardingVersion] = CURRENT_ONBOARDING_VERSION
        }
    }

    suspend fun markFeatureTutorialHandled() {
        dataStore.edit { preferences ->
            preferences[Keys.featureTutorialAutoLaunchPending] = false
        }
    }
}
