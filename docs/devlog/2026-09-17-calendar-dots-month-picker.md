# Devlog · 2026-09-17 · 织历优化：月份下拉 / 任务点分色 / 圆点丢失修复

## 背景

用户反馈织历三个问题：① 无月份快速切换入口（只能 ◀ ▶ 逐月翻）；② 日历任务点全是一个颜色，
项目已有的「紧急度 / 用户自定义颜色标记」数据没有用上；③ 任务点**有时不显示，刷新才恢复**。
要求不改变整体设计。

## 根因定位

**圆点丢失（真 bug）**：`calendarDays` computed 依赖 `schedules.value`，而该列表由
`syncSchedulesFromFilter()` 按**织程当前筛选**（startDate/endDate）拉取。此前织历→织程联动
（点日期 → `#/schedules?date=` → 筛选锁定单日）会让 `schedules.value` 只剩当天数据；
回到织历后其他日期的圆点全部消失，直到整页刷新重置筛选才恢复——这就是「需要刷新才显示」的机理。

**颜色没用上**：`calendarDays` 虽已读 `s.color`，但新建日程表单默认 `color='#4A6CF7'`（蓝色），
后端全存成蓝色，紧急度分色形同虚设。

## 变更明细

### 前端 `frontend/js/app.js`（LF）
1. 新增全量专用列表 `allSchedules = ref([])`：`loadAllSchedules()` 拉 `/schedules`（view=all，
   无日期筛选）；`loadSchedules()` 改为 `Promise.all([syncSchedulesFromFilter(), loadAllSchedules()])`，
   所有既有调用点（登录、增删改、落梭/取消、AI 采纳）自动同步，织程筛选列表与织历全量列表解耦。
2. `calendarDays` 改读 `allSchedules`；`selectCalendarDay` 的当日明细也改用 `allSchedules`。
3. 分色函数 `scheduleDotColor(s)`：**用户自定义 `color` 优先**，否则按紧急度
   `PRIORITY_DOT_COLOR`（URGENT=#EF4444 / HIGH=#F59E0B / MEDIUM=#4A6CF7 / LOW=#94A3B8）。
4. 月份下拉：`calendarMonthOptions`（当前年 ±2，共 60 项）+ `onCalendarMonthChange`，
   与既有 `calYear/calMonth` 状态、sessionStorage 持久化、路由 year/month 参数天然兼容。
5. 表单颜色默认改 `''`（自动）：新建日程默认按紧急度分色，选色板后才落自定义颜色；
   `editSchedule` 同步兼容空值。

### 前端 `frontend/index.html`（CRLF，经 python 脚本改）
1. 织历头部静态月份标签 → `<select class="month-select">`（◀ ▶ 保留，下拉为快速跳转）。
2. 颜色标记色板前新增「自动」选项（conic-gradient 四色小圆，选中态描边），点击置 `color=''`。

### 前端 `frontend/css/style.css`（LF）
新增 `.month-select`（宽 132px）与 `.color-swatch-auto`（自动/选中态）样式。

## 验证（浏览器实测，sorts-frontend 容器重建 `?v=20260917-3`）

| 用例 | 结果 |
| --- | --- |
| 月份下拉存在、切到 2026-8 网格随之变化 | ✅（首格变 30，2026-08-01 为周六） |
| 9/17 建 3 条测试日程（URGENT 无色 / HIGH 无色 / LOW+#8B5CF6） | ✅ 圆点 = 红、琥珀、紫（自定义优先） |
| 硬刷新后圆点稳定出现（t+3s / t+6s 均 3 点） | ✅ |
| 织历点 18 日 → `#/schedules?date=2026-09-18` → 回织历 | ✅ 17 日 3 点仍在（修复前会消失） |
| 织程页筛选列表回归 | ✅ 正常渲染 |
| 测试数据清理 | ✅ 3 条已软删 |

## 说明
- 织程页筛选逻辑未动，`schedules` 仍服务织程列表与当日面板；织历改用独立全量列表，互不干扰。
- 旧日程若已存过蓝色默认色，圆点仍显示蓝色（属历史数据，编辑保存一次即按新逻辑）。
