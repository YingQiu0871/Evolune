# Evolune v1.7 — 基线审计（V17_BASELINE_AUDIT）

> 状态：`V17 BASELINE FROZEN` + `PHASE-0 CORRECTION APPLIED`（2026-09-13）
> **Product/code baseline**：`main` @ `72a468c`（与 `origin/main` 同指，`git rev-parse` 验证）
> **Phase-0 freeze documentation commit**：`docs/evolune/v1.7/*` 的 docs-only commit（**不是**产品实现基线；SHA 见 Phase-0 correction report）
> 封版稳定版：`v1.6.0`（2026-09-10）
> 架构/产品门裁决 **Decision A–F** 原文见 [V17_SPEC.md 附录](V17_SPEC.md#phase-0-gate-decisions架构产品门裁决2026-09-13)；
> 本文件在 correction 轮中已修正引用行号、UTP 计数口径、remediation 表述、派生层消费者与持久化措辞；证据清单见 [V17_BASELINE_EVIDENCE.md](V17_BASELINE_EVIDENCE.md)。
> 审计方式：**只读**。证据来自 `*/src/main`、`*/src/test`、`*/src/androidTest`、Room schema JSON、manifest 与只读 git 命令。
> `docs/` 下叙述不作为运行时行为证据，仅用于定位。
> 配套：[规格](V17_SPEC.md) · [开发计划](V17_PLAN.md) · [数据语义](V17_DATA_SEMANTICS.md) · [验收](V17_ACCEPTANCE.md)

---

## 1. 基线与版本身份

| 项目 | 值 | 证据 |
|---|---|---|
| 分支 / HEAD | `main` / `72a468c` | `git rev-parse HEAD`、`git log --oneline -1` |
| 与远端关系 | 与 `origin/main` 同指（`## main...origin/main`，无 ahead/behind） | `git status -sb` |
| 冻结稳定版 | `v1.6.0`（tag 存在，已封存） | `git tag` |
| 模块 | `:app`（Phone）、`:wear`（Wear OS）、`:experience-core`（纯 Kotlin，无 Android 依赖） | `settings.gradle.kts:26-28` |
| 版本工具链 | AGP 9.0.1、Kotlin 2.3.10、Gradle 9.2.1、JDK 17、compileSdk/API 35 设备 | `gradle/libs.versions.toml:2`、`gradle/wrapper/gradle-wrapper.properties` |
| 版本校验任务 | `:validateEvoluneIdentityAndVersioning` 存在并通过 | `jvm-baseline-fresh.log:11` |
| 仓库规模 | 662 个 tracked 文件 | `git ls-files \| wc -l` |

### 1.1 工作树状态（**legacy primary checkout 不是** clean working tree）

> 适用范围说明：本节与 §9 的全部 dirty/hygiene 计量，**只描述 legacy primary checkout**
> `D:\Evolune-Workspace\current\Evolune-v1.2`（保留 954 个 v1.6 untracked 证据与 `.idea/*` 本地修改）。
> v1.7 的活跃开发 worktree `D:\Evolune-Workspace\worktrees\Evolune-v1.7`（branch `v1.7-development`）**是 clean 的**。

> 计量命令（Phase-0 correction 重新测量，精确清单与哈希见 [V17_BASELINE_EVIDENCE.md](V17_BASELINE_EVIDENCE.md)）：
> `git status --porcelain`、`git status --porcelain --ignored`、`git ls-files --others --exclude-standard`、`stat -c %s`。

| 类别 | 计量 | 说明 |
|---|---:|---|
| porcelain 未跟踪条目 | **958** | porcelain 条目数（含目录条目） |
| 未跟踪实际文件数 | **1028** | `git ls-files --others --exclude-standard \| wc -l` |
| porcelain 忽略条目 | **37** | `git status --porcelain --ignored`，`!!` 行 |
| tracked 修改 | **6** | 全部是 `.idea/*`（`.name`、`compiler.xml`、`deploymentTargetSelector.xml`、`gradle.xml`、`misc.xml`、`vcs.xml`）；`.idea` 已在 `.gitignore`，但这些文件历史上已被跟踪 |
| 其中：v1.6 原始证据 | **954 文件 / 144,253,218 B（137.570589 MiB）** | PNG 521、XML 282、LOG 144、TXT 7（`docs/evolune/v1.6/**`，未跟踪且未被忽略） |
| 其中：`release-artifacts/` | 未跟踪未忽略 **51 文件 / 1,413,018 B**；磁盘总计 **72 文件 / 86,120,966 B（82.13 MiB）** | 含 21 个被 `*.apk` 忽略的 APK |
| 其中：`.kotlin/` | 17 文件 / 79,514 B | Kotlin 构建会话目录 |
| 其中：`.tmp-app-tasks.txt` | 1 文件 / 1,989 B | 临时文件 |
| tracked 文档 `.md` | 106（`git ls-files 'docs/**/*.md'`） | 全仓 `.md` 为 161 |
| tracked `.apk` | 0 | 仓内无 APK（仅在磁盘与 GitHub Release） |

> 与 High 复审计量的差异（如实记录，不机械复制）：
> **计量修正（v1.7-A gate cleanup）**：本文早前记录的 v1.6 证据字节数（与一个偏低的 MiB 取整值）是**错误测量**——
> 统计脚本未正确处理文件名含双引号的 4 个文件，导致少算 73,407 B 与 4 个 PNG。
> 使用 `git ls-files -z --others --exclude-standard` + `stat -c %s` 重新测量得到
> **954 文件 / 144,253,218 B / 137.570589 MiB**（PNG 521、XML 282、LOG 144、TXT 7），与独立复审计量一致。
> `tracked docs *.md` 仍按本仓命令口径记为 **106**（`git ls-files 'docs/**/*.md'`）；其余项
> （958 porcelain 未跟踪条目 / 1028 未跟踪文件 / 51 release-artifacts 未跟踪文件 / 37 ignored / tracked APK 0）与复审一致。

> 规格/计划要求"clean final worktree or explicitly documented generated artifacts"。
> **legacy primary checkout** 如实记录为 dirty（见上述适用范围）；活跃 v1.7 worktree 为 clean。
> 卫生审计与最小提案见 §9（本轮不删除、不改 `.gitignore`）。

### 1.2 v1.6 完成状态

`v1.6.0` 已发布并封存，`docs/evolune/CURRENT_STATUS.md` 与 `docs/evolune/v1.6/V16_FINAL_RELEASE_GATE_2026-09-09.md`
记录了四个 Phone Widget、三个 Wear Tile、三个 Complication 的交付矩阵与发布门禁。
本轮审计**不重新验证**这些产品结论（属 v1.6 范围），仅确认：tag 存在、代码基线可构建、测试基线可复现（§11）。

---

## 2. 权威用药事实模型（authoritative medication facts）

### 2.1 存储

Room database v3，**只有三张表**（无曲线表、无缓存表、无审计表）：

| 表 | 实体 | 关键列 | 证据 |
|---|---|---|---|
| `medication_plans` | `MedicationPlanEntity` | `id`、`timeOfDay`（"HH:mm" 列表，legacy 冗余）、`createdAt`(epoch millis)、`isEnabled`、`daysOfWeek`、`intervalDays` | `data/MedicationPlanEntity.kt:15-32` |
| `scheduled_dose_slots` | `ScheduledDoseSlotEntity` | `id`（UUIDv5）、`planId`（**FK → plan，CASCADE**）、`localTime`("HH:mm")、`position`（与 planId 唯一） | `data/ScheduledDoseSlotEntity.kt:9-37` |
| `dose_events` | `DoseEventEntity` | `id`（PK，UUID）、`route`、`timeH`（legacy 影子列）、`doseMG`、`ester`、`extras`、`occurredAtEpochMillis`、`zoneId?`、`localDate?`、`slotId?`、`source`、`status`、`revision` | `data/DoseEventEntity.kt:16-36` |

关键结构性事实：

- `dose_events` **没有任何外键，也没有 `planId` 列**（`slotId` 是唯一能指向计划的间接引用，且可空）。
  → 计划/slot 删除不会级联删除历史事件；同时**事件一旦失去 slot，就无法再从数据本身判断它原本属于哪个计划**（见语义文档 §5）。
- `timeH` 与 `occurredAtEpochMillis` 是**双写且强一致**：写入时要求 `round(timeH*3.6e6) == occurredAtEpochMillis`
  且 `Instant` 具备毫秒精度，否则整条记录判 corrupt（`data/mapper/DoseEventEntityMapper.kt:134-158`、`:67-75`）。
- 事件身份的多重来源：`source ∈ {LEGACY, MANUAL, JSON_V1, REMINDER, WIDGET, WEAR}`（`core/model/DoseEventSource.kt:3-10`），
  `status` **只有一个值** `RECORDED`（`core/model/DoseEventStatus.kt:3-5`），另有 `revision`（乐观并发）。

### 2.2 occurrence 生成与身份

- 生成器：`experience-core/src/main/kotlin/io/github/yingqiu0871/evolune/experience/MedicationOccurrenceGenerator.kt:27-114`。
  输入为 `List<MedicationSchedule>` + 半开窗口 `[startInclusive, endExclusive)` + `ZoneId`；输出按
  `(scheduledAt, planId, slotPosition, slotId, id)` 排序（`:116-122`）。
- 只展开 `enabled = true` 的计划（`:40`）；激活锚点为 `createdAt` 在该 zone 的本地日期（`:63`），早于锚点的日期不产出（`:106`）。
- 计划类型：DAILY / WEEKLY（`daysOfWeek`）/ CUSTOM（按 `intervalDays` 从锚点起算，`:107-112`）。
- **身份**：`MedicationOccurrenceIdentity.derive(planId, slotId, scheduledLocalDate)` →
  canonical 名 `medication-occurrence:v1:plan=<uuid>;slot=<uuid>;localDate=<yyyy-MM-dd>` →
  `UUID.nameUUIDFromBytes(...)`（`experience/MedicationOccurrence.kt:93-110`）。
- ⚠️ **身份算法（精确口径）**：
  - **Occurrence**：`UUID.nameUUIDFromBytes(...)` = **RFC UUID v3 / MD5 semantics**（`experience/MedicationOccurrence.kt:93-110`，调用点 `:107-109`）。
  - **Slot**：手写 SHA-1 **UUIDv5**（`core/model/ScheduledDoseSlot.kt:42-142`，`uuidV5()` 位于 `:118-140`），
    并有固定向量测试（`ScheduledDoseSlotTest.kt:16`，值 `17d1fd14-9d70-5344-beaa-0b158c9f62f4`，断言 `version()==5`、`variant()==2`）。
  - 即：**slot id 是 v5，occurrence id 是 v3**。`.github/copilot-instructions.md` 只声明"稳定 occurrence identity"，未声明版本；
    把"稳定 UUIDv5 identity"套用到 occurrence 属**表述不精确**。
- **occurrence identity 缺 fixed golden vector**：现有测试（`MedicationOccurrenceIdentityTest.kt:15,23,31,43`）只断言关系性质
  （同输入稳定、不同输入不碰撞），没有锁定具体 UUID 值与 `version()`。
  **本轮不补测试**（Phase-0 correction 限定为非产品、非测试代码变更）；
  **fixed-vector / version 断言已列入 v1.7-A mandatory backlog**（见 [验收 §9](V17_ACCEPTANCE.md)）。
- 窗口上限：单次生成 ≤ 3660 天、≤ 100,000 条（`:16-25`、`:28`、`:77-79`）。

### 2.3 事件 → occurrence 的匹配策略（共享派生层）

`experience/MedicationTimeline.kt` 提供四阶段匹配（详见数据语义文档 §0 表）：

1. `slotId + localDate` 精确（`:100-117`）
2. `slotId` + 时间窗（无 localDate 的旧事件，`:119-136`）
3. `slotId == null` + 时间窗（含 legacy，`:138-174`）
4. `slotId == null` + 同日回退（仅第三阶段未消费且从无窗口证据者，`:176-208`）

判定为**逐事件唯一候选**才归属；`MedicationOccurrencePolicy` 默认前后各 1h、剂量容差 `1e-6`（`:38-52`）。
**该层的真实消费方式（精确口径，按 direct import 与调用点核对）**：

| 消费者 | 使用方式 | 证据 |
|---|---|---|
| `WidgetPresentation` | 直接用 `MedicationOccurrencePresentation.derive` | `widget/WidgetPresentation.kt:7`（import）、`:127`（derive）、`:122`（generator） |
| `WidgetWork` | 直接用 `derive` + 共享辅助函数 | `widget/WidgetWork.kt:26`、`:280`（derive）、`:271/:312`（`findPresentedEventForOccurrence`） |
| `WearAppSnapshotBuilder` | 直接用 `derive`（两处） | `wear/WearAppSnapshotBuilder.kt:8`、`:59`、`:80` |
| `findPresentedEventForOccurrence`（共享辅助） | 内部调用 `derive` 并取该 occurrence 的 `recordedEventId` | `application/OccurrenceConfirmationCoordinator.kt:6,34-40` |
| `ReminderReceiverWork` | **不直接 import `MedicationOccurrencePresentation`**：经 `findPresentedEventForOccurrence` 间接使用匹配，并用 `MedicationOccurrenceGenerator` 自行做 exact occurrence 重建（`singleOrNull { scheduledAt == … }`） | `reminder/ReminderReceiverWork.kt:7,15`（imports）、`:165/:212/:236`、`:267-271` |
| `WearAppConfirmationHandler` | 同上：经辅助函数间接匹配 + 自行 exact 重建 | `application/WearAppConfirmationHandler.kt:15`（import generator）、`:131/:163/:197`、`:265-276` |
| Phone UI（`HomeScreen`/`MedicationRecordsScreen`/`HRTViewModel`） | **完全不使用**该层：Home 用 `MedicationPlanPredictor`，记录页按 `occurredAt` 平铺 | `ui/screens/HomeScreen.kt:681-689`、`ui/screens/MedicationRecordsScreen.kt:233-255` |

`Widget` 的旧启发式读逻辑 `widget/WidgetUtils.kt:77-188` 是**无调用者的死代码**。

### 2.4 各来源写入的事件形态（决定匹配阶段）

| 来源 | `id` | `occurredAt` | `localDate` | `slotId` | 匹配阶段 |
|---|---|---|---|---|---|
| 提醒确认 REMINDER | `nameUUIDFromBytes("reminder:$planId:$scheduledAtMillis")`（`ReminderDoseFactory.kt:46-51`） | 点击时刻（手机时钟） | **计划日**（`:35`） | occurrence slot（`:39`） | 1 |
| Widget WIDGET | `nameUUIDFromBytes("widget-occurrence-action:v1:$occurrenceId")`（`LocalActionRecorder.kt:61-64`） | 点击时刻（手机时钟） | **记录日**（`WidgetWork.kt:434`） | Intent slot（`:438`） | 1（同日点击时）/跨午夜被前置校验拒绝（`:244`） |
| Wear App confirm WEAR | `nameUUIDFromBytes("wear-app-confirm-occurrence:v1:$operationId")`（`WearAppConfirmationHandler.kt:379-381`） | **手机时钟**（`:151`） | 命令携带（计划日，`:308`） | 命令 slot（`:312`） | 1 |
| legacy Wear Tile WEAR | 手表 actionId（`WearDoseActionHandler.kt:136`） | **手表时钟**（`DoseTileService.kt:227`） | 记录日（`:142`） | `null`（`:140`） | 3 |
| Phone 手动 MANUAL | 随机 UUID（`DoseEventEditor.kt:32`） | 用户选择/now，分钟截断（`:67-69`） | 记录日（`:75`） | `null`（`:79`） | 3 或 4 或未归属 |
| JSON 导入 JSON_V1 | 导入文件内 id | 文件内时间 | — | — | 视字段 |
| legacy 迁移 LEGACY | 原 id | `round(timeH*3.6e6)` | `null`（强制） | `null`（强制） | 3 |

### 2.5 手工事件

`DoseEventEditor` 的 `createNew()`（会话）与 `createQuickEvent(plan)`（快速补录）都产出
`slotId = null` + `source = MANUAL` + 记录时区/日期（`DoseEventEditor.kt:37-84`）。
编辑时：仅当用户改了 `occurredAt` 才重算 `zoneId/localDate`，否则沿用原值（`:144-150`）。
无"不得晚于现在"的校验，日期选择器未设 `selectableDates`（`ui/components/MedicationRecordBottomSheet.kt:398-434`）。
`revision` 用于更新路径的乐观并发（`RoomDoseEventRepository.update:100-148`）。

### 2.6 undo / 删除

- **物理删除**，无 tombstone、无状态位、无反向事件：`DoseEventRepository.delete` 注释即 "Physically deletes the event."（`core/dataapi/DoseEventRepository.kt:33`）。
- Phone：`HRTViewModel.deleteEvent` → `delete(id)`（`HRTViewModel.kt:266-279`，无条件删）。
- Wear App：`deleteLatestRecordedIfRevisionMatches`（事务内校验 revision + 仍是最新 RECORDED，`RoomDoseEventRepository.kt:175-211`）。
- Widget：无 undo。
- `deleteAll()` 作为维护操作存在（`:213-220`，被备份恢复等路径使用）。

### 2.7 幂等 / 重试 / 重复点击

- 写入侧：`RecordDoseEventEngine` 三种策略 `RepositoryStrict` / `FirstAcceptedBySource` / `FirstAcceptedBySourceAndOccurredAt`
  （`application/RecordDoseEventAction.kt:20-31`），配合**确定性事件 id**（表 2.4）与
  Room `@Insert(onConflict = IGNORE)`（`DoseEventDao.kt:49-50`）+ 整事件相等比较（`RoomDoseEventRepository.kt:81-95`）。
- 编排侧：`OccurrenceConfirmationCoordinator`（进程内 mutex，串行提醒/Widget/Wear confirm，`:15-26`）
  与 `WearAppMutationCoordinator`（Wear confirm/undo 串行）。
- **Phone 手动写入不参与任何互斥**（`HRTViewModel.kt:403-404` 直接 `repository.insert`）。
- 数据层**没有** `(planId, slotId, localDate)` 唯一约束（`DoseEventEntity.kt:16-36` 仅 PK），
  occurrence 级去重完全依赖读时派生 + 上述预检。

### 2.8 legacy / null-slot 语义

- v3 迁移后的 legacy 行：`zoneId = NULL`、`localDate = NULL`、`slotId = NULL`、`source = LEGACY`
  （迁移断言 `data/migration/AppDatabaseMigrations.kt:367-369`；映射 `data/mapper/DoseEventEntityMapper.kt:42-43,48`）。
- 旧 `timeH` 值**逐字节保留**（矩阵测试 `AppDatabaseMigrationTest.kt:72`、`:791-792` 锁定 identity hash）。
- 这类事件只能走第三阶段时间窗；第四阶段要求 `localDate != null`（`MedicationTimeline.kt:240`）。
- v1.2.2 的"同日唯一回退"语义完整保留在第四阶段，并由 `MedicationOccurrencePresentationTest.kt:223`
  （窗口竞争者不可被回退回收）等用例锁定。

### 2.9 时区与 DST

- 事件时间 = 绝对 epoch millis；计划时间 = 墙上 `"HH:mm"`；occurrence 展开时用调用方 `ZoneId` 折算；
  记录时把当时 `zoneId/localDate` 写死（详见数据语义文档 §10-§12）。
- DST **无显式处理**，依赖 `LocalDateTime.atZone`（gap 前移、overlap 取较早偏移），
  由 `MedicationOccurrenceGenerator.kt:69-72` 注释与三处测试锁定。
- 提醒与 Widget 注册 `TIME_SET` / `TIMEZONE_CHANGED` 全量重排；**Wear 无该类重发路径**（`wear/src/main/AndroidManifest.xml:185-186`）。

---

## 3. Mutation paths 收敛判定

| 入口 | 应用层入口 | 是否经 `RecordDoseEventEngine` | 最终写入 | 判定 |
|---|---|---|---|---|
| Phone 手动（编辑器 / 快速补录） | `HRTViewModel.handleInsert` (`HRTViewModel.kt:403-404`) | ✗ | `RoomDoseEventRepository.insert` | 基准 |
| Phone 提醒通知确认 | `LocalActionRecorder.recordReminder` (`:17-34`) | ✓ | 同上 | CONVERGES（仓储层） |
| Phone Widget | `LocalActionRecorder.recordWidget` (`:36-52`) + `OccurrenceConfirmationCoordinator` | ✓ | 同上 | CONVERGES（仓储层）/ 应用层入口不同 |
| Wear App confirm（页面 + 新版 Tile） | `WearActionRecorder.record` (`WearActionRecorder.kt:17-32`) | ✓ | 同上 | CONVERGES（仓储层） |
| Wear legacy Tile `/hrt/dose-actions` | `WearDoseActionHandler.handle:47-56` | ✓ | 同上 | CONVERGES（仓储层），但时间是手表时钟、slot 为 null |
| Wear App undo | `WearAppUndoHandler.handle` → `deleteLatestRecordedIfRevisionMatches` | —（删除路径） | `DELETE`（条件删除） | **DIVERGES**（与 Phone 无条件删除不同函数） |
| Wear App skip | `ReminderSkipStore.markSkipped` | —（不写用药事实） | SharedPreferences | **DIVERGES**；且该 DataItem path **未在 Phone manifest 注册**（见 §6 P1-1） |
| Wear Complication | — | — | 只读 | 无写入能力 |

**结论（严格版，按 High 复审要求区分"一个权威表"与"一个写入函数"）**：

- **只有一个权威 Room 数据库/表**：`dose_events` 是唯一用药事件表，不存在第二份 medication truth、第二套事件表或平行事件模型。
- **常规 record / confirm 路径**（Phone 手动、提醒确认、Widget、Wear App confirm、Wear legacy Tile）
  最终都经 `DoseEventRepository.insert` → `RoomDoseEventRepository.insert`（应用层策略不同，见下）。
- **backup restore 存在 direct DAO bypass**：`RoomRestorePersistence.kt:32/46/56` 直接使用
  `database.doseEventDao()`（`getAllEventsForRestore` / `deleteAllEventsIfPresent` / `insertEventsForRestore`），
  **不经过** `DoseEventRepository` 接口。这是恢复事务的一部分，不是普通记录路径。
- **Mahiro JSON v1 导入**经 repository 接口写入（`MahiroJsonV1ImportService.kt:47 repository.insert(event)`）。
- **`DoseEventRepository` 接口本身**是读/写契约（`core/dataapi/DoseEventRepository.kt:8-49`），
  但它**不是**"所有 mutation 的唯一入口"：上述 restore 路径绕过它，Phone 手动路径绕过 engine，
  Wear undo 走条件删除函数而不是 `delete(id)`。
- **应用层策略不唯一**：Phone 手动路径不经 `RecordDoseEventEngine`、不参与 `OccurrenceConfirmationCoordinator` 互斥；
  undo 有两条严格度不同的删除函数（`delete(id)` vs `deleteLatestRecordedIfRevisionMatches`）；
  skip 不是用药事实但会改变用户对"未记录"的感知。
- v1.7 规格 Invariant 1（一份事实一个权威）**成立**（单表、无第二事实源）；
  Invariant 2（共享派生）在**读时投影**上成立，但不覆盖 Phone 手动写入的应用层策略差异与 Phone UI 未使用该层的事实；
  Invariant 6（跨面收敛）在"数据行"层面**不成立**（Wear/Widget/Phone 写入字段不同）。

---

## 4. PK 输入管线与回顾性可行性

### 4.1 现状

- 引擎：`pk/SimulationEngine.kt:137-198`，输入 `List<pk.DoseEvent>` + 体重 + `startTimeH/endTimeH` + 步数，
  时间轴为**自 1970 起的绝对小时**（`core/time/LegacyTimeAdapter.kt:26,58-59,69-73`），无时区参数。
- 生产取数：`RoomDoseEventRepository.getEventsForPk(asOf)`：`[asOf-30d, ∞)` 全取，
  其中非 `PATCH_REMOVE` 给药 < 20 次时回退为"最近 20 条"（`RoomDoseEventRepository.kt:52-69`，常量 `:234`）。
- 适配：`core/adapter/DomainDoseEventToPkAdapter.kt:10-29`，只取 `occurredAt`，route/ester 恒等拷贝，剂量不换算。
- 计算：`viewmodel/PkSimulationCalculator.kt:32-104`
  - 历史事件：`status == RECORDED && route != ANTIANDROGEN`（`:34-39`）
  - **未来计划虚拟事件**：`MedicationPlanPredictor.generateFutureEventsForDomainPlans(..., daysAhead = 15)`
    并做 1 小时冲突过滤（`:40-53`，`utils/MedicationPlanPredictor.kt:149-164`）
  - 区间**硬编码** `currentTimeH ± 24*15`，步长 12 点/小时（`:63-68`）
  - `baselineSimulationResult` = 纯历史；`simulationResult` = 历史 + 未来计划（`:69-92`）
  - 当前浓度取 full 结果（`:94-95`），UI 主曲线同（`ui/screens/HomeScreen.kt:355-369,399`）

### 4.2 对 v1.7-C 的可行性判定

| 能力 | 现状 | 判定 |
|---|---|---|
| 任意历史区间重建 | 引擎支持任意 `[start,end]`；但计算层无区间参数、UI 无区间输入、`getEventsForPk` 是"现在语义" | **缺 3 层入口**（PkSimulationInput 字段 / 区间取数 / UI 状态） |
| 区间取数 | `findOccurredBetween(start,end)` 已存在且被测试覆盖（`core/dataapi/DoseEventRepository.kt:14-18`），但 PK 路径从未使用 | 可直接复用 |
| 计划/实际区分 | 权威历史与预测虚拟事件在代码上区分清晰（虚拟事件不落库），但**没有类型级标记** | 需显式区分，避免 v1.7 把预测当历史 |
| 撤销语义 | 物理删除，无"已撤销"行 | PK 天然排除，但**无法表达"曾记录后撤销"** |
| 前史（长尾 depot） | 目前靠 30 天窗口隐式提供 | 区间重建需显式定义前史窗口 |
| 上界过滤 | `getEventsForPk`/`getEventsAfterOccurredAt` **无上界**；Phone/Wear 计算器无 `occurredAt <= now` 过滤（仅 `widget/WidgetWork.kt:97` 有） | **风险**：区间重建会把未来事件算进历史 |
| 结果持久化 | 无（`PKState` 仅内存） | 无历史曲线缓存，符合"派生"定位 |
| 数值锁定 | AUC `23285.499354395688` ±1e-9（`SimulationEngineTest.kt:43`）、1441 点（`:39-40`）、采样点（`:53`）、`concentration(124.0)`（`:55`）；适配层 parity：`maxObservedDelta`（`DomainDoseEventToPkParityTest.kt:22`）、逐点 ≤1e-6（`:54`）、AUC ≤1e-6（`:57`） | 回归骨架已具备 |

### 4.3 数值/单位风险（需 owner 澄清，非本轮改动）

- `PKParameters.kt:7-11` 注释写"通常为 10-15 L/kg"，实际 `VD_PER_KG = 2.0`（`:11`）；`K_CLEAR = 0.41`（`:17`）vs `K_CLEAR_INJECTION = 0.041`（`:24`）相差 10 倍。
- `Ester.E2 + INJECTION` 因 `k1Fast[E2]` 缺失而 `?: 0.0` → 模型静默返回 0（`ParameterResolver.kt:61-62` + `ThreeCompartmentModel.kt:160`）。
- 贴片佩戴时长依赖事件列表顺序（`SimulationEngine.kt:57-60`），而仓储两个分支排序方向相反（ASC vs DESC）。
- 上述均属**既存事实**，v1.7 期间不得在无授权情况下改动（规格 §2.2、计划 §10）。

---

## 5. 共享历史派生层：可直接复用的既有资产（对应任务 §5）

任务要求"先确认现有 domain/policy 哪些可以直接复用"。结论如下。

| 资产 | 位置 | v1.7 可用性 |
|---|---|---|
| occurrence 生成器（纯函数、窗口化、zone 显式） | `experience-core/.../MedicationOccurrenceGenerator.kt` | **直接复用**（History/Timeline/Insights 的 occurrence 来源） |
| occurrence 身份（plan+slot+localDate） | `experience/MedicationOccurrence.kt:93-110` | **直接复用**；注意是 v3-MD5，需在 v1.7 决定是否补固定向量测试 |
| 四阶段匹配策略 + policy | `experience/MedicationTimeline.kt:38-266` | **直接复用**，是"plan+event → 历史投影"的现成实现 |
| 事件侧投影 `toRecordedMedicationEvent()` | `core/presentation/MedicationOccurrenceDomainMapper.kt:42-55` | **直接复用**（只取 RECORDED） |
| plan → schedule 投影 `toMedicationSchedule()` | 同上 `:14-40` | **直接复用** |
| 时间线选择器 `MedicationTimelineSelector` | `experience/MedicationTimeline.kt:285-341` | 可复用（当前/前 N/后 N 窗口，非历史日视图） |
| 区间事件查询 | `core/dataapi/DoseEventRepository.findOccurredBetween` | **直接复用**（History 日/区间取数） |
| PK 适配与引擎 | `core/adapter/DomainDoseEventToPkAdapter.kt`、`pk/SimulationEngine.kt` | 复用（回顾性 PK 需补区间入口） |
| occurrence 状态枚举 | `MedicationOccurrenceStatus`（UPCOMING/DUE/RECORDED/PAST_UNRECORDED） | **复用但不足**：无"未匹配事件"、"已撤销"、"手动/孤儿"分类 |

> 结论：任务 §5 的"authoritative facts → shared historical derivation → History / Timeline / Insights / Retrospective PK"
> **不需要新建派生层**——第 1、2 层已存在（`experience-core`）。
> 但"复用"的**精确形态**见 §2.3 消费者表：只有 `WidgetPresentation` / `WidgetWork` / `WearAppSnapshotBuilder`
> 与共享辅助 `findPresentedEventForOccurrence` 直接调用 `MedicationOccurrencePresentation.derive`；
> `ReminderReceiverWork` / `WearAppConfirmationHandler` 是**经该辅助函数间接匹配 + 用 generator 自行做 exact 重建**；
> Phone UI 完全不使用该层。v1.7-A 的最小动作是
> **扩展该层的历史投影能力**（日视图 + 未匹配/孤儿事件 + 撤销后语义），而不是重写匹配逻辑。
> 未匹配事件与孤儿事件缺少类型，是本阶段发现的最大实现缺口（见 §7 P1-2）。

---

## 6. 与 V17_SPEC 的冲突与实现缺口（P0–P3）

### P0 — 无（无阻塞基线的缺陷）

未发现"两份权威事实""读路径改写真值"或"历史必然损坏"级别的缺陷。

### P1 — 需在对应阶段开始前解决或决策

| # | 事项 | 依据 | 影响阶段 |
|---|---|---|---|
| P1-1 | **Wear skip 链路静态不可达**：Wear 端会向 `/hrt/v1/wear-app/skip-notification` 发送 DataItem，但 Phone manifest 只注册了 `/hrt/v1/wear-app/commands` 与 `/hrt/v1/wear-app/request`（`app/src/main/AndroidManifest.xml:110-127`），手表 UI 仍提示"已发送跳过请求" | 穷尽 grep：该 path 常量仅被生产/消费代码引用，无 manifest 注册 | v1.7-F 跨面一致性；如需修复属**行为修复**，须单独授权 |
| P1-2 | **派生层无"未匹配/孤儿事件"类型**：计划删除、slot 身份替换、跨时区后的事件会失去归属但仍是权威行；现有枚举无法表达 | `MedicationTimeline.kt:17-22` 只有 occurrence 视角状态 | v1.7-A（规格 §8 "Deleted schedule after intake"） |
| P1-3 | **撤销不可见**：undo 是物理删除，History/PK 无法表达"曾记录后撤销" | `DoseEventRepository.kt:33`、`DoseEventStatus.kt` 单值 | v1.7-A（规格 §2.1 明列该项） |
| P1-4 | **跨时区/跨午夜动作被拒**：Widget 同日校验（`WidgetWork.kt:244`）、提醒/ Wear 精确重建（`ReminderReceiverWork.kt:283-296`、`WearAppConfirmationHandler.kt:254-277`）在时区变化后返回 Invalid | 时区审计（穷尽 grep） | v1.7-A/F（规格 §8 时区行） |
| P1-5 | **skip 与"未记录"不可区分**：skip 只在 48h 过期的 SharedPreferences 中 | `ReminderSkipStore.kt:13-55` | v1.7-B（规格 §3 禁止自创 missed 语义） |
| P1-6 | **测试零覆盖**：删除计划后事件存活、计划编辑后历史归属、时区 A→B、DST×匹配、Phone↔Wear/Widget 等价 | 覆盖率审计（§8） | v1.7-A/F |

### P2 — 需在文档/测试中收敛

| # | 事项 | 依据 |
|---|---|---|
| P2-1 | 文档口径"稳定 UUIDv5 identity"与实现不一致：occurrence = **v3/MD5**（`UUID.nameUUIDFromBytes`），slot = **v5/SHA-1**（手写） | `MedicationOccurrence.kt:107-109` vs `ScheduledDoseSlot.kt:42-142`（`uuidV5()` 在 `:118-140`）；occurrence 缺 fixed golden vector → v1.7-A mandatory backlog |
| P2-2 | Phone 手动写入绕过 `RecordDoseEventEngine` 与互斥，与 Wear/Widget/提醒路径策略不一致 | `HRTViewModel.kt:403-404` |
| P2-3 | `getEventsForPk` 无上界；Phone/Wear 计算器无 `occurredAt <= now` 过滤 | `DoseEventDao.kt:31-38`、`WidgetWork.kt:97` 对比 |
| P2-4 | 死代码：`widget/WidgetUtils.kt:77-188`（无调用者）、`data/DoseEventRepository.kt`（旧类 `getEventsForSimulation` 无调用者） | 覆盖率审计 |
| P2-5 | DST gap 日 `scheduledLocalDateTime`（请求的墙上时间 = user intent）与 `scheduledAt`（valid instant）此前缺少语义区分与校验 —— **语义已由 Decision D 裁决**，仍需在 v1.7-A 呈现层落实并测试 | `MedicationOccurrenceGenerator.kt:86,91`；Decision D 见 [V17_SPEC.md 附录](V17_SPEC.md#phase-0-gate-decisions架构产品门裁决2026-09-13) |
| P2-6 | 派生层默认 policy 与提醒/Widget 的 1h 边界在多处重复定义（`DoseCheckInMatcher.kt:7`、`MedicationTimeline.kt:39-42`、`MedicationPlanPredictor.kt:26`） | 三处独立常量 |

### P3 — 记录，不阻塞

- Widget 失败路径静默（`EvoluneWidgetReceiver.kt:114-121` 丢弃 outcome）；导出/分享失败无用户反馈。
- `MedicationRecordsScreen` 无日期分组，与 v1.7 History 的日视图目标不同（属新功能，不是缺陷）。
- 数值注释与实际取值不符（`PKParameters.kt:7-11`）。
- `widget` 的 `RECORD` 按钮仅在 TODAY_PLAN / LEGACY_DEFAULT 渲染（`EvoluneWidgetReceiver.kt:212-221`）。

---

## 7. 规格 ↔ 实现 逐条对照（规格 §8 边界用例）

| 规格要求 | 现有实现证据 | 判定 |
|---|---|---|
| Cross-midnight occurrence 不误判归属 | 生成按日历日、无位移；但 Widget 跨午夜点击被前置校验拒绝（`WidgetWork.kt:244`） | 部分成立；需决策（语义文档 UNRESOLVED #6） |
| 昨日临午夜事件（legacy null-slot）与今日 00:00 occurrence | **可以匹配**：第三阶段允许 `+1h` **闭区间**跨 local-date 匹配（`MedicationTimeline.kt:217-220`）；这是 **code-derived behavior，目前没有该精确跨日边界的回归测试**（既有用例覆盖同窗口内优先/拒绝，见数据语义 §1） | 与规格原字面表述冲突 → 已按 **Decision F** 修订规格 §8：改为 provenance/ambiguity 要求（"Legacy inferred match" 不得伪装成 exact），**不要求修改现有 matcher** |
| legacy null-slot 歧义保持 | 第三/四阶段 + 唯一候选判据 + 测试 `:223` | 成立 |
| 同药多剂量确定身份 | slot 身份稳定（v5 + 固定向量） | 成立 |
| 同日同药同剂量不误配最近事件 | 唯一候选判据，歧义不归属（测试 `:132`） | 成立 |
| 手动历史录入正确呈现 | 事件可存；但派生层无"手动/未匹配"分类 | **缺口（P1-2）** |
| undo 按既有语义回退 | 物理删除，历史无痕 | **缺口（P1-3，需产品决策）** |
| 计划编辑后历史行为明确 | 无定义、无测试 | **缺口（UNRESOLVED #1）** |
| 计划删除后历史不消失 | 结构上事件保留；零测试 | **缺口（P1-6）** |
| 时区变化保留绝对时刻 | `occurredAtEpochMillis` 绝对 | 成立 |
| DST 不产生重复/丢失事件 | 不重复；但当天第二个重叠时刻**不物化** | 部分成立（语义文档 §12） |
| Wear 动作与 Phone 历史结果一致 | 数据行**不等价**（字段/时间源不同），投影可能一致 | **部分成立**，需在验收中改为"投影一致 + 差异已文档化" |
| Widget 动作与 Phone 一致 | 同上 | 同上 |
| legacy 升级不损坏历史 | 迁移矩阵 + v1.4→v1.5 真机就地升级测试 | 成立 |

---

## 8. 现有测试覆盖（对 v1.7 的可用性）

- 规模：JVM 测试文件 103（`app` 82 / `wear` 11 / `experience-core` 10），设备测试文件 50（`app` 45 / `wear` 5）。
- 框架：无 Robolectric、无 mock 框架，全部手写替身；Room 用真实文件库 + `MigrationTestHelper`（in-memory 仅 1 处）。
- 时间全部可注入（`Clock.fixed`、`DoseEventEditSessionFactory(idSupplier, clock, zoneIdSupplier)`）。
- 并发用例使用 `Mutex + CompletableDeferred` 做确定性 interleave（非概率 race）。

覆盖强度（相对 v1.7 需要）：

| 主题 | 强度 | 备注 |
|---|---|---|
| 四阶段匹配 / 精确优先 | COVERED | `MedicationOccurrencePresentationTest` 23 用例 |
| legacy / null-slot / 同日回退 | COVERED | 含窗口竞争者不可回收 |
| 同日多次归属歧义 | COVERED | 歧义不归属 |
| 幂等 / 重试 / 并发 | COVERED | 含 `getCalls/insertCalls` 计数锁定 |
| 手动补录 | COVERED | 无"指定 slot 补录"用例 |
| PK 数值回归 | COVERED | 单组黄金值 + parity |
| 迁移 v2→v3 对历史影响 | COVERED | 含 2000 事件/100 计划长历史 |
| occurrence identity | **PARTIAL** | 仅关系性质，无固定向量/版本断言 |
| 跨午夜 | **PARTIAL** | 仅 Wear 今日汇总路径有实证 |
| 计划修改后历史 | **PARTIAL** | 仅锁定 slotId 复用，无历史事件归属断言 |
| 时区变化 | **PARTIAL** | 无"时区 A 记录 → 时区 B 读取/确认/撤销" |
| DST | **PARTIAL** | 仅生成/换算侧，无匹配/去重/撤销 |
| Wear↔Phone / Widget↔Phone 等价 | **PARTIAL** | Phone 无 occurrence 级动作，无法直接比对 |
| **删除计划后历史事件存活** | **NONE** | 零用例 |
| Phone/Widget undo | NONE（Widget 无该功能） | Wear undo 有 COVERED |

---

## 9. 仓库卫生审计（REPOSITORY HYGIENE AUDIT，只读，本轮不执行任何删除/忽略变更）

### 9.1 现状计量

| 桶 | 未跟踪文件 | 体积 | 路径模式 | `.gitignore` 是否覆盖 | 同类 tracked 证据是否存在 |
|---|---:|---:|---|---|---|
| v1.6 原始证据 | **954** | **144,253,218 B（137.570589 MiB）** | `docs/evolune/v1.6/V16_*.png` (**521**)、`*.xml` (282)、`*.log` (144)、`*.txt` (7) | 否（`.apk` 除外） | **是**：`docs/` 下已有 19 个 tracked PNG/XML/LOG，其中 **16 个在 v1.6 目录**（12 PNG + 4 LOG），另有 106 个 tracked `docs/**/*.md` |
| Release 产物 | 磁盘 **72** 文件 / **86,120,966 B（82.13 MiB）**；其中未跟踪未忽略 **51** 文件 / **1,413,018 B** | 见左 | `release-artifacts/v1.6.0{,-final,-rc}/**`：21 APK（被 `*.apk` 忽略）、26 log、22 txt、1 md/xml/png | 部分（仅 APK） | 否（仓内 tracked APK = 0） |
| Kotlin 会话目录 | 17 | 140 KB | `.kotlin/**` | 否 | 否 |
| 临时任务文件 | 1 | 1.9 KB | `.tmp-app-tasks.txt` | 否 | 否 |
| IDE 状态 | 6（tracked 修改） | — | `.idea/*` | `.idea` 已在 `.gitignore`，但文件已被跟踪，规则对已跟踪文件无效 | 是（历史跟踪） |

### 9.2 五类分类（按任务要求）

1. **权威/发布证据，应长期保留**：
   `release-artifacts/v1.6.0-final/{Evolune-Phone-v1.6.0.apk, Evolune-Wear-v1.6.0.apk, SHA256SUMS.txt, RELEASE_NOTES.md}`
   与 `release-artifacts/v1.6.0/{BUILD-IDENTITY.txt, SIGNING-CERTS.txt, SHA256SUMS.txt}`。
   签名 APK 同时已作为 v1.6.0 GitHub Release 附件对外发布（`docs/evolune/CURRENT_STATUS.md` 记载），
   仓内副本是**冗余但可追溯**的。
2. **索引/摘要应保留，原始大文件可再生成**：`docs/evolune/v1.6/` 下 954 个 PNG/XML/LOG/TXT 属于此类——
   其结论已由 tracked 的 `V16_*_EVIDENCE.md` / `V16_*_REVIEW_*.md` 承载；原始文件依赖当时设备/模拟器状态，
   **不可确定性重建**（`release-artifacts/v1.6.0-rc/*/` 的构建日志可重建，模拟器截图与 UI dump 不可精确重建）。
3. **确定性生成物**：`release-artifacts/v1.6.0-rc/*/**.log`（构建日志）、`.kotlin/**`（Kotlin 构建会话）、`.tmp-app-tasks.txt`。
4. **本地 IDE 状态**：`.idea/*` 的 6 个 tracked 修改。
5. **未知/需决策**：`docs/evolune/v1.6/*.apk`（7 个，被忽略）——与 `release-artifacts/` 中的候选包疑似重复，需 owner 判断是否保留。

### 9.3 对 v1.6 审计可追溯性的影响

- tracked 的 `V16_*.md` 通过**文件名**引用原始证据（例如 `V16_EMU5558_*` 系列）。
  删除原始文件会让这些引用变成悬空引用（文件不存在），**削弱**但不会摧毁审计链（结论与哈希仍在 MD 中）。
- `release-artifacts/v1.6.0-final/SHA256SUMS.txt` 是签名包的权威校验记录，**不建议删除**。

### 9.4 最小 hygiene proposal（待批准，本轮不执行）

| 优先级 | 提案 | 理由 | 需要 owner 决策 |
|---|---|---|---|
| H1 | `.gitignore` 增加 `.kotlin/` 与 `.tmp-app-tasks.txt` | 纯构建/临时产物，永不需要入库，改动最小、零风险 | 否（低风险） |
| H2 | `release-artifacts/` 只**入库文本记录**（`SHA256SUMS.txt`、`SIGNING-CERTS.txt`、`BUILD-IDENTITY.txt`、`RELEASE_NOTES.md`），忽略 `release-artifacts/**/*.log`；APK 维持现有 `*.apk` 忽略 | 保留可审计的哈希/证书记录，不把 82.13 MiB 二进制与可重建日志带进仓库 | 是（是否接受"APK 仅在 GitHub Release"） |
| H3 | `docs/evolune/v1.6/` 的 954 个原始证据**打包为单个归档**（例如 `docs/evolune/v1.6/evidence-archive/…zip`，或移出仓库到 `D:\Evolune-Workspace\archive\v1.6-raw-evidence\`），仓库只保留 tracked 的 `.md` 索引 | 一次性解决 137.570589 MiB 噪声，同时保留可追溯性（文件名与字节不变） | 是（归档位置与是否入库归档） |
| H4 | `git rm --cached` 取消跟踪 `.idea/*` 6 个文件（保留本地文件） | 停止 IDE 噪声污染 `git status` | 是（会出现在下一次提交里） |
| H5 | 明确 `docs/evolune/v1.6/*.apk`（7 个）与 `release-artifacts/` 的关系，二选一保留 | 去除疑似重复的候选包 | 是 |

**本轮未执行任何一项**；未修改 `.gitignore`；未删除任何文件。

---

## 10. Phase 0 验证证据（fresh）

### 10.1 JVM 全量（fresh，`--rerun-tasks`）

命令：

```
./gradlew validateEvoluneIdentityAndVersioning :experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest \
  --no-daemon --console=plain --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 1m 34s`，`55 actionable tasks: 55 executed`。

| 模块 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `app`（testDebugUnitTest） | 689 | 0 | 0 | 0 |
| `experience-core`（test） | 84 | 0 | 0 | 0 |
| `wear`（testDebugUnitTest） | 90 | 0 | 0 | 0 |
| **合计** | **863** | **0** | **0** | **0** |

计数来源：**JUnit XML**（`build/test-results/**/*.xml` 的 `testsuite` 属性求和），
逐文件 SHA-256 manifest 见 [V17_BASELINE_EVIDENCE.md](V17_BASELINE_EVIDENCE.md)。
原始日志 `jvm-baseline-fresh.log` **不包含** test counts，它只证明命令、任务执行与构建结果（`BUILD SUCCESSFUL`）。

### 10.2 Android instrumentation（Pixel_7 AVD, API 35）

设备：`Pixel_7(AVD) - 15`（`sdk_gphone64_x86_64`，API 35）。
命令：`./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain`

结果：`BUILD SUCCESSFUL in 4m 28s`；**Phone：208 executed cases, 5 skipped**（
`app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_7(AVD) - 15-_app-.xml`，
`tests="208" failures="0" errors="0" skipped="5"`；SHA-256 见证据文档）：

| tests (total cases) | failures | errors | skipped | passed | time |
|---:|---:|---:|---:|---:|---:|
| 208 | 0 | 0 | 5 | **203** | 242.8s |

> UTP 控制台打印的用例计数**高于** JUnit XML 汇总（run 1 为 `Starting 0 tests`）。该差异**不是 retry 证据**，原因未定；
> 本次纠正撤回此前「UTP 计数含重试」的表述，并且**不引用该控制台计数**。权威计数一律以 JUnit XML 为准：
> **total test cases = 208 / skipped = 5 / passed = 203**（passed = tests − skipped）。

5 个跳过用例（均为既有 assumption/fixture 依赖，非失败）：

| 用例 | 说明 |
|---|---|
| `RepairToolOutputMigrationTest#repairedV2CopyMigratesAndProductionRepositoriesReadIt` | 依赖预置 v2 修复产物 fixture |
| `V15InPlaceUpgradeDeviceTest#prepareV14State`、`#verifyV15State` | 两阶段就地升级流程，需顺序执行的前置状态 |
| `FoldableNavigationLayoutTest#expandedRailAndSharedTopBarKeepStableGeometry`、`#topLevelTitleRemainsCenteredThroughoutPageTransition` | 折叠屏几何断言，需折叠屏设备条件 |

> 环境阻塞记录（**仅陈述可核验事实**）：
> - run 1（`androidtest-app-pixel7.log`）在 **test execution 之前**失败，错误为 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
>   （`androidtest-app-pixel7.log:103`），UTP 报告 `Starting 0 tests` / `Finished 0 tests`。
> - run 2（`androidtest-app-pixel7-rerun.log`）成功执行了完整 suite（JUnit XML：208 executed / 5 skipped）。
> - **repository / source / build script 没有为规避该错误发生任何改变**（见 §10.5 的 `git diff --name-only` 结论）。
> - 两次运行之间**具体的 device remediation 没有被当前 evidence capture**，因此**不能从日志独立证明**当时执行过什么设备操作。
>   本文档先前的"用 `adb install -r` 覆盖后重跑成功"表述属于未捕获证据的推断，**已撤回**，不再作为已证明事实。
>
> 与 v1.5 时期记录的差异：历史记录（v1.5 审阅）中同机 instrumentation 有 15 个 `ComposeTimeoutException` 失败；
> 本轮基线在 `v1.6.0` 代码上为 **0 失败**。该差异属观察事实，本轮不追查原因。v1.7 应以本表为对照基线。

### 10.3 未执行的验证（如实声明）

- Lint / 静态检查（v1.6 门禁已覆盖；Phase 0 未重复执行）。
- 真机（Samsung SM-F976B / SM-L500 / Pixel 11 Pro）验证：本阶段无产品代码改动，不涉及。
- Wear ↔ Phone 配对链路（Data Layer probe）需要配对设备，本轮无配对环境（见 §10.4.1）。

### 10.4 结果表

> 计数权威来源 = **JUnit XML**（不是原始日志；原始日志只证明命令、任务执行与构建结果）。
> XML 与日志的 SHA-256、源路径与重跑方式见 [V17_BASELINE_EVIDENCE.md](V17_BASELINE_EVIDENCE.md)。

| 验证 | 目标 | 结果（JUnit XML） | XML 源 | 原始日志 |
|---|---|---|---|---|
| JVM 全量（fresh） | 本机 JDK 17.0.19 / Gradle 9.2.1 | **863 通过 / 0 失败 / 0 错误 / 0 跳过**（app 689、experience-core 84、wear 90） | `app/build/test-results/testDebugUnitTest/*.xml`(80)、`experience-core/build/test-results/test/*.xml`(9)、`wear/build/test-results/testDebugUnitTest/*.xml`(11) | `jvm-baseline-fresh.log` |
| app instrumentation | `Pixel_7 AVD` API 35 | **208 executed / 0 失败 / 0 错误 / 5 跳过** | `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_7(AVD) - 15-_app-.xml` | `androidtest-app-pixel7-rerun.log`（run 2）、`androidtest-app-pixel7.log`（run 1，安装阶段失败） |
| wear instrumentation | `Wear_OS_Large_Round` AVD API 37 | **5 executed / 0 失败 / 0 错误 / 1 跳过** | `wear/build/outputs/androidTest-results/connected/debug/TEST-Wear_OS_Large_Round(AVD) - 17-_wear-.xml` | `androidtest-wear.log` |

#### 10.4.1 Wear 模块 instrumentation（补充执行）

设备：`Wear_OS_Large_Round(AVD) - 17`（`sdk_gwear_x86_64`，watch，API 37）。
命令：`./gradlew :wear:connectedDebugAndroidTest --no-daemon --console=plain`
结果：`BUILD SUCCESSFUL in 26s`；**Wear：5 executed cases, 1 skipped**
（`wear/build/outputs/androidTest-results/connected/debug/TEST-Wear_OS_Large_Round(AVD) - 17-_wear-.xml`，
`tests="5" failures="0" errors="0" skipped="1"`；SHA-256 见证据文档）。
UTP 控制台另打印 `Finished 6 tests`，**该差异不作为 retry 证据**，权威计数以 JUnit XML 为准。

| 用例 | 结果 |
|---|---|
| `WearLauncherIconResourceTest#launcherIconResolvesToAdaptiveLayers` | PASS |
| `WearManifestContractTest#finalLauncherManifestUsesValidIsolatedTaskAffinity` | PASS |
| `WearOccurrenceDialogEmulatorTest#actionButtonsShareOneVisibleRoundSafeRow` | PASS |
| `WearTileEmulatorPreviewTest#seedReadySnapshotAndRefreshGalleryTiles` | PASS |
| `WearDataLayerDeviceProbeTest#requestPlansFromPhoneReceivesAnAuthoritativeDashboard` | SKIPPED（需配对 Phone，本轮无配对环境） |

---

### 10.5 Phase-0 correction 轮的溯源核验

| 检查 | 命令 | 结果 |
|---|---|---|
| 无产品/测试源码改动 | `git diff --name-only HEAD` | 仅 `docs/evolune/v1.7/*` 为新增（untracked/staged）；无 `app/`、`wear/`、`experience-core/` 源码或测试改动 |
| 空白/补丁完整性 | `git diff --check` | 退出码 0 |
| 证据哈希 | `sha256sum`（原始日志）、JUnit XML | 见 [V17_BASELINE_EVIDENCE.md](V17_BASELINE_EVIDENCE.md) 的 manifest |
| 计数可核验 | 从 JUnit XML 的 `testsuite` 属性求和 | app 689 / experience-core 84 / wear 90 → 863 |
| 未触碰边界 | 无 schema/migration/依赖/版本/gitignore 改动 | 见 §10.5 表与 freeze commit 文件清单 |

> 本轮**未**重新运行完整 863 + instrumentation：变更仅为文档与证据，未触及任何可执行源码。

## 11. 门禁结论

**`V17 BASELINE FROZEN` + `PHASE-0 CORRECTION APPLIED`** —— 满足以下全部条件：

1. 基线与版本身份确定（`main` @ `72a468c`，`v1.6.0` 封版）。
2. 权威用药事实模型已由源码与 schema 证实（§2）：**单一权威 Room DB/表**（`dose_events`）；常规记录路径（提醒 / Widget / Wear / 手动 UI）**大体**经 repository 汇合（其中确认类动作再经 `RecordDoseEventEngine`）；**backup restore 存在直接 DAO bypass**；**Mahiro JSON v1 导入经 repository**。因此**不得**把它表述为「所有 mutation 必经同一函数」。
3. occurrence 身份与四阶段匹配策略已完整记录（§2.2、§2.3、数据语义文档）。
4. 手工事件、undo、幂等、legacy/null-slot、时区与 DST 均有源码级结论，未决项已显式标记 `SEMANTICS UNRESOLVED`（6 项）。
5. PK 输入管线与回顾性缺口已记录（§4）：**引擎具备、入口与取数缺失**。
6. 与规格的冲突与缺口已分级（§6 P0 无 / P1 六项 / P2 六项 / P3 若干）。
7. **legacy primary checkout**（`current/Evolune-v1.2`）如实记录为 dirty；**活跃 v1.7 worktree**（`worktrees/Evolune-v1.7`）为 clean。卫生审计与最小提案已给出且**未执行**（§9）。
8. fresh JVM 基线 863/863 通过（来源 = JUnit XML，见 [V17_BASELINE_EVIDENCE.md](V17_BASELINE_EVIDENCE.md)）；
   app instrumentation 208 executed / 5 skipped、wear instrumentation 5 executed / 1 skipped 见 §10。
9. Phase-0 correction（独立复审 `REQUEST_CHANGES` 后）已完成：Decision A–F 写入规格/语义/验收/计划，
   文档引用与计数口径已修正，evidence manifest 已建立，并创建 docs-only freeze commit（不改产品基线）。

### 进入 v1.7-A 前建议先落实（不阻塞冻结，但阻塞 A 的验收）

- 决策语义文档中的 6 项 `SEMANTICS UNRESOLVED`（尤其 #1 计划编辑的历史语义、#4 孤儿事件呈现）。
- 为"删除计划后事件存活""跨午夜跨日匹配""时区 A→B"补确定性测试骨架（可作为 A-03 的一部分）。
- 明确是否修复 P1-1（Wear skip 链路不可达）——若修复，属**行为变更**，需单独授权与独立审阅。

---

## 运行记录

| 项目 | 值 |
|---|---|
| 审计基线 | `main` @ `72a468c` |
| 审计日期 | 2026-09-13 |
| 审计者 | 开发代理（Phase 0 / A-00），只读 |
| 原始日志位置 | `D:\Evolune-Workspace\temp\v17-phase0\logs\`（工作区外，未污染仓库） |
| JVM fresh 日志 | `jvm-baseline-fresh.log`（863/0/0/0） |
| Android 日志 | `androidtest-app-pixel7.log`（首次失败：设备残留包签名冲突）、`androidtest-app-pixel7-rerun.log`（208/0/0/5）、`androidtest-wear.log`（5/0/0/1） |
| 模拟器 | `Pixel_7`（API 35，app instrumentation）、`Wear_OS_Large_Round`（API 37，wear instrumentation）；审计结束后均已关闭 |
| 仓库改动 | 仅新增本目录 5 份文档；无产品代码、无构建脚本、无 `.gitignore` 改动 |
