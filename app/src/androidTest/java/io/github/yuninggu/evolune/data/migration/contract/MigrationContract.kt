package io.github.yuninggu.evolune.data.migration.contract

enum class PersistedAggregate {
    DOSE_EVENT,
    MEDICATION_PLAN,
    DATABASE
}

enum class ContractValidity {
    VALID,
    INVALID_UNMIGRATABLE,
    NOT_REPRESENTABLE
}

enum class RejectionStage {
    SQLITE_CONSTRAINT,
    MIGRATION_PREFLIGHT,
    PRODUCTION_CONVERTER,
    PRODUCTION_MAPPER,
    REPOSITORY_SEMANTICS,
    POST_DDL_ATOMICITY_HARNESS
}

enum class CurrentMigrationOutcome {
    MIGRATES_AND_REPOSITORY_READS,
    REJECTS_AND_ROLLS_BACK,
    MIGRATES_BUT_REPOSITORY_REJECTS,
    MIGRATES_WITH_INCONSISTENT_REPOSITORY_SEMANTICS,
    NOT_EXECUTABLE
}

data class MigrationContractCase(
    val name: String,
    val aggregate: PersistedAggregate,
    val field: String,
    val validity: ContractValidity,
    val rejectionStage: RejectionStage? = null,
    val currentOutcome: CurrentMigrationOutcome,
    val requiresBatch8B: Boolean,
    val fixture: RawV2Fixture? = null
)

object MigrationContractMatrix {
    val validCases: List<MigrationContractCase> = listOf(
        valid("empty-v2", PersistedAggregate.DATABASE, "all"),
        valid("minimal-event", PersistedAggregate.DOSE_EVENT, "all"),
        valid("minimal-plan", PersistedAggregate.MEDICATION_PLAN, "all"),
        valid("all-routes", PersistedAggregate.DATABASE, "route"),
        valid("all-esters", PersistedAggregate.DATABASE, "ester"),
        valid("all-schedule-types", PersistedAggregate.MEDICATION_PLAN, "scheduleType"),
        valid("days-empty-and-1-through-7", PersistedAggregate.MEDICATION_PLAN, "daysOfWeek"),
        valid("interval-1-and-int-max", PersistedAggregate.MEDICATION_PLAN, "intervalDays"),
        valid("all-extra-keys", PersistedAggregate.DATABASE, "extras"),
        valid("positive-time", PersistedAggregate.DOSE_EVENT, "timeH"),
        valid("zero-time", PersistedAggregate.DOSE_EVENT, "timeH"),
        valid("negative-historical-time", PersistedAggregate.DOSE_EVENT, "timeH"),
        valid("millisecond-time", PersistedAggregate.DOSE_EVENT, "timeH"),
        valid("old-time", PersistedAggregate.DOSE_EVENT, "timeH"),
        valid("representable-time-boundaries", PersistedAggregate.DOSE_EVENT, "timeH"),
        valid("duplicate-event-times", PersistedAggregate.DOSE_EVENT, "timeH"),
        valid("duplicate-plan-times", PersistedAggregate.MEDICATION_PLAN, "timeOfDay"),
        valid("larger-deterministic-fixture", PersistedAggregate.DATABASE, "all")
    )

    val currentPreflightRejections: List<MigrationContractCase> = listOf(
        currentReject(
            name = "malformed-event-id",
            aggregate = PersistedAggregate.DOSE_EVENT,
            field = "id",
            fixture = RawV2Fixture(events = listOf(V2EventRow(id = "not-a-uuid")))
        ),
        currentReject(
            name = "malformed-plan-id",
            aggregate = PersistedAggregate.MEDICATION_PLAN,
            field = "id",
            fixture = RawV2Fixture(plans = listOf(V2PlanRow(id = "not-a-uuid")))
        ),
        currentReject(
            name = "unrepresentable-event-time",
            aggregate = PersistedAggregate.DOSE_EVENT,
            field = "timeH",
            fixture = RawV2Fixture(
                events = listOf(
                    V2EventRow(
                        id = "10000000-0000-0000-0000-000000000003",
                        timeH = Double.POSITIVE_INFINITY
                    )
                )
            )
        ),
        currentReject(
            name = "malformed-plan-times",
            aggregate = PersistedAggregate.MEDICATION_PLAN,
            field = "timeOfDay",
            fixture = RawV2Fixture(
                plans = listOf(
                    V2PlanRow(
                        id = "20000000-0000-0000-0000-000000000003",
                        timeOfDay = "[\"08:30\""
                    )
                )
            )
        ),
        currentReject(
            name = "non-minute-plan-time",
            aggregate = PersistedAggregate.MEDICATION_PLAN,
            field = "timeOfDay",
            fixture = RawV2Fixture(
                plans = listOf(
                    V2PlanRow(
                        id = "20000000-0000-0000-0000-000000000004",
                        timeOfDay = "[\"20:30:15\"]"
                    )
                )
            )
        )
    )

