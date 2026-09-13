# B-00-R1 B-01 readiness checklist (snapshot)
# Extraction rule: start at "## 28. B-01 readiness checklist", to end of file; verbatim.

## 28. B-01 readiness checklist（P3-23）

B-01 只有在以下各项**全部冻结**后才能开始：

- [x] fact taxonomy（§2）
- [x] eligibility（§3：coverage-count 与 future-ratio 两列已拆分）
- [x] confidence（§4 + §25：四 bucket，仅 recorded intakes）
- [x] source/provenance（§16：writer 级审计 + 三态可达性）
- [x] range presets（§19：端点与示例全部冻结）
- [x] identity disposition（§13.1：单值 `Unknown medication`）
- [x] dose-total eligibility（§13.2：无跨药物总量；per-medication 仅 known）
- [x] record-coverage semantics（§12 + §24：公式已去 `identityEligible`，disclosure 必附）
- [x] timezone disclosure（§26：`containsCurrentTimezoneDerivedDates`）
- [x] delete/undo semantics（§15：物理删除，无 tombstone 指标）

**Open decisions 不阻塞 B-01**：charts / granularity（B-03）、cache（须可重建）、
Option-2 timing（需单独批准）、percentage UI（未 ship）、anti-androgen projection field（`A-04/B` debt，
只影响 anti-androgen 的 per-drug 指标，已按 identity unavailable 排除）。
