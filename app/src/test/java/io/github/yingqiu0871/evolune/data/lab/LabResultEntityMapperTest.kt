package io.github.yingqiu0871.evolune.data.lab

import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class LabResultEntityMapperTest {

    private val id = UUID.fromString("aaaaaaaa-2222-4333-8444-bbbbbbbbbbbb")
    private val lab = LabResult(id, Instant.ofEpochMilli(1_700_000_000_123L), 367.1, LabUnit.PMOL_PER_L, revision = 3)
    private val row = LabResultEntity(id.toString(), 1_700_000_000_123L, 367.1, "PMOL_PER_L", 3)

    @Test
    fun `round trip is lossless`() {
        assertEquals(LabMappingResult.Success(row), lab.toEntity())
        assertEquals(LabMappingResult.Success(lab), row.toDomain())
    }

    @Test
    fun `persisted unit codes are frozen`() {
        assertEquals(
            listOf("PG_PER_ML", "PMOL_PER_L", "NG_PER_DL", "NMOL_PER_L"),
            LabUnit.entries.map { it.code }
        )
        assertEquals(listOf("pg/ml", "pmol/l", "ng/dl", "nmol/l"), LabUnit.entries.map { it.mahiroCode })
        assertEquals(listOf(true, true, false, false), LabUnit.entries.map { it.isEstradiol })
    }

    @Test
    fun `domain values that cannot be stored exactly are rejected`() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            assertEquals(
                LabMappingResult.Failure(LabMappingError.InvalidValue(value)),
                lab.copy(value = value).toEntity()
            )
        }
        val subMillisecond = Instant.ofEpochSecond(1_700_000_000L, 1)
        assertEquals(
            LabMappingResult.Failure(LabMappingError.InvalidMeasuredAt(subMillisecond)),
            lab.copy(measuredAt = subMillisecond).toEntity()
        )
        assertTrue(lab.copy(measuredAt = Instant.MAX).toEntity() is LabMappingResult.Failure)
    }

    @Test
    fun `invalid persisted rows are reported`() {
        assertEquals(
            LabMappingResult.Failure(LabMappingError.InvalidId("not-a-uuid")),
            row.copy(id = "not-a-uuid").toDomain()
        )
        assertEquals(
            LabMappingResult.Failure(LabMappingError.InvalidId(id.toString().uppercase())),
            row.copy(id = id.toString().uppercase()).toDomain()
        )
        assertEquals(
            LabMappingResult.Failure(LabMappingError.InvalidUnit("pg/ml")),
            row.copy(unit = "pg/ml").toDomain()
        )
        assertEquals(
            LabMappingResult.Failure(LabMappingError.InvalidValue(0.0)),
            row.copy(value = 0.0).toDomain()
        )
        assertEquals(
            LabMappingResult.Failure(LabMappingError.InvalidRevision(0)),
            row.copy(revision = 0).toDomain()
        )
    }
}