    val batch8BRejections: List<MigrationContractCase> = listOf(
        eventMapperFailure("unknown-event-route", "route", route = "UNKNOWN"),
        eventMapperFailure("unknown-event-ester", "ester", ester = "UNKNOWN"),
        eventMapperFailure(
            "unknown-event-extra-key",
            "extras",
            extras = "{\"UNKNOWN\":1.0}"
        ),
        eventConverterFailure("malformed-event-extras", "extras", "{"),
        planMapperFailure("unknown-plan-route", "route", route = "UNKNOWN"),
        planMapperFailure("unknown-plan-ester", "ester", ester = "UNKNOWN"),
        planMapperFailure(
            "unknown-plan-schedule-type",
            "scheduleType",
            scheduleType = "UNKNOWN"
        ),
        planMapperFailure("invalid-plan-day", "daysOfWeek", daysOfWeek = "[8]"),
        planMapperFailure("zero-plan-interval", "intervalDays", intervalDays = 0),
        planMapperFailure(
            "unknown-plan-extra-key",
            "extras",
            extras = "{\"UNKNOWN\":1.0}"
        ),
        planConverterFailure("malformed-plan-days", "daysOfWeek", daysOfWeek = "{"),
        planConverterFailure("malformed-plan-extras", "extras", extras = "{"),
        MigrationContractCase(
            name = "noncanonical-enabled-integer",
            aggregate = PersistedAggregate.MEDICATION_PLAN,
            field = "isEnabled",
            validity = ContractValidity.INVALID_UNMIGRATABLE,
            rejectionStage = RejectionStage.REPOSITORY_SEMANTICS,
            currentOutcome = CurrentMigrationOutcome.MIGRATES_WITH_INCONSISTENT_REPOSITORY_SEMANTICS,
            requiresBatch8B = true,
            fixture = RawV2Fixture(
                plans = listOf(
                    V2PlanRow(
                        id = "22000000-0000-0000-0000-000000000013",
                        isEnabled = 2
                    )
                )
            )
        )
    )

    val notRepresentableCases: List<MigrationContractCase> = listOf(
        notRepresentable("null-in-not-null-column", "all", RejectionStage.SQLITE_CONSTRAINT),
        notRepresentable("nan-real-binding", "timeH", RejectionStage.SQLITE_CONSTRAINT),
        notRepresentable("duplicate-primary-key", "id", RejectionStage.SQLITE_CONSTRAINT),
        MigrationContractCase(
            name = "post-ddl-injected-failure-without-test-seam",
            aggregate = PersistedAggregate.DATABASE,
            field = "transaction",
            validity = ContractValidity.NOT_REPRESENTABLE,
            rejectionStage = RejectionStage.POST_DDL_ATOMICITY_HARNESS,
            currentOutcome = CurrentMigrationOutcome.NOT_EXECUTABLE,
            requiresBatch8B = true
        )
    )

    val allCases: List<MigrationContractCase> =
        validCases + currentPreflightRejections + batch8BRejections + notRepresentableCases

    private fun valid(
        name: String,
        aggregate: PersistedAggregate,
        field: String
    ) = MigrationContractCase(
        name = name,
        aggregate = aggregate,
        field = field,
        validity = ContractValidity.VALID,
        currentOutcome = CurrentMigrationOutcome.MIGRATES_AND_REPOSITORY_READS,
        requiresBatch8B = false
    )

