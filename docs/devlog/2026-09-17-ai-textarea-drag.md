# Devlog · 2026-09-17 · AI 输入框上边框拖拽扩宽

## 需求
用户反馈 AI（梭灵）输入框的扩展方式「反人类」——原生 textarea 只有右下角拖拽手柄，期望**在输入框上边框任意位置向上拉取扩宽**（像 ChatGPT 输入框：拖上边缘向上扩展，底部不动）。

## 实现
### 1. CSS（style.css）
- `.ai-input-row` 加 `position: relative`，顶部 padding 增加给手柄留出空间
- `.ai-input-resize-handle`：绝对定位横跨整行（top:-6px, height:12px），`cursor: ns-resize`；hover 时显示 44px 主题色「小提手」（带微光）
- `.ai-chat-textarea`：`resize: none`（禁用原生右下角手柄）、`max-height: 320px`（提高拖拽上限）

### 2. 逻辑（app-logic.ts）
- `aiTextareaH` ref：拖拽高度（null=自动），**Vue 响应式驱动**——避免直接改 DOM inline style 被 Vue 重渲染重置
- `startAiTextareaResize(e)`：mousedown 记录起始 Y + 当前高度 → mousemove 计算 delta（向上为正）→ `aiTextareaH = clamp(startH + delta, 44, 320)` → mouseup 移除监听
- 命名独立（`startAiTextareaResize`）避免与已有的面板宽度拖拽 `startAiResize(e, key)` 冲突

### 3. 模板（index.html）
- 输入区顶部加 `<div class="ai-input-resize-handle" @mousedown.prevent="startAiTextareaResize">`
- textarea 绑定 `:style="aiTextareaH ? {height: aiTextareaH+'px'} : {}"`

## 验证（浏览器端到端，模拟鼠标事件）
1. 手柄存在、`resize:none`、`cursor:ns-resize`、max-height 320px ✓
2. 向上拖 120px：45 → **165**（1s 后稳定，Vue 异步渲染后生效）✓
3. 向下拖 60px：165 → **105**（1s 后稳定）✓
4. 响应式状态驱动，重渲染不丢高度 ✓
5. 注意：同步查 offsetHeight 会看到旧值（Vue :style 绑定 nextTick 生效），需延迟验证

## 变更文件
- `frontend/vite-app/index.html`：手柄 + textarea :style 绑定
- `frontend/vite-app/src/legacy/app-logic.ts`：aiTextareaH ref + startAiTextareaResize
- `frontend/vite-app/src/legacy/style.css`：手柄样式 + resize:none + max-height 320

## 备注
- 未提交前完成 git 提交（先提交不推送）。
