package io.github.yingqiu0871.evolune.labs

import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LabResultRepository
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectiveLabPoint
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.viewmodel.E2CurveCalibrationCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class RepositoryRetrospectiveCalibrationSourceTest {
    @Test
    fun `reading matches the Home fit and returns comparable labs at their exact draw time`() =
        runBlocking {
            val events = listOf(injection(FIRST_DOSE), injection(FIRST_DOSE.plusSeconds(WEEK_S)))
            val comparable = LabResult(LAB_A, FIRST_DOSE.plusSeconds(WEEK_S + 2 * DAY_S), 250.0, LabUnit.PG_PER_ML)
            val beforeAnyDose = LabResult(LAB_B, FIRST_DOSE.minusSeconds(DAY_S), 40.0, LabUnit.PG_PER_ML)
            val testosterone = LabResult(LAB_C, FIRST_DOSE.plusSeconds(DAY_S), 30.0, LabUnit.NG_PER_DL)
            val labs = listOf(beforeAnyDose, comparable, testosterone)

            val reading = RepositoryRetrospectiveCalibrationSource(
                doseEvents = FakeDoseEventRepository(events),
                labResults = StaticLabs(labs),
                dispatcher = Dispatchers.Unconfined
            ).read(bodyWeightKg = 60.0)

            val home = E2CurveCalibrationCalculator.calculate(events, labs, 60.0)
            assertEquals(home.scale, reading.scale, 0.0)
            assertEquals(1, reading.labCount)
            assertEquals(listOf(RetrospectiveLabPoint(comparable.measuredAt, 250.0)), reading.labPoints)
        }

    private class StaticLabs(private val results: List<LabResult>) : LabResultRepository {
        override fun observeAll(): Flow<List<LabResult>> = flowOf(results)
        override suspend fun getById(id: UUID): LabResult? = null
        override suspend fun insert(result: LabResult): InsertResult = InsertResult.Invalid
        override suspend fun update(result: LabResult, expectedRevision: Long): UpdateResult =
            UpdateResult.Invalid
        override suspend fun deleteIfRevisionMatches(
            id: UUID,
            expectedRevision: Long
        ): ConditionalDeleteResult = ConditionalDeleteResult.Invalid
    }

    private fun injection(at: Instant) = DoseEvent(
        id = UUID.randomUUID(),
        route = Route.INJECTION,
        occurredAt = at,
        doseMG = 5.0,
        ester = Ester.EV,
        source = DoseEventSource.MANUAL
    )

    private companion object {
        const val DAY_S = 86_400L
        const val WEEK_S = 7 * DAY_S
        val FIRST_DOSE: Instant = Instant.parse("2025-03-01T08:00:00Z")
        val LAB_A: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
        val LAB_B: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000b2")
        val LAB_C: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000c3")
    }
}
