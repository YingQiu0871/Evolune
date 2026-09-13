# V17-PRE-A-01 — Wear Skip Reachability Fix（证据记录）

> 状态：`IMPLEMENTED / READY FOR INDEPENDENT REVIEW`
> Product/code baseline：`66fb73e65e95df844c2f672cfcb78f98a614b77b`（Phase-0 freeze commit，父提交 `72a468c…`）
> Candidate commit：本文档所在的窄 commit（`fix: restore Wear skip DataItem reachability`；同一 commit 无法自引用自身 SHA，SHA 见 PRE-A-01 closure report）
> 工作区：`D:\Evolune-Workspace\worktrees\Evolune-v1.7`（branch `v1.7-development`）
> 范围：仅修复 **reachability**；未做 v1.7-A、未改协议/Room/PK/版本/依赖。

---

## 1. Root cause（从当前 HEAD 独立重验）

**症状**：Wear App 的"跳过本次"显示"已发送跳过请求"，但 Phone 永远收不到该请求。

**已确认根因**：Phone manifest 中 `.wear.WearAppListenerService`（类声明见 `app/src/main/java/io/github/yingqiu0871/evolune/wear/WearAppDataLayer.kt:125`；注意该 service 类**不在**独立文件里）的 `DATA_CHANGED` intent-filter 只声明了
`pathPrefix="/hrt/v1/wear-app/commands"`（`app/src/main/AndroidManifest.xml:120-126`，基线状态），
而 Wear 发送 skip 请求使用的是 **精确路径** `/hrt/v1/wear-app/skip-notification`
（`experience-core/.../wear/WearAppProtocol.kt:30`）。该路径既不等于 `/hrt/v1/wear-app/request`（MESSAGE_RECEIVED filter），
也不以 `/hrt/v1/wear-app/commands` 开头，因此 **没有任何 intent-filter 与之匹配**，服务不会被投递。

链路两侧均已存在且完整（本轮未改）：

| 侧 | 位置 | 状态 |
|---|---|---|
| Wear 发送 | `wear/.../WearAppDataLayer.kt:23-55`（`skipOccurrence` → `PutDataMapRequest.create(SKIP_NOTIFICATION_PATH)` + `skip_*` 字段） | 已存在 |
| Phone 接收 | `app/src/main/java/io/github/yingqiu0871/evolune/wear/WearAppDataLayer.kt:125`（`class WearAppListenerService : WearableListenerService()`；`onDataChanged` 在 `:145`，skip 分派 `uri.path == SKIP_NOTIFICATION_PATH` 在 `:153`） | 已存在 |
| Phone 处理体 | 同文件 `:181` `private suspend fun processWearAppSkipNotification(...)`（协议版本校验 → 解析 plan/slot/scheduled/notification → occurrence 校验 → `ReminderSkipStore.markSkipped` → `cancelOccurrence` → `cancelNotification` → 删除 DataItem） | 已存在 |

即：**handler 早已写好，缺的只是 manifest 可达性**。

## 2. Old manifest behavior

```
<service android:name=".wear.WearAppListenerService" android:exported="true">
    <intent-filter>                                      <!-- MESSAGE_RECEIVED -->
        <data android:scheme="wear" android:host="*" android:path="/hrt/v1/wear-app/request" />
    </intent-filter>
    <intent-filter>                                      <!-- DATA_CHANGED -->
        <data android:scheme="wear" android:host="*" android:pathPrefix="/hrt/v1/wear-app/commands" />
    </intent-filter>
</service>
```

对 `wear://<node>/hrt/v1/wear-app/skip-notification` 的解析结果：**空集合**（实测，见 §6 失败日志）。

## 3. New manifest behavior（最小 delta，+11 行）

在同一 service 下新增一个 **精确路径** filter：

```
<intent-filter>
    <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
    <data android:scheme="wear" android:host="*" android:path="/hrt/v1/wear-app/skip-notification" />
</intent-filter>
```

- 使用 `android:path`（**精确匹配**），不是 `pathPrefix` —— skip 路径不是命令命名空间，不得顺带扩大监听面。
- 未使用 catch-all；未把其它 `/hrt/*` 路径纳入。
- 未新增/修改任何 path constant、payload key、发送端 payload、handler 解析、skip store、提醒取消语义。

## 4. Protocol / Room 影响声明

