import json
import hashlib
import math
import pathlib
import sqlite3
import tempfile
import unittest

import create_synthetic_evidence
import repair_v2


EVENT_ID = "00000000-0000-0000-0000-000000000101"
EVENT_ID_2 = "00000000-0000-0000-0000-000000000102"
PLAN_ID = "00000000-0000-0000-0000-000000000201"
PLAN_ID_2 = "00000000-0000-0000-0000-000000000202"
PARITY_CORPUS = pathlib.Path(__file__).with_name("parity-corpus.json")
ANDROID_PARITY_CORPUS = (
    pathlib.Path(__file__).parents[2]
    / "app"
    / "src"
    / "androidTest"
    / "assets"
    / "repair-v2-parity-corpus.json"
)


def create_v2_database(
    path: pathlib.Path,
    *,
    user_version: int = 2,
    identity_hash: str = repair_v2.V2_IDENTITY_HASH,
    include_events: bool = True,
    include_plans: bool = True,
    include_room_master: bool = True,
    event_has_time_h: bool = True,
    plan_has_time_of_day: bool = True,
) -> None:
    connection = sqlite3.connect(path)
    try:
        if include_events:
            event_time_column = ", timeH REAL NOT NULL" if event_has_time_h else ""
            connection.execute(
                "CREATE TABLE dose_events ("
                "id TEXT NOT NULL PRIMARY KEY, "
                "route TEXT NOT NULL"
                f"{event_time_column}, "
                "doseMG REAL NOT NULL, "
                "ester TEXT NOT NULL, "
                "extras TEXT NOT NULL)"
            )
        if include_plans:
            plan_time_column = ", timeOfDay TEXT NOT NULL" if plan_has_time_of_day else ""
            connection.execute(
                "CREATE TABLE medication_plans ("
                "id TEXT NOT NULL PRIMARY KEY, "
                "name TEXT NOT NULL, "
                "route TEXT NOT NULL, "
                "ester TEXT NOT NULL, "
                "doseMG REAL NOT NULL, "
                "scheduleType TEXT NOT NULL"
                f"{plan_time_column}, "
                "daysOfWeek TEXT NOT NULL, "
                "intervalDays INTEGER NOT NULL, "
                "isEnabled INTEGER NOT NULL, "
                "extras TEXT NOT NULL, "
                "createdAt INTEGER NOT NULL)"
            )
        if include_room_master:
            connection.execute(
                "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            connection.execute(
                "INSERT INTO room_master_table(id, identity_hash) VALUES (42, ?)",
                (identity_hash,),
            )
        connection.execute(f"PRAGMA user_version = {user_version}")
        connection.commit()
    finally:
        connection.close()


def insert_event(
    path: pathlib.Path,
    event_id: str = EVENT_ID,
    time_h=1.0,
    *,
    route: str = "ORAL",
    dose_mg: float = 1.25,
    ester: str = "E2",
    extras: str = "{}",
) -> None:
    connection = sqlite3.connect(path)
    try:
        connection.execute(
            "INSERT INTO dose_events(id, route, timeH, doseMG, ester, extras) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            (event_id, route, time_h, dose_mg, ester, extras),
        )
        connection.commit()
    finally:
        connection.close()


def insert_plan(
    path: pathlib.Path,
    plan_id: str = PLAN_ID,
    time_of_day: str = '["08:30"]',
    *,
    name: str = "Synthetic Plan",
    extras: str = "{}",
    route: str = "ORAL",
    ester: str = "E2",
    schedule_type: str = "DAILY",
    days_of_week: str = "[]",
    interval_days: int = 1,
    is_enabled: int = 1,
    created_at: int = 0,
) -> None:
    connection = sqlite3.connect(path)
    try:
        connection.execute(
            "INSERT INTO medication_plans("
            "id, name, route, ester, doseMG, scheduleType, timeOfDay, daysOfWeek, "
            "intervalDays, isEnabled, extras, createdAt"
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                plan_id,
                name,
                route,
                ester,
                2.5,
                schedule_type,
                time_of_day,
                days_of_week,
                interval_days,
                is_enabled,
                extras,
                created_at,
            ),
        )
        connection.commit()
    finally:
        connection.close()


def write_manifest(
    path: pathlib.Path,
    input_sha256: str,
    *,
    event_corrections=None,
    plan_corrections=None,
) -> None:
    content = {
        "version": 1,
        "inputSha256": input_sha256,
        "eventCorrections": event_corrections or {},
        "planCorrections": plan_corrections or {},
    }
    path.write_text(json.dumps(content, indent=2), encoding="utf-8")


