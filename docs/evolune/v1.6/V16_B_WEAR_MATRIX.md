# v1.6-B Wear 字段缺口与兼容矩阵

日期：2026-09-06
基线：`df12329278eafa488713edf202554cdbd523b8d0`（v1.5.0）
范围：B 阶段协议盘点、兼容设计与可选 `todaySummary` 实现；协议版本仍为 1，候选实现已通过独立复审，
发布仍受真实组合与宿主门控

## 当前 v1 快照的事实来源

Phone 是唯一事实来源。`WearAppSnapshotBuilder` 从 Phone 的 enabled plans、DoseEvent
和 PK 计算派生快照；Wear 只缓存、展示和发送带 occurrence identity 的确认/撤销命令。
当前 `WearAppProtocol.PROTOCOL_VERSION` 为 1，快照包含：

| 类别 | 当前字段 | 语义 |
|---|---|---|
| 快照身份 | `snapshotRevision`、`generatedAt`、`zoneId` | Phone 计算批次、计算时间和本地时区 |
| 生产者身份 | `producerInstanceId`、`producerGeneration` | Phone 重建/换实例后的顺序与旧缓存淘汰 |
| 计划状态 | `overallStatus`、最多 5 个 `upcomingOccurrences` | 仅展示 `UPCOMING`/`DUE`，不把截断列表当今日总数 |
| 最近记录 | `recentDose`、可选 `eventRevision` | 展示最近记录；只有正 revision 才有撤销权威 |
| PK | `concentrationState`、value/unit/calculatedAt | 可用、过期、空或错误；空值不能伪造成 0 |

## B 阶段字段缺口

当前协议不能直接表达 Wear 今日计划/今日完成度所需的完整 Phone 派生汇总：

- `todayLocalDate`：由 Phone 以 `computedAt.atZone(zoneId).toLocalDate()` 计算；Wear 不得使用
  自己的墙钟重算。`zoneId` 必须是这次汇总使用的 IANA 时区。
- `computedAt`：Phone 完成当日 occurrence 派生和事件匹配的时间点；`generatedAt` 是整个 snapshot
  发布/组装的时间点。两者在同一次计算中可以相同；允许分开是为了将来缓存汇总后重新发布，且必须
  满足 `computedAt <= generatedAt`。
- `completedCount`：完整当日 occurrence 集合中状态为 `RECORDED` 的 occurrence 数，不是 event 数。
- `totalCount`：完整当日 occurrence 集合的 occurrence 数，包含 `RECORDED`、`DUE`、`UPCOMING` 和
  `PAST_UNRECORDED`；因此始终满足 `0 <= completedCount <= totalCount`。
- `todaySummaryState`：必须区分 `NO_ENABLED_PLANS`（没有启用计划）、`NO_OCCURRENCES`（有启用计划，
  但该本地日期没有 occurrence）和 `HAS_OCCURRENCES`。前两种状态的计数均为 0，但不能合并为一个
  没有语义的 `0/0` 进度。
- 首个 `todaySummary` 扩展不携带今日明细列表，因此不发布含义不完整的 `truncated`。如果后续增加
  有明确上限的 occurrence detail list，`truncated` 必须与该列表成对定义；它只表示完整集合是否超过
  该列表上限，不能改变 `totalCount`。

扩展使用可选顶层 tag 11 `todaySummary`，保持 v1 的未知字段跳过行为。当前工作树已经实现
该字段的编码、解码和 Phone 派生；`todaySummary` 缺失只表示“汇总不可用”，不得使已有 v1
snapshot 失效，也不得由 Wear 补成 0/0。该实现批次仍需独立审阅和真实组合验证后才可随 Phone
发布。若未来改变确认语义或把字段变成必需字段，应提升协议版本并让未知版本安全拒绝。

## B-08 — 可选 `todaySummary` 实现边界

tag 11 的 payload 只包含 `todayLocalDate`、`computedAt`、`completedCount`、`totalCount` 和
`state`；首个扩展不携带今日明细列表，因此不发布 `truncated`。Phone builder 使用完整的当日
occurrence 集合派生该对象：`computedAt` 与 snapshot 的 `generatedAt` 当前相同，日期由
`computedAt + zoneId` 得出，完成数按 `RECORDED` occurrence 计数。

Phone 仍是唯一事实来源；Wear 只读取可选 summary。旧 v1 decoder 读取 tag 1–10 并跳过 tag 11，
新 decoder 在 tag 11 缺失时保留既有 v1 展示。此批次没有修改确认/撤销路径、legacy `/hrt/*`
通道或协议版本。

## 配对标签与证据边界

下表使用“v1.5/v1.6”描述构建能力，而不是假定每个版本都拥有全新协议。基线 v1.5.0 已经
包含 v1 snapshot、request、Wear store/decoder；v1.6 在 `todaySummary` 扩展审阅前仍只
发布同一组 v1 已知字段。

以下是可执行的**契约预期矩阵**，不是实体 Phone–Wear Data Layer 配对测试。纯 JVM codec、
store 和 presentation fixture 只能证明解码、降级和状态规则；真实旧/新 APK 组合、动作和回执
仍是 B 阶段后续证据。

