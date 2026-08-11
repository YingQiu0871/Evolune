package io.github.yuninggu.evolune.core.adapter

import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.time.LegacyTimeAdapter
import io.github.yuninggu.evolune.core.time.LegacyTimeResult
import io.github.yuninggu.evolune.pk.DoseEvent as PkDoseEvent
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import io.github.yuninggu.evolune.pk.SimulationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class DomainDoseEventToPkParityTest {
    @Test
    fun `formal adapter preserves old structural and numerical PK parity`() {
        var maxObservedDelta = 0.0

        parityCorpora().forEach { (name, domainEvents) ->
            val oldProjection = domainEvents.map(::legacyProjection)
            val newProjection = DomainDoseEventToPkAdapter.adapt(domainEvents)

            assertEquals(name, oldProjection, newProjection)

            val startTimeH = oldProjection.minOf { it.timeH } - 24.0
            val endTimeH = oldProjection.maxOf { it.timeH } + 72.0
            val oldResult = SimulationEngine(
                events = oldProjection,
                bodyWeightKG = 60.0,
                startTimeH = startTimeH,
                endTimeH = endTimeH,
                numberOfSteps = 257
            ).run()
            val newResult = SimulationEngine(
                events = newProjection,
                bodyWeightKG = 60.0,
                startTimeH = startTimeH,
                endTimeH = endTimeH,
                numberOfSteps = 257
            ).run()

            assertEquals(name, oldResult.timeH, newResult.timeH)
            assertEquals(name, oldResult.concPGmL.size, newResult.concPGmL.size)
            oldResult.concPGmL.zip(newResult.concPGmL).forEachIndexed { index, (old, new) ->
                val delta = kotlin.math.abs(old - new)
                maxObservedDelta = maxOf(maxObservedDelta, delta)
                assertTrue(
                    "$name concentration sample $index delta=$delta",
                    delta <= 1e-6
                )
            }
            assertEquals(name, oldResult.auc, newResult.auc, 1e-6)
        }

        assertEquals(0.0, maxObservedDelta, 0.0)
    }

    private fun parityCorpora(): Map<String, List<DoseEvent>> {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        return linkedMapOf(
            "representative routes" to listOf(
                domainEvent(1L, base, Route.ORAL, Ester.E2),
                domainEvent(
                    2L,
                    base.plusSeconds(3_600L),
                    Route.SUBLINGUAL,
                    Ester.EV,
                    extras = mapOf(ExtraKey.SUBLINGUAL_THETA to 0.25)
                ),
                domainEvent(3L, base.plusSeconds(7_200L), Route.INJECTION, Ester.EB),
                domainEvent(
                    4L,
                    base.plusSeconds(10_800L),
                    Route.GEL,
                    Ester.EC,
                    extras = mapOf(ExtraKey.AREA_CM2 to 42.0)
                ),
                domainEvent(
                    5L,
                    base.plusSeconds(14_400L),
                    Route.PATCH_APPLY,
                    Ester.EN,
                    extras = mapOf(ExtraKey.RELEASE_RATE_UG_PER_DAY to 120.0)
                ),
                domainEvent(6L, base.plusSeconds(18_000L), Route.PATCH_REMOVE, Ester.E2),
                domainEvent(
                    7L,
                    base.plusSeconds(21_600L),
                    Route.ANTIANDROGEN,
                    Ester.E2,
                    extras = mapOf(ExtraKey.ANTI_ANDROGEN_TYPE to 1.0)
                )
            ),
            "same timestamp and repeated events" to listOf(
                domainEvent(101L, base, Route.ORAL, Ester.E2),
                domainEvent(102L, base, Route.ORAL, Ester.E2, doseMG = 3.0),
                domainEvent(103L, base, Route.SUBLINGUAL, Ester.EV),
                domainEvent(104L, base.plusMillis(1L), Route.INJECTION, Ester.EV)
            ),
            "long history" to List(30) { index ->
                domainEvent(
                    idSuffix = 200L + index,
                    occurredAt = base.plusSeconds(index * 43_200L),
                    route = if (index % 2 == 0) Route.INJECTION else Route.ORAL,
                    ester = if (index % 3 == 0) Ester.EV else Ester.E2,
                    doseMG = 1.0 + index / 10.0
                )
            },
            "sparse history" to listOf(
                domainEvent(301L, base.minusSeconds(259_200L), Route.GEL, Ester.E2),
                domainEvent(302L, base.plusSeconds(2_073_600L), Route.INJECTION, Ester.EN)
            )
        )
    }

    private fun legacyProjection(event: DoseEvent): PkDoseEvent {
        val timeH = when (val result = LegacyTimeAdapter.instantToTimeH(event.occurredAt)) {
            is LegacyTimeResult.Success -> result.value
            is LegacyTimeResult.Failure -> throw IllegalArgumentException("synthetic oracle time")
        }
        return PkDoseEvent(
            id = event.id,
            route = event.route,
            timeH = timeH,
            doseMG = event.doseMG,
            ester = event.ester,
            extras = event.extras.mapKeys { (key, _) ->
                when (key) {
                    ExtraKey.CONCENTRATION_MG_ML -> PkDoseEvent.ExtraKey.CONCENTRATION_MG_ML
                    ExtraKey.AREA_CM2 -> PkDoseEvent.ExtraKey.AREA_CM2
                    ExtraKey.RELEASE_RATE_UG_PER_DAY ->
                        PkDoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY
                    ExtraKey.SUBLINGUAL_THETA -> PkDoseEvent.ExtraKey.SUBLINGUAL_THETA
                    ExtraKey.SUBLINGUAL_TIER -> PkDoseEvent.ExtraKey.SUBLINGUAL_TIER
                    ExtraKey.ANTI_ANDROGEN_TYPE -> PkDoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE
                }
            }
        )
    }

    private fun domainEvent(
        idSuffix: Long,
        occurredAt: Instant,
        route: Route,
        ester: Ester,
        doseMG: Double = 2.0,
        extras: Map<ExtraKey, Double> = emptyMap()
    ): DoseEvent = DoseEvent(
        id = UUID(0L, idSuffix),
        route = route,
        occurredAt = occurredAt,
        zoneId = ZoneId.of("Asia/Shanghai"),
        localDate = occurredAt.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate(),
        doseMG = doseMG,
        ester = ester,
        extras = extras,
        slotId = UUID(0L, idSuffix + 10_000L),
        source = DoseEventSource.MANUAL
    )
}
