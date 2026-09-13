# B-00-R1 medication identity disposition + dose rules (snapshot)
# Extraction rule: start at "## 13. Medication identity eligibility", end at "## 14. Date / timezone aggregation"; verbatim.

## 13. Medication identity eligibility（**单值**处置 + 剂量规则；B-00-R1 冻结）

**来源事实**：`matchKey = MedicationMatchKey(routeKey, medicationKey, doseAmount)`；
生产映射 `MedicationPlan.toMedicationSchedule()` / `DoseEvent.toRecordedMedicationEvent()` 一律设
`medicationKey = ester.name`（**酯类槽位**），而 anti-androgen 的真实药物在 `extras[ExtraKey.ANTI_ANDROGEN_TYPE]`，
**投影不携带 extras**（A-04 已登记为 presentation-model debt）。

### 13.1 身份三态（单值处置，不再有 "raw key 或 Unknown" / "Unknown 或 exclude" 的歧义）

| 身份状态 | 判据 | 用户可见名称 | 能否进入 named-medication aggregate |
|---|---|---|---|
| `identity known` | `routeKey != ANTIANDROGEN` 且 `medicationKey ∈ {E2,EB,EV,EC,EN}` | 既有 `ester_*` 字符串 | **可以**（per-medication dose total / chart / breakdown） |
| `identity partial` | `medicationKey` 不在上述集合（未知/外来值），或 `routeKey` 无法映射 | **`Unknown medication`**（**不得**把 raw key 当作用户可见药名） | **不可以** |
| `identity unavailable` | `routeKey == ANTIANDROGEN`（真实药物不可从投影恢复） | **`Unknown medication`** | **不可以** |

**P2-2 冻结**：`partial` 与 `unavailable` 采用**同一个**用户可见名称 **`Unknown medication`**
（不采用 "Unknown anti-androgen" 分支：更简单的 aggregation model，且同样不猜药名）。
两者都不进入任何 named-medication aggregate；如需区分原因，只能作为**非用户可见**的内部诊断，不作为药名。

### 13.2 剂量规则（B-00-R1 冻结）

- **不提供跨药物的 overall dose total**：不同 medication 的 mg 不可直接相加，没有可比较的临床意义；
- **Per-medication dose total** 只统计 `identity == known`；
  `partial` / `unavailable` **排除**在 per-medication dose totals 之外；
- 另可提供 **`unknownIdentityRecordedIntakeCount`**（**计数**，不是剂量）以保持透明度；
- **不得**建立 `Unknown medication = 350 mg` 这类把不同药物 mg 混在一起的剂量桶。

**冻结**：`Ester.E2` 是**槽位占位符**，绝不能据以推断抗雄药身份；
真实修法需要投影携带 anti-androgen identity（`A-04/B presentation model debt`，不阻塞 B-01）。
