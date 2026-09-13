package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.experience.HistoricalRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Read seam between the History presentation layer and the authoritative history read
 * path (A-03 UI).
 *
 * The ViewModel depends on this single method — never on a DAO, a repository, the
 * occurrence matcher or the occurrence generator — so the UI cannot reach past
 * [HistoryReadService] into the domain internals. The only production implementation is
 * `HistoryReadService::readRange`, wired at the composition root.
 *
 * The returned [HistoricalRange] only ever contains domain `HistoricalEntry` values, i.e.
 * matched facts and arrived-but-unrecorded occurrences. Occurrences that have not arrived
 * yet are deliberately not part of this type, so no UI-level future filtering exists or is
 * possible (see `V17_A_03_HISTORY_UI.md` §8).
 */
fun interface HistoryRangeSource {
    suspend fun read(
        startDate: LocalDate,
        endDate: LocalDate,
        displayZone: ZoneId,
        now: Instant
    ): HistoricalRange
}
