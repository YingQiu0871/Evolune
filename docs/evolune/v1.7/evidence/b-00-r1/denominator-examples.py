#!/usr/bin/env python3
"""B-00-R1 denominator / eligibility worked examples (deterministic throw-away calculation).

Evidence only - this script does NOT ship. It encodes a small fact set in the *frozen*
vocabulary of docs/evolune/v1.7/V17_B_00_INSIGHTS_SEMANTICS.md (B-00-R1) and derives every
number by rule, so a reviewer can re-run it and diff the output against the recorded file:

    python3 denominator-examples.py

B-00-R1 fixes over the frozen B-00 copy (which stays untouched in evidence/b-00/):
- the output path is resolved relative to THIS script (not the current working directory);
- the confidence breakdown covers recorded intakes only (HIGH/MEDIUM/LOW/NONE); the
  "n/a (no record)" bucket is gone;
- the forbidden-formulation wording states explicitly that the 6/(6+1) example is an
  *occurrence coverage* ratio (matched occurrences over matched+unrecorded occurrences),
  not "all recorded authoritative intakes" (unmatched intakes are not occurrence coverage).
"""

from collections import Counter, OrderedDict
from pathlib import Path

# ---------------------------------------------------------------------------
# Fact set: range 2025-01-01 .. 2025-01-07 inclusive, current plan = daily 23:00
# ---------------------------------------------------------------------------
FACTS = [
    dict(kind="matched", provenance="EXACT_SLOT_AND_LOCAL_DATE", source="REMINDER", display_date="2025-01-01", dose=2.0, identity="E2"),
    dict(kind="matched", provenance="EXACT_SLOT_AND_LOCAL_DATE", source="REMINDER", display_date="2025-01-02", dose=2.0, identity="E2"),
    dict(kind="matched", provenance="EXACT_SLOT_AND_LOCAL_DATE", source="WIDGET", display_date="2025-01-03", dose=2.0, identity="E2"),
    dict(kind="matched", provenance="EXACT_SLOT_AND_LOCAL_DATE", source="WEAR", display_date="2025-01-04", dose=2.0, identity="E2"),
    # modern quick record (null slot, manual) matched by the time window -> MEDIUM inferred
    dict(kind="matched", provenance="NULL_SLOT_TIME_WINDOW", source="MANUAL", display_date="2025-01-05", dose=2.0, identity="E2"),
    # null-slot same-day -> LOW inferred
    dict(kind="matched", provenance="NULL_SLOT_SAME_DAY", source="MANUAL", display_date="2025-01-06", dose=1.0, identity="EV"),
    # occurrence without any matching record
    dict(kind="unrecorded", provenance=None, source=None, display_date="2025-01-07", dose=2.0, identity="E2"),
    # unmatched authoritative intakes (no occurrence binding)
    dict(kind="unmatched", provenance=None, source="MANUAL", display_date="2025-01-03", dose=5.0, identity="EV"),
    dict(kind="unmatched", provenance=None, source="LEGACY", display_date="2025-01-02", dose=1.0, identity="E2"),
    # identity partial (unknown medication key) - counts as an intake, no per-medication dose
    dict(kind="unmatched", provenance=None, source="JSON_V1", display_date="2025-01-04", dose=3.0, identity="UNKNOWN"),
    # not a HistoricalEntry (upcoming context) - must never enter any history metric
    dict(kind="future", provenance=None, source=None, display_date="2025-01-08", dose=2.0, identity="E2"),
]

TIER = {
    "EXACT_SLOT_AND_LOCAL_DATE": "HIGH",
    "SLOT_WINDOW_WITHOUT_LOCAL_DATE": "MEDIUM",
    "NULL_SLOT_TIME_WINDOW": "MEDIUM",
    "NULL_SLOT_SAME_DAY": "LOW",
}
KNOWN_IDENTITIES = ("E2", "EB", "EV", "EC", "EN")

START, END = "2025-01-01", "2025-01-07"
IN_RANGE = [f for f in FACTS if f["kind"] != "future" and START <= f["display_date"] <= END]
RECORDED = [f for f in IN_RANGE if f["kind"] in ("matched", "unmatched")]
DAYS_WITH_RECORDED = sorted({f["display_date"] for f in RECORDED})
BY_SOURCE = Counter(f["source"] for f in RECORDED)
WITH_RECORD = [f for f in IN_RANGE if f["kind"] == "matched"]
WITHOUT_RECORD = [f for f in IN_RANGE if f["kind"] == "unrecorded"]

# confidence breakdown: recorded intakes only, exactly four buckets
CONFIDENCE = Counter()
for fact in RECORDED:
    CONFIDENCE[TIER.get(fact["provenance"], "NONE")] += 1

# dose totals: per-medication, identity known only (no cross-medication total, no Unknown bucket)
DOSE_BY_IDENTITY = OrderedDict()
unknown_identity_intakes = 0
for fact in RECORDED:
    if fact["identity"] in KNOWN_IDENTITIES:
        DOSE_BY_IDENTITY[fact["identity"]] = DOSE_BY_IDENTITY.get(fact["identity"], 0.0) + fact["dose"]
    else:
        unknown_identity_intakes += 1


