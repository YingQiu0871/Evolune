# Evolune v1.7 — 基线证据清单（V17_BASELINE_EVIDENCE）

> 状态：`PHASE-0 CORRECTION APPLIED`（2026-09-13）
> **Product/code baseline**：`main` @ `72a468cc929e3967e7be297cd0b645a0c0fb03df`（= `origin/main`）
> **Phase-0 freeze documentation commit**：包含本目录 `docs/evolune/v1.7/*` 的 docs-only commit
> （**不是**产品实现基线；同一 commit 无法自引用自身 SHA，故该值不写入本文件，见 Phase-0 correction report）
> 封版稳定版：`v1.6.0`（`main` HEAD 本身不是任何 tag 的指向对象）
>
> 本文件的目标：checkout 本仓库后，任何复审者都能独立得到
> **(1) 当时运行了什么；(2) 数字从哪里得出；(3) 对应原始 artifact 的哈希是什么；(4) 如何重新执行得到新的 fresh evidence。**

---

## 1. 身份

| 项 | 值 | 命令 |
|---|---|---|
| 分支 | `main` | `git rev-parse --abbrev-ref HEAD` |
| Product/code baseline SHA | `72a468cc929e3967e7be297cd0b645a0c0fb03df` | `git rev-parse HEAD` |
| `origin/main` SHA | `72a468cc929e3967e7be297cd0b645a0c0fb03df` | `git rev-parse origin/main` |
| HEAD 提交时间 | 2026-09-13 14:07:34 +0200 | `git log -1 --format='%H %ci'` |
| HEAD 是否 tag 指向 | 否（`v1.6.0` 指向更早的发布提交） | `git describe --tags --exact-match` |
| 上次封版发布 | `v1.6.0` | `git tag` |

## 2. 环境

| 项 | 值 | 来源 |
|---|---|---|
| OS | Windows 11 10.0 amd64（Git Bash / MSYS 环境） | `./gradlew --version` |
| JDK（运行 Gradle 与测试） | Eclipse Temurin **17.0.19+10**（`openjdk version "17.0.19" 2026-04-21`） | `java -version` |
| Gradle | **9.2.1**（wrapper；`distributionSha256Sum=72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f`） | `gradle/wrapper/gradle-wrapper.properties`、`./gradlew --version` |
| Android Gradle Plugin | 9.0.1 | `gradle/libs.versions.toml:2` |
| Kotlin（项目插件） | 2.3.10 | `gradle/libs.versions.toml:10` |
| Android platform-tools | 37.0.1（`Android Debug Bridge version 1.0.41`） | `adb version` |
| Phone 设备 | `Pixel_7` AVD，`Pixel_7(AVD) - 15`，`sdk_gphone64_x86_64`，**API 35** | `adb shell getprop` |
| Wear 设备 | `Wear_OS_Large_Round` AVD，`Wear_OS_Large_Round(AVD) - 17`，`sdk_gwear_x86_64`，**API 37**，`emulator,nosdcard,watch` | `adb shell getprop` |

> `./gradlew --version` 报告的 `Kotlin: 2.2.20` 是 **Gradle 自带 Kotlin**，不是项目编译用的 Kotlin 插件版本（后者见 version catalog）。

## 3. 运行的命令（原文，逐字）

```bash
# JVM fresh（强制重跑，非 up-to-date）
./gradlew validateEvoluneIdentityAndVersioning :experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest \
  --no-daemon --console=plain --rerun-tasks

# Phone instrumentation（run 2；run 1 见 §10 已知限制）
./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain

# Wear instrumentation
./gradlew :wear:connectedDebugAndroidTest --no-daemon --console=plain
```

## 4. 计数从哪来（**关键**）

**计数权威来源 = JUnit XML**。`testsuite` 元素属性求和，逐文件哈希见 §6：

```bash
grep -h -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' <dir>/*.xml \
  | awk -F'"' '{t+=$2; sk+=$4; f+=$6; e+=$8} END {printf "tests=%d skipped=%d failures=%d errors=%d\n", t, sk, f, e}'
```

