# Evolune 旧功能规格 Clean-Room 审计报告

**审计日期**: 2026-08-01
**审计范围**: `docs/legacy-specs/` 下 7 份规格文件
**审计基准**: `docs/evolune/ARCHITECTURE.md`, `DECISIONS.md`, `MIGRATION_PLAN.md`, `ROADMAP.md`, `FEATURE_MATRIX.md`, `SOURCE_PROVENANCE.md`
**审计方法**: 逐行交叉比对，对照 12 项 clean-room 检查清单
**规则**: 本轮不修改任何文件、不生成补丁、不补充实现细节

---

## 逐份审计

---

### 1. health-connect.md — "修改后可用"

**综合评估**: 规格整体聚焦于用户可观察行为，数据字段以业务含义描述。但在 Evolune 数据模型映射、过时 Featherline 功能引用和少量平台约定的准确性上存在问题。

#### 问题清单

**I-LS-001**
- **文件**: `docs/legacy-specs/health-connect.md`
- **章节**: 第 7 节（数据字段业务含义）/ 药物目录、血液检测、日记
- **严重程度**: P1
- **置信度**: 高
- **问题类型**: 功能边界错误
- **具体问题**: 规格在"与其他功能的关系"（第 13 节）和"Auto Backup 隔离"（验收测试 T31-T32）中引用了"血液检测、日记、药物目录（库存追踪）、Anchor 小组件"作为被 Health Connect 设置隔离的对象。这些功能在 Evolune 中**当前不存在**，且 ROADMAP 均标记为 P2–P3。验收测试 T22-T23 同样引用血液检测和日记恢复。
- **合规/工程风险**: 若按此规格实现，验收测试 T22-T23 将不可执行。开发者可能误认为这些功能是 Health Connect 实现的前置条件，从而阻塞进度。
- **最小修改建议**: 在验收测试 T22-T23 和"与其他功能的关系"表格中，将 Evolune 不存在的功能（血液检测、日记笔记、药物库存、Anchor 小组件）标记为"Evolune 暂不存在"或直接移除。验收测试应当只绑定到 PRODUCT_OVERVIEW 中已列入计划的功能（PK、药物计划、记录、设置）。
- **是否阻止交给 Codex**: 是（需先修正，否则开发者会被误导）
- **需要项目所有者决定**: 否

**I-LS-002**
- **文件**: `docs/legacy-specs/health-connect.md`
- **章节**: 第 5 节（用户操作流程）— 体重读取验证方式
- **严重程度**: P2
- **置信度**: 中
- **问题类型**: 事实来源错误
- **具体问题**: 规格描述 "体重同步启用后，应用内的'体重'字段被更新为 Health Connect 中读取的数值" 以及验收测试 T10-T13 验证 "应用内体重被同步更新"。这与 ADR-003（Room 数据库是主要事实来源）和 ADR-004（Health Connect 只做可选交换层）一致——但当前 Evolune 中体重存储在 `SettingsDataStore` (DataStore Preferences)，而规格将此描述为 "覆盖用户手动输入的值"（T37 验收测试）。`SettingsDataStore` 不区分数据来源，Health Connect 写入后会**静默覆盖**手动输入值，用户无法追溯来源。这违反了数据来源可解释性原则。
- **合规/工程风险**: 实现后用户手动输入的体重可能被 Health Connect 无提示覆盖，且无法区分两种来源。ADRs 要求 "记录来源"，但当前架构没有来源字段。
- **最小修改建议**: 在规格中添加"Health Connect 同步的体重应携带来源标记（Health Connect provider 名称 + 记录时间）"，验收测试 T10 增加 "用户能在设置页看到体重来源"。或明确标注此为 "Evolune 不具备来源字段时的临时行为"。
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 是（需决定体重数据的来源审计时机）

**I-LS-003**
- **文件**: `docs/legacy-specs/health-connect.md`
- **章节**: 第 13.1 节（与本地文件备份的关系）
- **严重程度**: P2
- **置信度**: 高
- **问题类型**: 技术绑定
- **具体问题**: 规格声明 "云端同步与手动导出的本地文件备份（`.hrtbackup` 文件）使用相同的加密格式" 并详细描述 Argon2id + AES-256-GCM 加密。但 `.hrtbackup` 是 Featherline 的文件扩展名和加密方案。Evolune 的当前导出格式是 `MahiroJsonFormat`（纯文本 JSON），不是加密的二进制 blob。ARCHITECTURE.md 第 7 阶段的加密备份格式尚未设计。
- **合规/工程风险**: `.hrtbackup` 作为 Featherline 特有命名不应该出现在独立规格中。加密算法选择（Argon2id、AES-256-GCM）不是单纯的产品行为——它们是 Featherline 的技术决策。
- **最小修改建议**: 将 `.hrtbackup` 替换为 "加密备份文件" 描述，去掉具体算法名称 Argon2id 和 AES-256-GCM，替换为 "用户设置的密码保护的加密格式（具体算法待 Evolune 独立评估）"。
- **是否阻止交给 Codex**: 是（`hrtbackup` 是 Featherline 专属命名，pass 此元素会有许可风险）
- **需要项目所有者决定**: 否

