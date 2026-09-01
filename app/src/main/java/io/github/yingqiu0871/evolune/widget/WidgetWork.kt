package io.github.yingqiu0871.evolune.widget

import io.github.yingqiu0871.evolune.application.LocalActionRecorder
import io.github.yingqiu0871.evolune.application.OccurrenceConfirmationCoordinator
import io.github.yingqiu0871.evolune.application.RecordAcceptance
import io.github.yingqiu0871.evolune.application.RecordDoseEventActionResult
import io.github.yingqiu0871.evolune.application.findPresentedEventForOccurrence
import io.github.yingqiu0871.evolune.application.widgetOccurrenceActionEventId
import io.github.yingqiu0871.evolune.core.adapter.DomainDoseEventToPkAdapter
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.core.time.LegacyTimeAdapter
import io.github.yingqiu0871.evolune.core.time.LegacyTimeResult
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.repository.RepositoryStorageException
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceIdentity
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal data class WidgetSnapshot(
    val presentation: WidgetPresentationState,
    val concentration: Double?,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM
)

internal class WidgetSnapshotLoader(
    private val medicationPlans: MedicationPlanRepository,
    private val doseEvents: DoseEventRepository,
    private val bodyWeight: suspend () -> Double,
    private val timeFormat: suspend () -> TimeFormat = { TimeFormat.SYSTEM },
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
    private val presentationMapper: WidgetPresentationMapper = WidgetPresentationMapper()
) {
    suspend fun load(): WidgetSnapshot {
        val plans = medicationPlans.observeEnabled().first()
        val now = Instant.ofEpochMilli(clock.millis())
        val eventWindow = WidgetPresentationPolicy.eventWindow(now)
        val presentation = presentationMapper.map(
            enabledPlans = plans,
            doseEvents = doseEvents.findOccurredBetween(
                startInclusive = eventWindow.first,
                endExclusive = eventWindow.second
            ),
            now = now,
            zoneId = zoneId()
        )
        val events = doseEvents.getEventsForPk(now)
            .filter { event ->
                event.route != Route.ANTIANDROGEN && !event.occurredAt.isAfter(now)
            }
        val concentration = if (events.isEmpty()) {
            null
        } else {
            val nowH = now.toWidgetPkTimeH()
            SimulationEngine(
                events = DomainDoseEventToPkAdapter.adapt(events),
                bodyWeightKG = bodyWeight(),
                startTimeH = nowH - 0.01,
                endTimeH = nowH,
                numberOfSteps = 2
            ).run().concPGmL.lastOrNull()
        }
        return WidgetSnapshot(
            presentation = presentation,
            concentration = concentration,
            timeFormat = timeFormat()
        )
    }
}

internal fun interface WidgetSnapshotRenderer {
    fun render(appWidgetId: Int, state: WidgetRenderState)
}

internal fun interface WidgetUpdateWork {
    suspend fun handle(appWidgetIds: IntArray)
}

internal enum class WidgetUpdateReason {
    APP_WIDGET_UPDATE,
    WIDGET_RESIZED,
    PLAN_CHANGED,
    DOSE_EVENT_CHANGED,
    ACCEPTED_WIDGET_DOSE_EVENT,
    ACCEPTED_NOTIFICATION_DOSE_EVENT,
    ACCEPTED_WEAR_DOSE_EVENT,
    DATE_OR_TIMEZONE_CHANGED,
    APPEARANCE_CHANGED,
    MANUAL_APP_REFRESH
}

internal fun interface WidgetUpdateCoordinator {
    suspend fun request(reason: WidgetUpdateReason)
}

internal class ContractWidgetUpdateCoordinator(
    private val update: suspend (WidgetUpdateReason) -> Unit
) : WidgetUpdateCoordinator {
    override suspend fun request(reason: WidgetUpdateReason) = update(reason)
}

