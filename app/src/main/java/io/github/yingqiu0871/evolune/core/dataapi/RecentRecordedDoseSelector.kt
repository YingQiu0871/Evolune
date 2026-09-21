package io.github.yingqiu0871.evolune.core.dataapi

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus

/**
 * The single authoritative qualification and ordering rule for the most recent
 * recorded dose.
 *
 * Shared neutral policy: the Room repository uses it to enforce the
 * [DoseEventRepository.deleteLatestRecordedIfRevisionMatches] contract, and the
 * Wear snapshot builder uses it to derive the recent dose. It depends on
 * `core.model` only.
 */
internal object RecentRecordedDoseSelector {
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
