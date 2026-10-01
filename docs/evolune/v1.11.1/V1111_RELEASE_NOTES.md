# Evolune v1.11.1 正式版 / Official Release

Evolune v1.11.1 是一个修复版本：添加用药记录后，「记录」页会自动滚动到新记录。
Evolune v1.11.1 is a fix release: after adding a dose record, the Records page scrolls to the new record.

## 修复 / Fix

- 「记录」页的列表按记录标识锚定当前可见项，新记录插在其上方时不会自己露出来：停在列表顶部时新记录落在顶部标题栏之后，需要手动下拉才能看到。现在恰好新增一条记录时，列表会自动滚动到这条记录——新记录通常回到顶部，补录较早时间的记录则滚到它所在的位置。首次进入页面和批量导入多条记录时不滚动。/ The Records list anchors on the keyed first visible item, so a record inserted above it stayed out of view: at the top of the list the new row ended up behind the top bar and had to be pulled down by hand. When exactly one record is added the list now scrolls to it — the top for a new dose, its own position for a back-dated one. The first composition and multi-record imports do not scroll.

## 兼容性 / Compatibility

- 无 Room 数据库 schema 迁移；无备份或 Portable 格式变化；PK 数值代码与用药语义不变；应用 ID 与签名证书连续。/ No Room schema migration, no backup or Portable format change, PK numerics and medication semantics unchanged, application ID and signing continuity unchanged.
- 版本信息：Phone `1.11.1` — versionCode `101110100`；Wear `1.11.1` — versionCode `1101110100`。/ Version: Phone `1.11.1` / `101110100`; Wear `1.11.1` / `1101110100`.

## 验证状态 / Verification status

（发布前补充 / to be completed before publication）
