import argparse
import json
import pathlib
import sqlite3

import repair_v2


EVENT_ID = "00000000-0000-0000-0000-000000008001"
PLAN_ID = "00000000-0000-0000-0000-000000008002"


def create_invalid_v2(path: pathlib.Path) -> None:
    connection = sqlite3.connect(path)
    try:
        connection.executescript(
            """
            CREATE TABLE dose_events (
                id TEXT NOT NULL PRIMARY KEY,
                route TEXT NOT NULL,
                timeH REAL NOT NULL,
                doseMG REAL NOT NULL,
                ester TEXT NOT NULL,
                extras TEXT NOT NULL
            );
            CREATE TABLE medication_plans (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                route TEXT NOT NULL,
                ester TEXT NOT NULL,
                doseMG REAL NOT NULL,
                scheduleType TEXT NOT NULL,
                timeOfDay TEXT NOT NULL,
                daysOfWeek TEXT NOT NULL,
                intervalDays INTEGER NOT NULL,
                isEnabled INTEGER NOT NULL,
                extras TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            );
            CREATE TABLE room_master_table (
                id INTEGER PRIMARY KEY,
                identity_hash TEXT
            );
            """
        )
        connection.execute(
            "INSERT INTO room_master_table(id, identity_hash) VALUES (42, ?)",
            (repair_v2.V2_IDENTITY_HASH,),
        )
        connection.execute(
            "INSERT INTO dose_events(id, route, timeH, doseMG, ester, extras) "
            "VALUES (?, 'ORAL', ?, 1.25, 'E2', '{}')",
            (EVENT_ID, float("inf")),
        )
        connection.execute(
            "INSERT INTO medication_plans("
            "id, name, route, ester, doseMG, scheduleType, timeOfDay, daysOfWeek, "
            "intervalDays, isEnabled, extras, createdAt"
            ") VALUES (?, 'Synthetic Plan', 'ORAL', 'E2', 2.5, 'WEEKLY', "
            "'[\"08:30\",\"20:30:15\"]', '[1,5]', 7, 1, '{}', 0)",
            (PLAN_ID,),
        )
        connection.execute("PRAGMA user_version = 2")
        connection.commit()
    finally:
        connection.close()


def create_evidence(output_directory: pathlib.Path) -> dict[str, object]:
    output_directory.mkdir(parents=True, exist_ok=False)
    original = output_directory / "synthetic-invalid-v2.db"
    manifest_path = output_directory / "manifest.json"
    repaired = output_directory / "synthetic-repaired-v2.db"
    create_invalid_v2(original)
    original_sha256 = repair_v2.sha256_file(original)
    manifest = {
        "version": 1,
        "inputSha256": original_sha256,
        "eventCorrections": {EVENT_ID: {"timeH": 1.0}},
        "planCorrections": {PLAN_ID: {"timeOfDay": ["08:30", "20:30"]}},
    }
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    scan = repair_v2.scan_database(original)
    preview = repair_v2.preview_repair(original, manifest_path)
    result = repair_v2.repair_database(
        original,
        repaired,
        manifest_path,
        preview.preview_token,
    )
    verified = repair_v2.scan_database(repaired)
    if verified.issues:
        raise RuntimeError("synthetic repaired fixture did not verify cleanly")
    if repair_v2.sha256_file(original) != original_sha256:
        raise RuntimeError("synthetic original changed during repair")

    return {
        "toolVersion": repair_v2.TOOL_VERSION,
        "originalSha256": original_sha256,
        "repairedSha256": result.output_sha256,
        "inputIssueCount": len(scan.issues),
        "inputIssueCategories": sorted({issue.code.value for issue in scan.issues}),
        "eventCorrectionCount": result.event_correction_count,
        "planCorrectionCount": result.plan_correction_count,
        "outputIssueCount": len(verified.issues),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Create deterministic Batch 8D synthetic repair evidence.")
    parser.add_argument("--output-directory", required=True)
    args = parser.parse_args()
    output_directory = pathlib.Path(args.output_directory).resolve()
    evidence = create_evidence(output_directory)
    evidence_path = output_directory / "evidence.json"
    evidence_path.write_text(json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8")
    print(json.dumps(evidence, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
