D-06 — M04 COOPERATIVE CANCELLATION IMPLEMENTATION EVIDENCE
============================================================

This package records the D-06 implementation from the exact accepted D-05
starting state. It separates raw Windows checkout observations from the
canonical tracked-LF regression gate.

Decision: APPROVE.

The implementation adds a pure-Kotlin optional cancellation callback to the
synchronous SimulationEngine. The callback is checked once at the start of
each outer simulation step. Home supplies a coroutine-owner check through its
existing calculator boundary; Retrospective keeps the no-op default.

The device evidence uses the real HRTViewModel collectLatest path on Pixel_7
(Android 15 / API 35). The temporary measurement source is preserved under
raw/ for audit and was removed from app/src/androidTest before the evidence
commit.

See full-jvm-summary.txt, measurements/recomputed-statistics.txt, and
final-summary.txt for the acceptance result.
