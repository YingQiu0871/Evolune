# Evolune v1.11.1 正式版 / Official Release

Evolune v1.11.1 是一个修复版本：添加用药记录后，「记录」页会自动滚动到新记录。
Evolune v1.11.1 is a fix release: after adding a dose record, the Records page scrolls to the new record.

## 修复 / Fix

- 「记录」页的列表按记录标识锚定当前可见项，新记录插在其上方时不会自己露出来：停在列表顶部时新记录落在顶部标题栏之后，需要手动下拉才能看到。现在恰好新增一条记录时，列表会自动滚动到这条记录——新记录通常回到顶部，补录较早时间的记录则滚到它所在的位置。首次进入页面和批量导入多条记录时不滚动。/ The Records list anchors on the keyed first visible item, so a record inserted above it stayed out of view: at the top of the list the new row ended up behind the top bar and had to be pulled down by hand. When exactly one record is added the list now scrolls to it — the top for a new dose, its own position for a back-dated one. The first composition and multi-record imports do not scroll.

## 兼容性 / Compatibility

- 无 Room 数据库 schema 迁移；无备份或 Portable 格式变化；PK 数值代码与用药语义不变；应用 ID 与签名证书连续。/ No Room schema migration, no backup or Portable format change, PK numerics and medication semantics unchanged, application ID and signing continuity unchanged.
- 版本信息：Phone `1.11.1` — versionCode `101110100`；Wear `1.11.1` — versionCode `1101110100`。/ Version: Phone `1.11.1` / `101110100`; Wear `1.11.1` / `1101110100`.

## 验证状态 / Verification status

- **CI #105（`3aadea0`，修复提交）通过**：`Build Debug APK` 工作流（`./gradlew test`、`:app:assembleDebug`、`:wear:assembleDebug`）。发布准备提交仅改版本号与文档，其 CI 结果见 PR #39。/ CI run #105 (`3aadea0`, the fix commit) passed. The release-preparation commits only change the version and documentation; their CI result is on PR #39.
- **JVM**：`./gradlew test`，app 1,461 个测试，0 失败。/ JVM: `./gradlew test`, 1,461 app tests, 0 failures.
- **androidTest（2026-10-01，本机 Pixel_7 AVD，Android 15 / API 35，zh-CN、1080×2400，Debug 构建，修复提交 `3aadea0`）**：`:app:connectedDebugAndroidTest` **396 个测试，0 失败，0 错误，5 跳过**（条件门控）。发布准备相对该提交没有代码变化。/ androidTest on the fix commit `3aadea0`: 396 tests, 0 failures, 0 errors, 5 condition-gated skips. The release preparation changes no code relative to that commit.
- **问题复现与修复确认（模拟器）**：在 v1.11.0 发布 APK 上复现——停在列表顶部一键添加后，新记录位于 y=170（顶部标题栏之后），列表仍锚定原第一条。修复后的构建中，新记录出现在第一个可见位置（y=391）；滚动到较早日期后再添加，列表自动回到顶部并显示新记录。/ Reproduced on the v1.11.0 release APK (emulator): after a quick-add at the top of the list the new row sat at y=170, behind the top bar. With the fix the new row is at the first visible position (y=391); adding while scrolled to older dates returns the list to the top and shows the new row.
- **签名 Release 构建（2026-10-01，所有者本机，候选提交 `640c310`）**：`scripts/release_verify.ps1` 签名构建 Phone/Wear Release（含 R8）成功；versionName 1.11.1，versionCode 101110100 / 1101110100，两个 APK 的签名证书 SHA-256 均为 `b9b6b955…aab08`。哈希以 Release 附带的 `SHA256SUMS.txt` 为准。合并到 `main` 后相对该提交仅有文档变化，因此发布的就是这两个 APK。/ Signed Release build from candidate `640c310` (R8): version 1.11.1, 101110100 / 1101110100, release certificate matches. Hashes: see the attached `SHA256SUMS.txt`. Only documentation differs after merging, so these APKs are published without a rebuild.
- **模拟器冒烟（发布 APK 本身）**：在已安装 v1.11.0 正式版（保留数据）的 Pixel_7 AVD 上 `adb install -r` 覆盖升级到 1.11.1，首次安装时间与已有记录保留；在 R8 构建上重复上述两种添加场景，新记录均出现在第一个可见位置；主页、历史、设置可正常打开；无 Evolune 的 `FATAL EXCEPTION` 或 ANR。/ Emulator smoke on the release APK: in-place upgrade from v1.11.0 with data retained; both add scenarios repeated on the R8 build, the new row is at the first visible position each time; Home, History and Settings open; no fatal exception or ANR.
- **未覆盖**：补录较早时间记录时滚动到其所在位置的场景未单独在设备上演示（逻辑与新增到顶部相同，按新增记录的下标滚动）；真机安装；Google Drive 备份/恢复；Phone↔Wear 配对同步；Wear 发布 APK 安装冒烟（本次无 Wear 模块改动）。/ Not covered: the back-dated record case was not separately exercised on a device (same logic: scroll to the added record's index); real-device installation; Google Drive backup/restore; paired Phone↔Wear sync; Wear release APK install smoke (no Wear module changes).
