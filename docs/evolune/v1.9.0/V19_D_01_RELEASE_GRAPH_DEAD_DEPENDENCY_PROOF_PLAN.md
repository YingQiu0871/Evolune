# EVOLUNE V1.9.0 — D-01 RELEASE-GRAPH DEAD-DEPENDENCY PROOF PLAN

STATUS: CANDIDATE / REQUIRES INDEPENDENT REVIEW / NOT YET FROZEN

This document is a normative PLAN. It authorizes NO execution. No proof command,
no build, no Gradle invocation, no evidence file and no MANIFEST may be produced
until this plan has been independently reviewed and frozen.

---

## 0. Scope of this document

This plan defines the future governed evidence execution that must decide the
A-00 findings H-01 and H-02 from scratch. It is the only repository document
authorized by the D-01 EVIDENCE / PROOF PLAN DESIGN round.

D-01 remains EVIDENCE-ONLY. The future execution may use disposable worktrees
and disposable mutations. The future execution must never commit dependency
removal, catalog cleanup, ProGuard cleanup, product-code changes, version
changes, signing changes or release changes. Only governed docs/evidence may
ultimately be committed in D-01.

---

## 1. Authority inputs

| ID | Authority | Identity |
|---|---|---|
| A1 | Accepted/frozen corrected D-01 opening preflight | `V1.9.0 D-01 READ-ONLY OPENING PREFLIGHT — ACCEPTED / FROZEN AS PLAN INPUT` (independent re-review after Owner A′ narrow correction) |
| A2 | Owner decision | `A′ — EXCLUDE AND REPRODUCE` |
| A3 | A-00 audit (repository-external) | `D:\Evolune-Workspace\review-packets\v190-a00-post-v180-audit.txt`; size 21159 B; SHA-256 `728ab05f0526342a29ee40c47d65099ea12fc10bebc47cd43ae76f072b6c88ca` |
| A4 | Governance predecessor (immutable baseline) | `2fe04ee9ffc2651f89b8dc8513c1b39141054648`; tree `fad10191910964c3cfa3659022d9781f7c09a4c2` |
| A5 | Tracked repository content at A4 | the only permitted source-of-record for static facts |

No later D-01 or D-02 execution material is source authority.

---

## 2. Excluded / non-authoritative sources

### 2.1 Excluded candidate commit

`fc364e64bcfc62a3d9daa2636a80d2561935e96a` (tree `4c67cafe58a5f1ebb709044b12551543a5781080`) is
UNACCEPTED / NOT GOVERNED D-01 EVIDENCE.

This plan MUST NOT copy, restate as fact, or inherit from it:

* its results or outcomes;
* its hashes;
* its APK observations;
* its resolved-graph conclusions;
* its transitive-reachability conclusions;
* its DEX conclusions;
* its decision labels;
* its MANIFEST (`MANIFEST.sha256`, 38 entries);
* its experiment outputs (Experiment A / B / C);
* its transcribed baseline logs (`logs/baseline-*-raw.txt`);
* its `temporary-worktree-cleanup.txt`.

It may be inspected only where necessary to avoid accidental reuse or naming
collision. It is not a design authority.

### 2.2 Excluded review packet

`D:\Evolune-Workspace\review-packets\v190-d01-dead-dependency-proof.txt` is
UNACCEPTED CANDIDATE MATERIAL and is not an approved plan or result.

### 2.3 Excluded filesystem material

The original unmanaged build outputs, the two release APKs and the three
`_d01logs` files were physically removed before the Owner A′ correction round.
They are historical/unaccepted and MUST NOT be used as the execution baseline.
Their removal is an accepted historical fact; this plan does not require their
existence and does not recreate them.

---

## 3. Governance predecessor and baseline

* Predecessor / only permitted execution baseline: `2fe04ee9ffc2651f89b8dc8513c1b39141054648`.
* Predecessor tree: `fad10191910964c3cfa3659022d9781f7c09a4c2`.
* Every future execution worktree MUST be newly derived from A4, or from that
  exact product tree with ONLY the specifically frozen disposable mutation of
  §7 applied.
* The following MUST NOT be used as the execution baseline: `fc364e6` worktree
  state; existing D-02 worktrees; existing D-02 build output; existing D-02
  `_d02logs`; historical D-01 build output; old APKs; old transcribed logs.
* T-01 remains CLOSED / FROZEN / FULLY INTEGRATED and is not reopened.

---

## 4. Purpose and non-goals

Purpose: produce governed, reproducible evidence sufficient for an independent
result review to decide the H-01 and H-02 proof questions and to map the outcome
onto D-02 eligibility WITHOUT deciding it in D-01.

Non-goals: see §24 (Non-claims).

---

## 5. Frozen static inputs

### 5.1 H-01 static facts (accepted; carried forward)

| Fact | Value |
|---|---|
| version catalog version key | `graphicsPath = "1.1.0"` (`gradle/libs.versions.toml`) |
| version catalog alias | `androidx-graphics-path` = `androidx.graphics:graphics-path` |
| direct declaration | `implementation(libs.androidx.graphics.path)` (`app/build.gradle.kts`) |
| direct consuming module | `:app` only |
| direct production source use | NO |
| direct test use | NO |

### 5.2 H-02 static facts (accepted; carried forward)

| Fact | Value |
|---|---|
| catalog version key | `glance = "1.2.0-rc01"` |
| unused aliases | `androidx-glance-appwidget`, `androidx-glance-material3` |
| Gradle consuming references | 0 |
| tracked source classes absent | `StartConfirmAction`, `ConfirmDoseAction`, `CancelConfirmAction` |
| tracked `ActionCallback` implementations | 0 |
| Glance-targeted ProGuard/R8 rules | `app/proguard-rules.pro`: the ActionCallback wildcard keep, the three explicit named-class keeps, the package-wildcard keep and the package-wildcard keepnames — plus the two Glance/WorkManager comment headers attached to them |

### 5.3 Explicitly NOT frozen as fact

