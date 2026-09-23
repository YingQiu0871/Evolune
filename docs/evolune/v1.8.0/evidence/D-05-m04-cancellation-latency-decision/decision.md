# D-05 M04 DECISION

## Correctness boundary

D-04R remains closed and frozen. Its production-topology conclusion is not reopened: obsolete
Home results did not publish in the tested Main.immediate -> Default -> Main path. Continued
non-cooperative computation is not evidence that stale publication is possible.

## M04 resource/cancellation finding

The realistic production burst (five manual-refresh callback submissions at 200 ms intervals,
30 repetitions) produced a final-request engine-start mean of 704.005 ms versus 8.542 ms for the
clean reference, an absolute delta of 695.463 ms. Final publication mean was 1473.222 ms versus
773.246 ms, an absolute delta of 699.976 ms (+90.524%). Obsolete work drained for 700.050 ms
mean after the last input. One obsolete engine was active at every final submission.

The cost is bounded: peak engine concurrency stayed at one, 55 of 150 realistic-burst inputs
were coalesced before starting, and no accumulating concurrent engine queue was observed. The
non-normal 10 ms stress also stayed at peak one and coalesced 557 of 630 inputs, although its
tail contained a 2028.334 ms maximum publication latency. Stress-only behavior is not the
decision basis.

There is no explicit numerical product SLA for this cancellation/resource property. The
realistic burst nevertheless demonstrates a repeatable latest-request delay of approximately
one engine runtime. That is a concrete production-relevant latency/resource cost under the
actual manual refresh/retry entry point, even though it is serialized and bounded.

## Disposition

M04 remains open. D-05 does not implement the follow-on.

The smallest follow-on problem is an M04 computation-latency/resource slice: make the
non-cooperative simulation computation stop or yield at cancellation-sensitive boundaries so
the latest Home request does not wait behind a full obsolete engine body. The likely first
intervention layer is the SimulationEngine/calculation loop, with HRTViewModel orchestration
changed only if required to propagate that cooperative cancellation. Any future checkpoint
must be justified as cancellation latency/resource work, never as Home stale-publication
hardening.

APPROVE V1.8.0 D-05 — M04 IMPLEMENTATION SLICE REQUIRED
