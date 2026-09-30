# Evolune v1.10.0 正式版 / Official Release

Evolune v1.10.0 新增可选的醋酸环丙孕酮（CPA）估算曲线，并改进历史页的返回体验与设置页的整体排版。
Evolune v1.10.0 adds an optional cyproterone acetate (CPA) estimated curve, keeps the History scroll position when returning from its sub-pages, and reorganizes the Settings page.

## 主要更新 / Highlights

### CPA 估算曲线（默认关闭）/ Optional CPA estimated curve (off by default)

- 设置 →「血药浓度计算」→「显示 CPA 估算曲线」。开启后，首页浓度图在同一时间轴上叠加一条 CPA 估算虚线，使用右侧独立的 ng/mL 坐标轴；E2 仍使用左侧 pg/mL 坐标轴，两者从不相加或共用刻度。/ Settings → Concentration estimate → Show CPA estimated curve. The Home chart then overlays a dashed CPA estimate on the same time axis with its own right-hand ng/mL axis; E2 keeps the left pg/mL axis and the two are never summed or share a scale.
- 只计入明确记录为 CPA 的抗雄药记录与已启用的 CPA 方案；未标明药物类型的旧记录不会被猜测为 CPA。/ Only anti-androgen records and enabled plans explicitly typed as CPA are used; untyped legacy records are never guessed to be CPA.
- 模型：口服一室模型，参数取自 Androcur 说明书（终末半衰期 43.9 h、清除率 3.5 mL/min/kg、生物利用度 88%），与 Oyama 参考实现交叉核对。它能反映总暴露量、累积与稳态水平，但**会低估单次服药后的峰值**（CPA 实际为双相分布）。界面文案明确标注"模型估算，非实测"。/ Model: oral one-compartment with parameters from the Androcur product information (terminal half-life 43.9 h, clearance 3.5 mL/min/kg, bioavailability 88%), cross-checked against the Oyama reference. It reflects total exposure, accumulation and steady-state levels but **underestimates single-dose peaks** (CPA disposition is biphasic). The UI labels it as a model estimate, not a measurement.
- 关闭时不进行任何 CPA 计算；无论开关状态，E2 曲线与数值、Widget、Wear、回顾性 PK 均不受影响。该开关属于本机显示偏好，不进入备份、恢复或导出。/ With the switch off no CPA work runs; either way the E2 curve and values, Widgets, Wear and Retrospective PK are unchanged. The switch is a local display preference and is not part of backup, restore or export.
- 触摸图表可同时查看该时刻的 E2 与 CPA 数值；靠近右边缘时信息窗改为显示在触点左侧，不再被裁切。/ Touch inspection shows E2 and CPA at the same time; near the right edge the tooltip now flips to the left of the finger instead of being clipped.

### 历史页 / History

- 从「用药洞察」「回顾性 PK」「用药时间线」返回时，历史页停留在进入前的位置，不再跳回顶部；返回时的刷新不再把当天记录临时替换为加载占位。/ Returning from Insights, Retrospective PK or Timeline keeps the History list where it was; the return refresh no longer swaps the day's entries for a loading placeholder.

### 设置页 / Settings

- 按用途重新分组为四部分：血药浓度计算（体重、Health Connect 体重同步、CPA 曲线）、外观、备份与数据（Google Drive 备份置顶）、关于与帮助。/ Regrouped by purpose: concentration estimate (body weight, Health Connect weight sync, CPA curve), appearance, backup & data (Google Drive first), help & about.
- 夜间模式与时间制式改为紧凑的分段按钮；预设配色的 8 个色块只在选择「预设配色」后显示。/ Theme mode and time format are compact segmented rows; the eight preset palette tiles appear only after choosing the preset source.
- 「功能教程」与「指南」合并为一个「使用帮助」入口，其中「指南」更名为「首次引导」。/ Feature tutorial and guide are merged into one Help entry; the guide is renamed First-run guide.
- 旧格式（Legacy / Mahiro JSON v1）导入导出默认收起；Health Connect 控件改为与其他设置一致的列表样式，并移除过时的「用药数据同步」占位行。/ Legacy Mahiro import/export is collapsed by default; Health Connect controls use the same list style as the rest of the page and the stale medication-sync placeholder row is removed.

## 兼容性 / Compatibility

- 无 Room 数据库 schema 迁移；无备份格式变化（仍为 schema v2）；Portable JSON/CSV 格式不变；E2 用药计算语义与 PK 数值代码不变；应用 ID 与签名证书连续。/ No Room schema migration, no backup format change (still schema v2), Portable JSON/CSV unchanged, E2 medication calculation semantics and PK numerics unchanged, application ID and signing continuity unchanged.
- 历史页刷新期间保留已加载内容，是对 v1.7 Phase A 历史页加载状态行为的有意调整；其余 v1.7 冻结语义未改动。/ Keeping loaded History content during a refresh is a deliberate adjustment of the v1.7 Phase A loading-state behavior; other frozen v1.7 semantics are untouched.
- 版本信息：Phone `1.10.0` — versionCode `101100000`；Wear `1.10.0` — versionCode `1101100000`。/ Version: Phone `1.10.0` / `101100000`; Wear `1.10.0` / `1101100000`.

## 验证状态 / Verification status

（发布前补充 / to be completed before publication）
