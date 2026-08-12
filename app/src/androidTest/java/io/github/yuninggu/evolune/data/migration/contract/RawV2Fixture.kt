package io.github.yuninggu.evolune.data.migration.contract

import androidx.sqlite.db.SupportSQLiteDatabase

data class RawV2Fixture(
    val events: List<V2EventRow> = emptyList(),
    val plans: List<V2PlanRow> = emptyList()
) {
    fun insertInto(database: SupportSQLiteDatabase) {
        database.beginTransaction()
        try {
            events.forEach { it.insertInto(database) }
            plans.forEach { it.insertInto(database) }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }
}

data class V2EventRow(
    val id: String = "10000000-0000-0000-0000-000000000001",
    val route: Any? = "ORAL",
    val timeH: Any? = 0.0,
    val doseMG: Any? = 1.0,
    val ester: Any? = "E2",
    val extras: Any? = "{}"
) {
    fun insertInto(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO dose_events(id, route, timeH, doseMG, ester, extras)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(id, route, timeH, doseMG, ester, extras)
        )
    }
}

data class V2PlanRow(
    val id: String = "20000000-0000-0000-0000-000000000001",
    val name: Any? = "Synthetic plan",
    val route: Any? = "ORAL",
    val ester: Any? = "E2",
    val doseMG: Any? = 1.0,
    val scheduleType: Any? = "DAILY",
    val timeOfDay: Any? = "[]",
    val daysOfWeek: Any? = "[]",
    val intervalDays: Any? = 1,
    val isEnabled: Any? = 1,
    val extras: Any? = "{}",
    val createdAt: Any? = 0L
) {
    fun insertInto(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO medication_plans(
                id, name, route, ester, doseMG, scheduleType, timeOfDay,
                daysOfWeek, intervalDays, isEnabled, extras, createdAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                id,
                name,
                route,
                ester,
                doseMG,
                scheduleType,
                timeOfDay,
                daysOfWeek,
                intervalDays,
                isEnabled,
                extras,
                createdAt
            )
        )
    }
}
