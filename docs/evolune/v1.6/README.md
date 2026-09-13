# v1.6 文档导航

当前状态：v1.6.0 已于 2026-09-10 发布。此页于 2026-09-12 补充导航，不重做发布批准。

建议按以下顺序阅读：

1. [发布说明](V16_RELEASE_NOTES.md)：用户可见变更。
2. [最终发布门禁](V16_FINAL_RELEASE_GATE_2026-09-09.md)：最终候选、设备证据组合、负责人验收和局限。
3. [验收记录](V16_ACCEPTANCE.md)：最终矩阵与历史阶段结论。
4. [规格](V16_SPEC.md) 与 [计划](V16_PLAN.md)：顶部的最终差异说明优先于早期单 provider/五样式方案。
5. [审阅方法](V16_REVIEW.md)：质量门槛；不是本次文档维护新执行的测试。

最终 Phone 是四个独立入口，今日完成度并入今日计划；下一次服药的当前 renderer 只读并打开 App。
Wear 有三个新 Tile、保留身份的旧曲线 Tile 和三个 Short Text Complication。

## 过程证据如何阅读

本目录的 A/B/C/D/E/F/G、视觉、配对、覆盖安装和 RC 文件都是特定日期/候选的过程记录。
早期的 REQUEST_CHANGES、OPEN、NOT_RUN 保留原始含义，由后续明确证据决定最终处置，不能统一批量改为 PASS。

公开树目前只包含四份 `.log`；部分早期 Markdown 链接及行内日志/截图路径指向未提交材料。
缺失清单和扫描边界见 [文档盘点](../DOCUMENTATION_REVIEW_V16_2026-09-12.md)。本次没有补造日志、上传设备私有材料或声称独立重跑设备验收。

| 已在 Git 中的日志 | 可核验范围 |
|---|---|
| [最终覆盖安装回读](V16_FINAL_RC_224735_PHYSICAL_OVERLAY_2026-09-09.log) | 两端 APK hashMatch、版本、首次安装时间和 fatalKeywords 结果 |
| [完整 Debug 门禁](V16_P1_SKIP_FIX_FULL_DEBUG_VERIFICATION_2026-09-09.log) | Gradle task 执行与 BUILD SUCCESSFUL；不单独提供完整测试 XML |
| [修复最终验证](V16_P1_P2_SKIP_FIX_FINAL_VERIFICATION_2026-09-09.log) | 对应 Gradle 执行结果 |
| [Phone instrumentation](V16_P1_P2_SKIP_FIX_PHONE_INSTRUMENTATION_2026-09-09.log) | 对应设备测试执行和构建结果 |

完整逐文件分类见 [文档索引](../DOCUMENTATION_INDEX.md)。
