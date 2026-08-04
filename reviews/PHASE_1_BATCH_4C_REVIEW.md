# Evolune Phase 1 Batch 4C 代码审阅报告

**审阅日期**: 2026-08-04
**审阅者**: DeepSeek（独立高级代码审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch4c-repair-tool`（HEAD: `2a2ae06`）
**方式**: 只读审阅；未修改/暂存/提交/打标签任何文件；未开始 Batch 3C/4B 后续工作

---

## Executive summary

- **最终决定**: **APPROVE WITH P2**
- **P0**: 0
- **P1**: 0
- **P2**: 1（本机无 Python 3.12，实际以 Python 3.14.6 验证；实施方已如实记录）
- **是否允许提交**: 是
- **最大剩余风险**: 无 P0/P1。工具仅处理离线副本、不触碰生产代码/schema；唯一 P2 为运行时版本差异（3.14.6 vs 3.12），代码使用 3.12 兼容的标准库 API。v3 仍处 ADR-016 内部不可发布区间。

---

## Git and scope

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch4c-repair-tool` ✓ |
| 未跟踪文件 | 恰好 5 个（repair_v2.py / test_repair_v2.py / README.md / manifest.example.json / 4C_REPORT.md）✓ |
| 已跟踪文件修改 | 无（`git diff` 对 app/src/main、app/schemas、app/src/test、app/src/androidTest、wear、gradle 全空）✓ |
| 暂存区 | 空 ✓ |
| `git diff --check` | 通过 ✓ |
| Batch 4B 已提交并打标签 | ✓（`9422961` + tag `phase-1-batch-4b` + 审阅 `2a2ae06`）|
| 越界文件 | 无 Batch 3C/Repository 接线/生产代码 ✓ |
| 敏感数据 | 无真实/派生健康数据、无二进制 SQLite fixture 入库 ✓ |

---

## Findings

### F1 (P2) — 验证运行时为 Python 3.14.6 而非 3.12（实施方已记录）

- **严重程度**: P2
- **文件**: `docs/phase-reports/PHASE_1_BATCH_4C_REPORT.md:118-120, 175`
- **问题**: `py -3.12` 在本机不可用，全部验证在 Python 3.14.6 上执行；代码仅声明"语法与 API 兼容 3.12"，未在 3.12 实机运行。
- **影响**: 极小。实现只使用 3.12 及更早版本就存在的标准库 API（`sqlite3`/`argparse`/`json`/`hashlib`/`pathlib`/`shutil`/`datetime`/`dataclasses`/`enum`/`typing`，含 `X | None` 语法 —— 3.10+ 支持）；唯一可能的新特性风险点是 `datetime.datetime.now(datetime.timezone.utc)`（3.2+）、`pathlib.Path.is_relative_to`（未使用）、`str.removeprefix`（3.9+，未使用）。风险极低但严格讲未在目标运行时验证。
- **依据**: ADR-016 §13.3 要求 Python 3.12-compatible 标准库工具。
- **最小修复方向**: 提交后在装有 Python 3.12 的 CI/本机补跑一次 `python -m unittest` 与 smoke；不阻塞提交。
- **是否阻止提交**: 否

**其余无问题（None）。**

---

## Repair tool verdict

**符合设计。** `repair_v2.py`（1081 行）逐项核实：

**范围/依赖**：仅标准库（L4-15）；无 pip 依赖、无网络 ✓

**身份验证（L279-331）**：`PRAGMA quick_check`=ok、`user_version`=2、必需三表、必需列（id/timeH、id/timeOfDay）、`room_master_table` v2 identity hash `a8036e3f...` —— 全部硬编码常量；非 v2/任意 SQLite/缺表缺列 → DatabaseIdentityError(exit 3) ✓；永不改 user_version/room_master_table ✓

**timeH 数学（L333-359）**：
- bool 与数字类型检查（bool 是 int 子类，显式拒绝）✓
- `float()` → binary64；非 finite → NON_FINITE ✓
- `scaled = binary64 * 3_600_000.0`；非 finite → MULTIPLICATION_OVERFLOW ✓
- 范围 `[-2^63, 2^63)`（LONG_MIN inclusive / LONG_MAX_EXCLUSIVE）与 Kotlin `LegacyTimeAdapter` 完全一致 ✓
- **`rounded = math.floor(scaled + 0.5)`** —— 与 Java `Math.round(double)` 定义（floor(x+0.5)）逐位一致，非 Python 银行家舍入 ✓
- 舍入后再查一次范围（防 floor 边界越界）✓
- 不 clamp、不回退 0、不使用当前时间 ✓

**storage class（L362-368）**：仅 integer/real 接受；NULL/TEXT/BLOB 拒绝 ✓（与 ADR-016 一致）

