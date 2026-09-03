# 后续路线（v1.4 已实现，下一版本为 v1.5）

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

## v1.4 — Onboarding / Terms / Permission Guidance（已实现，待封版发布）

- [x] v1.4-A：首次使用、条款/隐私/医疗免责声明和上下文式权限引导。
- [x] v1.4-B：用药方案、记录服药、PK 图、Widget、Wear 与备份六步功能教程。
- [x] 自动化回归、Phone clean-install smoke 与独立复核。
- [ ] 创建 release commit/tag，并完成最终发布签核。

## v1.5 — Stability / Performance / Cleanup（下一版本）

- [ ] 覆盖进程重建、跨午夜、时区/DST、升级恢复、Phone/Wear 断连和 Widget 刷新；
      清理死代码、重复状态和性能/耗电问题。

## v1.6 — Widget Gallery

- [ ] 在统一 presentation/state 边界上增加可选 Phone Widget、Wear Tile 或
      Complication 样式，不建立新的事实来源。

## v1.7 — Optional CPA PK Curve

- [ ] 在独立科学、来源和回归评估通过后，增加默认关闭且单位独立的 CPA 估算曲线。

## Deferred

- Tracked Date、个性化 PK/calibration、SQLCipher 和 Gradle module extraction 仍需
  单独产品/安全/迁移决策。
