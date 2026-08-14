package io.github.yingqiu0871.evolune.data.migration.contract

data class PersistedFieldContract(
    val aggregate: String,
    val field: String,
    val sqliteRepresentation: String,
    val productionDecoder: String,
    val allowedLegacyValues: String,
    val invalidRepresentableValues: String,
    val failureBehavior: String,
    val migrationPreflightRequired: Boolean,
    val repositoryReadRequired: Boolean,
    val fixtureRequired: Boolean
)

data class MigrationFieldMapping(
    val schema2Column: String,
    val schema3Column: String,
    val operation: String,
    val validator: String,
    val postcondition: String
)

object PersistedContractInventory {
    val schema2Fields: List<PersistedFieldContract> = listOf(
        field("dose_events", "id", "TEXT NOT NULL PK", "Converters.toUUID", "canonical UUID", "malformed UUID", "migration rejects", true),
        field("dose_events", "route", "TEXT NOT NULL", "routeFromLegacyStorage", "7 explicit Route names", "unknown text", "mapper failure", true),
        field("dose_events", "timeH", "REAL NOT NULL", "LegacyTimeAdapter", "finite millisecond-representable hours", "non-numeric storage, infinity, overflow", "migration rejects", true),
        field("dose_events", "doseMG", "REAL NOT NULL", "Room numeric read", "all SQLite numeric values accepted by Domain", "non-numeric storage", "production coercion is not a semantic validator", true),
        field("dose_events", "ester", "TEXT NOT NULL", "esterFromLegacyStorage", "E2/EB/EV/EC/EN", "unknown text", "mapper failure", true),
        field("dose_events", "extras", "TEXT NOT NULL", "Converters.toMap + ExtraKeyMapper", "empty text or JSON object with 6 explicit keys", "malformed JSON or unknown key", "converter or mapper failure", true),
        field("medication_plans", "id", "TEXT NOT NULL PK", "Converters.toUUID", "canonical UUID", "malformed UUID", "migration rejects", true),
        field("medication_plans", "name", "TEXT NOT NULL", "Room text read", "all text", "non-text storage", "production coercion is not a semantic validator", true),
        field("medication_plans", "route", "TEXT NOT NULL", "routeFromLegacyStorage", "7 explicit Route names", "unknown text", "mapper failure", true),
        field("medication_plans", "ester", "TEXT NOT NULL", "esterFromLegacyStorage", "E2/EB/EV/EC/EN", "unknown text", "mapper failure", true),
        field("medication_plans", "doseMG", "REAL NOT NULL", "Room numeric read", "all SQLite numeric values accepted by Domain", "non-numeric storage", "production coercion is not a semantic validator", true),
        field("medication_plans", "scheduleType", "TEXT NOT NULL", "scheduleTypeFromLegacyStorage", "DAILY/WEEKLY/CUSTOM", "unknown text", "mapper failure", true),
        field("medication_plans", "timeOfDay", "TEXT NOT NULL", "Converters.toStringList + LegacyPlanTimeParser", "empty text or JSON list of minute-precision local times", "malformed JSON, non-string, invalid or non-minute time", "migration rejects", true),
        field("medication_plans", "daysOfWeek", "TEXT NOT NULL", "Converters.toIntSet + mapper", "empty text or JSON set using 1..7", "malformed JSON or value outside 1..7", "converter or mapper failure", true),
        field("medication_plans", "intervalDays", "INTEGER NOT NULL", "Room integer read + Domain init", "1..Int.MAX_VALUE", "zero or negative", "mapper invariant failure", true),
        field("medication_plans", "isEnabled", "INTEGER NOT NULL", "Room Boolean adapter", "canonical 0 or 1", "other integers", "currently coerced; future preflight rejection", true),
        field("medication_plans", "extras", "TEXT NOT NULL", "Converters.toMap + ExtraKeyMapper", "empty text or JSON object with 6 explicit keys", "malformed JSON or unknown key", "converter or mapper failure", true),
        field("medication_plans", "createdAt", "INTEGER NOT NULL", "Instant.ofEpochMilli", "all Long epoch millis", "non-integer storage", "production coercion is not a semantic validator", true)
    )

    val schema2To3Mappings: List<MigrationFieldMapping> = listOf(
        preserve("dose_events.id", "UUID and TEXT storage preflight"),
        preserve("dose_events.route", "future converter-backed preflight"),
        preserve("dose_events.timeH", "storage class and LegacyTimeAdapter preflight"),
        preserve("dose_events.doseMG", "future storage contract preflight"),
        preserve("dose_events.ester", "future converter-backed preflight"),
        preserve("dose_events.extras", "future converter-backed preflight"),
        derive("dose_events.timeH", "dose_events.occurredAtEpochMillis", "LegacyTimeAdapter", "exact epoch millis"),
        default("dose_events.zoneId", "NULL", "must remain NULL"),
        default("dose_events.localDate", "NULL", "must remain NULL"),
        default("dose_events.slotId", "NULL", "legacy events are not assigned a slot"),
        default("dose_events.source", "LEGACY", "must equal LEGACY"),
        default("dose_events.status", "RECORDED", "must equal RECORDED"),
        default("dose_events.revision", "1", "must equal 1"),
        preserve("medication_plans.id", "UUID and TEXT storage preflight"),
        preserve("medication_plans.name", "future storage contract preflight"),
        preserve("medication_plans.route", "future converter-backed preflight"),
        preserve("medication_plans.ester", "future converter-backed preflight"),
        preserve("medication_plans.doseMG", "future storage contract preflight"),
        preserve("medication_plans.scheduleType", "future converter-backed preflight"),
        preserve("medication_plans.timeOfDay", "LegacyPlanTimeParser preflight"),
        preserve("medication_plans.daysOfWeek", "future converter-backed preflight"),
        preserve("medication_plans.intervalDays", "future mapper invariant preflight"),
        preserve("medication_plans.isEnabled", "future canonical Boolean preflight"),
        preserve("medication_plans.extras", "future converter-backed preflight"),
        preserve("medication_plans.createdAt", "future storage contract preflight"),
        derive("medication_plans.timeOfDay", "scheduled_dose_slots.*", "LegacyPlanTimeParser + UUIDv5", "ordered slots exactly match legacy times")
    )

    private fun field(
        aggregate: String,
        field: String,
        sqlite: String,
        decoder: String,
        allowed: String,
        invalid: String,
        failure: String,
        preflight: Boolean
    ) = PersistedFieldContract(
        aggregate = aggregate,
        field = field,
        sqliteRepresentation = sqlite,
        productionDecoder = decoder,
        allowedLegacyValues = allowed,
        invalidRepresentableValues = invalid,
        failureBehavior = failure,
        migrationPreflightRequired = preflight,
        repositoryReadRequired = true,
        fixtureRequired = true
    )

    private fun preserve(column: String, validator: String) = MigrationFieldMapping(
        schema2Column = column,
        schema3Column = column,
        operation = "preserve",
        validator = validator,
        postcondition = "byte/value semantics unchanged"
    )

    private fun derive(
        source: String,
        target: String,
        validator: String,
        postcondition: String
    ) = MigrationFieldMapping(source, target, "derive", validator, postcondition)

    private fun default(target: String, value: String, postcondition: String) =
        MigrationFieldMapping("none", target, "default $value", "DDL default", postcondition)
}