**I-LS-004**
- **文件**: `docs/legacy-specs/health-connect.md`
- **章节**: 第 6 节（数据字段业务含义）— 给药文本字段映射
- **严重程度**: P2
- **置信度**: 中
- **问题类型**: 实现结构暗示
- **具体问题**: FHIR MedicationStatement 字段映射表列出了十分详细的字段对应关系（如 `dosage.doseAndRate.doseQuantity` 包含 "mg、mL、g 或 tablet（含 UCUM 系统编码，视制剂类型而定）"）。这个级别的映射详细度接近 Featherline 的 `MedicationStatementFhirMapper.kt` 的实现结构。`medicationCodeableConcept` 用 "Estradiol valerate · Injection" 格式 —— 这个连接符格式是 Featherline UI 的显示格式，不是 FHIR 标准规定。
- **合规/工程风险**: 开发者可能将此映射表直接转化为代码中的字段映射逻辑，从而在名义上"独立实现"但实质上复制了 Featherline 的数据结构设计。
- **最小修改建议**: 字段表精简为：只需说明（1）映射目标 FHIR 资源类型；（2）关键字段的业务含义（药物名称、剂量、时间、状态）；（3）映射标识使用本地记录 UUID 以保证幂等。移除具体的连接符格式和枚举级映射细节。
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 否

**I-LS-005**
- **文件**: `docs/legacy-specs/health-connect.md`
- **章节**: 第 9 节（边界情况）— 体重单位硬编码
- **严重程度**: P3
- **置信度**: 低
- **问题类型**: 平台约束准确性问题
- **具体问题**: "以千克为单位存储的体重值" 和 "用户输入时的原始数值和单位" —— 当前 Evolune 中体重输入**仅支持 kg**（SettingsDataStore 用 Double 值，UI 只展示 kg），没有"原始数值和单位"概念。规格描述了一个比当前 Evolune 更丰富的体重数据模型。
- **合规/工程风险**: 实现者可能创建比 Evolune 实际需要更复杂的体重模型。
- **最小修改建议**: 体重数据描述简化为 "以千克为单位的单一数值；未来可扩展多单位支持"。
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 否

**I-LS-006**
- **文件**: `docs/legacy-specs/health-connect.md`
- **章节**: 第 10 节（离线行为）— 缺少前台触发说明
- **严重程度**: P3
- **置信度**: 低
- **问题类型**: 遗漏
- **具体问题**: 离线行为描述为 "Health Connect 集成不使用网络；所有读写操作通过本地 Health Connect provider IPC 完成" —— 这是正确的。但规格未说明 Health Connect provider 自身是否可在无 Google Play Services 的设备上使用（例如华为设备不使用 GMS），而 ARCHITECTURE.md Phase 5 的完成定义要求 "至少两个目标 Android/Provider 组合验证通过"。
- **合规/工程风险**: 验收测试仅在单设备/单提供商上通过会遗漏关键兼容性问题。
- **最小修改建议**: 在验收测试中添加 "在无 Google Play Services 的 Android 设备上，Health Connect 分区显示不可用状态" 作为 T01 变体。
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 否

---

### 2. cloud-sync.md — "不建议用于实现"（当前阶段）

**综合评估**: 这是一份**完整的 cloud sync 功能规格**，覆盖 Google Drive 集成、加密备份、冲突解决和 WorkManager 后台调度。但问题在于：它描述了 ROADMAP 标记为 **P3**、MIGRATION_PLAN 标记为 **Phase 7** 的功能，远超当前开发阶段。包含大量 Featherline 专属的实现约定，且与当前的架构决策存在严重冲突。可以作为**远期参考**保留，但不能在当前阶段交给实现者。

#### 问题清单

**I-LS-007**
- **文件**: `docs/legacy-specs/cloud-sync.md`
- **章节**: 全文 — 标题和内容
- **严重程度**: P0
- **置信度**: 高
- **问题类型**: 超出开发阶段要求
- **具体问题**: 整份规格描述了一个 ROADMAP 标记为 **P3**（长期规划）、MIGRATION_PLAN 标记为 **Phase 7**（云同步）的功能。标题为 "Google Drive 加密云端备份与同步"——Google Drive 作为唯一 provider 的绑定。但 ADR-009 明确说 "Google Drive 不作为默认同步方案"，ROADMAP 明确说 "P3：Google Drive appData、用户可见文件或 WebDAV 的小范围实验"。没有本地加密备份格式（P2）、没有冲突策略、没有 OAuth 客户端时，让实现者阅读这份规格会产生严重误导。
- **合规/工程风险**: 若在 Phase 0–2 将此规格交给实现者，开发者可能将云同步误解为优先任务，从而在数据模型尚未稳定时引入 OAuth、WorkManager、加密备份等复杂依赖。
- **最小修改建议**: 添加醒目前置声明："本文档仅作为远期参考。在 ROADMAP 的 P2 本地加密备份和 P3 云同步阶段到达前，不得作为开发任务。当前 Evolune 缺少加密备份格式、密钥策略和 OAuth 客户端。"
- **是否阻止交给 Codex**: 是（除非加上声明，否则实现者将开始编码 P3 功能）
- **需要项目所有者决定**: 否