class RepairV2TestCase(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def database(self, name: str = "synthetic-v2.db", **kwargs) -> pathlib.Path:
        path = self.root / name
        create_v2_database(path, **kwargs)
        return path

    def manifest_for(self, database: pathlib.Path, **kwargs) -> pathlib.Path:
        path = self.root / "corrections.json"
        write_manifest(path, repair_v2.sha256_file(database), **kwargs)
        return path

    def issue_codes(self, database: pathlib.Path) -> list[repair_v2.IssueCode]:
        return [issue.code for issue in repair_v2.scan_database(database).issues]

    def test_synthetic_evidence_generator_runs_complete_copy_repair(self) -> None:
        evidence_root = self.root / "evidence"
        evidence = create_synthetic_evidence.create_evidence(evidence_root)
        self.assertEqual(2, evidence["inputIssueCount"])
        self.assertEqual(
            ["TIME_H_NON_FINITE", "TIME_OF_DAY_NON_MINUTE"],
            evidence["inputIssueCategories"],
        )
        self.assertEqual(1, evidence["eventCorrectionCount"])
        self.assertEqual(1, evidence["planCorrectionCount"])
        self.assertEqual(0, evidence["outputIssueCount"])
        self.assertEqual(
            repair_v2.sha256_file(evidence_root / "synthetic-invalid-v2.db"),
            evidence["originalSha256"],
        )
        self.assertEqual(
            repair_v2.sha256_file(evidence_root / "synthetic-repaired-v2.db"),
            evidence["repairedSha256"],
        )

    def repair_database(
        self,
        database: pathlib.Path,
        output: pathlib.Path,
        manifest: pathlib.Path,
    ) -> repair_v2.RepairResult:
        preview = repair_v2.preview_repair(database, manifest)
        return repair_v2.repair_database(
            database,
            output,
            manifest,
            preview.preview_token,
        )

    # Database identity

    def test_identity_accepts_user_version_two(self) -> None:
        database = self.database()
        result = repair_v2.scan_database(database)
        self.assertEqual(2, result.identity.user_version)
        self.assertEqual(repair_v2.V2_IDENTITY_HASH, result.identity.identity_hash)

    def test_identity_rejects_user_version_three(self) -> None:
        database = self.database(user_version=3)
        with self.assertRaises(repair_v2.DatabaseIdentityError):
            repair_v2.scan_database(database)

    def test_identity_rejects_user_version_one(self) -> None:
        database = self.database(user_version=1)
        with self.assertRaises(repair_v2.DatabaseIdentityError):
            repair_v2.scan_database(database)

    def test_identity_rejects_non_sqlite_file(self) -> None:
        path = self.root / "not-sqlite.db"
        path.write_bytes(b"synthetic non-sqlite content")
        with self.assertRaises(repair_v2.DatabaseIdentityError):
            repair_v2.scan_database(path)

    def test_identity_rejects_missing_event_table(self) -> None:
        database = self.database(include_events=False)
        with self.assertRaises(repair_v2.DatabaseIdentityError):
            repair_v2.scan_database(database)

    def test_identity_rejects_missing_plan_table(self) -> None:
        database = self.database(include_plans=False)
        with self.assertRaises(repair_v2.DatabaseIdentityError):
            repair_v2.scan_database(database)

    def test_identity_rejects_missing_event_time_column(self) -> None:
        database = self.database(event_has_time_h=False)
        with self.assertRaises(repair_v2.DatabaseIdentityError):
            repair_v2.scan_database(database)

    def test_identity_rejects_missing_plan_time_column(self) -> None:
        database = self.database(plan_has_time_of_day=False)
        with self.assertRaises(repair_v2.DatabaseIdentityError):
            repair_v2.scan_database(database)

    def test_identity_rejects_missing_room_master(self) -> None:
        database = self.database(include_room_master=False)
        with self.assertRaises(repair_v2.DatabaseIdentityError):
            repair_v2.scan_database(database)

    def test_identity_rejects_wrong_room_hash(self) -> None:
        database = self.database(identity_hash="0" * 32)
        with self.assertRaises(repair_v2.DatabaseIdentityError):
            repair_v2.scan_database(database)

    # Scan

    def test_scan_clean_database_succeeds(self) -> None:
        database = self.database()
        insert_event(database)
        insert_plan(database)
        self.assertEqual((), repair_v2.scan_database(database).issues)

    def test_scan_reports_positive_infinity(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        self.assertIn(repair_v2.IssueCode.TIME_H_NON_FINITE, self.issue_codes(database))

    def test_scan_reports_negative_infinity(self) -> None:
        database = self.database()
        insert_event(database, time_h=-math.inf)
        self.assertIn(repair_v2.IssueCode.TIME_H_NON_FINITE, self.issue_codes(database))

    def test_scan_reports_epoch_overflow(self) -> None:
        database = self.database()
        insert_event(database, time_h=2_562_047_788_015.216)
        self.assertIn(repair_v2.IssueCode.TIME_H_OUT_OF_RANGE, self.issue_codes(database))

    def test_scan_reports_multiplication_overflow(self) -> None:
        database = self.database()
        insert_event(database, time_h=1.0e308)
        self.assertIn(repair_v2.IssueCode.TIME_H_MULTIPLICATION_OVERFLOW, self.issue_codes(database))

    def test_scan_reports_text_time_h(self) -> None:
        database = self.database()
        insert_event(database, time_h="not-a-number")
        self.assertIn(repair_v2.IssueCode.TIME_H_STORAGE_CLASS, self.issue_codes(database))

    def test_scan_reports_blob_time_h(self) -> None:
        database = self.database()
        insert_event(database, time_h=sqlite3.Binary(b"synthetic"))
        self.assertIn(repair_v2.IssueCode.TIME_H_STORAGE_CLASS, self.issue_codes(database))

    def test_scan_reports_non_minute_plan_time(self) -> None:
        database = self.database()
        insert_plan(database, time_of_day='["20:30:15"]')
        result = repair_v2.scan_database(database)
        issue = result.issues[0]
        self.assertEqual(repair_v2.IssueCode.TIME_OF_DAY_NON_MINUTE, issue.code)
        self.assertEqual(PLAN_ID, issue.entity_id)
        self.assertEqual(0, issue.position)
        self.assertEqual("20:30:15", issue.raw_value)

    def test_scan_reports_malformed_plan_json(self) -> None:
        database = self.database()
        insert_plan(database, time_of_day="[")
        self.assertIn(repair_v2.IssueCode.TIME_OF_DAY_JSON_MALFORMED, self.issue_codes(database))

    def test_scan_reports_non_string_plan_element(self) -> None:
        database = self.database()
        insert_plan(database, time_of_day='["08:30",1]')
        self.assertIn(repair_v2.IssueCode.TIME_OF_DAY_ELEMENT_NOT_STRING, self.issue_codes(database))

    def test_scan_reports_object_plan_root(self) -> None:
        database = self.database()
        insert_plan(database, time_of_day='{"time":"08:30"}')
        self.assertIn(repair_v2.IssueCode.TIME_OF_DAY_ROOT_NOT_ARRAY, self.issue_codes(database))

    def test_scan_reports_multiple_issues(self) -> None:
        database = self.database()
        insert_event(database, EVENT_ID, math.inf)
        insert_event(database, EVENT_ID_2, "text")
        insert_plan(database, PLAN_ID, '["20:30:15",1,"25:00"]')
        result = repair_v2.scan_database(database)
        self.assertEqual(5, len(result.issues))

    def test_scan_does_not_change_input_hash_or_mtime(self) -> None:
        database = self.database()
        insert_event(database)
        before_hash = repair_v2.sha256_file(database)
        before_mtime = database.stat().st_mtime_ns
        repair_v2.scan_database(database)
        self.assertEqual(before_hash, repair_v2.sha256_file(database))
        self.assertEqual(before_mtime, database.stat().st_mtime_ns)

    def test_scan_reports_invalid_event_uuid(self) -> None:
        database = self.database()
        insert_event(database, event_id="synthetic-invalid-event")
        self.assertIn(repair_v2.IssueCode.EVENT_ID_INVALID, self.issue_codes(database))

    def test_scan_reports_invalid_plan_uuid(self) -> None:
        database = self.database()
        insert_plan(database, plan_id="synthetic-invalid-plan")
        self.assertIn(repair_v2.IssueCode.PLAN_ID_INVALID, self.issue_codes(database))

    def test_scan_reports_complete_persisted_contract_categories(self) -> None:
        cases = [
            ("event-route", lambda db: insert_event(db, route="UNKNOWN"), repair_v2.IssueCode.ROUTE_INVALID),
            ("event-ester", lambda db: insert_event(db, ester="UNKNOWN"), repair_v2.IssueCode.ESTER_INVALID),
            ("event-extras-json", lambda db: insert_event(db, extras="{"), repair_v2.IssueCode.EXTRAS_JSON_MALFORMED),
            ("event-extra-key", lambda db: insert_event(db, extras='{"UNKNOWN":1.0}'), repair_v2.IssueCode.EXTRA_KEY_INVALID),
            ("plan-route", lambda db: insert_plan(db, route="UNKNOWN"), repair_v2.IssueCode.ROUTE_INVALID),
            ("plan-schedule", lambda db: insert_plan(db, schedule_type="UNKNOWN"), repair_v2.IssueCode.SCHEDULE_TYPE_INVALID),
            ("plan-days-json", lambda db: insert_plan(db, days_of_week="{"), repair_v2.IssueCode.DAYS_JSON_MALFORMED),
            ("plan-day-value", lambda db: insert_plan(db, days_of_week="[8]"), repair_v2.IssueCode.DAY_VALUE_INVALID),
            ("plan-interval", lambda db: insert_plan(db, interval_days=0), repair_v2.IssueCode.INTERVAL_INVALID),
            ("plan-enabled", lambda db: insert_plan(db, is_enabled=2), repair_v2.IssueCode.ENABLED_NONCANONICAL),
        ]
        for name, arrange, expected in cases:
            with self.subTest(name=name):
                database = self.database(f"{name}.db")
                arrange(database)
                self.assertIn(expected, self.issue_codes(database))

    def test_shared_parity_corpus_matches_python_classification(self) -> None:
        corpus = json.loads(PARITY_CORPUS.read_text(encoding="utf-8"))
        self.assertEqual(
            corpus,
            json.loads(ANDROID_PARITY_CORPUS.read_text(encoding="utf-8")),
        )
        self.assertEqual(1, corpus["version"])
        for index, case in enumerate(corpus["cases"]):
            with self.subTest(name=case["name"]):
                database = self.database(f"parity-{index}.db")
                values = dict(case["values"])
                if values.get("timeH") == "Infinity":
                    values["timeH"] = math.inf
                for field in ("timeH", "doseMG", "intervalDays", "isEnabled", "createdAt"):
                    if values.get(field) == "TEXT":
                        values[field] = "synthetic-text"
                if case["aggregate"] == "event":
                    insert_event(database, **{
                        "event_id": values.get("id", EVENT_ID),
                        "time_h": values.get("timeH", 1.0),
                        "route": values.get("route", "ORAL"),
                        "dose_mg": values.get("doseMG", 1.25),
                        "ester": values.get("ester", "E2"),
                        "extras": values.get("extras", "{}"),
                    })
                else:
                    insert_plan(database, **{
                        "plan_id": values.get("id", PLAN_ID),
                        "time_of_day": values.get("timeOfDay", '["08:30"]'),
                        "route": values.get("route", "ORAL"),
                        "ester": values.get("ester", "E2"),
                        "schedule_type": values.get("scheduleType", "DAILY"),
                        "days_of_week": values.get("daysOfWeek", "[]"),
                        "interval_days": values.get("intervalDays", 1),
                        "is_enabled": values.get("isEnabled", 1),
                        "extras": values.get("extras", "{}"),
                        "created_at": values.get("createdAt", 0),
                    })
                self.assertEqual(case["expectedValid"], not repair_v2.scan_database(database).issues)

    def test_non_time_issue_is_not_manifest_repairable(self) -> None:
        database = self.database()
        insert_event(database, route="UNKNOWN")
        manifest = self.manifest_for(database)
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.preview_repair(database, manifest)

    def test_scan_rejects_active_wal_sidecar(self) -> None:
        database = self.database()
        pathlib.Path(f"{database}-wal").write_bytes(b"synthetic active wal")
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.scan_database(database)

    def test_scan_rejects_active_shm_sidecar(self) -> None:
        database = self.database()
        pathlib.Path(f"{database}-shm").write_bytes(b"synthetic active shm")
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.scan_database(database)

    # Java-compatible time conversion

    def test_time_conversion_zero(self) -> None:
        self.assertEqual(0, repair_v2.java_math_round_scaled(0.0))

    def test_time_conversion_positive_value(self) -> None:
        self.assertEqual(3_600_000, repair_v2.java_math_round_scaled(1.0))

    def test_time_conversion_negative_value(self) -> None:
        self.assertEqual(-3_600_000, repair_v2.java_math_round_scaled(-1.0))

    def test_time_conversion_millisecond_vector(self) -> None:
        self.assertEqual(1_700_000_000_123, repair_v2.java_math_round_scaled(472_222.22225638886))

    def test_time_conversion_positive_rounding_boundary(self) -> None:
        self.assertEqual(1, repair_v2.java_math_round_scaled(1.388888888888889e-7))

    def test_time_conversion_negative_rounding_boundary(self) -> None:
        self.assertEqual(-1, repair_v2.java_math_round_scaled(-1.388888888888889e-7))

    def test_time_conversion_rejects_nan(self) -> None:
        with self.assertRaises(repair_v2.ValidationError) as context:
            repair_v2.java_math_round_scaled(math.nan)
        self.assertEqual(repair_v2.IssueCode.TIME_H_NON_FINITE, context.exception.code)

    def test_time_conversion_rejects_infinity(self) -> None:
        with self.assertRaises(repair_v2.ValidationError) as context:
            repair_v2.java_math_round_scaled(math.inf)
        self.assertEqual(repair_v2.IssueCode.TIME_H_NON_FINITE, context.exception.code)

    def test_time_conversion_accepts_positive_long_near_boundary(self) -> None:
        self.assertEqual(
            9_223_372_036_854_774_784,
            repair_v2.java_math_round_scaled(2_562_047_788_015.2153),
        )

    def test_time_conversion_accepts_negative_long_near_boundary(self) -> None:
        self.assertEqual(
            -9_223_372_036_854_774_784,
            repair_v2.java_math_round_scaled(-2_562_047_788_015.2153),
        )

    def test_time_conversion_rejects_positive_long_overflow(self) -> None:
        with self.assertRaises(repair_v2.ValidationError) as context:
            repair_v2.java_math_round_scaled(2_562_047_788_015.216)
        self.assertEqual(repair_v2.IssueCode.TIME_H_OUT_OF_RANGE, context.exception.code)

    def test_time_conversion_rejects_negative_long_overflow(self) -> None:
        with self.assertRaises(repair_v2.ValidationError) as context:
            repair_v2.java_math_round_scaled(-2_562_047_788_015.216)
        self.assertEqual(repair_v2.IssueCode.TIME_H_OUT_OF_RANGE, context.exception.code)

    def test_time_conversion_rejects_bool(self) -> None:
        with self.assertRaises(repair_v2.ValidationError):
            repair_v2.java_math_round_scaled(True)

    # Legacy timeOfDay parser

    def test_parser_accepts_empty_sql_string(self) -> None:
        self.assertEqual(((), ()), repair_v2.inspect_legacy_time_of_day(""))

    def test_parser_accepts_canonical_time(self) -> None:
        self.assertEqual(("08:30",), repair_v2.inspect_legacy_time_of_day('["08:30"]')[0])

    def test_parser_accepts_zero_seconds(self) -> None:
        self.assertEqual(("08:30",), repair_v2.inspect_legacy_time_of_day('["08:30:00"]')[0])

    def test_parser_accepts_zero_fraction(self) -> None:
        self.assertEqual(("08:30",), repair_v2.inspect_legacy_time_of_day('["08:30:00.000"]')[0])

    def test_parser_preserves_order_and_duplicates(self) -> None:
        canonical, failures = repair_v2.inspect_legacy_time_of_day('["20:00","08:30","20:00"]')
        self.assertEqual(("20:00", "08:30", "20:00"), canonical)
        self.assertEqual((), failures)

    def test_parser_rejects_nonzero_fraction(self) -> None:
        _, failures = repair_v2.inspect_legacy_time_of_day('["08:30:00.500"]')
        self.assertEqual(repair_v2.IssueCode.TIME_OF_DAY_NON_MINUTE, failures[0][0])

    def test_parser_rejects_offset(self) -> None:
        _, failures = repair_v2.inspect_legacy_time_of_day('["08:30+01:00"]')
        self.assertEqual(repair_v2.IssueCode.TIME_OF_DAY_INVALID_LOCAL_TIME, failures[0][0])

    def test_parser_rejects_non_iso_separator(self) -> None:
        _, failures = repair_v2.inspect_legacy_time_of_day('["08.30"]')
        self.assertEqual(repair_v2.IssueCode.TIME_OF_DAY_INVALID_LOCAL_TIME, failures[0][0])

    def test_parser_rejects_empty_element(self) -> None:
        _, failures = repair_v2.inspect_legacy_time_of_day('[""]')
        self.assertEqual(repair_v2.IssueCode.TIME_OF_DAY_INVALID_LOCAL_TIME, failures[0][0])

    def test_parser_rejects_more_than_nine_fraction_digits(self) -> None:
        _, failures = repair_v2.inspect_legacy_time_of_day('["08:30:00.0000000000"]')
        self.assertEqual(repair_v2.IssueCode.TIME_OF_DAY_INVALID_LOCAL_TIME, failures[0][0])

    # Manifest

    def test_manifest_accepts_valid_schema(self) -> None:
        path = self.root / "manifest.json"
        write_manifest(
            path,
            "a" * 64,
            event_corrections={EVENT_ID: {"timeH": 1.0}},
            plan_corrections={PLAN_ID: {"timeOfDay": ["08:30"]}},
        )
        manifest = repair_v2.load_manifest(path)
        self.assertEqual(1.0, manifest.event_corrections[EVENT_ID])
        self.assertEqual(("08:30",), manifest.plan_corrections[PLAN_ID])

    def test_manifest_rejects_sha_mismatch(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        scan = repair_v2.scan_database(database)
        path = self.root / "manifest.json"
        write_manifest(path, "0" * 64, event_corrections={EVENT_ID: {"timeH": 1.0}})
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.validate_manifest_for_scan(repair_v2.load_manifest(path), scan)

    def test_manifest_rejects_duplicate_json_key(self) -> None:
        path = self.root / "manifest.json"
        path.write_text(
            '{"version":1,"version":1,"inputSha256":"' + "a" * 64 + '","eventCorrections":{},"planCorrections":{}}',
            encoding="utf-8",
        )
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.load_manifest(path)

    def test_manifest_rejects_unknown_top_level_field(self) -> None:
        path = self.root / "manifest.json"
        content = {
            "version": 1,
            "inputSha256": "a" * 64,
            "eventCorrections": {},
            "planCorrections": {},
            "unknown": True,
        }
        path.write_text(json.dumps(content), encoding="utf-8")
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.load_manifest(path)

    def test_manifest_rejects_unknown_correction_field(self) -> None:
        path = self.root / "manifest.json"
        write_manifest(path, "a" * 64, event_corrections={EVENT_ID: {"timeH": 1.0, "note": "x"}})
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.load_manifest(path)

    def test_manifest_rejects_invalid_uuid(self) -> None:
        path = self.root / "manifest.json"
        write_manifest(path, "a" * 64, event_corrections={"invalid": {"timeH": 1.0}})
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.load_manifest(path)

    def test_manifest_rejects_bool_time_h(self) -> None:
        path = self.root / "manifest.json"
        write_manifest(path, "a" * 64, event_corrections={EVENT_ID: {"timeH": True}})
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.load_manifest(path)

    def test_manifest_rejects_noncanonical_plan_time(self) -> None:
        path = self.root / "manifest.json"
        write_manifest(path, "a" * 64, plan_corrections={PLAN_ID: {"timeOfDay": ["08:30:00"]}})
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.load_manifest(path)

    def test_manifest_rejects_unknown_event_id(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        scan = repair_v2.scan_database(database)
        manifest = self.manifest_for(
            database,
            event_corrections={EVENT_ID_2: {"timeH": 1.0}},
        )
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.validate_manifest_for_scan(repair_v2.load_manifest(manifest), scan)

    def test_manifest_rejects_missing_issue_correction(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        scan = repair_v2.scan_database(database)
        manifest = self.manifest_for(database)
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.validate_manifest_for_scan(repair_v2.load_manifest(manifest), scan)

    def test_manifest_rejects_nonblocking_correction(self) -> None:
        database = self.database()
        insert_event(database, time_h=1.0)
        scan = repair_v2.scan_database(database)
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 2.0}})
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.validate_manifest_for_scan(repair_v2.load_manifest(manifest), scan)

    def test_manifest_rejects_case_variant_duplicate_uuid(self) -> None:
        path = self.root / "manifest.json"
        alphabetic_uuid = "abcdefab-cdef-abcd-efab-cdefabcdefab"
        content = {
            "version": 1,
            "inputSha256": "a" * 64,
            "eventCorrections": {
                alphabetic_uuid: {"timeH": 1.0},
                alphabetic_uuid.upper(): {"timeH": 2.0},
            },
            "planCorrections": {},
        }
        path.write_text(json.dumps(content), encoding="utf-8")
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.load_manifest(path)

    def test_manifest_rejects_invalid_version_bool(self) -> None:
        path = self.root / "manifest.json"
        content = {
            "version": True,
            "inputSha256": "a" * 64,
            "eventCorrections": {},
            "planCorrections": {},
        }
        path.write_text(json.dumps(content), encoding="utf-8")
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.load_manifest(path)

    # Repair

    def test_preview_is_required_before_repair(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 1.0}})
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.repair_database(database, self.root / "output.db", manifest, "wrong")

    def test_preview_token_is_bound_to_input_and_manifest(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 1.0}})
        preview = repair_v2.preview_repair(database, manifest)
        self.assertEqual(64, len(preview.preview_token))
        self.assertEqual(preview.preview_token, repair_v2.preview_repair(database, manifest).preview_token)

    def test_repair_event_succeeds(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 123.456}})
        output = self.root / "output.db"
        result = self.repair_database(database, output, manifest)
        self.assertEqual((), result.output_scan.issues)
        connection = sqlite3.connect(output)
        try:
            self.assertEqual(123.456, connection.execute("SELECT timeH FROM dose_events").fetchone()[0])
        finally:
            connection.close()

    def test_repair_plan_succeeds(self) -> None:
        database = self.database()
        insert_plan(database, time_of_day='["20:30:15"]')
        manifest = self.manifest_for(
            database,
            plan_corrections={PLAN_ID: {"timeOfDay": ["08:30", "20:00"]}},
        )
        output = self.root / "output.db"
        self.repair_database(database, output, manifest)
        connection = sqlite3.connect(output)
        try:
            self.assertEqual(
                '["08:30","20:00"]',
                connection.execute("SELECT timeOfDay FROM medication_plans").fetchone()[0],
            )
        finally:
            connection.close()

    def test_repair_event_and_plan_succeeds(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        insert_plan(database, time_of_day='["20:30:15"]')
        manifest = self.manifest_for(
            database,
            event_corrections={EVENT_ID: {"timeH": -1.5}},
            plan_corrections={PLAN_ID: {"timeOfDay": ["08:30"]}},
        )
        result = self.repair_database(database, self.root / "output.db", manifest)
        self.assertEqual(1, result.event_correction_count)
        self.assertEqual(1, result.plan_correction_count)

    def test_repair_input_hash_and_mtime_remain_unchanged(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        before_hash = repair_v2.sha256_file(database)
        before_mtime = database.stat().st_mtime_ns
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 1.0}})
        self.repair_database(database, self.root / "output.db", manifest)
        self.assertEqual(before_hash, repair_v2.sha256_file(database))
        self.assertEqual(before_mtime, database.stat().st_mtime_ns)

    def test_repair_output_remains_user_version_two(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 1.0}})
        output = self.root / "output.db"
        self.repair_database(database, output, manifest)
        connection = sqlite3.connect(output)
        try:
            self.assertEqual(2, connection.execute("PRAGMA user_version").fetchone()[0])
        finally:
            connection.close()

    def test_repair_only_changes_target_columns(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf, extras='{"AREA_CM2":1}')
        insert_plan(database, time_of_day='["20:30:15"]', extras='{"AREA_CM2":2}')
        connection = sqlite3.connect(database)
        try:
            event_before = connection.execute(
                "SELECT id, route, doseMG, ester, extras FROM dose_events"
            ).fetchone()
            plan_before = connection.execute(
                "SELECT id, name, route, ester, doseMG, scheduleType, daysOfWeek, "
                "intervalDays, isEnabled, extras, createdAt FROM medication_plans"
            ).fetchone()
        finally:
            connection.close()
        manifest = self.manifest_for(
            database,
            event_corrections={EVENT_ID: {"timeH": 1.0}},
            plan_corrections={PLAN_ID: {"timeOfDay": ["08:30"]}},
        )
        output = self.root / "output.db"
        self.repair_database(database, output, manifest)
        connection = sqlite3.connect(output)
        try:
            self.assertEqual(
                event_before,
                connection.execute("SELECT id, route, doseMG, ester, extras FROM dose_events").fetchone(),
            )
            self.assertEqual(
                plan_before,
                connection.execute(
                    "SELECT id, name, route, ester, doseMG, scheduleType, daysOfWeek, "
                    "intervalDays, isEnabled, extras, createdAt FROM medication_plans"
                ).fetchone(),
            )
        finally:
            connection.close()

    def test_repair_rejects_existing_output(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 1.0}})
        output = self.root / "output.db"
        output.write_bytes(b"synthetic existing output")
        with self.assertRaises(repair_v2.UsageError):
            self.repair_database(database, output, manifest)

    def test_repair_rejects_same_input_output_path(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 1.0}})
        with self.assertRaises(repair_v2.UsageError):
            self.repair_database(database, database, manifest)

    def test_repair_rejects_resolved_same_path_alias(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 1.0}})
        alias = self.root / "missing-parent" / ".." / database.name
        with self.assertRaises(repair_v2.UsageError):
            self.repair_database(database, alias, manifest)

    def test_repair_failure_deletes_output_copy(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        connection = sqlite3.connect(database)
        try:
            connection.execute(
                "CREATE TRIGGER synthetic_block_update BEFORE UPDATE OF timeH ON dose_events "
                "BEGIN SELECT RAISE(ABORT, 'synthetic blocked update'); END"
            )
            connection.commit()
        finally:
            connection.close()
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 1.0}})
        output = self.root / "output.db"
        with self.assertRaises((repair_v2.RepairError, sqlite3.DatabaseError)):
            self.repair_database(database, output, manifest)
        self.assertFalse(output.exists())

    def test_verify_clean_output_succeeds(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 1.0}})
        output = self.root / "output.db"
        self.repair_database(database, output, manifest)
        outcome = repair_v2.verify_command(str(output))
        self.assertEqual(repair_v2.EXIT_OK, outcome.exit_code)

    def test_verify_dirty_database_uses_verify_failure_exit(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        outcome = repair_v2.verify_command(str(database))
        self.assertEqual(repair_v2.EXIT_REPAIR_OR_VERIFY, outcome.exit_code)

    def test_scan_dirty_database_uses_blocking_exit(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        outcome = repair_v2.scan_command(str(database))
        self.assertEqual(repair_v2.EXIT_BLOCKING_DATA, outcome.exit_code)

    def test_unresolved_issue_prevents_output_creation(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        manifest = self.manifest_for(database)
        output = self.root / "output.db"
        with self.assertRaises(repair_v2.UsageError):
            self.repair_database(database, output, manifest)
        self.assertFalse(output.exists())

    def test_repair_preserves_plan_order_and_duplicates(self) -> None:
        database = self.database()
        insert_plan(database, time_of_day='["20:30:15"]')
        replacement = ["20:00", "08:30", "20:00"]
        manifest = self.manifest_for(
            database,
            plan_corrections={PLAN_ID: {"timeOfDay": replacement}},
        )
        output = self.root / "output.db"
        self.repair_database(database, output, manifest)
        connection = sqlite3.connect(output)
        try:
            raw = connection.execute("SELECT timeOfDay FROM medication_plans").fetchone()[0]
        finally:
            connection.close()
        self.assertEqual(replacement, json.loads(raw))

    def test_repair_does_not_create_v3_schema(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 1.0}})
        output = self.root / "output.db"
        self.repair_database(database, output, manifest)
        connection = sqlite3.connect(output)
        try:
            columns = {row[1] for row in connection.execute("PRAGMA table_info(dose_events)")}
            tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        finally:
            connection.close()
        self.assertNotIn("occurredAtEpochMillis", columns)
        self.assertNotIn("scheduled_dose_slots", tables)

    def test_repair_is_idempotent_at_the_semantic_boundary(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        manifest = self.manifest_for(database, event_corrections={EVENT_ID: {"timeH": 1.0}})
        output = self.root / "output.db"
        self.repair_database(database, output, manifest)
        self.assertEqual((), repair_v2.scan_database(output).issues)
        clean_manifest = self.root / "clean-manifest.json"
        write_manifest(clean_manifest, repair_v2.sha256_file(output))
        preview = repair_v2.preview_repair(output, clean_manifest)
        second = self.root / "second.db"
        result = repair_v2.repair_database(output, second, clean_manifest, preview.preview_token)
        self.assertEqual(0, result.event_correction_count)
        self.assertEqual(0, result.plan_correction_count)
        self.assertEqual((), result.output_scan.issues)

    # Audit

    def test_audit_jsonl_is_parseable(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        outcome = repair_v2.scan_command(str(database))
        audit = self.root / "audit.jsonl"
        repair_v2.write_audit(audit, outcome.audit_records)
        records = [json.loads(line) for line in audit.read_text(encoding="utf-8").splitlines()]
        self.assertEqual(2, len(records))

    def test_audit_contains_hash_mode_and_counts(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf)
        outcome = repair_v2.scan_command(str(database))
        summary = outcome.audit_records[-1]
        self.assertEqual("scan", summary["mode"])
        self.assertEqual(repair_v2.sha256_file(database), summary["inputSha256"])
        self.assertEqual(1, summary["issueCount"])

    def test_persistent_output_is_privacy_safe(self) -> None:
        database = self.database()
        insert_plan(database, time_of_day='["20:30:15"]')
        outcome = repair_v2.scan_command(str(database))
        serialized = json.dumps(
            {"summary": outcome.summary, "audit": outcome.audit_records},
            sort_keys=True,
        )
        self.assertNotIn(str(database), serialized)
        self.assertNotIn(PLAN_ID, serialized)
        self.assertNotIn("20:30:15", serialized)
        self.assertIn(hashlib.sha256(PLAN_ID.encode("utf-8")).hexdigest()[:16], serialized)

    def test_audit_does_not_contain_extras(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf, extras='{"private":"synthetic"}')
        serialized = json.dumps(repair_v2.scan_command(str(database)).audit_records)
        self.assertNotIn("private", serialized)

    def test_audit_does_not_contain_dose_mg(self) -> None:
        database = self.database()
        insert_event(database, time_h=math.inf, dose_mg=98765.4321)
        serialized = json.dumps(repair_v2.scan_command(str(database)).audit_records)
        self.assertNotIn("doseMG", serialized)
        self.assertNotIn("98765.4321", serialized)

    def test_audit_does_not_contain_complete_plan_array(self) -> None:
        database = self.database()
        insert_plan(database, time_of_day='["08:30","20:30:15","22:00"]')
        serialized = json.dumps(repair_v2.scan_command(str(database)).audit_records)
        self.assertNotIn('[\\"08:30\\",\\"20:30:15\\",\\"22:00\\"]', serialized)
        self.assertNotIn("20:30:15", serialized)

    def test_audit_refuses_existing_file(self) -> None:
        audit = self.root / "audit.jsonl"
        audit.write_text("synthetic existing audit", encoding="utf-8")
        with self.assertRaises(repair_v2.UsageError):
            repair_v2.resolve_new_file(audit, "audit")

    def test_audit_has_utc_timestamp_and_tool_version(self) -> None:
        database = self.database()
        outcome = repair_v2.scan_command(str(database))
        summary = outcome.audit_records[-1]
        self.assertTrue(summary["timestampUtc"].endswith("Z"))
        self.assertEqual(repair_v2.TOOL_VERSION, summary["toolVersion"])


if __name__ == "__main__":
    unittest.main()
