package io.github.yingqiu0871.evolune.ui.screens.insights

import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.insights.InsightsBindingConfidence
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityKey
import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsSummary
import io.github.yingqiu0871.evolune.history.insights.InsightsLoadFailure
import io.github.yingqiu0871.evolune.history.insights.InsightsPhase
import io.github.yingqiu0871.evolune.history.insights.InsightsRangeSelection
import io.github.yingqiu0871.evolune.history.insights.InsightsRangeValidationError
import io.github.yingqiu0871.evolune.history.insights.InsightsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

/**
 * v1.7-B-04 hardening: release-gate invariants that are cheaper to prove on the JVM than on a
 * device — presentation invariants, the full state matrix, picker date round-trips, localization
 * parity and a presentation cost sanity check.
 */
class InsightsHardeningTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 14)
    private val us: Locale = Locale.US

    private fun summary(
        recordedIntakes: Int = 6,
        recordedDays: Int = 4,
        matched: Int = 4,
        unrecorded: Int = 3,
        unmatched: Int = 2,
        high: Int = 3,
        medium: Int = 1,
        low: Int = 0,
        sources: Map<MedicationIntakeSource, Int> = mapOf(
            MedicationIntakeSource.MANUAL to 3,
            MedicationIntakeSource.REMINDER to 2,
            MedicationIntakeSource.LEGACY to 1
        ),
        doses: Map<MedicationIdentityKey, Double> = mapOf(
            MedicationIdentityKey.E2 to 12.5,
            MedicationIdentityKey.EV to 6.0
        ),
        unknownIdentity: Int = 1,
        timezoneDerived: Boolean = false,
        startDate: LocalDate = today.minusDays(29),
        endDate: LocalDate = today
    ): MedicationInsightsSummary = MedicationInsightsSummary(
        startDate = startDate,
        endDate = endDate,
        recordedIntakeCount = recordedIntakes,
        recordedDayCount = recordedDays,
        matchedOccurrenceCount = matched,
        unrecordedOccurrenceCount = unrecorded,
        unmatchedActualIntakeCount = unmatched,
        sourceCounts = MedicationIntakeSource.entries.associateWith { sources[it] ?: 0 },
        bindingConfidenceCounts = InsightsBindingConfidence.entries.associateWith {
            when (it) {
                InsightsBindingConfidence.HIGH -> high
                InsightsBindingConfidence.MEDIUM -> medium
                InsightsBindingConfidence.LOW -> low
                InsightsBindingConfidence.NONE -> unmatched
            }
        },
        perMedicationDoseTotalsMg = doses,
        unknownIdentityRecordedIntakeCount = unknownIdentity,
        containsCurrentTimezoneDerivedDates = timezoneDerived
    )

    private fun state(
        phase: InsightsPhase,
        summary: MedicationInsightsSummary? = null,
        selection: InsightsRangeSelection = InsightsRangeSelection.Last30Days,
        validationError: InsightsRangeValidationError? = null
    ): InsightsUiState = InsightsUiState(
        selection = selection,
        startDate = summary?.startDate ?: today.minusDays(29),
        endDate = summary?.endDate ?: today,
        today = today,
        displayZone = ZoneOffset.UTC,
        phase = phase,
        summary = summary,
        failure = if (phase == InsightsPhase.ERROR) {
            InsightsLoadFailure.ReadFailure(IllegalStateException("hardening"))
        } else {
            null
        },
        validationError = validationError
    )

    // ---------- presentation invariants (v1.7-B-04 §15) ----------

    @Test
    fun `source rows are exactly the six frozen sources`() {
        val rows = InsightsPresentation.present(state(InsightsPhase.CONTENT, summary()), us).sources
        assertEquals(MedicationIntakeSource.entries.size, rows.size)
        assertEquals(6, rows.size)
        assertEquals(MedicationIntakeSource.entries.toSet(), rows.map { it.source }.toSet())
    }

    @Test
    fun `confidence rows are exactly the four frozen buckets`() {
        val rows = InsightsPresentation.present(state(InsightsPhase.CONTENT, summary()), us).confidence
        assertEquals(4, rows.size)
        assertEquals(InsightsBindingConfidence.entries.toSet(), rows.map { it.confidence }.toSet())
        assertEquals(0, rows.first { it.confidence == InsightsBindingConfidence.LOW }.count)
    }

    @Test
    fun `dose rows only carry provable medication keys and never a combined total`() {
        val model = InsightsPresentation.present(state(InsightsPhase.CONTENT, summary()), us)
        assertTrue(model.doseRows.isNotEmpty())
        model.doseRows.forEach { row ->
            assertTrue(
                "only frozen identity keys may reach the dose section",
                MedicationIdentityKey.entries.contains(row.medicationKey)
            )
        }
        // the two rows are independent facts: no row equals the sum of the others
        assertEquals(2, model.doseRows.size)
        assertNotEquals(model.doseRows[0].totalMg, model.doseRows[0].totalMg + model.doseRows[1].totalMg)
    }

    @Test
    fun `unknown identity is a count outside the dose list`() {
        val model = InsightsPresentation.present(
            state(InsightsPhase.CONTENT, summary(doses = mapOf(MedicationIdentityKey.E2 to 1.0), unknownIdentity = 4)),
            us
        )
        assertEquals(1, model.doseRows.size)
        assertEquals(4, model.unknownIdentityCount)
        assertTrue("unknown identity must not appear as a dose row", model.doseRows.none { it.totalMg == 4.0 })
    }

    @Test
    fun `coverage always ships both disclosures together`() {
        listOf(true, false).forEach { derived ->
            val coverage = requireNotNull(
                InsightsPresentation.present(state(InsightsPhase.CONTENT, summary(timezoneDerived = derived)), us)
                    .coverage
            )
            assertEquals(2, coverage.disclosureRes.size)
            assertTrue(coverage.disclosureRes.contains(R.string.insights_coverage_disclosure_schedule))
            assertTrue(coverage.disclosureRes.contains(R.string.insights_coverage_disclosure_absence))
        }
    }

    @Test
    fun `the timezone disclosure appears only when the aggregate says so`() {
        assertNull(
            InsightsPresentation.present(state(InsightsPhase.CONTENT, summary(timezoneDerived = false)), us)
                .timezoneDisclosureRes
        )
        assertEquals(
            R.string.insights_timezone_disclosure,
            InsightsPresentation.present(state(InsightsPhase.CONTENT, summary(timezoneDerived = true)), us)
                .timezoneDisclosureRes
        )
    }

    // ---------- state matrix (v1.7-B-04 §16) ----------

    @Test
    fun `the render matrix is locked for every phase`() {
        // LOADING: no sections, no stale summary, no empty/error copy
        val loading = InsightsPresentation.present(state(InsightsPhase.LOADING), us)
        assertNull(loading.coverage)
        assertEquals(emptyList<InsightsPresentation.MetricCard>(), loading.overviewCards)
        assertNull(loading.emptyMessageRes)
        assertNull(loading.errorMessageRes)
        assertNull(loading.validationMessageRes)

        // CONTENT: sections present, no empty/error/validation copy
        val content = InsightsPresentation.present(state(InsightsPhase.CONTENT, summary()), us)
        assertTrue(content.hasSummary)
        assertEquals(2, content.overviewCards.size)
        assertNull(content.emptyMessageRes)
        assertNull(content.errorMessageRes)
        assertNull(content.validationMessageRes)

        // EMPTY: empty copy only, still no error/validation
        val empty = InsightsPresentation.present(
            state(
                InsightsPhase.EMPTY,
                summary(
                    recordedIntakes = 0,
                    recordedDays = 0,
                    matched = 0,
                    unrecorded = 0,
                    unmatched = 0,
                    high = 0,
                    medium = 0,
                    low = 0,
                    sources = emptyMap(),
                    doses = emptyMap(),
                    unknownIdentity = 0
                )
            ),
            us
        )
        assertEquals(R.string.insights_empty, empty.emptyMessageRes)
        assertNull(empty.errorMessageRes)
        assertNull(empty.validationMessageRes)

        // ERROR: error copy only, and no stale sections
        val error = InsightsPresentation.present(state(InsightsPhase.ERROR), us)
        assertEquals(R.string.insights_error, error.errorMessageRes)
        assertNull(error.coverage)
        assertNull(error.emptyMessageRes)
        assertNull(error.validationMessageRes)

        // INVALID_RANGE: typed validation copy only
        val invalid = InsightsPresentation.present(
            state(
                InsightsPhase.INVALID_RANGE,
                validationError = InsightsRangeValidationError.START_AFTER_END,
                selection = InsightsRangeSelection.Custom(today, today.minusDays(1))
            ),
            us
        )
        assertEquals(R.string.insights_invalid_start_after_end, invalid.validationMessageRes)
        assertNull(invalid.emptyMessageRes)
        assertNull(invalid.errorMessageRes)
        assertNull(invalid.coverage)
    }

    // ---------- picker date round-trip (v1.7-B-04 §7) ----------

    @Test
    fun `picker millis round-trip every season and never consult the system zone`() {
        val originalZone = TimeZone.getDefault()
        try {
            // a non-UTC device zone must not shift the selected date
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
            listOf(
                LocalDate.of(2026, 1, 15),  // winter CET (+01:00)
                LocalDate.of(2026, 7, 15),  // summer CEST (+02:00)
                LocalDate.of(2026, 3, 29),  // spring DST transition day
                LocalDate.of(2026, 10, 25), // autumn DST transition day
                LocalDate.of(2026, 12, 31), // year boundary
                LocalDate.of(2026, 1, 1)    // year boundary
            ).forEach { date ->
                val millis = InsightsDatePicker.toUtcMillis(date)
                assertEquals("round-trip must not shift $date", date, InsightsDatePicker.toLocalDate(millis))
            }
            // and the millis are UTC midnight, not local midnight
            assertEquals(
                LocalDate.of(2026, 7, 15).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
                InsightsDatePicker.toUtcMillis(LocalDate.of(2026, 7, 15))
            )
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun `only a custom selection carries picker endpoints`() {
        assertNull(InsightsDatePicker.fromSelection(InsightsRangeSelection.Last7Days).first)
        assertNull(InsightsDatePicker.fromSelection(InsightsRangeSelection.CurrentMonth).second)
        val (start, end) = InsightsDatePicker.fromSelection(
            InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 14))
        )
        assertEquals(LocalDate.of(2026, 9, 1), InsightsDatePicker.toLocalDate(requireNotNull(start)))
        assertEquals(LocalDate.of(2026, 9, 14), InsightsDatePicker.toLocalDate(requireNotNull(end)))
    }

    // ---------- localization parity (v1.7-B-04 §11) ----------

    @Test
    fun `both shipped locales expose the same Insights keys with compatible format arguments`() {
        val defaultXml = Files.readString(Path.of("src/main/res/values/strings.xml"))
        val zhXml = Files.readString(Path.of("src/main/res/values-zh-rCN/strings.xml"))

        val defaultKeys = insightsKeys(defaultXml)
        val zhKeys = insightsKeys(zhXml)
        assertEquals("the two locale files must expose the same Insights keys", defaultKeys, zhKeys)
        assertTrue(defaultKeys.size >= 40)

        defaultKeys.forEach { key ->
            val defaultValue = insightsValue(defaultXml, key)
            val zhValue = insightsValue(zhXml, key)
            assertEquals(
                "format placeholders must match for $key",
                placeholders(defaultValue),
                placeholders(zhValue)
            )
        }
    }

    @Test
    fun `count row placeholders stay string-plus-integer and value rows stay string-plus-string`() {
        val defaultXml = Files.readString(Path.of("src/main/res/values/strings.xml"))
        assertEquals(listOf("%1\$s", "%2\$d"), placeholders(insightsValue(defaultXml, "insights_count_row_description")))
        assertEquals(listOf("%1\$s", "%2\$s"), placeholders(insightsValue(defaultXml, "insights_value_row_description")))
    }

    // ---------- performance sanity (v1.7-B-04 §28) ----------

    @Test
    fun `a ninety day range with several hundred intakes stays linear and cheap to present`() {
        val start = today.minusDays(89)
        val sources = mapOf(
            MedicationIntakeSource.MANUAL to 200,
            MedicationIntakeSource.REMINDER to 150,
            MedicationIntakeSource.WEAR to 100,
            MedicationIntakeSource.WIDGET to 40,
            MedicationIntakeSource.JSON_V1 to 10,
            MedicationIntakeSource.LEGACY to 0
        )
        val big = summary(
            recordedIntakes = 500,
            recordedDays = 90,
            matched = 300,
            unrecorded = 120,
            unmatched = 200,
            high = 200,
            medium = 80,
            low = 20,
            sources = sources,
            doses = MedicationIdentityKey.entries.associateWith { 100.0 },
            unknownIdentity = 12,
            startDate = start,
            endDate = today
        )

        val started = System.nanoTime()
        val model = InsightsPresentation.present(state(InsightsPhase.CONTENT, big), us)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        // the mapper must stay a fixed-size projection of the aggregate, not a per-entry pass
        assertEquals(6, model.sources.size)
        assertEquals(4, model.confidence.size)
        assertEquals(MedicationIdentityKey.entries.size, model.doseRows.size)
        assertTrue("presentation of a 90-day aggregate took ${elapsedMs}ms", elapsedMs < 250)
    }

    private fun insightsKeys(xml: String): Set<String> =
        Regex("<string name=\"(insights_[^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toSet()

    private fun insightsValue(xml: String, key: String): String =
        Regex("<string name=\"$key\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)
            ?.groupValues
            ?.get(1)
            ?: error("missing $key")

    private fun placeholders(value: String): List<String> =
        Regex("%\\d\\\$[sd]").findAll(value).map { it.value }.toList().sorted()
}