**I-LS-008**
- **文件**: `docs/legacy-specs/cloud-sync.md`
- **章节**: 第 7 节（数据字段的业务含义）— 备份数据范围
- **严重程度**: P1
- **置信度**: 高
- **问题类型**: 功能边界错误
- **具体问题**: 规格列出的备份数据包括 "血液检测数据"（7.7）、"日记日期标记与笔记"（7.8）、"药物目录含库存追踪"（7.4）、"已追踪日期（里程碑）"（7.8）、"视觉辅助偏好：毛玻璃模糊效果、CJK 文字对齐偏移"（7.2）。这些功能在 Evolune 中**全部不存在**。Evolune 当前只有 `dose_events` 和 `medication_plans` 两张表、DataStore 偏好和 PK 计算。备份 envelope 应当只包含 Evolune 计划中存在的数据（用药记录、方案、设置、体重）。包含不存在的数据会导致备份格式定义过度膨胀且不可测试。
- **合规/工程风险**: 如果为该备份 envelope 定义文件格式和加密方案，Evolune 将承担为不存在的功能维持向后兼容的负担。
- **最小修改建议**: 在备份 envelope 中移除 Evolune 不存在的数据类别（血液检测、日记、药物目录含库存、已追踪日期、模糊效果偏好、CJK 对齐偏好）。保留当前存在的数据（用药记录、方案、设置、体重），其他类别标注为 "未来扩展字段，当前为空".
- **是否阻止交给 Codex**: 是（备份格式定义过度会影响 Phase 7 设计）
- **需要项目所有者决定**: 否

**I-LS-009**
- **文件**: `docs/legacy-specs/cloud-sync.md`
- **章节**: 第 13.1 节（与本地文件备份的关系）
- **严重程度**: P1
- **置信度**: 高
- **问题类型**: 技术绑定 + 命名污染
- **具体问题**: 与 I-LS-003 相同 —— `.hrtbackup` 文件扩展名和 "Argon2id 密钥派生 + AES-256-GCM 对称加密" 是 Featherline 专属技术选择。这不是 "可观察产品行为"。用户可观察的是 "文件可以被密码保护"，不是底层加密算法的选择。
- **合规/工程风险**: 算法选择不是 clean-room 规格的合理内容——它们是实现者在 Evolune 架构下的工程决策。
- **最小修改建议**: 移除 Argon2id 和 AES-256-GCM 的具体算法引用，替换为 "通过用户密码加密（密码强度验证由实现决定，算法应选择广泛审核过的标准加密方案）"。移除 `.hrtbackup` 扩展名。
- **是否阻止交给 Codex**: 是
- **需要项目所有者决定**: 否

**I-LS-010**
- **文件**: `docs/legacy-specs/cloud-sync.md`
- **章节**: 第 3 节（输入）— 同步密码
- **严重程度**: P2
- **置信度**: 高
- **问题类型**: 技术绑定
- **具体问题**: "密码以 Android Keystore 包装后存储，重启后可直接读取"（第 11 节）。这是 Featherline 的 KeyStore 密钥管理方式的具体描述。虽然 Android Keystore 是标准 API，但 "exported key material wrapping" 的具体方式（是否导出包装密钥、使用哪些算法参数）是 Featherline 的实现细节。
- **合规/工程风险**: 将 Featherline 的密钥管理方式描述为产品行为可能限制 Evolune 独立选择（例如使用 AndroidX Security Crypto 库的 EncryptedSharedPreferences 或 MasterKeys API）。
- **最小修改建议**: 替换为 "密码通过平台标准安全存储机制保存（如 Android Keystore），用户正常使用期间无需重复输入密码"。
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 否

**I-LS-011**
- **文件**: `docs/legacy-specs/cloud-sync.md`
- **章节**: 第 8 节（边界情况）— 大小限制常量
- **严重程度**: P3
- **置信度**: 中
- **问题类型**: 实现结构暗示
- **具体问题**: "Google Drive 上的文件超过大小限制（快照 128 MiB，清单 256 KiB）" —— 128 MiB 和 256 KiB 这些精确数字是 Google Drive API 限制还是 Featherline 的内部限制？如果是内部限制，则是一个实现选择，不应该出现在规格中。
- **合规/工程风险**: 128 MiB 远大于合理用药数据备份的大小——常规用药记录备份可能不超过 1 MB。这个值可能是 Featherline 处理大型数据库快照时的经验值。
- **最小修改建议**: 替换为 "单个备份文件有大小上限；超过时同步失败并提示用户" —— 不指定具体数值。
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 是（需要确认这些值来自 Google API 限制还是 Featherline 内部设定）

