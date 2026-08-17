# Evolune Phase 1 Batch 5B 重新审阅报告（F1 复验）

**审阅日期**: 2026-08-05
**审阅者**: DeepSeek（独立重新审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch5b-plan-cutover`（前置 tag `phase-1-batch-5a`）
**第一次审阅**: `reviews/PHASE_1_BATCH_5B_REVIEW.md`（REQUEST CHANGES，P0/P1/P2 = 0/1/2，唯一阻断 F1）
**方式**: 只读重新审阅；未修改第一次 review/生产代码/测试/报告；未暂存/提交/打标签；未开始 Batch 6

---

## Executive summary

- **最终决定**: **APPROVE WITH P2**
- **P0/P1/P2**: **0/0/2**（两个 P2 为第一次审阅保留项，均非阻断）
- **F1 是否解决**: **RESOLVED**（根因 = 错误设备形态：Wear OS AVD 被当作 phone 验收目标；有效 API 35 phone 上全部通过）
- **是否允许提交**: 是
- **是否允许创建 `phase-1-batch-5` 标签**: 是（Batch 5B 提交 + 两次审阅记录一并提交后）
- **是否允许随后进入 Batch 6**: 是（Batch 5 封存后）
- **最大剩余风险**: 无 P0/P1。剩余 P2 为已知非阻断项（Reminder RuntimeException 捕获面、防御性 slot 排序）。

---

## Git and scope

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch5b-plan-cutover` ✓ |
| 前置 tag | `phase-1-batch-5a` 为 HEAD 祖先（exit 0）✓ |
| 暂存区 | 空 ✓ |
| 文件范围 | 与第一次审阅一致：7 修改 + 10 新增 + 更新报告 + 第一次 review（`git diff --stat` 754+/354- 与第一次审阅相同）✓ |
| F1 处理中新增生产/测试变化 | **无**（无 tracked 修改超出第一次审阅范围；测试文件 240 行与第一次审阅逐行一致）✓ |
| `PHASE_1_BATCH_5B_REPORT.md` 已更新 | ✓（§19 新增 F1 复验章节）|
| 第一次 review 未覆盖 | ✓（`reviews/PHASE_1_BATCH_5B_REVIEW.md` 存在且未修改；rereview 为新增文件）✓ |
| 数据库/APK/日志/真实数据 | 无 ✓ |

---

## Original review disposition

第一次审阅结论（REQUEST CHANGES，P0/P1/P2 = 0/1/2）：

- **F1 (P1)**: `MedicationPlansScreenTest` 3/5 在 Wear OS API 35 AVD（当时被误认为 API 35 手机）上失败——`plan-name` testTag 未显示。
- **F2 (P2)**: ViewModel Reminder 相关 catch `RuntimeException` 面较宽。
- **F3 (P2)**: `reminderOccurrences` 中 `slots.sortedBy { position }` 防御性冗余。

本轮重新审阅 F1 是否关闭；F2/F3 保留引用。

---

## Device-form-factor verdict

**独立设备核实（adb 实测）**：

| Serial | SDK | characteristics | model | wm size / density | 结论 |
|---|---|---|---|---|---|
| `emulator-5558` | 35 | `emulator`（**不含 watch**）| sdk_gphone64_x86_64 | 1080x2400 / 420 | **有效 API 35 手机**（Pixel_7 AVD，Gradle 设备名 `Pixel_7(AVD) - 15`）✓ |
| `emulator-5560` | 33 | `emulator`（**不含 watch**）| sdk_gphone64_x86_64 | 1080x2400 / 420 | **有效 API 33 手机**（Evolune_API33_Migration AVD）✓ |
| `emulator-5556` | 35 | `emulator,nosdcard,watch` | sdk_gwear_x86_64 | 454x454 / 320 | **Wear OS 手表**——非 phone UI 验收目标 ✓ |

**结论**：第一次审阅 F1 的失败设备确认为 Wear OS AVD（characteristics 含 `watch`、454x454 表盘尺寸）；`emulator-5558` 与 `emulator-5560` 均为有效手机（characteristics 不含 watch）。Wear OS 上的手机 Compose 测试结果**不应作为 phone UI 验收结论**——该论断成立。两个有效手机 AVD 均未触发 BLOCK 条件。

---

## F1 root-cause verdict

第一次失败的三方法复验（`MedicationPlansScreenTest`，240 行与第一次审阅逐行一致）：

