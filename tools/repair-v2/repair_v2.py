#!/usr/bin/env python3
"""Strict Evolune v2 database scan, repair, and verification tool."""

import argparse
import datetime
import hashlib
import json
import math
import pathlib
import re
import shutil
import sqlite3
import uuid
from dataclasses import dataclass
from enum import Enum
from typing import Any, Iterable, Mapping, Sequence


TOOL_VERSION = "2.0.0"
V2_USER_VERSION = 2
V2_IDENTITY_HASH = "a8036e3f5ed6bb42d0e7289ac84039f3"
MILLIS_PER_HOUR = 3_600_000.0
LONG_MIN = -(2**63)
LONG_MAX_EXCLUSIVE = 2**63

EXIT_OK = 0
EXIT_BLOCKING_DATA = 1
EXIT_USAGE = 2
EXIT_DATABASE_IDENTITY = 3
EXIT_REPAIR_OR_VERIFY = 4
EXIT_INTERNAL = 5

UUID_RE = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)
LEGACY_LOCAL_TIME_RE = re.compile(
    r"^(?P<hour>[01][0-9]|2[0-3]):(?P<minute>[0-5][0-9])"
    r"(?::(?P<second>[0-5][0-9])(?:\.(?P<fraction>[0-9]{1,9}))?)?$"
)
CANONICAL_LOCAL_TIME_RE = re.compile(r"^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")
SHA256_RE = re.compile(r"^[0-9a-fA-F]{64}$")
VALID_ROUTES = {
    "INJECTION", "ORAL", "SUBLINGUAL", "GEL", "PATCH_APPLY", "PATCH_REMOVE",
    "ANTIANDROGEN",
}
VALID_ESTERS = {"E2", "EB", "EV", "EC", "EN"}
VALID_SCHEDULE_TYPES = {"DAILY", "WEEKLY", "CUSTOM"}
VALID_EXTRA_KEYS = {
    "CONCENTRATION_MG_ML", "AREA_CM2", "RELEASE_RATE_UG_PER_DAY",
    "SUBLINGUAL_THETA", "SUBLINGUAL_TIER", "ANTI_ANDROGEN_TYPE",
}


class IssueCode(str, Enum):
    EVENT_ID_INVALID = "EVENT_ID_INVALID"
    PLAN_ID_INVALID = "PLAN_ID_INVALID"
    TIME_H_STORAGE_CLASS = "TIME_H_STORAGE_CLASS"
    TIME_H_NON_FINITE = "TIME_H_NON_FINITE"
    TIME_H_MULTIPLICATION_OVERFLOW = "TIME_H_MULTIPLICATION_OVERFLOW"
    TIME_H_OUT_OF_RANGE = "TIME_H_OUT_OF_RANGE"
    TIME_OF_DAY_STORAGE_CLASS = "TIME_OF_DAY_STORAGE_CLASS"
    TIME_OF_DAY_JSON_MALFORMED = "TIME_OF_DAY_JSON_MALFORMED"
    TIME_OF_DAY_ROOT_NOT_ARRAY = "TIME_OF_DAY_ROOT_NOT_ARRAY"
    TIME_OF_DAY_ELEMENT_NOT_STRING = "TIME_OF_DAY_ELEMENT_NOT_STRING"
    TIME_OF_DAY_INVALID_LOCAL_TIME = "TIME_OF_DAY_INVALID_LOCAL_TIME"
    TIME_OF_DAY_NON_MINUTE = "TIME_OF_DAY_NON_MINUTE"
    INVALID_STORAGE_CLASS = "INVALID_STORAGE_CLASS"
    ROUTE_INVALID = "ROUTE_INVALID"
    ESTER_INVALID = "ESTER_INVALID"
    EXTRAS_JSON_MALFORMED = "EXTRAS_JSON_MALFORMED"
    EXTRA_KEY_INVALID = "EXTRA_KEY_INVALID"
    SCHEDULE_TYPE_INVALID = "SCHEDULE_TYPE_INVALID"
    DAYS_JSON_MALFORMED = "DAYS_JSON_MALFORMED"
    DAY_VALUE_INVALID = "DAY_VALUE_INVALID"
    INTERVAL_INVALID = "INTERVAL_INVALID"
    ENABLED_NONCANONICAL = "ENABLED_NONCANONICAL"


EVENT_TIME_ISSUES = {
    IssueCode.TIME_H_STORAGE_CLASS,
    IssueCode.TIME_H_NON_FINITE,
    IssueCode.TIME_H_MULTIPLICATION_OVERFLOW,
    IssueCode.TIME_H_OUT_OF_RANGE,
}
PLAN_TIME_ISSUES = {
    IssueCode.TIME_OF_DAY_STORAGE_CLASS,
    IssueCode.TIME_OF_DAY_JSON_MALFORMED,
    IssueCode.TIME_OF_DAY_ROOT_NOT_ARRAY,
    IssueCode.TIME_OF_DAY_ELEMENT_NOT_STRING,
    IssueCode.TIME_OF_DAY_INVALID_LOCAL_TIME,
    IssueCode.TIME_OF_DAY_NON_MINUTE,
}


class ToolError(Exception):
    exit_code = EXIT_INTERNAL

    def __init__(self, message: str, details: Mapping[str, Any] | None = None):
        super().__init__(message)
        self.message = message
        self.details = dict(details or {})


class UsageError(ToolError):
    exit_code = EXIT_USAGE


class DatabaseIdentityError(ToolError):
    exit_code = EXIT_DATABASE_IDENTITY


class RepairError(ToolError):
    exit_code = EXIT_REPAIR_OR_VERIFY


