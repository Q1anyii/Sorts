# 2026-09-17 织历调色板 / 月份选择器 / 全局气泡修复

## 一、织历「调色板」渐变（圆形画布 + 互斥落点）

### 需求演进
1. 初版：多色圆点标记 → 用户要求改成「调色板」：颜色块大小=数量占比、块间渐变
2. 二版：linear-gradient 色块条（flex 占比）→ 用户要求覆盖整个格子
3. 三版：全格 linear-gradient 硬断点 + 2.5% 过渡带 → 用户反馈太丑、要真渐变
4. 四版：全格平滑 linear-gradient（角度随机、颜色顺序随机）→ 用户要求改圆形渐变画布
5. 定稿：**radial-gradient 圆形画布**：
   - 每种颜色 = 一个随机落点的「颜料点」，圆形扩散（`radial-gradient(circle at x% y%, ...)`）
   - **互斥分区**：按颜色数排 rows×cols 网格，每个点固定落在独立单元内（单元内随机 ±0.55 单元宽，单元间互斥），避免所有点挤在同一区域
   - **半径随数量占比**：`30% + cnt/total*52%`，占比越大扩散越广
   - **颜色渐变过渡**：每层 `rgba(c, .92) 0% → rgba(c, .5) 42% → rgba(c, 0) R%`，多层半透明叠加自然融合
   - 底层垫 `linear-gradient(135deg, #F7F8FC, #FDF7EF)` 柔和暖底

### 柔和自然暖色板
- 紧急度分色（`PRIORITY_DOT_COLOR`）改柔和系：URGENT `#F49B8B`（珊瑚）/ HIGH `#F6C177`（蜜杏）/ MEDIUM `#8FB8DE`（淡蓝）/ LOW `#A9C3A3`（鼠尾草绿）
- 创建日程自定义色板（`tagColors`）改 8 色柔和系：淡蓝 / 淡绿 / 蜜杏 / 珊瑚 / 淡紫 / 淡粉 / 灰蓝 / 淡青

### 验证
- 17 日（3 色）落点 (26.8%,28.8%) / (69.6%,82.1%) / (63.2%,30.5%)——左上/右下/右上互斥
- 18 日（2 色）(40.3%,35.8%) / (48%,80.2%)——上下分开
- 无 JS 报错

## 二、织历月份选择器（自定义弹层）

- 原生长列表 select → **自定义弹层**：点击 trigger（`2026年9月 ▾`）弹出面板，年份 ◀/▶ + 12 个月网格，`slide-in-top` 入场动画
- **选定才跳转**：箭头只切换面板内预览年（`pickerYear`），日历不动；点击月份格子才把 `pickerYear + calMonth` 一并应用并关闭面板
  - 修复点：原实现箭头直接改 `calYear`，日历随箭头跳动，体验差
- 月份高亮：仅当前选中年+月高亮（`active: m === calMonth && pickerYear === calYear`）

## 三、织程日期筛选自动查询（修复需刷新才更新）

- 根因：日期 input `v-model="scheduleFilter.startDate/endDate"` 无 change 监听，手动改日期后列表不刷新
- 修复：新增 `onDateChange()`——手动改起止日期即自动置 `preset='custom'` 并 `syncSchedulesFromFilter()` 查询；两个 date input 挂 `@change`
- 验证：改 startDate=2026-09-17 → 列表自动刷新为 9/17 分组 4 条

## 四、全局穿梭气泡修复（补充前篇 devlog 未覆盖项）

1. **今日经纬页隐藏气泡**：`v-if` 增加 `currentPage!=='dashboard'`（主面板已有 active-timer，避免重复计时 UI）
2. **计时跳变（2:16 → 8 分钟）**：根因在后端 `TimerServiceImpl`——
   - `resume` 未重置 `actualStartTime`（仍为首次开梭时刻），前端自愈 computed `actualDuration + (now - actualStartTime)` 把暂停时段计入
   - 修复：`resume` 置 `actualStartTime = now`；`pause` 置 `actualStartTime = null`（已结算片段语义）
   - 验证：暂停 00:01:05 静止 8 秒仍 00:01:05，续梭 3 秒后 00:01:06（不含暂停）
3. **点击空白不跳转**：`@click.self` 只响应根元素（点到文字/时间子元素不触发）→ 改 `@click` 根事件，按钮保留 `@click.stop`；真实鼠标点击气泡空白/文字区均跳转 `#/dashboard`
