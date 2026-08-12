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

private fun preflightEvents(db: SupportSQLiteDatabase): List<LegacyEventValues> {
    val events = mutableListOf<LegacyEventValues>()
    db.query(
        """
        SELECT `id`, `route`, `timeH`, `doseMG`, `ester`, `extras`
        FROM `dose_events`
        ORDER BY `id`
        """.trimIndent()
    ).use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow("id")
        val routeIndex = cursor.getColumnIndexOrThrow("route")
        val timeHIndex = cursor.getColumnIndexOrThrow("timeH")
        val doseIndex = cursor.getColumnIndexOrThrow("doseMG")
        val esterIndex = cursor.getColumnIndexOrThrow("ester")
        val extrasIndex = cursor.getColumnIndexOrThrow("extras")
        while (cursor.moveToNext()) {
            val rawId = requireText(
                cursor = cursor,
                columnIndex = idIndex,
                tableName = DOSE_EVENTS_TABLE,
                rowId = null,
                operation = "event ID preflight"
            )
            val eventId = parseUuid(DOSE_EVENTS_TABLE, rawId, "event ID preflight")
            val route = requireText(cursor, routeIndex, DOSE_EVENTS_TABLE, rawId, "route")
            val storageClass = sqliteStorageClass(cursor, timeHIndex)
            validateLegacyTimeHStorageClass(storageClass).valueOrThrow(
                tableName = DOSE_EVENTS_TABLE,
                rowId = rawId,
                operation = "timeH storage preflight"
            )
            val rawTimeH = cursor.getDouble(timeHIndex)
            val doseMG = requireNumeric(cursor, doseIndex, DOSE_EVENTS_TABLE, rawId, "doseMG")
            val ester = requireText(cursor, esterIndex, DOSE_EVENTS_TABLE, rawId, "ester")
            val extras = requireText(cursor, extrasIndex, DOSE_EVENTS_TABLE, rawId, "extras")
            val occurredAtEpochMillis = legacyTimeHToOccurredAtEpochMillis(
                eventId = eventId,
                rawTimeH = rawTimeH
            ).valueOrThrow(
                tableName = DOSE_EVENTS_TABLE,
                rowId = rawId,
                operation = "timeH conversion preflight"
            )
            val event = LegacyEventValues(
                id = eventId,
                route = route,
                timeH = rawTimeH,
                doseMG = doseMG,
                ester = ester,
                extrasPayload = extras,
                occurredAtEpochMillis = occurredAtEpochMillis
            )
            requireAggregateReadable(DOSE_EVENTS_TABLE, rawId) {
                LegacyAggregatePreflight.requireReadable(event)
            }
            events += event
        }
    }
    return events
}

