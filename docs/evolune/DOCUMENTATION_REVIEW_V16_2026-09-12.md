# 截至 v1.6.0 的文档盘点与更新

盘点日期：2026-09-12；整理完成：2026-09-13。基线：main `c7f3d266357af08b737aa4fd4015f1b1391279c3`；v1.6.0 标签源码：`58ab66fc22b93630de4ea7137651b2388ff5f1a2`。

## 结论与方法

v1.6.0 已于 2026-09-10 08:35:14 UTC 公开发布，当前文档应描述正式版。早期“等待验收”“五款 Widget”“v1.2 未实施”等记录必须按其历史阶段阅读。此次更新同步现行说明、增加版本阅读入口，并保留原始验收记录及缺失证据说明。

本次对基线中全部 157 份 Markdown/TXT/RST 文档建立全文索引、扫描版本与状态表述及相对文件链接；重点交叉核对现行说明、版本计划、最终门禁、Release/标签和对应源码。另检查 LICENSE、NOTICE 的权利范围，未改动许可声明。全量索引不等于逐条重新验证历史测试、科学模型或设备行为。本次没有运行 App 测试、连接设备或重新校验下载的 APK。

新增 5 份文档，更新 22 份已有文档；全部路径见[文档索引](DOCUMENTATION_INDEX.md)。

## 公开版本回顾

日期取 GitHub Release 的 UTC 发布日期，不能用历史 RC 文档的结论代替后来发布事实。

