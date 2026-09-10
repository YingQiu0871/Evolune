package io.github.yingqiu0871.evolune.wear

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.tiles.TileService
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentration
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOverallStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppTodaySummary
import io.github.yingqiu0871.evolune.experience.wear.WearAppTodaySummaryState
import io.github.yingqiu0871.evolune.experience.wear.WearAppUpcomingOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Seeds read-only sample data solely for visual checks on an isolated Wear emulator. */
@RunWith(AndroidJUnit4::class)
class WearTileEmulatorPreviewTest {
    @Test
    fun seedReadySnapshotAndRefreshGalleryTiles() {
        assumeTrue(
            "This visual fixture must never replace data on a physical watch",
            Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.contains("emulator") ||
                Build.PRODUCT.contains("sdk") ||
                Build.HARDWARE.contains("ranchu")
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WearAppearanceStore.write(context, WearColorScheme.SAKURA)
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val planId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val current = WearAppStore.getSnapshot(context)
        val snapshot = WearAppSnapshot(
            protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
            snapshotRevision = (current?.snapshotRevision ?: 99L) + 1L,
            generatedAt = now,
            zoneId = zone.id,
            overallStatus = WearAppOverallStatus.READY,
            recentDose = null,
            upcomingOccurrences = listOf(
                occurrence(1, planId, today, now.plusSeconds(3_600), "示例药物 A", 12.5),
                occurrence(2, planId, today, now.plusSeconds(14_400), "示例药物 B", 2.0),
                occurrence(3, planId, today, now.plusSeconds(28_800), "示例药物 C", 200.0)
            ),
            concentrationState = WearAppConcentration(
                status = WearAppConcentrationStatus.AVAILABLE,
                value = 153.2,
                unit = "pg/mL",
                calculatedAt = now
            ),
            producerInstanceId = current?.producerInstanceId
                ?: UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
            producerGeneration = current?.producerGeneration ?: 100L,
            todaySummary = WearAppTodaySummary(
                todayLocalDate = today,
                computedAt = now,
                completedCount = 1,
                totalCount = 4,
                state = WearAppTodaySummaryState.HAS_OCCURRENCES
            )
        )
        assertEquals(
            WearAppSnapshotApplyResult.Applied,
            WearAppStore.acceptSnapshot(context, WearAppSnapshotCodec.encode(snapshot), now.toEpochMilli())
        )
        val dashboard = WearDashboard(
            plans = listOf(
                WearPlan(planId.toString(), "示例药物 A", 12.5),
                WearPlan("33333333-3333-3333-3333-333333333333", "示例药物 B", 2.0)
            ),
            currentConcentration = 153.2,
            curveValues = listOf(
                42f, 58f, 81f, 116f, 149f, 171f, 153f, 132f, 110f, 91f, 74f, 61f, 50f
            ),
            updatedAt = now.toEpochMilli()
        )
        WearPlanStore.saveDashboard(
            context,
            """[{"id":"${dashboard.plans[0].id}","name":"示例药物 A","doseMG":12.5},{"id":"${dashboard.plans[1].id}","name":"示例药物 B","doseMG":2.0}]""",
            dashboard,
            now.toEpochMilli()
        )
        // Keep the visual fixture in READY while the Tile is first requested; the emulator
        // has no paired phone to answer a fresh legacy dashboard request.
        WearPlanStore.markPlansRequested(context, now.toEpochMilli())
        assertEquals(2, WearPlanStore.getDashboard(context).plans.size)
        TileService.getUpdater(context).apply {
            requestUpdate(DoseTileService::class.java)
            requestUpdate(NextDoseTileService::class.java)
            requestUpdate(TodayPlanTileService::class.java)
            requestUpdate(CurrentE2TileService::class.java)
        }
        Thread.sleep(750L)
    }

    private fun occurrence(
        index: Int,
        planId: UUID,
        today: java.time.LocalDate,
        at: Instant,
        name: String,
        dose: Double
    ) = WearAppUpcomingOccurrence(
        occurrenceId = UUID(0L, index.toLong()),
        planId = planId,
        slotId = UUID(1L, index.toLong()),
        localDate = today,
        scheduledAt = at,
        medicationName = name,
        route = "口服",
        dose = dose,
        doseUnit = "mg",
        status = WearAppOccurrenceStatus.UPCOMING,
        notificationId = 1000 + index
    )
}
