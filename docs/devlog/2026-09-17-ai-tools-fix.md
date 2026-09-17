# Devlog · 2026-09-17 · 梭灵写工具链路修复（未授权 / 反复确认 / 日期错乱）

## 背景

用户反馈梭灵两个实际体验问题（附真实对话证据）：
1. AI 无法调用写工具：用户粘贴 9/18 执行清单、逐轮确认后 AI 才告知「当前没有可用的创建日程工具」，说「写入」也无法落库；
2. AI 对不明确点反复确认多轮（日期 / 时段 / 优先级 / 标签逐条追问），体验差。

## 根因（三个叠加）

| # | 根因 | 定位 |
|---|---|---|
| 1 | **服务端写工具开关 `AI_TOOL_ALLOW_WRITE` 默认 false**，且 docker/.env 与 compose 从未传入 → 写工具 `createSchedule/createSchedules` 从未下发，模型只能查询 | `application.yml` → `sorts.ai.tool.allow-write: ${AI_TOOL_ALLOW_WRITE:false}`；`docker/.env`、`docker/compose.yml` 均无该变量 |
| 2 | **前端「写入意图」不自动授权**：`sendAiMessage` 普通对话分支仅用户手动勾选「允许 AI 写入」才置 `allowWrite=true`，写入意图消息不会自动授权 | `frontend/vite-app/src/legacy/app-logic.ts` `sendAiMessage` |
| 3 | **模型无服务端日期锚点**：chat system 提示与工具描述均未注入服务器当前日期，DeepSeek 凭内部知识推算「明天」→ 2026-06-06（正确 2026-09-18） | `AiPrompts.CHAT_SYSTEM` / `CreateScheduleTool`（对比 `planPrompt` 有注入「今天是 %s（服务器时间）」） |

## 修复内容

### 后端
- `AiPrompts.java`：
  - `CHAT_SYSTEM` 重写：规则 4「用户已明确表达写入意图直接执行，不再二次确认」；规则 5 默认值策略（日期=今年或上下文日期、时间=09:00、时长=60 分钟、优先级=MEDIUM、标签不设，全程最多一轮澄清）。
  - 新增动态方法 `writeToolsAvailable()` / `writeToolsUnavailable()`：注入**服务器权威日期**（今天 / 明天具体值）与写能力事实；无写权限时第一时间如实告知并提示勾选「允许 AI 写入日程」，禁止先复述多轮最后才说无法写入。
- `ChatServiceImpl.chat()`：system 消息改用动态方法 `AiPrompts.CHAT_SYSTEM + (allowWrite ? writeToolsAvailable() : writeToolsUnavailable())`，日期锚点随每次请求刷新。

### 前端（`frontend/vite-app/src/legacy/app-logic.ts`）
- `sendAiMessage` 普通对话分支增加**写入意图自动授权**：正则 `/写入|创建日程|创建.{0,6}日程|加入日程|添加到日程|添加到织程|批量添|落梭写入|保存到日程|入库|帮我(建|创建|安排|加入).{0,12}(日程|计划)/` 命中即自动 `aiWriteEnabled=true` 并 toast 提示，`finally` 复位（仅本次生效）。
- `runSuggestedAction` 处理 `requiresWritePermission`：后端检测到写被拒时返回该标记 → 自动开启授权并提示重发。

### 配置
- `docker/compose.yml`：`AI_TOOL_ALLOW_WRITE: ${AI_TOOL_ALLOW_WRITE:-true}` 加入 `*service` 锚点 environment（**不能放 ai 服务自身块**——YAML merge 语义下子级 environment 整块覆盖锚点，导致 `NACOS_ADDR=nacos:8848` 丢失 → serverAddr='null' → Nacos 注册失败崩溃循环，本次实际踩坑一次，RestartCount=9）。
- `docker/.env`：新增 `AI_TOOL_ALLOW_WRITE=true`（gitignored 不入库）。

## 验证（浏览器端到端 + DB）

1. 旧行为复现（开关关闭时）：AI 回复「写操作未授权，需要开启允许 AI 写入」+ `requiresWritePermission` 建议操作 → 提示词与建议操作生效。
2. 开关开启 + 自动授权后，发「写入：明天 09:00-10:00 背英语单词，14:00-15:00 复习操作系统」→ AI **一轮直接回复**「两条都已写入织程」，无确认循环。
3. 日期锚点修复前：落库 `2026-06-06 09:00/14:00`（错误）；修复后：落库 `2026-09-18 09:00/14:00`（正确，DB 实证 id=837/838，验证后已删除）。
4. 默认值策略：AI 自动采用 MEDIUM / 无标签并说明「要改说一声」，不再逐条追问。

## 变更文件
- `backend/sorts-ai/.../support/AiPrompts.java`（CHAT_SYSTEM 重写 + 动态写能力/日期注入）
- `backend/sorts-ai/.../service/impl/ChatServiceImpl.java`（system 注入调用点）
- `frontend/vite-app/src/legacy/app-logic.ts`（写入意图自动授权 + requiresWritePermission 处理）
- `docker/compose.yml`（锚点加 AI_TOOL_ALLOW_WRITE）
- `docker/.env`（gitignored，不入库）

## 备注
- 本地默认开启写工具；生产部署应将 `AI_TOOL_ALLOW_WRITE` 收紧为按需 true，或依赖网关/前端授权层约束。
- 规划通道（/ai/plan，含「规划 / 安排 / 计划 / 日程」关键词）走右侧勾选面板，与 chat 写工具通道是既有双通道设计，未改动。
