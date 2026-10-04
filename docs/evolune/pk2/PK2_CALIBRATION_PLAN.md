# PK 2.0 — 个性化 E2 校准：切片计划与第 1 片设计

状态：进行中（2026-10-04 起）。第 1 片只交付纯计算核心，没有任何生产调用方、界面或数据库变更；
截至本文，个性化 calibration/PK 2.0 仍不是已交付功能。

## 1. 目标与边界

用户录入的血清雌二醇（E2）化验结果，用来校准首页与回顾性 PK 的 E2 **模型估算**曲线。

沿用[路线图](../ROADMAP.md)的永久边界：

- 校准后的曲线仍是模型估算，不是实测血药浓度，也不是诊断或治疗建议。
- 生产 E2 模型（`SimulationEngine` 及其参数）不改动。校准是作用在模型输出上的独立乘法层，
  关闭或没有化验时，曲线与数值和现在逐位一致。
- Widget、Wear 不接入校准，也不成为化验数据的第二事实来源。
- 化验数据不上传；未来进入备份/导出时遵循现有本机加密备份与 JSON 兼容规则。

## 2. 切片

| # | 内容 | 用户可见 | Schema |
| --- | --- | --- | --- |
| 1 | 校准核心算法：单位换算、化验点与模型对比、个人幅度系数、测试与本文 | 否 | 否 |
| 2a | 化验结果存储：独立 Room 数据库 `evolune_labs`（v1）、repository、与第 1 片的输入桥接 | 否 | 新库，不改 v3 |
| 2b | 加密备份/恢复包含化验结果：备份 payload schema v3、恢复日志格式 v2 | 否 | 备份格式 v3（无化验时仍写 v2） |
| 2c | Mahiro JSON `labResults` 导入导出（当前忽略并写空数组） | 否 | 否 |
| 3 | 化验结果录入、编辑、删除与列表界面 | 是 | 否 |
| 4 | 首页 E2 曲线校准：设置中默认关闭的开关，图上标出化验点与校准系数，文案注明模型估算 | 是 | 否 |
| 5 | 回顾性 PK 接入校准；评估个人清除率（半衰期）拟合与时间加权 | 是 | 否 |

每片单独 PR，按顺序合并。

## 3. 第 1 片设计

代码：`app/src/main/java/io/github/yingqiu0871/evolune/pk/calibration/E2Calibration.kt`；
测试：`app/src/test/.../pk/calibration/E2CalibratorTest.kt`（10 个）。

### 3.1 算法

1. **化验点**（`E2Calibrator.points`）：把每条化验换算为 pg/mL（pmol/L ÷ 3.671，由 E2 摩尔质量
   272.38 g/mol 得出），与未校准模型在同一时刻的插值预测配对。以下化验被排除：
   时间或数值非有限、数值 ≤ 0、在模拟时间范围之外、模型预测 < 1 pg/mL（例如首剂前的基线抽血，
   比值没有意义）。
2. **幅度拟合**（`E2Calibrator.fit`）：设 `mᵢ = ln(实测ᵢ / 预测ᵢ)`。在 log 空间做最大后验估计，
   先验为以模型为中心（系数 1）的高斯分布，测量噪声为高斯分布，闭式解：

   ```text
   a = Σ mᵢ/σ² ÷ (n/σ² + 1/τ²)      scale = exp(a)，再限制在 [0.25, 4]
   ```

   单次化验时系数会被轻微拉向 1（σ=0.13、τ=0.5 时保留约 94% 的 log 偏差）；化验越多越接近
   各次比值的几何平均。互为倒数的两次化验相互抵消。
3. **拟合误差**：n ≥ 2 时报告 log 空间残差 RMSE 换算的 ±%，残差相对于实际应用的系数计算。
4. **应用**（`E2Calibrator.apply`）：浓度与 AUC 同乘系数，时间网格不变；没有可用化验时返回原对象。

只校准幅度（对应分布容积/生物利用度的个体差异），不改变曲线形状。这是最保守的选择：
半衰期需要多次间隔充分的化验才可辨识，放到第 5 片单独评估。

### 3.2 参数与来源

