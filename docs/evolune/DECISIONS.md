# 架构决策记录

This file preserves historical decision context. Statuses below describe the post-v1.0 outcome without rewriting the original background or rationale.

## Current status index

| ADR | Current status | Post-v1.0 note |
|---|---|---|
| ADR-001 | Implemented in v1.0 | Evolune remains MIT within the documented source and permission boundaries. |
| ADR-002 | Implemented in v1.0 | Third-party source and assets require verified provenance and compatible terms. |
| ADR-003 | Implemented in v1.0 | Room v3 is the local source of truth. |
| ADR-004 | Accepted / planned v1.2 | Health Connect remains an optional future integration and is not implemented. |
| ADR-005 | Accepted / partially implemented | v1.0/v1.1 Data Layer replay/conflict behavior is tested; a general versioned protocol remains future work. |
| ADR-006 | Accepted / ongoing | Logical package boundaries are implemented; further Gradle module extraction is deferred. |
| ADR-007 | Implemented in part / deferred | Phone/Wear backup exclusions shipped in v1.0; SQLCipher and encrypted backup remain deferred. |
| ADR-008 | Deferred | RemoteViews and the v1.1 occurrence-driven enhancement are shipped; a future Glance evaluation remains optional. |
| ADR-009 | Accepted / planned v1.2 | Google cloud backup remains separate from local export and Wear transport. |
| ADR-010 | Implemented in v1.0 | Domain, Room entity and external DTO boundaries are explicit; PK uses an adapter. |
| ADR-011 | Deferred | Tracked Date did not enter v1.0 and has no current entity or product surface. |
| ADR-012 | Implemented in v1.0 | Wear device transfer, user export and future cloud backup remain separate boundaries. |
| ADR-013 | Implemented in v1.0 | `core.dataapi` contracts are implemented by Room repositories. |
| ADR-014 | Implemented in v1.0 | UUIDv5 slot IDs and the historical namespace input are persisted compatibility rules. |
| ADR-015 | Implemented in v1.0 | The staged domain/mapper/Repository transition completed against Room v3. |
| ADR-016 | Implemented in v1.0 | Strict migration, repair tooling and all release gates completed; the internal no-release interval is closed. |
| ADR-017 | Accepted / implemented in v1.0 | Final public Phone/Wear application identity is fixed. |
| ADR-018 | Accepted / implemented in v1.0 | Public Release builds require the persistent external signing identity. |
| ADR-019 | Accepted / implemented in v1.0 | PK permission is scoped to author-owned or authorizable rights and requires attribution. |
| ADR-020 | Accepted / implemented in v1.0 | Publication is limited to explicitly approved refs and assets. |

## ADR-001：保持 Evolune 使用 MIT

- **背景**：当前 Evolune `LICENSE` 是 MIT；产品目标是独立、可长期维护的项目，直接继承的 `upstream/master` 基线也使用 MIT。
- **可选方案**：继续 MIT；对具有独立许可的组件保留其各自条款；整体改用其他许可证。
- **最终建议**：保持 Evolune MIT，并逐项保留实际第三方材料的许可与归属要求。
- **优点**：许可边界清晰，便于独立维护和分发。
- **缺点**：新增来源需要逐项审查和记录。
- **风险**：来源不明的文件或第三方资源可能带来额外许可义务。
- **重新评估**：若版权持有人书面确认可授权特定代码，逐文件更新 NOTICE 和许可证策略。

## ADR-002：只纳入来源与许可已确认的材料

- **背景**：项目必须能够说明纳入公开树的源码、资产和测试资料的来源及适用条款；改名或机械重写不会改变其来源。
- **可选方案**：不经核验直接复用；按兼容条款与归属要求纳入；仅依据公开行为和 API 独立实现。
- **最终建议**：仅纳入来源和许可已确认的材料；其他需求使用 Evolune 自己的接口、实现与测试独立完成。
- **优点**：维持清晰、可审计的许可与来源边界。
- **缺点**：来源核验和独立实现会增加工作量。
- **风险**：不可证明来源的实现细节、图片、XML 和测试样例可能仍有许可问题。
- **重新评估**：人工完成来源审查后逐项决定是否可复用。

## ADR-003：Evolune Room 数据库是主要事实来源

