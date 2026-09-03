package io.github.yingqiu0871.evolune.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {
    @Test
    fun freshStateIsIncomplete() {
        assertFalse(OnboardingState().isComplete)
        assertTrue(OnboardingState().needsBeginnerOnboarding)
    }

    @Test
    fun currentAcknowledgementsCompleteTheGate() {
        val state = OnboardingState(
                completedOnboardingVersion = CURRENT_ONBOARDING_VERSION,
                acceptedTermsVersion = CURRENT_TERMS_VERSION,
                acknowledgedMedicalPkDisclosureVersion = CURRENT_MEDICAL_PK_DISCLOSURE_VERSION
            )

        assertTrue(state.isComplete)
        assertFalse(state.featureTutorialAutoLaunchPending)
    }

    @Test
    fun anOlderAcknowledgementIsStale() {
        val completed = OnboardingState(
            completedOnboardingVersion = CURRENT_ONBOARDING_VERSION,
            acceptedTermsVersion = CURRENT_TERMS_VERSION,
            acknowledgedMedicalPkDisclosureVersion = CURRENT_MEDICAL_PK_DISCLOSURE_VERSION - 1
        )

        assertTrue(completed.hasAcceptedTerms)
        assertFalse(completed.hasAcknowledgedMedicalPkDisclosure)
        assertFalse(completed.isComplete)
    }

    @Test
    fun migratedExistingInstallationKeepsDisclosureGateWithoutBeginnerTour() {
        val migrated = OnboardingState(
            beginnerOnboardingVersion = CURRENT_ONBOARDING_VERSION
        )

        assertFalse(migrated.needsBeginnerOnboarding)
        assertFalse(migrated.isComplete)
    }

    @Test
    fun featureTutorialPendingDoesNotChangeLegalCompletion() {
        val state = OnboardingState(
            completedOnboardingVersion = CURRENT_ONBOARDING_VERSION,
            acceptedTermsVersion = CURRENT_TERMS_VERSION,
            acknowledgedMedicalPkDisclosureVersion = CURRENT_MEDICAL_PK_DISCLOSURE_VERSION,
            featureTutorialAutoLaunchPending = true
        )

        assertTrue(state.isComplete)
    }
}
