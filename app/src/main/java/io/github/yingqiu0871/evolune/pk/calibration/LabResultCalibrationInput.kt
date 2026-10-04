package io.github.yingqiu0871.evolune.pk.calibration

import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit

private const val MILLIS_PER_HOUR = 3_600_000.0

/**
 * Projects a stored lab result onto the calibration input, on the PK clock (absolute hours since
 * 1970). Testosterone units return null: they never take part in E2 calibration.
 */
fun LabResult.toE2LabResultOrNull(): E2LabResult? {
    val e2Unit = when (unit) {
        LabUnit.PG_PER_ML -> E2LabUnit.PG_PER_ML
        LabUnit.PMOL_PER_L -> E2LabUnit.PMOL_PER_L
        LabUnit.NG_PER_DL, LabUnit.NMOL_PER_L -> return null
    }
    return E2LabResult(
        id = id.toString(),
        timeH = measuredAt.toEpochMilli() / MILLIS_PER_HOUR,
        value = value,
        unit = e2Unit
    )
}
