# PK 2.0：σ/τ 参数审阅

状态：审阅结论（2026-10-17）。本审阅基于公开文献独立重新推导两个参数，不再沿用第 1 片从
Oyama-s-HRT-Tracker 取的参考值。它不是同行评审，也不是临床验证；正式对外宣传前，仍建议请熟悉
药代动力学或内分泌检验的人复核本文。

## 1. 两个参数的含义

校准在 log 空间做最大后验估计（计划 §3.1）：

```text
a = Σ mᵢ/σ² ÷ (n/σ² + 1/τ²)      mᵢ = ln(实测ᵢ / 模型预测ᵢ)
```

- **σ（`MEASUREMENT_LOG_SD`）**：同一个人、个人系数已经校正之后，单次化验相对模型的 log 偏差。
  它不只是检测仪器的精密度，而是下面几项的合成：
  1. 实验室内检测不精密度；
  2. 同一方案下不同日子之间的真实波动（吸收、代谢、贴片贴合、注射部位等）；
  3. 采血时间记录误差，以及模型曲线形状与本人不完全一致（例如清除率差异，见
     [第 5b 片评估](PK2_CLEARANCE_EVALUATION.md)）；
  4. 换实验室或换检测方法带来的系统偏差（只在用户换实验室时出现）。
- **τ（`PRIOR_LOG_SD`）**：同样剂量下，人与人之间 E2 暴露（相对模型）的 log 差异，即先验有多宽。
  它同样吸收了模型人群均值本身的偏差，以及用户所用实验室的系统偏差。

σ/τ 决定只有一次化验时信任它多少：保留比例为 τ²/(τ²+σ²)。

## 2. 文献证据

### 2.1 检测误差

- 美国 76 家实验室对 5 份单供体血清的能力验证（Cao 等，Clin Biochem 2023；ADLM 2024 摘要）：
  相对 CDC 目标值的平均偏差在 24 pg/mL 时为 +34%（单个实验室 −17% 至 +175%），
  62 pg/mL 时 +16%（−45% 至 +193%），127 pg/mL 时 −4%（−31% 至 +21%）。
  只有 59%–87% 的实验室能满足 ±25%–±30% 的判定标准；以同方法组均值为目标时，95% 以上合格，
  说明误差主要是**方法之间的系统偏差**，而不是同一方法内部的随机误差。
- Caudill 等（Steroids 2014）比较 11 种免疫法与 6 种质谱法：各参与者平均偏差 −2.4% 至 +235%，
  17 种方法中只有 3 种满足 ±12.5% 的偏差标准；校准偏差是主要来源。
- 口服雌二醇的跨性别女性中，Beckman Access Sensitive Estradiol 免疫法相对 LC-MS/MS 中位偏差
  −40%，Roche −14%，旧版 Beckman +23%（Endocrine Connections 2022, EC-21-0550）。
  也就是说偏差方向和大小因方法和用药途径而异。
- 质谱法实验室内精密度可以很好（Fuhrman 等，CEBP 2014，总 CV 远低于 1%），
  但该研究是单次采血，无法回答同一人随时间的波动。

结论：**同一实验室、同一方法内**的随机误差大约 5%–15%；**跨实验室**的系统偏差可达 ±30% 以上，
在 < 50 pg/mL 时更大。系统偏差对同一实验室的所有化验相同，会被个人系数吸收，不属于 σ；
但用户换实验室时会表现为化验之间的不一致。

### 2.2 同一人不同日子的波动

- 透皮制剂个体内 AUC 变异约 20%（维基百科《Pharmacokinetics of estradiol》引用的综述）。
- 口服雌二醇服药后峰谷比约 7 倍，饮食、酒精、药物等会造成短暂变化（British Menopause Society，
  Measurement of serum estradiol, 2025）。口服时 1 小时的采血时间误差就可能明显改变预测值。
- 皮下注射 E2 的小样本中，同一人注射前与注射后 1–2 天的 E2 均值分别为 88 与 138 pg/mL，
  注射前值的 95% CI 为 16–159 pg/mL（Moreau 等，J Endocr Soc 2026, bvaf192），
  说明谷值附近的个体差异和时间敏感性都很大。

### 2.3 人与人之间的差异

- 口服：绝对生物利用度约 5%（0.1%–12%），平均 AUC 个体间差异 28%–127%，最大约 4.6 倍。
  跨性别女性口服 E2 的横断面数据（Leinung 等，Transgender Health 2018）：
  2 mg/天 62 ± 23，4 mg/天 102 ± 59，6 mg/天 125 ± 62，8 mg/天 159 ± 76 pg/mL，
  CV 约 37%–58%，对应 log SD 约 0.36–0.53（该数据还混有检测误差、采血时间和按化验调剂量的影响）。
