package io.github.yuninggu.evolune.data.migration.contract

import io.github.yuninggu.evolune.data.Converters
import io.github.yuninggu.evolune.data.DoseEventEntity
import io.github.yuninggu.evolune.data.MedicationPlanEntity
import io.github.yuninggu.evolune.data.mapper.MappingError
import io.github.yuninggu.evolune.data.mapper.MappingResult
import io.github.yuninggu.evolune.data.mapper.toDomainDoseEvent
import io.github.yuninggu.evolune.data.mapper.toDomainMedicationPlan
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class MigrationPersistenceContractTest {
    private val converters = Converters()

    @Test
    fun inventoryCoversEverySchema2ColumnAndEverySchema3Mapping() {
        val fields = PersistedContractInventory.schema2Fields

        assertEquals(18, fields.size)
        assertEquals(6, fields.count { it.aggregate == "dose_events" })
        assertEquals(12, fields.count { it.aggregate == "medication_plans" })
        assertEquals(fields.size, fields.map { "${it.aggregate}.${it.field}" }.toSet().size)
        assertTrue(fields.all { it.migrationPreflightRequired })
        assertTrue(fields.all { it.repositoryReadRequired })
        assertTrue(fields.all { it.fixtureRequired })
        assertEquals(26, PersistedContractInventory.schema2To3Mappings.size)
        assertEquals(
            fields.map { "${it.aggregate}.${it.field}" }.toSet(),
            PersistedContractInventory.schema2To3Mappings
                .filter { it.operation == "preserve" }
                .map { it.schema2Column }
                .toSet()
        )
    }

    @Test
    fun convertersAcceptExactlyTheLockedEmptyAndJsonShapes() {
        assertEquals(emptyMap<String, Double>(), converters.toMap(""))
        assertEquals(mapOf("AREA_CM2" to 4.0), converters.toMap("{\"AREA_CM2\":4.0}"))
        assertEquals(emptyList<String>(), converters.toStringList(""))
        assertEquals(listOf("08:30", "20:00"), converters.toStringList("[\"08:30\",\"20:00\"]"))
        assertEquals(emptySet<Int>(), converters.toIntSet(""))
        assertEquals(setOf(1, 5, 7), converters.toIntSet("[1,5,7]"))
        assertEquals(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            converters.toUUID("00000000-0000-0000-0000-000000000001")
        )
    }

    @Test
    fun malformedConverterPayloadsRemainExplicitFailures() {
        assertFails<SerializationException> { converters.toMap("{") }
        assertFails<SerializationException> { converters.toStringList("[\"08:30\"") }
        assertFails<SerializationException> { converters.toIntSet("{") }
        assertFails<IllegalArgumentException> { converters.toUUID("not-a-uuid") }
    }

    @Test
    fun everyRouteEsterAndExtraKeyStorageValueMapsThroughDoseEventMapper() {
        val routes = listOf(
            "INJECTION",
            "ORAL",
            "SUBLINGUAL",
            "GEL",
            "PATCH_APPLY",
            "PATCH_REMOVE",
            "ANTIANDROGEN"
        )
        val esters = listOf("E2", "EB", "EV", "EC", "EN")
        val extras = linkedMapOf(
            "CONCENTRATION_MG_ML" to 20.0,
            "AREA_CM2" to 4.0,
            "RELEASE_RATE_UG_PER_DAY" to 50.0,
            "SUBLINGUAL_THETA" to 0.4,
            "SUBLINGUAL_TIER" to 2.0,
            "ANTI_ANDROGEN_TYPE" to 1.0
        )

        routes.forEachIndexed { index, route ->
            val result = DoseEventEntity(
                id = UUID(0L, index.toLong() + 1),
                route = route,
                timeH = 0.0,
                doseMG = 1.0,
                ester = esters[index % esters.size],
                extras = extras,
                occurredAtEpochMillis = 0L
            ).toDomainDoseEvent()
            assertTrue("$route must map", result is MappingResult.Success)
        }
    }

    @Test
    fun doseEventMapperClassifiesEveryLockedInvalidStorageDimension() {
        assertEquals(
            MappingError.InvalidRoute("UNKNOWN"),
            event(route = "UNKNOWN").failureError()
        )
        assertEquals(
            MappingError.InvalidEster("UNKNOWN"),
            event(ester = "UNKNOWN").failureError()
        )
        assertEquals(
            MappingError.InvalidExtraKey("UNKNOWN"),
            event(extras = mapOf("UNKNOWN" to 1.0)).failureError()
        )
    }

    @Test
    fun medicationPlanMapperLocksScheduleDaysIntervalAndExtrasFailures() {
        listOf("DAILY", "WEEKLY", "CUSTOM").forEach { scheduleType ->
            assertTrue(plan(scheduleType = scheduleType).toDomainMedicationPlan() is MappingResult.Success)
        }
        assertEquals(
            MappingError.InvalidScheduleType("UNKNOWN"),
            plan(scheduleType = "UNKNOWN").failureError()
        )
        assertEquals(
            MappingError.InvalidDayOfWeek(8),
            plan(days = setOf(8)).failureError()
        )
        assertEquals(
            MappingError.InvalidPlanInvariant(0),
            plan(intervalDays = 0).failureError()
        )
        assertEquals(
            MappingError.InvalidExtraKey("UNKNOWN"),
            plan(extras = mapOf("UNKNOWN" to 1.0)).failureError()
        )
        assertTrue(plan(times = listOf("08:30", "08:30")).toDomainMedicationPlan() is MappingResult.Success)
        assertTrue(plan(scheduleType = "WEEKLY", days = emptySet()).toDomainMedicationPlan() is MappingResult.Success)
        assertTrue(plan(scheduleType = "DAILY", days = setOf(1, 5), intervalDays = 9).toDomainMedicationPlan() is MappingResult.Success)
    }

    private fun event(
        route: String = "ORAL",
        ester: String = "E2",
        extras: Map<String, Double> = emptyMap()
    ) = DoseEventEntity(
        id = UUID(0L, 1L),
        route = route,
        timeH = 0.0,
        doseMG = 1.0,
        ester = ester,
        extras = extras,
        occurredAtEpochMillis = 0L
    )

    private fun plan(
        scheduleType: String = "DAILY",
        days: Set<Int> = emptySet(),
        intervalDays: Int = 1,
        extras: Map<String, Double> = emptyMap(),
        times: List<String> = emptyList()
    ) = MedicationPlanEntity(
        id = UUID(0L, 2L),
        name = "Synthetic plan",
        route = "ORAL",
        ester = "E2",
        doseMG = 1.0,
        scheduleType = scheduleType,
        timeOfDay = times,
        daysOfWeek = days,
        intervalDays = intervalDays,
        isEnabled = true,
        extras = extras,
        createdAt = 0L
    )

    private fun DoseEventEntity.failureError(): MappingError =
        (toDomainDoseEvent() as MappingResult.Failure).error

    private fun MedicationPlanEntity.failureError(): MappingError =
        (toDomainMedicationPlan() as MappingResult.Failure).error

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }
}
