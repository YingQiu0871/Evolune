# 架构决策记录

## ADR-001：保持 Evolune 使用 MIT

- **背景**：当前 Evolune `LICENSE` 是 MIT；产品目标是独立、可长期维护的项目。远程 `upstream/master` 的 `LICENSE` 也实际为 MIT，但迁移包来自另一个 GPLv3 Featherline 来源。
- **可选方案**：继续 MIT；整体改为 GPLv3；不同模块混合许可证。
- **最终建议**：保持 Evolune MIT，未经人工确认不纳入 GPLv3 快照源码或专属资源。
- **优点**：许可边界清晰，便于独立维护和分发。
- **缺点**：不能直接使用迁移包已有实现，需重新设计和测试。
- **风险**：历史文件来源和第三方资源许可仍需人工确认。
- **重新评估**：若版权持有人书面确认可授权特定代码，逐文件更新 NOTICE 和许可证策略。

## ADR-002：不直接复制 GPLv3 源码

- **背景**：`feiwuliyong/06-licenses/SOURCE-AND-LICENSE-NOTICE.md` 将快照和补丁标为 Featherline GPLv3 衍生物。
- **可选方案**：复制并把包名改成 Evolune；采用 GPLv3；只参考行为后独立重写。
- **最终建议**：只参考产品行为、数据概念和公开 API，使用 Evolune 自己的接口和实现重写。
- **优点**：维持 MIT 目标，减少代码血缘污染。
- **缺点**：实施成本更高，行为一致性需要重新验证。
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

- **背景**：迁移资料展示了体重读取和 MedicationStatement/PHR 写入，但当前 Evolune 没有 Health Connect。
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

- **背景**：当前数据库是 Room 默认实现；迁移包使用 SQLCipher、Argon2 和更多安全设施，但 Evolune 没有威胁模型、密钥恢复或迁移方案。
- **可选方案**：立即引入 SQLCipher；继续明文 Room 并限制备份；先做加密导出。
- **最终建议**：先由项目所有者决定 Android Auto Backup 政策，并在进入 Phase 1 前实施可验证的排除规则或关闭备份；随后建立用户主动加密备份，再评估数据库透明加密。
- **优点**：降低立即迁移风险，优先解决真正的导出和备份场景。
- **缺点**：设备被完整提取时，明文数据库保护有限。
- **风险**：备份规则缺失是当前必须修复的隐私问题。
- **重新评估**：明确威胁模型、密钥丢失处理和 Room/SQLCipher 迁移测试后。

## ADR-008：Glance 先试点，不立即替换 RemoteViews

- **背景**：当前 Evolune 使用 RemoteViews；迁移包包含 Glance 小组件，但其实现与 Featherline 数据模型、Hilt 和配置系统紧密耦合。
- **可选方案**：立即全面迁移；继续 RemoteViews；抽出 snapshot 后做一个 Glance 试点。
- **最终建议**：第三种方案。
- **优点**：保持现有可靠路径，同时验证现代 API 和 OEM 行为。
- **缺点**：短期会维护两种 provider。
- **风险**：状态更新、预览、尺寸和交互差异。
- **重新评估**：至少完成目标 OEM Launcher 矩阵后。

## ADR-009：Google Drive 不作为默认同步方案

- **背景**：迁移包中的 Drive 快照包含 OAuth、加密数据库、冲突决策和 WorkManager，但当前 Evolune 没有加密备份格式和账户体系。
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
