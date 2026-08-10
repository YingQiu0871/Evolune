# Phase 1 Batch 7A Independent Review

Date: 2026-08-10
Reviewer: DeepSeek (independent read-only)
Worktree: `D:\Evolune-batch7a`
Branch: `phase1/batch7a-json-v1`

## Executive summary

**Decision: APPROVE WITH P2**

- P0 / P1 / P2 = **0 / 0 / 3**（一项兼容性注意项 + 一项验证边界 + 一项继承设计 P2；均不阻止封存）
- Protocol DTO: **PASS**
- Codec boundary: **PASS**
- Domain adapter: **PASS**
- ID semantics: **PASS**
- Time semantics: **PASS**
- Extras semantics: **PASS**
- Legacy compatibility: **PASS**
- Repository boundary: **PASS**
- PK boundary: **PASS**
- Widget boundary: **PASS**
- Wear boundary: **PASS**
- Custom medication boundary: **PASS**
- MedicationPlan boundary: **PASS**
- **7A may be sealed: YES**
- **7B may begin after sealing: YES**（仅 7A 提交/review/tag 封存后）
- **7C: NO**（未授权）
- **Batch8: reserved**
- **Release: forbidden**

## Git/scope verdict

- 分支 `phase1/batch7a-json-v1` ✓；HEAD = `ac0ce360...`（design review commit）✓；`phase-1-batch-7-design-v1` tag → 同一 commit ✓
- 暂存区空；仅 6 个未跟踪文件（3 生产 + 2 测试 + 1 报告）——**无其他 modified/untracked artifact** ✓
- 无 APK/截图/log/DB/dump/真实数据；无 7B/7C 实现 ✓
- `stash@{0}` 原样（未触碰）✓
- 无生产/测试既有文件被修改（`git status` 证实仅新增目录）✓

## DTO verdict

`MahiroV1Dto.kt`（40 行）：

- 仅 JSON v1 representation：`MahiroV1DocumentDto(weight, events)` + `MahiroV1DoseEventDto(id?, route, ester, timeH, doseMG, extras)` ✓
- 无 Repository/Entity/DAO/Android Context/PK 参数/Compose/UI 引用 ✓
- **无系统时间/时区**（Codec 的 clock 是显式构造注入，DTO 本身无）✓；无隐式业务 fallback ✓
- 字段类型/nullability/枚举表示（wire string）与设计 §6.1 一致 ✓
- 结构化解码结果：`DecodeResult.Success(diagnostics)/Failure` + `DocumentError.Syntax/InvalidRepresentation` + `EntryDiagnostic(index, MissingField/InvalidFieldType/ExpectedObject)`——**非 catch→null** ✓
- **JSON v1 无 MedicationPlan**：无 plan/slot/schedule DTO ✓

## Codec verdict

`MahiroV1Codec.kt`（158 行）：

- 只负责 `JSON text ↔ DTO`（decode/encode）✓；不承担 Domain 校验/UUID fallback/Repository insert/Entity 映射/conflict/PK/时区重建 ✓
- decode：syntax 失败 → `Failure(Syntax)`（SerializationException/IllegalArgumentException 分类）✓；非对象文档 → InvalidRepresentation ✓；weight 非 primitive → InvalidRepresentation（文档失败，与旧 `jsonPrimitive` 抛出的文档失败一致）；weight 字符串 primitive → null（与设计 §5.1 "absent or non-number primitive becomes null" 一致）✓；events 非数组 → InvalidRepresentation ✓
- **逐条 entry 独立解析**（`decodeEvent` 失败进 diagnostics 不中止）——设计 §6.2 ✓；unknown 顶层/事件字段忽略 ✓；meta.version 不选 parser ✓；labResults/doseTemplates 忽略 ✓
- extras：object/missing；**未知键与非数字值在 DTO 层保留/忽略**（L119-123 保留全部数值键，未知键保留、非数字忽略）——adapter 层按六键过滤——**最终语义与旧 "ignore unknown keys and non-numeric values" 一致** ✓
- encode：固定 `meta.version=1` + 注入 clock 的 `exportedAt` + 事件顺序 + 空数组 ✓（设计 §6.3）
- 错误语义结构化（Syntax/InvalidRepresentation/EntryError 三分），**无 catch-all→Unknown** ✓

