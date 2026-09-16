# V17 Phase E — Export & Data Portability — Contract

> 状态：`PHASE-E CONTRACT — REVIEW PENDING`（**PHASE-E PRODUCTION — NOT STARTED**）；
> docs-only contract；批准后未经重开评审不得改 Phase-E 语义。
> Slice：**V17-E — Export & Data Portability（E-01 export contract / E-02 CSV schema /
> E-03 JSON schema；E-04–E-07 生产在批准前未授权）**
> Base HEAD：`4c3be35b8fd417ae13d88d0569e5174ae3ded5f8`（Phase D CLOSED / FROZEN）
> 上游（约束性输入，**优先于本文件**）：
> - [`V17_PLAN.md`](V17_PLAN.md) §8（Phase E work sequence；"Export must not mutate source data"）
> - [`V17_SPEC.md`](V17_SPEC.md) §6（CSV minimum candidate fields / JSON portability /
>   30-90-all ranges / export-vs-backup separation）+ §12 Invariants（1 单一真相、2 共享派生、
>   5 读路径不得改写真值）+ §13 #5（export schema documented & deterministic）
> - [`V17_ACCEPTANCE.md`](V17_ACCEPTANCE.md) §6 E1–E8 + §0 G1–G7
> - Decision A（无可证历史计划时间）/ Decision C（事实优先，不造 Skipped/Missed）/
>   Decision G（legacy 时区归因）/ D-01 §10（delta 排除）
> - pre-contract audit：`review-packets/v17-phase-e-pre-contract-audit.txt`（strict read-only）
> Phase A/B/C/D（含 D-01…D-07）全部 CLOSED / FROZEN；Phase E **不得**重解释任何既有语义。

---

## 0. Source-verified identifiers（写死名字前已核对）

| 事实 | 位置 |
|---|---|
| 权威事件读接口：`observeAll()` / `getById(id)` / `findOccurredBetween(start, endExclusive)` / `findRecordedLocalDateBetween(...)` / `findAllOccurredUpTo(endInclusive)`（`occurredAt <=` inclusive，按 `(occurredAt, id)` 排序）/ `insert(event): InsertResult` / `update(...)` / `delete(...)` | `core/dataapi/DoseEventRepository.kt` |
| `InsertResult` 词汇：`Inserted` / `Idempotent` / `Conflict` / `Invalid` | `core/dataapi/RepositoryResults.kt` |
| 事件真值字段（domain）：`id: UUID`、`route: Route`、`occurredAt: Instant`、`zoneId: ZoneId?`、`localDate: LocalDate?`、`doseMG: Double`、`ester: Ester`、`extras: Map<ExtraKey, Double>`、`slotId: UUID?`、`source: DoseEventSource`、`status: DoseEventStatus`、`revision: Long(>=1)` | `core/model/DoseEvent.kt` |
| 持久化路由词汇（entity 存 `route.name`）：`INJECTION` / `ORAL` / `SUBLINGUAL` / `GEL` / `PATCH_APPLY` / `PATCH_REMOVE` / `ANTIANDROGEN` | `pk/Route.kt`；`data/DoseEventEntity.kt` |
| 持久化酯类词汇（entity 存 `ester.name`）：`E2` / `EB` / `EV` / `EC` / `EN` | `pk/Ester.kt`；`data/DoseEventEntity.kt` |
| extras 键词汇：`CONCENTRATION_MG_ML` / `AREA_CM2` / `RELEASE_RATE_UG_PER_DAY` / `SUBLINGUAL_THETA` / `SUBLINGUAL_TIER` / `ANTI_ANDROGEN_TYPE` | `core/model/DoseEvent.kt`（ExtraKey） |
| `ANTI_ANDROGEN_TYPE` 值域（ordinal Double 0–3）：`CPA` / `MPA` / `BICALUTAMIDE` / `SPIRONOLACTONE` | `pk/AntiAndrogen.kt` |
| `source` 词汇：`LEGACY` / `MANUAL` / `JSON_V1` / `REMINDER` / `WIDGET` / `WEAR` | `core/model/DoseEventSource.kt` |
| `status` 词汇：`RECORDED`（唯一值） | `core/model/DoseEventStatus.kt` |
| entity 持久字段集合：`id, route, timeH, doseMG, ester, extras, occurredAtEpochMillis, zoneId?, localDate?, slotId?, source, status, revision` | `data/DoseEventEntity.kt` |
| 现有 Mahiro v1 兼容面（legacy，不在本契约扩展）：`application/MahiroJsonV1ExportService.kt`、`application/MahiroJsonV1ImportService.kt`、`external/mahiro/v1/MahiroV1Codec.kt`、`external/mahiro/v1/MahiroV1DomainAdapter.kt` | audit §3/§5 |
| 现有 import/export UI：`ui/screens/DataImportExportScreen.kt` + `navigation/AppNavigation.kt` SAF/clipboard 路径；`export_filename`（translatable=false） | audit §4/§23 |
| 备份面（完全分离，E4 不得触碰）：`backup/EvoluneBackupV1.kt`、`EvoluneBackupCodec.kt`、`BackupRestoreCoordinator.kt`、`B2Restore*`、`cloud/google/*`、`.evbackup` 命名 | audit §8 |
| 死代码：`utils/MahiroJsonFormat.kt`（main 无 caller） | audit §3C/§53 |
| 资源权威：`values/strings.xml` + `values-zh-rCN/strings.xml`（无 `values-en/`） | 全项目约定（D-05 §32） |

---

## 1. Goal 与范围（冻结）

Phase E 冻结 **user-owned medication-history export and structured portability**，独立于
Evolune backup/restore，满足 E1–E8 与适用 G1–G7。

**In scope**：E-01 export contract、E-02 CSV v1 schema、E-03 Evolune Portable JSON v1 schema、
以及其后经批准的一次性生产实现（E-04 确定性序列化 / E-05 sharing-storage UX / E-06 隐私验证 /
E-07 round-trip fixture tests —— 见 §50）。

**Out of scope（冻结，禁止）**：full application backup；full-state restore；cloud sync；
History/Timeline 导出；PK-result 导出；settings/profile backup；plan/schedule backup；
新 locale/`values-en`；CSV import；任何 Phase-A/B/C/D 语义改动；新 library/dependency；
schema/DAO/migration 改动。

## 2. Portable truth scope（冻结）

Canonical Phase-E portable truth **仅包含 `dose_events`**，且包含精确可携所需的**全部
portable 事件语义字段（11 项）**：

```text
id, actual_time(occurredAt), local_date, zone_id, route, ester, dose_mg, extras,
slot_id, source, status
```