private fun preflightPlans(db: SupportSQLiteDatabase): List<LegacyPlanValues> {
    val plans = mutableListOf<LegacyPlanValues>()
    db.query(
        """
        SELECT `id`, `name`, `route`, `ester`, `doseMG`, `scheduleType`,
            `timeOfDay`, `daysOfWeek`, `intervalDays`, `isEnabled`, `extras`, `createdAt`
        FROM `medication_plans`
        ORDER BY `id`
        """.trimIndent()
    ).use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow("id")
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        val routeIndex = cursor.getColumnIndexOrThrow("route")
        val esterIndex = cursor.getColumnIndexOrThrow("ester")
        val doseIndex = cursor.getColumnIndexOrThrow("doseMG")
        val scheduleIndex = cursor.getColumnIndexOrThrow("scheduleType")
        val timeOfDayIndex = cursor.getColumnIndexOrThrow("timeOfDay")
        val daysIndex = cursor.getColumnIndexOrThrow("daysOfWeek")
        val intervalIndex = cursor.getColumnIndexOrThrow("intervalDays")
        val enabledIndex = cursor.getColumnIndexOrThrow("isEnabled")
        val extrasIndex = cursor.getColumnIndexOrThrow("extras")
        val createdAtIndex = cursor.getColumnIndexOrThrow("createdAt")
        while (cursor.moveToNext()) {
            val rawId = requireText(
                cursor = cursor,
                columnIndex = idIndex,
                tableName = MEDICATION_PLANS_TABLE,
                rowId = null,
                operation = "plan ID preflight"
            )
            val planId = parseUuid(MEDICATION_PLANS_TABLE, rawId, "plan ID preflight")
            val name = requireText(cursor, nameIndex, MEDICATION_PLANS_TABLE, rawId, "name")
            val route = requireText(cursor, routeIndex, MEDICATION_PLANS_TABLE, rawId, "route")
            val ester = requireText(cursor, esterIndex, MEDICATION_PLANS_TABLE, rawId, "ester")
            val doseMG = requireNumeric(cursor, doseIndex, MEDICATION_PLANS_TABLE, rawId, "doseMG")
            val scheduleType = requireText(
                cursor,
                scheduleIndex,
                MEDICATION_PLANS_TABLE,
                rawId,
                "scheduleType"
            )
            val rawTimeOfDay = requireText(
                cursor = cursor,
                columnIndex = timeOfDayIndex,
                tableName = MEDICATION_PLANS_TABLE,
                rowId = rawId,
                operation = "timeOfDay preflight"
            )
            val daysOfWeek = requireText(
                cursor,
                daysIndex,
                MEDICATION_PLANS_TABLE,
                rawId,
                "daysOfWeek"
            )
            val intervalDays = requireInt(
                cursor,
                intervalIndex,
                MEDICATION_PLANS_TABLE,
                rawId,
                "intervalDays"
            )
            val enabledValue = requireLong(
                cursor,
                enabledIndex,
                MEDICATION_PLANS_TABLE,
                rawId,
                "isEnabled"
            )
            if (enabledValue != 0L && enabledValue != 1L) {
                persistedValueFailure(
                    MEDICATION_PLANS_TABLE,
                    rawId,
                    "isEnabled",
                    PersistedValueFailure.NONCANONICAL_BOOLEAN
                )
            }
            val extras = requireText(cursor, extrasIndex, MEDICATION_PLANS_TABLE, rawId, "extras")
            val createdAt = requireLong(
                cursor,
                createdAtIndex,
                MEDICATION_PLANS_TABLE,
                rawId,
                "createdAt"
            )
            val parsed = LegacyPlanTimeParser.parse(planId, rawTimeOfDay).valueOrThrow(
                tableName = MEDICATION_PLANS_TABLE,
                rowId = rawId,
                operation = "timeOfDay preflight"
            )
            val plan = LegacyPlanValues(
                id = planId,
                name = name,
                route = route,
                ester = ester,
                doseMG = doseMG,
                scheduleType = scheduleType,
                timeOfDayPayload = rawTimeOfDay,
                daysOfWeekPayload = daysOfWeek,
                intervalDays = intervalDays,
                isEnabled = enabledValue == 1L,
                extrasPayload = extras,
                createdAt = createdAt,
                slots = parsed.entries.map { entry ->
                    LegacySlotValues(
                        id = entry.slotId,
                        planId = planId,
                        localTime = entry.canonicalLocalTime,
                        position = entry.position
                    )
                }
            )
            requireAggregateReadable(MEDICATION_PLANS_TABLE, rawId) {
                LegacyAggregatePreflight.requireReadable(plan)
            }
            plans += plan
        }
    }
    return plans
}