- **背景**：当前 `AppDatabase` 保存 `DoseEventEntity` 和 `MedicationPlanEntity`；Wear 只保存缓存，应用不依赖云端。
- **可选方案**：Health Connect 主导；云端主导；Evolune 本地 Room 主导。
- **最终建议**：Room 主导，外部系统通过 adapter 交换数据。
- **优点**：离线可靠、业务规则可控、测试和恢复路径清晰。
- **缺点**：需要自己处理迁移、备份、冲突和跨设备同步。
- **风险**：数据库 schema 设计不完整会把错误传播到所有外部适配器。
- **重新评估**：未来若产品转为多设备协作，再评估同步数据库或服务端事实来源。

## ADR-004：Health Connect 作为集成层

- **背景**：当前 Evolune 没有 Health Connect；未来集成需要覆盖体重读取、MedicationStatement/PHR 写入及权限边界。
- **可选方案**：核心模型依赖 Health Connect；Health Connect 只做可选交换层；完全不接入。
- **最终建议**：可选交换层；按数据类型授权、映射、去重和记录来源。
- **优点**：权限可控，Provider 不可用时核心功能仍工作。
- **缺点**：存在单位、来源、时间和 Provider 能力差异。
- **风险**：PHR/FHIR 数据写入能力、撤销和删除语义需要真实设备确认。
- **重新评估**：当目标设备和用户场景明确，并完成 Provider 矩阵后评估第二阶段。

## ADR-005：Wear 协议必须版本化

- **背景**：当前 `WearDataLayer` 使用 `/hrt/plans`、`/hrt/request-plans` 和 DataMap JSON，没有统一 schema、ack、checksum 或版本协商。
- **可选方案**：继续扩展现有 DataMap；采用独立版本化 envelope；使用 ChannelClient 长连接。
- **最终建议**：`DataClient + MessageClient` 配合独立 `core:wear-protocol`。
- **优点**：最新快照和短命令的语义匹配，协议可测试、兼容和废弃。
- **缺点**：需要新建协议测试和手机/Wear 双端适配。
- **风险**：版本不兼容和离线重复命令。
- **重新评估**：当 payload 超出小快照或需要文件传输时再评估 ChannelClient。

## ADR-006：逐步多模块，而不是一次性拆分

- **背景**：当前仓库只有 `app` 和 `wear`，且大量逻辑直接位于 app 包内。
- **可选方案**：继续单体；一次性创建全部规划模块；按稳定边界增量拆分。
- **最终建议**：先在 app 内建立 package/interface 边界，再按测试和依赖需要创建模块。
- **优点**：改动可回滚，减少 Gradle 和依赖迁移风险。
- **缺点**：过渡期会存在 adapter 和重复代码。
- **风险**：边界长期不拆会继续耦合。
- **重新评估**：当 `core:model` 或 protocol 可独立 JVM 测试时创建对应 module。

## ADR-007：暂不使用 SQLCipher

- **背景**：当前数据库是 Room 默认实现；Evolune 尚未形成数据库加密所需的威胁模型、密钥恢复或迁移方案。
- **可选方案**：立即引入 SQLCipher；继续明文 Room 并限制备份；先做加密导出。
- **最终建议**：先由项目所有者决定 Android Auto Backup 政策，并在进入 Phase 1 前实施可验证的排除规则或关闭备份；随后建立用户主动加密备份，再评估数据库透明加密。
- **优点**：降低立即迁移风险，优先解决真正的导出和备份场景。
- **缺点**：设备被完整提取时，明文数据库保护有限。
- **风险**：备份规则缺失是当前必须修复的隐私问题。
- **重新评估**：明确威胁模型、密钥丢失处理和 Room/SQLCipher 迁移测试后。

## ADR-008：Glance 先试点，不立即替换 RemoteViews

- **背景**：当前 Evolune 使用 RemoteViews；迁移前需要先建立独立的 Widget snapshot 边界，并验证 Glance 在目标 Launcher/OEM 上的尺寸、更新和交互行为。
- **可选方案**：立即全面迁移；继续 RemoteViews；抽出 snapshot 后做一个 Glance 试点。
- **最终建议**：第三种方案。
- **优点**：保持现有可靠路径，同时验证现代 API 和 OEM 行为。
- **缺点**：短期会维护两种 provider。
- **风险**：状态更新、预览、尺寸和交互差异。
- **重新评估**：至少完成目标 OEM Launcher 矩阵后。

