# Evolune Legacy Spec 审阅处置记录 V1

**核验日期**：2026-08-01

**核验范围**：`docs/legacy-specs/` 全部 7 份规格、`reviews/LEGACY_SPEC_AUDIT_V1.md`、`docs/evolune/ARCHITECTURE.md`、`DECISIONS.md`、`MIGRATION_PLAN.md`、`ROADMAP.md` 与 `docs/SOURCE_PROVENANCE.md`

**变更边界**：只修改本文档、处置结果为 `Accepted` 或 `Accepted with Changes` 的规格，以及这些规格的来源台账；不修改 Android、Kotlin、Gradle 或资源文件。

## 结论口径

- `Accepted`：问题及主要修订方向成立。
- `Accepted with Changes`：根因成立，但审计中的证据位置、范围或建议方案需要修正。
- `Rejected`：当前规格或架构文档不支持该问题。
- `Deferred`：问题成立，但本阶段不修改。
- `Needs Product Decision`：必须先由项目所有者决定，当前不修改规格。
- `Duplicate`：根因和修改范围已由另一 Issue 覆盖。

只有 `Accepted` 和 `Accepted with Changes` 项进入本轮规格修订。

## Issue 处置表

| Issue ID | DeepSeek 严重程度 | Codex 处理结论 | 实际证据 | 最终优先级 / 阶段 | 本轮修改 | 独立验收要求 | 许可证风险 |
|---|---|---|---|---|---|---|---|
| I-LS-001 | P1 | Rejected | 当前 `health-connect.md` 的关联功能只有体重、PK、服药记录、隐私和云备份；T22-T23 是“立即同步”按钮测试，不是血检或日记恢复。审计证据与当前文件不符。 | 无 | 无 | 无新增 | 否 |
| I-LS-002 | P2 | Accepted with Changes | `health-connect.md` 明确写入并覆盖手动体重；ADR-004 与 Migration Phase 5 要求外部体重有来源且不覆盖本地事实。修订为来源明确的外部 observation，只有用户主动采用才改变本地体重。 | P2 / Phase 5 | `health-connect.md` | 来源、时间、重复读取、用户采用、PK 输入不静默变化 | 否 |
| I-LS-003 | P2 | Rejected | `.hrtbackup`、Argon2id 和 AES-256-GCM 均不在当前 `health-connect.md`；相关内容实际位于 `cloud-sync.md`，由 I-LS-009 处置。 | 无 | 无 | 无新增 | 是，但不在所指文件 |
| I-LS-004 | P2 | Accepted | 当前 FHIR 表包含字段路径、展示连接符、具体单位编码等实现级映射。行为规格只需资源类型、稳定标识、药物、剂量、时间、状态和来源语义。 | P2 / Phase 5 | `health-connect.md` | 稳定标识、幂等写入、时间/剂量语义、缺失数据 | 是 |
| I-LS-005 | P3 | Rejected | 当前 `health-connect.md` 只规定 kg 单值，没有“用户输入时的原始数值和单位”；审计引用的描述实际出现在 `cloud-sync.md`。 | 无 | 无 | 无新增 | 否 |
| I-LS-006 | P3 | Accepted with Changes | 现有 T02 只覆盖不可用设备；Migration Phase 5 要求至少两个目标 Android/Provider 组合。不能把“无 GMS”直接等同于不可用，应以 provider 能力探测结果验收。 | P2 / Phase 5 | `health-connect.md` | 两个目标 Android/Provider 组合、provider 不可用降级 | 否 |
| I-LS-007 | P0 | Accepted with Changes | `cloud-sync.md` 把 Google Drive 写成确定方案；ADR-009、ROADMAP 和 Migration Phase 7 只允许在本地加密备份成熟后进行 provider 实验。保留为 P3 候选行为参考，不作为当前实现任务。 | P3 / Phase 7 | `cloud-sync.md` | Phase 7 进入条件、provider 可替换性、未启用时不影响本地功能 | 是 |
| I-LS-008 | P1 | Accepted | 云备份范围包含当前不存在的血检、日记、库存和 Tracked Date。备份格式必须从届时已实现且有来源记录的数据模型推导，不能为不存在功能预建兼容负担。 | P3 / Phase 7 | `cloud-sync.md` | 当前支持类别往返、未知类别拒绝/忽略、版本兼容 | 是 |
| I-LS-009 | P1 | Accepted | `.hrtbackup` 和具体算法组合不是用户可观察行为，且可能携带旧项目命名和安全架构。改为 Evolune 独立定义的密码保护备份格式。 | P2 前置 / Phase 7 | `cloud-sync.md` | 错误密码、篡改、损坏、版本兼容、安全评审 | 是 |
| I-LS-010 | P2 | Accepted | “Keystore 包装密码”的保存方式是实现选择。规格仅保留使用平台安全存储、正常使用期间无需重复输入的行为要求。 | P3 / Phase 7 | `cloud-sync.md` | 重启、登出、撤权、设备迁移后凭据不可复用 | 是 |
| I-LS-011 | P3 | Accepted with Changes | 128 MiB/256 KiB 没有当前项目证据。容量上限是必要防护，但数值应由 Evolune 在目标 provider 和数据规模测试后确定，不需要产品所有者先决定常量。 | P3 / Phase 7 | `cloud-sync.md` | 边界容量、超限提示、拒绝部分恢复 | 是 |
| I-LS-012 | P1 | Accepted | `phone-widgets.md` 把追踪日期小组件写成已存在的 Anchor 功能；ADR-011 和 ROADMAP 明确它是 P2、非 MVP、不得进入 Phase 1。 | P2 / 产品确认后、Phase 6 或更晚 | `phone-widgets.md` | 服药小组件不依赖 Tracked Date；独立启用 | 是 |
| I-LS-013 | P2 | Accepted | `phone-widgets.md` 直接列出 camelCase 快照字段和 UUID 组合，容易被误当成 Evolune 数据类。改成用户可观察信息及稳定业务标识语义。 | P2 / Phase 6 | `phone-widgets.md` | 快照派生、隐私隐藏、稳定动作目标、schema 失配 | 是 |
| I-LS-014 | P2 | Accepted with Changes | 精确 dp/sp、画布和 HCT 实现并非全部不可记录，但不应成为逐像素复制要求。保留可读性、层级、尺寸适配和品牌独立性，具体设计由 Evolune 设计系统确定。 | P2 / Phase 6 | `phone-widgets.md` | 多尺寸、字体缩放、深浅色、OEM Launcher、独立视觉审查 | 是 |
| I-LS-015 | P3 | Rejected | 规格描述目标数据流，ARCHITECTURE 也要求 Widget 消费独立 snapshot；当前代码尚未持久化投影不构成规格错误。 | 无 | 无 | 后续 snapshot builder 测试 | 否 |
| I-LS-016 | P1 | Accepted | `tracked-date-widget.md` 全文使用旧内部名称且缺少优先级。ADR-011 明确 P2（1.0、待产品确认、非 MVP），Phase 1 不创建实体或迁移。 | P2 / 非 MVP、不得进入 Phase 1 | `tracked-date-widget.md` | 仅在产品确认后启用全部验收测试 | 是 |
| I-LS-017 | P2 | Accepted | 彩旗类别可以作为行为需求，但颜色、渐变、形状和资源必须由 Evolune 独立创作。 | P2 / Tracked Date 获确认后 | `tracked-date-widget.md` | 原创资产来源记录、主题/对比度、多尺寸预览 | 是 |
| I-LS-018 | P2 | Accepted with Changes | 当前规格把完整外观参数和全局共享范围写死，而 Evolune 设计系统尚未定义该契约。保留可配置外观目标，不规定键名、存储结构或跨类型共享范围。 | P2 / Phase 6 或更晚 | `tracked-date-widget.md` | 实例隔离、设置范围、升级默认值、取消不覆盖 | 是 |
| I-LS-019 | P1 | Accepted with Changes | 完整 Wear App 属于 ROADMAP 1.0 和 Migration Phase 4；当前只有基础 Tile。前置条件应是版本化协议和 wear bridge，不是 Google 账号或手机 Widget 快照。 | P1（1.0） / Phase 4 | `wear-os-app.md` | 配对、断连、重连、协议兼容、命令幂等、缓存恢复 | 是 |
| I-LS-020 | P2 | Accepted | 25 点、2 小时间隔是实现采样策略；用户需求是约 48 小时且在手表上可辨识的趋势。 | P2 / Phase 4 | `wear-os-app.md` | 覆盖时长、有限值、曲线可读性、不同密度等价 | 是 |
| I-LS-021 | P2 | Rejected | 当前 `wear-os-app.md` 只写“本地加密缓存”，未指定 AES-256-GCM 或 Keystore 算法。精确算法实际出现在 `wear-protocol.md`，由 I-LS-025 修正。 | 无 | 无 | 无新增 | 是，但证据位置错误 |
| I-LS-022 | P2 | Accepted | `wear-protocol.md` 写死约 64 行、97 点和 256 KB。协议需要上限，但实际值应由 Evolune 的 codec 与真实设备容量测试确定。 | P1 / Phase 4 | `wear-protocol.md` | 编解码边界、目标设备 payload 容量、超限拒绝、降级快照 | 是 |
| I-LS-023 | P2 | Rejected | 唯一请求 ID 和幂等处理是通用分布式协议要求；规格没有 UUID 生成算法、类名或内部去重存储结构。审计本身也建议无需修改。 | 无 | 无 | 重复命令幂等测试保留 | 否 |
| I-LS-024 | P1 | Duplicate | 与 I-LS-008、I-LS-012、I-LS-016 同一根因。当前 ADR-011 已给出统一临时结论：P2、待产品确认、非 MVP、不得进入 Phase 1。 | 随上述 Issue | 无单独修改 | 文档搜索不得出现旧名称或 MVP/Phase 1 承诺 | 是 |
| I-LS-025 | P2 | Accepted with Changes | 根因成立，但审计位置不准确：具体算法存在于 `cloud-sync.md` 和 `wear-protocol.md`，不在当前 `health-connect.md` 或 `wear-os-app.md`。统一改为由 Evolune 安全策略独立选择。 | P2 / Phase 4 与 Phase 7 安全评审 | `cloud-sync.md`、`wear-protocol.md` | 静态数据保护、密钥丢失、篡改、重启、迁移隔离 | 是 |
| I-LS-026 | P2 | Duplicate | 当前不存在的数据类别只出现在 `cloud-sync.md`；`health-connect.md` 的 T22-T23 与审计描述不符。云备份范围由 I-LS-008 修订。 | 随 I-LS-008 | 无单独修改 | 随 I-LS-008 | 是 |

## 数量汇总

| 结论 | 数量 |
|---|---:|
| Accepted | 10 |
| Accepted with Changes | 8 |
| Rejected | 6 |
| Deferred | 0 |
| Needs Product Decision | 0 |
| Duplicate | 2 |
| **总计** | **26** |

## 本轮实施边界

- Tracked Date 继续是 `P2（1.0，待产品确认，非 MVP）`，不进入 MVP 或 Phase 1。
- Wear 设备传输、用户本地备份和云 provider 保持三个独立边界。
- Legacy 规格只保存行为、边界和独立验收目标，不定义 Evolune 类名、package、Room schema、加密算法或旧项目视觉资源。
- 本轮不修改 `app/`、`wear/`、Gradle、Manifest 或任何业务实现。
