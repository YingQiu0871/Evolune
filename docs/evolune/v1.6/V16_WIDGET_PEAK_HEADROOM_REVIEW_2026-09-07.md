# v1.6 Phone PK chart peak headroom review

日期：2026-09-07
范围：Phone `E2 趋势` Widget 的新增记录后最高柱平顶问题
工作区：`main` / `df12329278eafa488713edf202554cdbd523b8d0`，dirty，未创建提交

## 结论

实现层根因已经确认并修复：最高柱高度贴近绘图区上边缘时，父级 `clipToOutline` 裁掉圆角，导致平顶。
`WidgetChartGeometryPolicy` 现在为每种响应式布局保留 10dp 顶部空间；Phone Debug 模拟器的真实
Launcher 运行态已观察到最高柱圆角完整，未见平顶或上下溢出。

本记录不是 v1.6 最终发布批准。当前结论为 **REQUEST CHANGES / 验收未关闭**，原因是 Release
签名构建缺少密钥环境变量，connected instrumentation 在安装阶段遇到既有 Debug 包签名不兼容，且
Release/真机、Wear 和完整跨入口证据仍未齐备。

## 审阅依据

- 实现：[WidgetUi.kt](../../../app/src/main/java/io/github/yingqiu0871/evolune/widget/WidgetUi.kt)、
  [EvoluneWidgetReceiver.kt](../../../app/src/main/java/io/github/yingqiu0871/evolune/widget/EvoluneWidgetReceiver.kt)
- Debug 运行态截图/XML：`V16_EMU5558_DEBUG_WIDGET_CHART_RUNTIME_DEBUG_ONLY_2026-09-07.*`
- Debug appwidget 注册：`V16_EMU5558_DEBUG_WIDGET_DUMPSYS_DEBUG_ONLY_2026-09-07.txt`
- Fresh JVM/Debug 构建：`V16_WIDGET_PEAK_HEADROOM_FINAL_BUILD_2026-09-07.log`
- Connected instrumentation 原始失败：`V16_WIDGET_PEAK_HEADROOM_CONNECTED_DEBUG_TEST_2026-09-07.log`
- Release 构建原始失败：`V16_WIDGET_RELEASE_BUILD_FRESH_2026-09-07.log`

## 问题分级

| 项目 | 状态 | 说明 |
|---|---|---|
| P0 | 无 | 未发现崩溃、数据写入或 PK 模型回归 |
| P1 | 已修复，待独立复核 | 圆角柱顶部父布局裁切；Debug-only 运行态已验证 |
| P2 | OPEN | Release 签名产物、覆盖升级及真实 Phone/Wear 宿主证据缺失 |
| P3 | OPEN | connected instrumentation 需要同签名测试安装环境；当前日志不得算通过 |

除非上述缺失证据补齐并完成独立复审，不得将本问题或整个 v1.6 标记为 `DONE`。