**revision 分类（R1 冻结）**：`revision` 是 **repository optimistic-concurrency metadata**，
**不是** Phase-E portable medication truth。Canonical export 不含 `revision`；canonical
import 不重建 `revision`（新导入事件由既有 repository insert contract 以 **`revision = 1`**
创建）。**No repository/DAO/schema reopen is authorized**（EG2）。

**明确排除**（不得进入 canonical 导出）：body weight、plans、scheduled slots、settings、
theme/preferences、tutorial/onboarding state、Drive state、Health Connect state、
Wear/Widget caches、PK series、Timeline projection、History projection、matching results、
generated occurrences、UI state。

理由（冻结）：**portable medication history != application recovery**。既有 legacy Mahiro
行为可以继续携带其历史 weight 字段（兼容面不动），但 **weight 不属于 canonical Phase-E schema**
（canonical export 不含、canonical import 不写 weight）。

## 3. 两套 JSON 系统 — 必须保持区分（冻结）

```text
A. Evolune Portable JSON v1
   - 新的 canonical Phase-E structured portability 格式
   - dose_events portable-field fidelity（11 项语义字段；`revision` 除外，见 §2）；
     import + export；由 Evolune 版本化
   - 用于满足 E2/E3/E7
B. Mahiro JSON v1
   - 既有 legacy compatibility 格式（外部/历史互操作）
   - 明确标注 legacy/compatibility；绝不成为 canonical Phase-E schema
```

- **禁止**给 Mahiro v1 增加 Evolune-only 字段；
- **禁止**静默重解释既有 Mahiro 文件；
- canonical 与 legacy 的 UI、文件名、schema、错误类型必须可区分（§35/§36/§44）。

## 4. Backup separation（冻结；E4）

以下全部保持冻结、不被 Phase E 复用/触碰：`EvoluneBackupV1`、`EvoluneBackupCodec`、
`BackupRestoreCoordinator`、B2 journal/protocol/transaction、Google Drive `appDataFolder`
行为、`.evbackup` 命名/加密/恢复语义。

Phase-E 格式**禁止**：复用 backup schema；复用 `.evbackup` 文件名空间；调用 backup restore；
替换应用状态；携带 OAuth credentials；携带 backup passphrase；写 Drive appDataFolder。
E4 architecture guards 见 §48（EG3–EG6/EG12）。

## 5. Canonical JSON identifier 与版本政策（冻结）

Evolune Portable JSON v1 顶层身份必须包含：

```json
"schema": "evolune-portable",
"version": 1
```

Import 必须在**任何写入之前**拒绝：

```text
missing schema / wrong schema / missing version / non-integer version /
version != 1 / future version
```

返回 typed unsupported/invalid-format 结果。**禁止** silent version fallback。
（对比：既有 Mahiro codec 有意不读 version、且接受无 meta 文档 —— 该 legacy 宽松性是**刻意
保留**的兼容面（§20）；canonical strict schema/version 边界由 Evolune Portable JSON v1 自己
提供，E2 不依赖加固 Mahiro。）

## 6. JSON 顶层 schema（冻结；Table A）

字段**有序**、名称固定、缺省即非法：

| # | field | type | semantics |
|---|---|---|---|
| 1 | `schema` | string | 必须 == `"evolune-portable"` |
| 2 | `version` | integer | 必须 == `1` |
| 3 | `captured_at` | string | §7；UTC RFC3339 毫秒精度、terminal `Z` |
| 4 | `range` | string | `LAST_30_DAYS` / `LAST_90_DAYS` / `ALL`（§8） |
| 5 | `events` | array | canonical 事件对象数组（Table B），按 §30 排序 |

No body weight. No plans. No settings. No derived data. 顶层仅此五字段；未知顶层字段
fail closed（§17）。`captured_at` 属于 export request/snapshot 输入，不违反确定性序列化
（§30/§31）。

## 7. Captured time（冻结）

- 一次 export action **只捕获一次** `capturedAt`；
- 归一化为 **epoch-millisecond 精度**；
- 同一值控制：range 边界、JSON `captured_at`、deterministic snapshot identity、文件名日期
  （仅命名）；
- Serializer **不得**调用：`Instant.now()`、`Clock.system*`、`LocalDate.now()`、
  `ZoneId.systemDefault()`；
- Same request + same snapshot -> byte-identical output（§31）。

## 8. Range model（冻结；Table E）

| range | membership | inclusive boundaries |
|---|---|---|
| `LAST_30_DAYS` | `startInclusive = capturedAt - exactly 30 * 24 hours`；`endInclusive = capturedAt` | 两端 inclusive |
| `LAST_90_DAYS` | `startInclusive = capturedAt - exactly 90 * 24 hours`；`endInclusive = capturedAt` | 两端 inclusive |
| `ALL` | 所有 stored dose events 且 `occurredAt <= capturedAt` | `capturedAt` inclusive |

- Range membership **不涉及当前设备时区**（绝对 Instant 运算，无 DST/日历语义）；
- `startInclusive` 边界事件 included；`endInclusive/capturedAt` 边界事件 included；
- `occurredAt > capturedAt` 的记录**永不**出现在任何 range；
- 实现读法（冻结）：30/90 用 `DoseEventRepository.findOccurredBetween(startInclusive,
  capturedAt.plusMillis(1))` 后按 `occurredAt <= capturedAt` 复核（`findOccurredBetween` 为
  half-open `[start, end)`，故 endExclusive 取 `capturedAt + 1ms` 实现 inclusive 语义）；
  ALL 用 `findAllOccurredUpTo(capturedAt)`；两者结果都必须再经 §30 排序后才进入序列化。

## 9. Export read authority（冻结；E1）

Canonical export 只通过权威 dose-event 真值读取：

```text
core/dataapi/DoseEventRepository（§8 的读 API；既有权 API，不新增 accessor）
```

**禁止**从以下任何来源读取：`HRTViewModel.events` state、Timeline、`HistoricalRange`、
History projection、Retrospective PK、DAO 直达。

Note（作用域澄清，冻结）：`findAllOccurredUpTo` 的既有 KDoc（V17-C-01 §4.1）注明该 all-history
通道“must not be called from outside the History layer”。本契约将该 export service 明确列为
approved consumer（只读、inclusive-≤ 语义完全一致）。若实现评审认为该 KDoc 限制具有约束力、
需要修改 repository 文档/接口或新增 accessor：**STOP 并请求 explicit Architect reopening**，
不得自行加读写旁路。

E1 要求 export 全程 **zero writes**（§47 E1.*；§58 计数型替身断言）。

## 10. Canonical event JSON fields（冻结；Table B）

每个 event object 字段**固定顺序**、全部必存（nullable 字段以 JSON `null` 显式存在，禁止
silently omit）。Canonical event 共 **11 字段**：