## ADR-009：Google Drive 不作为默认同步方案

- **背景**：Drive 同步需要 OAuth、加密备份、冲突决策和后台调度，但当前 Evolune 没有加密备份格式和账户体系。
- **可选方案**：Drive appData；用户可见文件；WebDAV；自建服务；仅本地加密备份。
- **最终建议**：先做本地导出/恢复和加密文件格式，再按用户价值选择 provider；不承诺实时同步。
- **优点**：先解决可恢复性，避免过早承担 OAuth、删除同步和多设备冲突。
- **缺点**：跨设备便利性较低。
- **风险**：加密密钥丢失、令牌撤销、云文件损坏和隐私合规。
- **重新评估**：本地恢复演练完成且有明确用户需求时。

## ADR-010：保持数据模型与 UI 模型分离

- **背景**：当前 `MedicationPlan` 与 `MedicationPlanEntity` 已有转换，但 `DoseEvent` 仍服务于 PK、数据库和 UI 多个场景。
- **可选方案**：一个模型贯穿全部层；Entity/Domain/UI 三层模型；只分离外部 DTO。
- **最终建议**：至少分离 Entity、Domain 和外部/UI DTO；PK 使用明确 adapter。
- **优点**：schema、Health Connect、Wear 和 UI 可以独立演进。
- **缺点**：映射代码增加。
- **风险**：映射遗漏、默认值和版本兼容。
- **重新评估**：Phase 1 数据模型迁移时。

## ADR-011：Tracked Date 作为正式领域模型

- **背景**：迁移包中的 Tracked Date 用于小组件锚点和日历语义；当前 Evolune 没有此模型。
- **可选方案**：继续用 UI 字段；使用 `LocalDate` 临时值；建立带时区和审计字段的实体。
- **最终建议**：在产品所有者确认前，统一标记为 `P2（1.0，待产品确认，非 MVP）`，Phase 1 不创建实体或迁移。若确认需要周期起点/追踪日期，再建立正式 `TrackedDate`，使用用户所在时区的日期语义，而不是瞬时时间。
- **优点**：统计、日历、Widget 和同步可以共享稳定含义。
- **缺点**：需要处理时区、夏令时、修改和审计。
- **风险**：用户修改历史日期会改变统计和提醒解释。
- **重新评估**：产品确认“追踪日期”是核心场景后；若决定进入 MVP，必须先同步修改功能矩阵、路线图和迁移计划。

## ADR-012：分离 Wear 设备传输、本地备份与云同步

- **背景**：旧架构图用 `core:sync` 同时表示手机到手表的 Data Layer bridge 和未来云 provider，容易把完全不同的安全、冲突和生命周期语义混在一起。
- **可选方案**：继续使用统一 `core:sync`；按 transport/provider 建多个实现但共享入口；建立三个明确边界。
- **最终建议**：建立三个边界：`core:wear-bridge` 负责配对手机与手表的快照和命令；`feature:backup` 负责用户主动导出/恢复；`core:sync` 仅保留给未来云 provider、多设备冲突和后台编排。
- **优点**：Wear 不需要 OAuth 或云冲突模型；云 provider 不依赖 Wearable SDK；本地恢复可以独立发布和测试。
- **缺点**：需要为快照、备份 envelope 和云对象分别维护 DTO/adapter。
- **风险**：若为了复用而共享可变实现，三个边界仍可能重新耦合。
- **重新评估**：只有当多个边界确实共享稳定、纯 Kotlin 的值对象时，才把该值对象下沉到 `core:model` 或 `core:common`。

## ADR-013：Repository contract 与数据库实现采用反向依赖

