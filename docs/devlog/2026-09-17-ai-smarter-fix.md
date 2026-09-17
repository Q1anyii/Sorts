# Devlog · 2026-09-17 · 梭灵 AI 体验修复（写链路 / 路由误判 / md 渲染 / 修改工具）

## 需求
用户真实对话反馈 AI「傻」：①「修改刷算法的时间：19:00到21:00」被拒（无写权限）；②「补充每天上午的计划」类消息被误路由到规划面板（只出建议不写日程）；③AI 落库把时间段 09:00–12:00 压成 09:00 各 60 分钟；④前端对话 Markdown 未渲染（原始 `##`/`**` 裸露）；⑤「查看新建的日程」建议操作跳转错误。

## 根因与修复
### 1. 前端自动授权正则缺「修改」类意图（前两条的共因之一）
- `sendAiMessage` 双钥匙第二把自动授权正则原为 `写入|创建日程|创建.{0,6}日程|加入日程|添加到日程|添加到织程|批量添|落梭写入|保存到日程|入库|帮我(建|创建|安排|加入).{0,12}(日程|计划)`——不含「修改/调整/更新/改」→「修改刷算法的时间…」不置位 `aiWriteEnabled` → 后端不下发写工具 → AI 连续两轮拒绝。
- **修复**：正则扩展 `修改|调整|更新|改成|改到|推迟|提前|重命名|删除日程|移除日程` + `帮我…(改|调整|更新)…(日程|计划)`；自动授权仅本次生效（finally 复位）。

### 2. isPlanning 误路由（「日程」关键词过宽）
- 原正则 `/规划|安排|计划|日程/`——「修改我的日程…」「补充每天上午的计划」含「日程/计划」→ 被路由到 `/ai/plan` 结构化面板（该通道不写历史、不具写工具），造成 AI「生成 2 条建议」而非执行用户意图。
- **修复**：收紧为「生成/规划/安排 新计划」类意图，并显式排除 `修改|调整|更新|删除|移除|改成|改到|推迟|提前|补充|在此基础上|在此之上` 等操作语义 → 一律走普通对话（含写工具自动授权）。

### 3. 时间段被压短（模型语义）
- 工具描述/系统提示未约束「时间段→时长」换算，AI 把「上午9-12点 / 19-21点」建成 09:00/21:00 各 60 分钟（t_schedule ID856–943 实证）。
- **修复**：`AiPrompts.writeToolsAvailable()` 增加时间语义约束：时间段 = plannedStartTime 起始时刻 + plannedDuration 完整时段时长（9-12 → 180 分钟），描述与理解不一致以用户描述为准。

### 4. 新增 updateSchedule 写工具（AI 此前「没有修改接口」）
- `ScheduleClient` 增加 `GET /schedules/{id}`、`PUT /schedules/{id}`（Feign 契约对应 schedule 服务既有接口）。
- 新增 `UpdateScheduleTool`（ToolLevel.WRITE，自动注册进 ToolRegistry）：
  - 参数 `id`（必填）+ `title / plannedStartTime / plannedDuration / description / priority / tags / color`（可选）；
  - **局部更新语义**：先按 id 读原日程，仅覆盖显式给出的字段，未给出的保持不变；
  - 提示词指引：修改前先查询定位 ID，用户说「全部后续的/所有」时逐条 update。
- `JsonArgs` 补充 `longValue`。

### 5. 规划通道上下文衔接
- `AIPlanRequest` 增加可选 `conversationId`；`PlanServiceImpl.generate` 成功后把「用户 prompt + 规划摘要」写入 Redis 对话历史（ConversationStore），后续普通对话可衔接「上一轮规划了什么」；Redis 故障不阻塞主流程。
- 前端 plan 分支：无会话 ID 时初始化 UUID 并随请求传递。

### 6. Markdown 未渲染（`##` 留在段落内）
- `renderMarkdown` 配置 `breaks:false`——AI 以单个换行分隔「标题/表格/列表」时，CommonMark 软换行不触发块级语法 → `##9月20日` 裸露在 `<p>` 内。
- **修复**：`marked.setOptions({ gfm: true, breaks: true })`（GitHub 风格，软换行视为块边界），表格/粗体/标题/列表均正常解析。

### 7. 「查看新建的日程」跳转修正
- `runSuggestedAction(CREATE_SCHEDULE)` 原逻辑：有 scheduleIds 时打开「新建日程弹窗」（错误）。
- **修复**：跳转织程页 `/schedules` + 清空日期筛选 + 刷新列表 + toast「已为你定位 N 条新建日程」；新增 `aiJustCreatedIds` 状态，织程列表中命中项加 `.just-created` 高亮呼吸动画（2.2s × 2）。

## 验证（浏览器端到端 + DB）
1. 「修改我的日程：明天9点背单词改成10点」→ 自动授权置位（toggle=checked）→ AI 查询定位（ID88「绿1」）→ 用户确认「就是ID88，改成明天10点」→ **updateSchedule 执行成功，DB 实证 `t_schedule ID88 planned_start_time=2026-09-18 10:00:00`**；AI 主动提示与 ID89 时间冲突。
2. 「帮我生成未来一周规划」仍走规划面板（回归通过）。
3. 「生成今日总结」Markdown 渲染：`<h2>整体评价</h2>`、`<strong>` 正常（breaks:true 生效）。
4. 点击「查看新建的日程」→ 跳转 `#/schedules`（织程页）。
5. 容器日志：`梭灵工具集已装载：[queryPoints, queryStatistics, createSchedules, updateSchedule, getProfile, querySchedules, createSchedule]`（7 工具）。

## 变更文件
- 前端：`frontend/vite-app/src/legacy/app-logic.ts`（授权正则、isPlanning 收紧、runSuggestedAction、plan conversationId、breaks:true）、`index.html`（just-created 高亮）、`src/legacy/style.css`（高亮动画）。
- 后端：`AiPrompts.java`、`AIPlanRequest.java`、`PlanServiceImpl.java`（+ConversationStore 注入）、`ScheduleClient.java`、`UpdateScheduleTool.java`（新增）、`JsonArgs.java`、`PlanServiceImplTest.java`（构造器同步）。

## 备注
- 自动授权为单次生效（finally 复位），连续多轮修改需每轮授权；如需会话级常开需产品决策。
- 未推送；git 遵循「先提交不推送」。