**timeOfDay（L371-440）**：
- 空串 → 空 ✓；非 str → STORAGE_CLASS ✓
- `json.loads(parse_constant=拒绝 NaN/Infinity 常量)`；MALFORMED ✓；非数组根 → ROOT_NOT_ARRAY ✓
- 元素非 str → ELEMENT_NOT_STRING（带 position）✓
- `LEGACY_LOCAL_TIME_RE`：`HH:mm`/`HH:mm:ss`/`HH:mm:ss.fff(1-9)`；second≠"00" 或 fraction 含非零 → NON_MINUTE ✓；零秒/零纳秒形式 canonical 为 `HH:mm` ✓
- offset/zone/空白/单数字小时/非 ISO 分隔符 → INVALID_LOCAL_TIME ✓（与 Java `LocalTime.parse` 行为一致）
- 顺序与重复保留；canonical 列表累积 ✓

**scan（L443-555）**：ORDER BY id 稳定；event/plan ID UUID 校验（EVENT_ID_INVALID/PLAN_ID_INVALID）；只读 URI（`mode=ro` + `query_only`）；before/after stat（size+mtime_ns）+ SHA-256 变更检测 ✓

**manifest v1（L584-657）**：
- `object_pairs_hook` 拒绝重复键（大小写变体经 canonical_uuid 二次拒绝）✓
- `parse_constant` 拒绝 NaN/Infinity ✓
- 顶层/修正对象级 `require_exact_fields`（未知+缺失字段拒绝）✓
- version 必须为整数 1（bool 拒绝）✓；inputSha256 64 hex ✓
- event timeH 必须 JSON 数字（bool 拒绝）且经 `java_math_round_scaled` 预校验 ✓
- plan timeOfDay 必须已是 canonical `HH:mm`（`HH:mm:ss` 拒绝而非 canonicalize）✓；顺序/重复保留 ✓

**manifest↔scan 双向覆盖（L660-710）**：SHA 必须匹配输入；无效 ID 阻断项 → 明确不可修复报错；manifest ID 必须存在于输入；只能修正阻断行（非阻断行拒绝）；必须覆盖全部阻断行（缺失拒绝）—— 双向完整 ✓

**repair（L731-846）**：
- 输入/输出/manifest/audit 路径解析 + `ensure_distinct_paths` + symlink/存在输出/WAL/journal sidecar 拒绝 ✓
- 输入扫描 → manifest → 复制（copy2）→ 输出 SHA 与输入比对 → 输入 SHA 复查 ✓
- `BEGIN IMMEDIATE`；**仅两个参数化 UPDATE**（`dose_events.timeH`、`medication_plans.timeOfDay` by ID）；每行 `rowcount == 1` 校验 ✓
- 事务内重扫 `scan_connection` 无阻断才 commit；WAL checkpoint + sidecar 清理 ✓
- 失败：rollback + 关闭 + `remove_database_copy`（含 -wal/-shm/-journal）✓
- 输入 mtime/size/SHA 前后不变 ✓；输出最终 `scan_database` 验证 ✓
- 无 in-place/force/自动搜索/自动推断 ✓

**审计隐私（L872-921）**：`--audit` 可选且 `open("x")` 从不覆盖 ✓；issue 记录仅 eventId/planId/position/单值 rawValue（NaN→"NaN"、Infinity→"Infinity"、bytes→`<blob:N bytes>`）；summary 含 UTC 时间戳/版本/mode/路径/SHA/user_version/计数/退出码；repair 摘要区分 input 阻断数与零剩余 ✓；`allow_nan=False` ✓；不含 extras/doseMG/完整 plan 数组/用户名/完整行 ✓

**main（L1019-1077）**：分层退出码（0/1/2/3/4/5）；ToolError 失败时 audit 仅写 summary；未知异常 → EXIT_INTERNAL ✓

---

## Test quality verdict

**独立核实（非采信报告）**：

- `python -m unittest discover -s tools/repair-v2 -p "test_*.py"` → **Ran 84 tests, OK**（Python 3.14.6 实测）✓
- 测试类结构：`RepairV2TestCase` 基类 + setUp/tearDown 用 `tempfile.TemporaryDirectory`；`create_v2_database`/`insert_event`/`insert_plan`/`write_manifest` 合成 helper ✓
- **Java 固定向量硬编码独立预期**（L313-365），与本批 Kotlin `LegacyTimeAdapterTest` 向量逐值一致：
  - `0.0→0`、`1.0→3_600_000`、`-1.0→-3_600_000`、`472_222.22225638886→1_700_000_000_123`、`±1.388888888888889e-7→±1`、`±2_562_047_788_015.2153→±9_223_372_036_854_774_784`、`±2_562_047_788_015.216→OUT_OF_RANGE`、bool/NaN/Infinity 拒绝 —— **非调用被测实现生成预期** ✓
- 覆盖：身份失败（user_version 3/非 SQLite/缺表/缺列/错误 identity hash）、全部 scan 阻断类、WAL sidecar 拒绝、manifest 重复键/未知字段/非 canonical 时间/bool、显式修复组合、输入 SHA/mtime 不变、目标列隔离、回滚+输出删除、保留 v2 schema、verify 退出行为、JSONL 隐私 ✓

