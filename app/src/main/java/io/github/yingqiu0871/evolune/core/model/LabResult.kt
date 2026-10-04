package io.github.yingqiu0871.evolune.core.model

import java.time.Instant
import java.util.UUID

/**
 * A measured serum hormone value entered by the user (PK 2.0, docs/evolune/pk2/PK2_CALIBRATION_PLAN.md).
 *
 * Testosterone units are stored so imported data is not lost, but only estradiol units take part
 * in E2 calibration.
 */
data class LabResult(
    val id: UUID,
    val measuredAt: Instant,
    val value: Double,
    val unit: LabUnit,
    val revision: Long = 1
) {
    init {
        require(revision >= 1) { "revision must be at least 1" }
    }
}

/**
 * Lab units. [code] is the persisted value and must never be renamed; [mahiroCode] is the
 * interoperable Mahiro JSON spelling.
 */
enum class LabUnit(val code: String, val mahiroCode: String, val isEstradiol: Boolean) {
    PG_PER_ML("PG_PER_ML", "pg/ml", isEstradiol = true),
    PMOL_PER_L("PMOL_PER_L", "pmol/l", isEstradiol = true),
    NG_PER_DL("NG_PER_DL", "ng/dl", isEstradiol = false),
    NMOL_PER_L("NMOL_PER_L", "nmol/l", isEstradiol = false);

    companion object {
        fun fromCode(code: String): LabUnit? = entries.firstOrNull { it.code == code }
    }
}
