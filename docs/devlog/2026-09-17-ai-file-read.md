# Devlog · 2026-09-17 · 梭灵文件读取功能（临时记忆拼接）

## 需求
AI 聊天界面支持上传文件：内容仅作为**本次对话的临时记忆**用于提取用户的计划；
文件不包含可执行计划时**不存入记忆**并明确提示用户；支持 md / docx / doc / txt，大小合理限制。

## 实现
### 后端（sorts-ai）
- `pom.xml`：新增 Apache POI（poi-ooxml 5.2.5 解析 docx，poi-scratchpad 解析 doc）。
- `FileParseService` / `FileParseServiceImpl`：
  - 支持扩展名 md / docx / doc / txt；单文件上限 **2MB**；解析文本上限 20000 字符（与 `ChatRequest.fileContent` @Size 一致，超出截断）；
  - txt/md 直接 UTF-8 读取；docx 用 XWPF 提取段落 + 表格；doc 用 HWPF/WordExtractor。
- `FileParseController`：`POST /api/v1/ai/files/parse`（multipart `file`）→ 返回 `{ fileName, chars, text }`，不落库。
- `ChatRequest`：新增可选字段 `fileContent`（≤20000 字符）。
- `ChatServiceImpl.chat()`：`fileContent` 非空时作为**临时 user 消息**注入本轮模型上下文，**不写入 Redis 会话历史**（刷新即失效，满足「不存入记忆」）。
- `AiPrompts.fileMemoryPrefix()`：提示词约束——文件含可执行计划（日期/时间段/事项）→ 按天整理为结构化计划供确认；不含 → 直接告知「文件中未发现可提取的计划内容」，禁止编造日程、禁止强行套用规划模板。

### 前端（vite-app）
- AI 输入行新增 **📎 上传按钮** + 隐藏 file input（accept=.md,.docx,.doc,.txt）+ 附件 chip（显示文件名、解析字数、✕ 移除）。
- `onAiFileSelected`：格式 / 大小校验 → multipart 直传 `/ai/files/parse` → 成功挂 `aiAttachedFile` 并 toast；401 自动续期重试。
- `sendAiMessage`：带附件时**强制走普通对话**（/ai/chat 的 fileContent，plan 通道不支持）；输入框为空但带附件时默认指令「请从上传的文件内容中提取计划安排」；发送成功后在 `onDone` 释放附件（临时记忆不留存）。
- 附件 chip CSS（.ai-file-btn / .ai-file-chip）。

## 验证（浏览器端到端，probe_ok）
1. **md 计划文件**（秋招冲刺计划 9/18~9/19 5 条）：上传 → chip 显示「已解析 181 字」→ 发送 → AI 按天整理出 5 条计划（日期、时间段、事项齐全）。
2. **md 无计划文件**（Redis 学习笔记）：上传 → 发送 → AI 回复「这份文件里没有发现可提取的计划内容…没有生成任何日程」，不编造。
3. **docx 计划文件**（POI 解析）：上传 → chip 显示「已解析 54 字」，解析链路正常。
4. 附件发送成功后自动清空；未勾选「允许 AI 写入」时如实提示无法落库。

## 踩坑
- `FileParseController` 前缀首版写成 `/api/ai/files`（缺 `/v1`）→ 网关转发后 `NoResourceFoundException: No static resource api/v1/ai/files/parse`，前端提示「系统繁忙」；修正为 `/api/v1/ai/files` 后正常（AiController 类级前缀为 `/api/v1/ai`）。

## 变更文件
- 后端：`pom.xml`、`ChatRequest.java`、`ChatServiceImpl.java`、`AiPrompts.java`、`FileParseService.java`、`FileParseServiceImpl.java`、`FileParseResult.java`、`FileParseController.java`
- 前端：`index.html`、`src/legacy/app-logic.ts`、`src/legacy/style.css`
