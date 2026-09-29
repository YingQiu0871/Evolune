# Evolune 发布流程 / Release Process

签名密码只从环境变量读取（`EVOLUNE_KEYSTORE_PATH`、`EVOLUNE_KEYSTORE_PASSWORD`、`EVOLUNE_KEY_ALIAS`、
`EVOLUNE_KEY_PASSWORD`），脚本不会输出或写入它们。

1. **合并**：功能分支经 PR 以普通 merge commit 合入 `main`，CI（`Build Debug APK`）通过。
2. **设备测试**：在标准 AVD 上跑 `:app:connectedDebugAndroidTest`，见 [TESTING.md](../evolune/TESTING.md)。
3. **构建并验证**：`.\scripts\release_verify.ps1`（版本号默认取自根 `build.gradle.kts`）。它会签名构建 Phone/Wear
   Release、用 `apksigner` 核对签名与证书指纹（不匹配则失败）、把 APK 按发布名
   `Evolune-Phone-v<version>.apk` / `Evolune-Wear-v<version>.apk` 复制到
   `release-artifacts\v<version>-rc\<时间戳>\`，并生成 `SHA256SUMS.txt`（LF、无 BOM）。
4. **发布**：先空跑 `.\scripts\release_publish.ps1 -ArtifactDir <上面的目录> -Version <version> -Commit <main 合并提交>`，
   确认计划后加 `-Publish`，并输入版本号确认。脚本会复核哈希、创建 annotated tag 并只推送该 tag、
   创建 GitHub Release 上传三个资产，再下载回读比对哈希与签名。已存在的 tag / Release 会直接拒绝，不会移动或覆盖。
5. **文档**：见下节。

## 哈希写在哪里 / Where hashes live

tag、合并提交与 APK 哈希只有发布后才确定，因此**不再**在 `CURRENT_STATUS`、`ROADMAP` 等状态文档里逐份回填。
以 GitHub Release 页面和 Release 附带的 `SHA256SUMS.txt` 为准；状态文档只写版本号、日期与 Release 链接。
发布说明（`docs/evolune/v<version>/`）可以记录验证过程，但不是哈希的唯一权威来源。
已封存版本（v1.9.1 及以前）里已有的哈希保持不变。
