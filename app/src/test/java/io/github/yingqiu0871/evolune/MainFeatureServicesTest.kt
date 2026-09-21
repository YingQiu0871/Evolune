package io.github.yingqiu0871.evolune

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.history.retrospective.FakeSettingsStore
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class MainFeatureServicesTest {
    @Test
    fun `same activity owner keeps service graph identities across root recomposition`() {
        val graph = createGraph()

        // The setContent lambda captures this one owner graph; recomposition only reads it.
        val beforeRecomposition = graph
        val afterRecomposition = graph

        assertSame(beforeRecomposition, afterRecomposition)
        assertSame(beforeRecomposition.historyReadService, afterRecomposition.historyReadService)
        assertSame(
            beforeRecomposition.insightsViewModelFactory,
            afterRecomposition.insightsViewModelFactory
        )
        assertSame(
            beforeRecomposition.retrospectiveViewModelFactory,
            afterRecomposition.retrospectiveViewModelFactory
        )
        assertSame(
            beforeRecomposition.timelineViewModelFactory,
            afterRecomposition.timelineViewModelFactory
        )
        assertSame(
            beforeRecomposition.portableExportService,
            afterRecomposition.portableExportService
        )
        assertSame(
            beforeRecomposition.portableImportService,
            afterRecomposition.portableImportService
        )
    }

    @Test
    fun `new activity owner creates a fresh service graph`() {
        val oldOwner = createGraph()
        val newOwner = createGraph()

        assertNotSame(oldOwner, newOwner)
        assertNotSame(oldOwner.historyReadService, newOwner.historyReadService)
        assertNotSame(oldOwner.insightsViewModelFactory, newOwner.insightsViewModelFactory)
        assertNotSame(
            oldOwner.retrospectiveViewModelFactory,
            newOwner.retrospectiveViewModelFactory
        )
        assertNotSame(oldOwner.timelineViewModelFactory, newOwner.timelineViewModelFactory)
        assertNotSame(oldOwner.portableExportService, newOwner.portableExportService)
        assertNotSame(oldOwner.portableImportService, newOwner.portableImportService)
    }

    @Test
    fun `history consumers share the activity owned reader and range seam`() {
        val graph = createGraph()

        assertSame(
            graph.historyRangeSource,
            privateField(graph.historyViewModelFactory, "rangeSource")
        )
        assertSame(
            graph.historyReadService,
            privateField(graph.insightsViewModelFactory, "historyReadService")
        )
        assertSame(
            graph.historyReadService,
            privateField(graph.retrospectivePkService, "history")
        )
        assertSame(
            graph.historyReadService,
            privateField(graph.retrospectiveViewModelFactory, "allAvailableHistorySource")
        )
        assertSame(
            graph.historyRangeSource,
            privateField(graph.retrospectiveViewModelFactory, "historyRangeSource")
        )
        assertSame(
            graph.historyRangeSource,
            privateField(graph.timelineViewModelFactory, "rangeSource")
        )
    }

    private fun createGraph(): MainFeatureServices = MainFeatureServices.create(
        medicationPlans = FakeMedicationPlanRepository(),
        doseEvents = FakeDoseEventRepository(),
        settingsStore = FakeSettingsStore(),
        backupRestoreViewModelFactory = NoOpFactory,
        hrtViewModelFactory = NoOpFactory,
        medicationPlanViewModelFactory = NoOpFactory
    )

    private fun privateField(target: Any, name: String): Any {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            runCatching {
                return requireNotNull(
                    type.getDeclaredField(name).apply { isAccessible = true }.get(target)
                )
            }
            type = type.superclass
        }
        error("Missing field $name on ${target.javaClass.name}")
    }

    private object NoOpFactory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            error("No ViewModel should be created by the ownership test")
    }
}