The following MUST be reproduced by this plan and MUST NOT be stated as frozen:

* the actual resolved release runtime graph;
* actual transitive persistence of `androidx.graphics:graphics-path`;
* the actual native-library packaging result;
* the actual APK-size effect;
* resolved-classpath absence of the Glance target — NOT PROVEN;
* DEX absence of the Glance target — NOT PROVEN;
* Glance keep rules being no-op — NOT PROVEN.

---

## 6. Fresh-reproduction rule

Every acceptance-critical observation in the future governed execution MUST be
newly produced from worktrees created under this plan. No prior result, hash,
inventory, log or label may be inherited from any excluded source of §2.

---

## 7. Execution topology

Three disposable source trees are defined.

### B0 — PRISTINE BASELINE

* source: exactly `2fe04ee9ffc2651f89b8dc8513c1b39141054648`
* mutation: NONE
* purpose: capture the governed release graph, dependency insight, release APK,
  native-library inventory, H-02 classpath checks and baseline DEX target
  inventory.

### H01-A — GRAPHICS-PATH DIRECT-DECLARATION REMOVAL

* source: exactly `2fe04ee9ffc2651f89b8dc8513c1b39141054648`
* only permitted source mutation: delete the single line
  `    implementation(libs.androidx.graphics.path)`
  from `app/build.gradle.kts`.
* NOT permitted: any version-catalog edit; any other build/config/source edit.
* purpose: capture the H01-A release graph, dependency insight, release APK and
  native-library inventory, to be compared against B0.

### H02-B — GLANCE CONFIG PROOF

* source: exactly `2fe04ee9ffc2651f89b8dc8513c1b39141054648`
* permitted disposable mutation, exactly these removals and nothing else:
  1. remove the two unused catalog aliases (the two `androidx-glance-*` library
     entries) from `gradle/libs.versions.toml`;
  2. remove the `glance` version key from `gradle/libs.versions.toml` **if and
     only if** no other catalog entry references it — this condition MUST be
     verified on the mutated file before the build, and the verification result
     recorded;
  3. remove ONLY the Glance-related ProGuard/R8 rules targeted by A-00 H-02 from
     `app/proguard-rules.pro`, i.e. the Glance `ActionCallback` keep block, the
     three explicit named-class keeps and the package-wildcard keep/keepnames
     pair, together with the Glance comment header that introduces them.

  The exact blocks to remove are identified by baseline content at A4:
  `app/proguard-rules.pro` rule lines
  `-keep class * implements androidx.glance.appwidget.action.ActionCallback {`,
  `-keep class io.github.yingqiu0871.evolune.widget.StartConfirmAction {`,
  `-keep class io.github.yingqiu0871.evolune.widget.ConfirmDoseAction {`,
  `-keep class io.github.yingqiu0871.evolune.widget.CancelConfirmAction {`,
  `-keep class io.github.yingqiu0871.evolune.widget.** implements androidx.glance.appwidget.action.ActionCallback {`
  and `-keepnames class io.github.yingqiu0871.evolune.widget.** implements androidx.glance.appwidget.action.ActionCallback`,
  each with its `public <init>();` body and closing brace, plus the
  `# Glance callback actions are instantiated reflectively from class name.` and
  `# Explicit keep for widget action callbacks used by actionRunCallback<T>().`
  and `# Future-proof: keep all widget package callback implementations and class names.`
  comment lines that belong to those blocks.
* MUST NOT remove: the WorkManager `InputMerger` keep rules, the
  `OverwritingInputMerger` keep rule, their comments, any unrelated ProGuard/R8
  rule, any unrelated catalog entry, or any product code.
* purpose: capture the H02-B release graph, release APK, DEX target inventory
  and classpath checks, to be compared against B0.

### Combined experiment decision

COMBINED EXPERIMENT = NOT REQUIRED.

Justification: the two causal questions are independent. H-01 concerns exactly
one dependency declaration in `app/build.gradle.kts`; H-02 concerns two catalog
aliases, one catalog version key and a bounded set of Glance ProGuard/R8 rules.
These surfaces do not interact: the Glance configuration is not on the
`androidx.graphics` resolution path and the graphics-path declaration is not on
the Glance classpath path. No acceptance-critical D-01 proposition requires the
two mutations to be present simultaneously. B0 + H01-A answers every H-01
proposition, and B0 + H02-B answers every H-02 proposition. The integrated
combination gate belongs to D-02, which performs its own later implementation
gate.

No unjustified experiment is added.

---

## 8. Future release-build prerequisite (fail-closed)

`app/build.gradle.kts` registers a `gradle.taskGraph.whenReady` gate that throws
a `GradleException` when any task whose name contains `release`
(case-insensitive) is requested and any of `EVOLUNE_KEYSTORE_PATH`,
`EVOLUNE_KEYSTORE_PASSWORD`, `EVOLUNE_KEY_ALIAS`, `EVOLUNE_KEY_PASSWORD` is
missing or blank, and a second throw when `EVOLUNE_KEYSTORE_PATH` does not point
to an existing persistent release keystore.

Therefore:

* Future governed execution of every release build and every
  `releaseRuntimeClasspath` task REQUIRES those four environment variables to be
  configured in the execution environment by the owner through the approved
  external mechanism.
* If any of them is missing at execution time, the run MUST stop immediately and
  be recorded as `PREREQUISITE_UNAVAILABLE`. It MUST NOT be downgraded to a debug
  build and MUST NOT be reported as a partial result.
* Their VALUES are secrets and MUST NOT be captured into evidence (see §15).
  Only presence/absence, and the non-secret fact that the keystore file existed,
  may be recorded.

At plan-design time the four variables are NOT present in the design
environment. This is a recorded execution prerequisite, not a design gap.

---

## 9. Frozen future commands

All commands are to be executed from the root of the relevant disposable
worktree, with the repository's own wrapper. No command in this section is
executed by this plan-design round.

### 9.1 B0 graph and insight