| 参数 | 值 | 含义 | 来源 |
| --- | --- | --- | --- |
| `PMOL_PER_L_PER_PG_PER_ML` | 3.671 | 单位换算 | E2 摩尔质量 272.38 g/mol |
| `MEASUREMENT_LOG_SD` (σ) | 0.13 | 单次化验 log 噪声（检测 + 抽血时点），约 ±13% | 参考值，见下 |
| `PRIOR_LOG_SD` (τ) | 0.5 | 人群幅度先验，约 ±65% | 参考值，见下 |
| `MIN_PREDICTED_PG_ML` | 1.0 | 可比较的最低模型预测 | 参考值，见下 |
| `MIN_SCALE`/`MAX_SCALE` | 0.25 / 4 | 硬性安全边界 | Evolune 选择 |

σ、τ 和 1 pg/mL 阈值取自 [`SmirnovaOyama/Oyama-s-HRT-Tracker`](https://github.com/SmirnovaOyama/Oyama-s-HRT-Tracker)
的 `logic.ts` 中化验校准部分（2026-10-04 阅读，该仓库 `LICENSE` 为 MIT，Copyright (c) 2025 Joseph
Smirnova Oyama）。该项目提供 EKF、OU-Kalman 与 MIPD 等多种校准方式并同时拟合清除率；Evolune 第 1 片
只采用"人群先验下的 log 空间幅度"这一思路，以闭式解独立实现，没有复制源码。这些数值是工程参考值，
不是经过独立文献核验的科学参数；在第 4 片面向用户发布前，需要完成独立科学审阅，并在
[SOURCE_PROVENANCE](../../SOURCE_PROVENANCE.md) 中记录最终的来源与许可结论。

### 3.3 回归保证

- 新代码只新增文件，不修改 `pk/` 下已有文件；现有 E2 数值测试不受影响。
- 没有生产调用方，因此 Home、Widget、Wear、回顾性 PK 的行为不变。

## 4. 待定事项（后续切片）

- 化验来自很久以前、方案已变化时是否按时间衰减权重（第 5 片评估）。
- 是否在单次化验离群时使用稳健似然（如 Student-t）。
- 睾酮单位化验：不在范围内，导入时保留但不参与 E2 校准（第 2 片决定存储语义）。

## 5. 第 2a 片：存储

- 化验结果放在独立的 Room 数据库 `evolune_labs`（version 1，表 `lab_results`），不改 `AppDatabase`
  的 v3 结构、迁移链和降级边界；旧版 App 不会打开这个文件。现有 `backup_rules.xml` 与
  `data_extraction_rules.xml` 的 `domain="database" path="."` 已把它排除在云备份和设备迁移之外。
- 领域模型 `core.model.LabResult`（UUID、`measuredAt`、数值、单位、revision）与
  `core.dataapi.LabResultRepository`，语义与 `DoseEventRepository` 一致：插入要求 revision 1 且幂等，
  更新和删除按 revision 比较。
- 单位持久化码 `PG_PER_ML`/`PMOL_PER_L`/`NG_PER_DL`/`NMOL_PER_L` 冻结；睾酮单位只保存不参与 E2 校准
  （`LabResult.toE2LabResultOrNull()` 返回 null）。
- 数值必须有限且 > 0，时间必须能精确表示为毫秒；不合规的持久化行报错而不修复。
- 不建时间索引：化验条数很少，全表读取即可。
- 在第 2b 片之前，恢复备份不触及化验库；此时还没有录入界面，库为空。

## 6. 第 2b 片：加密备份与恢复

- 备份 payload 新增可选字段 `labResults`（id、`measuredAt`、数值、单位码、revision）。有该字段的
  payload 写 schema v3，没有则仍写字节不变的 schema v2；解码时版本号与字段是否存在必须一致，否则视为
  格式错误。支持读取的版本为 1、2、3。
- 本机没有化验结果时导出不带 `labResults`（schema v2），所以旧版 App 仍能恢复这类备份。
- 恢复 v1/v2 备份（没有 `labResults`）不触及本机化验库；恢复 v3 备份则整体替换化验库，空列表会清空。
- 主库和化验库各自一个事务，先主库后化验库；两者之间的空档由恢复日志兜底。日志的 `beforeRoom`
  带上恢复前的化验结果，格式版本升到 2；不含化验结果的日志仍写版本 1、不出现该键。启动恢复和失败回滚
  会把化验库一并还原。
- 校验规则与第 2a 片的持久化规则一致：规范 UUID 且不重复、时间可精确到毫秒、数值有限且 > 0、
  单位码在冻结列表内、revision ≥ 1。
