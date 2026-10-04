package io.github.yingqiu0871.evolune.labs

import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

class LabResultsTest {
    @Test
    fun `validator accepts comma decimals and truncates to whole milliseconds`() {
        val draft = draft("  180,5 ").copy(measuredAt = Instant.parse("2026-10-01T08:00:00.123456Z"))

        assertEquals(
            LabResultDraftValidation.Valid(
                Instant.parse("2026-10-01T08:00:00.123Z"),
                180.5,
                LabUnit.PG_PER_ML
            ),
            LabResultDraftValidator.validate(draft, NOW)
        )
    }

    @Test
    fun `validator names the first problem with the input`() {
        val cases = mapOf(
            "" to LabResultDraftIssue.VALUE_MISSING,
            "   " to LabResultDraftIssue.VALUE_MISSING,
            "abc" to LabResultDraftIssue.VALUE_NOT_A_NUMBER,
            "1.2.3" to LabResultDraftIssue.VALUE_NOT_A_NUMBER,
            "NaN" to LabResultDraftIssue.VALUE_NOT_A_NUMBER,
            "Infinity" to LabResultDraftIssue.VALUE_NOT_A_NUMBER,
            "0" to LabResultDraftIssue.VALUE_NOT_POSITIVE,
            "-5" to LabResultDraftIssue.VALUE_NOT_POSITIVE,
            "100001" to LabResultDraftIssue.VALUE_TOO_LARGE
        )
        cases.forEach { (text, issue) ->
            assertEquals(
                text,
                LabResultDraftValidation.Invalid(issue),
                LabResultDraftValidator.validate(draft(text), NOW)
            )
        }
    }

    @Test
    fun `validator allows five minutes of clock skew but not a future measurement`() {
        val skewed = draft("100").copy(measuredAt = NOW.plusSeconds(300))
        val future = draft("100").copy(measuredAt = NOW.plusSeconds(301))

        assertEquals(
            LabResultDraftValidation.Valid(NOW.plusSeconds(300), 100.0, LabUnit.PG_PER_ML),
            LabResultDraftValidator.validate(skewed, NOW)
        )
        assertEquals(
            LabResultDraftValidation.Invalid(LabResultDraftIssue.MEASURED_IN_FUTURE),
            LabResultDraftValidator.validate(future, NOW)
        )
    }

    @Test
    fun `presentation lists newest first with plain values and lab-report units`() {
        val older = LabResult(ID_A, Instant.parse("2026-09-01T06:05:00Z"), 367.10, LabUnit.PMOL_PER_L)
        val newer = LabResult(ID_B, Instant.parse("2026-09-02T14:30:00Z"), 2.0E2, LabUnit.NG_PER_DL)

        val rows = LabResultsPresentation.rows(listOf(older, newer), UTC, is24Hour = true, Locale.US)

        assertEquals(listOf(ID_B, ID_A), rows.map { it.result.id })
        assertEquals(listOf("200", "367.1"), rows.map { it.valueText })
        assertEquals(listOf("ng/dL", "pmol/L"), rows.map { it.unitLabel })
        assertEquals(listOf(true, false), rows.map { it.isTestosterone })
        assertEquals("2026-09-02 14:30", rows.first().measuredAtText)
        assertEquals(
            "2026-09-02 2:30 PM",
            LabResultsPresentation.measuredAtText(newer.measuredAt, UTC, is24Hour = false, Locale.US)
        )
        assertEquals("0.0001", LabResultsPresentation.valueText(1.0E-4))
    }

    private fun draft(valueText: String) = LabResultDraft(
        measuredAt = NOW.minusSeconds(3600),
        valueText = valueText,
        unit = LabUnit.PG_PER_ML
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-04T12:00:00Z")
        val UTC: ZoneId = ZoneId.of("UTC")
        val ID_A: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
        val ID_B: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000b2")
    }
}
