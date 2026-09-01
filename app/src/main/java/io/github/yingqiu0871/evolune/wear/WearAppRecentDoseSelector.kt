package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus

/** The single authoritative qualification and ordering rule for Wear recent dose. */
internal object WearAppRecentDoseSelector {
    private val recentOrder = compareBy<DoseEvent>(
        { it.occurredAt },
        { it.id.toString() }
    )

    fun eligible(events: List<DoseEvent>): List<DoseEvent> = events.filter(::isEligible)

    fun select(events: List<DoseEvent>): DoseEvent? =
        events.asSequence()
            .filter(::isEligible)
            .maxWithOrNull(recentOrder)

    fun isEligible(event: DoseEvent): Boolean =
        event.status == DoseEventStatus.RECORDED &&
            event.occurredAt.toEpochMilliOrNull()?.let { it > 0L } == true &&
            event.doseMG.isFinite() &&
            event.doseMG >= 0.0

    private fun java.time.Instant.toEpochMilliOrNull(): Long? =
        runCatching { toEpochMilli() }.getOrNull()
}
