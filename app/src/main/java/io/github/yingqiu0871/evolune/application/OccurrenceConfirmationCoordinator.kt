package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.presentation.toRecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePresentation
import kotlinx.coroutines.sync.Mutex
import java.time.Instant

/**
 * Serializes all in-process occurrence confirmations, regardless of entry point.
 * Callers keep repository reads, presentation matching, insert, and verification
 * inside this boundary.
 */
internal object OccurrenceConfirmationCoordinator {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T {
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}

internal fun findPresentedEventForOccurrence(
    target: MedicationOccurrence,
    occurrences: List<MedicationOccurrence>,
    events: List<DoseEvent>,
    now: Instant
): DoseEvent? {
    val presentation = MedicationOccurrencePresentation.derive(
        occurrences = occurrences,
        recordedEvents = events.mapNotNull(DoseEvent::toRecordedMedicationEvent),
        now = now
    ).singleOrNull { it.occurrence.id == target.id }
    val eventId = presentation?.recordedEventId ?: return null
    return events.firstOrNull { it.id == eventId }
}