```
.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath
```
expected exit 0. Captures the full `releaseRuntimeClasspath` resolution tree for
`:app`.

```
.\gradlew.bat :app:dependencyInsight --configuration releaseRuntimeClasspath --dependency androidx.graphics:graphics-path
```
expected exit 0. Captures the targeted reachability of the graphics-path
artifact.

Both invocations have task names that do not contain `release`
(`dependencies`, `dependencyInsight`), so the §8 signing gate is not triggered by
them. The `--configuration releaseRuntimeClasspath` argument selects the
configuration only.

### 9.2 H01-A graph and insight

Same two commands as §9.1, executed in the H01-A disposable worktree.

### 9.3 B0 release build

```
.\gradlew.bat :app:clean :app:assembleRelease
```
expected exit 0. Requires the §8 prerequisite. Expected artifact location:
`app/build/outputs/apk/release/app-release.apk`.

### 9.4 H01-A release build

```
.\gradlew.bat :app:clean :app:assembleRelease
```
expected exit 0, in the H01-A worktree.

### 9.5 H02-B release build

```
.\gradlew.bat :app:clean :app:assembleRelease
```
expected exit 0, in the H02-B worktree.

### 9.6 H-02 classpath checks

```
.\gradlew.bat :app:dependencyInsight --configuration releaseRuntimeClasspath --dependency androidx.glance
```
expected exit 0. Records whether any `androidx.glance` artifact resolves.

```
.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath
```
in H02-B, to confirm the Glance configuration removal changed no resolved
artifact set other than what the mutation itself removes.

### 9.7 APK native-library inventory

Primary and only governed method — Python 3 `zipfile` over the built APK, which
yields entry path, uncompressed size, compressed size and CRC32:

```
python scripts\d01_apk_inventory.py <apk-path> > <output-raw>
```

The helper script `scripts\d01_apk_inventory.py` is a narrowly necessary plan
artifact (§17) whose frozen content is defined in §17.2. It prints one
deterministic line per zip entry in the form
`<crc32-hex> <compress_size> <file_size> <entry-path>`, sorted by entry path.

### 9.8 DEX target inspection

Primary and only governed method — Android SDK `apkanalyzer` (cmdline-tools),
class tree from DEX:

```
"%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\apkanalyzer.bat" dex packages <apk-path> > <output-raw>
```

Expected exit 0. The tool prints packages, classes, methods and fields with the
`P/C/M/F` markers and the `x/k/r/d` (removed / kept / referenced / defined)
markers, which is exactly the observable needed to decide whether the targeted
Glance type and the three named classes are present, kept or absent in the final
packaged DEX.

Tool availability was verified read-only at plan-design time: `apkanalyzer.bat`
exists at `C:\Users\1\AppData\Local\Android\Sdk\cmdline-tools\latest\bin\apkanalyzer.bat`
(a second copy exists under `cmdline-tools\latest-2`), `dexdump.exe` and `aapt2.exe`
exist under `build-tools\36.0.0` and `build-tools\36.1.0`, and `java` 17 is on
PATH. The plan freezes `apkanalyzer dex packages` as the single primary method
and does not offer an interchangeable method list. `dexdump` is NOT used.

### 9.9 JVM test gates

```
.\gradlew.bat :app:testDebugUnitTest
```
expected exit 0, executed in B0, H01-A and H02-B (§13).

```
.\gradlew.bat :app:testDebugUnitTest --tests "io.github.yingqiu0871.evolune.widget.*"
```
expected exit 0, executed in B0 and H02-B (§13).

These task names do not contain `release`, so the §8 gate is not triggered.

### 9.10 Raw capture wrapper

Each command above is captured as:

```
<command> > <raw-output-path> 2>&1 ; record exit code
```

with the raw file left byte-exact as emitted by the tool (see §18), and the exit
code recorded separately into the run's exit-state record.

---

## 10. Frozen observable semantics

### 10.1 Resolved-graph observables

For `androidx.graphics:graphics-path` in `releaseRuntimeClasspath`:

* **DIRECT PATH** — the artifact appears as an edge whose immediate parent is the
  `:app` project's `releaseRuntimeClasspath` root, i.e. it is contributed by a
  declaration in `:app` itself.
* **TRANSITIVE PATH** — the artifact appears only beneath at least one non-root
  dependency node, i.e. it is contributed by another dependency's own
  requirements.
* **CONFLICT RESOLUTION** — the insight output reports a version selection
  between two or more requested versions (an arrow-replaced version or an
  explicit selection-reason line). The selected version and the requesting
  modules MUST both be recorded.
* **ABSENCE** — the artifact does not appear anywhere in the
  `releaseRuntimeClasspath` tree, AND the targeted dependency insight reports no
  matching dependency.

A DIRECT PATH observation and a TRANSITIVE PATH observation may coexist; they are
recorded as separate facts and MUST NOT be collapsed.

### 10.2 APK native-library observables

For the release APK, every `lib/**` zip entry is recorded with entry path, ABI
(first path segment after `lib/`), file name, uncompressed size, compressed size
and CRC32.

* The actual ABI set is established by the B0 execution. It MUST NOT be
  hard-coded, and the historically observed four-ABI set is NOT a success
  condition.
* `libandroidx.graphics.path.so` is reported for every ABI present in each APK.
* APK total byte size and APK SHA-256 are also recorded.

### 10.2.1 APK size-attribution method

Packaging effects MUST be attributed from per-entry evidence, never from
whole-APK size alone.

Required comparison between the B0 APK and the H01-A APK:

1. compare total APK byte size (recorded and reported; NOT causal evidence by
   itself);
2. compare every `lib/**` entry pairwise by entry path: presence, uncompressed
   size, compressed size and CRC32;
3. compare the graphics-path entries specifically,
   `lib/<abi>/libandroidx.graphics.path.so`, for every ABI present;
4. compare the full native-library entry SET, i.e. the set of `lib/**` paths
   present in each APK, to detect any entry that appears or disappears besides the
   graphics-path entries.