class ValidationError(Exception):
    def __init__(self, code: IssueCode, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


class DuplicateKeyError(ValueError):
    pass


@dataclass(frozen=True)
class Issue:
    code: IssueCode
    entity_type: str
    entity_id: str | None
    message: str
    position: int | None = None
    raw_value: Any = None
    field: str | None = None
    repairability: str = "NO_SAFE_AUTOMATIC_REPAIR"

    @property
    def row_fingerprint(self) -> str | None:
        if self.entity_id is None:
            return None
        return hashlib.sha256(self.entity_id.encode("utf-8")).hexdigest()[:16]

    def to_dict(self) -> dict[str, Any]:
        result: dict[str, Any] = {
            "issueCode": self.code.value,
            "entityType": self.entity_type,
            "repairability": self.repairability,
        }
        if self.field is not None:
            result["field"] = self.field
        if self.row_fingerprint is not None:
            result["rowFingerprint"] = self.row_fingerprint
        if self.position is not None:
            result["position"] = self.position
        return result


@dataclass(frozen=True)
class DatabaseIdentity:
    user_version: int
    identity_hash: str


@dataclass(frozen=True)
class ScanResult:
    input_path: pathlib.Path
    input_sha256: str
    identity: DatabaseIdentity
    issues: tuple[Issue, ...]
    event_ids: Mapping[str, str]
    plan_ids: Mapping[str, str]


@dataclass(frozen=True)
class CorrectionManifest:
    input_sha256: str
    event_corrections: Mapping[str, float]
    plan_corrections: Mapping[str, tuple[str, ...]]


@dataclass(frozen=True)
class RepairResult:
    input_scan: ScanResult
    output_scan: ScanResult
    output_path: pathlib.Path
    output_sha256: str
    event_correction_count: int
    plan_correction_count: int


@dataclass(frozen=True)
class RepairPreview:
    scan: ScanResult
    manifest: CorrectionManifest
    preview_token: str


def preview_token(input_sha256: str, manifest_sha256: str) -> str:
    payload = f"{TOOL_VERSION}\0{input_sha256}\0{manifest_sha256}".encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


@dataclass(frozen=True)
class CommandOutcome:
    exit_code: int
    summary: Mapping[str, Any]
    audit_records: Sequence[Mapping[str, Any]]


def canonical_uuid(value: Any, field_name: str) -> str:
    if not isinstance(value, str) or UUID_RE.fullmatch(value) is None:
        raise UsageError(f"{field_name} must be a standard UUID string")
    try:
        canonical = str(uuid.UUID(value))
    except ValueError as error:
        raise UsageError(f"{field_name} must be a standard UUID string") from error
    if value != canonical:
        raise UsageError(f"{field_name} must be a canonical lowercase UUID string")
    return canonical


def utc_timestamp() -> str:
    return datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z")


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while True:
            block = source.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def database_sidecars(path: pathlib.Path) -> tuple[pathlib.Path, ...]:
    return tuple(pathlib.Path(f"{path}{suffix}") for suffix in ("-wal", "-shm", "-journal"))


def reject_active_database_sidecars(path: pathlib.Path) -> None:
    active = [str(sidecar) for sidecar in database_sidecars(path) if sidecar.exists() and sidecar.stat().st_size > 0]
    if active:
        raise UsageError(
            "input has an active SQLite sidecar; create a cleanly closed SQLite snapshot first",
            {"sidecarCount": len(active)},
        )


def resolve_input_file(value: str | pathlib.Path, label: str = "input") -> pathlib.Path:
    candidate = pathlib.Path(value)
    if candidate.is_symlink():
        raise UsageError(f"{label} must not be a symbolic link")
    try:
        resolved = candidate.resolve(strict=True)
    except (FileNotFoundError, OSError) as error:
        raise UsageError(f"{label} file does not exist: {candidate}") from error
    if not resolved.is_file():
        raise UsageError(f"{label} must be a regular file: {resolved}")
    return resolved


def resolve_new_file(value: str | pathlib.Path, label: str) -> pathlib.Path:
    candidate = pathlib.Path(value)
    if candidate.exists() or candidate.is_symlink():
        raise UsageError(f"{label} already exists: {candidate}")
    try:
        resolved = candidate.resolve(strict=False)
    except OSError as error:
        raise UsageError(f"cannot resolve {label} path: {candidate}") from error
    parent = resolved.parent
    if not parent.exists() or not parent.is_dir():
        raise UsageError(f"{label} parent directory does not exist: {parent}")
    return resolved


def ensure_distinct_paths(paths: Mapping[str, pathlib.Path]) -> None:
    seen: dict[pathlib.Path, str] = {}
    for label, path in paths.items():
        previous = seen.get(path)
        if previous is not None:
            raise UsageError(f"{label} must use a different path from {previous}")
        seen[path] = label


def open_read_only_database(path: pathlib.Path) -> sqlite3.Connection:
    uri = f"{path.as_uri()}?mode=ro"
    try:
        connection = sqlite3.connect(uri, uri=True)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA query_only = ON")
        return connection
    except sqlite3.Error as error:
        raise DatabaseIdentityError(f"cannot open SQLite database read-only: {error}") from error


def table_names(connection: sqlite3.Connection) -> set[str]:
    rows = connection.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table'"
    ).fetchall()
    return {str(row[0]) for row in rows}


def table_columns(connection: sqlite3.Connection, table_name: str) -> set[str]:
    rows = connection.execute(f"PRAGMA table_info({table_name})").fetchall()
    return {str(row[1]) for row in rows}


def validate_database_identity(connection: sqlite3.Connection) -> DatabaseIdentity:
    try:
        integrity = connection.execute("PRAGMA quick_check").fetchone()
        if integrity is None or integrity[0] != "ok":
            raise DatabaseIdentityError("SQLite quick_check did not return ok")

        user_version_row = connection.execute("PRAGMA user_version").fetchone()
        user_version = int(user_version_row[0]) if user_version_row is not None else -1
        if user_version != V2_USER_VERSION:
            raise DatabaseIdentityError(
                f"expected Evolune database user_version 2, found {user_version}",
                {"userVersion": user_version},
            )

        required_tables = {"dose_events", "medication_plans", "room_master_table"}
        missing_tables = sorted(required_tables - table_names(connection))
        if missing_tables:
            raise DatabaseIdentityError(
                "database is missing required Evolune v2 tables",
                {"missingTables": missing_tables, "userVersion": user_version},
            )

        required_columns = {
            "dose_events": {"id", "timeH"},
            "medication_plans": {"id", "timeOfDay"},
        }
        for table_name, columns in required_columns.items():
            missing_columns = sorted(columns - table_columns(connection, table_name))
            if missing_columns:
                raise DatabaseIdentityError(
                    f"database table {table_name} is missing required columns",
                    {
                        "table": table_name,
                        "missingColumns": missing_columns,
                        "userVersion": user_version,
                    },
                )

        identity_row = connection.execute(
            "SELECT identity_hash FROM room_master_table WHERE id = 42"
        ).fetchone()
        identity_hash = str(identity_row[0]) if identity_row is not None else ""
        if identity_hash != V2_IDENTITY_HASH:
            raise DatabaseIdentityError(
                "database does not have the known Evolune Room v2 identity hash",
                {"identityHash": identity_hash, "userVersion": user_version},
            )
        return DatabaseIdentity(user_version=user_version, identity_hash=identity_hash)
    except DatabaseIdentityError:
        raise
    except sqlite3.DatabaseError as error:
        raise DatabaseIdentityError(f"invalid or unreadable SQLite database: {error}") from error


