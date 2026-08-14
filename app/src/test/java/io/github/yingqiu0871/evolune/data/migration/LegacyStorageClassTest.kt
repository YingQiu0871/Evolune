package io.github.yingqiu0871.evolune.data.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyStorageClassTest {
    @Test
    fun integerStorageClassIsAllowed() {
        assertEquals(
            LegacySqliteStorageClass.INTEGER,
            success(LegacySqliteStorageClass.INTEGER)
        )
    }

    @Test
    fun floatStorageClassIsAllowed() {
        assertEquals(
            LegacySqliteStorageClass.FLOAT,
            success(LegacySqliteStorageClass.FLOAT)
        )
    }

    @Test
    fun nullStorageClassFails() {
        assertInvalid(LegacySqliteStorageClass.NULL)
    }

    @Test
    fun stringStorageClassFails() {
        assertInvalid(LegacySqliteStorageClass.STRING)
    }

    @Test
    fun blobStorageClassFails() {
        assertInvalid(LegacySqliteStorageClass.BLOB)
    }

    private fun success(storageClass: LegacySqliteStorageClass): LegacySqliteStorageClass {
        val result = validateLegacyTimeHStorageClass(storageClass)
        assertTrue(result is LegacyMigrationResult.Success)
        return (result as LegacyMigrationResult.Success).value
    }

    private fun assertInvalid(storageClass: LegacySqliteStorageClass) {
        val result = validateLegacyTimeHStorageClass(storageClass)
        assertTrue(result is LegacyMigrationResult.Failure)
        assertEquals(
            LegacyMigrationError.InvalidTimeHStorageClass(storageClass),
            (result as LegacyMigrationResult.Failure).error
        )
    }
}