| 项 | 结论 | 依据 |
|---|---|---|
| `/hrt/*` path constants | **未改** | diff 仅 `app/src/main/AndroidManifest.xml` |
| Wear 发送端（`wear/.../WearAppDataLayer.kt`） | **未改** | 同上 |
| Phone 接收/处理（`WearAppListenerService`、`ReminderSkipStore`、`ReminderManager`） | **未改** | 同上 |
| `PROTOCOL_VERSION` / payload 字段 | **未改** | 同上 |
| Room entity / DAO / schema / migration | **未改** | 无 `data/` 变更；`app/schemas/**` 无 diff |
| PK / Widget / History / Timeline / Insights / Export | **未改** | 无相关文件变更 |
| version metadata / dependencies | **未改** | 无 `build.gradle.kts`、`libs.versions.toml` 变更 |
| authoritative skip fact | **未创建** | skip 仍只写短期 `ReminderSkipStore`（SharedPreferences，48h 过期），不进 Room |

## 5. Tests added

新增 `app/src/androidTest/java/io/github/yingqiu0871/evolune/wear/WearAppListenerReachabilityTest.kt`
（instrumented，通过 `PackageManager.queryIntentServices` 断言 **merged manifest** 的实际可达性，不做脆弱字符串 grep）：

| # | 用例 | 断言 | 旧状态 | 修复后 |
|---|---|---|---|---|
| 1 | `skipNotificationDataChangedPathResolvesToTheWearAppListener` | `DATA_CHANGED` + `wear://*/hrt/v1/wear-app/skip-notification` 必须解析到 `WearAppListenerService` | **FAIL**（resolved = `[]`） | PASS |
| 2 | `snapshotRequestMessagePathStillResolvesToTheWearAppListener` | `MESSAGE_RECEIVED` + `/hrt/v1/wear-app/request` 仍可达 | PASS | PASS |
| 3 | `wearAppCommandPrefixStillResolvesToTheWearAppListener` | `DATA_CHANGED` + `/hrt/v1/wear-app/commands/<opId>` 仍可达 | PASS | PASS |
| 4 | `unrelatedWearAppPathsDoNotResolveToTheWearAppListener` | `/hrt/v1/wear-app/snapshot`、`/hrt/v1/wear-app/skip-notification-extra`、`/hrt/dose-actions/<id>` **不得**被该 listener 捕获（防过宽 filter） | PASS | PASS |

Test 1 在旧 manifest 上确实失败、在修复后通过 —— 满足"失败于旧状态、成功于修复状态"。

## 6. Fresh test results（原始日志已随本 commit 固化）

| 验证 | 命令（摘要） | 结果 | 原始日志 |
|---|---|---|---|
| 定向 instrumentation（**修复前**，期望失败） | `:app:connectedDebugAndroidTest -P…class=…WearAppListenerReachabilityTest` | `Finished 4 tests`，**1 failure**（Test 1，`Resolved: []`） | `evidence/pre-a-01/pre-a-01-test-before-fix.log`（SHA-256 `ae977b79…`） |
| 定向 instrumentation（**修复后**） | 同上 | `Starting 4 tests` → `Finished 4 tests`，`BUILD SUCCESSFUL in 24s` | `evidence/pre-a-01/pre-a-01-test-after-fix.log`（`8e186ba5…`） |
| app instrumentation **全量** | `:app:connectedDebugAndroidTest` | JUnit XML：**tests=212 / failures=0 / errors=0 / skipped=5**（基线 208 + 新增 4），`BUILD SUCCESSFUL in 4m 4s`，设备 `Pixel_7(AVD) - 15`（API 35） | `evidence/pre-a-01/pre-a-01-androidtest-full.log`（`7dd27e7d…`）、`evidence/pre-a-01/connected-app-pixel7-full-suite.xml`（`19aca9f0…`） |
| JVM 测试任务（fresh，`--rerun-tasks`） | `:experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest` | **三个 JVM test task 执行成功 + `BUILD SUCCESSFUL in 1m 12s`**；该日志**不含** per-test counts，PRE-A-01 **没有**重新捕获逐测试计数证据 → **不得**把 863 当作 PRE-A-01 新证据 | `evidence/pre-a-01/pre-a-01-jvm-build.log`（`f84a638f…`） |
| Debug 构建 | `:app:assembleDebug :wear:assembleDebug`（同一次运行） | **app assembleDebug successful**；**wear assembleDebug successful**；`86 actionable tasks: 86 executed`（构建日志未记录 APK 字节大小，故不引用尺寸数字） | 同上 |
| `git diff --check` | — | 退出码 0 | — |