| 版本 | 发布日期 | 截至该版本的主要记录 |
| --- | --- | --- |
| [v1.0.0](https://github.com/YingQiu0871/Evolune/releases/tag/v1.0.0) | 2026-08-15 | 首个公开版本 |
| [v1.1.0](https://github.com/YingQiu0871/Evolune/releases/tag/v1.1.0) | 2026-08-22 | 身份迁移及稳定化 |
| [v1.2.0](https://github.com/YingQiu0871/Evolune/releases/tag/v1.2.0) | 2026-08-28 | Health Connect 体重读取、原生加密备份、手动 Google Drive 备份 |
| [v1.2.2](https://github.com/YingQiu0871/Evolune/releases/tag/v1.2.2) | 2026-08-30 | 匹配与稳定性修复；无独立公开 v1.2.1 标签 |
| [v1.3.0](https://github.com/YingQiu0871/Evolune/releases/tag/v1.3.0) | 2026-09-01 | Wear App；该版 Wear 安装存在 taskAffinity 缺陷 |
| [v1.3.1](https://github.com/YingQiu0871/Evolune/releases/tag/v1.3.1) | 2026-09-02 | 修复 Wear 安装及覆盖升级 |
| [v1.4.0](https://github.com/YingQiu0871/Evolune/releases/tag/v1.4.0) | 2026-09-03 | 引导与教程 |
| [v1.5.0](https://github.com/YingQiu0871/Evolune/releases/tag/v1.5.0) | 2026-09-05 | 体验整合与稳定性；能耗验收存在 owner 豁免 |
| [v1.6.0](https://github.com/YingQiu0871/Evolune/releases/tag/v1.6.0) | 2026-09-10 | 四个 Phone Widget、三个新 Tile、三个 Complication、外观和跳过动作 |

v1.2 的 RC 文档保留当时未放行状态；新增[v1.2 阅读说明](v1.2/README.md)解释其时序。v1.3.0 与 v1.3.1 的安装差异见[v1.3 阅读说明](v1.3/README.md)。v1.5 的 `SKIPPED_BY_OWNER` 不能转述成能耗测试通过或续航提升。

## 统一后的现行事实

| 范围 | v1.6.0 的文档口径 | 核对依据 |
| --- | --- | --- |
| Phone Widget | 四个独立 provider：今日计划、下一次服药、当前 E2、E2 趋势；今日完成度并入今日计划 | [最终规格说明](v1.6/V16_SPEC.md)、[Widget 实现](../../app/src/main/java/io/github/yingqiu0871/evolune/widget/EvoluneWidgetReceiver.kt) |
| Widget 动作 | 今日计划符合 AVAILABLE 条件的项目提供确认；下一次服药仅展示并打开 App，没有直接确认按钮 | 同上；早期下一次服药确认要求保留为历史差异 |
| Widget 配置 | 每实例外观配置；应用成功才保存，取消不写入；四个系统入口决定样式 | [快速指南](../../QUICK_START_GUIDE.md) |
| Wear Tile | 三个新 Tile，加保留旧身份的 DoseTileService，共四个服务；预览元数据及品牌图标已补齐 | [Wear Manifest](../../wear/src/main/AndroidManifest.xml)、[最终门禁](v1.6/V16_FINAL_RELEASE_GATE_2026-09-09.md) |
| Complication | 三个 SHORT_TEXT provider；点击打开 App | [v1.6 阅读入口](v1.6/README.md) |
| Wear 外观 | Wear 本地选择系统动态色或八组预制色；不描述成从 Phone 同步外观偏好 | [设置说明](../../SETTINGS_FEATURE.md) |
| 权威与同步 | app、wear、experience-core 三模块；Phone Room 为用药数据权威，Wear 为可重建缓存；v1 快照协议与兼容旧路径分开描述 | [架构](ARCHITECTURE.md) |
| 跳过本次 | 按计划、slot、scheduledAt 保存跳过并抑制该次提醒；不写 DoseEvent，不算已完成 | [用药方案](../../MEDICATION_PLAN_FEATURE.md) |
| 备份与体重 | Health Connect 只读体重；原生加密备份与 Mahiro JSON 分开；Google Drive 为前台手动 appDataFolder 流程 | [设置说明](../../SETTINGS_FEATURE.md)、[v1.2 说明](v1.2/README.md) |
| PK 与存储 | v1.6 更改呈现与入口，未改变 PK 数学模型、Room schema 或备份格式 | [实现总结](../../IMPLEMENTATION_SUMMARY.md)、[发行说明](v1.6/V16_RELEASE_NOTES.md) |

## 对最初设备问题的回顾

早期手机只有一个入口的情况与旧单 provider 设计、配置页可被跳过有关。最终源码已拆分四个 provider。旧 Widget 身份保留用于升级兼容，不能据此认定仍只有一款。Wear 早期新 Tile 缺少预览元数据、服务沿用旧加号图标；最终 Manifest 已补预览并更换品牌资源。系统“添加卡片”的黑色加号占位也不能直接等同于 Tile 运行时渲染失败。

以上是源码与历史证据中的修复闭环，不是本次重新检查用户设备后的结论。判断某台设备仍有问题，应先核对安装版本、签名、APK 哈希与系统添加入口，再分别检查预览、发现、运行渲染和旧实例兼容，避免直接卸载清数据。

## 发布身份与验收证据边界

[公开 Release](https://github.com/YingQiu0871/Evolune/releases/tag/v1.6.0) 与[最终门禁](v1.6/V16_FINAL_RELEASE_GATE_2026-09-09.md)对应以下身份。哈希是公开元数据/已有记录的核对值，本次未重新下载计算。

| 项目 | 值 |
| --- | --- |
| Application ID（Phone / Wear） | `io.github.yingqiu0871.evolune` |
| Phone versionCode | `101060000` |
| Wear versionCode | `1101060000` |
| Phone APK SHA-256 | `e964fc8a89711b4f0f68b9bc93eb185bf77189e41cc0145f53e77dcd94a76665` |
| Wear APK SHA-256 | `b344c07ccb67dfedcce294ec8a655f6229b65d6c36a158e78461c25dbb8d5ba4` |

最终门禁记录 863 项 JVM 测试、0 失败/错误/跳过，以及 Release 152 tasks、debug 161 tasks、Phone receiver 7/7。863 为报告记载，公开日志不能独立重算所有测试 XML。物理覆盖升级记录涵盖 Pixel 11 与 Galaxy Watch、哈希/版本、首次安装时间及旧实例保留。最终四种 Widget 的新增流程证据来自 Pixel Fold 模拟器，没有重新完成所有真实 Pixel 新增流程；owner 接受了这一证据组合。真实手表的 owner 手动验收记录也不等于每一状态均有独立公开取证。

历史候选包曾以 dirty main `df12329` 构建；本次补充后来的标签与发布身份，未把历史构建记录改写成当时已有干净提交。公开树保存的四份原始日志及用途见[v1.6 阅读入口](v1.6/README.md)。

## 尚需明确的文档与证据问题

1. **下一次服药 Widget 的动作范围变更**：早期规格要求 AVAILABLE 直接确认，最终实现为只读并打开 App。现行文档已按实现修正，同时明确差异；未找到单独批准这一范围缩减的记录，应由维护者补充决策依据，不能虚构批准。
2. **历史取证文件不完整**：相对 Markdown 文件链接检查发现 65 处引用指向 26 个公开树中不存在的文件，分布在 9 份 v1.6 文档。下表保留准确文件名与引用来源；不删除历史链接来掩盖缺失，不补造日志。文件是否仍在 owner 本地未知。
3. **实机扩展验证**：真实 AlarmManager 队列、并发 skip 压力等 P3 仍非已完成测试。未来若补测，应单列设备、构建身份、日期与日志，不能覆盖原验收记录。

这些问题是发布后文档补全项与证据限制；不自行撤销已发生的发布，也不自动宣称全部设备路径已复验。

### 公开树中缺失的历史链接目标

| 缺失文件 | 引用文档 |
| --- | --- |
| `A-00-worktree-2026-09-05.log` | [V16_A_EVIDENCE.md](v1.6/V16_A_EVIDENCE.md) |
| `A-03-gradle-2026-09-05-rerun.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_A_EVIDENCE.md](v1.6/V16_A_EVIDENCE.md)、[V16_A_REVIEW_2026-09-05.md](v1.6/V16_A_REVIEW_2026-09-05.md) |
| `A-03-gradle-2026-09-05.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_A_EVIDENCE.md](v1.6/V16_A_EVIDENCE.md)、[V16_A_REVIEW_2026-09-05.md](v1.6/V16_A_REVIEW_2026-09-05.md) |
| `B-01-gradle-2026-09-05.log` | [V16_B_EVIDENCE.md](v1.6/V16_B_EVIDENCE.md) |
| `B-02-android-2026-09-05.log` | [V16_B_EVIDENCE.md](v1.6/V16_B_EVIDENCE.md) |
| `B-03-gradle-2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_B_EVIDENCE.md](v1.6/V16_B_EVIDENCE.md)、[V16_B_REVIEW_2026-09-06.md](v1.6/V16_B_REVIEW_2026-09-06.md) |
| `B-04-android-2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_B_EVIDENCE.md](v1.6/V16_B_EVIDENCE.md)、[V16_B_REVIEW_2026-09-06.md](v1.6/V16_B_REVIEW_2026-09-06.md) |
| `B-05-wear-contract-2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_B_EVIDENCE.md](v1.6/V16_B_EVIDENCE.md)、[V16_B_REVIEW_2026-09-06.md](v1.6/V16_B_REVIEW_2026-09-06.md)、[V16_B_WEAR_MATRIX.md](v1.6/V16_B_WEAR_MATRIX.md) |
| `B-08-wear-summary-2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_B_EVIDENCE.md](v1.6/V16_B_EVIDENCE.md)、[V16_B_REVIEW_2026-09-06.md](v1.6/V16_B_REVIEW_2026-09-06.md)、[V16_B_WEAR_MATRIX.md](v1.6/V16_B_WEAR_MATRIX.md) |
| `V16_A_G_PIXEL7_PERF_2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_A_EVIDENCE.md](v1.6/V16_A_EVIDENCE.md)、[V16_G_EVIDENCE.md](v1.6/V16_G_EVIDENCE.md) |
| `V16_C_D_E_F_ANDROID_PIXEL7_2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_C_D_E_F_EVIDENCE.md](v1.6/V16_C_D_E_F_EVIDENCE.md) |
| `V16_C_D_E_F_ASSEMBLE_2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_C_D_E_F_EVIDENCE.md](v1.6/V16_C_D_E_F_EVIDENCE.md) |
| `V16_C_D_E_F_JVM_2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_C_D_E_F_EVIDENCE.md](v1.6/V16_C_D_E_F_EVIDENCE.md) |
| `V16_EMU5558_DEBUG_WIDGET_CHART_RUNTIME_DEBUG_ONLY_2026-09-07.png` | [V16_C_D_E_F_EVIDENCE.md](v1.6/V16_C_D_E_F_EVIDENCE.md) |
| `V16_EMU5558_DEBUG_WIDGET_DUMPSYS_DEBUG_ONLY_2026-09-07.txt` | [V16_C_D_E_F_EVIDENCE.md](v1.6/V16_C_D_E_F_EVIDENCE.md) |
| `V16_G_PIXEL7_MIGRATION_2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_G_EVIDENCE.md](v1.6/V16_G_EVIDENCE.md) |
| `V16_G_PIXEL7_STABLE_COLD_LAUNCH_2026-09-06.log` | [V16_G_EVIDENCE.md](v1.6/V16_G_EVIDENCE.md) |
| `V16_G_REAL_PIXEL11_2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_G_EVIDENCE.md](v1.6/V16_G_EVIDENCE.md) |
| `V16_G_RELEASE_ASSEMBLE_2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_G_EVIDENCE.md](v1.6/V16_G_EVIDENCE.md) |
| `V16_G_UPGRADE_PIXEL7_WEAR_2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_G_EVIDENCE.md](v1.6/V16_G_EVIDENCE.md) |
| `V16_RELEASE_BUILD_1.6.0_2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_G_EVIDENCE.md](v1.6/V16_G_EVIDENCE.md) |
| `V16_VERSION_BUMP_2026-09-06.log` | [V16_ACCEPTANCE.md](v1.6/V16_ACCEPTANCE.md)、[V16_G_EVIDENCE.md](v1.6/V16_G_EVIDENCE.md) |
| `V16_WEAR_PHONE_PAIRING_FRESH_2026-09-07.log` | [V16_C_D_E_F_EVIDENCE.md](v1.6/V16_C_D_E_F_EVIDENCE.md)、[V16_WEAR_MONET_APP_TILE_POWER_REVIEW_2026-09-07.md](v1.6/V16_WEAR_MONET_APP_TILE_POWER_REVIEW_2026-09-07.md) |
| `V16_WIDGET_PEAK_HEADROOM_CONNECTED_DEBUG_TEST_2026-09-07.log` | [V16_C_D_E_F_EVIDENCE.md](v1.6/V16_C_D_E_F_EVIDENCE.md) |
| `V16_WIDGET_PEAK_HEADROOM_FINAL_BUILD_2026-09-07.log` | [V16_C_D_E_F_EVIDENCE.md](v1.6/V16_C_D_E_F_EVIDENCE.md) |
| `V16_WIDGET_RELEASE_BUILD_FRESH_2026-09-07.log` | [V16_C_D_E_F_EVIDENCE.md](v1.6/V16_C_D_E_F_EVIDENCE.md) |

检查范围是内联 Markdown 相对链接的文件是否存在，不包括所有行内代码形式的证据名、外部 HTTP 可用性或文档内部锚点，因此不能将 26 视作全部历史证据缺失数量。

## 文档维护方式

当前使用顺序：[当前状态](CURRENT_STATUS.md) → [功能矩阵](FEATURE_MATRIX.md) → [架构](ARCHITECTURE.md) → [v1.6 最终门禁](v1.6/V16_FINAL_RELEASE_GATE_2026-09-09.md)。历史 plans、reviews、phase reports 保留原文与时间语境；需要更正时添加明确的后续说明，不倒填测试或审批。新增版本时同步现行文档、版本入口和 Release 身份，并明确已实现、已验证、owner 豁免与待办。