| # | field | type | semantics |
|---|---|---|---|
| 1 | `id` | string | 持久 UUID（canonical lowercase hyphenated）；§11 |
| 2 | `actual_time` | string | 持久 `occurredAt` instant；§12 |
| 3 | `local_date` | string\|null | 持久 `localDate` ISO `yyyy-MM-dd` 或 null；§13 |
| 4 | `zone_id` | string\|null | 持久 IANA `ZoneId` 字符串或 null；§13 |
| 5 | `route` | string | 持久 route wire（§0 词汇） |
| 6 | `ester` | string | 持久 ester wire（§0 词汇） |
| 7 | `dose_mg` | number | 有限数值，canonical decimal text（§15） |
| 8 | `extras` | object | 全部持久 extras；键 lexical 升序；值有限数值；§14 |
| 9 | `slot_id` | string\|null | 持久 `slotId` UUID 字符串或 null |
| 10 | `source` | string | 持久 source wire（§0 词汇） |
| 11 | `status` | string | 持久 status wire（§16） |

**`revision` 不在 canonical event schema 中**（§2 分类：repository optimistic-concurrency
metadata；import 不重建，新行由既有 insert contract 以 `revision = 1` 创建）。

**禁止**：planName、schedule context、medication identity inference（来自 UI label/plan）、
derived provenance。Canonical JSON 的 route/ester/extras 就是 lossless authority。

## 11. Event id（冻结）

- Canonical JSON `id` **必须是**合法存储 UUID；
- Canonical import **不得**生成替代 ID；
- malformed/missing/non-UUID id：**整份 canonical document 校验失败**，在第一次写入之前；
- Round-trip 必须精确保留 event identity。

## 12. Actual time（冻结）

`actual_time` = 持久 `occurredAt` instant。Canonical 编码：

```text
UTC RFC3339，精确 millisecond 精度，terminal Z
示例形状：2026-09-16T08:05:00.000Z
```

无 locale 相关格式；无当前设备时区转换；无秒以下/以上精度扩展。

## 13. Local date / Zone id（冻结）

精确、独立保留存储值：

```text
local_date : ISO yyyy-MM-dd 或 null
zone_id    : 存储 IANA ZoneId 字符串或 null
```

**禁止**从以下来源合成：`actual_time`、当前设备时区、当前 display zone、当前 plan。
Null remains null（保留歧义，不猜测；Decision G 语义不变）。

## 14. Route / Ester / Extras（冻结）

- route 与 ester 使用既有稳定 domain wire 词汇 = 持久 entity 字符串（`Route.name` /
  `Ester.name`，§0 表）；
- `extras` 必须保留**全部当前持久化 extras**（§0 六键词汇）；JSON object 键按 **canonical
  lexical order**（键名升序）；
- **禁止** UI-localized label（中/英文显示名）进入 canonical JSON；
- **禁止** unknown medication guessing；未知 extras 键（未来 schema）在 canonical import
  按 §17 fail closed。

## 15. Dose numeric format（冻结）

- `dose_mg` 是 finite JSON numeric；
- canonical textual representation：locale-independent、round-trip-safe 的十进制渲染，等价于
  canonical `Double.toString` 语义（dot 小数点；不使用 locale 小数逗号）；
- 拒绝 `NaN` / `+Infinity` / `-Infinity`；
- CSV 使用同一 canonical numeric text；
- **无单位换算**；unit 由 schema 定义为 mg。

## 16. Source / Status（冻结）

- 精确保留持久 `source` / `status`，使用稳定 wire 值（§0 词汇）；
- `status` 不映射为 missed/late/on-time/adherent/non-adherent（除非该字面值已存在于权威
  持久 enum —— 当前唯一值为 `RECORDED`）；
- **无** derived timing/adherence classification；
- `revision` 不属于 portable truth（§2）：export 不输出、import 不重建。

## 17. JSON unknown-field policy（冻结）

对 Evolune Portable JSON v1，以下全部 **FAIL CLOSED**：

```text
unknown top-level fields
unknown event fields
duplicate JSON keys（任意层级）
```

理由（冻结）：canonical import 不得静默丢弃未来/新语义。Malformed document -> **zero writes**。

## 18. Canonical JSON import（冻结；Table F）

Evolune Portable JSON v1 import 语义：

```text
additive —— 不是 restore，不是 replace，不 merge plans/settings
```

**在第一次写入之前**必须完成：完整解析全文档、校验 schema/version、校验每个 required
field、校验每个 event、校验 event IDs、校验 finite numbers、校验 enum wire values、
校验 extras 键词汇、校验大小上限（§32）。只有全部结构/语义校验通过后才允许 repository 写入。

**Collision equality（R1 冻结；R2 补充 stored-revision independence）**：canonical import 的
相等性判定覆盖 **11 个 portable 字段**（id + 其余 10 个），**有意忽略 repository `revision`**：

```text
same id + 其余 10 个 portable 字段全部相等 -> IDEMPOTENT（zero write；与 stored revision 无关）
same id + 任一 portable 字段不同          -> CONFLICT（拒绝、计数；不 overwrite）
id is absent from the repository          -> 构造 event 且 revision = 1，再经既有
                                             DoseEventRepository.insert 插入
```

（“absent id” 措辞澄清，R2：指 **id is absent from the repository** —— no existing repository
row for this valid canonical UUID。canonical document 自身仍要求 valid id（§11）；
missing/malformed canonical id 仍是 whole-document validation failure + zero writes。）

**Stored-revision independence（R2 冻结）**：

- 已存在行 **stored revision > 1** 且其余 10 个 portable 字段相等 -> **IDEMPOTENT**
  （zero write；**不得**仅因 stored revision 不同而判 Conflict）；
- 已存在行 **stored revision > 1** 且任一 portable 字段不同 -> **CONFLICT**
  （zero overwrite/write）——“ignore revision” **不得**变成 “ignore real content changes”；
- Implementability boundary（冻结）：允许使用既有 public repository seam
  `DoseEventRepository.getById(id)` 在 insert 分类前检查既有行，或等价 public-repository
  逻辑。**禁止**：DAO bypass、repository API extension、schema change、temporarily resetting
  revision、calling update to normalize revision、treating revision difference as portable
  conflict。

**禁止** DAO bypass；**禁止** repository API extension；**禁止** restore path。

| 场景 | 结果（观察性） |
|---|---|
| same id + same authoritative payload（11 portable 字段相等；stored revision 任意，含 >1） | **IDEMPOTENT**（不覆盖、不重复；忽略 revision） |
| same id + different authoritative payload（任一 portable 字段不同，stored revision 任意） | **CONFLICT**（拒绝该条、计数；不 overwrite） |
| new id（repository 中不存在） | **INSERTED**（revision = 1） |
| 语义等价但不同 id | 作为独立新事件插入（**无** semantic-equality dedup） |
| settings/plans | **不写**（canonical import 不触碰） |

