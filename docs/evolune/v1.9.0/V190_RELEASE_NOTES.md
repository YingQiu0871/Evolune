# Evolune v1.9.0 正式版 / Official Release

Evolune v1.9.0 是一个维护与配置清理版本：改进测试与检出可复现性；移除一个冗余的直接依赖声明（仍由 Compose 传递提供），并移除未使用的 Glance 目录元数据与已过时的 Glance/ActionCallback ProGuard 保留规则。
Evolune v1.9.0 is a maintenance and configuration-hygiene release: better test and checkout reproducibility; one redundant direct dependency declaration was removed (still provided transitively by Compose); unused Glance catalog metadata and obsolete Glance/ActionCallback ProGuard keep rules were removed.

## 主要更新 / Highlights

- 测试与检出可复现性：Windows 检出（CRLF）下测试不再依赖手工的行尾材料化；金色字节夹具在检出时保持确定性。/ Better test and checkout reproducibility: tests no longer depend on manual line-ending materialization on Windows (CRLF) checkouts; canonical golden byte fixtures are deterministic at checkout.
- 依赖与配置清理：移除一个冗余的直接依赖声明（仍由 Compose 传递提供）。/ Dependency and configuration cleanup: one redundant direct dependency declaration removed (still provided transitively by Compose).
- 移除未使用的 Glance 目录元数据与已过时的 Glance/ActionCallback ProGuard 保留规则。/ Removed unused Glance catalog metadata and obsolete Glance/ActionCallback ProGuard keep rules.
- 内部维护性与可靠性改进（不改变现有数据、备份格式与用药计算语义）。/ Internal maintainability and reliability improvements (no changes to your data, backup format, or medication calculation semantics).

## 兼容性 / Compatibility

- 无 Room 数据库 schema 迁移；无备份格式变化；用药计算语义不变。/ No Room database schema migration; no backup format change; medication calculation semantics unchanged.
- 使用同一持久 Evolune 发布证书签名（SHA-256 `b9b6b955…`）。/ Signed with the same persistent Evolune release certificate (SHA-256 `b9b6b955...`).
