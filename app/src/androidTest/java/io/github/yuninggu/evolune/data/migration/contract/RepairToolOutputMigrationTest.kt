package io.github.yuninggu.evolune.data.migration.contract

import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuninggu.evolune.data.AppDatabase
import io.github.yuninggu.evolune.data.migration.MIGRATION_2_3
import io.github.yuninggu.evolune.data.repository.RoomDoseEventRepository
import io.github.yuninggu.evolune.data.repository.RoomMedicationPlanRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.time.LocalTime
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPInputStream

@RunWith(AndroidJUnit4::class)
class RepairToolOutputMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun repairedV2CopyMigratesAndProductionRepositoriesReadIt() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val encodedDatabase = arguments.getString(REPAIRED_DATABASE_ARGUMENT)
        val expectedSha256 = arguments.getString(REPAIRED_DATABASE_SHA_ARGUMENT)
        assumeNotNull(encodedDatabase, expectedSha256)
        val destination = context.getDatabasePath(DATABASE_NAME)
        destination.parentFile?.mkdirs()
        val compressed = Base64.decode(requireNotNull(encodedDatabase), Base64.DEFAULT)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            destination.outputStream().use(input::copyTo)
        }
        assertEquals(expectedSha256, destination.sha256())

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(3, sqlite.version)
            sqlite.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
            sqlite.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals(0, cursor.count)
            }

            val event = RoomDoseEventRepository(database).getById(EVENT_ID)
            assertNotNull(event)
            assertEquals(3_600_000L, event?.occurredAt?.toEpochMilli())

            val plan = RoomMedicationPlanRepository(database).getById(PLAN_ID)
            assertNotNull(plan)
            assertEquals(listOf(LocalTime.of(8, 30), LocalTime.of(20, 30)), plan?.slots?.map { it.localTime })
            assertEquals(listOf(0, 1), plan?.slots?.map { it.position })
        } finally {
            database.close()
        }
    }

    private fun java.io.File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val DATABASE_NAME = "phase1-batch8d-repaired-copy"
        const val REPAIRED_DATABASE_ARGUMENT = "repairFixtureGzipBase64"
        const val REPAIRED_DATABASE_SHA_ARGUMENT = "repairFixtureSha256"
        val EVENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000008001")
        val PLAN_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000008002")
    }
}