## Domain-adapter verdict

`MahiroV1DomainAdapter.kt`（143 行）：

- 只承担 `DTO ↔ core.model.DoseEvent` 语义转换 ✓
- 无 Repository/DAO/Entity/PK/UI/Context 引用；不调当前时间/系统时区 ✓
- 显式映射：7 route + 5 ester（精确 wire 字符串、大小写严格、无 ordinal/lowercase heuristic）✓；timeH 经 `LegacyTimeAdapter.timeHToInstant`（**唯一时间源，无复制算法**）✓；extras 六键双向穷举映射 ✓
- import defaults 固定：`zoneId=null, localDate=null, slotId=null, source=JSON_V1, status=RECORDED, revision=1`（设计 §5.3）✓——**不通过 systemDefault/now 重建 legacy 元数据** ✓
- export：`instantToTimeH`；不可表示 → `UnrepresentableInstant` 显式失败（**无 truncate/round/clamp/omit/近似**）✓
- `ROUTE_TO_WIRE.getValue`/`ESTER_TO_WIRE.getValue`：Domain 枚举 7/5 全覆盖（双射），无 fallback 差异（旧实现的 `?.name.lowercase()` fallback 在当前枚举下不可达）✓

## UUID verdict

- valid UUID → 原样保留（`parseUuidOrNull` 成功即用，**不调 supplier**——测试以 supplier throw 验证）✓
- missing / blank / malformed → `uuidSupplier()`（生产默认 `UUID::randomUUID`，**逐次独立调用**，不 cache/reuse）✓
- supplier 仅构造注入（测试 seam），**生产默认严格等价 UUID.randomUUID()，不形成 deterministic ID** ✓
- 测试：inject deque 三个固定 UUID → missing/blank/malformed 各得独立 UUID 且互不相等——**无随机碰撞依赖、无 flaky** ✓；`assertNotEquals` 断言独立性 ✓
- 测试**未错误要求**同 corrupt payload 每次 decode 得相同 UUID（正确语义 = 随机 fallback）✓

## Time verdict

- 复用 `LegacyTimeAdapter`（`timeHToInstant`/`instantToTimeH`）——**无第二套 timeH↔Instant 算法** ✓
- 测试覆盖：非有限（NaN→NonFinite）、溢出（MAX→Overflow）、负数（-1.5→-5400000ms）、亚毫秒（1/3600000→1ms，Math.round 语义）——**浮点/边界判定由 LegacyTimeAdapter 提供，无新 magic number** ✓
- epoch millis 精度：`Instant.ofEpochMilli(1_700_000_000_125L)` 精确往返（测试断言）✓
- **无 silent time corruption**：不可表示 Instant → 显式 `UnrepresentableInstant`（Instant.MAX 测试断言 Overflow + value 保留）✓

## Extras verdict

- 六键与 `MahiroJsonFormat` 完全一致（`sublingualTier/sublingualTheta/concentrationMgMl/areaCm2/releaseRateUgPerDay/antiAndrogenType`）——本审阅逐行核对两处映射 ✓
- 导入：未知键忽略、非数字值忽略、数值保留（无单位换算/无 clamp）✓
- 导出：固定六键顺序（测试：reversed Domain input → 输出仍按锁定顺序）✓
- **无 custom medication 键/协议扩展**（grep `customName/medicationType/Route.CUSTOM/fake ester/placeholder` 零命中——语义判断后无越界）✓

## Enum verdict

- Route/ester 编码解码均为**精确 wire 字符串**（非 `enum.name` 直出——虽然名称恰好一致，实现用显式双向 map，大小写严格：`"Oral"`/`"ev"` 显式失败测试）✓
- 未知枚举 → 显式 `UnknownRoute/UnknownEster` error（entry 级失败）✓
- Status 不参与协议（import 固定 RECORDED；导出省略）✓

## Compatibility verdict

