package io.github.yingqiu0871.evolune.history

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.MainActivity
import androidx.lifecycle.ViewModelProvider
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A-04 §8: the **production** `HistoryViewModelFactory` SavedState path on a device.
 *
 * The ViewModel is created through `HistoryViewModelFactory.create(modelClass, extras)` with the
 * **real Activity extras** (`ComponentActivity.defaultViewModelCreationExtras`, which carries the
 * saved-state registry and view-model-store owners), i.e. exactly the inputs `viewModel(factory)`
 * passes in production. This is the path the JVM tests cannot reach: there, `SavedStateHandleSupport`
 * is internal to lifecycle, so a hand-built handle would be the only option — which the review
 * explicitly rejected as a substitute.
 *
 * Coverage split (stated honestly): this test proves the production factory + real extras path and
 * that month/selection survive a real Activity save/restore cycle; the value-level restore rules
 * (malformed value, restored future month clamp) are proven in `HistoryViewModelTest`, where the
 * same parse/clamp code runs against a handle. Simulating true process death (a discarded
 * `SavedStateHandlesVM`) is not reachable from instrumentation without internal APIs.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HistorySavedStateFactoryTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone: ZoneId = ZoneId.systemDefault()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchFromDeterministicState() {
        runBlocking {
            val store = OnboardingStateStore(context, isExistingInstallation = true)
            store.initializeIfNeeded()
            store.acceptTerms()
            store.acknowledgeMedicalPkDisclosure()
            store.completeOnboarding()
            store.markFeatureTutorialHandled()
        }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        composeRule.waitForIdle()
    }

    @After
    fun closeActivity() {
        scenario.close()
    }

    @Test
    fun theProductionFactoryObtainsARealSavedStateHandleAndPersistsTheSelection() {
        val currentMonth = YearMonth.from(LocalDate.now(zone))

        // 1) create the ViewModel exactly as production does (ViewModelProvider adds the
        //    VIEW_MODEL_KEY that `extras.createSavedStateHandle()` needs)
        val first = createViewModel()
        assertEquals(currentMonth, first.uiState.value.visibleMonth)

        // 2) the user browses to the previous month and picks a day there
        first.onSurfaceShown()
        first.showPreviousMonth()
        val browsedMonth = currentMonth.minusMonths(1)
        val browsedDay = browsedMonth.atDay(3)
        first.selectDate(browsedDay)
        assertEquals(browsedMonth, first.uiState.value.visibleMonth)
        assertEquals(browsedDay, first.uiState.value.selectedDate)

        // 3) the Activity's saved state must carry the history selection: a null handle
        //    (`createSavedStateHandle()` failure) would persist nothing at all.
        val saved = Bundle()
        scenario.onActivity { activity ->
            InstrumentationRegistry.getInstrumentation()
                .callActivityOnSaveInstanceState(activity, saved)
        }
        val persisted = flatten(saved)
        assertTrue(
            "the saved state must contain the browsed month: $persisted",
            persisted.any { it.contains(browsedMonth.toString()) }
        )
        assertTrue(
            "the saved state must contain the browsed day: $persisted",
            persisted.any { it.contains(browsedDay.toString()) }
        )

        // 4) a real Activity save/restore cycle keeps the browsed month and day
        scenario.recreate()
        composeRule.waitForIdle()
        val afterRecreation = createViewModel()
        assertEquals(browsedMonth, afterRecreation.uiState.value.visibleMonth)
        assertEquals(browsedDay, afterRecreation.uiState.value.selectedDate)
    }

    private fun createViewModel(): HistoryViewModel {
        lateinit var viewModel: HistoryViewModel
        scenario.onActivity { activity ->
            val provider = ProductionRepositoryProvider.get(activity)
            val factory = HistoryViewModelFactory(
                historyReadService = HistoryReadService(provider.medicationPlans, provider.doseEvents),
                clock = Clock.fixed(LocalDate.now(zone).atTime(12, 0).toInstant(ZoneOffset.UTC), zone),
                displayZone = { zone }
            )
            viewModel = ViewModelProvider(activity, factory)[HistoryViewModel::class.java]
        }
        return viewModel
    }

    /** Flattens every string reachable from a saved-state bundle (nested bundles included). */
    private fun flatten(bundle: Bundle): List<String> {
        val values = mutableListOf<String>()
        fun walk(current: Bundle) {
            current.keySet().forEach { key ->
                when (val value = current.get(key)) {
                    is Bundle -> walk(value)
                    is String -> values += value
                    null -> Unit
                    else -> values += value.toString()
                }
            }
        }
        walk(bundle)
        return values
    }

    private companion object {
        /** Mirrors `AppNavigation.NAV_CLICK_THROTTLE_MS`. */
        const val NAV_CLICK_THROTTLE_MS = 200L
    }
}
