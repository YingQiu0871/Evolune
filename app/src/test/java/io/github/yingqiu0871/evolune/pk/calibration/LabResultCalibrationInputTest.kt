package io.github.yingqiu0871.evolune.pk.calibration

import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.UUID

class LabResultCalibrationInputTest {

    private val id = UUID.fromString("11111111-2222-4333-8444-555555555555")

    private fun lab(unit: LabUnit) = LabResult(id, Instant.ofEpochMilli(5_400_000L), 367.1, unit)

    @Test
    fun `estradiol units map onto the PK clock in hours`() {
        val pmol = lab(LabUnit.PMOL_PER_L).toE2LabResultOrNull()!!
        assertEquals(id.toString(), pmol.id)
        assertEquals(1.5, pmol.timeH, 0.0)
        assertEquals(E2LabUnit.PMOL_PER_L, pmol.unit)
        assertEquals(100.0, pmol.valuePgMl, 1e-9)
        assertEquals(E2LabUnit.PG_PER_ML, lab(LabUnit.PG_PER_ML).toE2LabResultOrNull()!!.unit)
    }

    @Test
    fun `testosterone units are never calibration input`() {
        assertNull(lab(LabUnit.NG_PER_DL).toE2LabResultOrNull())
        assertNull(lab(LabUnit.NMOL_PER_L).toE2LabResultOrNull())
    }
}
