package io.github.yuninggu.evolune.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PersistenceInstantMapperTest {
    @Test
    fun ordinaryInstantMapsToExactEpochMillis() {
        val instant = Instant.parse("2024-02-03T04:05:06.789Z")
        val result = instantToEpochMillisForPersistence(instant)

        assertEquals(1_706_933_106_789L, success(result))
    }

    @Test
    fun minimumInstantReturnsExplicitFailure() {
        assertEquals(
            MappingError.InvalidCreatedAt(CreatedAtInput.InstantValue(Instant.MIN)),
            failure(instantToEpochMillisForPersistence(Instant.MIN))
        )
    }

    @Test
    fun maximumInstantReturnsExplicitFailure() {
        assertEquals(
            MappingError.InvalidCreatedAt(CreatedAtInput.InstantValue(Instant.MAX)),
            failure(instantToEpochMillisForPersistence(Instant.MAX))
        )
    }

    private fun success(result: MappingResult<Long>): Long {
        assertTrue(result is MappingResult.Success)
        return (result as MappingResult.Success).value
    }

    private fun failure(result: MappingResult<*>): MappingError {
        assertTrue(result is MappingResult.Failure)
        return (result as MappingResult.Failure).error
    }
}