未使用 Phone/Wear 配对模拟器做端到端 skip 投递（本轮不新建复杂 e2e 框架）。

### 6.1 测试计数的证据归属（口径）

- **app instrumentation**：计数来自 `evidence/pre-a-01/connected-app-pixel7-full-suite.xml` 的 `testsuite` 属性（tests=212 / failures=0 / errors=0 / skipped=5），属 PRE-A-01 新鲜证据。
- **定向 reachability 测试**：计数来自两份运行日志（修复前 4 tests/1 failure；修复后 4 tests/0 failure）。
- **JVM**：PRE-A-01 只执行了三个 JVM test task 并确认成功，**没有**保存逐测试计数的 JUnit 证据；
  因此 PRE-A-01 **不主张** 863 这一数字。
  最近一次**有完整逐测试证据**的 JVM 基线是 **Phase-0**：app 689 + experience-core 84 + wear 90 = **863 / 0 failures / 0 errors / 0 skipped**
  （证据见 `docs/evolune/v1.7/V17_BASELINE_EVIDENCE.md` 与 `evidence/junit/**`）。
- 本轮的改动面是 manifest 与 Android instrumentation 测试，**未触及 JVM 生产/测试语义**，故不重复捕获 JVM 逐测试证据是刻意的范围控制。

### 6.2 本阶段的 evidence manifest

PRE-A-01 的证据清单与自校验：`docs/evolune/v1.7/evidence/pre-a-01/MANIFEST.sha256`
（覆盖该目录全部 **5** 个 data file，manifest 自身 SHA-256 = `735817fe5b929ace6c9d461692c9529d54617cc28c7d00794bf5d11f65e64669`）。

```bash
cd docs/evolune/v1.7/evidence/pre-a-01
expected=$(find . -type f ! -name 'MANIFEST.sha256' | wc -l)   # 5
listed=$(wc -l < MANIFEST.sha256)                              # 5
[ "$expected" = "$listed" ] || { echo "COVERAGE MISMATCH"; exit 1; }
sha256sum -c MANIFEST.sha256                                   # → 5 行全部 OK
```

Phase-0 的 `evidence/MANIFEST.sha256`（111 条目）**不**覆盖本目录；其 scope 说明见
[V17_BASELINE_EVIDENCE.md §6.0](V17_BASELINE_EVIDENCE.md)。

## 7. Known remaining UX limitation（P2，不在本轮范围）

Wear 侧 `skipOccurrence(...)` 的返回值只代表 **本地 DataItem 入队成功**
（`Wearable.getDataClient(...).putDataItem(request)` 的返回值未被检查，函数在构造成功后即返回 `true`），
并不代表 Phone 已实际完成 skip。因此"已发送跳过请求"文案在 Phone 侧校验失败（计划已删除、occurrence 不匹配等）时仍可能误导。

- 本轮**不修**：修文案或状态机会触及 Wear 状态机与协议语义，超出 reachability 范围。
- 建议后续作为独立 P2 UX item：把"已发送"改为"已发送，等待手机确认"或引入结果回投。

## 8. 边界与未触碰清单

允许并已修改：
- `app/src/main/AndroidManifest.xml`（+11 行）；
- `app/src/androidTest/java/io/github/yingqiu0871/evolune/wear/WearAppListenerReachabilityTest.kt`（新增回归测试）；
- `docs/evolune/v1.7/evidence/.gitattributes`（在既有 `* -text` 基础上追加 `-whitespace`：原始日志属采集产物，其尾随空格是记录字节的一部分，不应触发 `git diff --check`；**日志字节未改动**，SHA-256 不变）；
- 本文档与 `docs/evolune/v1.7/evidence/pre-a-01/`（原始日志与 XML）。

未触碰：`/hrt/*` 常量、Wear 发送协议、handler 解析、`ReminderSkipStore` 语义、occurrence matching、PK、Widget、History、Timeline、Insights、Export、Room/schema/migration、版本元数据、依赖、`.gitignore`。
未 push、未 tag、未 merge。