- **背景**：feature 需要稳定的数据能力，但不应依赖 Room Entity、DAO 或数据库工厂。原架构文档没有明确 Repository 接口由谁拥有。
- **可选方案**：接口和实现在 `core:database` 中全部公开；接口放入 `core:model`；建立独立 `core:data-api` 逻辑边界，由 `core:database` 实现。
- **最终建议**：目标关系为 `feature -> core:data-api <- core:database`。`core:data-api` 只定义 Repository contract、查询结果和一致性语义；Room 实现保持内部。迁移初期可先用独立 package 表达该边界，不要求立即创建 Gradle module。
- **优点**：feature 和平台 adapter 不接触 Room；数据库可以替换和独立测试；依赖方向清晰。
- **缺点**：增加 contract、mapping 和 composition root 代码。
- **风险**：过早拆 module 会增加 Gradle 复杂度；长期不拆 package 边界又可能被绕过。
- **重新评估**：Phase 1 开始前由项目所有者确认模块名称和创建时机；依赖方向本身不得反转。

## ADR-014：Scheduled Dose Slot 使用版本化 UUIDv5 标识

- **背景**：当前 `MedicationPlan.timeOfDay` 只有本地时间列表，没有稳定槽位 ID。Room v2 到 v3 backfill、计划编辑和未来跨入口幂等需要同一计划槽在重复计算时得到相同标识，同时必须保留重复时间和列表顺序。
- **可选方案**：新槽和 backfill 全部使用随机 UUID；直接对业务内容做未版本化 hash；使用带固定 namespace 和 canonical name 的 UUIDv5；使用数据库自增 ID。
- **最终选择**：使用 UUID version 5 和 SHA-1 生成版本化稳定 ID。SHA-1 仅用于稳定标识，不用于密码学安全、签名、认证或完整性校验。根 namespace 是标准 DNS namespace `6ba7b810-9dad-11d1-80b4-00c04fd430c8`；对 UTF-8 名称 `io.github.yuninggu.evolune:scheduled-dose-slot` 执行 UUIDv5，得到不可变项目 namespace `68559b97-4ddc-5be2-bcbd-9ab409f0d95b`。
- **Canonical name**：`slot:v1:plan=<canonicalPlanUuid>;position=<canonicalPosition>;time=<canonicalLocalTime>`，整体使用 UTF-8。planId 使用 UUID 标准小写带连字符格式；position 使用零基、无符号、无前导零的十进制 ASCII，范围为 `0..Int.MAX_VALUE`；localTime 只允许分钟精度并固定为 `HH:mm`。
- **身份语义**：Slot ID 由 planId、position 和 localTime 共同决定。任一变化都会改变 ID；相同时间可由不同 position 区分。剂量、药物名称、route、ester 和启用状态不定义槽位身份，因此不进入 canonical name。Slot ID 不是通用数据库行 ID，也不是 `DoseEvent.id`。
- **优点**：跨重复迁移和进程稳定；不依赖数据库插入顺序、Locale、时区、设备状态或随机源；标准 UUID 可直接用于现有 UUID/Room 边界；版本前缀支持未来演进。
- **缺点**：时间列表重排会改变 position 并可能改变 Slot ID；canonical 规则一旦发布即成为兼容承诺；UUIDv5 需要项目自行提供明确且经过测试的标准实现。
- **不可变兼容规则**：Slot ID v1 的 namespace、UTF-8 编码、字段顺序、分隔符、规范化规则和固定测试向量不得改变。固定向量 `00000000-0000-0000-0000-000000000001`、position `0`、`08:30` 必须得到 `17d1fd14-9d70-5344-beaa-0b158c9f62f4`。
- **错误行为**：非法 UUID、首尾空白、负 position、非分钟精度 localTime 或 UUIDv5 输入构建失败必须产生明确错误；不得静默修正、返回 null、生成随机 ID 或回退到数据库自增值。
- **Slot ID v2 条件**：只有 canonical 输入语义、namespace、算法或兼容需求发生不可兼容变化时才设计 v2。v2 必须使用新的版本前缀或新的 namespace，不得重新解释已生成的 v1 ID。

## ADR-015：Repository contract、MedicationPlan Domain 与 v2 mapper 分阶段落地

