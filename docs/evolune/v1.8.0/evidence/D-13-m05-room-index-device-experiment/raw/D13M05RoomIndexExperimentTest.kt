package io.github.yingqiu0871.evolune.d13

import android.content.Context
import android.database.Cursor
import android.util.Log
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.data.AppDatabase
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

/**
 * Temporary D-13 evidence harness. Deleted before the evidence-only commit.
 *
 * Disposable file-backed experimental database only (instrumentation cache dir).
 * No production entity/DAO/schema/migration change; candidate indexes are created
 * with execSQL inside this disposable database. Every scenario is persisted to the
 * instrumentation output file immediately after completion; a bounded watchdog
 * marks an interrupted scenario INVALID before halting so partial output is never
 * presented as valid.
 */
@RunWith(AndroidJUnit4::class)
class D13M05RoomIndexExperimentTest {

    private val tag = "D13M05"
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    // Exact production DAO SQL (verbatim from DoseEventDao at HEAD a326326).
    private val sqlLocalDateRange =
        "SELECT * FROM dose_events WHERE localDate IS NOT NULL AND localDate >= ? AND localDate <= ? " +
            "ORDER BY localDate ASC, occurredAtEpochMillis ASC, id ASC"
    private val sqlOccurredAtRange =
        "SELECT * FROM dose_events WHERE occurredAtEpochMillis >= ? AND occurredAtEpochMillis < ? " +
            "ORDER BY occurredAtEpochMillis ASC, id ASC"
    private val sqlRecent20 =
        "SELECT * FROM dose_events ORDER BY occurredAtEpochMillis DESC, id ASC LIMIT 20"
    private val sqlAllHistory =
        "SELECT * FROM dose_events WHERE occurredAtEpochMillis <= ? ORDER BY occurredAtEpochMillis ASC, id ASC"
    private val sqlObserveAll =
        "SELECT * FROM dose_events ORDER BY occurredAtEpochMillis DESC, id ASC"
    private val sqlRecentTimeH =
        "SELECT * FROM dose_events ORDER BY timeH DESC LIMIT 20"
    private val sqlTimeHRange =
        "SELECT * FROM dose_events WHERE timeH >= ? AND timeH <= ? ORDER BY timeH ASC"

    private var sharedDb: AppDatabase? = null
    private var dbFile: File? = null
    private val perCardBaselineDigests = mutableMapOf<Int, Map<String, String>>()

