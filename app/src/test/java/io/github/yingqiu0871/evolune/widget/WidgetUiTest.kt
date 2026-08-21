package io.github.yingqiu0871.evolune.widget

import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.MedicationTimelineWindow
import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

class WidgetUiTest {
    private val now = Instant.parse("2027-01-15T08:30:00Z")
    private val appearance = WidgetAppearanceConfig.Default

    @Test
    fun `Samsung calibrated 2x2 is complete and 2x1 is the compact fallback`() {
        val twoByOne = WidgetSizePolicy.resolve(WidgetSize(150, 97))
        val twoByTwo = WidgetSizePolicy.resolve(WidgetSize(150, 213))

        assertEquals(WidgetSizeTier.NARROW_SHORT, twoByOne.tier)
        assertEquals(1, twoByOne.rowCapacity)
        assertEquals(WidgetSizeTier.NARROW_STANDARD, twoByTwo.tier)
        assertEquals(3, twoByTwo.rowCapacity)
    }

    @Test
    fun `width changes density without removing three row completeness`() {
        val narrow = WidgetSizePolicy.resolve(WidgetSize(150, 213))
        val wide = WidgetSizePolicy.resolve(WidgetSize(235, 213))
        val expanded = WidgetSizePolicy.resolve(WidgetSize(321, 213))

        assertEquals(WidgetSizeTier.NARROW_STANDARD, narrow.tier)
        assertEquals(WidgetSizeTier.WIDE_STANDARD, wide.tier)
        assertEquals(WidgetSizeTier.EXPANDED, expanded.tier)
        assertEquals(listOf(3, 3, 3), listOf(narrow, wide, expanded).map { it.rowCapacity })
    }

    @Test
    fun `height primarily controls row capacity`() {
        assertEquals(1, WidgetSizePolicy.resolve(WidgetSize(150, 120)).rowCapacity)
        assertEquals(3, WidgetSizePolicy.resolve(WidgetSize(150, 213)).rowCapacity)
        assertEquals(5, WidgetSizePolicy.resolve(WidgetSize(150, 275)).rowCapacity)
        assertEquals(5, WidgetSizePolicy.resolve(WidgetSize(321, 330)).rowCapacity)
    }

    @Test
    fun `2x2 maps all three daily occurrences with complete row metadata`() {
        val plans = listOf(
            plan(UUID(0L, 1L), "First", LocalTime.of(8, 30)),
            plan(UUID(0L, 2L), "Second", LocalTime.of(9, 0)),
            plan(UUID(0L, 3L), "Third", LocalTime.of(21, 0))
        )
        val presentation = WidgetPresentationMapper().map(
            enabledPlans = plans,
            doseEvents = emptyList(),
            now = now,
            zoneId = ZoneOffset.UTC
        )
        val model = WidgetUiMapper.map(
            WidgetRenderState.Loaded(WidgetSnapshot(presentation, 120.0)),
            WidgetSizePolicy.resolve(WidgetSize(150, 213)),
            appearance
        )

        assertEquals(WidgetContentState.TIMELINE, model.contentState)
        assertEquals(3, model.dailyProgress.total)
        assertEquals(listOf("First", "Second", "Third"), model.rows.map { it.planName })
        assertTrue(model.rows.all { it.routeKey == "ORAL" })
        assertEquals(listOf(2.0, 2.0, 2.0), model.rows.map { it.doseMg })
        assertEquals(List(3) { WidgetProgressSegment.EMPTY }, model.progressSegments)
        assertTrue(model.rows.all { it.action == WidgetRowAction.RECORD })
        assertTrue(
            model.rows.all {
                it.statusPresentation == WidgetRowStatusPresentation.SCHEDULED_TIME
            }
        )
    }

    @Test
    fun `one plans visible occurrence count drives three two and one row density`() {
        val id = UUID(0L, 4L)
        val three = modelFor(
            listOf(
                plan(
                    id,
                    "One",
                    LocalTime.of(9, 0),
                    LocalTime.of(17, 0),
                    LocalTime.of(22, 0)
                )
            )
        )
        val two = modelFor(
            listOf(plan(id, "One", LocalTime.of(9, 0), LocalTime.of(22, 0)))
        )
        val one = modelFor(listOf(plan(id, "One", LocalTime.of(22, 0))))

        assertEquals(3, three.rows.size)
        assertEquals(1, three.rows.map { it.planId }.distinct().size)
        assertEquals(3, three.rows.map { it.slotId }.distinct().size)
        assertEquals(3, three.rows.map { it.occurrenceId }.distinct().size)
        assertEquals(WidgetDailyProgress(0, 3), three.dailyProgress)
        assertEquals(List(3) { WidgetProgressSegment.EMPTY }, three.progressSegments)
        assertEquals(WidgetRowDensity.COMPACT, three.rowLayout.density)
        assertEquals(WidgetRowDistribution.CENTERED, three.rowLayout.distribution)

        assertEquals(2, two.rows.size)
        assertEquals(WidgetRowDensity.MEDIUM, two.rowLayout.density)
        assertEquals(WidgetRowDistribution.BALANCED, two.rowLayout.distribution)

        assertEquals(1, one.rows.size)
        assertEquals(WidgetRowDensity.EXPANDED, one.rowLayout.density)
        assertEquals(WidgetRowDistribution.CENTERED, one.rowLayout.distribution)
    }