def java_math_round_scaled(value: Any) -> int:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValidationError(IssueCode.TIME_H_STORAGE_CLASS, "timeH must be a numeric value")
    try:
        binary64 = float(value)
    except (OverflowError, ValueError) as error:
        raise ValidationError(IssueCode.TIME_H_OUT_OF_RANGE, "timeH cannot be represented as binary64") from error
    if not math.isfinite(binary64):
        raise ValidationError(IssueCode.TIME_H_NON_FINITE, "timeH must be finite")
    scaled = binary64 * MILLIS_PER_HOUR
    if not math.isfinite(scaled):
        raise ValidationError(
            IssueCode.TIME_H_MULTIPLICATION_OVERFLOW,
            "timeH multiplication by 3,600,000.0 overflowed",
        )
    if scaled < LONG_MIN or scaled >= LONG_MAX_EXCLUSIVE:
        raise ValidationError(
            IssueCode.TIME_H_OUT_OF_RANGE,
            "timeH maps outside [-2^63, 2^63)",
        )
    rounded = math.floor(scaled + 0.5)
    if rounded < LONG_MIN or rounded >= LONG_MAX_EXCLUSIVE:
        raise ValidationError(
            IssueCode.TIME_H_OUT_OF_RANGE,
            "rounded timeH maps outside [-2^63, 2^63)",
        )
    return int(rounded)


def validate_persisted_time_h(storage_class: str, value: Any) -> int:
    if storage_class not in {"integer", "real"}:
        raise ValidationError(
            IssueCode.TIME_H_STORAGE_CLASS,
            f"timeH SQLite storage class must be integer or real, found {storage_class}",
        )
    return java_math_round_scaled(value)


def is_canonical_uuid(value: Any) -> bool:
    if not isinstance(value, str) or UUID_RE.fullmatch(value) is None:
        return False
    try:
        return str(uuid.UUID(value)) == value
    except ValueError:
        return False


def parse_json(value: str) -> Any:
    return json.loads(
        value,
        parse_constant=lambda token: (_ for _ in ()).throw(ValueError(token)),
    )


def inspect_extras(raw_value: Any) -> tuple[IssueCode | None, str]:
    if not isinstance(raw_value, str):
        return IssueCode.INVALID_STORAGE_CLASS, "extras"
    if raw_value == "":
        return None, "extras"
    try:
        decoded = parse_json(raw_value)
    except (json.JSONDecodeError, ValueError):
        return IssueCode.EXTRAS_JSON_MALFORMED, "extras"
    if not isinstance(decoded, dict):
        return IssueCode.EXTRAS_JSON_MALFORMED, "extras"
    for key, value in decoded.items():
        if key not in VALID_EXTRA_KEYS:
            return IssueCode.EXTRA_KEY_INVALID, "extras"
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            return IssueCode.EXTRAS_JSON_MALFORMED, "extras"
    return None, "extras"


def inspect_days(raw_value: Any) -> IssueCode | None:
    if not isinstance(raw_value, str):
        return IssueCode.INVALID_STORAGE_CLASS
    if raw_value == "":
        return None
    try:
        decoded = parse_json(raw_value)
    except (json.JSONDecodeError, ValueError):
        return IssueCode.DAYS_JSON_MALFORMED
    if not isinstance(decoded, list):
        return IssueCode.DAYS_JSON_MALFORMED
    if any(isinstance(value, bool) or not isinstance(value, int) for value in decoded):
        return IssueCode.DAYS_JSON_MALFORMED
    if any(value not in range(1, 8) for value in decoded):
        return IssueCode.DAY_VALUE_INVALID
    return None


def issue(
    code: IssueCode,
    entity_type: str,
    entity_id: str | None,
    field: str,
    message: str,
    *,
    position: int | None = None,
    raw_value: Any = None,
    repairability: str = "NO_SAFE_AUTOMATIC_REPAIR",
) -> Issue:
    return Issue(
        code=code,
        entity_type=entity_type,
        entity_id=entity_id,
        message=message,
        position=position,
        raw_value=raw_value,
        field=field,
        repairability=repairability,
    )


def inspect_legacy_time_of_day(raw_value: Any) -> tuple[tuple[str, ...], tuple[tuple[IssueCode, int | None, Any, str], ...]]:
    if not isinstance(raw_value, str):
        failure = (
            IssueCode.TIME_OF_DAY_STORAGE_CLASS,
            None,
            raw_value,
            "timeOfDay SQLite storage class must be text",
        )
        return (), (failure,)
    if raw_value == "":
        return (), ()
    try:
        decoded = json.loads(raw_value, parse_constant=lambda token: (_ for _ in ()).throw(ValueError(token)))
    except (json.JSONDecodeError, ValueError) as error:
        failure = (
            IssueCode.TIME_OF_DAY_JSON_MALFORMED,
            None,
            None,
            f"timeOfDay is not valid JSON: {error}",
        )
        return (), (failure,)
    if not isinstance(decoded, list):
        failure = (
            IssueCode.TIME_OF_DAY_ROOT_NOT_ARRAY,
            None,
            None,
            "timeOfDay JSON root must be an array",
        )
        return (), (failure,)

    canonical: list[str] = []
    failures: list[tuple[IssueCode, int | None, Any, str]] = []
    for position, item in enumerate(decoded):
        if not isinstance(item, str):
            failures.append(
                (
                    IssueCode.TIME_OF_DAY_ELEMENT_NOT_STRING,
                    position,
                    item,
                    "timeOfDay element must be a string",
                )
            )
            continue
        match = LEGACY_LOCAL_TIME_RE.fullmatch(item)
        if match is None:
            failures.append(
                (
                    IssueCode.TIME_OF_DAY_INVALID_LOCAL_TIME,
                    position,
                    item,
                    "timeOfDay element must use strict ISO LocalTime syntax",
                )
            )
            continue
        second = match.group("second")
        fraction = match.group("fraction")
        if (second is not None and second != "00") or (
            fraction is not None and any(character != "0" for character in fraction)
        ):
            failures.append(
                (
                    IssueCode.TIME_OF_DAY_NON_MINUTE,
                    position,
                    item,
                    "timeOfDay element must have zero seconds and zero nanoseconds",
                )
            )
            continue
        canonical.append(f"{match.group('hour')}:{match.group('minute')}")
    return tuple(canonical), tuple(failures)


