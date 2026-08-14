package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class DoseEventEditMode {
    CREATE,
    UPDATE
}

data class DoseEventEditSession(
    val mode: DoseEventEditMode,
    val original: DoseEvent,
    val editZoneId: ZoneId
) {
    val expectedRevision: Long? = when (mode) {
        DoseEventEditMode.CREATE -> null
        DoseEventEditMode.UPDATE -> original.revision
    }
}

class DoseEventEditSessionFactory(
    private val idSupplier: () -> UUID = UUID::randomUUID,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneIdSupplier: () -> ZoneId = ZoneId::systemDefault
) {
    fun createNew(): DoseEventEditSession {
        val occurredAt = Instant.ofEpochMilli(clock.millis())
        val zoneId = zoneIdSupplier()
        return DoseEventEditSession(
            mode = DoseEventEditMode.CREATE,
            original = DoseEvent(
                id = idSupplier(),
                route = Route.INJECTION,
                occurredAt = occurredAt,
                zoneId = zoneId,
                localDate = occurredAt.atZone(zoneId).toLocalDate(),
                doseMG = 0.0,
                ester = Ester.EV,
                extras = emptyMap(),
                slotId = null,
                source = DoseEventSource.MANUAL,
                status = DoseEventStatus.RECORDED,
                revision = INITIAL_REVISION
            ),
            editZoneId = zoneId
        )
    }

    fun edit(event: DoseEvent): DoseEventEditSession = DoseEventEditSession(
        mode = DoseEventEditMode.UPDATE,
        original = event,
        editZoneId = zoneIdSupplier()
    )

    fun createQuickEvent(plan: MedicationPlan): DoseEvent {
        val zoneId = zoneIdSupplier()
        val occurredAt = Instant.ofEpochMilli(
            Math.floorDiv(clock.millis(), MILLIS_PER_MINUTE) * MILLIS_PER_MINUTE
        )
        return DoseEvent(
            id = idSupplier(),
            route = plan.route,
            occurredAt = occurredAt,
            zoneId = zoneId,
            localDate = occurredAt.atZone(zoneId).toLocalDate(),
            doseMG = plan.doseMG,
            ester = plan.ester,
            extras = plan.extras,
            slotId = null,
            source = DoseEventSource.MANUAL,
            status = DoseEventStatus.RECORDED,
            revision = INITIAL_REVISION
        )
    }

    private companion object {
        const val INITIAL_REVISION = 1L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

data class DoseEventEditorInput(
    val occurredAt: Instant,
    val occurredAtEdited: Boolean,
    val route: Route,
    val doseMG: Double,
    val ester: Ester,
    val extras: Map<ExtraKey, Double>
)

sealed interface DoseEventEditorResult {
    data class Valid(val command: DoseEventEditCommand) : DoseEventEditorResult
    data class Invalid(val issues: List<DoseEventInputIssue>) : DoseEventEditorResult
}

sealed interface DoseEventInputIssue {
    data object InvalidDose : DoseEventInputIssue
    data object NonPositiveDose : DoseEventInputIssue
    data object InvalidOccurredAtPrecision : DoseEventInputIssue
    data class InvalidExtra(val key: ExtraKey) : DoseEventInputIssue
}

sealed interface DoseEventEditCommand {
    val event: DoseEvent

    data class Create(override val event: DoseEvent) : DoseEventEditCommand
    data class Update(
        override val event: DoseEvent,
        val expectedRevision: Long
    ) : DoseEventEditCommand
}

fun DoseEventEditorInput.toDoseEventCommand(
    session: DoseEventEditSession
): DoseEventEditorResult {
    val issues = mutableListOf<DoseEventInputIssue>()
    if (!doseMG.isFinite()) {
        issues += DoseEventInputIssue.InvalidDose
    } else if (requiresPositiveDose(route, extras) && doseMG <= 0.0) {
        issues += DoseEventInputIssue.NonPositiveDose
    }
    if (!occurredAt.hasMillisecondPrecision()) {
        issues += DoseEventInputIssue.InvalidOccurredAtPrecision
    }
    extras.forEach { (key, value) ->
        if (!value.isFinite()) {
            issues += DoseEventInputIssue.InvalidExtra(key)
        }
    }
    if (issues.isNotEmpty()) {
        return DoseEventEditorResult.Invalid(issues)
    }

    val resolvedOccurredAt = if (occurredAtEdited) occurredAt else session.original.occurredAt
    val resolvedZoneId = if (occurredAtEdited) session.editZoneId else session.original.zoneId
    val resolvedLocalDate = if (occurredAtEdited) {
        resolvedOccurredAt.atZone(session.editZoneId).toLocalDate()
    } else {
        session.original.localDate
    }
    val mergedExtras = session.original.extras.toMutableMap().apply {
        putAll(extras)
    }
    val event = session.original.copy(
        route = route,
        occurredAt = resolvedOccurredAt,
        zoneId = resolvedZoneId,
        localDate = resolvedLocalDate,
        doseMG = doseMG,
        ester = ester,
        extras = mergedExtras
    )

    return DoseEventEditorResult.Valid(
        when (session.mode) {
            DoseEventEditMode.CREATE -> DoseEventEditCommand.Create(event)
            DoseEventEditMode.UPDATE -> DoseEventEditCommand.Update(
                event = event,
                expectedRevision = requireNotNull(session.expectedRevision)
            )
        }
    )
}

private fun requiresPositiveDose(
    route: Route,
    extras: Map<ExtraKey, Double>
): Boolean = route != Route.PATCH_REMOVE &&
    !(route == Route.PATCH_APPLY && ExtraKey.RELEASE_RATE_UG_PER_DAY in extras)

private fun Instant.hasMillisecondPrecision(): Boolean = try {
    Instant.ofEpochMilli(toEpochMilli()) == this
} catch (_: ArithmeticException) {
    false
}