    @Test
    fun `mixed plans are counted by total visible occurrences`() {
        val first = plan(
            UUID(0L, 5L),
            "First",
            LocalTime.of(9, 0),
            LocalTime.of(17, 0)
        )
        val second = plan(UUID(0L, 6L), "Second", LocalTime.of(22, 0))
        val model = modelFor(listOf(first, second))

        assertEquals(2, model.rows.map { it.planId }.distinct().size)
        assertEquals(3, model.rows.size)
        assertEquals(WidgetDailyProgress(0, 3), model.dailyProgress)
        assertEquals(WidgetRowDensity.COMPACT, model.rowLayout.density)
    }

    @Test
    fun `2x1 keeps summary and one meaningful row`() {
        val presentation = WidgetPresentationMapper().map(
            enabledPlans = listOf(
                plan(UUID(0L, 1L), "First", LocalTime.of(8, 30)),
                plan(UUID(0L, 2L), "Second", LocalTime.of(9, 0))
            ),
            doseEvents = emptyList(),
            now = now,
            zoneId = ZoneOffset.UTC
        )
        val model = WidgetUiMapper.map(
            WidgetRenderState.Loaded(WidgetSnapshot(presentation, null)),
            WidgetSizePolicy.resolve(WidgetSize(150, 97)),
            appearance
        )

        assertEquals(2, model.dailyProgress.total)
        assertEquals(1, model.rows.size)
        assertEquals(WidgetRowDensity.COMPACT, model.rowLayout.density)
    }

    @Test
    fun `rows beyond viewport capacity remain in model for collection scrolling`() {
        val plans = (1L..6L).map { index ->
            plan(
                UUID(0L, index),
                "Plan $index",
                LocalTime.of(6 + index.toInt(), 0)
            )
        }
        val presentation = WidgetPresentationMapper().map(
            enabledPlans = plans,
            doseEvents = emptyList(),
            now = now,
            zoneId = ZoneOffset.UTC
        )
        val model = WidgetUiMapper.map(
            WidgetRenderState.Loaded(WidgetSnapshot(presentation, null)),
            WidgetSizePolicy.resolve(WidgetSize(300, 260)),
            appearance
        )

        assertEquals(5, model.layout.rowCapacity)
        assertEquals(6, model.rows.size)
        assertEquals(plans.map { it.slots.single().localTime }, model.rows.map {
            it.scheduledLocalDateTime.toLocalTime()
        })
    }

    @Test
    fun `past today remains a scheduled-time check action with empty progress`() {
        val pastPlan = plan(UUID(0L, 10L), "Past", LocalTime.of(6, 0))
        val model = modelFor(listOf(pastPlan))
        val row = model.rows.single()

        assertEquals(MedicationOccurrenceStatus.PAST_UNRECORDED, row.status)
        assertEquals(WidgetRowAction.RECORD, row.action)
        assertEquals(R.drawable.ic_widget_check, row.action.iconRes())
        assertNotEquals(R.drawable.ic_widget_add, row.action.iconRes())
        assertEquals(WidgetRowStatusPresentation.SCHEDULED_TIME, row.statusPresentation)
        assertEquals(listOf(WidgetProgressSegment.EMPTY), model.progressSegments)
        assertEquals(WidgetDailyProgress(0, 1), model.dailyProgress)
    }

    @Test
    fun `recorded completion alone fills progress and increments header count`() {
        val past = plan(UUID(0L, 11L), "Past", LocalTime.of(6, 0))
        val current = plan(UUID(0L, 12L), "Current", LocalTime.of(8, 30))
        val future = plan(UUID(0L, 13L), "Future", LocalTime.of(21, 0))
        val recorded = DoseEvent(
            id = UUID(9L, 11L),
            route = Route.ORAL,
            occurredAt = now,
            localDate = now.atZone(ZoneOffset.UTC).toLocalDate(),
            doseMG = past.doseMG,
            ester = past.ester,
            slotId = past.slots.single().id,
            source = DoseEventSource.WIDGET
        )
        val model = modelFor(listOf(past, current, future), listOf(recorded))

        assertEquals(WidgetDailyProgress(1, 3), model.dailyProgress)
        assertEquals(
            listOf(
                WidgetProgressSegment.FILLED,
                WidgetProgressSegment.EMPTY,
                WidgetProgressSegment.EMPTY
            ),
            model.progressSegments
        )
        assertEquals(WidgetRowAction.COMPLETED, model.rows.first().action)
        assertEquals(
            WidgetRowStatusPresentation.COMPLETED,
            model.rows.first().statusPresentation
        )
        assertTrue(model.rows.drop(1).all { it.action == WidgetRowAction.RECORD })
    }