- **背景**：Phase 1 需要建立独立的 Domain 和 Repository 边界，但当前生产数据库仍为 Room v2。v2 Entity 不能保存 `occurredAt` 的完整领域元数据、`zoneId`、`localDate`、`slotId`、`source`、`status`、`revision` 或 `ScheduledDoseSlot.id`。
- **可选方案**：立即实现完整双向 mapper 并接入当前 Repository；只建立 Domain/contract 并推迟 mapper；先建立纯 contract，再建立 v2 Entity -> Domain 只读 mapper，等 v3 schema 后实现完整 Repository；继续让 feature 直接依赖 DAO/Entity。
- **最终选择**：采用三阶段 Batch 3。3A 只定义 `core.model.MedicationPlan`、`core.model.ScheduleType`、Repository contract、固定业务结果和纯 JVM 测试；3B 只实现 v2 Entity -> Domain 只读 mapper、显式枚举/ExtraKey mapper、LegacyTimeAdapter 和 Slot ID v1 的边界适配；3C 等 Batch 4 v3 schema、Entity、DAO 和 migration tests 全部通过后，才实现完整无损双向 mapper、Repository Room 实现、聚合 transaction 和生产接线。
- **依赖方向**：目标关系为 `feature -> core:data-api <- core:database`。Batch 3A 的 contract 只依赖 Domain、`Flow` 和 Kotlin 标准库，不暴露 DAO、Entity、Room、Context 或 Wear 类型；Batch 3B 的 persistence mapper 属于 data 边界，不反向污染 Domain。
- **不变量**：`DoseEvent.occurredAt` 是权威 `Instant`；`MedicationPlan` 的 slots 列表顺序权威，position 必须连续、唯一且从零开始，允许重复 localTime 和空列表；`DAILY` 忽略无关字段，`WEEKLY` 使用 `daysOfWeek`，`CUSTOM` 使用 `intervalDays`；不自动排序、重编号、去重或修正非法输入；Phase 1 status 仅为 `RECORDED`。
- **v2 读取策略**：v2 legacy event 读取后设置 `zoneId`、`localDate`、`slotId` 为 null，`source=LEGACY`、`status=RECORDED`、`revision=1`；v2 plan 按 `timeOfDay` 原顺序生成 Slot ID v1。Batch 3 不提供通用 Domain -> v2 Entity mapper，不以 best-effort、lossy 或静默丢字段方式写回。
- **Repository 结果语义**：事件插入使用 `Inserted`、`Idempotent`、`Conflict`、`Invalid`；事件更新使用 `Updated`、`NoChange`、`NotFound`、`RevisionConflict`、`Invalid`；删除使用 `Deleted`、`NotFound`；计划保存使用 `Created`、`Updated`、`NoChange`、`Invalid`；计划更新使用 `Updated`、`NoChange`、`NotFound`、`Invalid`。数据库打开、transaction 和不可恢复 I/O 故障仍作为异常处理，不伪装成业务结果。
- **现有行为冻结**：`getEventsForPk(asOf)` 保持当前 30 天窗口、最多 20 条选择逻辑以及两个分支各自的返回顺序；Batch 3 不统一排序，也不修改 PK 参数或算法。JSON v1 缺失/损坏 ID 继续使用随机 UUID。
- **过渡取舍**：`core.model` 在 Batch 3 暂时复用 `pk.Route` 和 `pk.Ester`，不移动枚举、不修改 `SimulationEngine`；Domain ExtraKey 与 PK ExtraKey 使用六个值的显式 `when` 映射，禁止 ordinal，未知值明确失败；`Instant.toEpochMilli()` 的 `ArithmeticException` 在 persistence mapper 边界转为明确 mapping failure，Domain 不加入数据库范围限制。
- **优点**：先锁定依赖方向和业务语义，避免 v2 有损写回；3A 可独立验证，3B 可独立验证 legacy 读取，Batch 4 后再一次性实现真实持久化写入。
- **缺点**：Batch 3A/3B 期间存在只读过渡层，当前生产 Repository 仍未切换；3C 需要等待 schema 迁移，短期会保留部分旧模型和 mapper。
- **不可变兼容规则**：不得因为 3A/3B 实施而修改 Room version、schema 2、Entity、DAO、JSON v1、PK、Wear 或 Widget 行为；不得提前引入 Tracked Date、Health Connect、Glance、WorkManager、云同步或生产路径切换。
- **重新评估条件**：Batch 4 v3 schema、Entity、DAO、migration test、schema export 和非法数据失败检查全部通过后，才能评估 3C。若需要让 v2 写入领域 metadata、移动 Route/Ester、改变 `getEventsForPk`、增加 result 状态或允许有损兼容，必须另立 ADR。