**禁止**引入新的跨数据库/文件级 transaction（仅为 Phase E）；写入仅走既有
`DoseEventRepository.insert` contract（per-record transactional；`InsertResult` 词汇 §0）。

## 19. Storage failure / partial semantics（冻结）

Canonical import **不是** file-transactional（明确冻结，非意外行为）：

```text
全部记录先通过校验；
写入阶段：正常 idempotent/conflict 结果累计并继续处理；
意外 storage RuntimeException：中止剩余写入，返回 typed PARTIAL_FAILURE summary；
已提交记录保持 committed。
```

因为 canonical rows 始终携带稳定 UUID，**重试同一份 canonical 文件对已插入的 identical rows
是 replay-safe 的**（idents -> Idempotent；冲突行 -> Conflict；不会重复）。

## 20. Legacy Mahiro version policy（冻结 — permissive compatibility）

Legacy Mahiro JSON v1 保持**既有宽松兼容面**，Phase E **不引入**任何新的 Mahiro version
rejection：

```text
existing accepted documents remain accepted
meta may be absent
meta.version is NOT used as the canonical Evolune version authority
existing unrelated/unknown-field tolerance remains
existing ID-generation behavior remains
existing lossy field behavior remains
```

**禁止**把 Mahiro 改造成严格 schema；**禁止**声称 Mahiro 是 schema-strict；**禁止**用
Mahiro 作为 canonical E2/E7 证明。canonical strict schema/version 边界完全由 §5 的
Evolune Portable JSON v1 承担（format/UI/action 显式区分，见 §3/§35）。**Cross-format
no-fallthrough（R2 冻结）**：合法 canonical 文档提交到 legacy Mahiro surface 时不得被静默
接受、不得产生任何 dose-event 写入或 weight side-write（证据：E2.6）。

## 21. Legacy Mahiro id behavior（冻结）

保留既有 legacy 兼容语义：

```text
valid UUID id : preserved
missing/malformed/non-UUID id : legacy importer 可照现状生成新 UUID
```

因此重复成功导入 id-less Mahiro 文件可能产生新记录 —— 该限制必须作为 **LEGACY behavior**
记录。**禁止**把该规则应用于 Evolune Portable JSON。

## 22. Legacy import atomicity（冻结）

**不**把 Mahiro import 重设计为 backup-style restore。冻结/记录其既有 additive per-record
行为，并满足：

```text
possible 时在写入前完成 whole-document 结构解析校验（no new version gate）；
storage abort 时返回 typed partial summary；
不得有 uncaught exception 进入 UI；
不得复用任何 B2 restore machinery。
```

## 23. CSV role（冻结）

CSV v1 是 **EXPORT ONLY**（spreadsheets / analysis）。v1.7 Phase E **无 CSV import**；
**禁止**创建 CSV restore/import 路径。

## 24. CSV exact columns（冻结；Table C）

精确 v1 header order（**16 列**，顺序固定、无未文档化列）：

| # | column | semantics |
|---|---|---|
| 1 | `schema_version` | 常量 `1`（数字文本 `1`） |
| 2 | `event_id` | 持久 UUID 文本（稳定 identity，lossless support） |
| 3 | `date` | 持久 `local_date`，否则 empty（§25） |
| 4 | `medication` | informational deterministic identity projection；**完整映射表 Table D**；unknown/partial → empty |
| 5 | `dose` | canonical dose numeric text（§15） |
| 6 | `route` | 持久 route wire |
| 7 | `planned_time` | **v1 恒为 empty**（§25） |
| 8 | `actual_time` | canonical UTC RFC3339 毫秒 Instant（§12） |
| 9 | `timing_delta` | **v1 恒为 empty**（§25） |
| 10 | `event_type` | 恒为 `recorded_intake`（§26） |
| 11 | `status` | 持久 status wire only（§26） |
| 12 | `ester` | 持久 ester wire |
| 13 | `zone_id` | 持久 zone_id 文本，否则 empty |
| 14 | `slot_id` | 持久 slot_id 文本，否则 empty |
| 15 | `source` | 持久 source wire |
| 16 | `extras_json` | canonical compact JSON object（键 lexical 升序；§28） |

（`revision` 列已移除；R1 冻结 —— 不以任何其他 internal-concurrency 列替代，§2。）

Header 顺序文本（唯一权威列举）：

```text
schema_version, event_id, date, medication, dose, route, planned_time,
actual_time, timing_delta, event_type, status, ester, zone_id, slot_id,
source, extras_json
```

Header alone 标识一个 empty v1 export（零事件时仅 header + final LF）。

## 25. CSV date / actual time（冻结）

- `date`：存储 `local_date` 存在时输出；否则 **empty**。**禁止**从 device timezone、
  capturedAt timezone、current plan、`actual_time` 推导日期（保留 null/歧义归因）。
- `actual_time`：与 JSON 相同的 canonical UTC RFC3339 毫秒 Instant；合法持久事件恒有值。
- `planned_time`：列必须存在（SPEC candidate field set），v1.7 **恒为 empty**。理由（冻结）：
  Evolune 不为每个记录事件持久化 versioned historical prescription/scheduled timestamp；
  current-plan schedule context 不是历史处方真值；**禁止**读 Timeline/History generated
  occurrence context 来填充。
- `timing_delta`：列必须存在，v1.7 **恒为 empty**；**禁止**计算 `actual_time - current
  schedule time` 或任何等价 delta（保全 Decision A / Decision C / D-01 §10 / Phase-D
  timing-adherence freeze）。

## 26. CSV status / event_type（冻结）

- `status`：**仅**持久 dose-event status wire value；**禁止**派生 missed / skipped / late /
  on-time / overdue / adherence / compliance；
- `event_type`：冻结为常量 `recorded_intake`（每个 Phase-E CSV row）；manual/legacy 等
  provenance 由 `source` 列单独表达；**无** inferred behavioral classification。

## 27. CSV medication mapping（冻结；Table D，完整穷举）

`medication` 是 informational、deterministic、non-localized identity projection；lossless
authority 永远是 `route` + `ester` + `extras_json` 三列。