def ratio(approved_tiers):
    """The frozen (shipped?) section-12 definition - NOT shipped in v1.7-B."""
    eligible_matched = [
        f for f in IN_RANGE if f["kind"] == "matched" and TIER[f["provenance"]] in approved_tiers
    ]
    eligible_unrecorded = WITHOUT_RECORD
    excluded = {
        "unmatched intakes": len([f for f in IN_RANGE if f["kind"] == "unmatched"]),
        "matched below approved tiers": len(
            [f for f in IN_RANGE if f["kind"] == "matched" and TIER[f["provenance"]] not in approved_tiers]
        ),
        "future occurrences": len([f for f in FACTS if f["kind"] == "future"]),
    }
    return len(eligible_matched), len(eligible_matched) + len(eligible_unrecorded), excluded


def main():
    print("# B-00-R1 denominator / eligibility worked examples (generated by denominator-examples.py)")
    print()
    print("Range: %s .. %s inclusive (LocalDate), 7 days, current plan = daily 23:00" % (START, END))
    print("Facts in range: %d (excluded future-context rows: %d)" % (len(IN_RANGE), len(FACTS) - len(IN_RANGE)))
    print()
    print("## Shipped metrics (facts only, V17_B_00_INSIGHTS_SEMANTICS section 17)")
    print("- Recorded intakes (matched + unmatched, one per authoritative event): %d" % len(RECORDED))
    print("- Days with recorded intake (unrecorded-only days excluded): %d -> %s"
          % (len(DAYS_WITH_RECORDED), ", ".join(DAYS_WITH_RECORDED)))
    print("- By source: %s" % ", ".join("%s=%d" % kv for kv in sorted(BY_SOURCE.items())))
    print("- Per-medication dose totals (mg, identity known only): %s"
          % (", ".join("%s=%.1f" % kv for kv in DOSE_BY_IDENTITY.items()) or "none"))
    print("  (no cross-medication total; no 'Unknown medication = N mg' bucket)")
    print("- unknownIdentityRecordedIntakeCount (count only, not a dose): %d" % unknown_identity_intakes)
    print("- Record coverage (counts, no percentage): occurrences with matching record=%d, without=%d"
          % (len(WITH_RECORD), len(WITHOUT_RECORD)))
    print("- Binding confidence breakdown (recorded intakes only, four buckets): %s"
          % ", ".join("%s=%d" % (bucket, CONFIDENCE.get(bucket, 0)) for bucket in ("HIGH", "MEDIUM", "LOW", "NONE")))
    print("  (UnrecordedHistoricalOccurrence has no binding confidence and is not in this breakdown)")
    print()
    print("## Frozen section-12 ratio (NOT shipped in v1.7-B), evaluated for evidence")
    for tiers in ({"HIGH"}, {"HIGH", "MEDIUM"}):
        numerator, denominator, excluded = ratio(tiers)
        print("- approvedTiers={%s}: numerator=%d denominator=%d -> %.1f%%  (excluded: %s)"
              % ("+".join(sorted(tiers)), numerator, denominator, 100.0 * numerator / denominator,
                 ", ".join("%s=%d" % kv for kv in sorted(excluded.items()))))
    print()
    print("## Forbidden formulations (sections 6/18) checked against the same fact set")
    print("- missedCount = unrecordedCount would report %d 'missed' doses: FORBIDDEN "
          "(no recorded intake does not mean not taken)" % len(WITHOUT_RECORD))
    print("- the forbidden occurrence-coverage ratio is matched occurrences / (matched + unrecorded) "
          "occurrences = %d / (%d + %d) = %.1f%%: FORBIDDEN (it reads as 'how much of what was "
          "prescribed was taken', while the denominator is generated from the CURRENT plan)."
          % (len(WITH_RECORD), len(WITH_RECORD), len(WITHOUT_RECORD),
             100.0 * len(WITH_RECORD) / (len(WITH_RECORD) + len(WITHOUT_RECORD))))
    print("  Note: this is NOT 'all recorded authoritative intakes' - unmatched intakes (%d) are not "
          "occurrence coverage at all." % len([f for f in IN_RANGE if f["kind"] == "unmatched"]))
    print("- unmatched intakes (%d) must stay out of any plan-based denominator while still counting "
          "as recorded intakes" % len([f for f in IN_RANGE if f["kind"] == "unmatched"]))
    print("- 'Unknown medication' must never become a dose bucket: %d intake(s) with unknown identity are "
          "counted, not summed into mg." % unknown_identity_intakes)


if __name__ == "__main__":
    import contextlib
    import io

    buffer = io.StringIO()
    with contextlib.redirect_stdout(buffer):
        main()
    text = buffer.getvalue()
    # output path is resolved relative to this script, not the current working directory
    target = Path(__file__).resolve().parent / "denominator-examples.md"
    io.open(target, "w", encoding="utf-8", newline="\n").write(text)
    print(text)