---

### 3. phone-widgets.md — "修改后可用"

**综合评估**: 规格详尽地描述了两种小组件尺寸的用户可观察行为和视觉规格，整体 clean-room 质量较高。主要问题是 Anchor/Tracked Date 小组件的越界引用和部分视觉规格的过度精确性。

#### 问题清单

**I-LS-012**
- **文件**: `docs/legacy-specs/phone-widgets.md`
- **章节**: 第 13.6 节（Anchor/Tracked Date 小组件）
- **严重程度**: P1
- **置信度**: 高
- **问题类型**: 功能边界错误
- **具体问题**: 规格描述 "应用还包含一种 Anchor 小组件（追踪日期小组件，2×1）……该小组件与本文档描述的服药剂量小组件完全独立"。Tracked Date / Anchor 小组件在 ROADMAP 和 FEATURE_MATRIX 中标记为 **P2（1.0，非 MVP）**，且 `tracked-date-widget.md` 是独立规格。在第 13.6 节将 Anchor 小组件描述为**已存在的分离功能**，可能误导开发者认为该功能已经实现或应该与服药小组件同时交付。
- **合规/工程风险**: 开发者可能将 Anchor 小组件的配置界面和存储纳入 Phase 3 的 Widget 工作，违反 "Tracked Date 不进入 MVP" 的规划。
- **最小修改建议**: 在第 13.6 节添加 "Tracked Date 小组件为独立功能，不随本文档中的服药小组件一同交付。其优先级为 P2（1.0，非 MVP），规格见 `tracked-date-widget.md`。两者共享外观设置存储，但数据逻辑互不干扰。"
- **是否阻止交给 Codex**: 否（但如果实现者看到此段，需提前警告）
- **需要项目所有者决定**: 否

**I-LS-013**
- **文件**: `docs/legacy-specs/phone-widgets.md`
- **章节**: 第 7 节（数据字段的业务含义）— hideMedicationDetails
- **严重程度**: P2
- **置信度**: 中
- **问题类型**: Evolune 架构映射不准确
- **具体问题**: 字段表描述了 `hideMedicationDetails`、`pkProjection`、`e2DisplayUnit`、`appLanguage`、`showArchivedGroupRecords` 等字段，这些字段**部分对应** Evolune 当前的数据模型（如 `MedicationPlan.isEnabled`、`SettingsDataStore`），但它们的组合方式和快照生成逻辑不完全一致。特别是 `hasActiveGroups`、`manualCount`、`contextChip`（LAST_NIGHT/COMING_UP 分组）等字段是 Featherline 小组件快照模型的内部表示。规格没有说 "这些字段是 Featherline 内部结构的抽象"，而是直接作为 Evolune 小组件的行为描述。
- **合规/工程风险**: 实现者可能直接按此字段列表设计 Evolune 的 WidgetSnapshot 数据类，导致与 Evolune 数据模型的实际结构出现不必要的映射层。
- **最小修改建议**: 在字段表前添加说明："以下字段描述的是用户可观察信息和小组件展示逻辑的业务含义。Evolune 实现时应根据自身数据模型决定快照结构、字段命名和派生方式。所列字段组合不代表 Featherline 的内部数据类。"
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 否

**I-LS-014**
- **文件**: `docs/legacy-specs/phone-widgets.md`
- **章节**: 第 12 节（可观察的视觉规格）— 精确视觉参数
- **严重程度**: P2
- **置信度**: 中
- **问题类型**: 视觉设计来源不清
- **具体问题**: 规格列出了极度精确的视觉参数：圆角 22 dp / 10 dp / 999 dp（12.1）、进度条高度 6 dp + 间距 3 dp（12.3）、左强调条 6 dp × 44 dp（12.4）、字体 18 sp / 14 sp（12.4）、预览画布 306 × 276 dp / 624 × 276 dp（12.8）。这些参数来源于 Featherline 的 Glance/RemoteViews 小组件布局的精确测量值。如果这些值直接转化为 Evolune 的布局参数，相当于在**视觉设计层面**复制 Featherline。
- **合规/工程风险**: dp 值不属于算法或源码的范畴，但大规模、精确的视觉参数复制可能构成对 Featherline 设计语言的非源码级照搬。风险较低，但需要在设计审查中确认。
- **最小修改建议**: 在小节开头添加声明："以下数值反映了原参考实现中的视觉规格，Evolune 实现时应根据自身设计系统（`core:designsystem`）调整尺寸比例和间距。所列数值为可观察的品质基准，不意味着必须逐像素复制。"
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 是（需要设计审查确认这些视觉参数是否构成 Featherline 品牌资产）

