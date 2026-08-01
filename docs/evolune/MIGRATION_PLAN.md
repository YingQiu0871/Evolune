# 迁移计划

## 1. 迁移边界

### 可以直接保留的 Evolune 代码

- `app/src/main/java/io/github/yuninggu/evolune/pk/` 下的 PK 模型和已有测试，前提是继续确认其原创来源与 MIT 许可覆盖范围。
- `data/` 下 Room 基础实现、Repository 和 DataStore 的现有行为，作为重构起点而不是最终架构。
- `reminder/` 的现有 Android 闹钟和通知入口。
- Compose UI、导航、主题、现有品牌资源和当前 MIT 许可文件。
- `utils/MahiroJsonFormat.kt` 的当前导入导出协议，后续通过版本 DTO 扩展。

### 需要重构的 Evolune 代码

- `DoseEvent.timeH`：从唯一时间字段演进为明确的 `occurredAt`、时区和计划槽位语义；PK 计算通过 adapter 使用小时值。
- `AppDatabase`：拆出数据库边界、导出 schema、增加迁移测试，避免 `exportSchema=false` 长期存在。
- `MainActivity` 和 `AppNavigation`：逐步减少手工创建 Repository/ViewModel 和跨层逻辑。
- `WearDataLayer.kt`：从字符串 JSON/DataMap 改为独立的版本化协议模块。
- `EvoluneWidgetReceiver.kt`：先抽出只读 snapshot，再决定是否用 Glance 替换 RemoteViews。
- 数据库备份规则和 Manifest：明确 Android Auto Backup 是否允许、排除哪些敏感文件。

### 仅参考行为后独立实现

- Featherline 的 Health Connect 权限、体重读取和 MedicationStatement 写入流程。
- Featherline 的 Wear snapshot、快速记录、跳过、撤销、离线缓存和重试行为。
- Featherline 的 Glance 尺寸适配、配置和预览行为。
- Featherline 的 Tracked Date 选择、空状态创建和单日期自动选择行为。
- Featherline 的加密备份、冲突预览和 Google Drive provider 抽象。

### 不建议迁移

- 直接应用 `feiwuliyong/03-patches` 中的 patch。
- 直接复制 `com.mkx.hrttracker` 包下源码或将其批量改名为 `io.github.yuninggu.evolune`。
- 直接复制 Featherline 的完整 Hilt/SQLCipher/Drive/Glance 技术栈。
- 在没有本地加密备份和冲突策略前实现实时云同步。
- 把 Health Connect 记录作为 Evolune 核心事实来源。

### 需要人工确认的许可证内容

1. 任务说明称原 HRTTracker 使用 GPLv3，但直接检查的 `upstream/master` `LICENSE` 是 MIT，GitHub API 也返回 MIT。
2. `feiwuliyong/06-licenses/SOURCE-AND-LICENSE-NOTICE.md` 明确说明快照来自 GPLv3 的 `mkx173/Featherline`。
3. 当前 Evolune 的 MIT `LICENSE` 和历史贡献来源是否覆盖所有现有文件，需要项目所有者确认。
4. 迁移包中的图片、图标、XML 和第三方库声明不能因为属于“资源”就自动视为 MIT。

在上述问题确认前，Evolune 只使用迁移包的产品行为、接口思想和一般架构概念，不复制其中源代码、补丁或专属资源。

`feiwuliyong/03-patches` 和 `02-source-snapshots` 只能用于阅读、定位行为和编写独立验收标准。不得在 Evolune 工作树执行 `git apply`，不得通过改包名、改版权头或机械重写规避许可证边界。

## 2. 包名策略

当前 Evolune 已使用：

- Android namespace/applicationId：`io.github.yuninggu.evolune`
- Wear namespace/applicationId：`io.github.yuninggu.evolune.wear`
- 原 HRTTracker：`cn.naivetomcat.hrt_tracker`
- Featherline 快照：`com.mkx.hrttracker`

建议保留 Evolune 当前包名，不再引入迁移包包名。这样可以避免应用身份、Room 数据路径、备份键、Intent 组件和 Wear 配对产生额外迁移影响。若未来需要从旧安装包迁移数据，应通过明确的 JSON/备份导入流程完成，而不是尝试兼容旧 applicationId。

## 3. 分阶段计划

### Phase 0：项目清理、命名和许可证确认