## 旧/新配对矩阵

| Phone | Wear | 快照/传输结果 | 动作与降级 |
|---|---|---|---|
| v1.5 Phone baseline（已有 v1 snapshot） | v1.5 legacy Tile | `/hrt/plans`、`/hrt/request-plans`、`/hrt/dose-actions` 保持原样 | 旧 Tile 继续使用旧 plan/action 路径 |
| v1.6 Phone（summary 扩展前） | v1.5 legacy Tile | legacy channel 仍发布；v1 `/hrt/v1/wear-app/*` 对旧 Tile 不可见 | 旧 Tile 继续工作；不依赖 `todaySummary` |
| v1.5 Phone baseline（已有 v1 snapshot，无 todaySummary） | v1.6 Wear App | 接受现有 v1 snapshot；`recentDose`、`upcomingOccurrences`、`concentrationState` 继续可用，今日汇总不可用 | 新 Wear 继续只读展示已有 v1 语义；不把缺失汇总补成 0/0 |
| v1.6 Phone（summary 扩展前） | v1.5 Wear App | 旧 Wear 解码现有 v1 已知字段；不存在 summary 也不影响已有功能 | 旧 Wear 保留现有 v1 展示与确认语义 |
| v1.6 Phone（发布可选 tag 11 的候选构建） | v1.5 Wear App | 旧 v1 decoder 跳过未知 `todaySummary`，继续读取已有字段 | 旧 Wear 不消费新汇总，不改变已有动作与回执 |
| v1.6 Phone（summary 扩展前） | v1.6 Wear App | v1 request/snapshot、producer generation 和 revision 生效；summary 缺失即“汇总不可用” | `UPCOMING`/`DUE` confirmation 仍走 Phone 持久化回执；旧 action 不改语义 |
| v1.6 Phone（发布可选 tag 11 的候选构建） | v1.6 Wear App（支持扩展） | 新 Wear 解码 `todaySummary`，按 state 展示计数；旧字段保持 v1 语义 | 仅使用 Phone 汇总；确认/撤销仍需 occurrence identity 和持久化回执；发布前需复审 |
| pre-v1 Phone（不发布 v1 snapshot） | v1.6 Wear App | 无可解码 snapshot，进入 `WAITING_FOR_PHONE` | 不伪造汇总、不写入 Phone；该行不代表 v1.5 baseline |
| 任意 v1 | 未知协议版本 | request/snapshot 安全拒绝；已有缓存不被未知 payload 覆盖 | 显示等待、过期或错误状态，不能默认为零 |
| Phone 重建 | 同一 Wear 的旧缓存 | generation 高于旧 producer 后接受；延迟旧 producer 被丢弃 | 不清空有效 Wear 展示，确认仍需新的 source identity |

legacy `/hrt/*` 与 v1 `/hrt/v1/wear-app/*` 路径是增量并行关系。新 Tile 或 Complication 不得
直接复用旧 `planId` action；确认必须携带 occurrence identity，并等待 Phone 的持久化结果。

## 本检查点的确定性测试

`wear/src/test/.../WearAppCompatibilityMatrixTest.kt` 覆盖：

- v1 request/snapshot 的 Phone↔Wear 编解码；
- 已知 v1 snapshot 在缺少 `todaySummary` 时仍可展示；
- 缺少 snapshot 时保持 `WAITING_FOR_PHONE`，不生成伪造汇总；
- 未知协议版本的 request/snapshot 安全拒绝；缺失 tag 11 的旧 v1 snapshot 仍可读取。

Phone 端真实 legacy/v1 path 边界由 `WearAppLegacyCompatibilityTest` 使用生产常量覆盖；
codec 测试覆盖 tag 11 的 round-trip、缺失 tag 11 的旧 payload 和 summary 约束；这些仍是
JVM codec/presentation fixture，不是实体 Data Layer 配对。

契约检查点执行记录：[B-05-wear-contract-2026-09-06.log](B-05-wear-contract-2026-09-06.log)。
该历史日志当时记录 45 项；B-08 增加约束和 malformed/duplicate tag 11 回归后，当前对应测试集为
`WearAppSnapshotCodecTest` 15 项、`WearAppRequestCodecTest` 11 项、
`WearAppLegacyCompatibilityTest` 1 项、`WearAppCompatibilityMatrixTest` 4 项、
`WearAppFollowUpRegressionTest` 17 项，共 48 项通过。Phone builder 的 15 项回归和全量
Experience Core/Phone/Wear JVM fresh rerun 共 815 项通过，日志见
[B-08-wear-summary-2026-09-06.log](B-08-wear-summary-2026-09-06.log)。

第三次范围收窄的独立只读复审已确认 B-08 候选实现可接受，结论为
`APPROVE B-08 CANDIDATE IMPLEMENTATION`，P0/P1/P2/P3 均无；复审记录见
[V16_B_REVIEW_2026-09-06.md](V16_B_REVIEW_2026-09-06.md)。这份矩阵和 B-08 实现仍不代表 B 阶段关闭，
仍需补旧 Phone/新 Wear、新 Phone/旧 Wear、两端新版的真实 APK/Data Layer 组合与确认/回执测试。
