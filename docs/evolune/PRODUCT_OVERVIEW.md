# 产品概览

## 文档状态

- **当前仓库状态**：已确认
- **目标状态**：建议设计
- **无法从代码确认的内容**：标记为“待确认”或“当前不存在”
- **许可证范围**：只描述产品行为，不复制迁移资料中的 GPLv3 源码

## 产品定位

Evolune 是面向个人长期记录的 Android 与 Wear OS 应用，用于管理激素相关用药方案、记录实际用药事件、安排提醒，并根据本地记录计算药代动力学估算趋势。

它不是医疗器械，不提供诊断、处方或治疗建议。浓度曲线是基于输入数据和模型参数的估算，不等同于实验室检测结果。用户应以实际检查结果和专业医疗意见为准。

## 目标用户

1. 需要长期记录用药计划和实际剂量的个人用户。
2. 需要在手机通知、桌面小组件或手表上快速确认用药的用户。
3. 需要保留本地历史、导入导出数据和查看趋势的用户。

不以医疗机构、医生处方管理、多人共享病历或实时医疗监护为目标。

## 核心使用场景

### 建立方案

用户配置药物名称、酯类、剂量、给药途径、周期和每日时间点。当前实现入口为 `app/src/main/java/io/github/yuninggu/evolune/ui/screens/MedicationPlansScreen.kt`，数据通过 `MedicationPlanViewModel` 和 `MedicationPlanRepository` 写入 Room。

### 记录用药

用户从记录页、通知或桌面小组件添加实际用药事件。事件进入 `DoseEventRepository`，随后驱动历史列表、模型计算和小组件刷新。通知动作由 `app/src/main/java/io/github/yuninggu/evolune/reminder/MedicationNotificationActionReceiver.kt` 处理。

### 查看趋势

首页使用 `HRTViewModel` 汇总记录和方案，调用 `SimulationEngine` 生成估算结果，并由 `ConcentrationChart` 展示。该结果只能作为个人记录和趋势观察工具。

### 手表快速操作

当前 Wear 模块主要提供 Tile。手机通过 `app/src/main/java/io/github/yuninggu/evolune/wear/WearDataLayer.kt` 发送计划快照，手表通过 `wear/src/main/java/io/github/yuninggu/evolune/wear/DoseTileService.kt` 展示并发送动作。

## 当前功能

| 功能 | 状态 | 代码证据 |
|---|---|---|
| 用药计划 | 已确认 | `data/MedicationPlan.kt`、`MedicationPlanDao.kt`、`MedicationPlansScreen.kt` |
| 用药事件 | 已确认 | `pk/DoseEvent.kt`、`DoseEventDao.kt`、`MedicationRecordsScreen.kt` |
| PK 估算 | 已确认 | `pk/SimulationEngine.kt`、`pk/ThreeCompartmentModel.kt` |
| 通知提醒 | 已确认 | `reminder/ReminderManager.kt`、多个 BroadcastReceiver |
| JSON 导入导出 | 已确认 | `utils/MahiroJsonFormat.kt`、`HRTViewModel.kt` |
| RemoteViews 小组件 | 已确认 | `widget/EvoluneWidgetReceiver.kt`、`res/layout/widget_evolune.xml` |
| Wear Tile 和基础设备传输 | 已确认（基础实现；协议未版本化） | `wear/`、`WearDataLayer.kt` |
| Health Connect | 当前不存在 | 未发现依赖、权限、Provider 或同步类 |
| Glance 小组件 | 当前不存在 | Gradle 未声明 Glance，当前使用 RemoteViews |
| WorkManager 后台任务 | 当前不存在 | 未发现依赖或 Worker |
| Google Drive/云同步 | 当前不存在 | 未发现 Drive、OAuth 或同步服务 |
| 正式 Tracked Date 模型 | 当前不存在 | 当前数据模型没有此实体或表 |

## 功能边界

### 计划内

- 更清晰的领域数据模型和时区语义。
- 可测试、可版本化的手机/Wear 协议。
- Health Connect 作为可选外部集成层。
- 可靠的手机小组件和独立 Wear App。
- 用户主动触发的本地导出、加密备份和后期云备份。

### 不计划实现

- 自动诊断、处方生成或治疗建议。
- 依据估算曲线替代实验室检测。
- 未经用户同意上传用药或健康数据。
- 面向多人协作的医疗档案服务。
- 为追求模块数量而引入复杂的微服务或实时云系统。

## 隐私原则

1. Evolune 数据库是核心事实来源，外部平台不能静默覆盖本地记录。
2. Health Connect 权限默认关闭，按数据类型分别授权。
3. 同步和备份必须明确显示数据范围、目的地、最近时间和失败原因。
4. 导出文件由用户负责保存；应用不应把敏感数据写入普通日志。
5. 云同步必须先完成加密文件格式、密钥生命周期和冲突恢复设计，再接入具体供应商。

## 当前限制

- 目前数据库只有 `dose_events` 和 `medication_plans` 两张实体表，Room 版本为 2，且 `exportSchema = false`。
- `DoseEvent.timeH` 是相对 Unix epoch 的小时数，缺少计划槽位、时区、来源、撤销和审计字段。
- 手机侧通过手动构造 Repository 和 ViewModel，尚未形成依赖注入边界。
- Wear 端是 Tile 与缓存，不是完整可启动的 Wear App。
- 当前 `allowBackup="true"`，工作区中没有重新声明有效的 `dataExtractionRules` 和 `fullBackupContent` 入口；这是需要在发布前处理的隐私风险。
