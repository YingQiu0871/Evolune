# v1.6 Wear Tile centered design review — 2026-09-07

结论：三个新 Tile 的模拟器视觉、系统发现、独立预览、添加、断连状态和 READY 布局已通过；Complication
仅完成注册/资源、现有实例和点击路径核对，尚未完成三个 provider 的逐槽 READY/STALE 实际表盘验证。
这不是 v1.6 最终批准。

## 已证实根因与修复

1. 三个新 Tile 共享 Logo preview，系统 Picker 无法用预览区分功能。Manifest 已改为三张功能预览。
2. 运行态使用黑底单列文字，虽不为空白，但与 Phone 最新信息层级不一致。改为圆形安全区内的居中莫奈色
   tonal panel；下一次服药与当前 E2 突出主值，今日计划使用紧凑三行列表。
3. READY 初版的今日计划日期占用宽度导致药名省略；下一次服药默认 button 色与显式文字色冲突。分别改为
   `HH:mm + 药名` 和统一高对比可点击 tonal panel。
4. 旧布局资源版本未变化会让宿主覆盖升级后继续渲染缓存。新资源协议使用 `3-NEXT_DOSE`、
   `3-TODAY_PLAN`、`3-CURRENT_E2`。

## 验证

- Wear JVM：83/83 PASS。
- Debug、AndroidTest APK：69 tasks PASS。
- 模拟器 fixture instrumentation：1/1 PASS；fixture 有物理设备保护条件。
- APK：三个独立 preview PNG 与 legacy preview 均打包；四个 Tile、三个 Complication 均存在于 merged manifest。
- 覆盖安装：`adb install -r` 成功，既有 Tile 数据库 id 0–4 保留，新 Debug id 5–7 追加；没有删除或清除。
- 系统 UI：三个 Debug Tile 均从 Picker 添加，断连与 READY 内容可见、居中、无裁切；系统“添加微件”加号仍是
  宿主控制，未被当成 Evolune 图标修改。

## 剩余风险

- P0：无已知源码/Debug 模拟器阻断项。
- P1：Release 签名包尚未构建和覆盖真实手表；三个 Complication 尚未在支持槽位逐个验证 READY/STALE、刷新。
- P2：未配对模拟器不能证明 Phone→Wear 的真实数据变化刷新和下一次服药确认回执；需最后在真实设备验证。
- P3：legacy `DoseTileService` 仍使用旧内部视觉，但组件身份和实例必须保留；本轮没有扩展其产品范围。

当前状态：Wear Tile 模拟器设计缺陷已关闭，Complication 和真实手表矩阵未关闭，保持 `IN_PROGRESS`。