- **`MahiroJsonFormat` 未修改**；`Batch6MahiroJsonBridge` 未修改；production import/export 未切换；Widget/Wear 未切换——7A 纯并行边界（7B 才 cutover）✓
- 新旧语义对照测试（AdapterTest `new boundary matches legacy facade...`）：同 synthetic input → legacy `parseImport` vs 新路径逐字段相等（id/route/ester/timeH/doseMG/extras）✓
- legacy compat 套件独立运行：MahiroJsonFormatTest 14 + Batch6DoseEventCompatibilityTest 7 = **21/21** ✓（旧生产行为未破坏）
- 完整 golden 测试：固定 clock + 全文本字节比较（设计 §8 要求）✓；encode/decode 顺序保持 ✓
- **P2-F1**：数字型 `id`（wrong JSON type）——新实现按**设计锁定**的 skip 语义（`InvalidFieldType("id")` diagnostic，测试 `wrong id type is an entry representation diagnostic` 显式锁定）；旧实现对此更宽容（`jsonPrimitive.content` → 解析失败 → randomUUID 保留 entry）。实现与设计一致；7B 切换时该边界行为变化（自产文件不受影响——导出恒写字符串 id）。记录为兼容性注意项。

## Error-model verdict

- 7A 错误分类完整区分：document syntax / document representation / indexed entry representation / unknown route / unknown ester / invalid timeH / unrepresentable instant ✓
- **无 7B 类型**（无 RepositoryConflict/StorageFailure/ImportSummary/InsertedCount/IdempotentCount/ConflictCount）——scope 干净 ✓
- 无 catch→null / catch-all→Unknown ✓

## Test-quality verdict

- CodecTest（12）：minimal/full/malformed/non-object/non-array/weight 非数字/entry 诊断（index+类型）/missing id/extras/wrong id type/unknown extras/golden 全文本/顺序 round-trip——**具体字段与值断言**（非 assertNotNull）✓
- AdapterTest（13）：valid 全字段+锁定 metadata/7 route/5 ester/独立 UUID fallback（inject deque）/valid 绕过 supplier/InvalidTimeH 两类/负值与亚毫秒 round 语义/export 精确拼写+顺序/7 route export/5 ester export/UnrepresentableInstant 显式失败/round-trip defaults/legacy parity ✓
- 无 @Ignore；fixtures 全合成（`59e6a6da-...` 等固定合成 UUID）✓
- 随机 UUID 测试：注入确定性 supplier（非真实随机）——**无 flaky** ✓

## Device-evidence verdict

- 独立核实（`adb`）：emulator-5556 = **sdk_gphone64_x86_64、SDK 33、1080x2400、sw411dp、无 watch/tablet feature** = **API 33 PHONE** ✓——报告 §12 声称 "emulator-5556, sdk_gphone64_x86_64, Android 13, API 33" **完全一致，证据有效**
- 5558 仍为 Wear（未用于本 7A 验证）；未发现端口身份混淆
- **未独立重跑**该 instrumentation（Existing JSON production-path 1 suite 2 tests）——7A 核心为 pure JVM，instrumentation 非架构门槛；记录为验证边界（P2-F2）

## Forbidden-boundary verdict

- 新生产文件 grep：`Repository|Dao|Entity|Database|insert|upsert|transaction|SimulationEngine|ParameterResolver|Context|Compose|Widget|Wear` **零命中** ✓
- `git diff` 越界路径（schemas/migration/core/data/build.gradle/libs.versions.toml）**全空** ✓；KSP 重跑后 schema blob 不变（报告 §13 声明 + diff 证实）✓
- 无 Gradle/dependency 变化 ✓；无 PK 引用/实现 ✓；无 Widget/Wear/Custom/Plan/schema 越界 ✓

## Report-accuracy verdict

`PHASE_1_BATCH_7A_REPORT.md`（263 行）逐项核对：

- baseline SHA（ac0ce36）/design commit（4b7cd3c）/tag 引用准确 ✓
- 文件清单准确（6 文件）✓
- 测试计数：focused 2 suites **25**（本审阅独立运行 CodecTest 12 + AdapterTest 13 一致）✓；legacy compat 2 suites **21**（MahiroJsonFormatTest 14 + Batch6 7 一致）✓；全量 **45 suites / 389**（本审阅独立运行一致，= 364+25）✓；PK 49（本审阅独立运行一致）✓
- scope/ID 语义/时间/extras/兼容/边界描述与代码一致 ✓；keystore 临时复制披露诚实 ✓；设备身份（已独立核实）✓
- P2 三项继承设计（非新）——报告如实 ✓；**无夸大**（未声称 import service 或 cutover 已实现）✓
- **P2-F2**：instrumentation 运行记录（1 suite 2 tests）本审阅未独立重跑——报告声称与设备身份核实通过，复跑属可选

