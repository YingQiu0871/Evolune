# C-01 golden and R4.2 runtime fixture evidence (final implementation state)

Base contract commit: `34ca5e1b2bd7f7f7476a63e795d75a9c827acef9`
Implementation: uncommitted worktree state covered by the full fresh JVM run
(`jvm-full-fresh.log`, `54 actionable tasks: 54 executed`, three test tasks executed).

## 1. Established repository golden (preserved, not replaced)

`SimulationEngineTest.fixed range representative output remains numerically stable`
(included in `junit/app/TEST-io.github.yingqiu0871.evolune.pk.SimulationEngineTest.xml`):

- AUC = `23285.499354395688` (asserted with 1e-9 tolerance)
- 1441 points
- six fixed concentration samples (indices 0/288/576/864/1152/1440)

Result: PASS (no change to SimulationEngine / ThreeCompartmentModel / ParameterResolver /
PKParameters; see `audits.txt`).

## 2. R4.2 runtime fixture (additional evidence only)

Fixture: single `INJECTION x EV` 5 mg at `2025-06-01T08:00:00Z`, window starting exactly at the
intake instant, 30 days, body weight 60 kg, 8641 steps.

Expected (from `RetrospectivePkServiceTest.S1 W1 tau-zero EV fixture ...`):
- engine AUC = `48338.59081520633`
- `concPGmL[0] = -2.7255464005139244E-13` (in `[-1e-9, 0)`)
- samples: 1440 = `213.21107814555296`; 4320 = `5.76256344482292`;
  7200 = `0.19353466027581312`; 8640 = `0.03659750073613962`

Independent replication: a Python replica of `ThreeCompartmentModel.analytic3C` with the frozen
constants (fracFast 0.40, k1Fast 0.0216, k1Slow 0.0138, k2 0.070, k3 0.041,
F = 0.062258288229969413 x 272.38/356.50, Vd = 2.0 L/kg) produced AUC `48338.59081520633`
(identical to the last double digit) and the same sub-epsilon negative first point.

Authoritative assertions (in the test):
- first value is in `[-1e-9, 0)`;
- returned `series` value is exactly (delta = 0.0) the same-run `SimulationResult.concPGmL[0]`;
- the literal value is asserted as a deterministic-fixture reference only.