**I-LS-015**
- **文件**: `docs/legacy-specs/phone-widgets.md`
- **章节**: 第 13.3 节（与 PK 血药浓度估算的关系）
- **严重程度**: P3
- **置信度**: 中
- **问题类型**: 数据路径描述不完整
- **具体问题**: "小组件不独立运行 PK 计算，而是读取应用预计算并写入快照的投影结果。" —— 这是正确的数据流描述。但当前 Evolune 的 PK 计算在 `HRTViewModel.runSimulation()` 中执行，由 `combine(events, enabledPlans, bodyWeightKG)` 触发，未生成持久化的快照投影。小组件当前通过 `EvoluneWidgetReceiver` 直接读取 Room 数据库，不经过快照。规格与当前代码的实现路径不一致，但这是架构演进方向（`Widget` → `WidgetSnapshot` → Room），这个差异是可接受的。
- **合规/工程风险**: 无立即风险。
- **最小修改建议**: 无需修改。
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 否

---

### 4. tracked-date-widget.md — "修改后可用"

**综合评估**: 体积最小的规格，聚焦于单一小组件类型。主要问题是 Anchor 命名残留和优先级不匹配。

#### 问题清单

**I-LS-016**
- **文件**: `docs/legacy-specs/tracked-date-widget.md`
- **章节**: 全文 — 功能名称 "追踪日期小组件（Anchor/Tracked Date Widget）"
- **严重程度**: P1
- **置信度**: 高
- **问题类型**: 命名污染 + 优先级冲突
- **具体问题**: 全文反复使用 "Anchor" 作为 "Tracked Date" 的备选名称。`Anchor` 是 Featherline 代码中对该功能的内部命名（`WidgetConfigActivity`、`WidgetConfigScreen` 等源码和 `AnchorSelectionResolutionTest.kt` 测试文件中使用）。这不是一个面向用户的功能名称。更严重的是，整份规格描述了一个 `P2（1.0，非 MVP）` 功能，但文件结构与 phone-widgets.md 并列，没有级别标注。与 `ARCHITECTURE.md` ADR-011 "在产品所有者确认前，统一标记为 P2（1.0，待产品确认，非 MVP），Phase 1 不创建实体或迁移" 矛盾。
- **合规/工程风险**: 开发者可能将 Anchor 小组件的实现与服药小组件同步纳入 Phase 3 (widget) 任务。
- **最小修改建议**:
  1. 全文移除 "Anchor" 一词，统一使用 "追踪日期（Tracked Date）"
  2. 文件开头添加醒目声明："**优先级**: P2（1.0，非 MVP，待产品确认）。Phase 1 不创建实体或迁移。参见 `DECISIONS.md` ADR-011。"
  3. 验收测试全部保持，但标注为 "在 Tracked Date 功能进入开发后生效"
- **是否阻止交给 Codex**: 是（需先添加优先级声明，否则开发者会将其纳入当前工作）
- **需要项目所有者决定**: 否

**I-LS-017**
- **文件**: `docs/legacy-specs/tracked-date-widget.md`
- **章节**: 第 6 节（数据字段的业务含义）— 渐变彩旗背景
- **严重程度**: P2
- **置信度**: 高
- **问题类型**: 品牌资产风险
- **具体问题**: "渐变彩旗背景"包含 "彩虹旗、跨性别旗等共 9 种，外加'无'"。这些旗帜图案是 Featherline 的设计资产（可能是 SVG/PNG 图片或程序化渐变）。spec 描述其存在是合理的（用户可观察行为），但如果这些旗帜的**具体渐变停止颜色、角度和图案**来自 Featherline 的资源文件，复制它们可能构成视觉资产侵权。
- **合规/工程风险**: Evolune 如需提供同等功能，应独立设计旗帜图案，不能从 Featherline 的资源文件中提取色值和渐变定义。
- **最小修改建议**: 在"渐变彩旗背景"描述中添加："旗帜图案的具体设计（颜色、渐变、形状）应由 Evolune 独立创作，不参考 Featherline 的图片或 SVG 资源。"
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 否

**I-LS-018**
- **文件**: `docs/legacy-specs/tracked-date-widget.md`
- **章节**: 第 6 节（数据字段的业务含义）— 全局共享设置
- **严重程度**: P2
- **置信度**: 中
- **问题类型**: 架构集成描述不足
- **具体问题**: "所有外观参数……均为全局共享设置，修改后对该应用所有小组件类型（追踪日期小组件和服药进度小组件）统一生效"。这描述了一个跨小组件类型共享的配置存储。当前 Evolune 没有小组件外观配置存储，DataStore 中的 `UserSettings` 包含 `themeMode` 和 `colorTheme` 但这不是小组件级的外观定制。此规格引入了 Featherline 的完整外观系统（色相、饱和度、明暗平衡、内容缩放、背景透明度、深色模式），但 EVOLUYE 的 `core:designsystem` 尚未定义小组件级外观参数。
- **合规/工程风险**: 实现者可能直接在 DataStore 中创建 Featherline 外观系统所需的所有键，引入不必要的数据模型膨胀。
- **最小修改建议**: 添加说明 "外观设置的范围和粒度应由 Evolune 的 `core:designsystem` 定义，当前规格中的参数仅反映可观察行为，不指示存储方案。"
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 否

