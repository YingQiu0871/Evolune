# Evolune 快速开始指南

Evolune 是本地优先的 Android/Wear OS 用药记录、提醒和药代动力学趋势工具。当前
公开稳定版本是 v1.1.0；v1.0.0 为上一版封存发布，v1.1 Phone Widget Completion
已正式发布，v1.2 尚未开始。

> 浓度曲线是模型估算，不是化验结果、诊断或治疗建议。

## 第一次使用

1. 打开“设置”，填写体重并选择应用主题、颜色主题和时间制式。
2. 在“记录”中添加已经发生的用药事件；选择给药途径、酯类、剂量和时间。
3. 在“方案”中创建每日、每周或自定义间隔的计划，添加一个或多个时间槽。
4. 保存并启用计划；系统会按计划安排提醒。
5. 返回“主页”查看当前 E2 浓度、历史曲线和未来趋势。

## 创建多时间方案

在方案编辑器中为同一计划添加多个每日时间，例如 09:00、17:00 和 22:00。保存
时会按本地时间自动排序；已有时间槽的稳定 ID 会尽可能保留，顺序位置不是槽位
身份。计划在其创建日的本地日期开始生成 occurrence，即使某个时间已经早于创建
时刻；创建日之前不会生成 occurrence。

## 记录和查看历史

- 在“记录”页使用添加入口创建手动记录，点击已有条目可编辑或删除。
- 通知和 Phone Widget 的确认动作最终写入同一个权威 `DoseEvent`。
- 记录实际发生时间与计划时间分别保存；主页曲线会在保存后重新计算。

## 添加 Phone Widget

1. 在手机桌面长按空白处，选择 Evolune Widget 并放置到桌面。
2. 首次添加或长按重新配置时，可选择 Auto/Light/Dark、Material You 或 Monet
   配色，以及 30%–100% 背景透明度。每个 Widget 实例独立保存这些设置。
3. Widget 展示当天 occurrence、完成数/总数、E2 浓度和进度。过去但尚未记录的
   occurrence 不会被自动算作完成。
4. 点击未记录 occurrence 的勾选按钮即可记录；动作使用 occurrence 的 plan、slot、
   日期和稳定 ID，并以实际点击时间写入。重复点击或多个 Widget 实例不会重复记账。
5. 2×2 是完整的日常使用规格；更大的尺寸显示更多行。当天 occurrence 超出可见
   容量时，固定的标题/进度区域保持不动，列表可在 RemoteViews collection 中纵向
   滚动。

## Wear Tile 基础

配对手机和手表后，Wear Tile 可显示手机发送的计划/浓度快照，并提交剂量动作。Phone
Room 仍是唯一事实来源；断连或重连时 Wear 使用可重建缓存，不会成为另一份数据库。
已安装 v1.0 Wear 包的用户请先阅读 [身份迁移说明](docs/evolune/WEAR_V11_MIGRATION.md)。

## 数据迁移与备份边界

在“设置”中使用 Mahiro JSON v1 文件或剪贴板导出/导入。Phone/Wear 私有数据明确排除
于 Android Auto Backup 和设备迁移；当前没有 Health Connect 或 Google 云备份。v1.2
将分别评估 Health Connect 和用户控制的 Google backup/restore，不等同于实时云端数据库
同步。

## 浓度查看提示

主页显示当前浓度、历史记录和基于已记录事件/启用方案的趋势。体重、给药途径、剂量、
吸收参数和记录时间都会影响结果；请不要把图表区间当作个人治疗目标。

## 开发构建

需要 JDK 17、Android SDK 36 和 Git：

```powershell
.\gradlew.bat test assembleDebug
```

Debug APK 仅用于开发/测试；正式下载请使用 [v1.1.0 GitHub Release](https://github.com/YingQiu0871/Evolune/releases/tag/v1.1.0)。生产代码保持
Room/domain/repository 权威边界，Widget 使用 Android RemoteViews，Wear 使用 Tile 和
Data Layer。