Attribution rules:

* A packaging effect is ATTRIBUTED to the frozen mutation only when the
  native-library entry-set difference is exactly the graphics-path entries and no
  other `lib/**` entry changed presence.
* If any unrelated `lib/**` entry unexpectedly differs in presence, or if any
  unrelated packaged entry set differs in a way not explained by the frozen
  mutation, the size-attribution result MUST be classified INCONCLUSIVE unless the
  recorded environment and command evidence provides an independently justified
  deterministic explanation, which MUST be stated explicitly.
* Causal inference from APK total byte size alone is PROHIBITED. Total size is
  recorded and reported; it never establishes or refutes the packaging effect.
* A total-size delta of zero with unchanged graphics-path entries supports the
  §11 combination A + B + D (declaration redundant, no packaging reduction). A
  total-size delta of zero WITH changed graphics-path entries is a contradiction
  and is classified `G` (proof invalid / inconclusive) unless independently
  explained.

### 10.3 DEX observables

For each release APK: whether the class `androidx.glance.appwidget.action.ActionCallback`
and each of `StartConfirmAction`, `ConfirmDoseAction`, `CancelConfirmAction`
(fully qualified in the `io.github.yingqiu0871.evolune.widget` package) appear in
the DEX class tree, and with which `x/k/r/d` markers. The presence of any other
class implementing the targeted interface is also recorded.

### 10.4 H-02 keep-rule no-op definition

A targeted Glance keep rule is classified **NO-OP** if and only if all of the
following hold for the same release build:

1. no tracked source declares the rule's target class or an implementer of the
   targeted interface (static fact, §5.2); AND
2. the target type and the named classes are absent from the resolved
   `releaseRuntimeClasspath` per §9.6/§10.1-style insight evidence; AND
3. the target type and the named classes are absent from the packaged DEX per
   §9.8/§10.3; AND
4. the release build and R8 complete successfully with no missing-class warning
   that names the target type or the named classes.

Any single condition that cannot be established makes the classification
NOT PROVEN, not NO-OP. "Source grep = 0" alone MUST NOT be used as the no-op
definition.

---

## 11. H-01 result propositions

The plan freezes seven independently reportable propositions:

| ID | Proposition |
|---|---|
| A | direct declaration removed successfully (mutation applied and verified) |
| B | `graphics-path` still present transitively |
| C | `graphics-path` absent from the resolved release graph |
| D | `libandroidx.graphics.path.so` still packaged |
| E | `libandroidx.graphics.path.so` absent from the packaged APK |
| F | release build or a required verification gate fails |
| G | proof invalid / inconclusive |

Allowed and meaningful combinations, and their meaning:

* **A + B + D + build-OK** — the direct declaration is redundant, but removing it
  yields NO native-library packaging reduction. This MUST be reported as
  DIRECT-DECLARATION REDUNDANCY with PACKAGING REMOVAL NOT DEMONSTRATED, and the
  two conclusions MUST NOT be collapsed.
* **A + C + E + build-OK** — the direct declaration was the sole supplier; removal
  eliminates the artifact and its native library from the release APK.
* **A + C + D + build-OK** — the artifact left the graph but its native library
  is still packaged by another mechanism; recorded as a distinct, unresolved
  attribution case.
* **A + B + E + build-OK** — the artifact remains in the graph but its native
  library is no longer packaged; recorded as a distinct, unresolved attribution
  case.
* **F (any combination)** — the removal is not proven safe; recorded as
  NOT PROVEN / KEEP CURRENT CONFIG.
* **G** — the run produced an incomplete, self-contradictory or
  precondition-violating evidence set; recorded as PROOF INVALID / INCONCLUSIVE.

Partial outcomes are explicitly representable; a simple binary PASS is
prohibited.

---

## 12. H-02 result propositions

Independently reportable:

| ID | Proposition |
|---|---|
| H02-S | the two catalog aliases and the `glance` version key are unused statically (accepted static fact, §5.2) |
| H02-C | the Glance target type is absent from the resolved `releaseRuntimeClasspath` |
| H02-X | the Glance target type and the three named classes are absent from the packaged DEX |
| H02-K | every targeted Glance keep rule satisfies the §10.4 NO-OP definition |
| H02-B | the H02-B release build succeeds and R8 reports no missing-class warning naming the targets |
| H02-W | the widget JVM test gate (§13.2) passes in B0 and H02-B |
| H02-F | a required H-02 gate fails |
| H02-G | H-02 proof invalid / inconclusive |

`H02-S` alone MUST NOT be reported as H-02 proven.

---

## 13. Test / smoke contract

### 13.1 Surfaces identified at the predecessor (read-only; none executed here)

JVM widget tests in `:app` (`app/src/test/java/io/github/yingqiu0871/evolune/widget/`):
`WidgetAppearanceTest`, `WidgetHostContractTest`, `WidgetPaletteIdentityGoldenTest`,
`WidgetPaletteTokenGoldenTest`, `WidgetPresentationTest`, `WidgetStringsParityTest`,
`WidgetUiTest`, `WidgetWorkTest`.

Instrumented widget surfaces present in the repository:
`app/src/androidTest/.../widget/WidgetRemoteViewsTest.kt`,
`app/src/androidTest/.../widget/V15WidgetPerformanceDeviceTest.kt`,
`app/src/androidTest/.../data/repository/ReceiverWidgetProductionCutoverTest.kt`,
`app/src/androidTest/.../reminder/ReceiverLifecycleInstrumentationTest.kt`.

### 13.2 Frozen gates

* After H01-A: `:app:testDebugUnitTest` (full JVM unit test set) — the release
  build success plus this gate is the D-01 required verification for H-01.
* After H02-B: `:app:testDebugUnitTest` and the widget-scoped
  `--tests "io.github.yingqiu0871.evolune.widget.*"` selection, in both B0 and
  H02-B, plus the two release builds' R8 output.

### 13.3 Explicit deferrals