def scan_connection(connection: sqlite3.Connection) -> tuple[tuple[Issue, ...], dict[str, str], dict[str, str]]:
    issues: list[Issue] = []
    event_ids: dict[str, str] = {}
    plan_ids: dict[str, str] = {}

    event_rows = connection.execute(
        "SELECT id, typeof(id) AS idStorage, route, typeof(route) AS routeStorage, "
        "timeH, typeof(timeH) AS timeStorage, doseMG, typeof(doseMG) AS doseStorage, "
        "ester, typeof(ester) AS esterStorage, extras, typeof(extras) AS extrasStorage "
        "FROM dose_events ORDER BY id"
    ).fetchall()
    for row in event_rows:
        raw_id = row["id"]
        event_id = raw_id if isinstance(raw_id, str) else None
        if row["idStorage"] != "text" or not is_canonical_uuid(event_id):
            issues.append(
                issue(
                    IssueCode.EVENT_ID_INVALID,
                    "event",
                    event_id,
                    "id",
                    "event id is not a standard UUID",
                    raw_value=raw_id,
                )
            )
        else:
            event_ids[event_id.lower()] = event_id
        if row["routeStorage"] != "text":
            issues.append(issue(IssueCode.INVALID_STORAGE_CLASS, "event", event_id, "route", "route must use TEXT storage"))
        elif row["route"] not in VALID_ROUTES:
            issues.append(issue(IssueCode.ROUTE_INVALID, "event", event_id, "route", "route is not supported"))
        try:
            validate_persisted_time_h(str(row["timeStorage"]), row["timeH"])
        except ValidationError as error:
            issues.append(
                issue(
                    error.code,
                    "event",
                    event_id,
                    "timeH",
                    error.message,
                    raw_value=row["timeH"],
                    repairability="OPERATOR_MANIFEST_REQUIRED",
                )
            )
        if row["doseStorage"] not in {"integer", "real"}:
            issues.append(issue(IssueCode.INVALID_STORAGE_CLASS, "event", event_id, "doseMG", "doseMG must use numeric storage"))
        if row["esterStorage"] != "text":
            issues.append(issue(IssueCode.INVALID_STORAGE_CLASS, "event", event_id, "ester", "ester must use TEXT storage"))
        elif row["ester"] not in VALID_ESTERS:
            issues.append(issue(IssueCode.ESTER_INVALID, "event", event_id, "ester", "ester is not supported"))
        extras_code, extras_field = inspect_extras(row["extras"])
        if row["extrasStorage"] != "text":
            extras_code = IssueCode.INVALID_STORAGE_CLASS
        if extras_code is not None:
            issues.append(issue(extras_code, "event", event_id, extras_field, "extras do not satisfy the persisted contract"))

    plan_rows = connection.execute(
        "SELECT id, typeof(id) AS idStorage, name, typeof(name) AS nameStorage, "
        "route, typeof(route) AS routeStorage, ester, typeof(ester) AS esterStorage, "
        "doseMG, typeof(doseMG) AS doseStorage, scheduleType, typeof(scheduleType) AS scheduleStorage, "
        "timeOfDay, typeof(timeOfDay) AS timeStorage, daysOfWeek, typeof(daysOfWeek) AS daysStorage, "
        "intervalDays, typeof(intervalDays) AS intervalStorage, isEnabled, typeof(isEnabled) AS enabledStorage, "
        "extras, typeof(extras) AS extrasStorage, createdAt, typeof(createdAt) AS createdStorage "
        "FROM medication_plans ORDER BY id"
    ).fetchall()
    for row in plan_rows:
        raw_id = row["id"]
        plan_id = raw_id if isinstance(raw_id, str) else None
        if row["idStorage"] != "text" or not is_canonical_uuid(plan_id):
            issues.append(
                issue(
                    IssueCode.PLAN_ID_INVALID,
                    "plan",
                    plan_id,
                    "id",
                    "plan id is not a standard UUID",
                    raw_value=raw_id,
                )
            )
        else:
            plan_ids[plan_id.lower()] = plan_id

        if row["nameStorage"] != "text":
            issues.append(issue(IssueCode.INVALID_STORAGE_CLASS, "plan", plan_id, "name", "name must use TEXT storage"))
        if row["routeStorage"] != "text":
            issues.append(issue(IssueCode.INVALID_STORAGE_CLASS, "plan", plan_id, "route", "route must use TEXT storage"))
        elif row["route"] not in VALID_ROUTES:
            issues.append(issue(IssueCode.ROUTE_INVALID, "plan", plan_id, "route", "route is not supported"))
        if row["esterStorage"] != "text":
            issues.append(issue(IssueCode.INVALID_STORAGE_CLASS, "plan", plan_id, "ester", "ester must use TEXT storage"))
        elif row["ester"] not in VALID_ESTERS:
            issues.append(issue(IssueCode.ESTER_INVALID, "plan", plan_id, "ester", "ester is not supported"))
        if row["doseStorage"] not in {"integer", "real"}:
            issues.append(issue(IssueCode.INVALID_STORAGE_CLASS, "plan", plan_id, "doseMG", "doseMG must use numeric storage"))
        if row["scheduleStorage"] != "text":
            issues.append(issue(IssueCode.INVALID_STORAGE_CLASS, "plan", plan_id, "scheduleType", "scheduleType must use TEXT storage"))
        elif row["scheduleType"] not in VALID_SCHEDULE_TYPES:
            issues.append(issue(IssueCode.SCHEDULE_TYPE_INVALID, "plan", plan_id, "scheduleType", "scheduleType is not supported"))

        storage_class = str(row["timeStorage"])
        if storage_class != "text":
            issues.append(
                issue(
                    IssueCode.TIME_OF_DAY_STORAGE_CLASS,
                    "plan",
                    plan_id,
                    "timeOfDay",
                    f"timeOfDay SQLite storage class must be text, found {storage_class}",
                    raw_value=row["timeOfDay"],
                    repairability="OPERATOR_MANIFEST_REQUIRED",
                )
            )
        else:
            _, failures = inspect_legacy_time_of_day(row["timeOfDay"])
            for code, position, raw_value, message in failures:
                issues.append(issue(code, "plan", plan_id, "timeOfDay", message, position=position, raw_value=raw_value, repairability="OPERATOR_MANIFEST_REQUIRED"))

        days_code = inspect_days(row["daysOfWeek"])
        if row["daysStorage"] != "text":
            days_code = IssueCode.INVALID_STORAGE_CLASS
        if days_code is not None:
            issues.append(issue(days_code, "plan", plan_id, "daysOfWeek", "daysOfWeek do not satisfy the persisted contract"))
        if row["intervalStorage"] != "integer":
            issues.append(issue(IssueCode.INVALID_STORAGE_CLASS, "plan", plan_id, "intervalDays", "intervalDays must use INTEGER storage"))
        elif not (1 <= int(row["intervalDays"]) <= 2**31 - 1):
            issues.append(issue(IssueCode.INTERVAL_INVALID, "plan", plan_id, "intervalDays", "intervalDays must be in 1..Int.MAX_VALUE"))
        if row["enabledStorage"] != "integer":
            issues.append(issue(IssueCode.INVALID_STORAGE_CLASS, "plan", plan_id, "isEnabled", "isEnabled must use INTEGER storage"))
        elif int(row["isEnabled"]) not in {0, 1}:
            issues.append(issue(IssueCode.ENABLED_NONCANONICAL, "plan", plan_id, "isEnabled", "isEnabled must be 0 or 1"))
        extras_code, extras_field = inspect_extras(row["extras"])
        if row["extrasStorage"] != "text":
            extras_code = IssueCode.INVALID_STORAGE_CLASS
        if extras_code is not None:
            issues.append(issue(extras_code, "plan", plan_id, extras_field, "extras do not satisfy the persisted contract"))
        if row["createdStorage"] != "integer":
            issues.append(issue(IssueCode.INVALID_STORAGE_CLASS, "plan", plan_id, "createdAt", "createdAt must use INTEGER storage"))

    return tuple(issues), event_ids, plan_ids


