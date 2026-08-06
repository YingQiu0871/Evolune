package io.github.yuninggu.evolune.widget

import io.github.yuninggu.evolune.application.RecordDoseEventAction
import io.github.yuninggu.evolune.application.RecordDoseEventActionResult
import io.github.yuninggu.evolune.core.dataapi.DoseEventRepository
import io.github.yuninggu.evolune.core.dataapi.MedicationPlanRepository
import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.core.time.LegacyTimeAdapter
import io.github.yuninggu.evolune.core.time.LegacyTimeResult
import io.github.yuninggu.evolune.pk.DoseEvent as PkDoseEvent
import io.github.yuninggu.evolune.pk.Route
import io.github.yuninggu.evolune.pk.SimulationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal data class WidgetSnapshot(
    val plans: List<MedicationPlan>,
    val concentration: Double?
)

internal class WidgetSnapshotLoader(
    private val medicationPlans: MedicationPlanRepository,
    private val doseEvents: DoseEventRepository,
    private val bodyWeight: suspend () -> Double,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun load(): WidgetSnapshot {
        val plans = medicationPlans.observeEnabled().first().take(2)
        val now = Instant.ofEpochMilli(clock.millis())
        val events = doseEvents.getEventsForPk(now)
            .filter { event ->
                event.route != Route.ANTIANDROGEN && !event.occurredAt.isAfter(now)
            }
        val concentration = if (events.isEmpty()) {
            null
        } else {
            val nowH = now.toWidgetPkTimeH()
            SimulationEngine(
                events = events.map(DoseEvent::toWidgetPkEvent),
                bodyWeightKG = bodyWeight(),
                startTimeH = nowH - 0.01,
                endTimeH = nowH,
                numberOfSteps = 2
            ).run().concPGmL.lastOrNull()
        }
        return WidgetSnapshot(plans = plans, concentration = concentration)
    }
}

internal fun interface WidgetSnapshotRenderer {
    fun render(appWidgetId: Int, snapshot: WidgetSnapshot)
}

internal fun interface WidgetUpdateWork {
    suspend fun handle(appWidgetIds: IntArray)
}

internal class ContractWidgetUpdateWork(
    private val snapshotLoader: WidgetSnapshotLoader,
    private val renderer: WidgetSnapshotRenderer
) : WidgetUpdateWork {
    override suspend fun handle(appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            renderer.render(appWidgetId, snapshotLoader.load())
        }
    }
}

internal data class WidgetQuickActionCommand(val planId: String?)

internal sealed interface WidgetQuickActionOutcome {
    data class Accepted(val replayed: Boolean) : WidgetQuickActionOutcome
    data object AcceptedWithSideEffectFailure : WidgetQuickActionOutcome
    data object Invalid : WidgetQuickActionOutcome
    data object PlanNotFound : WidgetQuickActionOutcome
    data object PlanDisabled : WidgetQuickActionOutcome
    data object Conflict : WidgetQuickActionOutcome
    data object StorageFailure : WidgetQuickActionOutcome
    data object UnexpectedFailure : WidgetQuickActionOutcome
}

internal interface WidgetQuickActionSideEffects {
    suspend fun refreshWidgets()
    suspend fun showRecorded(planName: String)
}

internal fun interface WidgetQuickActionWork {
    suspend fun handle(command: WidgetQuickActionCommand): WidgetQuickActionOutcome
}

