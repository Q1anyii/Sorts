# Devlog · 落梭后今日经纬被清空（需手动刷新才恢复）

日期：2026-09-17
状态：已修复并验证

## 现象

用户反馈：任意页面点击全局穿梭气泡「落梭」结束后，回到今日经纬页面，今日日程列表直接清空，必须手动刷新浏览器才恢复。

## 根因

今日经纬的 `todaySchedules` / `upcomingSchedules` / `todayStats` 三个 computed 均读取 `schedules`（织程筛选列表），而非全量列表 `allSchedules`。

- 织历 / 织程联动（如点击织历某日 → 跳转 `#/schedules?date=YYYY-MM-DD`）会把 `scheduleFilter.startDate/endDate` 置为非今日日期；
- `endSchedule()` / `cancelSchedule()` 等操作成功后调用 `loadSchedules()` → `syncSchedulesFromFilter()`，按**当前筛选**重新拉取 `schedules`；
- 此时 `schedules` 只包含筛选日期范围内的日程，而今日经纬按「今天」过滤 → 今日日程为空；
- 手动刷新时应用初始化重置筛选（或恢复默认），列表重新拉全量 → 看起来"刷新就好了"。

即：**今日经纬错误地耦合了织程的筛选状态**，落梭等任何刷新动作都会把筛选结果同步进来，导致与「今日」无关。

## 修复

`frontend/js/app.js`：`todaySchedules` / `upcomingSchedules` 改为从 `allSchedules`（织历全量列表，`loadAllSchedules()` 无日期筛选拉取）过滤，与织程筛选完全解耦。

```js
const todaySchedules = computed(() => {
  const today = getTodayStr();
  return allSchedules.value.filter(s => s.plannedStartTime && s.plannedStartTime.startsWith(today));
});
// upcomingSchedules 同理读 allSchedules.value
```

`todayStats` 依赖 `todaySchedules`，无需改动。`filteredSchedules`（织程列表）仍读 `schedules`，语义不变。

## 验证（浏览器实测）

1. 织历点击 19 号 → 跳转 `#/schedules?date=2026-09-19`（筛选非今日，0 条）；
2. 切回今日经纬 → 今日 4 条日程（蓝1/蓝2/红1/灰1）正常显示 ✅（修复前此步即清空）；
3. 开梭蓝1 → 落梭 → 蓝1 变「已完成」、统计卡「已完成=1」、其余 3 条今日日程与 9/18 即将到来均保留 ✅。

## 影响面

- 今日经纬三个 computed 依赖从 `schedules` → `allSchedules`，`loadSchedules()` 已并行拉取两者，无额外请求。
- 织程 / 织历 / AI 等模块不受影响（仍读各自数据源）。