## ADR-016：Room v3 迁移采用严格时间兼容、内部不可发布门槛与显式修复工具

- **背景**：Room v2 的 `dose_events.timeH` 使用 SQLite 动态数值存储，`medication_plans.timeOfDay` 是历史 JSON 字符串列表。v3 需要回填权威 `occurredAtEpochMillis` 和稳定 `ScheduledDoseSlot`，但旧数据可能包含异常 storage class、非法数值或非分钟时间；同时 schema、Repository、双写和入口切换会跨多个批次完成。
- **状态**：Batch 4 设计阻断项 B1、B2、B3、B4 均为 `Resolved`。
- **非分钟 `timeOfDay`**：读取时保留列表顺序和重复值；SQL 空字符串与 JSON `[]` 均为空列表；按 ISO `LocalTime` 解析。`HH:mm`、`HH:mm:00` 和 second/nano 均为 0 的等效 ISO 表达可迁移，新 slot 规范化为 `HH:mm`，旧字符串不变。second 或 nano 任一非零即中止整个 migration，不截断、舍入、跳过或修正；错误包含 planId、position、原始值和原因。
- **Slot ID v1 不变**：非分钟时间不通过改变 UUIDv5 namespace、canonical name、UTF-8 规则或固定向量解决。修改 v1 会让同一历史计划产生不同身份并破坏已锁定兼容承诺；不兼容需求必须设计 Slot ID v2。
- **Storage class**：先检查 `Cursor.isNull` 和 `Cursor.getType`，只允许 INTEGER/FLOAT；NULL/STRING/BLOB 明确失败。通过类型验证后才调用 `getDouble`，再统一交给 `LegacyTimeAdapter`。NaN、positive/negative infinity、乘法溢出和 `Long` 溢出全部失败；不 clamp、不替换为 0/当前时间、不删除记录、不更新旧 `timeH`。全部 event/plan 必须在任何回填 `UPDATE/INSERT` 前验证，失败由外层 SQLiteOpenHelper upgrade transaction 回滚。
- **SQL default 与运行时默认**：`occurredAtEpochMillis DEFAULT 0` 只服务于 `ALTER TABLE` 和 Room schema 兼容。运行时创建 `DoseEventEntity` 必须通过共享严格 helper 从合法 `timeH` 计算时间，不得把 0 当作业务默认值；兼容阶段只允许 `zoneId/localDate/slotId=null`、`source=LEGACY`、`status=RECORDED`、`revision=1`。
- **内部不可发布区间**：从首个包含 v3 schema 的提交到 Phase 1 Batch 8 退出验收全部通过，所有构建均不可正式发布。禁止生产 APK/AAB、用户主数据库升级、正式 release 和在真实健康数据上运行；只允许合成 fixture、测试数据库、emulator、非真实数据专用设备和本地可丢弃数据库。
- **显式修复工具**：Batch 4C 在 `tools/repair-v2/` 提供 Python 3.12-compatible 标准库工具，只使用 `sqlite3`、`argparse`、`json`、`hashlib`、`pathlib`、`shutil`、`datetime`。`scan` 只读诊断，`repair` 必须使用稳定 ID JSON correction manifest 并从只读输入生成不同输出副本，`verify` 重新扫描；禁止猜测、截断、当前时间、相邻记录/时区推断、无 manifest 修复和原地覆盖。JSONL 审计只记录版本、输入/输出 SHA-256、模式和计数，不记录完整行。
- **Batch 4 拆分**：4A-0 只实现 parser、compatibility helper、错误类型和 JVM tests，不改数据库；4A-1 用单个可构建原子提交完成 v3 Entity/slot DAO/schema/migration/基础 instrumentation；4B 完成并实际执行 migration matrix；4C 完成 Python repair toolkit。3C 仍在 v3 schema/DAO 就绪后独立实施，不并入 4A。
- **优点**：不歧义改写健康记录；保留原始列作为 rollback shadow；异常数据在写入前被发现并可完整回滚；Slot ID 保持确定性；修复过程保护原库且可审计；避免把不完整的中间 schema 当作可发布产品。
- **缺点**：发现异常数据时升级会明确失败；Phase 1 存在较长的内部不可发布区间；需要维护 Python 工具、合成 fixture 和设备 migration matrix；Batch 4A 通过后仍不能立即发布或直接开始生产切换。
- **发布门槛**：设备/emulator 上的 v2 -> v3 migration matrix、Batch 3C Repository、event 八字段双写、plan `timeOfDay`/slots 同 transaction、所有现有入口改走 contract、JSON v1 source/time 适配、PK 回归和 Batch 8 退出验收必须全部通过。仅编译 androidTest、生成 schema 或构建成功均不足以发布。
- **重新评估条件**：只有发布切片重新划分并能独立满足完整迁移/写入/入口门槛，或 Android/SQLite 支持矩阵、correction manifest 版本、审计合规要求发生变化时，才可新增 ADR。不得修改 Slot ID v1、放宽非法数据失败语义、将中间 v3 版本用于用户数据，或在实现中临时绕过本决策。