    private fun resultsFile(): File {
        val dir = File(context.getExternalFilesDir(null), "d13m05")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "d13-results.log")
    }

    private fun internalResultsFile(): File {
        val dir = File(context.filesDir, "d13m05")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "d13-results.log")
    }

    private fun persist(line: String) {
        try {
            resultsFile().appendText(line + "\n")
        } catch (_: Throwable) {
        }
        try {
            internalResultsFile().appendText(line + "\n")
        } catch (_: Throwable) {
        }
    }

    private fun openDb(): SupportSQLiteDatabase {
        sharedDb?.let { return it.openHelper.writableDatabase }
        val dir = File(context.cacheDir, "d13m05")
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "d13-probe.db")
        if (f.exists()) f.delete()
        dbFile = f
        val db = Room.databaseBuilder(context, AppDatabase::class.java, f.absolutePath)
            .allowMainThreadQueries()
            .build()
        sharedDb = db
        return db.openHelper.writableDatabase
    }

    private fun closeDb() {
        try {
            sharedDb?.close()
        } finally {
            sharedDb = null
            dbFile?.delete()
            dbFile = null
        }
    }

    private fun query(sql: String, vararg args: Any): List<List<String>> {
        val db = openDb()
        val out = mutableListOf<List<String>>()
        val cursor: Cursor = db.query(sql, args.map { it.toString() }.toTypedArray())
        cursor.use { c ->
            while (c.moveToNext()) {
                val row = mutableListOf<String>()
                for (i in 0 until c.columnCount) row.add(c.getString(i) ?: "NULL")
                out.add(row)
            }
        }
        return out
    }

    private fun explain(sql: String, vararg args: Any): String =
        query("EXPLAIN QUERY PLAN $sql", *args)
            .joinToString(" || ") { row -> row.joinToString("; ") }

    /** Deterministic fixture: 5% null localDate (legacy rows), 2 events/day, stable ids. */
    private fun fixtureRows(count: Int, seedBase: Long): List<Array<Any?>> {
        val rows = mutableListOf<Array<Any?>>()
        var i = 0
        while (rows.size < count) {
            val dayIndex = i / 2
            val hour = if (i % 2 == 0) 8 else 20
            val year = 2025 + dayIndex / 360
            val month = ((dayIndex % 360) / 30) + 1
            val day = (dayIndex % 30) + 1
            val localDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
            val millis = dayIndex * 86_400_000L + hour * 3_600_000L
            val nullLocalDate = i % 20 == 19
            rows.add(
                arrayOf(
                    UUID(0L, 100_000L + seedBase * 1_000_000L + i),
                    "ORAL", millis / 3_600_000.0, 2.0, "E2", "{}", millis,
                    if (nullLocalDate) null else "UTC",
                    if (nullLocalDate) null else localDate,
                    null,
                    if (nullLocalDate) "LEGACY" else "MANUAL",
                    "RECORDED", 1L
                )
            )
            i += 1
        }
        return rows
    }

    private fun insertAll(rows: List<Array<Any?>>): Long {
        val db = openDb()
        val t0 = System.nanoTime()
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                "INSERT INTO dose_events (id, route, timeH, doseMG, ester, extras, occurredAtEpochMillis, " +
                    "zoneId, localDate, slotId, source, status, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )
            for (row in rows) {
                stmt.bindString(1, row[0].toString())
                stmt.bindString(2, row[1] as String)
                stmt.bindDouble(3, row[2] as Double)
                stmt.bindDouble(4, row[3] as Double)
                stmt.bindString(5, row[4] as String)
                stmt.bindString(6, row[5] as String)
                stmt.bindLong(7, row[6] as Long)
                (row[7] as String?)?.let { stmt.bindString(8, it) } ?: stmt.bindNull(8)
                (row[8] as String?)?.let { stmt.bindString(9, it) } ?: stmt.bindNull(9)
                (row[9] as String?)?.let { stmt.bindString(10, it) } ?: stmt.bindNull(10)
                stmt.bindString(11, row[10] as String)
                stmt.bindString(12, row[11] as String)
                stmt.bindLong(13, row[12] as Long)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return (System.nanoTime() - t0) / 1_000_000L
    }

    private fun seed(count: Int, seedBase: Long) {
        val db = openDb()
        db.execSQL("DELETE FROM dose_events")
        insertAll(fixtureRows(count, seedBase))
    }

    private fun dropExperimentalIndexes() {
        val db = openDb()
        db.execSQL("DROP INDEX IF EXISTS `idx_d13_localdate`")
        db.execSQL("DROP INDEX IF EXISTS `idx_d13_occurred`")
        db.execSQL("DROP INDEX IF EXISTS `idx_d13_occurred_desc`")
    }

    private fun createIndexes(set: String): Pair<Double, Double> {
        val db = openDb()
        var localMs = -1.0
        var occMs = -1.0
        val t0 = System.nanoTime()
        if (set == "A" || set == "C" || set == "C3") {
            db.execSQL("CREATE INDEX `idx_d13_localdate` ON `dose_events` (`localDate`, `occurredAtEpochMillis`, `id`)")
            localMs = (System.nanoTime() - t0) / 1_000_000.0
        }
        if (set == "B" || set == "C" || set == "C3") {
            val t1 = System.nanoTime()
            db.execSQL("CREATE INDEX `idx_d13_occurred` ON `dose_events` (`occurredAtEpochMillis`, `id`)")
            occMs = (System.nanoTime() - t1) / 1_000_000.0
        }
        if (set == "C3") {
            db.execSQL("CREATE INDEX `idx_d13_occurred_desc` ON `dose_events` (`occurredAtEpochMillis` DESC, `id` ASC)")
        }
        return Pair(localMs, occMs)
    }

    private fun indexNames(): String =
        query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='dose_events'")
            .joinToString("|") { it[0] }

    private fun fileSizeBytes(): Long = dbFile?.length() ?: -1L

    // ------------------------------------------------------------------ scenarios

    @Test
    fun runExperiment() {
        val db = openDb()
        val journalMode = query("PRAGMA journal_mode")[0][0]
        val sqliteVersion = query("SELECT sqlite_version()")[0][0]
        val env = "D13_ENV sqlite=$sqliteVersion room=2.8.4 journal=$journalMode " +
            "model=${android.os.Build.MODEL} api=${android.os.Build.VERSION.SDK_INT} " +
            "abi=${android.os.Build.SUPPORTED_ABIS.firstOrNull()} build=debug device=Pixel_7(AVD)"
        Log.i(tag, env)
        persist(env)

        // Baseline semantic references (unindexed schema at card 1000).
        seed(1000, 1L)
        dropExperimentalIndexes()
        val semRefs = mapOf(
            "localDateRange" to digest(query(sqlLocalDateRange, "2025-01-05", "2025-01-25")),
            "occurredAtRange" to digest(query(sqlOccurredAtRange, 4L * 86_400_000L, 20L * 86_400_000L)),
            "recent20" to digest(query(sqlRecent20)),
            "allHistory" to digest(query(sqlAllHistory, 15L * 86_400_000L))
        )
        persist("D13_SEMREF $semRefs")
        Log.i(tag, "D13_SEMREF $semRefs")

        for (count in listOf(100, 1000, 10000)) {
            val label = if (count == 10000) "STRESS" else "NORMAL"
            persist("D13_CARD card=$count,label=$label")
            Log.i(tag, "D13_CARD card=$count label=$label")
            for (set in listOf("BASELINE", "A", "B", "C", "C3")) {
                val ok = runReadSet(count, set, semRefs)
                Log.i(tag, "D13_READSET-DONE card=$count set=$set ok=$ok")
            }
        }

        for (count in listOf(100, 1000, 10000)) {
            runIndexBuild(count)
        }

        runWriteCost()

        Log.i(tag, "D13_PHASE complete")
        persist("D13_PHASE complete")
        closeDb()
    }

    private fun runReadSet(count: Int, set: String, semRefs: Map<String, String>): Boolean {
        val timer = startWatchdog { persist("D13_READSET card=$count,set=$set,valid=false,reason=timeout") }
        try {
            seed(count, 1L)
            dropExperimentalIndexes()
            val build = if (set == "BASELINE") Pair(-1.0, -1.0) else createIndexes(set)
            persist("D13_INDEXBUILD card=$count,set=$set,localDateMs=${fmt(build.first)},occurredMs=${fmt(build.second)},indexes=${indexNames()}")

            val plans = mapOf(
                "localDateRange" to explain(sqlLocalDateRange, "2025-01-05", "2025-01-25"),
                "occurredAtRange" to explain(sqlOccurredAtRange, 4L * 86_400_000L, 20L * 86_400_000L),
                "recent20" to explain(sqlRecent20),
                "allHistory" to explain(sqlAllHistory, 15L * 86_400_000L),
                "observeAll" to explain(sqlObserveAll),
                "recentTimeH" to explain(sqlRecentTimeH),
                "timeHRange" to explain(sqlTimeHRange, 4100.0, 4200.0)
            )
            for ((name, plan) in plans) {
                persist("D13_PLAN card=$count,set=$set,query=$name,plan=$plan")
            }

            // localDate channel: three selectivities + empty.
            measure(count, set, "localDateRange", "narrow", sqlLocalDateRange, "2025-01-05", "2025-01-06")
            measure(count, set, "localDateRange", "medium", sqlLocalDateRange, "2025-01-05", "2025-01-20")
            measure(count, set, "localDateRange", "wide", sqlLocalDateRange, "2025-01-05", "2025-03-05")
            measure(count, set, "localDateRange", "empty", sqlLocalDateRange, "2099-01-01", "2099-01-02")
            // occurredAt channel: narrow/medium/wide + recent20 + allHistory.
            measure(count, set, "occurredAtRange", "narrow", sqlOccurredAtRange, 4L * 86_400_000L, 6L * 86_400_000L)
            measure(count, set, "occurredAtRange", "medium", sqlOccurredAtRange, 4L * 86_400_000L, 20L * 86_400_000L)
            measure(count, set, "occurredAtRange", "wide", sqlOccurredAtRange, 4L * 86_400_000L, 65L * 86_400_000L)
            measure(count, set, "recent20", "all", sqlRecent20)
            measure(count, set, "allHistory", "mid", sqlAllHistory, 15L * 86_400_000L)

            // Semantic equality: BASELINE digests become the per-cardinality reference;
            // candidate sets must reproduce them exactly at the SAME cardinality.
            val current = mapOf(
                "localDateRange" to digest(query(sqlLocalDateRange, "2025-01-05", "2025-01-25")),
                "occurredAtRange" to digest(query(sqlOccurredAtRange, 4L * 86_400_000L, 20L * 86_400_000L)),
                "recent20" to digest(query(sqlRecent20)),
                "allHistory" to digest(query(sqlAllHistory, 15L * 86_400_000L))
            )
            if (set == "BASELINE") {
                perCardBaselineDigests[count] = current
                persist("D13_SEM card=$count,set=$set,equal=true,reference=stored")
            } else {
                val reference = perCardBaselineDigests[count] ?: emptyMap()
                val mismatch = current.filter { reference[it.key] != it.value }.keys
                persist("D13_SEM card=$count,set=$set,equal=${mismatch.isEmpty()},mismatch=${mismatch.joinToString("|")}")
            }
            return true
        } catch (t: Throwable) {
            persist("D13_READSET card=$count,set=$set,valid=false,reason=${t::class.simpleName}:${t.message}")
            return false
        } finally {
            timer.cancel()
        }
    }

    private fun measure(
        count: Int,
        set: String,
        queryName: String,
        selectivity: String,
        sql: String,
        vararg args: Any
    ) {
        repeat(5) { query(sql, *args) }
        val samples = mutableListOf<Double>()
        var rows = -1L
        repeat(30) {
            val t0 = System.nanoTime()
            val result = query(sql, *args)
            samples.add((System.nanoTime() - t0) / 1_000_000.0)
            rows = result.size.toLong()
        }
        val sorted = samples.sorted()
        val line = "D13_ROW card=$count,set=$set,query=$queryName,sel=$selectivity,rows=$rows,n=30," +
            "min=${fmt(sorted.first())},max=${fmt(sorted.last())},mean=${fmt(samples.average())}," +
            "median=${fmt(median(sorted))},p95=${fmt(p95(sorted))}"
        persist(line)
    }

    private fun runIndexBuild(count: Int) {
        val timer = startWatchdog { persist("D13_INDEXBUILD card=$count,valid=false,reason=timeout") }
        try {
            seed(count, 2L)
            dropExperimentalIndexes()
            val db = openDb()
            val t0 = System.nanoTime()
            db.execSQL("CREATE INDEX `idx_d13_localdate` ON `dose_events` (`localDate`, `occurredAtEpochMillis`, `id`)")
            val t1 = System.nanoTime()
            db.execSQL("CREATE INDEX `idx_d13_occurred` ON `dose_events` (`occurredAtEpochMillis`, `id`)")
            val t2 = System.nanoTime()
            persist(
                "D13_INDEXBUILD card=$count,localDateMs=${fmt((t1 - t0) / 1_000_000.0)}," +
                    "occurredMs=${fmt((t2 - t1) / 1_000_000.0)}"
            )
            dropExperimentalIndexes()
        } catch (t: Throwable) {
            persist("D13_INDEXBUILD card=$count,valid=false,reason=${t::class.simpleName}:${t.message}")
        } finally {
            timer.cancel()
        }
    }

    private fun runWriteCost() {
        val timer = startWatchdog { persist("D13_WRITE valid=false,reason=timeout") }
        try {
            for (count in listOf(100, 1000, 10000)) {
                for (set in listOf("BASELINE", "C")) {
                    seed(0, 3L)
                    dropExperimentalIndexes()
                    if (set != "BASELINE") createIndexes("C")

                    // Bulk insert (one transaction)
                    val bulkMs = insertAll(fixtureRows(count, 4L))
                    persist("D13_WRITE card=$count,set=$set,op=bulkInsertN,ms=$bulkMs")

                    // 30 single inserts
                    val db = openDb()
                    val single = mutableListOf<Double>()
                    repeat(30) { r ->
                        val s0 = System.nanoTime()
                        db.execSQL(
                            "INSERT OR IGNORE INTO dose_events (id, route, timeH, doseMG, ester, extras, " +
                                "occurredAtEpochMillis, zoneId, localDate, slotId, source, status, revision) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf<Any?>(
                                UUID(0L, 555_000L + r).toString(), "ORAL", 5000.0 + r, 2.0, "E2", "{}",
                                5000L * 86_400_000L + r, "UTC", "2082-09-01", null, "MANUAL", "RECORDED", 1L
                            )
                        )
                        single.add((System.nanoTime() - s0) / 1_000_000.0)
                    }
                    persist(
                        "D13_WRITE card=$count,set=$set,op=singleInsertN30,median=${fmt(median(single.sorted()))}," +
                            "p95=${fmt(p95(single.sorted()))}"
                    )

                    // 30 single deletes
                    val del = mutableListOf<Double>()
                    repeat(30) { r ->
                        val d0 = System.nanoTime()
                        db.execSQL("DELETE FROM dose_events WHERE id = ?", arrayOf(UUID(0L, 555_000L + r).toString()))
                        del.add((System.nanoTime() - d0) / 1_000_000.0)
                    }
                    persist(
                        "D13_WRITE card=$count,set=$set,op=singleDeleteN30,median=${fmt(median(del.sorted()))}," +
                            "p95=${fmt(p95(del.sorted()))}"
                    )

                    // Restore-like replace: DELETE all + INSERT all in one transaction
                    db.beginTransaction()
                    val r0 = System.nanoTime()
                    try {
                        db.execSQL("DELETE FROM dose_events")
                        insertAllNoTx(fixtureRows(count, 5L))
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                    val restoreMs = (System.nanoTime() - r0) / 1_000_000.0
                    persist("D13_WRITE card=$count,set=$set,op=restoreReplace,ms=$restoreMs")

                    persist("D13_WRITE card=$count,set=$set,op=fileSizeBytes,bytes=${fileSizeBytes()}")
                    dropExperimentalIndexes()
                }
            }
        } catch (t: Throwable) {
            persist("D13_WRITE valid=false,reason=${t::class.simpleName}:${t.message}")
        } finally {
            timer.cancel()
        }
    }

    private fun insertAllNoTx(rows: List<Array<Any?>>) {
        val db = openDb()
        val stmt = db.compileStatement(
            "INSERT INTO dose_events (id, route, timeH, doseMG, ester, extras, occurredAtEpochMillis, " +
                "zoneId, localDate, slotId, source, status, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        for (row in rows) {
            stmt.bindString(1, row[0].toString())
            stmt.bindString(2, row[1] as String)
            stmt.bindDouble(3, row[2] as Double)
            stmt.bindDouble(4, row[3] as Double)
            stmt.bindString(5, row[4] as String)
            stmt.bindString(6, row[5] as String)
            stmt.bindLong(7, row[6] as Long)
            (row[7] as String?)?.let { stmt.bindString(8, it) } ?: stmt.bindNull(8)
            (row[8] as String?)?.let { stmt.bindString(9, it) } ?: stmt.bindNull(9)
            (row[9] as String?)?.let { stmt.bindString(10, it) } ?: stmt.bindNull(10)
            stmt.bindString(11, row[10] as String)
            stmt.bindString(12, row[11] as String)
            stmt.bindLong(13, row[12] as Long)
            stmt.executeInsert()
        }
    }

    // ------------------------------------------------------------------ utils

    private fun startWatchdog(action: () -> Unit): Timer {
        val timer = Timer("d13-watchdog", true)
        timer.schedule(object : TimerTask() {
            override fun run() {
                try {
                    action()
                } catch (_: Throwable) {
                }
                Runtime.getRuntime().halt(97)
            }
        }, 240_000L)
        return timer
    }

    private fun median(sorted: List<Double>): Double =
        if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0

    private fun p95(sorted: List<Double>): Double {
        val rank = Math.ceil(0.95 * sorted.size).toInt()
        return sorted[(rank - 1).coerceIn(0, sorted.size - 1)]
    }

    private fun fmt(v: Double): String = String.format(Locale.ROOT, "%.4f", v)

    private fun digest(rows: List<List<String>>): String {
        val joined = rows.joinToString("|") { row -> row.joinToString(",") }
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(joined.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