**独立 smoke（自包含临时脚本，精确断言）**：
- scan：exit 1，准确报告 2 个阻断项 `TIME_H_NON_FINITE` + `TIME_OF_DAY_NON_MINUTE`（+Infinity + `20:30:15` fixture）✓
- repair：exit 0，1 event + 1 plan 修正 ✓；输出 `timeH=12.5`、`timeOfDay='["08:30","20:30"]'`（compact）✓
- verify：exit 0，0 阻断 ✓
- 输出 `user_version=2`、无 `occurredAtEpochMillis` 列 ✓
- 输入 SHA 与 mtime 不变 ✓；临时目录清理 ✓

**Manifest 严格性独立抽查**：重复键 / 未知字段 / bool timeH / 非 canonical plan time → 全部退出码 2 拒绝 ✓

**CLI help**：三子命令（scan/repair/verify）正常显示 ✓

---

## Database/schema verdict

- `git diff` 对 app/src/main、app/schemas、app/src/test、app/src/androidTest、wear、gradle → **全空**（无任何生产/schema 变化）✓
- 工具不运行 MIGRATION_2_3、不升级 v3、不改 user_version/room_master_table ✓
- schema 2/3 无变化（继承 4A-1/4B 基线）✓

---

## Architecture boundary verdict

- 工具位于 `tools/repair-v2/`，独立于 Android 构建；不接 Repository/生产路径 ✓
- 无 Android/Room/JDBC/pandas/SQLAlchemy 依赖；纯标准库 ✓
- 未创建 Batch 3C/4B 后续内容 ✓

---

## Report accuracy verdict

逐项核对 `PHASE_1_BATCH_4C_REPORT.md`：

| 声明 | 独立核实 |
|---|---|
| 84 tests / 0 failures / 0 errors / 0 skipped（Python 3.14.6）| ✓ 实测 Ran 84 tests, OK |
| `py -3.12` 不可用；3.14.6 验证；3.12 兼容性声明 | ✓ 与本机一致（P2 如实记录）|
| CLI help 三命令 | ✓ 实测 |
| Smoke：scan exit 1 精确 2 阻断；repair/verify exit 0 | ✓ 独立复现（含精确 issue 类型）|
| 输入 SHA/mtime 不变；输出无 v3 列、user_version=2 | ✓ 独立复现 |
| manifest 重复键/未知字段/bool/非 canonical 拒绝 | ✓ 独立复现（exit 2）|
| 无真实数据；临时目录清理；无二进制 fixture 入库 | ✓ 代码+运行核实 |
| 生产/schema/Android 无修改 | ✓ git diff 全空 |
| v3 不可发布；3C 未开始 | ✓ 如实声明 |
| 未夸大：不声称 3.12 实机验证、不声称修复过真实数据库、不声称迁移本身被证明 | ✓ 全部如实（§15 明示限制）|

报告与代码/结果一致。

---

## Independent validation executed

以下全部为本轮实际执行：

| 项 | 结果 |
|---|---|
| `python --version` | Python 3.14.6 ✓ |
| `python -m unittest discover -s tools/repair-v2 -p "test_*.py" -v` | **Ran 84 tests, OK**（两次）|
| `repair_v2.py --help` | 三子命令正常 ✓ |
| Smoke scan（+Infinity + `["08:30","20:30:15"]` fixture）| exit 1，恰 2 阻断（NON_FINITE + NON_MINUTE）✓ |
| Smoke repair（manifest 1 event + 1 plan）| exit 0；EVENT_OK/PLAN_OK（`["08:30","20:30"]`）✓ |
| Smoke verify | exit 0 ✓ |
| 输出 user_version=2 / 无 v3 列 / 输入 SHA+mtime 不变 | 全部 True ✓ |
| Manifest 严格性（重复键/未知字段/bool/非 canonical）| 全部 exit 2 ✓ |
| `git diff`（生产/schema/test/androidTest/wear/gradle）| 全空 ✓ |
| `git diff --check` | 通过 ✓ |

---

## Final decision

### **APPROVE WITH P2**

无 P0/P1。唯一 P2（3.12 未实机验证）为环境限制，实施方已如实记录，不阻止提交。

**提交前必须处理事项**：无。

**可推迟事项**：
- F1：在 Python 3.12 环境补跑 unittest + smoke（CI 或本机安装后）
- Batch 3C（Repository 实现与生产接线，等 v3 schema/DAO 就绪）

**是否建议提交 Batch 4C**：是。提交建议信息：`feat: add offline v2 repair toolkit with scan repair verify`，并打标签 `phase-1-batch-4c`。

**提交后是否可进入 Batch 3C**：是，但仅在 4C 提交并打标签之后。Batch 3C 需按 PHASE_1_DESIGN §Batch 3C 与 ADR-015 实现完整无损双向 mapper、Repository Room 实现、plan+slots 原子 transaction、revision/idempotency/conflict 语义与 composition root 接线。

**v3 是否仍不可发布**：**是**。Room v3 仍处 ADR-016 §19.1 内部不可发布区间；Batch 4C 完成不等于 Batch 4 验收完成或可发布，最终 release 需满足 Batch 8 全部门槛。

---

*审阅结束。最终工作树：仅原 5 个 Batch 4C 文件 + 本审阅报告；未修改任何其他文件。*
