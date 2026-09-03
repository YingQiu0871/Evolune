package io.github.yingqiu0871.evolune.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeatureTutorialRouteTest {
    @Test
    fun pendingFreshInstallStartsAtFeatureTutorialAfterLegalGate() {
        assertEquals(
            FEATURE_TUTORIAL_ROUTE,
            resolveAppStartRoute(
                explicitRoute = null,
                onboardingComplete = true,
                featureTutorialAutoLaunchPending = true
            )
        )
    }

    @Test
    fun explicitIntentRouteWinsOverTutorial() {
        assertEquals(
            "disclosures",
            resolveAppStartRoute(
                explicitRoute = "disclosures",
                onboardingComplete = true,
                featureTutorialAutoLaunchPending = true
            )
        )
    }

    @Test
    fun completedOrUpgradeStateDoesNotAutoLaunchTutorial() {
        assertNull(
            resolveAppStartRoute(
                explicitRoute = null,
                onboardingComplete = true,
                featureTutorialAutoLaunchPending = false
            )
        )
        assertNull(
            resolveAppStartRoute(
                explicitRoute = null,
                onboardingComplete = false,
                featureTutorialAutoLaunchPending = true
            )
        )
    }
}
