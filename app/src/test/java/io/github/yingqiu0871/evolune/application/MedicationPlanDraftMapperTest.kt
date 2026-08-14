package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class MedicationPlanDraftMapperTest {
    @Test
    fun emptyTimesMapToEmptySlots() {
        val plan = success(draft(times = emptyList()).toDomainMedicationPlan())

        assertTrue(plan.slots.isEmpty())
    }

    @Test
    fun oneTimeMapsToPositionZero() {
        val plan = success(
            draft(times = listOf(LocalTime.of(8, 30))).toDomainMedicationPlan()
        )

        assertEquals(0, plan.slots.single().position)
        assertEquals(LocalTime.of(8, 30), plan.slots.single().localTime)
    }

    @Test
    fun multipleTimesKeepOrderAndContinuousPositions() {
        val times = listOf(LocalTime.of(22, 15), LocalTime.of(6, 45), LocalTime.NOON)

        val plan = success(draft(times = times).toDomainMedicationPlan())

        assertEquals(times, plan.slots.map { it.localTime })
        assertEquals(listOf(0, 1, 2), plan.slots.map { it.position })
    }

    @Test
    fun duplicateTimesArePreservedWithDistinctIds() {
        val duplicate = LocalTime.of(9, 5)

        val plan = success(
            draft(times = listOf(duplicate, duplicate)).toDomainMedicationPlan()
        )

        assertEquals(listOf(duplicate, duplicate), plan.slots.map { it.localTime })
        assertNotEquals(plan.slots[0].id, plan.slots[1].id)
    }

    @Test
    fun midnightAndEndOfDayAreAcceptedAndCanonical() {
        val plan = success(
            draft(times = listOf(LocalTime.MIDNIGHT, LocalTime.of(23, 59)))
                .toDomainMedicationPlan()
        )

        assertEquals(listOf("00:00", "23:59"), plan.slots.map { it.localTime.toString() })
    }

    @Test
    fun nonZeroSecondsAreRejectedAtTheirPosition() {
        assertEquals(
            listOf(DraftIssue.NonMinuteTime(1)),
            invalid(
                draft(
                    times = listOf(LocalTime.NOON, LocalTime.of(8, 30, 1))
                ).toDomainMedicationPlan()
            )
        )
    }

    @Test
    fun nonZeroNanosAreRejectedAtTheirPosition() {
        assertEquals(
            listOf(DraftIssue.NonMinuteTime(0)),
            invalid(
                draft(times = listOf(LocalTime.of(8, 30, 0, 1)))
                    .toDomainMedicationPlan()
            )
        )
    }

    @Test
    fun fixedUuidV5VectorMatchesLockedExpectedValue() {
        val plan = success(
            draft(
                id = FIXED_PLAN_ID,
                times = listOf(LocalTime.of(8, 30))
            ).toDomainMedicationPlan()
        )

        assertEquals(
            UUID.fromString("17d1fd14-9d70-5344-beaa-0b158c9f62f4"),
            plan.slots.single().id
        )
    }

    @Test
    fun planIdIsPreservedByPlanAndEverySlot() {
        val id = uuid(42)

        val plan = success(
            draft(
                id = id,
                times = listOf(LocalTime.of(7, 0), LocalTime.of(19, 0))
            ).toDomainMedicationPlan()
        )

        assertEquals(id, plan.id)
        assertTrue(plan.slots.all { it.planId == id })
    }

    @Test
    fun fixedCreatedAtIsPreservedExactly() {
        val createdAt = Instant.parse("2024-02-29T12:34:56.789Z")

        val plan = success(draft(createdAt = createdAt).toDomainMedicationPlan())

        assertEquals(createdAt, plan.createdAt)
    }

    @Test
    fun everyDraftFieldIncludingExtrasIsPreserved() {
        val source = draft(
            id = uuid(81),
            name = "Synthetic complete plan",
            route = Route.SUBLINGUAL,
            ester = Ester.E2,
            doseMG = 1.75,
            scheduleType = ScheduleType.CUSTOM,
            times = listOf(LocalTime.of(6, 5), LocalTime.of(21, 40)),
            daysOfWeek = setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY),
            intervalDays = 17,
            isEnabled = false,
            extras = mapOf(
                ExtraKey.SUBLINGUAL_THETA to 0.4,
                ExtraKey.SUBLINGUAL_TIER to 2.0,
                ExtraKey.CONCENTRATION_MG_ML to 15.0
            ),
            createdAt = Instant.parse("2023-07-08T09:10:11.012Z")
        )

        val plan = success(source.toDomainMedicationPlan())

        assertEquals(source.id, plan.id)
        assertEquals(source.name, plan.name)
        assertEquals(source.route, plan.route)
        assertEquals(source.ester, plan.ester)
        assertEquals(source.doseMG, plan.doseMG, 0.0)
        assertEquals(source.scheduleType, plan.scheduleType)
        assertEquals(source.times, plan.slots.map { it.localTime })
        assertEquals(source.daysOfWeek, plan.daysOfWeek)
        assertEquals(source.intervalDays, plan.intervalDays)
        assertEquals(source.isEnabled, plan.isEnabled)
        assertEquals(source.extras, plan.extras)
        assertEquals(source.createdAt, plan.createdAt)
    }

    @Test
    fun blankNameReturnsMissingRequiredField() {
        assertEquals(
            listOf(DraftIssue.MissingRequiredField(DraftField.NAME)),
            invalid(draft(name = " \t").toDomainMedicationPlan())
        )
    }

    @Test
    fun invalidDomainInvariantReturnsStableIssue() {
        assertEquals(
            listOf(DraftIssue.DomainValidationFailure),
            invalid(draft(intervalDays = 0).toDomainMedicationPlan())
        )
    }

    @Test
    fun fieldAndTimeIssuesUseStableValidationOrder() {
        val result = draft(
            name = "",
            times = listOf(
                LocalTime.of(8, 0, 1),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0, 0, 1)
            )
        ).toDomainMedicationPlan()

        assertEquals(
            listOf(
                DraftIssue.MissingRequiredField(DraftField.NAME),
                DraftIssue.NonMinuteTime(0),
                DraftIssue.NonMinuteTime(2)
            ),
            invalid(result)
        )
    }

    @Test
    fun domainToDraftToDomainPreservesCompletePlan() {
        val original = success(
            draft(
                id = uuid(91),
                times = listOf(LocalTime.of(20, 0), LocalTime.of(7, 30), LocalTime.of(20, 0)),
                extras = mapOf(
                    ExtraKey.ANTI_ANDROGEN_TYPE to 1.0,
                    ExtraKey.AREA_CM2 to 12.5
                )
            ).toDomainMedicationPlan()
        )

        val mappedDraft = success(original.toMedicationPlanDraft())
        val roundTrip = success(mappedDraft.toDomainMedicationPlan())

        assertEquals(original, roundTrip)
    }

    @Test
    fun domainToDraftRejectsUnexpectedSlotId() {
        val original = success(draft(times = listOf(LocalTime.of(8, 30))).toDomainMedicationPlan())
        val wrongSlot = original.slots.single().copy(id = uuid(999))
        val mismatched = original.copy(slots = listOf(wrongSlot))

        assertEquals(
            listOf(DraftIssue.SlotIdMismatch(0)),
            invalid(mismatched.toMedicationPlanDraft())
        )
    }

    @Test
    fun localeAndDefaultTimeZoneDoNotChangeMapping() {
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val first = success(draft().toDomainMedicationPlan())

            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val second = success(draft().toDomainMedicationPlan())

            assertEquals(first, second)
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun mapperSourceHasNoClockRandomPlatformOrSideEffectDependencies() {
        val source = Files.readString(mapperSourcePath())
        val forbidden = listOf(
            "UUID.randomUUID",
            "Instant.now",
            "System.currentTimeMillis",
            "Clock.system",
            "android.",
            "androidx.",
            "Room",
            "Repository",
            "JSON",
            "Reminder",
            "Predictor",
            "Widget",
            "Wear",
            "ZoneId.systemDefault",
            "Locale.getDefault"
        )

        forbidden.forEach { token ->
            assertFalse("Unexpected dependency token: $token", source.contains(token))
        }
    }

    private fun draft(
        id: UUID = FIXED_PLAN_ID,
        name: String = "Synthetic plan",
        route: Route = Route.ORAL,
        ester: Ester = Ester.EV,
        doseMG: Double = 2.0,
        scheduleType: ScheduleType = ScheduleType.DAILY,
        times: List<LocalTime> = listOf(LocalTime.of(8, 30)),
        daysOfWeek: Set<DayOfWeek> = emptySet(),
        intervalDays: Int = 1,
        isEnabled: Boolean = true,
        extras: Map<ExtraKey, Double> = emptyMap(),
        createdAt: Instant = Instant.parse("2024-01-02T03:04:05.006Z")
    ): MedicationPlanDraft = MedicationPlanDraft(
        id = id,
        name = name,
        route = route,
        ester = ester,
        doseMG = doseMG,
        scheduleType = scheduleType,
        times = times,
        daysOfWeek = daysOfWeek,
        intervalDays = intervalDays,
        isEnabled = isEnabled,
        extras = extras,
        createdAt = createdAt
    )

    private fun <T> success(result: DraftMappingResult<T>): T {
        assertTrue(result is DraftMappingResult.Success)
        return (result as DraftMappingResult.Success).value
    }

    private fun invalid(result: DraftMappingResult<*>): List<DraftIssue> {
        assertTrue(result is DraftMappingResult.InvalidDraft)
        return (result as DraftMappingResult.InvalidDraft).issues
    }

    private fun mapperSourcePath(): Path {
        val relative = Path.of(
            "src/main/java/io/github/yingqiu0871/evolune/application/MedicationPlanDraftMapper.kt"
        )
        val rootRelative = Path.of("app").resolve(relative)
        return when {
            Files.exists(relative) -> relative
            Files.exists(rootRelative) -> rootRelative
            else -> throw AssertionError("MedicationPlanDraftMapper.kt source was not found")
        }
    }

    private fun uuid(value: Int): UUID = UUID(0L, value.toLong())

    private companion object {
        val FIXED_PLAN_ID: UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
