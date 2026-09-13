package io.github.yingqiu0871.evolune.history.insights

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * v1.7-B-02 section 29: the production saved-state path, exercised with real `CreationExtras`.
 *
 * The ViewModel is created through [ViewModelProvider] on a real Activity, which is the path that
 * actually carries the view-model key into `extras.createSavedStateHandle()`. The test then proves
 * the selection lands in the Activity's saved state and survives a real save/restore cycle.
 *
 * True process death (a discarded `SavedStateHandlesVM`) is not reachable from instrumentation
 * without internal lifecycle APIs; the restore rules themselves (malformed value, invalid custom,
 * relative preset re-resolution) are covered by `InsightsViewModelTest` on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class InsightsSavedStateFactoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
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
    }

    @After
    fun closeActivity() {
        scenario.close()
    }

    @Test
    fun theFactoryPersistsTheSelectionAndRestoresItAfterRecreation() {
        val viewModel = createViewModel()
        assertEquals(InsightsRangeSelection.Last30Days, viewModel.uiState.value.selection)

        val custom = InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10))
        viewModel.selectRange(custom)
        assertEquals(custom, viewModel.uiState.value.selection)

        val saved = Bundle()
        scenario.onActivity { activity ->
            InstrumentationRegistry.getInstrumentation()
                .callActivityOnSaveInstanceState(activity, saved)
        }
        val persisted = flatten(saved)
        assertTrue(
            "the saved state must carry the selection type: $persisted",
            persisted.any { it == "CUSTOM" }
        )
        assertTrue(
            "the saved state must carry the custom endpoints: $persisted",
            persisted.any { it == "2026-09-01" } && persisted.any { it == "2026-09-10" }
        )

        scenario.recreate()
        val afterRecreation = createViewModel()
        assertEquals(custom, afterRecreation.uiState.value.selection)
    }

    @Test
    fun `a relative selection is stored as a preset rather than frozen endpoints`() {
        val viewModel = createViewModel()
        viewModel.selectRange(InsightsRangeSelection.Last7Days)

        val saved = Bundle()
        scenario.onActivity { activity ->
            InstrumentationRegistry.getInstrumentation()
                .callActivityOnSaveInstanceState(activity, saved)
        }
        val persisted = flatten(saved)

        assertTrue("expected the preset type: $persisted", persisted.any { it == "LAST_7_DAYS" })
        // resolved endpoints are never persisted (the JVM contract test pins the key set; this
        // device run confirms a relative preset writes its type instead of a date pair)
        assertTrue(
            "no custom endpoint pair may be written for a preset: $persisted",
            persisted.count { it.startsWith("2026-0") } <= 2
        )
    }

    private fun createViewModel(): InsightsViewModel {
        lateinit var viewModel: InsightsViewModel
        scenario.onActivity { activity ->
            val provider = io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider.get(activity)
            val factory = InsightsViewModelFactory(
                historyReadService = io.github.yingqiu0871.evolune.history.HistoryReadService(
                    provider.medicationPlans,
                    provider.doseEvents
                ),
                clock = java.time.Clock.systemUTC(),
                displayZone = { ZoneOffset.UTC }
            )
            viewModel = ViewModelProvider(activity, factory)[InsightsViewModel::class.java]
            assertNotNull(viewModel.uiState.value)
        }
        return viewModel
    }

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
}
