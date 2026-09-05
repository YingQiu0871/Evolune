package io.github.yingqiu0871.evolune.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Android-runtime PK measurements for the three fixed v1.5 data states.
 *
 * This is a diagnostic benchmark, not a pass/fail performance gate. It keeps the
 * data shape and clock fixed, checks repeated output at the observation point, and
 * emits measurements for the acceptance record without changing production code.
 */
@RunWith(AndroidJUnit4::class)
class V15PkPerformanceDeviceTest {
    @Test
    fun fixedDatasetsProduceRepeatablePkMeasurements() = runBlocking {
        DATASETS.forEach { dataset ->
            assertTrue(dataset.name.isNotBlank())
            assertEquals(dataset.planCount, dataset.plans.size)
            assertEquals(dataset.eventCount, dataset.events.size)

            val first = DefaultPkSimulationCalculator.calculate(dataset.input)
            val measuredNanos = LongArray(MEASUREMENT_RUNS) {
                val startedAt = SystemClock.elapsedRealtimeNanos()
                val result = DefaultPkSimulationCalculator.calculate(dataset.input)
                assertEquals(first.currentConcentration, result.currentConcentration)
                assertEquals(first.simulationResult?.timeH?.size, result.simulationResult?.timeH?.size)
                SystemClock.elapsedRealtimeNanos() - startedAt
            }

            val sorted = measuredNanos.sorted()
            val medianMillis = sorted[sorted.lastIndex / 2] / NANOS_PER_MILLISECOND
            val meanMillis = measuredNanos.average() / NANOS_PER_MILLISECOND
            Log.i(
                TAG,
                "PK_DATASET name=${dataset.name} plans=${dataset.planCount} " +
                    "slots=${dataset.slotCount} events=${dataset.eventCount} " +
                    "runs=$MEASUREMENT_RUNS medianMs=$medianMillis " +
                    "meanMs=${"%.3f".format(java.util.Locale.ROOT, meanMillis)} " +
                    "concentration=${first.currentConcentration}"
            )
        }
    }

    private data class Dataset(
        val name: String,
        val plans: List<MedicationPlan>,
        val events: List<DoseEvent>,
        val input: PkSimulationInput
    ) {
        val planCount: Int get() = plans.size
        val slotCount: Int get() = plans.sumOf { it.slots.size }
        val eventCount: Int get() = events.size
    }

    private companion object {
        const val TAG = "EvoluneV15Pk"
        const val MEASUREMENT_RUNS = 3
        const val NANOS_PER_MILLISECOND = 1_000_000.0
        val ZONE: ZoneId = ZoneId.of("Europe/Paris")
        val NOW: Instant = Instant.parse("2026-09-04T12:00:00Z")
        val CURRENT_TIME_H: Double = NOW.toEpochMilli() / 3_600_000.0
        val SLOT_TIMES = listOf(
            LocalTime.of(3, 15),
            LocalTime.of(9, 0),
            LocalTime.of(21, 0)
        )

        val DATASETS: List<Dataset> = listOf(
            dataset("PK-EMPTY", emptyList(), emptyList()),
            dataset("PK-STEADY", buildPlans(1), buildEvents(buildPlans(1), 30)),
            dataset("PK-DENSE", buildPlans(3), buildEvents(buildPlans(3), 90))
        )

        fun dataset(
            name: String,
            plans: List<MedicationPlan>,
            events: List<DoseEvent>
        ): Dataset = Dataset(
            name = name,
            plans = plans,
            events = events,
            input = PkSimulationInput(
                now = NOW,
                currentTimeH = CURRENT_TIME_H,
                historicalDoseEvents = events,
                enabledPlans = plans,
                bodyWeightKG = 62.0,
                zoneId = ZONE
            )
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
                name = "v1.5 benchmark plan ${index + 1}",
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