## ADR-017：固定 v1 公共应用身份

- **状态**：Accepted；Implemented in v1.0。
- **背景**：历史开发曾使用 `io.github.yuninggu.evolune`，但 v1 需要与当前维护者和公开仓库一致、可验证且长期稳定的分发身份。
- **最终选择**：Phone application ID 为 `io.github.yingqiu0871.evolune`，Wear application ID 为 `io.github.yingqiu0871.evolune.wear`。v1.0.0 使用 `versionCode = 10060`。
- **兼容说明**：Slot ID v1 的 UUIDv5 namespace input 仍包含历史字符串 `io.github.yuninggu.evolune:scheduled-dose-slot`。它是不可变持久化协议常量，不是当前应用身份，不得因包名现代化而修改。
- **重新评估**：公共 application ID 不应常规变更；任何变化都需要独立迁移、签名、数据和分发决策。

## ADR-018：Release signing 使用持久外部身份

- **状态**：Accepted；Implemented in v1.0。
- **背景**：公开更新链要求后续 Release 与 v1.0 使用同一受控签名身份。Debug 或临时签名不能建立该连续性。
- **最终选择**：Release build 只接受显式配置的外部 keystore、alias 与凭据，并验证预期证书；缺少 signing 环境必须失败，不允许 Debug fallback。keystore 与秘密不进入仓库或公开日志。
- **边界**：Debug workflow 只构建 Debug APK，不要求 Release secrets，也不能产生可冒充正式版本的 Release artifact。
- **重新评估**：仅在密钥轮换/失陷等需要正式迁移程序的事件中重新评估。

## ADR-019：PK permission 采用 scoped grant 与 attribution

- **状态**：Accepted；Implemented in v1.0。
- **背景**：`HRT-Recorder-PKcomponent-Test` 作者于 2026-08-14 明确授权 Evolune 使用、复制、修改、移植、二次开发、分发源代码和编译应用，并将相应衍生代码按 MIT 发布，但范围仅限作者拥有或有权授权的相关权利。
- **最终选择**：在该明确范围内发布相应衍生代码，并持续保留来源、版权、许可和贡献者 attribution。
- **限制**：不得声称整个上游仓库自动成为 MIT，不得声称作者代表第三方贡献者授予权利，也不得声称上游存在未经实际确认的正式 `LICENSE` 文件。
- **证据边界**：原始邮件是 owner-held provenance evidence；公开仓库只保存准确摘要，不保存私人地址或完整正文。

## ADR-020：显式公共发布边界

- **状态**：Accepted；Implemented in v1.0。
- **背景**：受保护的本地 evidence 和内部 refs 包含不得公开的历史材料；完整对象库或全部 refs 不能作为发布输入。
- **最终选择**：只发布经过审核的明确 branch/tag/refspec 与经过验证的 Release assets。禁止 `--all`、`--mirror`、通配 refspec、未过滤的全仓库 bundle 和 force push 已发布历史。
- **v1.0 closure**：`v1.0.0` 是 annotated tag，指向 release commit `780f167074cc737954c884d375825ef95db605c7`；该 tag 与 GitHub Release 永久封存，不因后续 `main` 维护而移动或重建。
- **重新评估**：任何扩大 publication surface 的请求都需要新的只读可达性、来源和 asset 审核。