- 透皮：个体间 AUC 变异 20%–44%，但最大可差 6–10 倍；同一 100 µg/天贴片剂量下观察到
  70–1045 pg/mL。
- 注射：生物利用度基本完整，没有找到可靠的个体间 CV；Transfeminine Science 的注射 meta 分析在合并
  不同研究时，把 AUC 偏离平均值 50% 以上的曲线排除，说明研究之间（即模型人群均值本身）也有
  ±50% 量级的差异。

## 3. 结论与取值

| 参数 | 原值 | 新值 | 理由 |
| --- | --- | --- | --- |
| σ | 0.13（约 ±13%） | **0.25**（约 ±28%） | 检测内误差约 0.1、日间波动约 0.2、时间与形状误差约 0.1 的合成（√(0.1²+0.2²+0.12²) ≈ 0.25）；0.13 只相当于单独的检测精密度，低估了真实散布 |
| τ | 0.5（约 ×0.37–×2.7 的 95% 区间） | **0.5**（不变） | 口服与透皮的人群数据对应 log SD 约 0.4–0.55；再加上模型均值和实验室偏差，0.5 合理，不需要更宽 |
| 安全边界 | 0.25–4 | 不变 | 约为 ±2.8τ |

改动的效果（单次化验比模型高一倍时）：

| σ / τ | 1 次化验后的系数 | 系数不确定度 | 4 次一致化验后的系数 |
| --- | --- | --- | --- |
| 0.13 / 0.5（原） | 1.91 | ±13% | 1.98 |
| **0.25 / 0.5（新）** | **1.74** | **±25%** | **1.92** |
| 0.35 / 0.5 | 1.59 | ±33% | 1.85 |

单次化验时更谨慎（保留 80% 的 log 偏差，原来 94%），多次一致的化验仍会接近实际比值。
拟合误差仍相对实际应用的系数计算，所以几次完全一致的化验也会因收缩显示几个百分点的差异
（4 次都是模型的 1.5 倍时约 ±2%）。

对第 5b 片的影响：σ 变大后清除率更难辨识，"暂不实现清除率拟合"的结论不变，而且更站得住。

## 4. 后续建议（未排期）

- **离群化验**：跨实验室偏差可达数倍，正态似然对单个离群值很敏感。若出现用户反馈，可考虑
  Student-t 似然，或在拟合误差很大时提示"化验之间差异较大，可能来自换实验室或采血时间"。
- **说明卡片**：可以在只有 1 次化验时提示"系数仍有约 ±25% 的不确定度"。
- **复核**：请熟悉 E2 药代或检验医学的人复核 §2–§3；若有 Evolune 用户自愿提供的匿名
  化验与用药记录，可直接估计 σ，比文献合成更可靠。

## 5. 来源

- Cao TZ, Rej R, Vesper H, Astles JR. Are estradiol assays accurate enough for clinical testing?
  ADLM Scientific Short, 2024；原始研究 Clin Biochem 2023, doi:10.1016/j.clinbiochem.2023.110700。
  <https://myadlm.org/science-and-research/scientific-shorts/2024/are-estradiol-assays-accurate-enough-for-clinical-testing>
- Caudill SP, Vesper HW, et al. High variability in serum estradiol measurements in men and women.
  Steroids 2014;82. doi:10.1016/j.steroids.2013.12.005
- Oral estrogen leads to falsely low concentrations of estradiol in a common immunoassay.
  Endocrine Connections 2022;11(2). doi:10.1530/EC-21-0550
- Fuhrman BJ, et al. Assay reproducibility and interindividual variation for 15 serum estrogens and
  estrogen metabolites measured by LC-MS/MS. Cancer Epidemiol Biomarkers Prev 2014;23(12):2649–57.
- Leinung MC, Feustel PJ, Joseph J. Hormonal treatment of transgender women with oral estradiol.
  Transgender Health 2018;3(1):74–81. doi:10.1089/trgh.2017.0035（数据表取自 Transfeminine Science 的整理）
- Moreau HM, et al. Evaluation of 17β-estradiol administration by subcutaneous injections in
  transgender women. J Endocr Soc 2026;10(1):bvaf192. doi:10.1210/jendso/bvaf192
- British Menopause Society. Measurement of serum estradiol in the menopause transition. 2025.
- Wikipedia, Pharmacokinetics of estradiol（仅用于转引个体内/个体间变异的原始文献数值）。
- Transfeminine Science. An informal meta-analysis of estradiol curves with injectable estradiol
  preparations.