    @Test
    fun `one two and three rows use bounded adaptive density and distribution`() {
        val layout = WidgetSizePolicy.resolve(WidgetSize(150, 213))
        val one = WidgetRowDensityPolicy.resolve(layout, 1)
        val two = WidgetRowDensityPolicy.resolve(layout, 2)
        val three = WidgetRowDensityPolicy.resolve(layout, 3)

        assertEquals(WidgetRowDensity.EXPANDED, one.density)
        assertEquals(WidgetRowDistribution.CENTERED, one.distribution)
        assertEquals(WidgetRowDensity.MEDIUM, two.density)
        assertEquals(WidgetRowDistribution.BALANCED, two.distribution)
        assertEquals(WidgetRowDensity.COMPACT, three.density)
        assertEquals(WidgetRowDistribution.CENTERED, three.distribution)
        assertEquals(WidgetRowSpacingPlan(1, 0, 1), three.distribution.spacingPlan(3))
        assertEquals(WidgetRowSpacingPlan(0, 1, 0), two.distribution.spacingPlan(2))
        assertEquals(WidgetRowSpacingPlan(1, 0, 1), one.distribution.spacingPlan(1))
        assertTrue(one.rowHeightDp > two.rowHeightDp)
        assertTrue(two.rowHeightDp > three.rowHeightDp)
        assertTrue(one.titleTextSp <= 16)
        assertTrue(one.rowHeightDp <= 72)
        assertTrue(three.actionContainerSizeDp in 30..34)
        assertTrue(two.actionContainerSizeDp in 30..34)
        assertTrue(one.actionContainerSizeDp in 30..34)
        assertTrue(three.actionTouchTargetDp in 40..44)
        assertTrue(two.actionTouchTargetDp in 40..44)
        assertTrue(one.actionTouchTargetDp in 40..44)
        assertTrue(three.actionTouchTargetDp <= three.rowHeightDp)
    }

    @Test
    fun `2x1 never adopts expanded single-row density`() {
        val compact = WidgetRowDensityPolicy.resolve(
            WidgetSizePolicy.resolve(WidgetSize(150, 97)),
            1
        )

        assertEquals(WidgetRowDensity.COMPACT, compact.density)
        assertEquals(WidgetRowDistribution.STACKED, compact.distribution)
        assertEquals(WidgetRowSpacingPlan(0, 0, 0), compact.distribution.spacingPlan(1))
    }

    @Test
    fun `truthful no plan no upcoming loading and failure states remain distinct`() {
        val layout = WidgetSizePolicy.resolve(WidgetSize(150, 213))
        val window = MedicationTimelineWindow(emptyList(), emptyList(), emptyList())
        val noPlans = WidgetUiMapper.map(
            WidgetRenderState.Loaded(WidgetSnapshot(WidgetPresentationState.NoEnabledPlans, null)),
            layout,
            appearance
        )
        val noUpcoming = WidgetUiMapper.map(
            WidgetRenderState.Loaded(
                WidgetSnapshot(
                    WidgetPresentationState.NoUpcomingOccurrence(
                        visiblePlans = listOf(WidgetPlanPresentation(UUID(0L, 3L), "Enabled", 2.0)),
                        window = window,
                        todayItems = emptyList(),
                        dailyProgress = WidgetDailyProgress.Empty,
                        nextMeaningfulBoundary = null
                    ),
                    null
                )
            ),
            layout,
            appearance
        )
        val loading = WidgetUiMapper.map(WidgetRenderState.Loading, layout, appearance)
        val failure = WidgetUiMapper.map(WidgetRenderState.ReadFailure(), layout, appearance)

        assertEquals(WidgetContentState.NO_ENABLED_PLANS, noPlans.contentState)
        assertEquals(WidgetContentState.NO_UPCOMING_OCCURRENCE, noUpcoming.contentState)
        assertEquals(WidgetContentState.LOADING, loading.contentState)
        assertEquals(WidgetContentState.READ_FAILURE, failure.contentState)
        assertTrue(failure.rows.isEmpty())
    }

    private fun plan(id: UUID, name: String, vararg times: LocalTime) =
        syntheticPlan(id = id, slots = times.toList()).copy(
            name = name,
            slots = times.mapIndexed { index, time ->
                ScheduledDoseSlot(
                    id = UUID(id.mostSignificantBits + 10L, id.leastSignificantBits + index),
                    planId = id,
                    localTime = time,
                    position = index
                )
            }
        )

    private fun modelFor(
        plans: List<io.github.yingqiu0871.evolune.core.model.MedicationPlan>,
        events: List<DoseEvent> = emptyList()
    ): WidgetUiModel {
        val presentation = WidgetPresentationMapper().map(
            enabledPlans = plans,
            doseEvents = events,
            now = now,
            zoneId = ZoneOffset.UTC
        )
        return WidgetUiMapper.map(
            WidgetRenderState.Loaded(WidgetSnapshot(presentation, null)),
            WidgetSizePolicy.resolve(WidgetSize(150, 213)),
            appearance
        )
    }
}