internal class ContractWidgetUpdateWork(
    private val snapshotLoader: WidgetSnapshotLoader,
    private val renderer: WidgetSnapshotRenderer
) : WidgetUpdateWork {
    override suspend fun handle(appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val state = try {
            WidgetRenderState.Loaded(snapshotLoader.load())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            WidgetRenderState.ReadFailure()
        }
        appWidgetIds.forEach { appWidgetId ->
            renderer.render(appWidgetId, state)
        }
    }
}

internal data class WidgetQuickActionCommand(
    val planId: String?,
    val slotId: String?,
    val scheduledLocalDate: String?,
    val occurrenceId: String?
)

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
    private val medicationPlans: MedicationPlanRepository,
    private val doseEvents: DoseEventRepository,
    private val sideEffects: WidgetQuickActionSideEffects,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: () -> ZoneId = ZoneId::systemDefault
) : WidgetQuickActionWork {
    private val recordAction = LocalActionRecorder(medicationPlans, doseEvents)

    override suspend fun handle(command: WidgetQuickActionCommand): WidgetQuickActionOutcome {
        val parsed = command.parsed()
            ?: return WidgetQuickActionOutcome.Invalid
        return try {
            OccurrenceConfirmationCoordinator.withLock {
                handleParsed(parsed)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: RepositoryStorageException) {
            WidgetQuickActionOutcome.StorageFailure
        } catch (_: Throwable) {
            WidgetQuickActionOutcome.UnexpectedFailure
        }
    }

    private suspend fun handleParsed(
        parsed: ParsedWidgetQuickAction
    ): WidgetQuickActionOutcome {
        val recordedAtMillis = clock.millis()
        val actionZoneId = zoneId()
        val recordedAt = Instant.ofEpochMilli(recordedAtMillis)
        if (parsed.scheduledLocalDate != recordedAt.atZone(actionZoneId).toLocalDate()) {
            return WidgetQuickActionOutcome.Invalid
        }
        val derivedOccurrenceId = MedicationOccurrenceIdentity.derive(
            parsed.planId,
            parsed.slotId,
            parsed.scheduledLocalDate
        ).value
        if (derivedOccurrenceId != parsed.occurrenceId) {
            return WidgetQuickActionOutcome.Invalid
        }

        val currentPlan = medicationPlans.getById(parsed.planId)
            ?: return WidgetQuickActionOutcome.PlanNotFound
        if (!currentPlan.isEnabled) return WidgetQuickActionOutcome.PlanDisabled
        val targetOccurrence = parsed.regenerateExactOccurrence(currentPlan, actionZoneId)
            ?: return WidgetQuickActionOutcome.Invalid
        val presentationOccurrences = regeneratePresentationOccurrences(
            medicationPlans.observeEnabled().first(),
            parsed.scheduledLocalDate,
            actionZoneId
        )
        val existingAction = doseEvents.getById(
            widgetOccurrenceActionEventId(parsed.occurrenceId)
        )
        if (existingAction == null) {
            val existingPresentationEvent = findPresentedEventForOccurrence(
                targetOccurrence,
                presentationOccurrences,
                doseEvents.observeAll().first(),
                recordedAt
            )
            if (existingPresentationEvent != null) {
                return accepted(currentPlan.name, replayed = true)
            }
        }

        return when (
            val result = recordAction.recordWidget(
                planId = parsed.planId,
                occurrenceId = parsed.occurrenceId,
                validatePlan = { plan -> parsed.regenerateExactOccurrence(plan, actionZoneId) != null }
            ) { plan, eventId ->
                createWidgetDoseEvent(
                    plan = plan,
                    eventId = eventId,
                    recordedAtMillis = recordedAtMillis,
                    zoneId = actionZoneId,
                    slotId = parsed.slotId
                )
            }
        ) {
            is RecordDoseEventActionResult.Accepted -> {
                val verifiedPlan = result.plan ?: currentPlan
                val verifiedOccurrence = parsed.regenerateExactOccurrence(
                    verifiedPlan,
                    actionZoneId
                )
                val verifiedWinner = verifiedOccurrence?.let { occurrence ->
                    findPresentedEventForOccurrence(
                        occurrence,
                        regeneratePresentationOccurrences(
                            medicationPlans.observeEnabled().first(),
                            parsed.scheduledLocalDate,
                            actionZoneId
                        ),
                        doseEvents.observeAll().first(),
                        Instant.ofEpochMilli(clock.millis())
                    )
                }
                if (verifiedWinner == null) {
                    WidgetQuickActionOutcome.Conflict
                } else {
                    accepted(
                        planName = verifiedPlan.name,
                        replayed = result.acceptance != RecordAcceptance.Inserted ||
                            verifiedWinner.id != result.event.id
                    )
                }
            }
            RecordDoseEventActionResult.PlanNotFound -> WidgetQuickActionOutcome.PlanNotFound
            RecordDoseEventActionResult.PlanDisabled -> WidgetQuickActionOutcome.PlanDisabled
            RecordDoseEventActionResult.Conflict -> WidgetQuickActionOutcome.Conflict
            RecordDoseEventActionResult.Invalid -> WidgetQuickActionOutcome.Invalid
            RecordDoseEventActionResult.StorageFailure -> WidgetQuickActionOutcome.StorageFailure
            RecordDoseEventActionResult.UnexpectedFailure ->
                WidgetQuickActionOutcome.UnexpectedFailure
        }
    }

    private fun regeneratePresentationOccurrences(
        plans: List<MedicationPlan>,
        localDate: LocalDate,
        zoneId: ZoneId
    ): List<MedicationOccurrence> {
        val window = runCatching {
            OccurrenceGenerationWindow(
                startInclusive = localDate.minusDays(1L).atStartOfDay(zoneId).toInstant(),
                endExclusive = localDate.plusDays(2L).atStartOfDay(zoneId).toInstant()
            )
        }.getOrNull() ?: return emptyList()
        return MedicationOccurrenceGenerator.generate(
            schedules = plans.map(MedicationPlan::toMedicationSchedule),
            window = window,
            zoneId = zoneId
        )
    }

    private fun WidgetQuickActionCommand.parsed(): ParsedWidgetQuickAction? {
        val parsedPlanId = planId?.let(::parseUuid) ?: return null
        val parsedSlotId = slotId?.let(::parseUuid) ?: return null
        val parsedDate = scheduledLocalDate
            ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
            ?: return null
        val parsedOccurrenceId = occurrenceId?.let(::parseUuid) ?: return null
        return ParsedWidgetQuickAction(
            parsedPlanId,
            parsedSlotId,
            parsedDate,
            parsedOccurrenceId
        )
    }

    private fun parseUuid(value: String): UUID? =
        runCatching { UUID.fromString(value) }.getOrNull()

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

private data class ParsedWidgetQuickAction(
    val planId: UUID,
    val slotId: UUID,
    val scheduledLocalDate: LocalDate,
    val occurrenceId: UUID
) {
    fun regenerateExactOccurrence(
        plan: MedicationPlan,
        zoneId: ZoneId
    ): MedicationOccurrence? {
        val start = runCatching { scheduledLocalDate.atStartOfDay(zoneId).toInstant() }
            .getOrNull() ?: return null
        val end = runCatching { scheduledLocalDate.plusDays(1L).atStartOfDay(zoneId).toInstant() }
            .getOrNull() ?: return null
        return MedicationOccurrenceGenerator.generate(
            schedules = listOf(plan.toMedicationSchedule()),
            window = OccurrenceGenerationWindow(start, end),
            zoneId = zoneId
        ).singleOrNull { occurrence ->
            occurrence.id.value == occurrenceId &&
                occurrence.planId == planId &&
                occurrence.slotId == slotId &&
                occurrence.scheduledLocalDateTime.toLocalDate() == scheduledLocalDate
        }
    }
}

internal fun createWidgetDoseEvent(
    plan: MedicationPlan,
    eventId: UUID,
    recordedAtMillis: Long,
    zoneId: ZoneId,
    slotId: UUID
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
        slotId = slotId,
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
