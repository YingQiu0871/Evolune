package io.github.yingqiu0871.evolune.labs

import io.github.yingqiu0871.evolune.core.model.LabUnit
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** The editor's raw input; [id]/[revision] are null for a new result. */
data class LabResultDraft(
    val id: UUID? = null,
    val revision: Long? = null,
    val measuredAt: Instant,
    val valueText: String,
    val unit: LabUnit
)

enum class LabResultDraftIssue {
    VALUE_MISSING,
    VALUE_NOT_A_NUMBER,
    VALUE_NOT_POSITIVE,
    VALUE_TOO_LARGE,
    MEASURED_IN_FUTURE
}

sealed interface LabResultDraftValidation {
    data class Valid(val measuredAt: Instant, val value: Double, val unit: LabUnit) :
        LabResultDraftValidation

    data class Invalid(val issue: LabResultDraftIssue) : LabResultDraftValidation
}

/**
 * Editor validation. Accepts "," as the decimal separator, truncates the time to whole
 * milliseconds (the storage precision), and allows a small clock skew into the future.
 */
object LabResultDraftValidator {
    /** Far above any plausible E2 or T value in the supported units; catches typos. */
    const val MAX_VALUE: Double = 100_000.0
    private val FUTURE_TOLERANCE: Duration = Duration.ofMinutes(5)

    fun validate(draft: LabResultDraft, now: Instant): LabResultDraftValidation {
        val text = draft.valueText.trim().replace(',', '.')
        if (text.isEmpty()) return LabResultDraftValidation.Invalid(LabResultDraftIssue.VALUE_MISSING)
        val value = text.toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: return LabResultDraftValidation.Invalid(LabResultDraftIssue.VALUE_NOT_A_NUMBER)
        if (value <= 0.0) return LabResultDraftValidation.Invalid(LabResultDraftIssue.VALUE_NOT_POSITIVE)
        if (value > MAX_VALUE) return LabResultDraftValidation.Invalid(LabResultDraftIssue.VALUE_TOO_LARGE)
        val measuredAt = Instant.ofEpochMilli(draft.measuredAt.toEpochMilli())
        if (measuredAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
            return LabResultDraftValidation.Invalid(LabResultDraftIssue.MEASURED_IN_FUTURE)
        }
        return LabResultDraftValidation.Valid(measuredAt, value, draft.unit)
    }
}
