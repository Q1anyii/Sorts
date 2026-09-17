# Devlog · 2026-09-17 · 梭灵「采纳」报 planId 规划ID不能为空

## 现象

梭灵页 AI 生成规划建议后，点击单条「采纳」（或「一键全部采纳」）弹出
`planId 规划ID不能为空` 错误，日程未创建。

## 根因

- 后端 `AdoptPlanRequest.planId` 标注 `@NotBlank(message = "规划ID不能为空")`，
  `@Valid` 在控制器把路径变量 `setPlanId(planId)` 覆盖进请求体**之前**就已执行；
  请求体缺少 `planId` 时直接校验失败。
- api-spec.json 中 `AdoptPlanRequest.required: ["planId"]`，planId 本就是请求体必填项。
- 前端采纳调用只传了 `{ selectedIndices: [...] }`，漏传 `planId` → 契约不符。

## 修复

`frontend/js/app.js` 两处采纳调用补上 `planId: currentPlanId`：
- `adoptSuggestion(index)`（单条采纳）
- `adoptAllSuggestions()`（一键全部采纳）

路径变量仍保留（后端以路径为准），请求体补传只是满足契约校验，无行为变化。

## 验证

- 容器重建后浏览器实测全流程：梭灵页发送规划 → 生成 6 条建议 →
  点第一条「采纳」→ 聊天区出现「✅ 已采纳并创建日程：学习Java基础」，
  无 planId 报错，建议列表移除该项
- 测试日程随后经 API 删除（id=2，剩余 0），未留脏数据

## 说明

- 后端契约未改（api-spec 本就要求 body 带 planId）；仅修正前端调用
