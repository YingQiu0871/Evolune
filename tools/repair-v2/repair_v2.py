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
from dataclasses import dataclass
from enum import Enum
from typing import Any, Iterable, Mapping, Sequence


TOOL_VERSION = "1.0.0"
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

    def to_dict(self) -> dict[str, Any]:
        result: dict[str, Any] = {
            "issueCode": self.code.value,
            "entityType": self.entity_type,
            "message": self.message,
        }
        if self.entity_type == "event" and self.entity_id is not None:
            result["eventId"] = self.entity_id
        if self.entity_type == "plan" and self.entity_id is not None:
            result["planId"] = self.entity_id
        if self.position is not None:
            result["position"] = self.position
        if self.raw_value is not None:
            result["rawValue"] = json_safe_value(self.raw_value)
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
class CommandOutcome:
    exit_code: int
    summary: Mapping[str, Any]
    audit_records: Sequence[Mapping[str, Any]]


def canonical_uuid(value: Any, field_name: str) -> str:
    if not isinstance(value, str) or UUID_RE.fullmatch(value) is None:
        raise UsageError(f"{field_name} must be a standard UUID string")
    return value.lower()


def json_safe_value(value: Any) -> Any:
    if isinstance(value, float):
        if math.isnan(value):
            return "NaN"
        if math.isinf(value):
            return "Infinity" if value > 0 else "-Infinity"
    if isinstance(value, bytes):
        return f"<blob:{len(value)} bytes>"
    if value is None or isinstance(value, (str, int, float, bool)):
        return value
    return str(value)


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
    return tuple(pathlib.Path(f"{path}{suffix}") for suffix in ("-wal", "-journal"))


def reject_active_database_sidecars(path: pathlib.Path) -> None:
    active = [str(sidecar) for sidecar in database_sidecars(path) if sidecar.exists() and sidecar.stat().st_size > 0]
    if active:
        raise UsageError(
            "input has an active SQLite WAL or rollback journal; create an offline standalone copy first",
            {"sidecars": active},
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
        "SELECT id, typeof(timeH) AS storageClass, timeH FROM dose_events ORDER BY id"
    ).fetchall()
    for row in event_rows:
        raw_id = row["id"]
        event_id = raw_id if isinstance(raw_id, str) else None
        if event_id is None or UUID_RE.fullmatch(event_id) is None:
            issues.append(
                Issue(
                    IssueCode.EVENT_ID_INVALID,
                    "event",
                    event_id,
                    "event id is not a standard UUID",
                    raw_value=raw_id,
                )
            )
        else:
            event_ids[event_id.lower()] = event_id
        try:
            validate_persisted_time_h(str(row["storageClass"]), row["timeH"])
        except ValidationError as error:
            issues.append(
                Issue(
                    error.code,
                    "event",
                    event_id,
                    error.message,
                    raw_value=row["timeH"],
                )
            )

    plan_rows = connection.execute(
        "SELECT id, typeof(timeOfDay) AS storageClass, timeOfDay "
        "FROM medication_plans ORDER BY id"
    ).fetchall()
    for row in plan_rows:
        raw_id = row["id"]
        plan_id = raw_id if isinstance(raw_id, str) else None
        if plan_id is None or UUID_RE.fullmatch(plan_id) is None:
            issues.append(
                Issue(
                    IssueCode.PLAN_ID_INVALID,
                    "plan",
                    plan_id,
                    "plan id is not a standard UUID",
                    raw_value=raw_id,
                )
            )
        else:
            plan_ids[plan_id.lower()] = plan_id

        storage_class = str(row["storageClass"])
        if storage_class != "text":
            issues.append(
                Issue(
                    IssueCode.TIME_OF_DAY_STORAGE_CLASS,
                    "plan",
                    plan_id,
                    f"timeOfDay SQLite storage class must be text, found {storage_class}",
                    raw_value=row["timeOfDay"],
                )
            )
            continue
        _, failures = inspect_legacy_time_of_day(row["timeOfDay"])
        for code, position, raw_value, message in failures:
            issues.append(
                Issue(
                    code,
                    "plan",
                    plan_id,
                    message,
                    position=position,
                    raw_value=raw_value,
                )
            )

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
    uncorrectable = [
        issue.to_dict()
        for issue in scan.issues
        if issue.code in {IssueCode.EVENT_ID_INVALID, IssueCode.PLAN_ID_INVALID}
    ]
    if uncorrectable:
        raise UsageError("database contains blocking invalid IDs that this manifest cannot repair", {"issues": uncorrectable})

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
) -> RepairResult:
    input_path = resolve_input_file(input_value)
    reject_active_database_sidecars(input_path)
    output_path = resolve_new_file(output_value, "output")
    manifest_path = resolve_input_file(manifest_value, "manifest")
    ensure_distinct_paths({"input": input_path, "output": output_path, "manifest": manifest_path})

    before_stat = input_path.stat()
    input_scan = scan_database(input_path)
    manifest = load_manifest(manifest_path)
    validate_manifest_for_scan(manifest, input_scan)
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
        "inputPath": str(scan.input_path),
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
            "inputPath": str(scan.input_path),
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
        "inputPath": str(scan.input_path),
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


def repair_command(input_value: str, output_value: str, manifest_value: str) -> CommandOutcome:
    result = repair_database(input_value, output_value, manifest_value)
    summary = base_summary("repair", result.output_scan, EXIT_OK, True)
    summary = {
        **summary,
        "inputPath": str(result.input_scan.input_path),
        "inputSha256": result.input_scan.input_sha256,
        "outputPath": str(result.output_path),
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

    repair_parser = subparsers.add_parser("repair", help="repair a new copy using an explicit manifest")
    repair_parser.add_argument("--input", required=True, help="path to an offline Evolune v2 database copy")
    repair_parser.add_argument("--output", required=True, help="new output database path")
    repair_parser.add_argument("--manifest", required=True, help="version 1 correction manifest")
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
        "error": error.message,
        "details": error.details,
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
        if mode == "repair":
            resolved_paths["output"] = resolve_new_file(args.output, "output")
            resolved_paths["manifest"] = resolve_input_file(args.manifest, "manifest")
        if args.audit:
            audit_path = resolve_new_file(args.audit, "audit")
            resolved_paths["audit"] = audit_path
        ensure_distinct_paths(resolved_paths)

        if mode == "scan":
            outcome = scan_command(str(input_path))
        elif mode == "verify":
            outcome = verify_command(str(input_path))
        else:
            outcome = repair_command(str(input_path), args.output, args.manifest)
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
                "inputPath": str(pathlib.Path(args.input).resolve(strict=False)),
                "issueCount": 0,
                "issueCounts": {},
                "correctionCounts": {"events": 0, "plans": 0},
                "success": False,
                "exitCode": error.exit_code,
                "error": error.message,
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
            "error": f"internal tool error: {type(error).__name__}: {error}",
        }
        print(json.dumps(summary, ensure_ascii=False, sort_keys=True, allow_nan=False))
        return EXIT_INTERNAL


if __name__ == "__main__":
    raise SystemExit(main())