- **目标**：确定 Evolune 的独立身份、许可证来源和文档边界。
- **模块**：根项目、`app`、文档。
- **进入条件**：保存当前工作树状态并确认未提交改动的归属；本阶段仅做事实核验、文档和项目政策修订；未把 `feiwuliyong` 源码、补丁或专属资源加入 Evolune 构建。
- **任务**：保留 `io.github.yuninggu.evolune`；建立 `docs/SOURCE_PROVENANCE.md` 来源台账；将手机和 Wear 应用私有数据排除于 Auto Backup/设备迁移；由项目所有者确认来源记录和 Tracked Date 产品优先级；恢复根 README 文档入口；移除无效旧作者赞赏和旧品牌引用；检查文件名大小写。
- **数据迁移**：无。
- **测试**：`git diff --check`、Markdown 链接、许可证扫描、Manifest/备份政策核对；若业务文件没有变化，无需以本轮文档修订触发完整 APK 构建。
- **验收**：README、LICENSE、NOTICE、来源说明不互相矛盾；根文档入口可达；迁移资料不再指导直接应用 GPLv3 patch；无 `com.mkx.hrttracker` 生产包名；Manifest 引用了备份规则且敏感应用私有数据被排除；Wear 设备传输、本地备份和云同步职责分开记录。
- **风险**：来源事实不清会污染后续版权声明。
- **回滚**：只回滚文档变更，不触碰用户数据。
- **退出条件/完成定义**：项目所有者确认许可证和来源台账；备份规则通过 manifest/resource 验证；来源台账中所有拟进入构建的资产不再处于未知状态；I-01、I-02、I-06 无未处置 P0；Repository contract 目标依赖方向已记录；Tracked Date 保持 `P2（1.0，非 MVP）` 或已由所有者更新全部规划文档。

### Phase 1：核心数据模型和数据库

- **目标**：建立可扩展的领域模型和时间语义。
- **模块**：`core:model`、`core:data-api`、`core:database`，暂时可保留在 `app` 内用独立 package 分层。
- **进入条件**：Phase 0 的退出条件全部满足且没有未处置 P0；项目所有者已确认 `feature -> core:data-api <- core:database` 的依赖方向和模块创建时机；Tracked Date 已明确保持非 MVP 或完成产品决策；旧 `timeH` 的舍入、容差、回滚和旧 JSON 不变量已形成书面规则；Room schema 导出目录、基线版本和 migration test 方案已确定。
- **任务**：引入 `ScheduledDoseSlot`、来源、事件状态、revision 和时区值对象；为旧 `timeH` 写迁移 adapter；导出 Room schema。
- **数据迁移**：从 `dose_events.timeH` 生成 `occurredAt`，使用统一舍入规则并保留旧列只读一版；无法恢复的计划槽位保持 null 并标记来源为 legacy；外部 JSON v1 继续按原 epoch 小时语义读取。
- **测试**：Room migration、`timeH` 往返容差、PK 数值回归、时区、DST、旧 JSON、重复事件。
- **验收**：旧数据可读取；新记录不依赖 UI 时间字符串；PK 数值结果无意外变化。
- **风险**：错误转换会影响历史和 PK 曲线。
- **回滚**：保留旧列只读一版，禁止直接删除旧字段。
- **退出条件/完成定义**：schema 已导出并纳入版本控制；迁移测试和真实导入样本全部通过；feature、Wear、Widget 不直接依赖 DAO/Entity；Tracked Date 未经产品确认不会被顺带写入 schema。

### Phase 2：基础手机端功能

- **目标**：让计划、记录、历史、导入导出经过稳定 Use Case。
- **模块**：`feature:medications`、`feature:history`、`feature:backup`。
- **前置**：Phase 1。
- **任务**：把 ViewModel 中的写入逻辑移到 Use Case；统一错误和操作结果；给导出文件增加版本号。
- **数据迁移**：旧 JSON 作为 v1 外部 DTO 读取。
- **测试**：计划生成、记录/删除/撤销、导入预览、错误输入。
- **验收**：UI 不直接调用 DAO；所有核心动作可在 JVM 测试中执行。
- **风险**：短期会有 adapter 重复。
- **回滚**：保留旧 ViewModel 路径，按功能开关切换。
- **完成定义**：主流程不再绕过 Repository/Use Case。

### Phase 3：通知和后台任务

- **目标**：将提醒、Widget 刷新和重排逻辑集中管理。
- **模块**：`core:notifications`、`widget`。
- **前置**：Phase 1/2。
- **任务**：定义 slot 状态、通知动作幂等键、时间区变更重排；评估 WorkManager 只用于非精确后台任务。
- **数据迁移**：旧提醒重新计算，不迁移瞬时 AlarmManager ID。
- **测试**：重启、时区切换、夏令时、通知权限关闭、重复广播。
- **验收**：启停提醒可重复执行，不产生重复 alarm；用户关闭权限时数据仍可记录。
- **风险**：Android OEM 后台限制。
- **回滚**：保留现有 ReminderManager 作为 fallback。
- **完成定义**：目标设备矩阵提醒测试通过。

### Phase 4：Wear 协议及 Wear App

