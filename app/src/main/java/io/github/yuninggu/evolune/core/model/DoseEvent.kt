package io.github.yuninggu.evolune.core.model

import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class DoseEvent(
    val id: UUID,
    val route: Route,
    val occurredAt: Instant,
    val zoneId: ZoneId? = null,
    val localDate: LocalDate? = null,
    val doseMG: Double,
    val ester: Ester,
    val extras: Map<ExtraKey, Double> = emptyMap(),
    val slotId: UUID? = null,
    val source: DoseEventSource,
    val status: DoseEventStatus = DoseEventStatus.RECORDED,
    val revision: Long = 1
) {
    init {
        require(revision >= 1) { "revision must be at least 1" }
    }
}

enum class ExtraKey {
    CONCENTRATION_MG_ML,
    AREA_CM2,
    RELEASE_RATE_UG_PER_DAY,
    SUBLINGUAL_THETA,
    SUBLINGUAL_TIER,
    ANTI_ANDROGEN_TYPE
}