private fun backfillEvents(
    db: SupportSQLiteDatabase,
    events: List<LegacyEventValues>
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
    slots: List<LegacySlotValues>
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
    events: List<LegacyEventValues>,
    plans: List<LegacyPlanValues>,
    slots: List<LegacySlotValues>
) {
    validateEventRows(db, events)
    validatePlanRows(db, plans)
    validateSlotRows(db, slots)
    db.query("PRAGMA integrity_check").use { cursor ->
        if (!cursor.moveToFirst() || cursor.getString(0) != "ok" || cursor.moveToNext()) {
            migrationFailure(DATABASE_TABLE, null, "integrity validation")
        }
    }
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
    events: List<LegacyEventValues>
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
                cursor.getDouble(1) != expected.timeH ||
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
    plans: List<LegacyPlanValues>
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
            if (cursor.getString(1) != expected.timeOfDayPayload) {
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
    slots: List<LegacySlotValues>
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
    UUID.fromString(rawId).also { parsed ->
        if (parsed.toString() != rawId) {
            persistedValueFailure(
                tableName,
                rawId,
                "id",
                PersistedValueFailure.NONCANONICAL_ID
            )
        }
    }
} catch (_: IllegalArgumentException) {
    throw LegacyMigrationException(
        tableName = tableName,
        rowId = rawId,
        operation = operation
    )
}

private fun requireNumeric(
    cursor: Cursor,
    columnIndex: Int,
    tableName: String,
    rowId: String?,
    field: String
): Double {
    val type = cursor.getType(columnIndex)
    if (type != Cursor.FIELD_TYPE_INTEGER && type != Cursor.FIELD_TYPE_FLOAT) {
        persistedValueFailure(
            tableName,
            rowId,
            field,
            PersistedValueFailure.INVALID_STORAGE_CLASS
        )
    }
    return cursor.getDouble(columnIndex)
}

private fun requireLong(
    cursor: Cursor,
    columnIndex: Int,
    tableName: String,
    rowId: String?,
    field: String
): Long {
    if (cursor.getType(columnIndex) != Cursor.FIELD_TYPE_INTEGER) {
        persistedValueFailure(
            tableName,
            rowId,
            field,
            PersistedValueFailure.INVALID_STORAGE_CLASS
        )
    }
    return cursor.getLong(columnIndex)
}

private fun requireInt(
    cursor: Cursor,
    columnIndex: Int,
    tableName: String,
    rowId: String?,
    field: String
): Int {
    val value = requireLong(cursor, columnIndex, tableName, rowId, field)
    if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        persistedValueFailure(
            tableName,
            rowId,
            field,
            PersistedValueFailure.MAPPER_REJECTED
        )
    }
    return value.toInt()
}

private fun sqliteStorageClass(cursor: Cursor, columnIndex: Int): LegacySqliteStorageClass =
    if (cursor.isNull(columnIndex)) {
        LegacySqliteStorageClass.NULL
    } else {
        when (cursor.getType(columnIndex)) {
            Cursor.FIELD_TYPE_INTEGER -> LegacySqliteStorageClass.INTEGER
            Cursor.FIELD_TYPE_FLOAT -> LegacySqliteStorageClass.FLOAT
            Cursor.FIELD_TYPE_STRING -> LegacySqliteStorageClass.STRING
            Cursor.FIELD_TYPE_BLOB -> LegacySqliteStorageClass.BLOB
            Cursor.FIELD_TYPE_NULL -> LegacySqliteStorageClass.NULL
            else -> LegacySqliteStorageClass.BLOB
        }
    }

private inline fun requireAggregateReadable(
    tableName: String,
    rowId: String,
    block: () -> Unit
) {
    try {
        block()
    } catch (failure: LegacyAggregatePreflightException) {
        persistedValueFailure(tableName, rowId, failure.field, failure.reason, failure)
    }
}

private fun persistedValueFailure(
    tableName: String,
    rowId: String?,
    field: String,
    reason: PersistedValueFailure,
    cause: Throwable? = null
): Nothing = throw LegacyMigrationException(
    tableName = tableName,
    rowId = rowId,
    error = LegacyMigrationError.InvalidPersistedValue(field, reason),
    operation = "complete legacy preflight",
    cause = cause
)

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
private const val DATABASE_TABLE = "database"
private const val LEGACY_SOURCE = "LEGACY"
private const val RECORDED_STATUS = "RECORDED"
private const val INITIAL_REVISION = 1L
