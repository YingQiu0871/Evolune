package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.onboarding.CURRENT_MEDICAL_PK_DISCLOSURE_VERSION
import io.github.yingqiu0871.evolune.onboarding.CURRENT_ONBOARDING_VERSION
import io.github.yingqiu0871.evolune.onboarding.CURRENT_TERMS_VERSION
import io.github.yingqiu0871.evolune.onboarding.OnboardingState
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Rule
import org.junit.Test

class OnboardingFlowScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun requiredAcknowledgementsGateEachRequiredStep() {
        var state by mutableStateOf(OnboardingState())
        composeRule.setContent {
            EvoluneTheme {
                OnboardingFlowScreen(
                    state = state,
                    onAcceptTerms = {
                        state = state.copy(acceptedTermsVersion = CURRENT_TERMS_VERSION)
                    },
                    onAcknowledgeMedicalPkDisclosure = {
                        state = state.copy(
                            acknowledgedMedicalPkDisclosureVersion =
                                CURRENT_MEDICAL_PK_DISCLOSURE_VERSION
                        )
                    },
                    onComplete = {},
                    showTopBar = false
                )
            }
        }

        composeRule.onNodeWithTag("onboarding-flow").assertIsDisplayed()
        repeat(2) {
            composeRule.onNodeWithTag("onboarding-next").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("onboarding-next").assertIsNotEnabled()
        composeRule.onNodeWithTag("onboarding-terms-checkbox-control").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding-next").assertIsEnabled().performClick()

        composeRule.onNodeWithTag("onboarding-next").assertIsNotEnabled()
        composeRule.onNodeWithTag("onboarding-medical-pk-checkbox-control")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("onboarding-next").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("onboarding-finish").assertIsEnabled()
    }

    @Test
    fun currentAcknowledgementsAllowFinish() {
        var state by mutableStateOf(OnboardingState())
        var completed = false
        composeRule.setContent {
            EvoluneTheme {
                OnboardingFlowScreen(
                    state = state,
                    onAcceptTerms = {
                        state = state.copy(acceptedTermsVersion = CURRENT_TERMS_VERSION)
                    },
                    onAcknowledgeMedicalPkDisclosure = {
                        state = state.copy(
                            acknowledgedMedicalPkDisclosureVersion =
                                CURRENT_MEDICAL_PK_DISCLOSURE_VERSION
                        )
                    },
                    onComplete = { completed = true },
                    showTopBar = false
                )
            }
        }

        repeat(2) {
            composeRule.onNodeWithTag("onboarding-next").performScrollTo().performClick()
        }
        composeRule.onNodeWithText(string(R.string.disclosure_privacy_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-next").assertIsNotEnabled()
        composeRule.onNodeWithTag("onboarding-terms-checkbox-control").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding-next").assertIsEnabled().performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding-next").assertIsNotEnabled()
        composeRule.onNodeWithTag("onboarding-medical-pk-checkbox-control").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding-next").assertIsEnabled().performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding-finish").performScrollTo().performClick()

        composeRule.runOnIdle { check(completed) }
    }

    @Test
    fun migratedInstallationStartsAtRequiredDisclosureReview() {
        composeRule.setContent {
            EvoluneTheme {
                OnboardingFlowScreen(
                    state = OnboardingState(
                        beginnerOnboardingVersion = CURRENT_ONBOARDING_VERSION
                    ),
                    beginnerOnboarding = false,
                    onAcceptTerms = {},
                    onAcknowledgeMedicalPkDisclosure = {},
                    onComplete = {},
                    showTopBar = false
                )
            }
        }

        composeRule.onNodeWithText("使用条款与隐私说明").assertIsDisplayed()
        check(
            composeRule.onAllNodesWithText("先了解，再开始记录")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    private fun string(resourceId: Int): String =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(resourceId)
}
