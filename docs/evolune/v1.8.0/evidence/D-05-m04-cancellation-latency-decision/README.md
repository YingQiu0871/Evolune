D-05 M04 CANCELLATION-LATENCY DECISION EVIDENCE

This is an evidence-only D-05 decision from the frozen D-04R head
655ff2880485eb75300104c6a98cb057b0f72b05.

The measurement preserves the production Home topology:

  Main.immediate / HRTViewModel operation scope
    -> withContext(Dispatchers.Default)
    -> non-cooperative SimulationEngine
    -> suspended return and PKState publication on Main

No production source, committed test source, dispatcher topology, Home stale-publication
guard, cancellation checkpoint, or request-policy change was made. The Android harness was
temporary, its source is retained only as an evidence copy under raw/, and it was removed from
app/src/androidTest before finalization.

The corrected run contains 135 valid result rows:

  A clean reference:       30
  B single supersession:   45 (15 early, 15 middle, 15 late)
  C realistic burst:       30
  D stress boundary:       30

The final disposition is recorded in decision.md and is intentionally an M04
cancellation-latency/resource decision, not a reopening of D-04R correctness conclusions.
