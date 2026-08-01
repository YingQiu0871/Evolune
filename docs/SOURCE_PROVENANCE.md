# Evolune 来源追踪模板

**状态**：Phase 0 初始记录，仍待项目所有者逐项确认

**用途**：记录进入 Evolune 构建、发布包、仓库文档或品牌资产的来源、许可证和处理决定。

**限制**：本文是工程来源台账，不是法律意见。无法确认的内容必须保持 `Pending`，不得用推测补全。

## 状态定义

- `Confirmed`：已有可复核的仓库、提交、许可证文件或书面授权证据。
- `Partial`：只能确认项目级来源，尚未完成逐文件血缘核验。
- `Pending`：需要项目所有者、原作者或历史提交进一步确认。
- `Reference only`：只用于理解产品行为、公开 API 或测试目标，不复制代码、测试、图片、XML 或专属资源。
- `Not used`：已核验没有进入 Evolune 构建或分发。

## 当前来源台账

| 资产或范围 | 声称/候选来源 | 当前实际证据 | 许可证 | 使用状态 | 必需动作 | 复核人/日期 |
|---|---|---|---|---|---|---|
| Evolune 当前生产源码 | Evolune 历史提交与当前维护者 | 根 `LICENSE` 为 MIT，版权行为 `Copyright (c) 2026 Yitong Dang`；尚未完成逐文件历史核验 | MIT（项目声明） | Partial | 项目所有者确认历史贡献和现有文件是否均受该声明覆盖 | Pending |
| `upstream/master` HRTTracker | `NaiveTomcat/HRTTracker` | 已核对的上游 `LICENSE` 为 MIT；当前 Evolune 各文件是否复制或演进自上游尚无逐文件记录 | MIT（上游仓库证据） | Partial | 对继承文件使用 Git history/blame 建立清单；确认需要保留的版权通知 | Pending |
| PK 参考实现与参数 | `LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test` | `docs/evolune/README.md` 和历史文档声明为参考；`VD_PER_KG` 等参数缺少逐项来源证据 | Pending | Pending | 核实仓库许可证、引用范围、参数文献和是否存在代码复制 | Pending |
| 产品灵感 | `SmirnovaOyama/Oyama-s-HRT-Tracker` | README 仅声明“灵感来源”，未形成代码/资源使用清单 | Pending | Pending | 确认仅为产品参考还是存在实际复用；按结果更新 NOTICE/README | Pending |
| `feiwuliyong/02-source-snapshots`、`03-patches`、专属资源 | `mkx173/Featherline` 迁移资料 | `feiwuliyong/06-licenses/SOURCE-AND-LICENSE-NOTICE.md` 明确标记源码快照和补丁为 GPLv3 衍生成果 | GPL-3.0 | Reference only | 保持在资料区，不加入 Evolune 构建；独立实现时只使用行为和验收目标 | Pending |
| `branding/` 与 launcher 图标 | 当前 Evolune 品牌资产 | 工作树中存在品牌文件和新图标，但未见统一的作者、生成工具、原始提示或授权记录 | Pending | Pending | 记录每个最终发布资产的作者/工具、创建日期、源文件和授权 | Pending |
| Gradle 第三方依赖 | AndroidX、Kotlin、Room、Compose、Wearable 等 | 版本定义存在于 `gradle/libs.versions.toml`；尚未生成发布依赖许可证清单 | 各依赖许可证 | Partial | 发布前生成依赖清单并复核打包 NOTICE 要求 | Pending |

## Phase 0 已确认的项目边界

- Android namespace/applicationId 保持 `io.github.yuninggu.evolune` 和 `io.github.yuninggu.evolune.wear`。
- 手机与 Wear 的 Android Auto Backup、云备份和设备迁移规则均排除应用私有数据；跨设备迁移使用用户主动 JSON 导出/导入。
- 根 `LICENSE`、根 `NOTICE` 和本文件只描述可复核的项目边界，不把候选来源或致谢自动认定为代码血缘。
- `feiwuliyong/` 保持为参考资料区，不进入 Gradle 构建，不执行其中 patch。

## 新增来源记录模板

每次引入外部代码、文档、图片、字体、图标、数据、算法参数或测试向量时，复制下表并填写；没有证据时不得标记为 `Confirmed`。

| 字段 | 内容 |
|---|---|
| Evolune 目标路径 | `path/to/file` |
| 来源项目/作者 | Pending |
| 来源 URL/仓库 | Pending |
| 来源提交或版本 | Pending |
| 原始文件路径 | Pending |
| 引入方式 | 原创 / 修改 / 复制 / 生成 / 仅参考行为 |
| 许可证/SPDX | Pending |
| 版权通知要求 | Pending |
| 修改摘要 | Pending |
| 验证证据 | commit、hash、书面授权或工具记录 |
| 审阅结论 | Confirmed / Partial / Pending / Reference only / Not used |
| 审阅人和日期 | Pending |

## 合入门槛

1. `Pending` 的外部源码、补丁或专属资源不得进入 Evolune 构建和发布包。
2. `Reference only` 只能转化为独立需求、接口约束和测试目标，不得保留原实现的表达、结构或资源。
3. 复制或修改 MIT/BSD/Apache 等许可内容时，按原许可证保留所需版权和 NOTICE；项目根 MIT 文件不能替代第三方通知。
4. GPLv3 内容若要进入 Evolune，必须先由项目所有者明确改变许可策略或取得兼容的单独授权，并更新所有发布文档。
5. 发布前对本表、依赖清单、仓库跟踪文件和最终 APK/AAB 内容做一次交叉检查。

## 待项目所有者确认

- 当前 Evolune 生产源码与原 HRTTracker 的逐文件关系。
- PK 参数、公式、测试向量和文案的原始来源。
- `branding/`、launcher 图标和预览资产的作者与可分发权利。
- 是否需要根级 `NOTICE`；应在上述事实确认后生成，而不是先写未经证实的归属。
