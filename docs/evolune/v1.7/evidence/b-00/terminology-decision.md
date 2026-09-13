# B-00 terminology decision (snapshot)
# Extraction rule: verbatim copy of V17_B_00_INSIGHTS_SEMANTICS.md section 11 and section 18
#   section 11: start '## 11. Adherence', end '## 12. Percentage denominator definitions'
#   section 18: start '## 18. Explicitly forbidden metrics / wording', end '## 19. Range semantics'
# Source document at the B-00 base HEAD; the source document is authoritative if they differ.

## 11. Adherence 术语裁决（**B — 不对用户展示 clinical adherence 百分比**）

| 选项 | 裁决 |
|---|---|
| A 允许 `adherence` | **不采用**（缺少可证 denominator 与历史处方真实性，§9） |
| **B 采用** | v1.7 不展示临床含义的 adherence percentage；改用中性术语 |

**允许的用户可见术语（冻结）**：`Recorded` / `No recorded intake` / `Record coverage` / `Intake history` /
`Recording consistency` / `Intake activity` / `Recorded intakes` / `Days with recorded intake` / `By source`（来源分布）。

**禁止的用户可见术语（冻结）**：`adherence` / `adherence rate` / `compliance` / `missed` / `skipped` /
`forgot` / `non-adherent` / `on time` / `late` / `overdue` / `failed`，以及中文 `依从`/`依从率`/`漏服`/`漏吃`/
`跳过`/`忘记`/`未依从`/`准时`/`延迟`/`逾期`。

> 唯一例外（沿用 Phase A 冻结文案）：`无记录` 的**中性免责声明**必须保留（A-03 `history_note_not_necessarily_missed`），
> 它显式否定"没记录=漏服"的解读。路线图阶段名为 *Adherence Insights* **不构成** UI 必须输出 adherence % 的义务。

---

---

## 18. Explicitly forbidden metrics / wording（冻结）

**指标层面禁止**：`adherence rate`/`compliance`/`missed count`/`skipped count`/`forgot count`/
`on-time percentage`/`late count`/`delay from plan`/`uncompleted`（作为"未完成"语义）/
`undo count`/`retracted intake`/依从性评分或百分比/任何 red-green 评分。

**措辞层面禁止**：`Missed`/`Skipped`/`Forgot`/`Non-adherent`/`Late`/`Overdue`/`On time`/`Failed`/
`依从`/`漏服`/`漏吃`/`跳过`/`忘记`/`未依从`/`准时`/`延迟`/`逾期`。

**同时禁止**："没记录"被渲染为沉默的负向信号（颜色/图标/排序暗示"未完成"）——Ambiguity must stay ambiguous
（Invariant 4）。

---