* INSTRUMENTED DEVICE SMOKE (any AVD or physical-device widget test) =
  DEFERRED TO D-02. Justification from accepted A-00 wording: A-00 places
  "release smokes" in the D-02 slice's expected tests, while the D-01 H-02 proof
  is scoped to "confirming Glance keep rules reference nothing on the classpath".
  The H-02 proposition is a classpath/build property, not a runtime-UI property:
  the shipped widget is RemoteViews-based and does not depend on Glance keeps, so
  a device smoke adds no discriminating evidence for the H-02 propositions.
* A broader canonical JVM gate (the full multi-module canonical run used at
  release gates) = DELIBERATELY DEFERRED TO D-02 implementation.

No new test is invented in D-01. No test is deleted or weakened.

---

## 14. Build nondeterminism policy

Same-size release APKs produced by separate builds may carry different SHA-256
values; this was observed historically and is accepted.

Therefore: APK SHA-256 = identity / provenance metadata ONLY; it is NOT a
byte-reproducibility gate and MUST NOT be asserted as equal across separate
builds. The acceptance-critical comparison uses the frozen semantic inventories
(§10.2) and the targeted entry/class observations (§10.3).

If any later step wishes to assert whole-APK hash equality, that assertion must
carry its own explicit justification for that exact comparison.

---

## 15. Environment capture (future execution time)

Recorded per run, without secrets:

* OS name, version and architecture;
* Java runtime vendor and version used by Gradle;
* Gradle wrapper distribution version (`gradle-9.2.1-bin`) and the wrapper
  `distributionSha256Sum` from the repository;
* Android Gradle Plugin version from the repository version catalog (`9.0.1`);
* Android SDK location, and `build-tools` version used by the APK/DEX
  inspection;
* `apkanalyzer` and Python 3 versions used;
* Git commit SHA and tree SHA of the worktree;
* worktree absolute path and role (B0 / H01-A / H02-B);
* the exact command lines executed;
* exit codes;
* the names (NOT values) of the four `EVOLUNE_*` signing variables and whether
  the keystore file existed;
* `core.autocrlf` and relevant Git EOL state, because evidence text generation is
  involved.

Secret values are NEVER captured.

---

## 16. Governed evidence root

Future governed evidence root, intentionally distinct from the excluded
candidate path:

```
docs/evolune/v1.9.0/evidence/D-01-release-graph-dead-dependency-proof/
```

The excluded candidate path is `docs/evolune/v1.9.0/evidence/D-01-dead-dependency-proof/`
and MUST NOT be copied from, merged with, or referenced as evidence.

No evidence root is created in this design round.

---

## 17. Exact future evidence inventory

### 17.1 Inventory table

All paths are relative to the governed evidence root of §16 unless stated.
Encoding is UTF-8 without BOM and LF line endings unless the row says otherwise.
All files except `MANIFEST.sha256` are MANIFEST-covered.

