#!/usr/bin/env python3
"""Governed D-01 helper: deterministic full ZIP entry inventory of an APK.

Frozen per V19_D_01_RELEASE_GRAPH_DEAD_DEPENDENCY_PROOF_PLAN.md §9.7 / §17.2.
Prints exactly one line per ZIP entry, sorted by entry path, in the form:

    <crc32-hex> <compress_size> <file_size> <entry-path>

Reading is done with zipfile.ZipFile. It does not filter, sample or truncate.
"""
import sys
import zipfile


def main() -> int:
    if len(sys.argv) != 2:
        sys.stderr.write("usage: d01_apk_inventory.py <apk-path>\n")
        return 2
    apk_path = sys.argv[1]
    with zipfile.ZipFile(apk_path, "r") as zf:
        entries = sorted(zf.infolist(), key=lambda info: info.filename)
        for info in entries:
            sys.stdout.write(
                "{:08x} {} {} {}\n".format(
                    info.CRC & 0xFFFFFFFF,
                    info.compress_size,
                    info.file_size,
                    info.filename,
                )
            )
    return 0


if __name__ == "__main__":
    sys.exit(main())