| 持久 route | 条件 | `medication` 输出 | 依据 |
|---|---|---|---|
| `INJECTION` / `ORAL` / `SUBLINGUAL` / `GEL` / `PATCH_APPLY` / `PATCH_REMOVE` | ester == `E2` | `E2` | ester wire（§0） |
| 同上 | ester == `EB` | `EB` | 同上 |
| 同上 | ester == `EV` | `EV` | 同上 |
| 同上 | ester == `EC` | `EC` | 同上 |
| 同上 | ester == `EN` | `EN` | 同上 |
| 同上 | ester 为任何其他值 | **empty** | PARTIAL/unknown：不猜测 |
| `ANTIANDROGEN` | `extras[ANTI_ANDROGEN_TYPE]` 为 integral 且 ∈ {0,1,2,3} | `CPA`(0) / `MPA`(1) / `BICALUTAMIDE`(2) / `SPIRONOLACTONE`(3) | AntiAndrogen wire（§0） |
| `ANTIANDROGEN` | extra 缺失 / 非 integral / 超出 0–3 | **empty** | UNAVAILABLE：不猜测 |
| 任何其他 route 值 | — | **empty** | 未映射：不猜测 |

**禁止**：planName、UI display strings（中文/英文显示名）、historical prescription
inference、antiandrogen 猜测、placeholder → real drug 推断。映射枚举是**封闭词表**；未来新
route/ester 值在无新合约前一律 empty（fail closed 到空而非猜测）。

## 28. CSV lossless support fields 与序列化（冻结）

`event_id` / `ester` / `zone_id` / `slot_id` / `source` / `extras_json` 存在的
目的即为防止 human-readable 列破坏事件真值（`revision` 不在此列 —— §2 分类）：

- `extras_json`：canonical **compact** JSON object（无 pretty 缩进；键 lexical 升序；数值同
  §15 canonical text）；UTF-8 内容由 normal CSV quoting 转义；
- CSV 表示必须足以**概念上**重建事件真值（即使 v1 不提供 CSV import）。

CSV 序列化（冻结）：

```text
UTF-8
NO BOM
RFC4180 quoting（comma delimiter；双引号转义为 doubled quotes；必要时整体加引号）
LF line endings
one final LF
固定 header 顺序 + 固定 row field 顺序（§24）
row 排序：actual_time ascending，然后 event_id lexical ascending（§30）
no locale-sensitive formatting
```

## 29. JSON serialization（冻结）

```text
UTF-8
NO BOM
LF line endings
one final LF
human-readable deterministic pretty JSON
```

冻结项：**缩进宽度 = 2 spaces**；字段顺序 = Table A/B 固定顺序；**null emission policy =
显式输出 null（不省略）**；number rendering = §15 canonical text；`extras` 键 lexical 升序；
`events` 数组顺序 = §30。Same `PortableExportRequest` + same repository snapshot ->
**byte-identical output**。

## 30. Deterministic event order（冻结）

CSV 与 canonical JSON **共同**排序规则：

```text
actual_time ascending
then event id lexical ascending（UUID canonical lowercase 文本序）
```

不依赖 DAO 返回顺序（`observeAll` 的 DESC 顺序等一律不作为序列化依据）；不复用 Timeline
presentation ordering。

## 31. Determinism input definition（冻结；E3）

E3 的 same-input 定义为：

```text
same format
same range enum
same normalized capturedAt（epoch-ms）
same authoritative event snapshot
=> identical bytes
```

不同 capturedAt 即不同 input。序列化内部**无** hidden clock call（§7）。

## 32. Size bounds 与 off-main（冻结）

- **No artificial export row cap**（E5 ALL 表示全部历史 ≤ capturedAt；不得设置任意事件数上限）；
- Export serialization **必须**运行 off main thread（`Dispatchers.IO` 或 repository-approved
  IO dispatcher）；legacy Mahiro serialization 被 UI 调用时同样必须 off-main；**禁止**在
  Compose click handler 内做 blocking full-history serialization；
- Import 大小上限（**必须**，在 unbounded parse/write 之前）：

```text
MAX_INPUT_BYTES   = 32 MiB
MAX_EVENT_COUNT   = 100000
```

  适用于：Evolune Portable JSON file import；legacy Mahiro JSON file import；legacy Mahiro
  clipboard import（byte count 可确定时）。超限 -> typed **TOO_LARGE** failure，**zero
  writes**；不得 crash/OOM；
- Large-history evidence fixture（约 20,000 events）为 evidence check，**不是**产品上限
  （§49）；不得由该 fixture 制造 performance SLA。

## 33. Export result model（冻结；P2-1）

所有 user-triggered export path 必须使用 typed result；**任何** production UI callback 不得
收到 uncaught serializer/domain `IllegalArgumentException`。至少建模：

```text
Success
InvalidData
IoFailure
TooLarge（仅适用路径）
UnexpectedFailure（safe user-facing mapping）
```

不得向用户暴露 stack trace / internal path / 文件系统路径（§37）。

## 34. Import result model（冻结）

Typed user-visible outcomes 必须可区分至少：

```text
success
partial success
invalid document
unsupported version
too large
conflict-containing result
storage failure
```

Exact sealed-type naming 可循当前架构，但上述 observable distinctions 为 mandatory。
**禁止**以 raw exception string 作为唯一 UX contract。

## 35. UX（冻结）

Data Import / Export surface 必须提供显式用户动作：

```text
Export Evolune JSON
Export CSV
Import Evolune JSON
```

- Canonical JSON/CSV 使用 **SAF** file flows（`CreateDocument` / `OpenDocument`）；
- Export action 要求显式选择 range：**30 days / 90 days / all history**；
- **No silent export；No automatic upload**；
- Legacy Mahiro 兼容继续可用，但必须与 canonical Evolune portability **visibly
  distinguished**：wording 等价于 `Legacy / Mahiro JSON v1 compatibility`；**禁止**把 Mahiro v1
  标注为新的 Evolune portable 格式；
- Clipboard policy（冻结）：
  - 新 canonical Evolune JSON/CSV：**NO clipboard** export/import（SAF only）；
  - legacy Mahiro clipboard 功能可保留；legacy clipboard **EXPORT** 前必须显示 localized
    privacy warning/confirmation（说明 medication data 可能留在系统剪贴板、可能依 OS 行为对
    其他 apps/devices 可见），用户显式确认后才执行；clipboard import 保持显式用户动作；
- Busy/progress（冻结）：percentage 与 cancellation **不要求**；SHOULD 显示简单 busy/loading
  state 并在 import/export 运行期间阻止重复触发（不得发明百分比估计）。

## 36. File names / MIME（冻结）

```text
Canonical JSON : MIME application/json ; 默认名 evolune-export-<range>-<yyyyMMdd>.json
CSV            : MIME text/csv           ; 默认名 evolune-export-<range>-<yyyyMMdd>.csv
<range> ∈ {30d, 90d, all}（小写固定 token）
```

文件名日期可使用 capturedAt 的 **UTC date，仅用于命名**；文件名不属于 medical truth。
Mahiro legacy 文件名空间保持分离；backup `.evbackup` 命名空间保持分离（E4）。

## 37. Privacy（冻结；E6）

Phase-E artifacts 含 plaintext medication history。冻结：

