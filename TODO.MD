# 后续路线（v1.6.0 已封版）

v1.1 Phone Widget Completion 已完成并关闭。以下只保留路线图中的未来工作，不是
当前实现承诺；进入任何批次前都需要独立设计、来源审查、自动化回归和真实设备验收。

## v1.2 — Google Integration & Data Continuity

- [ ] Health Connect：明确数据类型、上下文授权、来源标记、撤权和 provider 不可用
      处理；Room 继续是唯一事实来源。
- [ ] Google backup/restore：先定义版本化、完整性保护和用户可控的备份格式，再评估
      provider、密钥生命周期、预览恢复和错误处理。
- [ ] 保持 Health Connect 与 Google backup 两条可独立验收的产品线；不做实时云端
      多设备数据库同步。

## v1.3 — Wear OS Companion App（已纳入当前基线，未单独发布）

- [x] 当前基线已包含可打开的轻量 Wear App、版本化 snapshot、occurrence identity、Phone
      权威 Repository、persist-first confirmation/undo 和可重建派生缓存；v1.6 继续以此为兼容前提。

## v1.4.0 — Onboarding / Terms / Permission Guidance（已封版发布）

- [x] v1.4-A：首次使用、条款/隐私/医疗免责声明和上下文式权限引导。
- [x] v1.4-B：用药方案、记录服药、PK 图、Widget、Wear 与备份六步功能教程。
- [x] 自动化回归、Phone clean-install smoke 与独立复核。
- [x] 创建 `v1.4.0` release tag/GitHub Release，上传签名 Phone/Wear APK，并完成最终发布签核。

## v1.5 — Stability / Performance / Cleanup（已封版，v1.6 基线）

- [x] v1.5.0 独立设计、稳定性验收矩阵和性能基线已封存。
- [x] 新装、进程重建、重启、跨午夜、时区/DST、升级恢复和异常数据状态已验收。
- [x] 方案修改/删除、多 Widget、Phone/Wear 断连重连、Widget 刷新和备份恢复已验收。
- [x] 启动、PK、Room、Compose、Widget、Wear Data Layer、WorkManager 和后台任务已完成范围内检查。
- [x] 代码清理、真实 Phone/Wear 矩阵和无新增 P0/P1 的 RC 验收已封存；Energy/background 保留明确豁免。

## v1.6 — Widget Gallery（已封版发布）

实施计划：[V16_PLAN](docs/evolune/v1.6/V16_PLAN.md)；
审阅方式：[V16_REVIEW](docs/evolune/v1.6/V16_REVIEW.md)。A–G 已完成，最终矩阵与产物身份见 [V16_ACCEPTANCE](docs/evolune/v1.6/V16_ACCEPTANCE.md) 和 [最终发布门禁](docs/evolune/v1.6/V16_FINAL_RELEASE_GATE_2026-09-09.md)。

- [x] v1.6-A：冻结 v1.5.0 基线、样式/状态/尺寸规格、设备矩阵和刷新性能预算。
- [x] v1.6-B：共享展示状态、按实例样式配置、旧 Widget 兼容及 Wear 字段/协议契约。
- [x] v1.6-C：手机今日计划、下一次服药、当前 E2 三类信息入口及宿主验收；今日完成度并入今日计划。
- [x] v1.6-D：手机大尺寸只读 PK 图表、数值一致性、响应式坐标轴及多实例性能验收。
- [x] v1.6-E：Wear 下一次服药、今日计划、当前 E2 三种 Tile 及跨端确认/跳过动作审阅。
- [x] v1.6-F：下一次服药时间、当前 E2、今日完成度三种表盘信息及真实手表验收。
- [x] v1.6-G：升级、全入口一致性、后台/性能回归、独立审阅、签名候选和真实 Phone/Wear 发布验收。

## v1.7 — Optional CPA PK Curve

- [ ] 在独立科学、来源和回归评估通过后，增加默认关闭且单位独立的 CPA 估算曲线。

## Deferred

- Tracked Date、个性化 PK/calibration、SQLCipher 和 Gradle module extraction 仍需
  单独产品/安全/迁移决策。