1. **测试未被删除**：5 个 `@Test` 全部存在（L64/84/103/121/135）✓
2. **断言未放宽**：`assertIsDisplayed`（L81/99/100/117/118/149/150）与 `assertDoesNotExist`（L132）原样保留 ✓
3. **无 Thread.sleep**：grep 无命中 ✓
4. **无 timeout 扩大**：`waitUntil(5_000L)` 与第一次审阅相同（L93/111/129/143）✓
5. **无生产代码迎合错误 form factor**：生产文件 diff 与第一次审阅完全一致（无新增修改）✓
6. **有效 API 35 phone 上无需修改即通过**：见下节验证 ✓
7. **报告准确说明根因**：报告 §19 明确"Wear AVD result did not prove either a phone UI defect or a test timing defect"——未声称已确认 UI bug ✓

**F1 根因 = 错误设备形态**，非已确认的 UI bug 或测试缺陷。断言未弱化 → 无 P1。

---

## API 35 phone independent validation

`ANDROID_SERIAL=emulator-5558`（Pixel_7 AVD，API 35 手机），全部独立执行：

| 验证 | 结果 |
|---|---|
| `MedicationPlansScreenTest` 整类，连续 3 次 | RUN1 5/5 ✓；RUN2 5/5 ✓；RUN3 5/5 ✓（各 0 failed 0 skipped）|
| `invalidDraftSkipsRepositoryAndKeepsEditorOpen` 单独 2 次 | 1/1 ✓ ×2 |
| `saveFailureKeepsEditorOpenAndShowsError` 单独 2 次 | 1/1 ✓ ×2 |
| `deleteFailureKeepsEditorOpen` 单独 2 次 | 1/1 ✓ ×2 |

合计：整类 15 项 + 单方法 6 项 = 21 项独立执行全部通过，0 failures、0 skipped。

---

## API 33 phone validation

`ANDROID_SERIAL=emulator-5560`（Evolune_API33_Migration AVD，API 33 手机）：

- `MedicationPlansScreenTest`：5/5 PASS ✓
- `MedicationPlanProductionCutoverTest`：2/2 PASS ✓

第一次审阅已独立验证 API 33 全量 connected 75/75（Evolune_API33_Migration）——该结果来自第一次独立审阅，本轮引用。

---

## Full connected matrix verdict

`ANDROID_SERIAL=emulator-5558`（API 35 phone）：

- `connectedDebugAndroidTest`（全量）→ **75/75 PASS**，0 failures、0 skipped
- **JUnit XML 独立核实**（非仅 Gradle 最后一行）：`TEST-Pixel_7(AVD) - 15-_app-.xml` → `tests="75" failures="0" errors="0" skipped="0"` ✓

设备矩阵汇总（独立执行）：

| 设备 | ScreenTest | Cutover | 全量 connected |
|---|---|---|---|
| API 35 phone (5558, Pixel_7) | 5/5 ×3 + 3 方法 ×2 各通过 | 2/2 | **75/75（XML 核实）** |
| API 33 phone (5560) | 5/5 | 2/2 | 75/75（第一次审阅独立验证）|

---

## Rollback regression verdict

`MedicationPlanProductionCutoverTest` 在 API 35 phone 上独立运行 2/2 PASS；在 API 33 phone 上 2/2 PASS。

Rollback 真实性（第一次审阅已完整核实，本轮代码未变，结论保持）：真实 SQLite trigger（`BEFORE INSERT ON scheduled_dose_slots ... RAISE(ABORT)`）→ StorageFailure → plan 逐字段/slots 逐项/timeOfDay 逐字不变、无部分写入、Reminder 零调用、无 legacy fallback、version=3 ✓

---

## Test-integrity verdict

- 测试文件与第一次审阅**逐行一致**（240 行）；无删除/放宽/忽略/Thread.sleep/timeout 扩大 ✓
- 生产代码与第一次审阅**完全一致**（无"迎合错误 form factor"的修改）✓
- 固定 expected 硬编码（17d1fd14 等）保留 ✓
- 有效 phone AVD 上无需任何修改即通过 ✓

---

## Report-update verdict

报告 §19（L347-379）新增内容逐项核实：

| 要求 | 核实 |
|---|---|
| 记录第一次审阅 F1 | ✓（L349）|
| Wear OS 设备信息 | ✓（L351-354：API 35、sdk_gwear_x86_64、characteristics 含 watch、454x454/320）|
| 为何非 phone 验收目标 | ✓（L356 "invalid phone-UI target"；L362 "did not prove either a phone UI defect or a test timing defect"）|
| API 35 Pixel 7 phone 信息 | ✓（L362：emulator-5558、API 35、sdk_gphone64_x86_64、1080x2400/420）|
| 三个原失败测试 | ✓（L358-360）|
| 无生产代码或测试修改 | ✓（L362 "No production or test file was changed in response"）|
| 整类 5 次共 25 项 | ✓（L366）|
| 三方法各 5 次共 15 项 | ✓（L367）|
| API 33 + API 35 全量各 75/75 | ✓（L369、L375）|
| rollback 2/2 | ✓（L374）|
| P0/P1/P2 = 0/0/2 | ✓（L379）|
| Room v3 不可发布 | ✓（L345、L385）|
| 原 API 33 验证结果保留 | ✓（§13 L242-245 原 emulator-5556 API 33 记录未删改；§19 说明设备迁移至 5560）|