internal class ContractWidgetQuickActionWork(
    medicationPlans: MedicationPlanRepository,
    doseEvents: DoseEventRepository,
    private val sideEffects: WidgetQuickActionSideEffects,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: () -> ZoneId = ZoneId::systemDefault
) : WidgetQuickActionWork {
    private val recordAction = RecordDoseEventAction(medicationPlans, doseEvents)

    override suspend fun handle(command: WidgetQuickActionCommand): WidgetQuickActionOutcome {
        val planId = command.planId
            ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
            ?: return WidgetQuickActionOutcome.Invalid
        val recordedAtMillis = clock.millis()
        val eventId = widgetDoseEventId(planId, recordedAtMillis)
        return when (
            val result = recordAction.execute(
                planId = planId,
                eventId = eventId,
                source = DoseEventSource.WIDGET,
                requireEnabledPlan = true
            ) { plan ->
                createWidgetDoseEvent(plan, eventId, recordedAtMillis, zoneId())
            }
        ) {
            is RecordDoseEventActionResult.Accepted -> accepted(
                planName = result.plan.name,
                replayed = result.replayed
            )
            RecordDoseEventActionResult.PlanNotFound -> WidgetQuickActionOutcome.PlanNotFound
            RecordDoseEventActionResult.PlanDisabled -> WidgetQuickActionOutcome.PlanDisabled
            RecordDoseEventActionResult.Conflict -> WidgetQuickActionOutcome.Conflict
            RecordDoseEventActionResult.Invalid -> WidgetQuickActionOutcome.Invalid
            RecordDoseEventActionResult.StorageFailure -> WidgetQuickActionOutcome.StorageFailure
            RecordDoseEventActionResult.UnexpectedFailure ->
                WidgetQuickActionOutcome.UnexpectedFailure
        }
    }

    private suspend fun accepted(
        planName: String,
        replayed: Boolean
    ): WidgetQuickActionOutcome = try {
        sideEffects.refreshWidgets()
        sideEffects.showRecorded(planName)
        WidgetQuickActionOutcome.Accepted(replayed)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        WidgetQuickActionOutcome.AcceptedWithSideEffectFailure
    }
}

internal fun widgetDoseEventId(planId: UUID, recordedAtMillis: Long): UUID =
    UUID.nameUUIDFromBytes(
        "widget:$planId:${recordedAtMillis / 60_000L}"
            .toByteArray(StandardCharsets.UTF_8)
    )

internal fun createWidgetDoseEvent(
    plan: MedicationPlan,
    eventId: UUID,
    recordedAtMillis: Long,
    zoneId: ZoneId
): DoseEvent {
    require(recordedAtMillis > 0L)
    val occurredAt = Instant.ofEpochMilli(recordedAtMillis)
    return DoseEvent(
        id = eventId,
        route = plan.route,
        occurredAt = occurredAt,
        zoneId = zoneId,
        localDate = occurredAt.atZone(zoneId).toLocalDate(),
        doseMG = plan.doseMG,
        ester = plan.ester,
        extras = plan.extras,
        slotId = null,
        source = DoseEventSource.WIDGET,
        status = DoseEventStatus.RECORDED,
        revision = 1L
    )
}

private fun Instant.toWidgetPkTimeH(): Double = when (
    val result = LegacyTimeAdapter.instantToTimeH(this)
) {
    is LegacyTimeResult.Success -> result.value
    is LegacyTimeResult.Failure -> throw IllegalArgumentException(
        "Widget PK time is outside the compatibility range"
    )
}

private fun DoseEvent.toWidgetPkEvent(): PkDoseEvent = PkDoseEvent(
    id = id,
    route = route,
    timeH = occurredAt.toWidgetPkTimeH(),
    doseMG = doseMG,
    ester = ester,
    extras = extras.mapKeys { (key, _) -> key.toWidgetPkExtraKey() }
)

private fun ExtraKey.toWidgetPkExtraKey(): PkDoseEvent.ExtraKey = when (this) {
    ExtraKey.CONCENTRATION_MG_ML -> PkDoseEvent.ExtraKey.CONCENTRATION_MG_ML
    ExtraKey.AREA_CM2 -> PkDoseEvent.ExtraKey.AREA_CM2
    ExtraKey.RELEASE_RATE_UG_PER_DAY -> PkDoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY
    ExtraKey.SUBLINGUAL_THETA -> PkDoseEvent.ExtraKey.SUBLINGUAL_THETA
    ExtraKey.SUBLINGUAL_TIER -> PkDoseEvent.ExtraKey.SUBLINGUAL_TIER
    ExtraKey.ANTI_ANDROGEN_TYPE -> PkDoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE
}