---

### 5. wear-os-app.md — "修改后可用"

**综合评估**: 聚焦于 Wear 手表 App 的用户交互流程和状态变化，clean-room 质量较好。主要问题是缺少 P1/P2 优先级声明和相关依赖说明。

#### 问题清单

**I-LS-019**
- **文件**: `docs/legacy-specs/wear-os-app.md`
- **章节**: 全文 — 无优先级/阶段声明
- **严重程度**: P1
- **置信度**: 高
- **问题类型**: 超出开发阶段要求
- **具体问题**: 规格描述了一个完整的 Wear OS 独立 App（非 Tile），包含主屏幕、进度计数、E2 卡片、48 小时曲线、最近记录、五项计划、记录/跳过/撤销操作。但 FEATURE_MATRIX 将此标记为 **"当前不存在"**，优先级为 **P1（1.0）**。ROADMAP 将其放在 1.0 阶段而非 MVP。当前 Evolune 只有 Wear Tile（`DoseTileService`），没有可独立启动的 Wear Activity。**Wear 协议版本化**（`core:wear-protocol`）是此 App 的前置依赖，而协议模块在 FEATURE_MATRIX 中也是 **"当前不存在"**。
- **合规/工程风险**: 与 cloud-sync.md 类似，在协议尚未版本化、Wear 基础设施不存在的情况下，开发者按此规格实现将遇到严重阻塞。
- **最小修改建议**: 文件开头添加："**优先级**: P1（1.0 阶段），前置条件：`core:wear-protocol` 和 `core:wear-bridge` 模块完成（Phase 4）。当前 Evolune 仅提供 Wear Tile，此规格描述的独立 App 尚不具备基础设施。参见 `MIGRATION_PLAN.md` Phase 4 和 `FEATURE_MATRIX.md`。"
- **是否阻止交给 Codex**: 是（需先添加阶段声明）
- **需要项目所有者决定**: 否

**I-LS-020**
- **文件**: `docs/legacy-specs/wear-os-app.md`
- **章节**: 第 4 节（数据字段的业务含义）— 近 48 小时曲线
- **严重程度**: P2
- **置信度**: 中
- **问题类型**: 实现结构暗示
- **具体问题**: "25 个采样点、每点间隔 2 小时的浓度历史曲线" —— 25 个点 × 2 小时间隔 = 48 小时。这是 Featherline 的具体采样策略。不同采样策略会产生不同的曲线平滑度：Evolune 可以选择 97 个点 × 30 分钟 = 48 小时，或其他采样密度。将 25 个点作为规格可能限制了 Evolune 独立选择采样方案。
- **合规/工程风险**: 低。采样点数不影响用户可观察行为（用户在手表上看到的是平滑曲线）。但如果将此写入验收测试 T5，测试可能过度约束实现。
- **最小修改建议**: 将 "25 个采样点、每点间隔 2 小时" 改为 "覆盖过去约 48 小时的浓度采样点序列，采样密度应使渲染曲线光滑且点间可见变化合理"。
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 否

**I-LS-021**
- **文件**: `docs/legacy-specs/wear-os-app.md`
- **章节**: 第 11 节（数据持久性与重启）— 加密缓存
- **严重程度**: P2
- **置信度**: 中
- **问题类型**: 技术绑定
- **具体问题**: "手表端缓存快照使用 AES-256-GCM 加密，密钥存储于手表的 Android Keystore"。与 I-LS-009 相同 —— 虽然 Android Keystore 是标准 API，选择 AES-256-GCM 而非其他 AEAD 算法是一个 Featherline 的技术选择，不是用户可观察的产品行为。手表端加密需求是合理的（protect health data at rest），但算法选择应该留给 Evolune 的安全工程决策。
- **合规/工程风险**: 后续安全审查可能选择不同的加密方案（如 AES-256-CBC + HMAC，或完全依赖 AndroidX Security Crypto 的默认选择）。规格不应该锁定算法。
- **最小修改建议**: "手表端缓存快照使用 Android 平台标准加密存储保护，密钥由设备硬件安全模块管理"。
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 否

---

### 6. wear-os-tile.md — "Clean-room ready"

**综合评估**: 七份规格中最干净的一份。聚焦于 Tile 的有限用户交互（查看下一项计划、一键记录、一键跳过），不描述完整 App。数据字段以业务含义描述，视觉规格适度。与当前 Evolune 的 Tile 实现差异明显但都在行为层面——当前 Tile 没有操作按钮（只显示浓度和计划名称），而此规格描述了完整的记录/跳过操作。这被视为**目标行为**，与架构计划一致（需要 `core:wear-protocol` 版本化后才可安全实现）。

