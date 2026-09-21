package io.github.yingqiu0871.evolune

import androidx.lifecycle.ViewModelProvider
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.data.SettingsStore
import io.github.yingqiu0871.evolune.export.PortableExportService
import io.github.yingqiu0871.evolune.export.PortableImportService
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import io.github.yingqiu0871.evolune.history.HistoryReadService
import io.github.yingqiu0871.evolune.history.HistoryViewModelFactory
import io.github.yingqiu0871.evolune.history.insights.InsightsViewModelFactory
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkService
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectivePkViewModelFactory
import io.github.yingqiu0871.evolune.history.timeline.TimelineViewModelFactory
import java.time.Clock

/**
 * Activity-owned service and factory graph used by the composition root.
 *
 * This graph is created after startup restore/recovery has completed and is captured by the
 * Activity's setContent lambda. Compose recomposition only reads these references; it does not
 * reconstruct the graph. A new MainActivity creates a new graph instance, so no process-global
 * service ownership is introduced.
 */
internal class MainFeatureServices private constructor(
    internal val backupRestoreViewModelFactory: ViewModelProvider.Factory,
    internal val hrtViewModelFactory: ViewModelProvider.Factory,
    internal val historyReadService: HistoryReadService,
    internal val historyViewModelFactory: ViewModelProvider.Factory,
    internal val insightsViewModelFactory: InsightsViewModelFactory,
    internal val retrospectivePkService: RetrospectivePkService,
    internal val historyRangeSource: HistoryRangeSource,
    internal val retrospectiveViewModelFactory: RetrospectivePkViewModelFactory,
    internal val timelineViewModelFactory: TimelineViewModelFactory,
    internal val portableExportService: PortableExportService,
    internal val portableImportService: PortableImportService,
    internal val medicationPlanViewModelFactory: ViewModelProvider.Factory
) {
    companion object {
        fun create(
            medicationPlans: MedicationPlanRepository,
            doseEvents: DoseEventRepository,
            settingsStore: SettingsStore,
            backupRestoreViewModelFactory: ViewModelProvider.Factory,
            hrtViewModelFactory: ViewModelProvider.Factory,
            medicationPlanViewModelFactory: ViewModelProvider.Factory,
            exportClock: Clock = Clock.systemUTC()
        ): MainFeatureServices {
            val historyReadService = HistoryReadService(
                medicationPlans = medicationPlans,
                doseEvents = doseEvents
            )
            val historyRangeSource = HistoryRangeSource { startDate, endDate, zone, now ->
                historyReadService.readRange(startDate, endDate, zone, now)
            }
            val retrospectivePkService = RetrospectivePkService(historyReadService)

            return MainFeatureServices(
                backupRestoreViewModelFactory = backupRestoreViewModelFactory,
                hrtViewModelFactory = hrtViewModelFactory,
                historyReadService = historyReadService,
                historyViewModelFactory = HistoryViewModelFactory(historyRangeSource),
                insightsViewModelFactory = InsightsViewModelFactory(historyReadService),
                retrospectivePkService = retrospectivePkService,
                historyRangeSource = historyRangeSource,
                retrospectiveViewModelFactory = RetrospectivePkViewModelFactory(
                    retrospectivePkSource = retrospectivePkService,
                    allAvailableHistorySource = historyReadService,
                    historyRangeSource = historyRangeSource,
                    settingsStore = settingsStore
                ),
                timelineViewModelFactory = TimelineViewModelFactory(
                    rangeSource = historyRangeSource
                ),
                portableExportService = PortableExportService(
                    repository = doseEvents,
                    clock = exportClock
                ),
                portableImportService = PortableImportService(
                    repository = doseEvents
                ),
                medicationPlanViewModelFactory = medicationPlanViewModelFactory
            )
        }
    }
}
