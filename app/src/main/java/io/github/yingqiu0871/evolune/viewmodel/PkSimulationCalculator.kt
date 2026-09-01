package io.github.yingqiu0871.evolune.viewmodel

import io.github.yingqiu0871.evolune.core.adapter.DomainDoseEventToPkAdapter
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import io.github.yingqiu0871.evolune.utils.MedicationPlanPredictor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ceil

private const val SIMULATION_POINTS_PER_HOUR = 12.0

internal data class PkSimulationInput(
    val now: Instant,
    val currentTimeH: Double,
    val historicalDoseEvents: List<DoseEvent>,
    val enabledPlans: List<MedicationPlan>,
    val bodyWeightKG: Double,
    val zoneId: ZoneId
)

internal fun interface PkSimulationCalculator {
    suspend fun calculate(input: PkSimulationInput): PKState
}

internal object DefaultPkSimulationCalculator : PkSimulationCalculator {
    override suspend fun calculate(input: PkSimulationInput): PKState {
        val historicalEvents = DomainDoseEventToPkAdapter.adapt(
            input.historicalDoseEvents.filter { event ->
                event.status == DoseEventStatus.RECORDED &&
                    event.route != Route.ANTIANDROGEN
            }
        )
        val plans = input.enabledPlans.filter { it.route != Route.ANTIANDROGEN }
        val futureEvents = if (plans.isNotEmpty()) {
            val predicted = MedicationPlanPredictor.generateFutureEventsForDomainPlans(
                plans = plans,
                fromDateTime = LocalDateTime.ofInstant(input.now, input.zoneId),
                daysAhead = 15
            )
            MedicationPlanPredictor.filterConflictingPredictions(
                predictedEvents = predicted,
                actualEvents = historicalEvents
            )
        } else {
            emptyList()
        }
        currentCoroutineContext().ensureActive()

        if (historicalEvents.isEmpty() && futureEvents.isEmpty()) {
            return PKState(
                currentTimeH = input.currentTimeH,
                concentrationCalculatedAt = input.now
            )
        }

        val startTimeH = input.currentTimeH - 24.0 * 15
        val endTimeH = input.currentTimeH + 24.0 * 15
        val stepsNeeded = ceil(
            (endTimeH - startTimeH) * SIMULATION_POINTS_PER_HOUR
        ).toInt() + 1
        val numberOfSteps = maxOf(stepsNeeded, 1000)
        val baselineResult = if (historicalEvents.isNotEmpty()) {
            SimulationEngine(
                events = historicalEvents,
                bodyWeightKG = input.bodyWeightKG,
                startTimeH = startTimeH,
                endTimeH = endTimeH,
                numberOfSteps = numberOfSteps
            ).run()
        } else {
            null
        }
        currentCoroutineContext().ensureActive()
        val allEvents = historicalEvents + futureEvents
        val fullResult = if (allEvents.isNotEmpty()) {
            SimulationEngine(
                events = allEvents,
                bodyWeightKG = input.bodyWeightKG,
                startTimeH = startTimeH,
                endTimeH = endTimeH,
                numberOfSteps = numberOfSteps
            ).run()
        } else {
            null
        }
        currentCoroutineContext().ensureActive()
        val currentConcentration = fullResult?.concentration(input.currentTimeH)
            ?: baselineResult?.concentration(input.currentTimeH)

        return PKState(
            simulationResult = fullResult,
            baselineSimulationResult = baselineResult,
            currentConcentration = currentConcentration,
            currentTimeH = input.currentTimeH,
            concentrationCalculatedAt = input.now
        )
    }
}