无明显 clean-room 冲突问题。验收测试直接可映射到用户可观察行为。

**建议**: 在开头添加 "**优先级**: P1（MVP 扩展），前置条件：`core:wear-protocol` 版本化（Phase 4）。当前 Evolune Tile 为基础实现，仅展示浓度和计划名称，无操作按钮。此规格描述的记录/跳过功能在协议版本化和命令去重机制就绪后启用。"

---

### 7. wear-protocol.md — "修改后可用"

**综合评估**: 描述了手机与手表之间的协议格式和同步流程。整体以行为描述为主，但个别字段的枚举值列表和采样策略细节可能暗示 Featherline 的实现结构。

#### 问题清单

**I-LS-022**
- **文件**: `docs/legacy-specs/wear-protocol.md`
- **章节**: 第 3 节（输入/输出）— 快照数据结构
- **严重程度**: P2
- **置信度**: 中
- **问题类型**: 实现结构暗示
- **具体问题**: 协议字段列表包含 "最多约 64 条"（计划行数限制）、"最多约 97 个点"（血药浓度采样点数）、"约 256 KB"（快照大小上限）。这些精确限制可能来源于 Featherline 的 Wear Data Layer 实现经验值（DataClient 对 DataItem 有大小和复杂度限制），但具体数值是 Featherline 的工程选择。Wearable Data Layer 的官方建议是 DataItem 小于 100KB，256KB 是一个校准后的值。
- **合规/工程风险**: Evolune 的快照复杂度不同（字段更少或更多），硬编码 Featherline 的限制会导致过早优化或容量不足。
- **最小修改建议**: 将这些限制表述为 "计划行数有上限（具体值由 Evolune 根据 Wear Data Layer payload 容量测试确定）" 和 "血药浓度采样点覆盖约 48 小时，点间距由手机端决定"。
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 是（需要在真实设备上验证 Wear Data Layer 的 payload 容量限制）

**I-LS-023**
- **文件**: `docs/legacy-specs/wear-protocol.md`
- **章节**: 第 5 节（状态变化）— 命令类型枚举
- **严重程度**: P2
- **置信度**: 低
- **问题类型**: 实现结构暗示
- **具体问题**: 协议定义了三种命令类型：记录服药、跳过本次、撤销服药。这是合理的协议语义。但规格提到 "每条命令均包含一个唯一的请求 ID，供手机端去重和幂等处理" 以及 "手机通过请求 ID 去重，相同请求 ID 的命令只执行一次"（第 8 节）。这个 "requestId + 幂等" 模式与 Featherline 的 `WearProtocol` 实现非常接近。请求 ID 的生成逻辑（手表端如何生成 UUID、是否使用 `UUID.nameUUIDFromBytes`）没有在规格中说明——这恰好说明规格成功避免了实现细节。
- **合规/工程风险**: 低。请求 ID 幂等是分布式系统的通用模式，不是 Featherline 专有。
- **最小修改建议**: 无需修改。
- **是否阻止交给 Codex**: 否
- **需要项目所有者决定**: 否

---

## 各规格结论汇总

| 规格文件 | 结论 | 阻止交给 Codex | 主要原因 |
|----------|------|---------------|---------|
| `health-connect.md` | **修改后可用** | I-LS-001, I-LS-003 | 不存在的 Evolune 功能引用、`.hrtbackup` 命名污染 |
| `cloud-sync.md` | **不建议用于实现**（当前阶段） | I-LS-007, I-LS-008, I-LS-009 | P3 功能、Google Drive 绑定、不存在的 Evolune 数据 |
| `phone-widgets.md` | **修改后可用** | I-LS-012 | Anchor 小组件越界引用、视觉参数来源待确认 |
| `tracked-date-widget.md` | **修改后可用** | I-LS-016 | Anchor 命名、缺少 P2 优先级声明 |
| `wear-os-app.md` | **修改后可用** | I-LS-019 | 缺少阶段声明、前置条件未标注 |
| `wear-os-tile.md` | **Clean-room ready** | 无 | 建议添加优先级和前置条件声明 |
| `wear-protocol.md` | **修改后可用** | 无 | 精确限制值可能来自 Featherline 经验 |

---

## 跨文件系统性问题

**I-LS-024** — Tracked Date 规格优先级全局不一致
- **严重程度**: P1
- **置信度**: 高
- **问题类型**: 文档冲突（全局性）
- **影响文件**: `phone-widgets.md` §13.6, `tracked-date-widget.md`（全文）
- **问题**: Tracked Date 功能在 `FEATURE_MATRIX.md` 和 `ROADMAP.md` 中标记为 P2（1.0，非 MVP），但其专用规格文件 `tracked-date-widget.md` 和 `phone-widgets.md` 的 §13.6 未携带此优先级信息。`cloud-sync.md` 的 §7.8 还将 "已追踪日期" 作为备份数据。`health-connect.md` 的验收测试 T22 验证日记恢复。所有这些跨文件引用共同构建了一个 Tracked Date 功能在开发范围内已存在的印象。
- **最小修改建议**: 在所有引用 Tracked Date / Anchor / Journal / Diary 的规格中添加统一声明："Tracked Date 功能当前为 P2（1.0，非 MVP，待产品确认），相关验收测试仅在功能进入开发后生效。"
- **需要项目所有者决定**: 是（需要确认 Tracked Date 的产品优先级并在所有文档中统一传播）

