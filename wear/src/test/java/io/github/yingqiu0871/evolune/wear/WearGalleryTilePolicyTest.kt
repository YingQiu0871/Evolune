package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultType
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentration
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppUpcomingOccurrence
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import io.github.yingqiu0871.evolune.experience.wear.WearAppOverallStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppTodaySummary
import io.github.yingqiu0871.evolune.experience.wear.WearAppTodaySummaryState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WearGalleryTilePolicyTest {
    private val today = LocalDate.parse("2026-09-06")
    private val todayOccurrence = occurrence(UUID(0L, 1L), today)
    private val tomorrowOccurrence = occurrence(UUID(0L, 2L), today.plusDays(1))

    @Test
    fun `Tile layout and resource response share a stable version`() {
        assertEquals("7-NEXT_DOSE-SYSTEM", wearGalleryResourcesVersion(WearGalleryTileKind.NEXT_DOSE))
        assertEquals("7-TODAY_PLAN-SYSTEM", wearGalleryResourcesVersion(WearGalleryTileKind.TODAY_PLAN))
        assertEquals("7-CURRENT_E2-SYSTEM", wearGalleryResourcesVersion(WearGalleryTileKind.CURRENT_E2))
        assertEquals(
            "7-CURRENT_E2-SAKURA",
            wearGalleryResourcesVersion(WearGalleryTileKind.CURRENT_E2, WearColorScheme.SAKURA)
        )
    }

    @Test
    fun `gallery panels use one moderate corner radius across tile kinds`() {
        assertEquals(24f, WEAR_GALLERY_PANEL_CORNER_RADIUS_DP, 0f)
    }

    @Test
    fun `gallery panels leave horizontal room inside a round viewport`() {
        assertEquals(156f, WEAR_GALLERY_PANEL_WIDTH_DP, 0f)
    }

    @Test
    fun `today tile only exposes occurrences from snapshot today`() {
        val snapshot = snapshot(listOf(todayOccurrence, tomorrowOccurrence))
        assertEquals(listOf(todayOccurrence), todayPendingOccurrences(snapshot))
    }

    @Test
    fun `confirmation label follows pending and terminal result lifecycle`() {
        assertEquals("待服", confirmationLabel(null, "待服"))
        val pending = WearAppPendingConfirmation(
            command = command(todayOccurrence.occurrenceId),
            commandDataItemUri = "wear-app/commands/test",
            terminalResult = null,
            sendAttempt = 1L
        )
        assertEquals("已发送，等待手机回执", confirmationLabel(pending, "待服"))
        val confirmed = pending.copy(terminalResult = result(WearAppConfirmResultType.CONFIRMED))
        assertEquals("已确认，等待快照更新", confirmationLabel(confirmed, "待服"))
        val retryable = pending.copy(terminalResult = result(WearAppConfirmResultType.RETRYABLE_STORAGE_FAILURE))
        assertEquals("确认失败，请重试", confirmationLabel(retryable, "待服"))
        assertEquals("确认失败，请重试", nextDoseActionLabel(retryable, "待服"))
        assertEquals("待服", nextDoseActionLabel(null, "待服"))
    }

    @Test
    fun `today tile exposes an explicit overflow label`() {
        assertEquals("还有 2 项 · 打开手机查看", todayPendingOverflowLabel(5, 3))
        val authoritative = snapshot(List(5) { index -> occurrence(UUID(0L, 20L + index), today) })
            .copy(todaySummary = authoritativeSummary(total = 6, completed = 0))
        assertEquals(6, todayPendingCount(authoritative, authoritative.upcomingOccurrences))
        assertEquals("还有 3 项 · 打开手机查看", todayPendingOverflowLabel(
            todayPendingCount(authoritative, authoritative.upcomingOccurrences), 3
        ))
    }

    @Test
    fun `stale concentration is never presented as current`() {
        val snapshot = snapshot(listOf(todayOccurrence)).copy(
            concentrationState = WearAppConcentration(
                status = WearAppConcentrationStatus.STALE,
                value = 123.0,
                unit = "pg/mL",
                calculatedAt = Instant.parse("2026-09-06T00:00:00Z")
            )
        )
        val now = Instant.parse("2026-09-06T00:30:00Z").toEpochMilli()
        assertEquals("E2 已过期", concentrationLabel(snapshot, now))
        assertEquals("--", currentE2ComplicationText(snapshot, now))
    }

    private fun snapshot(occurrences: List<WearAppUpcomingOccurrence>) = WearAppSnapshot(
        protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
        snapshotRevision = 4L,
        generatedAt = Instant.parse("2026-09-06T00:00:00Z"),
        zoneId = "Asia/Shanghai",
        overallStatus = WearAppOverallStatus.READY,
        recentDose = null,
        upcomingOccurrences = occurrences,
        concentrationState = io.github.yingqiu0871.evolune.experience.wear.WearAppConcentration.unavailable(),
        producerInstanceId = UUID(0L, 3L),
        producerGeneration = 1L,
        todaySummary = WearAppTodaySummary(
            todayLocalDate = today,
            computedAt = Instant.parse("2026-09-06T00:00:00Z"),
            completedCount = 0,
            totalCount = 2,
            state = WearAppTodaySummaryState.HAS_OCCURRENCES
        )
    )

    private fun authoritativeSummary(total: Int, completed: Int) = WearAppTodaySummary(
        todayLocalDate = today,
        computedAt = Instant.parse("2026-09-06T00:00:00Z"),
        completedCount = completed,
        totalCount = total,
        state = WearAppTodaySummaryState.HAS_OCCURRENCES
    )

    private fun occurrence(id: UUID, date: LocalDate) = WearAppUpcomingOccurrence(
        occurrenceId = id,
        planId = UUID(0L, 4L),
        slotId = UUID(0L, 5L),
        localDate = date,
        scheduledAt = Instant.parse("2026-09-06T04:00:00Z").plusSeconds((date.toEpochDay() - today.toEpochDay()) * 86400L),
        medicationName = "雌二醇",
        route = "口服",
        dose = 2.0,
        doseUnit = "mg",
        status = WearAppOccurrenceStatus.UPCOMING
    )

    private fun command(occurrenceId: UUID) = io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommand(
        protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
        commandType = io.github.yingqiu0871.evolune.experience.wear.WearAppCommandType.CONFIRM_OCCURRENCE,
        operationId = UUID(0L, 10L),
        createdAt = Instant.parse("2026-09-06T00:00:00Z"),
        sourceSnapshot = io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotIdentity(UUID(0L, 3L), 1L, 4L),
        occurrenceId = occurrenceId,
        planId = UUID(0L, 4L),
        slotId = UUID(0L, 5L),
        localDate = today,
        scheduledAt = Instant.parse("2026-09-06T04:00:00Z")
    )

    private fun result(type: WearAppConfirmResultType) = WearAppConfirmResult(
        protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
        operationId = UUID(0L, 10L),
        resultType = type,
        eventId = if (type == WearAppConfirmResultType.CONFIRMED) UUID(0L, 11L) else null,
        occurrenceId = todayOccurrence.occurrenceId,
        processedAt = Instant.parse("2026-09-06T00:01:00Z"),
        messageCode = null,
        snapshotRefreshExpected = false
    )
}