    private fun currentReject(
        name: String,
        aggregate: PersistedAggregate,
        field: String,
        fixture: RawV2Fixture
    ) = MigrationContractCase(
        name = name,
        aggregate = aggregate,
        field = field,
        validity = ContractValidity.INVALID_UNMIGRATABLE,
        rejectionStage = RejectionStage.MIGRATION_PREFLIGHT,
        currentOutcome = CurrentMigrationOutcome.REJECTS_AND_ROLLS_BACK,
        requiresBatch8B = false,
        fixture = fixture
    )

    private fun eventMapperFailure(
        name: String,
        field: String,
        route: String = "ORAL",
        ester: String = "E2",
        extras: String = "{}"
    ) = MigrationContractCase(
        name = name,
        aggregate = PersistedAggregate.DOSE_EVENT,
        field = field,
        validity = ContractValidity.INVALID_UNMIGRATABLE,
        rejectionStage = RejectionStage.PRODUCTION_MAPPER,
        currentOutcome = CurrentMigrationOutcome.MIGRATES_BUT_REPOSITORY_REJECTS,
        requiresBatch8B = true,
        fixture = RawV2Fixture(
            events = listOf(
                V2EventRow(
                    id = deterministicId("11000000", name),
                    route = route,
                    ester = ester,
                    extras = extras
                )
            )
        )
    )

    private fun eventConverterFailure(
        name: String,
        field: String,
        extras: String
    ) = MigrationContractCase(
        name = name,
        aggregate = PersistedAggregate.DOSE_EVENT,
        field = field,
        validity = ContractValidity.INVALID_UNMIGRATABLE,
        rejectionStage = RejectionStage.PRODUCTION_CONVERTER,
        currentOutcome = CurrentMigrationOutcome.MIGRATES_BUT_REPOSITORY_REJECTS,
        requiresBatch8B = true,
        fixture = RawV2Fixture(
            events = listOf(
                V2EventRow(
                    id = "11000000-0000-0000-0000-000000000004",
                    extras = extras
                )
            )
        )
    )

    private fun planMapperFailure(
        name: String,
        field: String,
        route: String = "ORAL",
        ester: String = "E2",
        scheduleType: String = "DAILY",
        daysOfWeek: String = "[]",
        intervalDays: Int = 1,
        extras: String = "{}"
    ) = MigrationContractCase(
        name = name,
        aggregate = PersistedAggregate.MEDICATION_PLAN,
        field = field,
        validity = ContractValidity.INVALID_UNMIGRATABLE,
        rejectionStage = RejectionStage.PRODUCTION_MAPPER,
        currentOutcome = CurrentMigrationOutcome.MIGRATES_BUT_REPOSITORY_REJECTS,
        requiresBatch8B = true,
        fixture = RawV2Fixture(
            plans = listOf(
                V2PlanRow(
                    id = deterministicId("21000000", name),
                    route = route,
                    ester = ester,
                    scheduleType = scheduleType,
                    daysOfWeek = daysOfWeek,
                    intervalDays = intervalDays,
                    extras = extras
                )
            )
        )
    )

    private fun planConverterFailure(
        name: String,
        field: String,
        daysOfWeek: String = "[]",
        extras: String = "{}"
    ) = MigrationContractCase(
        name = name,
        aggregate = PersistedAggregate.MEDICATION_PLAN,
        field = field,
        validity = ContractValidity.INVALID_UNMIGRATABLE,
        rejectionStage = RejectionStage.PRODUCTION_CONVERTER,
        currentOutcome = CurrentMigrationOutcome.MIGRATES_BUT_REPOSITORY_REJECTS,
        requiresBatch8B = true,
        fixture = RawV2Fixture(
            plans = listOf(
                V2PlanRow(
                    id = deterministicId("21000000", name),
                    daysOfWeek = daysOfWeek,
                    extras = extras
                )
            )
        )
    )

    private fun notRepresentable(
        name: String,
        field: String,
        stage: RejectionStage
    ) = MigrationContractCase(
        name = name,
        aggregate = PersistedAggregate.DATABASE,
        field = field,
        validity = ContractValidity.NOT_REPRESENTABLE,
        rejectionStage = stage,
        currentOutcome = CurrentMigrationOutcome.NOT_EXECUTABLE,
        requiresBatch8B = false
    )

    private fun deterministicId(prefix: String, value: String): String =
        "$prefix-0000-0000-0000-${value.hashCode().toUInt().toString().padStart(12, '0').takeLast(12)}"
}