- **目标**：协议版本化并支持离线快速记录。
- **模块**：`core:wear-protocol`、`core:wear-bridge`、`wear`；不使用云同步 `core:sync`。
- **前置**：核心事件具备稳定 ID、slot 和 revision。
- **任务**：定义 envelope、snapshot、command、ack、重试、过期策略；用 `DataClient + MessageClient`；实现 Wear App，再扩展 Tile/Complication。
- **数据迁移**：协议 v0 只读兼容一版；旧 `/hrt/*` 动作转换为新 command 或拒绝并请求全量同步。
- **测试**：纯 JVM 编解码、未知字段/版本、重复命令、断连、重新连接、真实设备。
- **验收**：同一事件无重复写入；离线动作最终可见；旧版本不会静默覆盖新数据。
- **风险**：Wear Data Layer 设备状态和电量。
- **回滚**：保留当前 `/hrt/*` 基础同步路径作为只读 fallback。
- **完成定义**：手机-Wear 配对、断连、重连和撤销演练通过。

### Phase 5：Health Connect

- **目标**：可选外部健康数据集成。
- **模块**：`core:healthconnect`、`feature:settings`。
- **前置**：Phase 1 数据模型稳定；人工确认权限和产品范围。
- **任务**：先实现 WeightRecord 读取；记录外部来源和 last sync token；再单独评估用药写入和 PHR/FHIR。
- **数据迁移**：无强制迁移；已有本地体重仍是默认值，外部读取只产生来源明确的 observation。
- **测试**：权限未授权、撤权、Provider 不可用、单位换算、重复同步、读写能力缺失。
- **验收**：关闭权限不影响核心记录；同步不会覆盖用户本地事实；状态可解释。
- **风险**：Provider 差异、PHR 能力限制和健康数据权限。
- **回滚**：按数据类型关闭同步；保留本地设置和记录。
- **完成定义**：至少两个目标 Android/Provider 组合验证通过。

### Phase 6：Glance 小组件

- **目标**：在不牺牲可靠性的前提下评估 Glance。
- **模块**：`widget`、必要时 `core:designsystem`。
- **前置**：snapshot 接口稳定；Phase 3 完成。
- **任务**：先做只读 medium widget；再做快速记录；实现尺寸响应、配置、刷新和隐私模式。
- **数据迁移**：旧 RemoteViews 配置不直接复用，提供默认配置。
- **测试**：XML/provider、Glance preview、不同尺寸、OEM Launcher、进程被杀恢复。
- **验收**：小组件不直接拼接业务状态；刷新失败保留最后有效快照；交互不重复记录。
- **风险**：OEM 对 Glance 和 RemoteViews 支持差异。
- **回滚**：保留 RemoteViews provider。
- **完成定义**：目标设备矩阵达到可接受刷新和交互成功率。

### Phase 7：备份与云同步

- **目标**：先建立可验证的加密备份，再评估云 provider。
- **模块**：`feature:backup`、`core:sync`（仅云 provider）；不依赖 `core:wear-bridge` 或 Wearable SDK。
- **前置**：数据 schema、backup format、冲突策略和密钥策略确定。
- **任务**：本地加密导出、校验、版本兼容、恢复预览；之后评估 Drive appData、用户可见文件、WebDAV 和自建服务。
- **数据迁移**：备份 envelope 包含 schemaVersion 和 appVersion，保留旧版本只读解析器。
- **测试**：错误密码、篡改、损坏、半恢复、旧版本、多设备冲突、退出登录。
- **验收**：没有密钥时不宣称可恢复；冲突不静默覆盖；云权限撤销后本地可用。
- **风险**：OAuth、密钥丢失、删除同步和隐私合规。
- **回滚**：只发布本地导出恢复，不发布云 provider。
- **完成定义**：恢复演练可重复且有明确用户提示。

### Phase 8：稳定性、测试、发布和文档完善

- **目标**：形成可发布的质量基线。
- **模块**：全仓库。
- **前置**：Phase 0-7 中计划进入版本的部分完成。
- **任务**：完成设备矩阵、性能、耗电、无障碍、翻译、许可证和隐私审查；移除临时代码。
- **数据迁移**：验证从至少一版旧数据库和旧 JSON 恢复。
- **测试**：`test`、instrumentation、真实手机/Wear、release shrink、备份恢复。
- **验收**：无 P0/P1 未知风险；文档与构建产物一致。
- **风险**：过早纳入云同步导致发布阻塞。
- **回滚**：按 feature flag 或版本回退独立能力。
- **完成定义**：可重复构建、可安装、可升级、可卸载和可恢复。

## 4. 迁移策略总结

迁移不是把六个快照模块拼进当前工程，而是按领域边界重建：先稳定本地数据，再稳定配对手机/Wear 的设备协议，最后接入外部健康平台和云端。Wear 设备传输、本地备份与云同步是三个独立边界。任何来自 GPLv3 快照的源码、补丁、专属资源或不可证明来源的实现都必须停在资料包内，直到人工完成许可证确认。