| 验证 | XML 源目录（仓库相对路径） | 文件数 | tests | failures | errors | skipped |
|---|---|---:|---:|---:|---:|---:|
| JVM `:app:testDebugUnitTest` | `app/build/test-results/testDebugUnitTest/` | 80 | **689** | 0 | 0 | 0 |
| JVM `:experience-core:test` | `experience-core/build/test-results/test/` | 9 | **84** | 0 | 0 | 0 |
| JVM `:wear:testDebugUnitTest` | `wear/build/test-results/testDebugUnitTest/` | 11 | **90** | 0 | 0 | 0 |
| **JVM 合计** | — | **100** | **863** | **0** | **0** | **0** |
| Phone instrumentation | `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_7(AVD) - 15-_app-.xml` | 1 | **208** | 0 | 0 | **5** |
| Wear instrumentation | `wear/build/outputs/androidTest-results/connected/debug/TEST-Wear_OS_Large_Round(AVD) - 17-_wear-.xml` | 1 | **5** | 0 | 0 | **1** |

**明确声明**：
- 原始日志 `logs/jvm-baseline-fresh.log` **不包含** test counts，它只证明**命令、任务执行与构建结果**（`BUILD SUCCESSFUL in 1m 34s`、`55 actionable tasks: 55 executed`）。
- UTP 控制台曾打印 Phone `Finished 213 tests`、Wear `Finished 6 tests`；**该差异不是 retry 证据**，本清单以 JUnit XML 为准（208 / 5）。
- Phone run 1 的 UTP 输出为 `Starting 0 tests` / `Finished 0 tests`（安装阶段失败，见 §10）。

## 5. 原始产物路径与 SHA-256

原始产物生成在**仓库之外**（`D:\Evolune-Workspace\temp\v17-phase0\logs\`）与**构建目录内**（`*/build/...`，被 `.gitignore` 忽略）。
为使证据可离线核验，本轮把**原始文件按字节复制**到本目录 `evidence/`（未做任何转码、裁剪或重写）：

| 类型 | 原始路径 | 仓库内副本 | SHA-256 |
|---|---|---|---|
| JVM fresh 日志 | `<temp>/logs/jvm-baseline-fresh.log` | `evidence/logs/jvm-baseline-fresh.log` | `125b0a2f15ceada7d3e1157217851da0faf0006d651da066a53642c1c3968c6a` |
| Phone instrumentation run 1 | `<temp>/logs/androidtest-app-pixel7.log` | `evidence/logs/androidtest-app-pixel7.log` | `5f5ac3a368e99cb4cdfa64354cb8d731e05ded489188baac7a829330fd435136` |
| Phone instrumentation run 2 | `<temp>/logs/androidtest-app-pixel7-rerun.log` | `evidence/logs/androidtest-app-pixel7-rerun.log` | `a1e4a2037b264fab0ed49cd6f0c5d40bea11eccd4c1e82f04d914717545e0815` |
| Wear instrumentation | `<temp>/logs/androidtest-wear.log` | `evidence/logs/androidtest-wear.log` | `e0bf98225916f3510d8e6152b1bd11d7a7dbc72af457199a042c2023677d8115` |
| Phone 模拟器启动日志 | `<temp>/logs/emulator-pixel7.log` | `evidence/logs/emulator-pixel7.log` | `148a68ff6c464838165a034e40e09de7c45342da3486db0b74001406a6d977e8` |
| Wear 模拟器启动日志 | `<temp>/logs/emulator-wear.log` | `evidence/logs/emulator-wear.log` | `4346390bd32c5cd7697e29d35454aa54e4982ab2a135dfe7ba34bc01e6d897ab` |
| Phone instrumentation 结果（JUnit XML，原始） | `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_7(AVD) - 15-_app-.xml` | `evidence/connected/TEST-Pixel_7(AVD) - 15-_app-.xml` | `b5c6a2a568b7afb44a2a45626ec6e8752c8d98ec28a1e601e061e3ca306ab877` |
| Wear instrumentation 结果（JUnit XML，原始） | `wear/build/outputs/androidTest-results/connected/debug/TEST-Wear_OS_Large_Round(AVD) - 17-_wear-.xml` | `evidence/connected/TEST-Wear_OS_Large_Round(AVD) - 17-_wear-.xml` | `d71f926fbf185e507aea191c1c36fc64bb84fda5ba1e7c59af83abaf83966107` |

`<temp>` = `D:\Evolune-Workspace\temp\v17-phase0\logs`（**不在仓库内**，不随仓库分发；对应副本已随本 commit 固化）。

## 6. deterministic SHA-256 manifest

### 6.0 Scope（阶段化 manifest 契约）

`evidence/MANIFEST.sha256` 的 scope 是 **Phase-0 frozen evidence set** ——
即 Phase-0 固化时由 6 个日志、100 个 JVM JUnit XML、2 个 instrumentation XML 与 3 个 worktree 快照组成的集合。
它**不覆盖**后续阶段新加入 evidence tree 的文件（例如 PRE-A-01 的 `evidence/pre-a-01/`），
也不应被理解为"整个 evidence tree 的当前状态清单"。

约定：**每个阶段在自有目录内维护自己的 manifest**，覆盖该阶段的全部 data evidence：

| 阶段 | manifest | 覆盖集合 |
|---|---|---|
| Phase-0 | `evidence/MANIFEST.sha256` | Phase-0 frozen evidence（111 entries） |
| PRE-A-01 | `evidence/pre-a-01/MANIFEST.sha256` | PRE-A-01 data evidence（5 entries，见 [V17_PRE_A_01_WEAR_SKIP.md](V17_PRE_A_01_WEAR_SKIP.md) §6） |

后续阶段必须新建自己的 manifest，**不得**把新文件无限追加进 Phase-0 manifest。

- 文件：`evidence/MANIFEST.sha256`（**111 条目**：6 日志 + 100 JVM XML + 2 instrumentation XML + 3 worktree 快照）
- **manifest 自身 SHA-256**：`f5a77ab8f54ff08fe42017ef599cf7ea898631f7cc5d01fadd96682d84501c80`
- 生成方式（确定性：按路径 `LC_ALL=C sort`，`sha256sum` 两空格分隔，相对 `evidence/`）：

```bash
cd docs/evolune/v1.7/evidence
find . -type f ! -name 'MANIFEST.sha256' | sed 's|^\./||' | LC_ALL=C sort \
  | while IFS= read -r f; do printf "%s  %s\n" "$(sha256sum "$f" | cut -d' ' -f1)" "$f"; done > MANIFEST.sha256
