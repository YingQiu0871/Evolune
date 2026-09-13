# B-00-R1 range semantics + deterministic examples (snapshot)
# Extraction rule: start at "## 19. Range semantics", end at "## 20. Open decisions"; verbatim.

## 19. Range semantics（**preset 端点冻结**，B-00-R1）

所有区间基于 **`LocalDate`，start 与 end 双端 inclusive**，与 `HistoryReadService`/`HistoricalRange` 一致。

| Preset | 冻结端点（`today` = 当前 display date） | 天数 |
|---|---|---|
| `Last 7 days` | `startDate = today − 6 days`，`endDate = today` | 恰好 **7** 个 LocalDate（含 today） |
| `Last 30 days` | `startDate = today − 29 days`，`endDate = today` | 恰好 **30** |
| `Last 90 days` | `startDate = today − 89 days`，`endDate = today` | 恰好 **90** |
| `Current month` | `startDate = 当月 1 日`，`endDate = today` | 当月 1 日..today |
| `Custom` | `startDate <= endDate <= today`，双端 inclusive | 任意 |

**明确禁止**（冻结）：
- "previous completed 7/30/90 days excluding today"（那是**另一个** preset，如未来需要必须新增命名，不得改变本表定义）；
- rolling **instant** 窗口（`Instant` 半开区间）用于历史指标；
- exclusive end（`endDate` 不含）。

若 UI 未来想提供"上一个完整周期"，属于新增 preset，本表冻结定义不变。

### 19.1 确定性区间示例（today = 2026-09-13）

| Preset | 结果 | 校验 |
|---|---|---|
| `Last 7 days` | `2026-09-07 .. 2026-09-13` | 2026-09-13 − 6d = 2026-09-07；含 today 共 7 日 |
| `Last 30 days` | `2026-08-15 .. 2026-09-13` | 08-15 + 29d = 09-13（8 月 31 日：16 + 13 = 29） |
| `Last 90 days` | `2026-06-16 .. 2026-09-13` | 06-16 + 89d = 09-13（6 月剩 14 + 7 月 31 + 8 月 31 + 13 = 89） |
| `Current month` | `2026-09-01 .. 2026-09-13` | 当月 1 日 .. today |
| `Custom`（合法） | `2026-09-01 .. 2026-09-10` | `startDate <= endDate <= today` |
| `Custom`（非法） | `2026-09-10 .. 2026-09-01` | `startDate > endDate` → 拒绝（fail-fast） |
| `Custom`（非法） | `2026-09-01 .. 2026-09-14` | `endDate > today` → 拒绝（未来不是历史） |

区间端点、空区间（无 entry）、DST 日、跨时区行为必须与 History 的既有 range 语义**逐项一致**（B-01 交叉断言）。