| # | Relative filename | Producer / command | Source worktree | Purpose | Status | Covered |
|---|---|---|---|---|---|---|
| 1 | `identity.txt` | manual record | n/a | predecessor SHA/tree, plan document identity, branch/worktree identity | required | yes |
| 2 | `authority-inputs.txt` | manual record | n/a | A1–A5 authority identities incl. A-00 SHA-256 and Owner A′ | required | yes |
| 3 | `excluded-sources.txt` | manual record | n/a | `fc364e6` identity and the explicit non-inheritance statement | required | yes |
| 4 | `toolchain-environment.txt` | commands | B0 | §15 environment record | required | yes |
| 5 | `static-finding-reconstruction.txt` | manual record | n/a | restatement of the frozen §5 static facts only | required | yes |
| 6 | `baseline-release-graph.txt` | `:app:dependencies --configuration releaseRuntimeClasspath` | B0 | governed baseline `releaseRuntimeClasspath` | required | yes |
| 7 | `baseline-graphicspath-insight.txt` | `:app:dependencyInsight` | B0 | governed baseline reachability of graphics-path | required | yes |
| 8 | `baseline-glance-classpath.txt` | `:app:dependencyInsight --dependency androidx.glance` | B0 | H-02 classpath status at baseline | required | yes |
| 9 | `h01a-release-graph.txt` | `:app:dependencies --configuration releaseRuntimeClasspath` | H01-A | post-mutation graph | required | yes |
| 10 | `h01a-graphicspath-insight.txt` | `:app:dependencyInsight` | H01-A | post-mutation reachability | required | yes |
| 11 | `baseline-release-build.txt` | `:app:clean :app:assembleRelease` | B0 | baseline release build result | required | yes |
| 12 | `h01a-release-build.txt` | `:app:clean :app:assembleRelease` | H01-A | H-01 post-mutation build result | required | yes |
| 13 | `h02b-release-build.txt` | `:app:clean :app:assembleRelease` | H02-B | H-02 post-mutation build result | required | yes |
| 14 | `baseline-apk-metadata.txt` | `apkanalyzer apk summary`, file hash/size | B0 | baseline APK identity metadata | required | yes |
| 15 | `h01a-apk-metadata.txt` | `apkanalyzer apk summary`, file hash/size | H01-A | post-mutation APK identity metadata | required | yes |
| 16 | `h02b-apk-metadata.txt` | `apkanalyzer apk summary`, file hash/size | H02-B | post-mutation APK identity metadata | required | yes |
| 17 | `baseline-native-library-inventory.txt` | `scripts\d01_apk_inventory.py` | B0 | baseline `lib/**` entries with sizes and CRC32 | required | yes |
| 18 | `h01a-native-library-inventory.txt` | `scripts\d01_apk_inventory.py` | H01-A | post-mutation `lib/**` entries | required | yes |
| 19 | `h02b-native-library-inventory.txt` | `scripts\d01_apk_inventory.py` | H02-B | H-02 post-mutation `lib/**` entries | required | yes |
| 20 | `native-library-comparison.txt` | diff of rows 17/18/19 | n/a | per-entry ABI/size/CRC comparison and attribution | required | yes |
| 21 | `baseline-dex-targets.txt` | `apkanalyzer dex packages` | B0 | baseline DEX status of the H-02 targets | required | yes |
| 22 | `h02b-dex-targets.txt` | `apkanalyzer dex packages` | H02-B | post-mutation DEX status of the H-02 targets | required | yes |
| 23 | `h02b-glance-classpath.txt` | `:app:dependencyInsight --dependency androidx.glance` | H02-B | post-mutation classpath status | required | yes |
| 24 | `h02-rule-evaluation.txt` | manual evaluation | n/a | per-rule §10.4 NO-OP decision | required | yes |
| 25 | `h01a-mutation.diff` | `git diff` vs predecessor | H01-A | exact disposable mutation | required | yes |
| 26 | `h02b-mutation.diff` | `git diff` vs predecessor | H02-B | exact disposable mutation | required | yes |
| 27 | `baseline-jvm-tests.txt` | `:app:testDebugUnitTest` | B0 | baseline JVM gate result | required | yes |
| 28 | `h01a-jvm-tests.txt` | `:app:testDebugUnitTest` | H01-A | H-01 JVM gate result | required | yes |
| 29 | `h02b-widget-jvm-tests.txt` | `:app:testDebugUnitTest --tests "...widget.*"` | H02-B | H-02 widget JVM gate result | required | yes |
| 30 | `result-decision-matrix.txt` | manual evaluation | n/a | §23 matrix with the observed result class | required | yes |
| 31 | `cleanup-worktree-report.txt` | commands | n/a | §22 cleanup execution and verification | required | yes |
| 32 | `final-contradiction-boundary-audit.txt` | manual audit | n/a | §24 non-claims restated as verified boundaries | required | yes |
| 33 | `encoding-audit.txt` | commands | n/a | proof that all governed text evidence is UTF-8/no-BOM/LF | required | yes |
| 34 | `logs/baseline-release-graph-raw.txt` | raw capture | B0 | raw tool bytes | required (raw) | yes |
| 35 | `logs/baseline-graphicspath-insight-raw.txt` | raw capture | B0 | raw tool bytes | required (raw) | yes |
| 36 | `logs/h01a-release-graph-raw.txt` | raw capture | H01-A | raw tool bytes | required (raw) | yes |
| 37 | `logs/h01a-graphicspath-insight-raw.txt` | raw capture | H01-A | raw tool bytes | required (raw) | yes |
| 38 | `logs/baseline-release-build-raw.txt` | raw capture | B0 | raw tool bytes | required (raw) | yes |
| 39 | `logs/h01a-release-build-raw.txt` | raw capture | H01-A | raw tool bytes | required (raw) | yes |
| 40 | `logs/h02b-release-build-raw.txt` | raw capture | H02-B | raw tool bytes | required (raw) | yes |
| 41 | `logs/baseline-native-library-inventory-raw.txt` | raw capture | B0 | raw inventory output | required (raw) | yes |
| 42 | `logs/h01a-native-library-inventory-raw.txt` | raw capture | H01-A | raw inventory output | required (raw) | yes |
| 43 | `logs/h02b-native-library-inventory-raw.txt` | raw capture | H02-B | raw inventory output | required (raw) | yes |
| 44 | `logs/baseline-dex-packages-raw.txt` | raw capture | B0 | raw DEX dump | required (raw) | yes |
| 45 | `logs/h02b-dex-packages-raw.txt` | raw capture | H02-B | raw DEX dump | required (raw) | yes |
| 46 | `logs/baseline-jvm-tests-raw.txt` | raw capture | B0 | raw test output | required (raw) | yes |
| 47 | `logs/h01a-jvm-tests-raw.txt` | raw capture | H01-A | raw test output | required (raw) | yes |
| 48 | `logs/h02b-widget-jvm-tests-raw.txt` | raw capture | H02-B | raw test output | required (raw) | yes |
| 49 | `logs/exit-states.txt` | manual record | n/a | exact exit code of every executed command | required | yes |
| 50 | `scripts/d01_apk_inventory.py` | plan-frozen helper | n/a | the §9.7 inventory script as executed | required | yes |
| 51 | `scripts/d01_canonicalize_text.py` | plan-frozen helper | n/a | the §18 canonicalization script as executed | required | yes |
| 52 | `MANIFEST.sha256` | generated | n/a | §20 manifest | required | self-excluded |

Rows that do not apply because of a fail-closed stop are recorded as
`NOT PRODUCED (PREREQUISITE_UNAVAILABLE)` or `NOT PRODUCED (PRIOR STEP FAILED)`
with the reason, and the run's result class becomes §19 fail-closed.

### 17.2 Frozen helper scripts

`scripts/d01_apk_inventory.py` prints exactly one line per zip entry, sorted by
entry path, in the form `<crc32-hex> <compress_size> <file_size> <entry-path>`,
reading the APK with `zipfile.ZipFile`. It must not filter, sample or truncate.
Its exact content is authored at execution time and captured as evidence row 50.

`scripts/d01_canonicalize_text.py` performs lossless decode → encode: it detects
UTF-16LE/BE with or without BOM and UTF-8 with or without BOM, decodes to text,
normalizes line endings to LF, and writes the result to stdout without BOM. It
must not alter any other byte content. Its exact content is captured as evidence
row 51.

---

## 18. Encoding and raw-log policy

Governed text evidence is UTF-8 WITHOUT BOM with LF line endings.

Acceptance-critical command output is retained in two forms:

* **RAW CAPTURE** — the byte-exact output as emitted by the tool, stored under
  `logs/`. Windows PowerShell redirection may emit UTF-16LE; the raw file is kept
  in whatever form the tool actually emitted.
* **NORMALIZED SUMMARY / COPY** — produced from the raw capture by
  `scripts/d01_canonicalize_text.py` as UTF-8/no-BOM/LF.

Rules:

