# Evolune 发布流程 / Release Process

签名密码只从环境变量读取（`EVOLUNE_KEYSTORE_PATH`、`EVOLUNE_KEYSTORE_PASSWORD`、`EVOLUNE_KEY_ALIAS`、
`EVOLUNE_KEY_PASSWORD`），脚本不会输出或写入它们。

两个发布脚本（`scripts/release_verify.ps1`、`scripts/release_publish.ps1`，自 v1.11.0 起）都以 `#Requires -Version 7.0`
要求 PowerShell 7（`pwsh`），在 Windows PowerShell 5.1 下会直接拒绝运行（5.1 会把 `gh`、`git push`
写到 stderr 的正常输出当成终止性错误，可能让发布中途停下）。调用方式例如
`pwsh -NoProfile -File .\scripts\release_publish.ps1 ...`。

1. **合并**：功能分支经 PR 以普通 merge commit 合入 `main`，CI（`Build Debug APK`）通过。
2. **设备测试**：在标准 AVD 上跑 `:app:connectedDebugAndroidTest`，见 [TESTING.md](../evolune/TESTING.md)。
3. **构建并验证**：`.\scripts\release_verify.ps1`（版本号默认取自根 `build.gradle.kts`）。它会签名构建 Phone/Wear
   Release、用 `apksigner` 核对签名与证书指纹（不匹配则失败）、把 APK 按发布名
   `Evolune-Phone-v<version>.apk` / `Evolune-Wear-v<version>.apk` 复制到
   `release-artifacts\v<version>-rc\<时间戳>\`，并生成 `SHA256SUMS.txt`（LF、无 BOM）。
4. **发布**：先空跑 `.\scripts\release_publish.ps1 -ArtifactDir <上面的目录> -Version <version> -Commit <main 合并提交>`，
   确认计划后加 `-Publish`，并输入版本号确认。可选 `-NotesFile <发布说明.md>` 作为 Release 正文。
   目标仓库由 `-Repo` 固定（默认 `YingQiu0871/Evolune`，脚本据此设置 `GH_REPO`），避免 `gh` 解析到本地的
   `upstream` 远端；空跑输出的计划会显示目标仓库。脚本会复核哈希、创建 annotated tag 并只推送该 tag、
   创建 GitHub Release 上传三个资产，再下载回读比对哈希与签名。已存在的 tag / Release 会直接拒绝，不会移动或覆盖。
5. **文档**：见下节。

## 云端发布 / Cloud release (GitHub Actions)

除本地脚本外，也可用 `.github/workflows/release.yml` 在云端签名构建并发布，逻辑与上面两个脚本一致（校验完整的发布证书 SHA-256 `B9B6B955…DAAB08`（本地脚本只校验前缀 `b9b6b955`）、生成 `SHA256SUMS.txt`、拒绝已存在的 tag / Release）。v1.12.0 是首个由该云端工作流签名并发布的版本。

- **Secrets**（仓库 Settings → Secrets and variables → Actions）：`EVOLUNE_KEYSTORE_BASE64`（`.jks` 的 `base64 -w0` 输出，单行）、
  `EVOLUNE_KEYSTORE_PASSWORD`、`EVOLUNE_KEY_ALIAS`、`EVOLUNE_KEY_PASSWORD`。缺少任何一个会在构建前失败并指出名称。
- **PR**：修改 `.github/workflows/release.yml`、根或模块 `build.gradle.kts`、`proguard-rules.pro`、`gradle/libs.versions.toml` 的 PR（非 fork）
  只跑构建 + 验证（产物名带 `-pr<编号>`），版本取自 `evoluneVersionName`，绝不发布。
- **构建**：Actions → Release → Run workflow，`version` 填 `evoluneVersionName`（不一致则失败），`publish=false`。
  产物 `evolune-release-v<version>`（两个 APK、`SHA256SUMS.txt`、`verification.txt`）可在运行页下载。
- **发布**：在 `main` 上运行并勾选 `publish=true`。需要已存在 `docs/evolune/v<version>/V<版本号去掉点>_RELEASE_NOTES.md`
  （如 `V1120_RELEASE_NOTES.md`）。workflow 会复核哈希与证书、在构建的提交上创建 annotated tag 并只推送该 tag、
  创建 Release 上传三个资产并下载回读比对哈希。
- **中断恢复**：若 tag 已推送但 Release 没建成，删除可能残留的草稿 Release 后，用同样参数在 `main` 上重跑即可：
  workflow 只在 tag 已指向同一提交时复用它，否则拒绝，绝不移动 tag。
- 本地脚本仍可作为替代路径。

## 哈希写在哪里 / Where hashes live

tag、合并提交与 APK 哈希只有发布后才确定，因此**不再**在 `CURRENT_STATUS`、`ROADMAP` 等状态文档里逐份回填。
以 GitHub Release 页面和 Release 附带的 `SHA256SUMS.txt` 为准；状态文档只写版本号、日期与 Release 链接。
发布说明（`docs/evolune/v<version>/`）可以记录验证过程，但不是哈希的唯一权威来源。
已封存版本（v1.9.1 及以前）里已有的哈希保持不变。
