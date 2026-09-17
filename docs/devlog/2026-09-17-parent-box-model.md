# Devlog · 2026-09-17 · 主计划「盒子模型」包含式展示 + 主计划表单修复

## 需求
用户反馈两处：
1. 主页主计划与子计划目前是**两个并列气泡**（主计划在上、子计时在下），期望改为**包含关系（盒子模型）**：主计划作为容器，子任务放在主计划**内部**；子任务少时完整行按时间排序内嵌，数量增多时压缩行，更多时变成按颜色区分的任务点。
2. 图二「主线 · 主计划」创建弹窗：日期输入框占位显示 `yyyy/mm/日`（原生 date 控件空值本地化格式，观感差）、优先级 select 显示「中」过窄、控件视觉不统一。

## 实现
### 1. 主页主计划盒子模型（三档自适应）
- **数据**：`loadParentPlans()` 在拿到列表后，对**活跃主计划**（IN_PROGRESS/PAUSED）额外拉 `GET /parent-plans/{id}` 补 `children` 明细（列表接口本身不带 children）。
- **模板**（index.html dashboard 区）：主计划气泡 `timer-info` 内新增「子任务区」：
  - `children ≤ 3`：**完整行**——颜色点 + 标题 + 「日期 时间 · 状态」；
  - `4 ≤ children ≤ 8`：**压缩行**——颜色点 + 标题 + 「时间 · 状态」（小字号）；
  - `children > 8`：**任务点**——按颜色区分的圆点（`parent-child-dot-lg`），hover 放大并显示标题；
  - 无子计划：空态文案「暂无子计划 · 到主线主计划挂载日程后可在此聚合」。
- **排序**：新增 `sortedParentChildren` computed，按 `plannedStartTime` 升序。
- **样式**（style.css）：`.parent-children-box`（半透明虚线框）/ `.parent-child-row` / `.parent-child-row-compact` / `.parent-child-dot` / `.parent-child-dots` / `.parent-child-dot-lg` / `.parent-children-empty`。

### 2. 关键 Bug：Vue 响应式失效
- **现象**：子计划已挂载（`子计划 0/5`），但盒子始终显示「暂无子计划」。
- **根因**：`const active = list.find(...)` 拿到的是 **raw 数组元素**（在 `parentPlans.value = list` 之前 find）；`active.children = ...` 直接修改 raw 对象，**不会触发 Vue 的 set 拦截**，模板不更新。
- **修复**：改为 `parentPlans.value.find(...)`，从 **响应式代理** 中取对象再赋值 `children`。
- **验证**：5 个子计划 → 压缩行展示；挂到 9 个 → 任务点模式（9 个 `.parent-child-dot-lg`，title 正确）。

### 3. 主计划表单视觉统一（图二）
- `.modal input[type="date"] { height: 38px }` 统一日期控件高度（与文本输入一致）；
- 优先级 select 宽度与输入框对齐（150px），不再只显示「中」；
- 说明：`yyyy/mm/日 / 年/月/日` 为 Chrome 原生 date 控件空值占位格式，非乱码。

## 验证（浏览器端到端 + API）
1. 建「盒子验证主计划」→ 挂 3 个子计划 → start → 主页显示**完整行**（红1/绿1/绿2 按时间排序）✓
2. 挂到 5 个 → **压缩行** ✓；挂到 9 个 → **任务点**（9 点，hover title）✓
3. 修复响应式后刷新稳定复现 5 个子计划内嵌 ✓
4. 弹窗：日期输入高 38px、select 宽 150px 与输入对齐 ✓
5. 清理测试数据（删主计划、删 4 个「盒点」日程），API 确认无残留 ✓

## 变更文件
- `frontend/vite-app/index.html`：主页主计划盒子三分支模板
- `frontend/vite-app/src/legacy/app-logic.ts`：`loadParentPlans` 补 children（从代理取对象）、`sortedParentChildren` computed 并暴露
- `frontend/vite-app/src/legacy/style.css`：盒子样式 + date 控件高度

## 备注
- 未提交前完成本轮 git 提交（先提交不推送）。
- 任务点模式仅展示颜色与标题（hover），如需点击跳转织程可后续增强。