## Findings

### P0
None.

### P1
None.

### P2

**F1 — 数字型 id 的 skip 语义与旧实现宽容行为差异（兼容性注意项）**
- Severity: P2
- File: `MahiroV1Codec.kt:104-111`（id 非字符串 → InvalidFieldType）；对照 `MahiroJsonFormat.kt:106-111`
- Problem: 新边界按设计锁定 skip（`InvalidFieldType("id")` diagnostic）；旧实现将数字 id 的 content 交给 `UUID.fromString` 失败后回退 randomUUID（保留 entry）
- Evidence: CodecTest `wrong id type is an entry representation diagnostic`（显式锁定 skip）；设计 §5.2 "A wrong JSON type can make that entry malformed and skipped"
- Impact: 实现与**设计**一致；7B 切换后此类外部文件（非自产——导出恒写字符串 id）行为变化；当前无生产影响（7A 未切）
- Required fix: 无代码修复；7B 报告应声明该边界切换
- Blocks sealing? NO

**F2 — instrumentation 复跑边界**
- Severity: P2
- File: 验证边界（报告 §12）
- Problem: Existing JSON production-path instrumentation（1 suite 2 tests）本审阅未独立重跑
- Evidence: 设备身份已独立核实为 API 33 phone（证据有效）；7A 核心 pure JVM，focused 25/25 + compat 21/21 + 全量 389 + PK 49 全绿
- Impact: 无代码影响；验证覆盖边界
- Required fix: 7B 实施时可顺带复跑
- Blocks sealing? NO

**F3 — 继承设计 P2（非本批新增）**
- Severity: P2
- File: 报告 §15
- Problem: 三项 P2（随机非幂等 ID / v1 不可表示 Domain 元数据 / ADR-015 Route-Ester 共享枚举）由 Batch 7 design review 继承，7A 未引入新 P2
- Blocks sealing? NO

## Independent validation

- Git: branch/HEAD/tag/rev-list/status/diff/--check/ls-files/stash 全部核实
- 完整读取: 3 生产文件 + 2 测试文件 + 7A 报告（263 行）+ 对照 `MahiroJsonFormat.kt`（178 行）、`LegacyTimeAdapter` 签名、`Batch6DoseEventCompatibility`
- 独立运行:
  - `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.external.mahiro.v1.*"` → CodecTest **12** + AdapterTest **13** = 25/25，XML 核实 0 failures/errors/skipped
  - legacy compat（MahiroJsonFormatTest + Batch6DoseEventCompatibilityTest）→ **14 + 7 = 21/21**
  - `:app:testDebugUnitTest --rerun-tasks` → **45 suites / 389 / 0 / 0 / 0**（XML 逐文件累加）
  - `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.pk.*"` → **5 suites / 49 / 0**
  - `:app:assembleDebug` PASS；`:app:lintDebug` PASS（0 errors）；`:app:kspDebugKotlin --rerun-tasks` PASS；`git diff --check` PASS
- 设备身份: emulator-5556 = API 33 phone（sdk_gphone64_x86_64/sw411dp/1080x2400/无 watch）——报告 instrumentation 证据有效
- 未运行: 该 instrumentation 复跑（F2）、Wear/折叠屏（不在 7A 门槛）
- 越界审计: 新文件 grep 零命中；schemas/migration/core/data/Gradle diff 全空

## Final decision

**APPROVE WITH P2**

批准标准逐项：P0=0 ✓；P1=0 ✓；DTO/codec/adapter 边界正确 ✓；UUID 语义正确 ✓；时间语义无损 ✓；extras 兼容 ✓；legacy JSON compatibility ✓；无 Repository ✓；无 PK ✓；无 Widget/Wear/Custom/Plan JSON/schema/migration/new dependency ✓；报告足够准确 ✓。

**Batch 7A may be sealed.**

**Batch 7B may begin only after 7A implementation/review/tag sealing.**

- 7B（Repository-backed import service + HRT cutover）：封存后；实现时应同步声明 F1 的 id 边界切换
- 7C（Domain→PK adapter + parity）：未授权
- Widget / Custom medication / Wear expansion：继续禁止
- Room v3：仍 internal/unreleasable
