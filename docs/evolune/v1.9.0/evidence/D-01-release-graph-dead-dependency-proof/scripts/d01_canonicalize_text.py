#!/usr/bin/env python3
"""Governed D-01 helper: lossless raw-log canonicalizer.

Frozen per V19_D_01_RELEASE_GRAPH_DEAD_DEPENDENCY_PROOF_PLAN.md §17.2 / §18.
Reads raw bytes on stdin, detects UTF-16LE/BE (with or without BOM) or UTF-8
(with or without BOM), decodes to text, normalizes line endings to LF, and
writes UTF-8 without BOM to stdout. No other byte content is altered.
Input that cannot be decoded as one of the supported encodings fails closed.
"""
import sys


def decode(raw: bytes) -> str:
    if raw.startswith(b"\xff\xfe"):
        return raw[2:].decode("utf-16-le")
    if raw.startswith(b"\xfe\xff"):
        return raw[2:].decode("utf-16-be")
    if raw.startswith(b"\xef\xbb\xbf"):
        return raw[3:].decode("utf-8")
    # No BOM: distinguish UTF-16 from UTF-8 by the NUL-byte pattern.
    if len(raw) >= 2:
        sample = raw[:4096]
        even_nuls = sample[0::2].count(0)
        odd_nuls = sample[1::2].count(0)
        if even_nuls > 0 and odd_nuls == 0:
            return raw.decode("utf-16-be")
        if odd_nuls > 0 and even_nuls == 0:
            return raw.decode("utf-16-le")
    return raw.decode("utf-8")


def main() -> int:
    raw = sys.stdin.buffer.read()
    if not raw:
        return 0
    try:
        text = decode(raw)
    except UnicodeDecodeError as exc:
        sys.stderr.write("d01_canonicalize_text.py: undecodable input: {}\n".format(exc))
        return 1
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    sys.stdout.buffer.write(text.encode("utf-8"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