```text
explicit user-triggered creation only
no silent upload
no background export
no Drive upload
no credentials / OAuth token / passphrase
no local filesystem path
no exported data-body logging
no raw exported payload in error logs
```

SAF destination 由用户选择。不得做未测量的 security 断言（§49 E6）。

## 38. Round-trip guarantee（冻结；Table I）

对 Evolune Portable JSON v1：

```text
export from repository A -> import into an empty compatible repository B
must preserve all Phase-E portable medication-event semantics exactly:
id, actual_time, local_date, zone_id, route, ester, dose_mg, extras,
slot_id, source, status
repository concurrency revision is intentionally outside the portability contract.
```

除同一值的 canonical textual serialization 外**无任何 normalization**。
Required empty-repository round-trip preserves the 11 portable fields above；imported
repository rows 的 `revision` 从 **1** 开始 —— **不**归类为 semantic data loss。
再次导入同一份 canonical 文件：全部 identical existing rows（11 portable 字段相等）-> idempotent，
无 duplicate；conflicting same IDs -> 报告为 conflicts，不 overwrite。

## 39. E7 event classes（冻结；必需 fixture）

必须包含以下 fixture：

```text
normal app-recorded event
manual event
legacy imported event
event with null slotId
event with null zoneId
event with null localDate
event with partial/unavailable medication identity（medication 列 empty 的类别）
event with populated extras
ambiguous/orphan case represented by stored nullable/provenance truth
```

Export 必须保留事实；不得把 ambiguity 转换为猜测的 plan/schedule facts。

## 40. Mahiro compatibility guarantee（冻结；Table G）

**不**声称 Mahiro v1 是 full-fidelity。其 intentional legacy omissions 必须记录：

```text
zoneId / localDate / slotId / source / revision
plans/settings（除历史 weight 行为外）
```

**禁止**修改其 wire shape 来“补齐”这些遗漏；Mahiro 保持 compatibility，**不是** E7 的
canonical round-trip 格式（不得用作 E7 证明）。

| legacy 行为 | 冻结 |
|---|---|
| 输出形状/词汇/文件命名 | 保持现状（golden/固定时钟测试锁定） |
| import 对 unknown fields | 保持 v1 兼容忽略规则 |
| import version policy | **permissive legacy**：无新 version gate；meta 可缺失；meta.version 不作为 canonical Evolune version authority；既有接受行为保持（§20） |
| id 行为 | 由 §21 冻结（valid UUID 保留；missing/malformed 可生成新 UUID —— documented legacy limitation） |
| atomicity | 由 §22 冻结（additive per-record；typed partial；无 uncaught） |
| plans/settings/weight | 不扩展；Mahiro 的历史 weight 行为不变 |

## 41. Dead legacy facade（冻结）

`utils/MahiroJsonFormat`：**OUT OF REQUIRED PHASE-E CLEANUP**。要求：无新 production caller；
不得成为第三套 authority；removal 仅可经单独 hygiene 决定；不得仅因其死代码而制造 churn。

## 42. Accessibility / Localization（冻结）

- 所有新 user-visible strings 必须同时存在于当前两个权威：
  `values/strings.xml` + `values-zh-rCN/strings.xml`；**无 `values-en`**；
- 新交互控件使用正常 Compose accessibility semantics（不重开 D-05 Timeline-specific 语义）；
- 新增资源 parities guards 限定于新的 Phase-E key family（planning prefix family
  `portable_*`；实现时每个新 key 必须记录在实现映射中）；forbidden-vocabulary scan 覆盖新
  strings（adherence/grading 词汇 + 中文等价物）。

## 43. Frozen surfaces（冻结；Table H）

Phase-E 生产**禁止**依赖：`TimelineReadModel`、`TimelineProjectionBuilder`、
`TimelineRangeCoordinator`、`TimelineViewModel`、`TimelineScreen`、`HistoryRangeSource`
（作为 export truth）、`HistoricalProjectionBuilder`（作为 export truth）、Retrospective PK、
matcher/generator（作为 export truth）、`MedicationOccurrencePolicy`（作为 export truth）。
Portable output 只来自 stored dose-event truth；无 planned-time reconstruction。

Presumptively unchanged：Room schema、DAO/migrations、Phase-C PK、Phase-D Timeline、Home、
Wear、Widget、Health Connect、Google Drive backup、backup envelope/B2、Gradle/dependencies
（**无新 library 授权**）。若实现显示需要 schema/DAO/backup 变更：**STOP** 并请求 explicit
Architect reopening。

Export-vs-backup boundary（Table H）：

| 维度 | Evolune Portable JSON/CSV（本契约） | Backup `.evbackup`（冻结） |
|---|---|---|
| audience | user / analyst / other app | Evolune only |
| readability | plaintext | AES-GCM ciphertext + passphrase |
| content | dose_events（11 portable fields；revision 为 repository-local concurrency metadata，不在格式内） | plans + slots + full events + settings |
| versioning | `schema` + `version`（strict, fail-closed） | envelope/payload versions（strict, fail-closed） |
| restore path | none（additive import only） | B2 journaled full replacement |
| names | `evolune-export-<range>-<date>.json/.csv` | `evolune-backup-<ts>-<uuid>.evbackup` |
| code path | Phase-E export/import service | backup codec / B2 / Drive |

## 44. P2 closures（冻结 — 三项审计 P2 全部在契约内闭环）

- **P2-1（无 uncaught export exception / 无 main-thread serializer path）**：§33 typed export
  result 模型 + §32 off-main 冻结 + §27（legacy UI 序列化 off-main）+ §47 E6.1/E6.7。
  No UI callback may receive uncaught `IllegalArgumentException`；用户可见 localized error。
- **P2-2（version policy — R1 修正措辞）**：旧 Mahiro path **刻意保留为 permissive legacy
  compatibility surface**（§20）；Phase E **不**把它当作 canonical structured-portability
  contract，也**不**宣称它 fail-closed。新的 **Evolune Portable JSON v1**（§5）提供 E2/E7 所需的
  strict schema/version 边界（missing/wrong schema、missing/non-integer/wrong/future version、
  unknown canonical field、duplicate key -> 全部在任何写入前拒绝）。由于 UI/actions/format 显式
  区分（§3/§35），未来任何 canonical Evolune 文档**不会**静默落入 Mahiro parser。
- **P2-3（import atomicity 冻结）**：§18 全量预校验 + additive per-record writes；§19
  documented partial storage-failure 语义；§18 stable-ID replay safety（canonical；equality
  覆盖 11 portable 字段、忽略 revision；**stored revision > 1 不产生 Conflict**，证据 E7.6）；
  §21 legacy id rule documented。
