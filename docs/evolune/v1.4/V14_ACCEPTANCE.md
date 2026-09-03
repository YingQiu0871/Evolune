# Evolune v1.4 — Acceptance Record

## Status

v1.4-A（Trust & Permission Foundation）和 v1.4-B（Guided Feature Tutorial）
已在当前开发分支完成实现并通过独立复核。本文记录的是开发分支验收，不表示
公开版本已经封版、打 tag 或发布。

## Accepted scope

- 必需的 Terms/Privacy 与 Medical/PK acknowledgement 在首次使用流程中独立受控；
  第三、第四步未勾选“我已阅读并理解”时不能继续。
- 通知、Health Connect、Google Drive 等授权流程先展示用途和数据边界，再进入
  既有系统或 provider 授权入口。
- 设置和 About 保留可重新查看的披露/权限说明入口。
- 新安装在完成必需披露后进入六步功能教程：用药方案、记录剂量、PK 图表、
  Widget、Wear 和备份。
- 教程可跳过，完成或跳过后不会再次自动弹出；设置可以单独重新打开。
- 方案和剂量 CTA 复用现有编辑器，PK 和备份 CTA 复用既有导航；Widget 与 Wear
  步骤仅提供说明，不申请权限、不触发平台操作。

## State and authority guarantees

- Tutorial state 使用独立的 device-local DataStore，不进入 `isComplete`、Room、
  Settings restore 或备份数据。
- v1.3 existing-install migration 不自动强制 beginner tutorial。
- Phone Room/domain/repository 仍是用药事实来源，Phone 仍是 Wear 权威；PK 数值、
  backup codec、Widget 实现和 Wear/W4 实现未被教程复制或改写。
- 教程中的 PK 文案明确为 model estimate，不是实验室检测或治疗建议。

## Evidence

### Automated checks

- `:app:testDebugUnitTest` — PASS
- `:experience-core:test` — PASS
- `:wear:testDebugUnitTest` — PASS
- Phone/Wear Kotlin compilation — PASS
- Focused Phone instrumentation selection — **16/16 PASS**
- `git diff --check` — PASS

Focused instrumentation included onboarding state migration, tutorial UI, tutorial
navigation/action seams, Settings re-entry, and existing backup/navigation regression
coverage. The tutorial UI also covers 1.5× font scale with an 840dp-wide window.

### Device smoke

On the Phone emulator, the following clean-install path passed:

`legal disclosures → tutorial auto-launch → Skip → relaunch without auto-open → Settings reopens tutorial`

The same smoke confirmed the mandatory checkboxes gate the third and fourth onboarding
steps. The existing Phone/Wear build and regression checks passed; no W4/Wear production
change was introduced.

### Independent review

The connected ChatGPT review accepted v1.4-B after verifying deterministic onboarding test
state, real `AppNavigation` CTA wiring, executed onboarding-store tests, large-font/wide-
window coverage, clean-install smoke, and the final working-tree scope.

## Release follow-up

A sealed v1.3.1 → v1.4 in-place upgrade on a physical release path remains useful as a
release-candidate check. The existing fresh-vs-existing DataStore tests cover the state
decision used by that path, and this residual check does not block the v1.4-B implementation
acceptance.

No commit, tag, or public release is created by this record.
