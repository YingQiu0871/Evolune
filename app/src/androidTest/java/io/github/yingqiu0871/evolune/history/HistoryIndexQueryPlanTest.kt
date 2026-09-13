package io.github.yingqiu0871.evolune.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.data.AppDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A-04 §9: read-only evaluation of the `dose_events.localDate` index question.
 *
 * This test does **not** change the schema. It creates a throw-away Room database with the
 * production schema, seeds a v1.7-scale row count, and records the raw `EXPLAIN QUERY PLAN`
 * output for both History read channels so the index decision can be reviewed from evidence.
 */
@RunWith(AndroidJUnit4::class)
class HistoryIndexQueryPlanTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "a04-query-plan.db"
    private val seedRows = 20_000
    private val warmUpRuns = 5
    private val measuredRuns = 20

    private val WARM_UP_RUNS = warmUpRuns
    private val MEASURED_RUNS = measuredRuns

    /** Generous ceiling: a once-per-month read must not need an index to stay off the UI budget. */
    private val ACCEPTABLE_MEDIAN_MS = 50.0

    @Test
    fun historyReadChannelsAreExplainedOnARealisticDataset() {
        context.deleteDatabase(databaseName)
        val database = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .build()

        try {
            val raw = database.openHelper.writableDatabase
            raw.beginTransaction()
            try {
                // Columns per app/schemas/3.json (all NOT NULL columns are supplied).
                val statement = raw.compileStatement(
                    """
                    INSERT INTO dose_events
                        (id, route, timeH, doseMG, ester, extras, occurredAtEpochMillis, zoneId,
                         localDate, slotId, source, status, revision)
                    VALUES (?, 'ORAL', 0.0, 2.0, 'E2', '{}', ?, 'UTC', ?, NULL, 'MANUAL', 'RECORDED', 1)
                    """.trimIndent()
                )
                val start = LocalDate.of(2023, 1, 1)
                repeat(seedRows) { index ->
                    val date = start.plusDays((index % 900).toLong())
                    statement.clearBindings()
                    statement.bindString(1, "00000000-0000-0000-0000-%012d".format(index))
                    statement.bindLong(2, date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                    statement.bindString(3, date.toString())
                    statement.executeInsert()
                }
                raw.setTransactionSuccessful()
            } finally {
                raw.endTransaction()
            }

            // Query texts mirror `DoseEventDao` exactly (Channel A = persistedDate, Channel B = instant).
            val channelA = explain(
                database,
                "SELECT * FROM dose_events " +
                    "WHERE localDate IS NOT NULL AND localDate >= ? AND localDate <= ? " +
                    "ORDER BY localDate ASC, occurredAtEpochMillis ASC, id ASC",
                arrayOf("2025-01-01", "2025-01-31")
            )
            val channelB = explain(
                database,
                "SELECT * FROM dose_events " +
                    "WHERE occurredAtEpochMillis >= ? AND occurredAtEpochMillis < ? " +
                    "ORDER BY occurredAtEpochMillis ASC, id ASC",
                arrayOf("1735689600000", "1738368000000")
            )
            val rowCount = countRows(database)

            val channelASql =
                "SELECT * FROM dose_events WHERE localDate IS NOT NULL AND localDate >= ? " +
                    "AND localDate <= ? ORDER BY localDate ASC, occurredAtEpochMillis ASC, id ASC"
            val channelBSql =
                "SELECT * FROM dose_events WHERE occurredAtEpochMillis >= ? " +
                    "AND occurredAtEpochMillis < ? ORDER BY occurredAtEpochMillis ASC, id ASC"
            val channelATiming = measureQuery(database, channelASql, arrayOf("2025-01-01", "2025-01-31"))
            val channelBTiming = measureQuery(database, channelBSql, arrayOf("1735689600000", "1738368000000"))

            val report = buildString {
                appendLine("# A-04 dose_events index evaluation — raw EXPLAIN QUERY PLAN + timing")
                appendLine("# database: throw-away copy with the production schema (v3)")
                appendLine("# seeded dose_events rows: $rowCount")
                appendLine("# index inventory from app/schemas/.../3.json: dose_events = [] (none)")
                appendLine("# timing: one History month load issues Channel A once and Channel B once")
                appendLine()
                appendLine("## Channel A (persisted localDate, History completeness channel)")
                appendLine(channelA)
                appendLine("## Channel B (occurredAt instant window)")
                appendLine(channelB)
                appendLine()
                appendLine("## measured query cost (20 runs after 5 warm-up runs, rows fully consumed)")
                appendLine("Channel A: median=${channelATiming.medianMs} ms  max=${channelATiming.maxMs} ms")
                appendLine("Channel B: median=${channelBTiming.medianMs} ms  max=${channelBTiming.maxMs} ms")
            }

            val directory = File(requireNotNull(context.getExternalFilesDir(null)), "a-04").apply { mkdirs() }
            File(directory, "explain-query-plan.txt").writeText(report)

            assertTrue("Channel A plan must mention dose_events", channelA.contains("dose_events"))
            assertTrue("Channel B plan must mention dose_events", channelB.contains("dose_events"))
            assertTrue("the plan report must be captured for evidence", report.contains("seeded dose_events rows"))
            // The deferred-index decision rests on this measurement: a full scan of a v1.7-scale
            // table must stay cheap for a once-per-month read. A failure here would mean the index
            // question has to be re-opened (and would then require a schema decision).
            assertTrue(
                "Channel A median ${channelATiming.medianMs} ms must stay well below a UI budget",
                channelATiming.medianMs < ACCEPTABLE_MEDIAN_MS
            )
            assertTrue(
                "Channel B median ${channelBTiming.medianMs} ms must stay well below a UI budget",
                channelBTiming.medianMs < ACCEPTABLE_MEDIAN_MS
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private data class Timing(val medianMs: Double, val maxMs: Double)

    /** Fully consumes each run so the measurement includes cursor decoding, not just planning. */
    private fun measureQuery(database: AppDatabase, sql: String, args: Array<String>): Timing {
        fun runOnce(): Long {
            val started = System.nanoTime()
            database.openHelper.readableDatabase.query(sql, args).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getColumnIndex("id").takeIf { it >= 0 }?.let { cursor.getString(it) }
                }
            }
            return System.nanoTime() - started
        }
        repeat(WARM_UP_RUNS) { runOnce() }
        val samples = List(MEASURED_RUNS) { runOnce() / 1_000_000.0 }.sorted()
        return Timing(medianMs = samples[samples.size / 2], maxMs = samples.last())
    }

    private fun explain(
        database: AppDatabase,
        sql: String,
        args: Array<String>
    ): String {
        val rows = mutableListOf<String>()
        database.openHelper.readableDatabase
            .query("EXPLAIN QUERY PLAN $sql", args)
            .use { cursor ->
                while (cursor.moveToNext()) {
                    val detail = cursor.getColumnIndex("detail").takeIf { it >= 0 }
                        ?.let { cursor.getString(it) }
                        ?: cursor.getString(cursor.columnCount - 1)
                    rows += detail
                }
            }
        return rows.joinToString("\n")
    }



    private fun countRows(database: AppDatabase): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM dose_events")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
}