* Normalized output MUST NOT replace raw output.
* Normalization is non-semantic formatting only; no content may be added,
  reordered or dropped.
* Acceptance-critical graph, build and error output MUST NOT be truncated. If any
  tool emits output that cannot be captured losslessly, the run stops and is
  recorded as `PREREQUISITE_UNAVAILABLE`, not truncated silently.
* The RAW capture is authoritative for content; the exit code is authoritative
  for command success, and is recorded in `logs/exit-states.txt`.
* The old pre-governance UTF-16LE pattern MUST NOT be repeated for governed
  evidence.

---

## 19. Exit-code and failure policy

Every command in §9 has: expected exit code 0; meaning of non-zero = the gate
failed; all subsequent steps that depend on its output MUST stop; partial output
is retained as raw evidence and explicitly labelled partial.

Build, test and tool failures MUST NOT be converted into positive proof. The
policy is fail-closed. A run that stops on a failed prerequisite or a failed gate
is recorded with the §11/§12 `F` or `G` proposition and the §36 result class
`NOT_PROVEN / KEEP_CURRENT_CONFIG` or `PROOF_INVALID / INCONCLUSIVE`, never with
a positive class.

---

## 20. Evidence MANIFEST contract

A NEW manifest is required. The excluded candidate's `MANIFEST.sha256` MUST NOT
be reused, copied or extended.

* Filename: `MANIFEST.sha256`
* Algorithm: SHA-256
* Coverage: every governed evidence file except `MANIFEST.sha256` itself
* Exactly one unique entry per covered file; no duplicate entries
* No phantom paths (every listed path must exist in the committed evidence set)
* No missing files (every governed evidence file must be listed)
* Self-entry is prohibited
* Entry format: `<lowercase-hex-sha256><two spaces><repo-root-relative-path>`,
  sorted by path

Checkout robustness: the authoritative hashes are hashes of the committed/staged
Git blob bytes of the covered files, not of CRLF-rematerialized working-tree
checkout bytes. The staging/hash procedure is:

1. write each evidence file to the governed evidence root;
2. `git add` the governed evidence root;
3. for each covered file compute the SHA-256 over the bytes stored in the Git
   object database for that staged path (equivalently: over the identical bytes
   obtained from the staged blob), not over the working-tree file;
4. write `MANIFEST.sha256` from those values;
5. stage `MANIFEST.sha256` as well.

An independent reviewer reproduces the manifest by re-running steps 3–4 against
the committed tree.

No MANIFEST is generated in this design round.

---

## 21. Mutation-diff contract

Every disposable mutation MUST have an exact captured diff against
`2fe04ee9ffc2651f89b8dc8513c1b39141054648`, produced in the mutated worktree with
`git diff` (not against any other base, and not a hand-written summary).

* H01-A expected mutation: exactly one deletion — the single
  `implementation(libs.androidx.graphics.path)` line in `app/build.gradle.kts`.
* H02-B expected mutation: only the enumerated Glance catalog removals and the
  enumerated Glance ProGuard/R8 removals of §7.

Any additional changed source or config path invalidates that experiment, and
the affected propositions are recorded as `G` (proof invalid / inconclusive).

No mutation may be committed in D-01.

---

## 22. Disposable worktree cleanup contract

Cleanup occurs only AFTER:

1. all required raw outputs have been copied into the governed evidence root;
2. hash and inventory capture is complete;
3. the mutation diff has been captured;
4. command exit states have been recorded.