报告准确 ✓。

---

## Remaining P2 findings

第一次审阅的两个 P2 保留引用（重新核实仍真实存在、未升级、不阻止封存）：

1. **F2 (P2)** — `MedicationPlanViewModel.kt:316-325, 257-259`：Reminder 相关 catch `RuntimeException` 面较宽（与 Repository 异常路径分离；UI 层副作用可接受；不阻塞）。
2. **F3 (P2)** — `MedicationPlanReminderSchedule.kt:28`：`slots.sortedBy { position }` 防御性冗余（Domain 已强制有序；无害）。

两者均不因 F1 修复升级为 P1；审计记录完整保留。

---

## Schema and release-safety verdict

- `git diff` 对 schemas / migration / core / wear / HRTViewModel → **全空** ✓
- Room version=3；schema 2/3 与 MIGRATION_2_3 无变化 ✓
- contracts/Domain/DAO/Entity 无变化 ✓
- 未访问真实数据库；未创建 release ✓

---

## Findings

### F1 (P1→RESOLVED) — MedicationPlansScreenTest 设备形态误判

- **文件/测试**: `MedicationPlansScreenTest`（3 方法）；根因设备为 Wear OS AVD
- **问题**: 第一次审阅在 Wear OS AVD 上观察到 3/5 失败，误标为 API 35 手机问题。
- **依据**: 设备特性核实（characteristics 含 `watch`、454x454）+ 有效 API 35 手机（Pixel_7）上全量通过。
- **当前状态**: **RESOLVED**（根因 = 错误 form factor；非 UI bug、非测试时序 bug、无代码/测试修改）
- **是否阻止封存**: 否

### F2/F3 (P2) — 保留（见 Remaining P2 findings）

- **是否阻止封存**: 否

**无新的 P0/P1。**

---

## Independent validation executed

本轮实际执行（`JAVA_HOME=C:\Program Files\kedou\jre`）：

| 命令 | 结果 |
|---|---|
| `adb devices -l` + 3 设备 getprop/wm | 5558=API 35 phone、5560=API 33 phone、5556=Wear ✓ |
| `MedicationPlansScreenTest` 整类 ×3（5558）| 5/5、5/5、5/5 ✓ |
| 3 个原失败方法 ×2（5558）| 6 次全部 1/1 ✓ |
| `MedicationPlansScreenTest`（5560）| 5/5 ✓ |
| 全量 `connectedDebugAndroidTest`（5558）| **75/75**，JUnit XML `tests=75 failures=0 errors=0 skipped=0` ✓ |
| `MedicationPlanProductionCutoverTest`（5558）| 2/2 ✓ |
| `MedicationPlanProductionCutoverTest`（5560）| 2/2 ✓ |
| 测试文件与生产文件 diff | 与第一次审阅一致（无修改）✓ |
| 禁止文件 `git diff` | 全空 ✓ |

第一次审阅的 API 33 全量 75/75 结果明确来自第一次独立审阅（非本轮重跑）。

---

## Final decision

### **APPROVE WITH P2**

**F1 是否关闭**: **是（RESOLVED）** —— 9 项清除条件全部满足：5558 确认 API 35 phone；整类连续 3 次 5/5；3 个原失败方法独立重复通过；API 35 全量 75/75（XML 核实）；rollback 2/2；断言未放宽；无生产/测试修改；报告准确；无新 P0/P1。

**提交前必须处理事项**: 无。

**可推迟事项**:
- F2/F3（两个 P2，非阻断）
- Batch 6（等 Batch 5 封存）

**是否建议提交 Batch 5B**: 是。提交建议信息：`feat: switch medication plan UI to domain repository`（17 个文件）。

**是否建议提交第一次 review 和 rereview**: 是。第一次 review（`PHASE_1_BATCH_5B_REVIEW.md`）作为历史证据保留提交；本 rereview 随其后提交。

**是否建议创建 `phase-1-batch-5` 标签**: 是（Batch 5B 提交 + 两次审阅记录提交后）。

**是否建议随后进入 Batch 6**: 是（Batch 5 正式封存后；Batch 6 按设计切换 HRTViewModel/记录 UI/receivers/Widget/Wear 至 contract）。

**是否继续禁止真实数据库和 release**: **是**。Room v3 仍处 ADR-016 内部不可发布区间；任何情况下不得打开/升级真实用户数据库或创建 release。

---

*重新审阅结束。最终工作树：原 Batch 5B 文件 + 第一次 review + 更新报告 + 本 rereview；未修改任何现有文件。*
