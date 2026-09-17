# Devlog 2026-09-17 · 主计划（长时间计划容器）功能落地

## 一、需求
用户提出：为「梭子」新增**主进程/主计划**概念，用于长时间计划（如秋招备战）。
- 子计划 = 现有日程（t_schedule），主计划里包含子计划；
- 主计划的全局悬浮气泡与子计划气泡**配合**：主计划在上且更大、子计划在下更小；
- 主页（今日经纬）气泡**并列**展示：主计划块在上、子计划块在下。

## 二、设计决策
| 项 | 决策 |
| --- | --- |
| 数据模型 | 独立 `t_parent_plan` 表（容器性质），子计划通过 `t_schedule.parent_id`（NULL=独立日程）挂载 |
| 状态机 | `PENDING → IN_PROGRESS ⇄ PAUSED → COMPLETED`，非法跃迁 409；`started_at` 记录进入进行中时间，`daysRunning = started 日期→今天+1` |
| 结算口径 | **不做分钟级穿梭结算**（与子计划穿梭计时解耦）；进度 = 子计划完成数/总数聚合（`SUM(CASE WHEN status='COMPLETED')`） |
| 删除语义 | 软删除主计划（`deleted` + `deleted_at`），同时 `clearChildren` 解除全部子计划挂载，子计划保留为独立日程，不级联删除 |
| 挂载/移出 | `attachChildren`（校验属主 + 总数一致，重复挂载幂等 UPDATE）、`detachChild`（parent_id 匹配才清除） |
| 网关 | 新增 `/api/v1/parent-plans/**` 路由到 sorts-schedule |

## 三、实施
### 后端（sorts-schedule）
- 新增 `entity/ParentPlan.java`、`dto/ParentPlanSaveRequest/AttachChildrenRequest/ParentPlanVO`、`mapper/ParentPlanMapper`、`service/ParentPlanService(+Impl)`、`controller/ParentPlanController`
- REST：POST /api/v1/parent-plans、GET 分页、GET /{id}（含 children 明细）、PUT /{id}、DELETE /{id}、POST /{id}/{action}（start/pause/resume/complete）、POST /{id}/children、DELETE /{id}/children/{scheduleId}
- `Schedule` 等 4 文件增加 `parentId` 字段回填
- 增量 SQL：`scripts/sql/upgrade_2026-09-17-parent-plan.sql`（t_parent_plan 建表 + ALTER t_schedule ADD parent_id + idx_parent），已在 sorts-mysql 执行成功

### 前端（vite-app）
- 侧边栏新增「主计划」入口；新增 `plans` 页面（列表卡片 + 详情含子计划行/移出 + 新建/编辑弹窗 + 添加子计划多选弹窗）
- 全局气泡改造为 `.global-bubbles` 容器（顶部居中纵向排列）：主计划气泡在上（大，含进度条与「子计划 x/y」）、子计划气泡在下（scale(.92) 较小，保留五态按钮）；主气泡点击回主计划页、子气泡回今日经纬
- 主页 Active Timer 拆分：`parent-active-timer`（大/上，状态/第N天/日期区间/进度条 + 开始/暂停/续梭/完成/查看）+ `child-active-timer`（小/下，原五态穿梭）

## 四、踩坑与修复
1. **网关路由缺失**：`/api/v1/parent-plans` 无路由 → 网关返回「系统繁忙」。修复：application.yml schedule 路由追加 `/api/v1/parent-plans/**`，重建 sorts-gateway。
2. **`notify is not a function`**：误把 reactive 状态 `notify` 当函数调用（`Be is not a function`）。修复：成功/警告提示改用 `toast(text, type)` API（6 处）。
3. **前端 asset 缓存**：reload 后旧 JS 仍在 → 用 CDP `Network.clearBrowserCache` 强刷验证新构建（index-*.js hash 变化）。

## 五、验证
浏览器端到端（probe_ok）：
- 创建主计划「秋招备战（9月-10月）」（HIGH，2026-09-10 ~ 2026-10-10）→ 列表卡片显示 ✓
- 详情挂载子计划「绿1」（现有日程）→ 详情「子计划 0/1」+ 移出按钮 ✓
- ▶ 开始 → IN_PROGRESS（暂停/完成按钮）✓
- 主页气泡并列：主计划块（进行中 · 第1天 / 进度 0%）+ 子计划块（00:00:08 穿梭计时）✓
- 全局气泡（AI 页）：主计划气泡在上（子计划 0/1 + 进度条）、子计划气泡在下 ✓
- ✓ 完成主计划 → 主气泡消失（COMPLETED）✓
- 落梭子计划 → 子气泡消失 ✓
- 后端 SQL：attachChildren 聚合 `parent_id IN` 正常；t_schedule.parent_id=1 挂载正确

## 六、说明
- 主计划不做分钟级结算：长时间计划按「天 + 子计划完成度」度量，与穿梭计时解耦
- 子计划挂载后仍可在织程独立操作（状态流转互不影响主计划状态机）
- 测试数据：主计划 id=1「秋招备战（9月-10月）」、子计划「绿1」（t_schedule.id=88, parent_id=1）保留，用户可自行管理