```

- 校验方式与结果：

```bash
cd docs/evolune/v1.7/evidence && sha256sum -c MANIFEST.sha256   # → 111 行全部 OK
```

- 分组哈希（对组内 manifest 行求 SHA-256，便于快速比对）：

| 分组 | 文件数 | 组哈希（grep 组内行后 sha256sum） |
|---|---:|---|
| `junit/app/` | 80 | `a4af6918c1d038c2a3ad90f5061262248daa1c6a4229e71bbcbb9499a612188e` |
| `junit/experience-core/` | 9 | `eb7006896b6ffeb637f1fa44f339ba31af2bc3554ac5d125674ab90058dc7172` |
| `junit/wear/` | 11 | `06011e9bb87499c01de97fa1febcb85af24c3fcfb6ae48eb6510198827285d45` |
| `logs/` | 6 | `a3f9b53e73a6a33dca6fae70d07c8f04d88aac2df3d9755d70c87855829047e0` |
| `connected/` | 2 | `503be48c20749374469951814b35a35dd6f7648f289339a80e1f6df6cb841766` |
| `worktree/` | 3 | `fa5e6748ac52880df1c6f4f603a83d2816a1dee3f95d7fc01f8b385f3eb749ed` |

- **Phase-0 frozen snapshot**（冻结时记录，作为历史事实保留，不代表当前整棵树）：
  `docs/evolune/v1.7/evidence/` 下实际文件数 **113**，合计 **361,766 B**；manifest 覆盖其中 **111** 个证据文件。
- **当前 evidence tree（2026-09-13 PRE-A-01 之后）**：新增 `evidence/pre-a-01/` 子目录（5 个 data file + 1 个自有 manifest），
  其覆盖与校验见该目录的 `MANIFEST.sha256`；本文件的 111 条目清单与哈希**未**随之变化。

### 6.1 字节级存储保证（Git EOL 不转换 ＋ 原始产物免 whitespace lint）

仓库在 Windows 上启用 `core.autocrlf`，默认会把文本文件规范化（LF↔CRLF），
那将使本目录的 SHA-256 在 checkout 后**不可复现**。因此本目录设有
`evidence/.gitattributes`，**当前内容**为：

```
* -text -whitespace
```

- `-text`：不做 EOL 转换，保证字节可复现；
- `-whitespace`：原始采集产物（构建/测试日志等）自带的尾随空格是其记录字节的一部分，
  不应被判为 whitespace error 而让 `git diff --check` 失败。**日志字节未被修改**，SHA-256 不变。

该文件在 PRE-A-01 阶段由 `* -text` 更新为 `* -text -whitespace`（历史事实：Phase-0 冻结时仅为 `* -text`）。

作用范围仅限 `docs/evolune/v1.7/evidence/**`（未改动仓库根或其它目录的 Git 行为）。核验方式：

```bash
# ① 工作区字节校验
cd docs/evolune/v1.7/evidence && sha256sum -c MANIFEST.sha256     # → 111 行全部 OK

# ② 索引（即将提交的 blob）字节校验：应无任何 MISMATCH 输出
while IFS= read -r line; do
  h="${line%%  *}"; f="${line#*  }"
  b=$(git cat-file blob ":docs/evolune/v1.7/evidence/$f" | sha256sum | cut -d' ' -f1)
  [ "$b" = "$h" ] || echo "MISMATCH $f"
done < MANIFEST.sha256

# ③ 转换属性检查：每行应为 i/* 与 w/* 一致且 attr/-text
git ls-files --eol docs/evolune/v1.7/evidence
```

**Phase-0 冻结时**的核验结果（历史事实）：111 个 blob 的 SHA-256 与 `MANIFEST.sha256` 全部一致（0 处不匹配）；
`git ls-files --eol` 当时显示 102 个 `i/crlf w/crlf`、5 个 `i/lf w/lf`、6 个 `i/mixed w/mixed`。
该 EOL 分布描述的是 **Phase-0 frozen snapshot**，不是当前整棵 evidence tree 的状态
（PRE-A-01 之后目录内新增了 `pre-a-01/` 文件；如需当前分布请重新执行 `git ls-files --eol docs/evolune/v1.7/evidence`）。

> 说明：`-text` 只作用于本证据目录；Phase-0 的 `.md` 文档仍是普通文本文件，Git 的 EOL 规范化不影响其内容语义，
> 也不影响本清单中的任何哈希声明。

## 7. 工作树快照（`git status --porcelain` 原文，freeze commit **之前**）

完整、未删节的原文快照（964 行）见 **`evidence/worktree/git-status-porcelain.pre-commit.txt`**；
同目录另有 `git-diff-check.pre-commit.txt` 与 `git-status-source-boundary.pre-commit.txt`（源码边界核验）。
摘要（文件头尾形态）：

```
 M .idea/.name
 M .idea/compiler.xml
 M .idea/deploymentTargetSelector.xml
 M .idea/gradle.xml
 M .idea/misc.xml
 M .idea/vcs.xml
?? .kotlin/
?? .tmp-app-tasks.txt
?? docs/evolune/v1.6/…（954 个原始证据文件，逐行列出，见上述快照文件）
?? docs/evolune/v1.7/
?? release-artifacts/
```

计量：

| 指标 | 值 | 命令 |
|---|---:|---|
| porcelain 行数 | 964 | `git status --porcelain \| wc -l` |
| 未跟踪条目 | 958 | `git status --porcelain \| grep -c '^??'` |
| tracked 修改 | 6（全部 `.idea/*`） | `git status --porcelain \| grep -c '^ M'` |
| 未跟踪实际文件数 | 1028 | `git ls-files --others --exclude-standard \| wc -l` |
| porcelain 忽略条目 | 37 | `git status --porcelain --ignored \| grep -c '^!!'` |

> **当前仓库不是 clean working tree。** 分组说明：
> ① 6 个 tracked `.idea/*` 修改（**本轮未 stage、未修改**）；
> ② `docs/evolune/v1.7/`（本次 Phase-0 产物，**唯一被 stage 的内容**）；
> ③ `docs/evolune/v1.6/` 954 个旧 v1.6 原始证据（未跟踪，**不提交**）；
> ④ `release-artifacts/`（未跟踪 51 文件 + 21 个被忽略 APK，**不提交**）；
> ⑤ `.kotlin/`、`.tmp-app-tasks.txt`（构建/临时产物，**不提交**；H1 忽略规则已批准但延后到 hygiene commit）。
>
> **freeze commit 之后的差异**：仅 `?? docs/evolune/v1.7/` 一行消失（其内容成为已提交文件），其余各行不变。

## 8. 边界确认（Phase-0 correction 轮）

| 检查 | 结果 |
|---|---|
| `git diff --check` | **退出码 0**（无空白/补丁错误） |
| `app/`、`wear/`、`experience-core/` 源码与测试改动 | **0 行**（`git status --porcelain -- app wear experience-core \| wc -l` = 0） |
| `scripts/`、`build.gradle.kts`、`settings.gradle.kts`、`gradle/`、`gradle.properties` | **0 行** |
| 版本元数据（`app/build.gradle.kts`、`wear/build.gradle.kts`、`gradle/libs.versions.toml`） | **0 行** |
| Room schema / migration / JSON v1 / Wear `/hrt/*` / PK 参数 | 未触碰 |
| `.gitignore` | 未修改（H1 延后） |

## 9. 如何重新执行得到新的 fresh evidence

1. **JVM**：`./gradlew :experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --no-daemon --console=plain --rerun-tasks`
   → 计数从 `*/build/test-results/**/*.xml` 用 §4 的聚合命令得出（**不要**从控制台日志读计数）。
2. **Phone instrumentation**：启动 `Pixel_7` AVD（API 35）→ `./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain`
   → 计数从 `app/build/outputs/androidTest-results/connected/debug/*.xml` 读取。
3. **Wear instrumentation**：启动 `Wear_OS_Large_Round` AVD（API 37）→ `./gradlew :wear:connectedDebugAndroidTest --no-daemon --console=plain`
   → 计数从 `wear/build/outputs/androidTest-results/connected/debug/*.xml` 读取。
4. **证据固化**：把原始日志与上述 XML **按字节复制**到新目录，重新生成 manifest（§6 命令），并记录 manifest 自身 SHA-256。
5. **对照**：本清单的 863 / 208+5 / 5+1 应可逐项复现；若被改动，应作为新的基线记录，而不是覆盖本文件。

## 10. 已知限制（如实声明）

1. **Phone run 1 失败**：`INSTALL_FAILED_UPDATE_INCOMPATIBLE`（`evidence/logs/androidtest-app-pixel7.log:103`），发生在 **test execution 之前**；
   run 2 成功执行完整 suite。**repository / source / build script 没有为该错误做任何改动**；
   两次运行之间的**具体 device remediation 未被 evidence capture**，因此**不能从日志独立证明**当时执行过什么设备命令。
2. **Lint / 静态检查**：本轮未执行（v1.6 门禁已覆盖；本轮无源码改动）。
3. **Wear Data Layer probe**：`WearDataLayerDeviceProbeTest#requestPlansFromPhoneReceivesAnAuthoritativeDashboard` **被跳过**（需与 Phone 配对，本轮无配对环境）。
4. **跳过的 5 个 Phone 用例**为既有 assumption/fixture/折叠屏条件依赖，非失败；名单见基线审计 §10.2。
5. **模拟器状态不具确定性**：AVD 数据、时区、系统时钟等不随本证据固化；重跑时环境差异可能导致 instrumentation 结果变化（v1.5 时期曾出现 15 个 `ComposeTimeoutException`，本轮为 0）。
6. **本清单不包含** `.idea/*`、`.kotlin/*`、旧 `docs/evolune/v1.6/` 原始证据、APK、产品/测试源码。
