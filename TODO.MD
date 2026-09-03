# 后续路线（v1.4.0 已发布，下一版本为 v1.5）

v1.1 Phone Widget Completion 已完成并关闭。以下只保留路线图中的未来工作，不是
当前实现承诺；进入任何批次前都需要独立设计、来源审查、自动化回归和真实设备验收。

## v1.2 — Google Integration & Data Continuity

- [ ] Health Connect：明确数据类型、上下文授权、来源标记、撤权和 provider 不可用
      处理；Room 继续是唯一事实来源。
- [ ] Google backup/restore：先定义版本化、完整性保护和用户可控的备份格式，再评估
      provider、密钥生命周期、预览恢复和错误处理。
- [ ] 保持 Health Connect 与 Google backup 两条可独立验收的产品线；不做实时云端
      多设备数据库同步。

## v1.3 — Wear OS Companion App

- [ ] 在保留现有 Wear Tile/Data Layer 的前提下，设计可打开的轻量 Wear App，并复用
      occurrence identity、Phone 权威 Repository 和 persist-first 动作。

## v1.4.0 — Onboarding / Terms / Permission Guidance（已封版发布）

- [x] v1.4-A：首次使用、条款/隐私/医疗免责声明和上下文式权限引导。
- [x] v1.4-B：用药方案、记录服药、PK 图、Widget、Wear 与备份六步功能教程。
- [x] 自动化回归、Phone clean-install smoke 与独立复核。
- [x] 创建 `v1.4.0` release tag/GitHub Release，上传签名 Phone/Wear APK，并完成最终发布签核。

## v1.5 — Stability / Performance / Cleanup（下一版本）

- [ ] 建立 v1.5 独立设计、稳定性验收矩阵和性能/耗电基线。
- [ ] 覆盖新装、进程重建、重启、跨午夜、时区/DST、升级恢复、异常数据状态。
- [ ] 覆盖方案修改/删除、多 Widget、Phone/Wear 断连重连、Widget 刷新和备份恢复。
- [ ] 检查启动、PK、Room、Compose、Widget、Wear Data Layer、WorkManager 和后台任务性能。
- [ ] 检查 wakeups、轮询、重复 Flow collection、重复 Widget refresh、Wear 活跃状态和耗电。
- [ ] 清理死代码、重复 mapper、废弃资源、无用依赖、重复状态和失效兼容层，并提供行为等价证据。
- [ ] 修正手机 launcher Logo：整体缩小前景标记与月亮视觉占比，保持比例、居中、安全区、各 density、mask 和 monochrome 一致。
- [ ] 完成真实 Phone/Wear 矩阵、代码清理审计和无新增 P0/P1 的 RC 验收。

## v1.6 — Widget Gallery

- [ ] 在统一 presentation/state 边界上增加可选 Phone Widget、Wear Tile 或
      Complication 样式，不建立新的事实来源。

## v1.7 — Optional CPA PK Curve

- [ ] 在独立科学、来源和回归评估通过后，增加默认关闭且单位独立的 CPA 估算曲线。

## Deferred

- Tracked Date、个性化 PK/calibration、SQLCipher 和 Gradle module extraction 仍需
  单独产品/安全/迁移决策。
