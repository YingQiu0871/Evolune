package io.github.yingqiu0871.evolune.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import io.github.yingqiu0871.evolune.data.lab.LabDatabase
import io.github.yingqiu0871.evolune.data.lab.LabResultEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomLabResultRepositoryTest {
    private lateinit var database: LabDatabase
    private lateinit var repository: RoomLabResultRepository

    @Before
    fun createDatabase() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LabDatabase::class.java).build()
        repository = RoomLabResultRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertPersistsEveryFieldAndIsIdempotent() = runBlocking {
        val lab = lab(1, millis = 1_700_000_000_123L, value = 512.5, unit = LabUnit.PMOL_PER_L)

        assertEquals(InsertResult.Inserted, repository.insert(lab))
        assertEquals(lab, repository.getById(lab.id))
        assertEquals(InsertResult.Idempotent, repository.insert(lab))
        assertEquals(InsertResult.Conflict, repository.insert(lab.copy(value = 100.0)))
        assertEquals(lab, repository.getById(lab.id))
    }

    @Test
    fun insertRejectsInvalidInputWithoutWriting() = runBlocking {
        assertEquals(InsertResult.Invalid, repository.insert(lab(1).copy(revision = 2)))
        assertEquals(InsertResult.Invalid, repository.insert(lab(2, value = 0.0)))
        assertEquals(InsertResult.Invalid, repository.insert(lab(3, value = Double.NaN)))
        assertEquals(
            InsertResult.Invalid,
            repository.insert(lab(4).copy(measuredAt = Instant.ofEpochSecond(1_700_000_000L, 1)))
        )
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun updateBumpsRevisionOnlyWhenExpectedRevisionMatches() = runBlocking {
        val lab = lab(1, value = 150.0)
        repository.insert(lab)

        assertEquals(UpdateResult.NoChange, repository.update(lab, expectedRevision = 1))
        assertEquals(UpdateResult.Updated, repository.update(lab.copy(value = 180.0), expectedRevision = 1))
        assertEquals(lab.copy(value = 180.0, revision = 2), repository.getById(lab.id))
        assertEquals(
            UpdateResult.RevisionConflict,
            repository.update(lab.copy(value = 200.0), expectedRevision = 1)
        )
        assertEquals(UpdateResult.NotFound, repository.update(lab(9), expectedRevision = 1))
        assertEquals(UpdateResult.Invalid, repository.update(lab, expectedRevision = 0))
    }

    @Test
    fun deleteRequiresTheStoredRevision() = runBlocking {
        val lab = lab(1)
        repository.insert(lab)

        assertEquals(ConditionalDeleteResult.RevisionConflict, repository.deleteIfRevisionMatches(lab.id, 2))
        assertEquals(ConditionalDeleteResult.Deleted, repository.deleteIfRevisionMatches(lab.id, 1))
        assertNull(repository.getById(lab.id))
        assertEquals(ConditionalDeleteResult.NotFound, repository.deleteIfRevisionMatches(lab.id, 1))
        assertEquals(ConditionalDeleteResult.Invalid, repository.deleteIfRevisionMatches(lab.id, 0))
    }

    @Test
    fun observeAllOrdersNewestFirstThenById() = runBlocking {
        val older = lab(3, millis = 1_000L)
        val newerB = lab(2, millis = 2_000L)
        val newerA = lab(1, millis = 2_000L)
        listOf(older, newerB, newerA).forEach { repository.insert(it) }

        assertEquals(listOf(newerA, newerB, older), repository.observeAll().first())
    }

    @Test
    fun corruptPersistedRowIsReportedNotRepaired() = runBlocking {
        database.labResultDao().insertIfAbsent(
            LabResultEntity(
                id = uuid(1).toString(),
                measuredAtEpochMillis = 1_000L,
                value = 100.0,
                unit = "UNKNOWN",
                revision = 1
            )
        )
        try {
            repository.getById(uuid(1))
            fail("Expected CorruptLabResultException")
        } catch (_: CorruptLabResultException) {
        }
    }

    private fun lab(
        n: Int,
        millis: Long = 1_700_000_000_000L,
        value: Double = 120.0,
        unit: LabUnit = LabUnit.PG_PER_ML
    ) = LabResult(
        id = uuid(n),
        measuredAt = Instant.ofEpochMilli(millis),
        value = value,
        unit = unit
    )

    private fun uuid(n: Int): UUID = UUID.fromString("00000000-0000-4000-8000-%012d".format(n))
}
