package io.github.yuninggu.evolune.core.adapter

import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.util.UUID

class DomainDoseEventToPkAdapterTest {
    @Test
    fun `maps every representable field and omits Domain-only metadata`() {
        val domainEvent = event(
            id = UUID(0L, 1L),
            route = Route.SUBLINGUAL,
            ester = Ester.EV,
            occurredAt = Instant.ofEpochMilli(-1_700_000_000_125L),
            doseMG = 0.0,
            extras = allExtras()
        ).copy(
            slotId = UUID(0L, 99L),
            source = DoseEventSource.WEAR,
            status = DoseEventStatus.RECORDED,
            revision = 8L
        )

        val projected = DomainDoseEventToPkAdapter.adapt(domainEvent)

        assertEquals(domainEvent.id, projected.id)
        assertEquals(domainEvent.route, projected.route)
        assertEquals(-1_700_000_000_125L / 3_600_000.0, projected.timeH, 0.0)
        assertEquals(domainEvent.doseMG, projected.doseMG, 0.0)
        assertEquals(domainEvent.ester, projected.ester)
        assertEquals(
            allExtras().mapKeys { (key, _) -> key.toPkExtraKey() },
            projected.extras
        )
        assertEquals(domainEvent, domainEvent.copy())
    }

    @Test
    fun `preserves empty input input order and duplicate timestamps`() {
        val occurredAt = Instant.parse("2026-01-01T00:00:00Z")
        val events = listOf(
            event(UUID(0L, 2L), Route.ORAL, Ester.E2, occurredAt),
            event(UUID(0L, 1L), Route.PATCH_APPLY, Ester.EC, occurredAt),
            event(UUID(0L, 3L), Route.PATCH_REMOVE, Ester.EN, occurredAt)
        )

        assertEquals(emptyList<Any>(), DomainDoseEventToPkAdapter.adapt(emptyList()).map { it.id })
        assertEquals(
            events.map { it.id },
            DomainDoseEventToPkAdapter.adapt(events).map { it.id }
        )
        assertEquals(
            events.map { it.route },
            DomainDoseEventToPkAdapter.adapt(events).map { it.route }
        )
    }

    @Test
    fun `covers every supported Route and Ester without fallback`() {
        val events = Route.values().flatMapIndexed { routeIndex, route ->
            Ester.values().mapIndexed { esterIndex, ester ->
                event(
                    id = UUID(0L, (routeIndex * Ester.values().size + esterIndex + 1).toLong()),
                    route = route,
                    ester = ester,
                    occurredAt = Instant.ofEpochMilli(
                        (routeIndex * Ester.values().size + esterIndex).toLong()
                    )
                )
            }
        }

        val projected = DomainDoseEventToPkAdapter.adapt(events)

        assertEquals(events.size, projected.size)
        assertEquals(events.map { it.route }, projected.map { it.route })
        assertEquals(events.map { it.ester }, projected.map { it.ester })
        assertEquals(events.map { it.id }, projected.map { it.id })
    }

    @Test
    fun `keeps edge dose values and exact millisecond conversion`() {
        val event = event(
            id = UUID(0L, 401L),
            route = Route.GEL,
            ester = Ester.EB,
            occurredAt = Instant.ofEpochSecond(-1L, 999_999_000L),
            doseMG = -0.25
        )

        val projected = DomainDoseEventToPkAdapter.adapt(event)

        assertEquals(event.occurredAt.toEpochMilli() / 3_600_000.0, projected.timeH, 0.0)
        assertEquals(-0.25, projected.doseMG, 0.0)
    }

    @Test
    fun `rejects an Instant outside the legacy PK time range`() {
        assertThrows(IllegalArgumentException::class.java) {
            DomainDoseEventToPkAdapter.adapt(
                event(
                    id = UUID(0L, 402L),
                    route = Route.ORAL,
                    ester = Ester.E2,
                    occurredAt = Instant.MAX
                )
            )
        }
    }

    private fun event(
        id: UUID,
        route: Route,
        ester: Ester,
        occurredAt: Instant,
        doseMG: Double = 2.0,
        extras: Map<ExtraKey, Double> = emptyMap()
    ): DoseEvent = DoseEvent(
        id = id,
        route = route,
        occurredAt = occurredAt,
        zoneId = null,
        localDate = null,
        doseMG = doseMG,
        ester = ester,
        extras = extras,
        slotId = UUID(0L, 99L),
        source = DoseEventSource.MANUAL,
        status = DoseEventStatus.RECORDED,
        revision = 4L
    )

    private fun allExtras(): Map<ExtraKey, Double> = linkedMapOf(
        ExtraKey.CONCENTRATION_MG_ML to 1.0,
        ExtraKey.AREA_CM2 to 2.0,
        ExtraKey.RELEASE_RATE_UG_PER_DAY to 3.0,
        ExtraKey.SUBLINGUAL_THETA to 4.0,
        ExtraKey.SUBLINGUAL_TIER to 5.0,
        ExtraKey.ANTI_ANDROGEN_TYPE to 6.0
    )
}
