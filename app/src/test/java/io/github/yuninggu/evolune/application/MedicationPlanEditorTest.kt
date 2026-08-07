package io.github.yuninggu.evolune.application

import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduleType
import io.github.yuninggu.evolune.core.model.ScheduledDoseSlot
import io.github.yuninggu.evolune.pk.AntiAndrogen
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import io.github.yuninggu.evolune.pk.SublingualTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class MedicationPlanEditorTest {
    @Test
    fun `new session captures one id and one instant`() {
        val clock = CountingClock(listOf(FIRST_INSTANT, SECOND_INSTANT))
        var nextId = 1L
        val factory = MedicationPlanEditSessionFactory(
            idSupplier = { UUID(0L, nextId++) },
            clock = clock
        )

        val first = factory.createNew()

        assertEquals(UUID(0L, 1L), first.id)
        assertEquals(FIRST_INSTANT, first.createdAt)
        assertEquals(1, clock.instantCalls)
        assertSame(first.id, first.id)
        assertSame(first.createdAt, first.createdAt)

        val second = factory.createNew()
        assertEquals(UUID(0L, 2L), second.id)
        assertEquals(SECOND_INSTANT, second.createdAt)
        assertEquals(2, clock.instantCalls)
    }

    @Test
    fun `new session normalizes device clock instant to persistence precision`() {
        val deviceInstant = Instant.parse("2026-08-07T01:02:03.123456789Z")
        val factory = MedicationPlanEditSessionFactory(
            idSupplier = { UUID(0L, 3L) },
            clock = Clock.fixed(deviceInstant, ZoneId.of("UTC"))
        )

        val session = factory.createNew()

        assertEquals(deviceInstant.truncatedTo(ChronoUnit.MILLIS), session.createdAt)
    }

    @Test
    fun `edit session preserves id and createdAt without consulting suppliers`() {
        var idCalls = 0
        val clock = CountingClock(listOf(SECOND_INSTANT))
        val plan = plan()
        val factory = MedicationPlanEditSessionFactory(
            idSupplier = {
                idCalls += 1
                UUID(0L, 999L)
            },
            clock = clock
        )

        val session = factory.edit(plan)

        assertEquals(plan.id, session.id)
        assertEquals(plan.createdAt, session.createdAt)
        assertSame(plan, session.existingPlan)
        assertEquals(0, idCalls)
        assertEquals(0, clock.instantCalls)
    }

    @Test
    fun `valid input parses numbers and preserves session identity`() {
        val session = MedicationPlanEditSession(UUID(0L, 11L), FIRST_INSTANT, null)

        val result = input(
            doseMGText = "2.75",
            intervalDaysText = "12",
            times = listOf(LocalTime.of(20, 0), LocalTime.of(8, 30))
        ).toMedicationPlanDraft(session)

        assertTrue(result is MedicationPlanInputResult.Success)
        val draft = (result as MedicationPlanInputResult.Success).draft
        assertEquals(session.id, draft.id)
        assertEquals(session.createdAt, draft.createdAt)
        assertEquals(2.75, draft.doseMG, 0.0)
        assertEquals(12, draft.intervalDays)
        assertEquals(
            listOf(LocalTime.of(20, 0), LocalTime.of(8, 30)),
            draft.times
        )
    }

    @Test
    fun `invalid text and schedule input returns explicit input errors`() {
        val result = input(
            doseMGText = "NaN",
            intervalDaysText = "0",
            scheduleType = ScheduleType.WEEKLY,
            times = emptyList(),
            daysOfWeek = emptySet()
        ).toMedicationPlanDraft(newSession())

        assertTrue(result is MedicationPlanInputResult.InvalidInput)
        assertEquals(
            listOf(
                MedicationPlanInputError.InvalidDoseMG,
                MedicationPlanInputError.NonPositiveIntervalDays,
                MedicationPlanInputError.MissingTime,
                MedicationPlanInputError.MissingWeeklyDay
            ),
            (result as MedicationPlanInputResult.InvalidInput).errors
        )
    }

    @Test
    fun `blank name reaches Draft validation instead of text parsing`() {
        val inputResult = input(name = "   ").toMedicationPlanDraft(newSession())

        assertTrue(inputResult is MedicationPlanInputResult.Success)
        val mappingResult =
            (inputResult as MedicationPlanInputResult.Success).draft.toDomainMedicationPlan()
        assertTrue(mappingResult is DraftMappingResult.InvalidDraft)
        assertEquals(
            listOf(DraftIssue.MissingRequiredField(DraftField.NAME)),
            (mappingResult as DraftMappingResult.InvalidDraft).issues
        )
    }

    @Test
    fun `editing preserves hidden extras and only updates visible route extra`() {
        val hiddenExtras = mapOf(
            ExtraKey.CONCENTRATION_MG_ML to 40.0,
            ExtraKey.AREA_CM2 to 12.5,
            ExtraKey.SUBLINGUAL_TIER to 1.0,
            ExtraKey.ANTI_ANDROGEN_TYPE to 3.0
        )
        val existing = plan(
            route = Route.SUBLINGUAL,
            extras = hiddenExtras
        )

        val result = input(
            route = Route.SUBLINGUAL,
            sublingualTier = SublingualTier.STRICT,
            selectedAntiAndrogen = AntiAndrogen.MPA
        ).toMedicationPlanDraft(MedicationPlanEditSessionFactory().edit(existing))

        assertTrue(result is MedicationPlanInputResult.Success)
        val extras = (result as MedicationPlanInputResult.Success).draft.extras
        assertEquals(40.0, extras[ExtraKey.CONCENTRATION_MG_ML])
        assertEquals(12.5, extras[ExtraKey.AREA_CM2])
        assertEquals(3.0, extras[ExtraKey.SUBLINGUAL_TIER])
        assertEquals(3.0, extras[ExtraKey.ANTI_ANDROGEN_TYPE])
    }

    @Test
    fun `antiandrogen and sublingual mappings use locked stable codes`() {
        val antiAndrogens = listOf(
            AntiAndrogen.CPA to 0.0,
            AntiAndrogen.MPA to 1.0,
            AntiAndrogen.BICALUTAMIDE to 2.0,
            AntiAndrogen.SPIRONOLACTONE to 3.0
        )
        antiAndrogens.forEach { (value, code) ->
            val result = input(
                route = Route.ANTIANDROGEN,
                selectedAntiAndrogen = value
            ).toMedicationPlanDraft(newSession()) as MedicationPlanInputResult.Success
            assertEquals(code, result.draft.extras[ExtraKey.ANTI_ANDROGEN_TYPE])
        }

        val tiers = listOf(
            SublingualTier.QUICK to 0.0,
            SublingualTier.CASUAL to 1.0,
            SublingualTier.STANDARD to 2.0,
            SublingualTier.STRICT to 3.0
        )
        tiers.forEach { (value, code) ->
            val result = input(
                route = Route.SUBLINGUAL,
                sublingualTier = value
            ).toMedicationPlanDraft(newSession()) as MedicationPlanInputResult.Success
            assertEquals(code, result.draft.extras[ExtraKey.SUBLINGUAL_TIER])
        }
    }

    private fun input(
        name: String = "Synthetic editor plan",
        route: Route = Route.ORAL,
        ester: Ester = Ester.E2,
        selectedAntiAndrogen: AntiAndrogen = AntiAndrogen.CPA,
        doseMGText: String = "2.0",
        scheduleType: ScheduleType = ScheduleType.DAILY,
        times: List<LocalTime> = listOf(LocalTime.of(8, 30)),
        daysOfWeek: Set<DayOfWeek> = emptySet(),
        intervalDaysText: String = "1",
        isEnabled: Boolean = true,
        sublingualTier: SublingualTier = SublingualTier.STANDARD
    ): MedicationPlanEditorInput = MedicationPlanEditorInput(
        name = name,
        route = route,
        ester = ester,
        selectedAntiAndrogen = selectedAntiAndrogen,
        doseMGText = doseMGText,
        scheduleType = scheduleType,
        times = times,
        daysOfWeek = daysOfWeek,
        intervalDaysText = intervalDaysText,
        isEnabled = isEnabled,
        sublingualTier = sublingualTier
    )

    private fun newSession(): MedicationPlanEditSession =
        MedicationPlanEditSession(UUID(0L, 12L), FIRST_INSTANT, null)

    private fun plan(
        route: Route = Route.ORAL,
        extras: Map<ExtraKey, Double> = emptyMap()
    ): MedicationPlan {
        val id = UUID(0L, 21L)
        return MedicationPlan(
            id = id,
            name = "Synthetic existing plan",
            route = route,
            ester = Ester.E2,
            doseMG = 2.0,
            scheduleType = ScheduleType.WEEKLY,
            slots = listOf(
                ScheduledDoseSlot(
                    id = UUID(1L, 1L),
                    planId = id,
                    localTime = LocalTime.of(8, 30),
                    position = 0
                )
            ),
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            intervalDays = 3,
            isEnabled = true,
            extras = extras,
            createdAt = FIRST_INSTANT
        )
    }

    private class CountingClock(
        instants: List<Instant>,
        private val zone: ZoneId = ZoneId.of("UTC")
    ) : Clock() {
        private val values = ArrayDeque(instants)
        var instantCalls: Int = 0
            private set

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = CountingClock(values.toList(), zone)

        override fun instant(): Instant {
            instantCalls += 1
            return values.removeFirst()
        }
    }

    private companion object {
        val FIRST_INSTANT: Instant = Instant.parse("2024-01-02T03:04:05Z")
        val SECOND_INSTANT: Instant = Instant.parse("2024-02-03T04:05:06Z")
    }
}