Temporary worktrees created by the execution: the B0, H01-A and H02-B disposable
worktrees. Removal is by `git worktree remove --force` for each, followed by
`git worktree prune`, with a long-path fallback (`\\?\` rmdir) only where Git
reports failure.

Verification: each removed path no longer exists on disk; `git worktree list`
contains no entry for it; the removal verification output is captured into
`cleanup-worktree-report.txt`.

Cleanup failure = any worktree that cannot be removed or whose removal cannot be
verified is recorded as a cleanup defect in the report; the run's result class
becomes `PROOF_INVALID / INCONCLUSIVE` if a leftover worktree could have
influenced any captured observation.

Cleanup records are evidence of execution hygiene, NOT proof of H-01/H-02 by
themselves.

This design round creates and removes no worktree other than the dedicated
governed plan worktree authorized for the plan itself.

---

## 23. Result decision matrix

### 23.0 Top-level governed result classes

Exactly three top-level governed result classes are frozen. A simple binary PASS
is prohibited, because different H-01 and H-02 subclaims can diverge and partial
eligibility must be representable.

| Class | Meaning |
|---|---|
| `PROVEN_ELIGIBLE_FOR_SPECIFIED_D02_CLEANUP` | every dimension required by §23.2 for the SPECIFIED D-02 action is established, and `PROOF_INTEGRITY = VALID` and `EVIDENCE_MANIFEST_INTEGRITY = VALID`; the eligible action(s) MUST be named explicitly |
| `NOT_PROVEN / KEEP_CURRENT_CONFIG` | at least one required dimension is not established, or a required gate failed (`F`), while `PROOF_INTEGRITY` remains VALID; the current configuration is retained |
| `PROOF_INVALID / INCONCLUSIVE` | the evidence set is incomplete, self-contradictory or precondition-violating (`G`), or `EVIDENCE_MANIFEST_INTEGRITY = INVALID` |

Partial eligibility rule: H-01 direct-declaration cleanup eligibility and the
H-01 packaging-removal claim are classed separately. A run may therefore be
`PROVEN_ELIGIBLE_FOR_SPECIFIED_D02_CLEANUP` for "H-01 direct-declaration cleanup"
while the packaging-removal claim remains `NOT_PROVEN / KEEP_CURRENT_CONFIG`. Such
a partial outcome MUST be reported with the eligible action named, and MUST NOT
be relabelled as PACKAGING REMOVAL PROVEN.

Likewise, H-02 alias cleanup eligibility and H-02 ProGuard/R8 cleanup
eligibility are classed separately and may diverge.

### 23.1 Independent result dimensions

| Dimension | Allowed values |
|---|---|
| `H01_DIRECT_DECLARATION_REMOVABLE` | YES / NO / INCONCLUSIVE |
| `H01_ARTIFACT_AFTER_DIRECT_REMOVAL` | PRESENT_TRANSITIVELY / ABSENT / INCONCLUSIVE |
| `H01_NATIVE_LIBRARY_AFTER_DIRECT_REMOVAL` | PRESENT / ABSENT / INCONCLUSIVE |
| `H01_BUILD_GATE` | PASS / FAIL |
| `H01_TEST_SMOKE_GATE` | PASS / FAIL / NOT_RUN |
| `H02_ALIAS_UNUSED_STATIC` | YES (frozen static fact) |
| `H02_TARGET_CLASSPATH_STATUS` | PRESENT / ABSENT / INCONCLUSIVE |
| `H02_TARGET_DEX_STATUS` | PRESENT / ABSENT / INCONCLUSIVE |
| `H02_KEEP_RULE_EFFECT` | NOOP_PER_SECTION_10_4 / EFFECTIVE / INCONCLUSIVE |
| `H02_BUILD_GATE` | PASS / FAIL |
| `H02_WIDGET_SMOKE_GATE` | PASS / FAIL / NOT_RUN (device smoke deferred per §13.3) |
| `PROOF_INTEGRITY` | VALID / INVALID |
| `EVIDENCE_MANIFEST_INTEGRITY` | VALID / INVALID |

### 23.2 Mapping to D-02 eligibility

| D-02 action | Eligible only if |
|---|---|
| H-01 direct-declaration cleanup | `H01_DIRECT_DECLARATION_REMOVABLE = YES` AND `H01_BUILD_GATE = PASS` AND `H01_TEST_SMOKE_GATE = PASS` AND `PROOF_INTEGRITY = VALID` AND `EVIDENCE_MANIFEST_INTEGRITY = VALID`. NOTE: this is DIRECT-DECLARATION CLEANUP ELIGIBILITY and is INDEPENDENT of `H01_NATIVE_LIBRARY_AFTER_DIRECT_REMOVAL`; a redundant-but-still-packaged declaration is still eligible for declaration cleanup while the packaging-removal effect remains NOT DEMONSTRATED. |
| H-01 packaging-removal claim | additionally requires `H01_ARTIFACT_AFTER_DIRECT_REMOVAL = ABSENT` AND `H01_NATIVE_LIBRARY_AFTER_DIRECT_REMOVAL = ABSENT` |
| H-02 Glance catalog cleanup | `H02_ALIAS_UNUSED_STATIC = YES` AND `H02_BUILD_GATE = PASS` AND `PROOF_INTEGRITY = VALID` AND `EVIDENCE_MANIFEST_INTEGRITY = VALID` |
| H-02 Glance ProGuard/R8 cleanup | `H02_ALIAS_UNUSED_STATIC = YES` AND `H02_TARGET_DEX_STATUS = ABSENT` AND `H02_KEEP_RULE_EFFECT = NOOP_PER_SECTION_10_4` AND `H02_BUILD_GATE = PASS` AND `PROOF_INTEGRITY = VALID` AND `EVIDENCE_MANIFEST_INTEGRITY = VALID` |

The plan freezes this mapping only. It does NOT decide the actual result, and the
mapping MUST NOT be applied until a governed D-01 execution has run AND an
independent D-01 result review has accepted it AND D-01 has been closed
evidence-only. Plan outcomes themselves do not authorize D-02.

---

## 24. Non-claims

D-01 does NOT itself remove production/config dependencies. D-01 does NOT
authorize D-02 before independent result acceptance and evidence-only closure.
D-01 does NOT prove general dependency hygiene beyond H-01/H-02. D-01 does NOT
cover WorkManager keep cleanup. D-01 does NOT treat source grep as classpath
proof. D-01 does NOT require whole-APK byte reproducibility unless explicitly
proven for a specific comparison. D-01 does NOT inherit `fc364e6` results. D-01
does NOT inherit D-02 worktree results. D-01 does NOT make a release. D-01 does
NOT bump the version.

---

## 25. D-02 quarantine

The current local D-02 state is a separate P2-D02 with normative D-02 state
BLOCKED and factual D-02 activity started without eligibility.

The future D-01 execution MUST NOT read D-02 output as evidence, copy D-02 logs,
copy D-02 diffs, complete D-02 modifications, stage or unstage D-02, commit
D-02, or clean or reset D-02 worktrees. Read-only verification that
`origin/main` remains `2fe04ee9ffc2651f89b8dc8513c1b39141054648`, that no remote
D-02 branch or tag exists, and that D-02 activity has not modified the governance
predecessor is performed at the start and end of the future execution.

If D-02 is pushed, merged or tagged into any authoritative remote/main state, the
execution MUST stop: D-01 PROOF EXECUTION BLOCKED — D-02 GOVERNANCE/REF
ESCALATION.

---

## 26. Plan self-check

Before this plan may be frozen, it must be verified to contain: the exact
predecessor; the exact A-00 SHA-256; Owner A′; the explicit exclusion of
`fc364e6`; the new governed evidence root; the fresh-reproduction rule; the exact
experiment topology; the exact future commands; the exact mutation boundaries;
the exact graph observables; the exact APK/native observables; the exact
classpath/DEX observables; the exact H-02 no-op definition; the exact test/smoke
contract; the exact evidence inventory; the UTF-8/LF policy; the raw-log policy;
the manifest contract; the cleanup contract; the result vocabulary; the decision
matrix; the non-claims; the D-02 quarantine; and the explicit statement that no
execution is authorized.

---

## 27. Execution authorization

NONE. This plan authorizes no execution of any kind. The planned disposable
execution may begin only after this document has passed
`V1.9.0 D-01 EVIDENCE / PROOF PLAN INDEPENDENT REVIEW / FREEZE`.

---

END OF PLAN
