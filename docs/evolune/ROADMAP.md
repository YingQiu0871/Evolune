# 路线图

本路线图从已发布的 v1.0.0 向后规划。当前实现事实见 [Current Status](CURRENT_STATUS.md)，pre-v1 分阶段计划见已标记为历史文档的 [Migration Plan](MIGRATION_PLAN.md)。

## Released

### v1.0.0 — 2026-08-15

首个公开稳定版本已经封存并发布，release commit 为 `780f167074cc737954c884d375825ef95db605c7`。

主要范围：

- Phone/Wear 公共应用身份、持久 Release signing 与经过验证的签名 APK
- 用药方案、稳定 scheduled-dose slots、用药事件、历史和提醒
- Room v3、schema export、严格 v2-to-v3 migration 与修复工具
- Repository/data boundary、domain/entity mapping 和 PK adapter
- Mahiro JSON v1 导入导出
- PK 估算与浓度图
- RemoteViews Phone Widget 及快速记录
- Wear Tile/Data Layer、dose action 持久化优先、重放/幂等/冲突处理
- Phone/Wear 私有数据的 Android backup/device-transfer 排除规则
- 更新检查、来源与第三方通知、明确的公开发布边界

`v1.0.0` tag 与 GitHub Release 保持封存；后续工作不会移动或重建该 tag。

## Next

### v1.1 — Wear OS + Phone Widget Enhancement

第一步：**Wear / Widget Gap Audit**。

Gap Audit 应根据 v1.0 真实设备行为、自动化测试和用户流程，识别并排序：

- Wear Tile、缓存、离线/重试反馈和手机配对流程的实际差距
- 当前 `/hrt/*` transport 是否需要版本化 envelope、ack 或兼容策略
- 是否需要以及需要多少 Wear application surface
- Phone Widget 的尺寸、布局、快速动作、配置、隐私和 OEM Launcher 差距
- 可观测性、无障碍、功耗和设备矩阵要求

Audit 完成后再锁定 v1.1 scope。当前路线图不预先决定 Glance、具体协议格式、完整 Wear UI 或 Complication 必须进入 v1.1。

## Planned

### v1.2 — Health Connect + Google cloud backup

v1.2 由两个独立 batch 组成，不作为一个耦合实现：

#### Health Connect batch

- 定义明确的数据类型和用户价值，优先评估显式授权的体重读取
- 保持 Room 为核心事实来源
- 覆盖未授权、撤权、provider 不可用、来源与单位映射
- 将任何用药写入/PHR 能力作为单独评估项

#### Google cloud backup batch

- 先定义版本化、加密、可验证的备份格式
- 明确密钥生命周期、损坏/错误密钥行为、恢复预览与冲突处理
- 再接入用户明确授权的 Google provider
- 不把 cloud backup、实时同步和 Wear Data Layer 混为同一边界

每个 batch 必须能够独立验收和推迟，不允许一个集成阻塞另一个已完成能力。

## Later / Deferred

- Personalized PK / calibration evolution，包括 PK 2.0；需独立科学、来源和回归评估
- Tracked Date；仍需产品决策和领域语义设计
- Repository rehousing 与 `D:\Evolune` protected-root retirement；需要单独、可验证的迁移批次
- 由测试隔离和构建收益驱动的 Gradle module extraction
- SQLCipher 或其他数据库透明加密；先完成威胁模型、迁移与密钥恢复设计
- 更丰富的历史统计、筛选、Wear 设备形态或桌面只读能力

## 永久边界

- 不把 PK 估算描述为实验室结果、诊断或治疗建议。
- 不静默上传健康或用药数据。
- 不以未来功能破坏 v1.0 schema、稳定 ID、JSON 兼容或 sealed release history。
- 不扩大已记录的 PK permission scope；始终保留来源和贡献者 attribution。