def scan_database(value: str | pathlib.Path) -> ScanResult:
    input_path = resolve_input_file(value)
    reject_active_database_sidecars(input_path)
    before_stat = input_path.stat()
    before_hash = sha256_file(input_path)
    connection: sqlite3.Connection | None = None
    try:
        connection = open_read_only_database(input_path)
        identity = validate_database_identity(connection)
        issues, event_ids, plan_ids = scan_connection(connection)
    finally:
        if connection is not None:
            connection.close()
    after_stat = input_path.stat()
    after_hash = sha256_file(input_path)
    if (
        before_hash != after_hash
        or before_stat.st_size != after_stat.st_size
        or before_stat.st_mtime_ns != after_stat.st_mtime_ns
    ):
        raise RepairError("input database changed while it was being scanned")
    return ScanResult(
        input_path=input_path,
        input_sha256=before_hash,
        identity=identity,
        issues=issues,
        event_ids=event_ids,
        plan_ids=plan_ids,
    )


def duplicate_key_object(pairs: Iterable[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateKeyError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def reject_json_constant(token: str) -> Any:
    raise ValueError(f"non-standard JSON number: {token}")


def require_exact_fields(value: Mapping[str, Any], required: set[str], context: str) -> None:
    actual = set(value.keys())
    unknown = sorted(actual - required)
    missing = sorted(required - actual)
    if unknown or missing:
        details: dict[str, Any] = {}
        if unknown:
            details["unknownFields"] = unknown
        if missing:
            details["missingFields"] = missing
        raise UsageError(f"{context} fields do not match version 1 schema", details)


def load_manifest(value: str | pathlib.Path) -> CorrectionManifest:
    path = resolve_input_file(value, "manifest")
    try:
        decoded = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=duplicate_key_object,
            parse_constant=reject_json_constant,
        )
    except (OSError, UnicodeError, json.JSONDecodeError, DuplicateKeyError, ValueError) as error:
        raise UsageError(f"cannot parse correction manifest: {error}") from error
    if not isinstance(decoded, dict):
        raise UsageError("correction manifest root must be an object")
    require_exact_fields(
        decoded,
        {"version", "inputSha256", "eventCorrections", "planCorrections"},
        "manifest",
    )
    if isinstance(decoded["version"], bool) or decoded["version"] != 1:
        raise UsageError("manifest version must be integer 1")
    input_sha256 = decoded["inputSha256"]
    if not isinstance(input_sha256, str) or SHA256_RE.fullmatch(input_sha256) is None:
        raise UsageError("manifest inputSha256 must be exactly 64 hexadecimal characters")

    raw_event_corrections = decoded["eventCorrections"]
    raw_plan_corrections = decoded["planCorrections"]
    if not isinstance(raw_event_corrections, dict):
        raise UsageError("eventCorrections must be an object")
    if not isinstance(raw_plan_corrections, dict):
        raise UsageError("planCorrections must be an object")

    event_corrections: dict[str, float] = {}
    for raw_id, correction in raw_event_corrections.items():
        event_id = canonical_uuid(raw_id, "event correction id")
        if event_id in event_corrections:
            raise UsageError("eventCorrections defines the same UUID more than once")
        if not isinstance(correction, dict):
            raise UsageError(f"event correction {raw_id} must be an object")
        require_exact_fields(correction, {"timeH"}, f"event correction {raw_id}")
        time_h = correction["timeH"]
        if isinstance(time_h, bool) or not isinstance(time_h, (int, float)):
            raise UsageError(f"event correction {raw_id} timeH must be a JSON number")
        try:
            java_math_round_scaled(time_h)
            event_corrections[event_id] = float(time_h)
        except ValidationError as error:
            raise UsageError(f"event correction {raw_id} has invalid timeH: {error.message}") from error
        except OverflowError as error:
            raise UsageError(f"event correction {raw_id} timeH cannot be represented as binary64") from error

    plan_corrections: dict[str, tuple[str, ...]] = {}
    for raw_id, correction in raw_plan_corrections.items():
        plan_id = canonical_uuid(raw_id, "plan correction id")
        if plan_id in plan_corrections:
            raise UsageError("planCorrections defines the same UUID more than once")
        if not isinstance(correction, dict):
            raise UsageError(f"plan correction {raw_id} must be an object")
        require_exact_fields(correction, {"timeOfDay"}, f"plan correction {raw_id}")
        time_values = correction["timeOfDay"]
        if not isinstance(time_values, list):
            raise UsageError(f"plan correction {raw_id} timeOfDay must be an array")
        canonical_values: list[str] = []
        for position, item in enumerate(time_values):
            if not isinstance(item, str) or CANONICAL_LOCAL_TIME_RE.fullmatch(item) is None:
                raise UsageError(
                    f"plan correction {raw_id} position {position} must already be canonical HH:mm"
                )
            canonical_values.append(item)
        plan_corrections[plan_id] = tuple(canonical_values)

    return CorrectionManifest(
        input_sha256=input_sha256.lower(),
        event_corrections=event_corrections,
        plan_corrections=plan_corrections,
    )


def validate_manifest_for_scan(manifest: CorrectionManifest, scan: ScanResult) -> None:
    if manifest.input_sha256 != scan.input_sha256:
        raise UsageError(
            "manifest inputSha256 does not match the input database",
            {"expected": scan.input_sha256, "actual": manifest.input_sha256},
        )

    event_issue_ids = {
        issue.entity_id.lower()
        for issue in scan.issues
        if issue.code in EVENT_TIME_ISSUES and issue.entity_id is not None and UUID_RE.fullmatch(issue.entity_id)
    }
    plan_issue_ids = {
        issue.entity_id.lower()
        for issue in scan.issues
        if issue.code in PLAN_TIME_ISSUES and issue.entity_id is not None and UUID_RE.fullmatch(issue.entity_id)
    }
    repairable_codes = EVENT_TIME_ISSUES | PLAN_TIME_ISSUES
    uncorrectable = [
        issue.to_dict()
        for issue in scan.issues
        if issue.code not in repairable_codes
    ]
    if uncorrectable:
        raise UsageError(
            "database contains issues with no safe automatic repair",
            {"issueCounts": issue_counts(tuple(issue for issue in scan.issues if issue.code not in repairable_codes))},
        )

    unknown_events = sorted(set(manifest.event_corrections) - set(scan.event_ids))
    unknown_plans = sorted(set(manifest.plan_corrections) - set(scan.plan_ids))
    if unknown_events or unknown_plans:
        raise UsageError(
            "manifest references IDs that do not exist in the input database",
            {"unknownEventIds": unknown_events, "unknownPlanIds": unknown_plans},
        )

    non_blocking_events = sorted(set(manifest.event_corrections) - event_issue_ids)
    non_blocking_plans = sorted(set(manifest.plan_corrections) - plan_issue_ids)
    if non_blocking_events or non_blocking_plans:
        raise UsageError(
            "manifest may only correct rows with blocking time data",
            {
                "nonBlockingEventIds": non_blocking_events,
                "nonBlockingPlanIds": non_blocking_plans,
            },
        )

    missing_events = sorted(event_issue_ids - set(manifest.event_corrections))
    missing_plans = sorted(plan_issue_ids - set(manifest.plan_corrections))
    if missing_events or missing_plans:
        raise UsageError(
            "manifest does not cover every blocking time issue",
            {"missingEventIds": missing_events, "missingPlanIds": missing_plans},
        )


def preview_repair(
    input_value: str | pathlib.Path,
    manifest_value: str | pathlib.Path,
) -> RepairPreview:
    input_path = resolve_input_file(input_value)
    reject_active_database_sidecars(input_path)
    manifest_path = resolve_input_file(manifest_value, "manifest")
    ensure_distinct_paths({"input": input_path, "manifest": manifest_path})
    scan = scan_database(input_path)
    manifest = load_manifest(manifest_path)
    validate_manifest_for_scan(manifest, scan)
    return RepairPreview(
        scan=scan,
        manifest=manifest,
        preview_token=preview_token(scan.input_sha256, sha256_file(manifest_path)),
    )


def compact_time_array(values: Sequence[str]) -> str:
    return json.dumps(list(values), ensure_ascii=True, separators=(",", ":"))


def remove_database_copy(path: pathlib.Path) -> None:
    for candidate in (
        path,
        pathlib.Path(f"{path}-wal"),
        pathlib.Path(f"{path}-shm"),
        pathlib.Path(f"{path}-journal"),
    ):
        try:
            if candidate.exists() or candidate.is_symlink():
                candidate.unlink()
        except OSError:
            pass


def repair_database(
    input_value: str | pathlib.Path,
    output_value: str | pathlib.Path,
    manifest_value: str | pathlib.Path,
    supplied_preview_token: str,
) -> RepairResult:
    input_path = resolve_input_file(input_value)
    reject_active_database_sidecars(input_path)
    output_path = resolve_new_file(output_value, "output")
    manifest_path = resolve_input_file(manifest_value, "manifest")
    ensure_distinct_paths({"input": input_path, "output": output_path, "manifest": manifest_path})

    before_stat = input_path.stat()
    preview = preview_repair(input_path, manifest_path)
    input_scan = preview.scan
    manifest = preview.manifest
    if supplied_preview_token != preview.preview_token:
        raise UsageError("repair requires the exact token produced by the preview command")
    if sha256_file(input_path) != input_scan.input_sha256:
        raise RepairError("input database changed before it could be copied")

    copied = False
    connection: sqlite3.Connection | None = None
    try:
        shutil.copy2(input_path, output_path)
        copied = True
        if sha256_file(output_path) != input_scan.input_sha256:
            raise RepairError("copied output hash does not match the input hash")
        if sha256_file(input_path) != input_scan.input_sha256:
            raise RepairError("input database changed during copy")

        connection = sqlite3.connect(output_path)
        connection.row_factory = sqlite3.Row
        validate_database_identity(connection)
        connection.execute("BEGIN IMMEDIATE")

        for canonical_id, time_h in manifest.event_corrections.items():
            actual_id = input_scan.event_ids[canonical_id]
            cursor = connection.execute(
                "UPDATE dose_events SET timeH = ? WHERE id = ?",
                (time_h, actual_id),
            )
            if cursor.rowcount != 1:
                raise RepairError(
                    "event correction did not update exactly one row",
                    {"eventId": actual_id, "rowCount": cursor.rowcount},
                )

        for canonical_id, time_values in manifest.plan_corrections.items():
            actual_id = input_scan.plan_ids[canonical_id]
            cursor = connection.execute(
                "UPDATE medication_plans SET timeOfDay = ? WHERE id = ?",
                (compact_time_array(time_values), actual_id),
            )
            if cursor.rowcount != 1:
                raise RepairError(
                    "plan correction did not update exactly one row",
                    {"planId": actual_id, "rowCount": cursor.rowcount},
                )

        validate_database_identity(connection)
        remaining_issues, _, _ = scan_connection(connection)
        if remaining_issues:
            raise RepairError(
                "repaired output still contains blocking migration issues",
                {"issues": [issue.to_dict() for issue in remaining_issues]},
            )
        connection.commit()
        journal_mode_row = connection.execute("PRAGMA journal_mode").fetchone()
        if journal_mode_row is not None and str(journal_mode_row[0]).lower() == "wal":
            connection.execute("PRAGMA wal_checkpoint(FULL)").fetchall()
        connection.close()
        connection = None

        for sidecar in (
            pathlib.Path(f"{output_path}-wal"),
            pathlib.Path(f"{output_path}-journal"),
        ):
            if sidecar.exists() and sidecar.stat().st_size > 0:
                raise RepairError("output still has a non-empty SQLite sidecar after repair", {"sidecar": str(sidecar)})
        for sidecar in (
            pathlib.Path(f"{output_path}-wal"),
            pathlib.Path(f"{output_path}-shm"),
            pathlib.Path(f"{output_path}-journal"),
        ):
            if sidecar.exists():
                sidecar.unlink()

        output_scan = scan_database(output_path)
        if output_scan.issues:
            raise RepairError("final read-only verification found blocking issues")
        after_stat = input_path.stat()
        if (
            sha256_file(input_path) != input_scan.input_sha256
            or before_stat.st_size != after_stat.st_size
            or before_stat.st_mtime_ns != after_stat.st_mtime_ns
        ):
            raise RepairError("input database content or mtime changed during repair")
        output_sha256 = sha256_file(output_path)
        return RepairResult(
            input_scan=input_scan,
            output_scan=output_scan,
            output_path=output_path,
            output_sha256=output_sha256,
            event_correction_count=len(manifest.event_corrections),
            plan_correction_count=len(manifest.plan_corrections),
        )
    except Exception as error:
        if connection is not None:
            try:
                connection.rollback()
            except sqlite3.Error:
                pass
            connection.close()
        if copied:
            remove_database_copy(output_path)
        if isinstance(error, sqlite3.Error):
            raise RepairError(f"SQLite repair failed: {error}") from error
        raise


def issue_counts(issues: Sequence[Issue]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for issue in issues:
        counts[issue.code.value] = counts.get(issue.code.value, 0) + 1
    return dict(sorted(counts.items()))


def base_summary(mode: str, scan: ScanResult, exit_code: int, success: bool) -> dict[str, Any]:
    return {
        "toolVersion": TOOL_VERSION,
        "mode": mode,
        "success": success,
        "exitCode": exit_code,
        "inputSha256": scan.input_sha256,
        "userVersion": scan.identity.user_version,
        "identityHash": scan.identity.identity_hash,
        "issueCount": len(scan.issues),
        "issueCounts": issue_counts(scan.issues),
        "issues": [issue.to_dict() for issue in scan.issues],
    }


def audit_records_for_scan(
    mode: str,
    scan: ScanResult,
    exit_code: int,
    success: bool,
    output_sha256: str | None = None,
    correction_counts: Mapping[str, int] | None = None,
) -> list[Mapping[str, Any]]:
    timestamp = utc_timestamp()
    records: list[Mapping[str, Any]] = []
    for issue in scan.issues:
        record = {
            "timestampUtc": timestamp,
            "toolVersion": TOOL_VERSION,
            "recordType": "issue",
            "mode": mode,
            "inputSha256": scan.input_sha256,
            "userVersion": scan.identity.user_version,
            **issue.to_dict(),
        }
        records.append(record)
    summary: dict[str, Any] = {
        "timestampUtc": timestamp,
        "toolVersion": TOOL_VERSION,
        "recordType": "summary",
        "mode": mode,
        "inputSha256": scan.input_sha256,
        "userVersion": scan.identity.user_version,
        "issueCount": len(scan.issues),
        "issueCounts": issue_counts(scan.issues),
        "correctionCounts": dict(correction_counts or {"events": 0, "plans": 0}),
        "success": success,
        "exitCode": exit_code,
    }
    if output_sha256 is not None:
        summary["outputSha256"] = output_sha256
    records.append(summary)
    return records


def write_audit(path: pathlib.Path, records: Sequence[Mapping[str, Any]]) -> None:
    try:
        with path.open("x", encoding="utf-8", newline="\n") as destination:
            for record in records:
                destination.write(json.dumps(record, ensure_ascii=False, sort_keys=True, allow_nan=False))
                destination.write("\n")
    except (OSError, ValueError) as error:
        raise UsageError(f"cannot write audit JSONL: {error}") from error


def scan_command(input_value: str) -> CommandOutcome:
    scan = scan_database(input_value)
    exit_code = EXIT_OK if not scan.issues else EXIT_BLOCKING_DATA
    success = exit_code == EXIT_OK
    summary = base_summary("scan", scan, exit_code, success)
    return CommandOutcome(
        exit_code=exit_code,
        summary=summary,
        audit_records=audit_records_for_scan("scan", scan, exit_code, success),
    )


def verify_command(input_value: str) -> CommandOutcome:
    scan = scan_database(input_value)
    exit_code = EXIT_OK if not scan.issues else EXIT_REPAIR_OR_VERIFY
    success = exit_code == EXIT_OK
    summary = base_summary("verify", scan, exit_code, success)
    return CommandOutcome(
        exit_code=exit_code,
        summary=summary,
        audit_records=audit_records_for_scan("verify", scan, exit_code, success),
    )


def preview_command(input_value: str, manifest_value: str) -> CommandOutcome:
    preview = preview_repair(input_value, manifest_value)
    summary = base_summary("preview", preview.scan, EXIT_OK, True)
    summary = {
        **summary,
        "previewToken": preview.preview_token,
        "correctionCounts": {
            "events": len(preview.manifest.event_corrections),
            "plans": len(preview.manifest.plan_corrections),
        },
    }
    return CommandOutcome(
        exit_code=EXIT_OK,
        summary=summary,
        audit_records=[{
            "timestampUtc": utc_timestamp(),
            "toolVersion": TOOL_VERSION,
            "recordType": "summary",
            **summary,
        }],
    )


def repair_command(
    input_value: str,
    output_value: str,
    manifest_value: str,
    supplied_preview_token: str,
) -> CommandOutcome:
    result = repair_database(input_value, output_value, manifest_value, supplied_preview_token)
    summary = base_summary("repair", result.output_scan, EXIT_OK, True)
    summary = {
        **summary,
        "inputSha256": result.input_scan.input_sha256,
        "outputSha256": result.output_sha256,
        "correctionCounts": {
            "events": result.event_correction_count,
            "plans": result.plan_correction_count,
        },
    }
    audit_records = audit_records_for_scan(
        "repair",
        result.input_scan,
        EXIT_OK,
        True,
        output_sha256=result.output_sha256,
        correction_counts={
            "events": result.event_correction_count,
            "plans": result.plan_correction_count,
        },
    )
    audit_summary = dict(audit_records[-1])
    audit_summary["inputIssueCount"] = len(result.input_scan.issues)
    audit_summary["inputIssueCounts"] = issue_counts(result.input_scan.issues)
    audit_summary["issueCount"] = len(result.output_scan.issues)
    audit_summary["issueCounts"] = issue_counts(result.output_scan.issues)
    audit_records[-1] = audit_summary
    return CommandOutcome(
        exit_code=EXIT_OK,
        summary=summary,
        audit_records=audit_records,
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Scan, explicitly repair, or verify an offline Evolune Room v2 database copy."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    scan_parser = subparsers.add_parser("scan", help="read-only scan for v2-to-v3 blockers")
    scan_parser.add_argument("--input", required=True, help="path to an offline Evolune v2 database copy")
    scan_parser.add_argument("--audit", help="new JSONL audit path; never overwritten")

    preview_parser = subparsers.add_parser("preview", help="validate a manifest and issue a repair authorization token")
    preview_parser.add_argument("--input", required=True, help="path to an offline Evolune v2 database copy")
    preview_parser.add_argument("--manifest", required=True, help="version 1 correction manifest")
    preview_parser.add_argument("--audit", help="new JSONL audit path; never overwritten")

    repair_parser = subparsers.add_parser("repair", help="repair a new copy using an explicit manifest")
    repair_parser.add_argument("--input", required=True, help="path to an offline Evolune v2 database copy")
    repair_parser.add_argument("--output", required=True, help="new output database path")
    repair_parser.add_argument("--manifest", required=True, help="version 1 correction manifest")
    repair_parser.add_argument("--preview-token", required=True, help="exact token from the preview command")
    repair_parser.add_argument("--audit", help="new JSONL audit path; never overwritten")

    verify_parser = subparsers.add_parser("verify", help="read-only verification of a repaired v2 copy")
    verify_parser.add_argument("--input", required=True, help="path to the repaired Evolune v2 database copy")
    verify_parser.add_argument("--audit", help="new JSONL audit path; never overwritten")
    return parser


def error_summary(mode: str, error: ToolError) -> dict[str, Any]:
    return {
        "toolVersion": TOOL_VERSION,
        "mode": mode,
        "success": False,
        "exitCode": error.exit_code,
        "errorCategory": type(error).__name__,
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    mode = str(args.command)
    audit_path: pathlib.Path | None = None
    try:
        resolved_paths: dict[str, pathlib.Path] = {}
        input_path = resolve_input_file(args.input)
        resolved_paths["input"] = input_path
        if mode in {"preview", "repair"}:
            resolved_paths["manifest"] = resolve_input_file(args.manifest, "manifest")
        if mode == "repair":
            resolved_paths["output"] = resolve_new_file(args.output, "output")
        if args.audit:
            audit_path = resolve_new_file(args.audit, "audit")
            resolved_paths["audit"] = audit_path
        ensure_distinct_paths(resolved_paths)

        if mode == "scan":
            outcome = scan_command(str(input_path))
        elif mode == "verify":
            outcome = verify_command(str(input_path))
        elif mode == "preview":
            outcome = preview_command(str(input_path), args.manifest)
        else:
            outcome = repair_command(
                str(input_path), args.output, args.manifest, args.preview_token
            )
        if audit_path is not None:
            write_audit(audit_path, outcome.audit_records)
        print(json.dumps(outcome.summary, ensure_ascii=False, sort_keys=True, allow_nan=False))
        return outcome.exit_code
    except ToolError as error:
        summary = error_summary(mode, error)
        if audit_path is not None:
            failure_record = {
                "timestampUtc": utc_timestamp(),
                "toolVersion": TOOL_VERSION,
                "recordType": "summary",
                "mode": mode,
                "issueCount": 0,
                "issueCounts": {},
                "correctionCounts": {"events": 0, "plans": 0},
                "success": False,
                "exitCode": error.exit_code,
                "errorCategory": type(error).__name__,
            }
            try:
                write_audit(audit_path, [failure_record])
            except ToolError:
                pass
        print(json.dumps(summary, ensure_ascii=False, sort_keys=True, allow_nan=False))
        return error.exit_code
    except Exception as error:
        summary = {
            "toolVersion": TOOL_VERSION,
            "mode": mode,
            "success": False,
            "exitCode": EXIT_INTERNAL,
            "errorCategory": type(error).__name__,
        }
        print(json.dumps(summary, ensure_ascii=False, sort_keys=True, allow_nan=False))
        return EXIT_INTERNAL


if __name__ == "__main__":
    raise SystemExit(main())
