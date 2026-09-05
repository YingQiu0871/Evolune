package io.github.yingqiu0871.evolune.data.repository

import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.data.mapper.toPersistenceAggregate
import io.github.yingqiu0871.evolune.data.mapper.toV3Entity
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Android-runtime Room measurements for the same fixed data shapes used by the v1.5 PK
 * diagnostic. This is observation-only: it uses an isolated in-memory database and does not
 * change production data or assert an arbitrary device-specific latency threshold.
 */
@RunWith(AndroidJUnit4::class)
class V15RoomPerformanceDeviceTest {
    @Test
    fun fixedDatasetsProduceRepeatableRoomMeasurements() = runBlocking {
        DATASETS.forEach { dataset ->
            val database = Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                AppDatabase::class.java
            ).build()
            try {
                seed(database, dataset)

                val eventRepository = RoomDoseEventRepository(database)
                val planRepository = RoomMedicationPlanRepository(database)
                val firstEvents = eventRepository.getEventsForPk(NOW)
                val firstPlans = planRepository.observeEnabled().first()
                assertEquals(dataset.expectedPkEventCount, firstEvents.size)
                assertEquals(dataset.plans, firstPlans)

                repeat(WARMUP_RUNS) {
                    eventRepository.getEventsForPk(NOW)
                    planRepository.observeEnabled().first()
                }

                val eventTimings = LongArray(MEASUREMENT_RUNS) {
                    val startedAt = SystemClock.elapsedRealtimeNanos()
                    val result = eventRepository.getEventsForPk(NOW)
                    assertEquals(firstEvents, result)
                    SystemClock.elapsedRealtimeNanos() - startedAt
                }
                val planTimings = LongArray(MEASUREMENT_RUNS) {
                    val startedAt = SystemClock.elapsedRealtimeNanos()
                    val result = planRepository.observeEnabled().first()
                    assertEquals(firstPlans, result)
                    SystemClock.elapsedRealtimeNanos() - startedAt
                }

                Log.i(
                    TAG,
                    "ROOM_DATASET name=${dataset.name} plans=${dataset.plans.size} " +
                        "slots=${dataset.slotCount} events=${dataset.events.size} " +
                        "pkEvents=${firstEvents.size} " +
                        "pkFetch=${statistics(eventTimings)} " +
                        "enabledPlanAggregate=${statistics(planTimings)}"
                )
            } finally {
                database.close()
            }
        }
    }

    private suspend fun seed(database: AppDatabase, dataset: Dataset) {
        val planAggregates = dataset.plans.map { plan ->
            when (val result = plan.toPersistenceAggregate()) {
                is io.github.yingqiu0871.evolune.data.mapper.MappingResult.Success -> result.value
                is io.github.yingqiu0871.evolune.data.mapper.MappingResult.Failure ->
                    error("failed to map Room benchmark plan: ${result.error}")
            }
        }
        val eventEntities = dataset.events.map { event ->
            when (val result = event.toV3Entity()) {
                is io.github.yingqiu0871.evolune.data.mapper.MappingResult.Success -> result.value
                is io.github.yingqiu0871.evolune.data.mapper.MappingResult.Failure ->
                    error("failed to map Room benchmark event: ${result.error}")
            }
        }
        database.withTransaction {
            database.medicationPlanDao().insertPlansForRestore(planAggregates.map { it.plan })
            database.scheduledDoseSlotDao().insertSlotsForRestore(
                planAggregates.flatMap { it.slots }
            )
            database.doseEventDao().insertEventsForRestore(eventEntities)
        }
    }

    private fun statistics(nanos: LongArray): String {
        val sorted = nanos.sorted()
        val medianMillis = sorted[sorted.lastIndex / 2] / NANOS_PER_MILLISECOND
        val meanMillis = nanos.average() / NANOS_PER_MILLISECOND
        return "runs=${nanos.size} medianMs=$medianMillis " +
            "meanMs=${"%.3f".format(java.util.Locale.ROOT, meanMillis)} " +
            "minMs=${sorted.first() / NANOS_PER_MILLISECOND} " +
            "maxMs=${sorted.last() / NANOS_PER_MILLISECOND}"
    }

    private data class Dataset(
        val name: String,
        val plans: List<MedicationPlan>,
        val events: List<DoseEvent>
    ) {
        val slotCount: Int get() = plans.sumOf { it.slots.size }
        val expectedPkEventCount: Int get() = when {
            events.size < PK_MINIMUM_DOSE_EVENTS -> events.size
            else -> events.count { it.occurredAt >= NOW.minusSeconds(30L * 24L * 60L * 60L) }
        }
    }

    private companion object {
        const val TAG = "EvoluneV15Room"
        const val WARMUP_RUNS = 2
        const val MEASUREMENT_RUNS = 7
        const val PK_MINIMUM_DOSE_EVENTS = 20
        const val NANOS_PER_MILLISECOND = 1_000_000.0
        val ZONE: ZoneId = ZoneId.of("Europe/Paris")
        val NOW: Instant = Instant.parse("2026-09-04T12:00:00Z")
        val SLOT_TIMES = listOf(
            LocalTime.of(3, 15),
            LocalTime.of(9, 0),
            LocalTime.of(21, 0)
        )
        val DATASETS: List<Dataset> = listOf(
            Dataset("PK-EMPTY", emptyList(), emptyList()),
            Dataset("PK-STEADY", buildPlans(1), buildEvents(buildPlans(1), 30)),
            Dataset("PK-DENSE", buildPlans(3), buildEvents(buildPlans(3), 90))
        )

        fun buildPlans(count: Int): List<MedicationPlan> = (0 until count).map { index ->
            val planId = UUID(0x1500000000000000L + index, index.toLong() + 1L)
            val slots = SLOT_TIMES.mapIndexed { position, time ->
                val slotId = when (
                    val result = ScheduledDoseSlotId.generate(planId, position, time)
                ) {
                    is io.github.yingqiu0871.evolune.core.model.SlotIdResult.Success -> result.id
                    is io.github.yingqiu0871.evolune.core.model.SlotIdResult.Failure ->
                        error("failed to generate benchmark slot: ${result.error}")
                }
                ScheduledDoseSlot(slotId, planId, time, position)
            }
            MedicationPlan(
                id = planId,
                name = "v1.5 Room benchmark plan ${index + 1}",
                route = Route.ORAL,
                ester = Ester.E2,
                doseMG = 1.0 + index,
                scheduleType = ScheduleType.DAILY,
                slots = slots,
                daysOfWeek = emptySet(),
                intervalDays = 1,
                isEnabled = true,
                extras = emptyMap(),
                createdAt = Instant.parse("2026-01-01T00:00:00Z")
            )
        }

        fun buildEvents(plans: List<MedicationPlan>, days: Int): List<DoseEvent> {
            val endDate = NOW.atZone(ZONE).toLocalDate().minusDays(1)
            return buildList {
                repeat(days) { dayOffset ->
                    val date = endDate.minusDays((days - 1 - dayOffset).toLong())
                    plans.forEachIndexed { planIndex, plan ->
                        plan.slots.forEach { slot ->
                            val localDateTime = LocalDateTime.of(date, slot.localTime)
                            add(
                                DoseEvent(
                                    id = UUID(
                                        0x1600000000000000L + planIndex,
                                        dayOffset.toLong() * 10L + slot.position + 1L
                                    ),
                                    route = plan.route,
                                    occurredAt = localDateTime.atZone(ZONE).toInstant(),
                                    zoneId = ZONE,
                                    localDate = date,
                                    doseMG = plan.doseMG,
                                    ester = plan.ester,
                                    slotId = slot.id,
                                    source = DoseEventSource.MANUAL,
                                    status = DoseEventStatus.RECORDED
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
