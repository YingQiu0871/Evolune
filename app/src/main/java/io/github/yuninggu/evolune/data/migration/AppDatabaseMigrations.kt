package io.github.yuninggu.evolune.data.migration

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        applyV3Schema(db)
        val events = preflightEvents(db)
        val plans = preflightPlans(db)
        val slots = plans.flatMap { it.slots }
        backfillEvents(db, events)
        insertSlots(db, slots)
        validateMigration(db, events, plans, slots)
    }
}

private data class EventBackfill(
    val id: UUID,
    val rawTimeH: Double,
    val occurredAtEpochMillis: Long
)

private data class PlanPreflight(
    val id: UUID,
    val rawTimeOfDay: String,
    val slots: List<SlotBackfill>
)

private data class SlotBackfill(
    val id: UUID,
    val planId: UUID,
    val localTime: String,
    val position: Int
)

private fun applyV3Schema(db: SupportSQLiteDatabase) {
    db.execSQL(
        "ALTER TABLE `dose_events` " +
            "ADD COLUMN `occurredAtEpochMillis` INTEGER NOT NULL DEFAULT 0"
    )
    db.execSQL("ALTER TABLE `dose_events` ADD COLUMN `zoneId` TEXT")
    db.execSQL("ALTER TABLE `dose_events` ADD COLUMN `localDate` TEXT")
    db.execSQL("ALTER TABLE `dose_events` ADD COLUMN `slotId` TEXT")
    db.execSQL(
        "ALTER TABLE `dose_events` " +
            "ADD COLUMN `source` TEXT NOT NULL DEFAULT 'LEGACY'"
    )
    db.execSQL(
        "ALTER TABLE `dose_events` " +
            "ADD COLUMN `status` TEXT NOT NULL DEFAULT 'RECORDED'"
    )
    db.execSQL(
        "ALTER TABLE `dose_events` " +
            "ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 1"
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `scheduled_dose_slots` (
            `id` TEXT NOT NULL,
            `planId` TEXT NOT NULL,
            `localTime` TEXT NOT NULL,
            `position` INTEGER NOT NULL,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`planId`)
                REFERENCES `medication_plans`(`id`)
                ON UPDATE NO ACTION
                ON DELETE CASCADE
        )
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS `index_scheduled_dose_slots_planId`
        ON `scheduled_dose_slots` (`planId`)
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS `index_scheduled_dose_slots_planId_position`
        ON `scheduled_dose_slots` (`planId`, `position`)
        """.trimIndent()
    )
}

private fun preflightEvents(db: SupportSQLiteDatabase): List<EventBackfill> {
    val events = mutableListOf<EventBackfill>()
    db.query(
        "SELECT `id`, `timeH` FROM `dose_events` ORDER BY `id`"
    ).use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow("id")
        val timeHIndex = cursor.getColumnIndexOrThrow("timeH")
        while (cursor.moveToNext()) {
            val rawId = requireText(
                cursor = cursor,
                columnIndex = idIndex,
                tableName = DOSE_EVENTS_TABLE,
                rowId = null,
                operation = "event ID preflight"
            )
            val eventId = parseUuid(DOSE_EVENTS_TABLE, rawId, "event ID preflight")
            val storageClass = if (cursor.isNull(timeHIndex)) {
                LegacySqliteStorageClass.NULL
            } else {
                storageClass(
                    type = cursor.getType(timeHIndex),
                    tableName = DOSE_EVENTS_TABLE,
                    rowId = rawId,
                    operation = "timeH storage preflight"
                )
            }
            validateLegacyTimeHStorageClass(storageClass).valueOrThrow(
                tableName = DOSE_EVENTS_TABLE,
                rowId = rawId,
                operation = "timeH storage preflight"
            )
            val rawTimeH = cursor.getDouble(timeHIndex)
            val occurredAtEpochMillis = legacyTimeHToOccurredAtEpochMillis(
                eventId = eventId,
                rawTimeH = rawTimeH
            ).valueOrThrow(
                tableName = DOSE_EVENTS_TABLE,
                rowId = rawId,
                operation = "timeH conversion preflight"
            )
            events += EventBackfill(eventId, rawTimeH, occurredAtEpochMillis)
        }
    }
    return events
}

private fun preflightPlans(db: SupportSQLiteDatabase): List<PlanPreflight> {
    val plans = mutableListOf<PlanPreflight>()
    db.query(
        "SELECT `id`, `timeOfDay` FROM `medication_plans` ORDER BY `id`"
    ).use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow("id")
        val timeOfDayIndex = cursor.getColumnIndexOrThrow("timeOfDay")
        while (cursor.moveToNext()) {
            val rawId = requireText(
                cursor = cursor,
                columnIndex = idIndex,
                tableName = MEDICATION_PLANS_TABLE,
                rowId = null,
                operation = "plan ID preflight"
            )
            val planId = parseUuid(MEDICATION_PLANS_TABLE, rawId, "plan ID preflight")
            val rawTimeOfDay = requireText(
                cursor = cursor,
                columnIndex = timeOfDayIndex,
                tableName = MEDICATION_PLANS_TABLE,
                rowId = rawId,
                operation = "timeOfDay preflight"
            )
            val parsed = LegacyPlanTimeParser.parse(planId, rawTimeOfDay).valueOrThrow(
                tableName = MEDICATION_PLANS_TABLE,
                rowId = rawId,
                operation = "timeOfDay preflight"
            )
            plans += PlanPreflight(
                id = planId,
                rawTimeOfDay = rawTimeOfDay,
                slots = parsed.entries.map { entry ->
                    SlotBackfill(
                        id = entry.slotId,
                        planId = planId,
                        localTime = entry.canonicalLocalTime,
                        position = entry.position
                    )
                }
            )
        }
    }
    return plans
}

private fun backfillEvents(
    db: SupportSQLiteDatabase,
    events: List<EventBackfill>
) {
    db.compileStatement(
        """
        UPDATE `dose_events`
        SET `occurredAtEpochMillis` = ?
        WHERE `id` = ?
        """.trimIndent()
    ).use { statement ->
        events.forEach { event ->
            statement.clearBindings()
            statement.bindLong(1, event.occurredAtEpochMillis)
            statement.bindString(2, event.id.toString())
            val updatedRows = statement.executeUpdateDelete()
            if (updatedRows != 1) {
                migrationFailure(
                    tableName = DOSE_EVENTS_TABLE,
                    rowId = event.id.toString(),
                    operation = "event backfill affected $updatedRows rows"
                )
            }
        }
    }
}

private fun insertSlots(
    db: SupportSQLiteDatabase,
    slots: List<SlotBackfill>
) {
    db.compileStatement(
        """
        INSERT INTO `scheduled_dose_slots`
            (`id`, `planId`, `localTime`, `position`)
        VALUES (?, ?, ?, ?)
        """.trimIndent()
    ).use { statement ->
        slots.forEach { slot ->
            statement.clearBindings()
            statement.bindString(1, slot.id.toString())
            statement.bindString(2, slot.planId.toString())
            statement.bindString(3, slot.localTime)
            statement.bindLong(4, slot.position.toLong())
            if (statement.executeInsert() == -1L) {
                migrationFailure(
                    tableName = SLOTS_TABLE,
                    rowId = slot.id.toString(),
                    operation = "slot insert"
                )
            }
        }
    }
}

private fun validateMigration(
    db: SupportSQLiteDatabase,
    events: List<EventBackfill>,
    plans: List<PlanPreflight>,
    slots: List<SlotBackfill>
) {
    validateEventRows(db, events)
    validatePlanRows(db, plans)
    validateSlotRows(db, slots)
    db.query("PRAGMA foreign_key_check").use { cursor ->
        if (cursor.moveToFirst()) {
            migrationFailure(
                tableName = cursor.getString(0),
                rowId = if (cursor.isNull(1)) null else cursor.getString(1),
                operation = "foreign key validation"
            )
        }
    }
}

private fun validateEventRows(
    db: SupportSQLiteDatabase,
    events: List<EventBackfill>
) {
    val expectedById = events.associateBy { it.id.toString() }
    db.query(
        """
        SELECT `id`, `timeH`, `occurredAtEpochMillis`, `zoneId`, `localDate`,
            `slotId`, `source`, `status`, `revision`
        FROM `dose_events`
        ORDER BY `id`
        """.trimIndent()
    ).use { cursor ->
        if (cursor.count != events.size) {
            migrationFailure(
                tableName = DOSE_EVENTS_TABLE,
                rowId = null,
                operation = "event row count validation"
            )
        }
        while (cursor.moveToNext()) {
            val rawId = cursor.getString(0)
            val canonicalId = parseUuid(
                DOSE_EVENTS_TABLE,
                rawId,
                "event validation"
            ).toString()
            val expected = expectedById[canonicalId] ?: migrationFailure(
                tableName = DOSE_EVENTS_TABLE,
                rowId = rawId,
                operation = "unexpected event row"
            )
            if (
                cursor.getDouble(1) != expected.rawTimeH ||
                cursor.getLong(2) != expected.occurredAtEpochMillis ||
                !cursor.isNull(3) ||
                !cursor.isNull(4) ||
                !cursor.isNull(5) ||
                cursor.getString(6) != LEGACY_SOURCE ||
                cursor.getString(7) != RECORDED_STATUS ||
                cursor.getLong(8) != INITIAL_REVISION
            ) {
                migrationFailure(
                    tableName = DOSE_EVENTS_TABLE,
                    rowId = rawId,
                    operation = "event value validation"
                )
            }
        }
    }
}

private fun validatePlanRows(
    db: SupportSQLiteDatabase,
    plans: List<PlanPreflight>
) {
    val expectedById = plans.associateBy { it.id.toString() }
    db.query(
        "SELECT `id`, `timeOfDay` FROM `medication_plans` ORDER BY `id`"
    ).use { cursor ->
        if (cursor.count != plans.size) {
            migrationFailure(
                tableName = MEDICATION_PLANS_TABLE,
                rowId = null,
                operation = "plan row count validation"
            )
        }
        while (cursor.moveToNext()) {
            val rawId = cursor.getString(0)
            val canonicalId = parseUuid(
                MEDICATION_PLANS_TABLE,
                rawId,
                "plan validation"
            ).toString()
            val expected = expectedById[canonicalId] ?: migrationFailure(
                tableName = MEDICATION_PLANS_TABLE,
                rowId = rawId,
                operation = "unexpected plan row"
            )
            if (cursor.getString(1) != expected.rawTimeOfDay) {
                migrationFailure(
                    tableName = MEDICATION_PLANS_TABLE,
                    rowId = rawId,
                    operation = "timeOfDay value validation"
                )
            }
        }
    }
}

private fun validateSlotRows(
    db: SupportSQLiteDatabase,
    slots: List<SlotBackfill>
) {
    val expectedById = slots.associateBy { it.id.toString() }
    db.query(
        """
        SELECT `id`, `planId`, `localTime`, `position`
        FROM `scheduled_dose_slots`
        ORDER BY `id`
        """.trimIndent()
    ).use { cursor ->
        if (cursor.count != slots.size) {
            migrationFailure(
                tableName = SLOTS_TABLE,
                rowId = null,
                operation = "slot row count validation"
            )
        }
        while (cursor.moveToNext()) {
            val rawId = cursor.getString(0)
            val expected = expectedById[rawId] ?: migrationFailure(
                tableName = SLOTS_TABLE,
                rowId = rawId,
                operation = "unexpected slot row"
            )
            if (
                cursor.getString(1) != expected.planId.toString() ||
                cursor.getString(2) != expected.localTime ||
                cursor.getInt(3) != expected.position
            ) {
                migrationFailure(
                    tableName = SLOTS_TABLE,
                    rowId = rawId,
                    operation = "slot value validation"
                )
            }
        }
    }
}

private fun requireText(
    cursor: Cursor,
    columnIndex: Int,
    tableName: String,
    rowId: String?,
    operation: String
): String {
    if (cursor.isNull(columnIndex)) {
        migrationFailure(tableName, rowId, "$operation found NULL")
    }
    if (cursor.getType(columnIndex) != Cursor.FIELD_TYPE_STRING) {
        migrationFailure(tableName, rowId, "$operation found non-TEXT storage")
    }
    return cursor.getString(columnIndex)
}

private fun parseUuid(
    tableName: String,
    rawId: String,
    operation: String
): UUID = try {
    UUID.fromString(rawId)
} catch (cause: IllegalArgumentException) {
    throw LegacyMigrationException(
        tableName = tableName,
        rowId = rawId,
        operation = operation,
        cause = cause
    )
}

private fun storageClass(
    type: Int,
    tableName: String,
    rowId: String?,
    operation: String
): LegacySqliteStorageClass = when (type) {
    Cursor.FIELD_TYPE_INTEGER -> LegacySqliteStorageClass.INTEGER
    Cursor.FIELD_TYPE_FLOAT -> LegacySqliteStorageClass.FLOAT
    Cursor.FIELD_TYPE_STRING -> LegacySqliteStorageClass.STRING
    Cursor.FIELD_TYPE_BLOB -> LegacySqliteStorageClass.BLOB
    Cursor.FIELD_TYPE_NULL -> LegacySqliteStorageClass.NULL
    else -> migrationFailure(tableName, rowId, "$operation found unknown storage")
}

private fun <T> LegacyMigrationResult<T>.valueOrThrow(
    tableName: String,
    rowId: String?,
    operation: String
): T = when (this) {
    is LegacyMigrationResult.Success -> value
    is LegacyMigrationResult.Failure -> throw LegacyMigrationException(
        tableName = tableName,
        rowId = rowId,
        error = error,
        operation = operation
    )
}

private fun migrationFailure(
    tableName: String,
    rowId: String?,
    operation: String
): Nothing = throw LegacyMigrationException(
    tableName = tableName,
    rowId = rowId,
    operation = operation
)

private const val DOSE_EVENTS_TABLE = "dose_events"
private const val MEDICATION_PLANS_TABLE = "medication_plans"
private const val SLOTS_TABLE = "scheduled_dose_slots"
private const val LEGACY_SOURCE = "LEGACY"
private const val RECORDED_STATUS = "RECORDED"
private const val INITIAL_REVISION = 1L
