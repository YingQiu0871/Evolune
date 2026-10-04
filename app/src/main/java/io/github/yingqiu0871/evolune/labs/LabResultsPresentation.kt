package io.github.yingqiu0871.evolune.labs

import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure formatting for the lab results list and editor (PK 2.0 slice 3). */
object LabResultsPresentation {
    data class Row(
        val result: LabResult,
        val valueText: String,
        val unitLabel: String,
        val measuredAtText: String,
        val isTestosterone: Boolean
    )

    /** Conventional lab-report spelling; not translated. */
    fun unitLabel(unit: LabUnit): String = when (unit) {
        LabUnit.PG_PER_ML -> "pg/mL"
        LabUnit.PMOL_PER_L -> "pmol/L"
        LabUnit.NG_PER_DL -> "ng/dL"
        LabUnit.NMOL_PER_L -> "nmol/L"
    }

    /** The value as entered: no exponent, no trailing zeros ("180.5", "200", "0.35"). */
    fun valueText(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    fun measuredAtText(
        instant: Instant,
        zone: ZoneId,
        is24Hour: Boolean,
        locale: Locale = Locale.getDefault()
    ): String {
        val pattern = if (is24Hour) "yyyy-MM-dd HH:mm" else "yyyy-MM-dd h:mm a"
        return DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(instant)
    }

    /** Rows newest first, whatever order the source list has. */
    fun rows(
        results: List<LabResult>,
        zone: ZoneId,
        is24Hour: Boolean,
        locale: Locale = Locale.getDefault()
    ): List<Row> = results
        .sortedWith(compareByDescending<LabResult> { it.measuredAt }.thenBy { it.id.toString() })
        .map { result ->
            Row(
                result = result,
                valueText = valueText(result.value),
                unitLabel = unitLabel(result.unit),
                measuredAtText = measuredAtText(result.measuredAt, zone, is24Hour, locale),
                isTestosterone = !result.unit.isEstradiol
            )
        }
}