**I-LS-025** — 加密算法选择未被识别为实现决策
- **严重程度**: P2
- **置信度**: 高
- **问题类型**: 技术绑定（跨三份文件）
- **影响文件**: `cloud-sync.md` §13.1, `wear-os-app.md` §11, `health-connect.md` §7.1
- **问题**: 三份文件分别在不同上下文中提到 "Argon2id + AES-256-GCM" 作为加密方案（云同步备份、Wear 缓存加密、Health Connect 关联格式）。虽然 AES-GCM 和 Argon2 是现代加密的最佳实践，但将具体的算法组合写入行为规格等同于 Featherline 安全架构的技术输出。Evolune 的安全工程审计应独立选择算法组合和参数。
- **最小修改建议**: 全局替换为 "通过密码或平台密钥进行加密保护（具体算法和参数由 Evolune 安全策略决定）"。
- **需要项目所有者决定**: 否

**I-LS-026** — 血液检测和日记功能扩散到多份规格
- **严重程度**: P2
- **置信度**: 高
- **问题类型**: 功能边界错误（跨两份文件）
- **影响文件**: `cloud-sync.md` §7.7-7.8, `health-connect.md` 验收测试 T22-T23
- **问题**: 血液检测和日记功能在 Evolune 中不存在，ROADMAP 也未列入（无任何 P-level 提及）。然而 cloud-sync.md 将 "血液检测面板、自定义指标、日记笔记" 作为备份数据；health-connect.md 将 "血检面板恢复" 和 "日记数据恢复" 作为验收测试。这些引用为不存在的功能建立了虚假的跨功能依赖。
- **最小修改建议**: 从 cloud-sync.md 和 health-connect.md 中移除血液检测和日记相关的备份/测试条目。若需要为未来扩展预留，创建单独的 "未来功能扩展点" 小节并标注 "当前不存在"。
- **需要项目所有者决定**: 是（需决定这些功能是否进入产品路线图）

---

## 基准文档审计结论

所有 legacy-spec 文件均与 ARCHITECTURE.md 的模块边界和 DECISIONS.md 的决策一致，**除以下例外**：

| Legacy Spec | 与基准文档的冲突 | 问题 ID |
|-------------|---------------|---------|
| `cloud-sync.md` | 与 ADR-009 "Drive 不作为默认" 和 ROADMAP P3 矛盾 | I-LS-007 |
| `cloud-sync.md` | 未区分 `core:wear-bridge`/`feature:backup`/`core:sync` 三个边界（ADR-012） | I-LS-007 |
| `tracked-date-widget.md` | 与 ADR-011 "统一标记为 P2" 和 FEATURE_MATRIX P2 矛盾 | I-LS-016 |
| `wear-os-app.md` | 缺少 Phase 4 前置条件（`core:wear-protocol`）声明 | I-LS-019 |
| `health-connect.md` | 未声明 Evolune 当前无血检/日记/库存功能 | I-LS-001 |
| `phone-widgets.md` | 引用 Anchor/Tracked Date 小组件为存在功能 | I-LS-012 |

---

## 审计总结

**7 份 legacy-spec 文件的 clean-room 质量整体良好**。规格作者在以下方面做得正确：

1. 无原项目类名、函数名、文件路径或包名泄露
2. 无伪代码、算法步骤或代码片段
3. 无 XML 布局、图片资源引用或原项目测试数据
4. 验收条件以用户可观察行为描述，不含代码
5. 正确声明 "Evolune 实现必须采用自身架构和模型"

**3 份文件存在需要立即修正的问题**（阻止交给实现者）：
- `cloud-sync.md` — 需重大修正或降级为远期参考
- `tracked-date-widget.md` — 需添加优先级声明和移除 Anchor 命名
- `wear-os-app.md` — 需添加阶段声明
- `health-connect.md` — 需移除不存在功能的引用

**1 份文件可以不做修改直接使用**：
- `wear-os-tile.md` — 只需建议性的前提条件声明

**最需要项目所有者决策的问题**：
1. Tracked Date 的最终优先级（影响 5 份规格文件）
2. Google Drive 作为云同步 provider 的决策范围（影响 `cloud-sync.md` 的存废）
3. `.hrtbackup` 命名是否属于 Featherline 专属资产
4. 加密算法选择是否应从所有规格中移除

---

*审计报告结束。所有发现基于 2026-08-01 的仓库快照。*
