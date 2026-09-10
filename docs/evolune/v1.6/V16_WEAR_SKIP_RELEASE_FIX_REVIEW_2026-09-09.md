# v1.6 Wear“跳过本次”发布阻断修复复审（2026-09-09）

## 结论

最终独立增量复审为 `APPROVE`：P0、P1、P2 均无；仅保留可选的 P3 压力测试建议。
项目负责人已完成真实手表视觉与功能验收。`20260909-210641` 签名候选生成于本修复之前并已作废；
当前源码重新生成的最终候选为 `20260909-224735`，已通过签名、哈希和真实 Phone/Wear 覆盖安装回读。

## 已证实根因

Wear 端原实现会发送跳过请求，Phone 端却只取消已经显示的通知。若该 occurrence 的
`AlarmManager` 闹钟仍在未来，闹钟到时仍会投递，因此“已发送跳过请求”不能保证本次提醒被抑制。

## 修复

- Phone 按 `planId + slotId + scheduledAt` 持久化 occurrence 级跳过状态，不写入 DoseEvent，
  不改变数据库 schema 或 occurrence 核心语义。
- Wear 请求必须同时匹配 `planId`、`slotId`、`scheduledAt`、`occurrenceId` 和
  `notificationId`；伪造、过期或畸形请求直接拒绝。
- 成功持久化后取消对应 PendingIntent 和当前通知；提醒重排不再创建已跳过 occurrence。
- Receiver 在最终投递前再次检查跳过状态，覆盖取消闹钟与广播到达之间的竞态。
- v1.5 旧闹钟没有 `slotId` 时继续走旧兼容路径；新输入携带但无法解析 `slotId` 时拒绝。
- 跳过集合的读取、清理、合并和同步提交使用进程级锁，避免并发跳过互相覆盖。

## 验证

- 完整 Debug 门禁：863 项 JVM 测试，0 failures/errors/skipped；Phone/Wear Lint 0 error；
  161 tasks，`BUILD SUCCESSFUL`。
- Pixel 11 Pro `ReceiverLifecycleInstrumentationTest` 最终 7/7 通过，覆盖旧闹钟兼容、
  已跳过 occurrence 不投递及畸形新 slot identity 不投递。
- 原始日志：
  - `V16_P1_SKIP_FIX_FULL_DEBUG_VERIFICATION_2026-09-09.log`
  - `V16_P1_P2_SKIP_FIX_FINAL_VERIFICATION_2026-09-09.log`
  - `V16_P1_P2_SKIP_FIX_PHONE_INSTRUMENTATION_2026-09-09.log`

## 剩余建议

P3：可在后续补充真实 AlarmManager 队列查询和连续并发跳过压力测试；不阻塞本次发布。