- **revision 边界（R1 补充）**：exact repository revision/state preservation 属于 application
  recovery 语义，不属于 user-data portability；Phase E **不得**为保留 revision 而复制
  backup/restore machinery（§4/E4 不变）。

No P2 may remain “implementation choice”。

## 45. P3 dispositions（冻结）

| P3 | 处置 |
|---|---|
| dead `MahiroJsonFormat` cleanup | **OUT OF SCOPE**（§41） |
| input-size bound | **MUST for import**（§32） |
| progress | simple busy state **SHOULD**；percentage/cancel **NOT REQUIRED**（§35） |
| clipboard | legacy export confirmation **MUST**（§35） |
| main-thread serialization | **MUST move off-main**（§32/§43） |

不得把这些膨胀为无关 refactor。

---

## 46. Acceptance matrix（冻结；Table J）

Complete acceptance ID ranges（权威穷举，R2 更新）：
`E1.1–E1.2` · `E2.1–E2.6` · `E3.1–E3.3` · `E4.1–E4.4` · `E5.1–E5.4` · `E6.1–E6.6` ·
`E7.1–E7.6` · `E8.1`。任何声称穷举覆盖的表述必须与本文一致。

### E1 — 导出只读（zero writes）

| # | requirement | 判定 / 证据 |
|---|---|---|
| E1.1 | export（JSON 与 CSV）对所有 range 的执行**零 repository 写调用** | 计数型/失败型 repository doubles：写 API 计数 == 0；两格式 × 三 range 覆盖（共享实现证据，避免组合爆炸） |
| E1.2 | export 不修改任何 source 数据（无 revision/字段/行变更） | 同 doubles + snapshot 前后对比断言 |

### E2 — Schema 冻结并文档化

| # | requirement | 判定 / 证据 |
|---|---|---|
| E2.1 | Canonical JSON v1 schema 文档化：`schema`/`version` 标识、五顶层字段、事件字段顺序、null/empty 语义、wire 词汇 | 本契约 §5/§6/§10/§14/§16 + golden fixtures 绑定 |
| E2.2 | CSV v1 schema 文档化：16 列固定顺序、header-only 空导出、`schema_version=1` | 本契约 §24 + golden fixtures |
| E2.3 | import failure matrix：missing/wrong schema、missing/future version、unknown field、duplicate key、malformed JSON、invalid UUID、invalid enum、non-finite number、over-size、over-count -> typed failure + zero writes | JVM 测试矩阵（§47 EG19/EG17） |
| E2.4 | legacy Mahiro **compatibility-regression**（R1）：无 meta/version 文档 accepted；既有 v1 exported 文档 accepted；unrelated `meta.version` 值**不**成为 canonical Evolune version authority；既有 unknown-field 兼容保持；既有 wire spellings 保持；**无**新增 Mahiro version rejection | JVM legacy regression 补充（§20/§40） |
| E2.5 | CSV medication mapping 穷举且可测（Table D 每一行） | JVM fixture 覆盖每一条映射规则 |
| E2.6 | **Cross-format negative（R2）**：合法 Evolune Portable JSON v1 文档提交到 **legacy Mahiro import surface** -> zero dose-event writes + zero weight side-write + **无 format fallthrough**（不得以 “success + inserted canonical records” 呈现）；typed outcome 循既有 legacy result 模型。反向（Mahiro -> canonical）**已由 E2.3 覆盖**（缺 `schema`/`version` 即拒绝），不重复建 ID | JVM cross-format fixture（§3/§20/§35；`§10` 文档形状） |

### E3 — 序列化确定性

| # | requirement | 判定 / 证据 |
|---|---|---|
| E3.1 | same request + same snapshot -> JSON bytes 逐字节相同 | 重复运行比对 + golden fixture（固定 capturedAt） |
| E3.2 | same request + same snapshot -> CSV bytes 逐字节相同 | 同上 |
| E3.3 | 确定性组成项验证：排序、capturedAt 传播、number format、UTF-8、无 BOM、LF、final newline、CSV quoting、JSON field order | JVM 断言（§29/§30/§31） |

### E4 — Backup 分离

| # | requirement | 判定 / 证据 |
|---|---|---|
| E4.1 | portable JSON/CSV 代码不依赖 backup codec/B2/Drive | architecture guard（EG3/EG4） |
| E4.2 | backup 代码不消费 portable schema | architecture guard（EG5） |
| E4.3 | 文件名/扩展名空间 disjoint（导出 vs `.evbackup` vs legacy Mahiro） | guard + 命名断言（§36） |
| E4.4 | Phase-E import 永不调用 restore/full-state replacement | guard + 行为测试（EG6） |

### E5 — Ranges

| # | requirement | 判定 / 证据 |
|---|---|---|
| E5.1 | 30/90 边界：exactly startInclusive、1ms before start、exactly capturedAt、1ms after capturedAt 的 membership 正确 | 边界测试（§8） |
| E5.2 | ALL：全部 authoritative events `occurredAt <= capturedAt`，无 lower bound，无 future | 边界测试 |
| E5.3 | DST/device-zone 变化不改变 fixed request 的 membership | Paris/Shanghai/DST gap/overlap fixtures |
| E5.4 | large-history sanity fixture（约 20k events）产生确定性输出且 off-main | bounded fixture 证据（非 SLA、非上限） |

### E6 — 隐私

| # | requirement | 判定 / 证据 |
|---|---|---|
| E6.1 | 所有 export/import 均为显式用户触发；SAF destination 用户选择 | UI 测试 + 代码审查（§35/§37） |
| E6.2 | 无网络/upload：export bytes 永不进入 Drive/网络路径 | guard + 审查（EG15） |
| E6.3 | 无 credential/path leakage：payload/log 无 token/passphrase/文件路径 | 扫描 + fixture 断言（EG16） |
| E6.4 | 无 payload logging（不记录导出正文/原始 payload） | 审查 + guard（EG16） |
| E6.5 | legacy clipboard export 有 localized privacy warning + 显式确认；canonical 无 clipboard | UI 测试（§35） |
| E6.6 | UI gate：canonical JSON export / CSV export / range 30-90-all / canonical import / typed export error / unsupported version error / busy 阻止重复 / legacy 标注 / clipboard 确认 / back 稳定 | affected instrumentation（§49） |

### E7 — Round-trip / fixtures

