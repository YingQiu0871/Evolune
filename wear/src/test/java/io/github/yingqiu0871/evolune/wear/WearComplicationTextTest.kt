package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentration
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOverallStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppTodaySummary
import io.github.yingqiu0871.evolune.experience.wear.WearAppTodaySummaryState
import io.github.yingqiu0871.evolune.experience.wear.WearAppUpcomingOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WearComplicationTextTest {
    private val snapshot = WearAppSnapshot(
        protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
        snapshotRevision = 5L,
        generatedAt = Instant.parse("2026-09-06T00:00:00Z"),
        zoneId = "Asia/Shanghai",
        overallStatus = WearAppOverallStatus.READY,
        recentDose = null,
        upcomingOccurrences = listOf(
            WearAppUpcomingOccurrence(
                occurrenceId = UUID(0L, 1L),
                planId = UUID(0L, 2L),
                slotId = UUID(0L, 3L),
                localDate = LocalDate.parse("2026-09-06"),
                scheduledAt = Instant.parse("2026-09-06T04:00:00Z"),
                medicationName = "雌二醇",
                route = "口服",
                dose = 2.0,
                doseUnit = "mg",
                status = WearAppOccurrenceStatus.DUE
            )
        ),
        concentrationState = WearAppConcentration(
            status = WearAppConcentrationStatus.AVAILABLE,
            value = 123.4,
            unit = "pg/mL",
            calculatedAt = Instant.parse("2026-09-06T00:00:00Z")
        ),
        producerInstanceId = UUID(0L, 4L),
        producerGeneration = 1L,
        todaySummary = WearAppTodaySummary(
            todayLocalDate = LocalDate.parse("2026-09-06"),
            computedAt = Instant.parse("2026-09-06T00:00:00Z"),
            completedCount = 1,
            totalCount = 3,
            state = WearAppTodaySummaryState.HAS_OCCURRENCES
        )
    )

    @Test
    fun `three complication texts use shared snapshot semantics`() {
        assertEquals("现在", nextDoseComplicationText(snapshot))
        assertEquals("1/3", todayProgressComplicationText(snapshot))
        assertEquals("123", currentE2ComplicationText(snapshot))
    }

    @Test
    fun `missing optional values render unavailable markers`() {
        assertEquals("--", nextDoseComplicationText(null))
        assertEquals("--", todayProgressComplicationText(snapshot.copy(todaySummary = null)))
        assertEquals(
            "--",
            currentE2ComplicationText(
                snapshot.copy(concentrationState = WearAppConcentration.unavailable())
            )
        )
    }

    @Test
    fun `current E2 provider value is unavailable when concentration freshness is stale`() {
        assertEquals(
            "--",
            currentE2ComplicationText(
                snapshot,
                Instant.parse("2026-09-06T00:30:00Z").toEpochMilli()
            )
        )
    }

    @Test
    fun `short text contract carries value description and preview has no tap action`() {
        val data = buildShortTextData(null, "12:00", "下一次", tapAction = false)
        assertEquals("12:00", complicationTextContract("下一次", "12:00").value)
        assertEquals("下一次：12:00", complicationTextContract("下一次", "12:00").contentDescription)
        assertNull(data.tapAction)
        assertEquals("08:00", complicationPreviewValue(WearComplicationKind.NEXT_DOSE))
    }

    @Test
    fun `all providers expose preview and READY or non READY values through the same contract`() {
        val now = Instant.parse("2026-09-06T00:00:30Z").toEpochMilli()
        listOf(
            WearComplicationKind.NEXT_DOSE to "现在",
            WearComplicationKind.TODAY_PROGRESS to "1/3",
            WearComplicationKind.CURRENT_E2 to "123"
        ).forEach { (kind, expected) ->
            val ready = complicationDataForState(kind, snapshot, WearAppDisplayState.READY, now)
            assertEquals(expected, complicationDisplayValueForState(kind, snapshot, WearAppDisplayState.READY, now))
            assertEquals("--", complicationDisplayValueForState(kind, snapshot, WearAppDisplayState.STALE, now))
            assertNull(complicationPreviewData(kind).tapAction)
        }
    }
}
