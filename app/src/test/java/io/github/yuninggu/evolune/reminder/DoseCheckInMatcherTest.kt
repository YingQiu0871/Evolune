package io.github.yuninggu.evolune.reminder

import io.github.yuninggu.evolune.application.syntheticPlan
import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.pk.Route
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class DoseCheckInMatcherTest {

    private val plan = syntheticPlan()
    private val scheduledAtMillis = 1_800_000_000_000L

    private fun event(
        timeOffsetMillis: Long,
        route: Route = plan.route,
        doseMG: Double = plan.doseMG
    ) = DoseEvent(
        id = UUID(0L, timeOffsetMillis + 3_600_001L),
        route = route,
        occurredAt = Instant.ofEpochMilli(scheduledAtMillis + timeOffsetMillis),
        doseMG = doseMG,
        ester = plan.ester,
        source = DoseEventSource.MANUAL
    )

    @Test
    fun `check-ins at both one-hour boundaries suppress the reminder`() {
        assertTrue(
            hasPlanDoseCheckIn(
                plan,
                listOf(event(-3_600_000L), event(3_600_000L)),
                scheduledAtMillis
            )
        )
    }

    @Test
    fun `check-in outside the window does not suppress the reminder`() {
        assertFalse(
            hasPlanDoseCheckIn(
                plan,
                listOf(event(3_600_001L)),
                scheduledAtMillis
            )
        )
    }

    @Test
    fun `different route or dose does not count as this plan`() {
        assertFalse(
            hasPlanDoseCheckIn(
                plan,
                listOf(
                    event(0L, route = Route.SUBLINGUAL),
                    event(0L, doseMG = 1.0)
                ),
                scheduledAtMillis
            )
        )
    }

    @Test
    fun `reminder is evaluated after the positive one-hour boundary`() {
        assertTrue(
            reminderEvaluationTimeMillis(scheduledAtMillis) ==
                scheduledAtMillis + 3_600_000L
        )
    }
}