| # | requirement | 判定 / 证据 |
|---|---|---|
| E7.1 | export -> import into empty repo：11 portable 字段语义精确保留（revision 不在内；imported revision 从 1 开始，不算 semantic data loss） | round-trip fixture（§38/Table I） |
| E7.2 | §39 九类事件 fixture 全部无损表示（含 null/partial/ambiguous） | fixture 测试 |
| E7.3 | CSV fixture：每类事件 truthful deterministic 表示，无 invented planned/delta/adherence | CSV golden 断言（§25/§26） |
| E7.4 | legacy Mahiro lossiness 保留为显式文档，不作为 E7 证明 | §40 记录（Table G） |
| E7.5 | replay：empty->inserted；same file again->idempotent；same ID changed payload->conflict（no overwrite）；storage failure after N writes->typed partial，前 N committed，retry 不重复 | import-service 测试扩展（§18/§19） |
| E7.6 | **Stored-revision independence（R2）**：既有 repository row = same id + 其余 10 个 portable 字段完全相等 + **stored revision > 1**；canonical artifact 携带同一 11 portable 语义、不含 revision；import -> **IDEMPOTENT** 且 **zero repository write**（`insert` 调用数 = 0、`update` 调用数 = 0、其它写入 = 0）、no overwrite / no duplicate / **不因 stored revision 不同而判 Conflict**（importer 必须独立于 repository revision 分类 portable equality）。**对照（同一 fixture family）**：same id + stored revision > 1 + 至少一个 portable 字段不同 -> **CONFLICT** + zero overwrite/write（确保 “ignore revision” 不变成 “ignore real content changes”） | JVM fixture（§18/§38）；分类允许用 `DoseEventRepository.getById(id)` seam 或等价 public-repository 逻辑 |

### E8 — 独立复审（process gate）

| # | requirement | 判定 |
|---|---|---|
| E8.1 | candidate implementation 后继以 Architect review + fresh independent review + final Phase-E closure（§50） | process record |

---

## 47. Guard matrix（冻结；Table K — EG1…）

| # | forbidden |
|---|---|
| EG1 | 任何 Phase-A/B/C/D（含 D-01…D-07）语义改动 |
| EG2 | Room schema / DAO / migration 改动（需要即 STOP + reopening） |
| EG3 | 复用 backup schema 或 `.evbackup` 文件名空间 |
| EG4 | portable 代码依赖 backup codec / B2 / Drive / credentials |
| EG5 | backup 代码消费 portable schema |
| EG6 | import 变成 restore/full-state replacement；写 settings/plans/schedule |
| EG7 | canonical schema 或 canonical import 携带/写入 body weight |
| EG8 | planName / UI label / 显示名作为 identity 进 canonical 输出 |
| EG9 | Timeline/History/Insights/PK 派生数据作为 export source |
| EG10 | 伪造 historical planned time 或计算 timing delta（两列 frozen empty） |
| EG11 | adherence/grading 词汇（missed/late/on-time/overdue/…及中文等价物）出现在输出或新 strings |
| EG12 | locale-sensitive 格式（小数逗号、locale 日期）；BOM；非 LF；无 final newline |
| EG13 | serializer/范围计算内 hidden clock（`Instant.now` / `Clock.system*` / `LocalDate.now` / `ZoneId.systemDefault`） |
| EG14 | canonical JSON/CSV 走 clipboard |
| EG15 | silent upload / background export / Drive write / 自动分享 |
| EG16 | credential / token / passphrase / 本地路径 / payload 正文进入 payload 或日志 |
| EG17 | 超限输入继续 parse/write（未在写入前执行 MAX_INPUT_BYTES / MAX_EVENT_COUNT） |
| EG18 | uncaught exporter/serializer exception 进入 UI；stack trace/path 作为用户文案 |
| EG19 | canonical import 在完整预校验前开始写入（validation failure 必须 zero writes） |
| EG20 | canonical strict schema/version 逻辑被委托给 Mahiro；Mahiro 被当作 canonical（或被改造成 strict schema）；Phase-E 实现为满足 canonical E2 versioning 而破坏既有 legacy decoder 兼容（permissive meta 行为、unknown-field 容忍、wire spellings）【证据：E2.6】 |
| EG21 | canonical import 重新生成 event ID（identity 必须保留） |
| EG22 | 依赖 DAO/返回顺序的序列化排序（必须 §30 排序） |
| EG23 | main-thread full-history serialization / blocking IO |
| EG24 | 新 library/dependency |
| EG25 | 新 locale / `values-en`；新 strings 缺任一现有权威；Phase-E key family parity 失守 |

---

## 48. Evidence gate（冻结；Table L）

Phase-E 生产实现完成后，必须（fresh）：

```text
focused Phase-E JVM（export/import/codec/guard 测试类）
fresh full JVM（:app:testDebugUnitTest :experience-core:test :wear:test --rerun）
affected Android instrumentation（§49 UI gate + 既有导入导出页面回归）
counts 一律来自 final JUnit XML
CSV/JSON golden byte fixtures（固定 capturedAt）
determinism repeat-run（E3）
zero-write counting（E1）
round-trip + replay suite（E7.1/E7.5）
revision>1 portable-equality fixture（E7.6：stored revision>1 相等 -> IDEMPOTENT + insert=0 /
update=0 / all writes=0；differing portable field -> CONFLICT + zero overwrite）
cross-format negative fixture（E2.6：canonical document via legacy Mahiro surface ->
zero dose-event writes、zero weight side-write、no format fallthrough）
boundary + DST/timezone fixtures（E5）
large-history bounded fixture（E5.4）
legacy compatibility-regression fixtures（E2.4：no-meta accepted / unrelated meta.version 非
authority / unknown-field 容忍 / wire spellings 保持）
resource parity（Phase-E key family）+ placeholder parity + forbidden-vocabulary scan
UTF-8 文本证据（manifest 前归一化；无 UTF-16；implementation.diff 用 git-native 字节重定向）
MANIFEST.sha256 + coverage + post-commit HEAD-blob verification（0 mismatch）
frozen-surface audit（EG1–EG25 对照 + git diff 边界）
```

不得未测量即声称 security/performance；不得做 7×…组合爆炸（bounded-matrix 先例沿用 D-05
§42.7）。

## 49. 实现后的 Phase-E 形态（冻结，非本次实施）

本契约批准后，production 为**一个** Phase-E candidate implementation，覆盖：

```text
E-04 deterministic serialization
E-05 sharing/storage UX
E-06 privacy validation
E-07 round-trip / fixture tests
```

随后：Architect implementation review → fresh independent implementation review → final
Phase-E closure。**不**为 E-04/E-05/E-06/E-07 额外制造 contract 循环（除非出现真实 blocker
才拆分）。

## 50. Documentation status

- 本契约状态：`PHASE-E CONTRACT — REVIEW PENDING`；
- **PHASE-E PRODUCTION — NOT STARTED**；
- Phase A/B/C/D 保持 CLOSED / FROZEN（含最新 Phase-D closure @ `4c3be35`）；
- 指针更新仅限 `TODO.MD` / `CURRENT_STATUS.md` / `ROADMAP.md` / `V17_PLAN.md` 的最小状态行；
  **NEXT: Phase-E contract review**。
