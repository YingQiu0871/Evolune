# B-00-R1 coverage disclosure + confidence buckets + timezone disclosure (snapshot)
# Extraction rule: start at "## 24. Coverage disclosure", end at "## 27. Dose unit assumption"; verbatim.

## 24. Coverage disclosure（P3-15，对 **shipped 计数**同样适用）

第一版 ship 的 `record coverage counts`（matched historical occurrences / no-record historical occurrences）
**必须同时**展示/定义以下两句，不能等到 ratio 才披露第二句：

1. `Occurrences are generated from the current schedule context.`
2. `No recorded intake does not prove that a dose was not taken.`

推荐英文产品 subtitle（语义冻结，中文 localization 后续）：

> **`Records linked to generated schedule occurrences`**

同时必须可展示 **binding confidence breakdown**（§25），以便让 inferred/LOW 的匹配质量透明。

## 25. Confidence breakdown bucket（P3-16）

- breakdown **只针对 recorded intakes**（matched + unmatched 的绑定置信度）；
- bucket 恰好四个：`HIGH` / `MEDIUM` / `LOW` / `NONE`；
- `UnrecordedHistoricalOccurrence` **不进入** confidence breakdown（无 binding confidence）；
- **不设** `n/a (no record)` bucket（原 worked example 中的该 bucket 已删除）。

## 26. Timezone confidence disclosure（P3-17）

对**所有按 `displayDate` 聚合**的指标，若区间内存在
`displayDateProvenance == CURRENT_DISPLAY_TIMEZONE_DERIVED` 的 entry：

- 聚合模型必须能暴露 **`containsCurrentTimezoneDerivedDates = true`**（或语义等价信息）；
- 后续 UI 可显示中性说明：`Some legacy dates are shown using the current time zone.`

适用范围（**全部**，不只 coverage）：`recorded intakes`、`days with recorded intake`、`by source`、
`confidence breakdown`、以及任何**按 range 过滤**的 `dose totals`
（剂量数值本身不受日期归属影响，但"是否落入该区间"仍由 `displayDate` 决定，因此同样继承 range-level disclosure）。

B-00 只冻结该 requirement，不写代码。
