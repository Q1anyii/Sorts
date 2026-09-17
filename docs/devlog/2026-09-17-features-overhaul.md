# Devlog · 2026-09-17 · 十二节功能改造：开启规则 / 批量删除 / 统一提示 / 织历联动 / AI 多日规划 / 会话持久化

## 背景

用户以「资深全栈工程师」身份一次性下达 12 节改造要求（8 个功能域），覆盖：
① 织程计划开启规则（未到开始时间禁止开梭）、② 织程单条/批量删除、③ 前端统一提示窗、
④ 织历点击日期跳转织程 + 日期范围查询、⑤ AI 未来一周多日规划、⑥ 织史单条/批量删除、
⑦ 刷新后保持当前页面、⑧ AI 会话持久化。要求不破坏现有功能、沿用项目技术栈、附完整测试。

## 变更点清单

### 后端 sorts-schedule
- `ErrorCode` 新增 `PLAN_NOT_STARTED(41010)`。
- `TimerServiceImpl.start`：`actualStartTime == null && now < plannedStartTime` → 抛 41010「未到计划开始时间，暂不可开启」；
  时间取服务器 `LocalDateTime.now()`（容器 TZ=Asia/Shanghai），客户端篡改无效；PAUSED 续梭不受限。
  状态机五动作（start/pause/resume/end/cancel）全部记录 `lastOperatorId`（X-User-Id）。
- `Schedule` 实体 + `t_schedule` 新增 `deleted_at`、`last_operator_id`；MyBatis-Plus 逻辑删除（deleted 0/1 全局自动过滤）。
- `ScheduleServiceImpl.delete`：单删改为自定义 UPDATE（属主 + deleted=1 + deleted_at + updated_at 一条 SQL，affected≠1 → 404），消除两步中间态。
- `ScheduleServiceImpl.deleteBatch`：`@Transactional`，去重后 ≤200，`affected != distinct.size()` 整体回滚抛「部分日程不存在或无权删除，已整体回滚」。
- `ScheduleController` 新增 `POST /schedules/batch-delete`（POST 带 body，规避 DELETE body 兼容问题）。
- `ScheduleQuery.applyTimeRange`：`startDate/endDate` 闭区间（ge startOfDay + lt end+1），`listInRange` 同步。

### 后端 sorts-ai
- **多日规划**：新增 `PlanRangeDetector`（未来一周/下周/本周/本月/今天/明天/同义表达 → Range，today 服务端注入，跨月跨年正确）；
  `AiPrompts` 多日重载硬性要求 date 落区间、每天 2-5 条、禁止堆一天；`PlanServiceImpl.generate` 接 detector →
  提示词 → 解析 → 越界过滤补缺省 → 落库 → 响应增 `range`、suggestions 逐条增 `date`；
  `AdoptPlanRequest` 增 `overrides`（PlanOverride[index,title,suggestedStart,duration]），越界整体拒绝，MAX_ADOPT 20→40。
- **织史删除**：`ReportServiceImpl.delete/deleteBatch`（softDeleteOwned，404 语义、事务整体回滚）；
  `AiReport` + `t_ai_report` 增 `deleted_at`；接口 `DELETE /ai/reports/{id}`、`POST /ai/reports/batch-delete`。
- **会话持久化**：新表 `t_ai_conversation` 全量快照存储（userId,title,messages MEDIUMTEXT JSON,draft,selected_plan_items,
  last_generated_range,plan_id,plan_suggestions,deleted,created_at,updated_at）；`ConversationServiceImpl`：
  列表按 updated_at 倒序分页、create 超 50 条软删最旧、消息上限 100 保留最近、单条正文 5000 截断、role 白名单；
  接口 GET/POST `/ai/conversations`、GET/PUT/DELETE `/ai/conversations/{id}`、POST `/ai/conversations/batch-delete`。

### 前端（frontend/，Vue 3 CDN 主版，hash 路由）
- **统一提示**：`notify` 响应式对象 + `notifyError`（生产隐藏 stack、开发 console.warn+详情 JSON）/`notifySuccess|Warning`（自动消失 toast）/
  `notifyConfirm`（Promise<bool>）/`copyNotifyDetail`；`apiFetch` 加 15s AbortController 超时、silent 选项、
  网络/超时/业务错误统一弹窗、401 无感续期重放。index.html 尾部 notify-mask/notify-box/toast-wrap；style.css 追加 v3 段。
- **织程**：全选/半选（indeterminate）/单条勾选 + 批量删除栏（confirm 显示数量）+ 单条删除（confirm）；
  日期预设下拉（今天/明天/本周/本月/自定义）+ startDate/endDate 双 input + 清空筛选；`groupedSchedules` 按日期分组渲染。
- **织历联动**：`navigateTo(page,params)` + `parseHash` + `applyRouteParams` + hashchange 双同步；
  织历点日期 → `#/schedules?date=YYYY-MM-DD` → preset=custom + 闭区间。
- **AI 三栏**：会话侧栏（新建/切换/重命名/单删/批删/清空）+ 聊天（SSE 流式）+ 多日规划面板
  （`aiGroupedSuggestions` 按天分组、按项/按天/全选、行内编辑、`adoptSelectedSuggestions` 批量采纳带 overrides）。
- **刷新保持**：hash 路由天然免刷新 404（VALID_PAGES 白名单）；onMounted 先恢复 hash 页面再登录校验；
  sessionStorage 前缀 `sorts.ss.` 存织程筛选/选中、织历月份/选中日、织史选中、AI currentId。
- **织史**：多选/全选/单删/批删 + confirm 数量。

### 部署 / 数据
- 迁移脚本 `scripts/sql/upgrade_2026-09-17-features.sql`（IF NOT EXISTS 语义可重复执行），已对现存数据卷执行并验证。
- `docker/nginx.conf`：index.html no-store、js/css no-cache；index.html 资源加 `?v=20260917-1`（解决浏览器缓存滞留旧 JS）。
- 容器重建：sorts-schedule / sorts-ai / sorts-frontend（网关因 compose 依赖被连带重建），全部 healthy。

## 测试（后端单测全绿）

- sorts-schedule：**79 个**用例通过。新增 `TimerServiceImplTest`（未到时间 41010 / 到时间成功 / 无计划时间兼容 / PAUSED 不受限 /
  状态变更写操作人）；`ScheduleServiceImplTest`（删除成功 / 404 / 批删空拒 / 超 200 拒 / 部分回滚 / 去重，旧用例改 softDeleteOwned 断言）。
- sorts-ai：**135 个**用例通过。新增 `PlanRangeDetectorTest`（11 例：未来一周=今天..+6 / 下周 / 本周 / 本月 / 明天 / 跨月跨年 / 月末实际天数 / 未命中回退）、
  `ConversationServiceImplTest`（8 例：容量淘汰 / 消息清洗截断保留最近 100 / 快照字段 / 404 / 空拒 / 部分回滚）。
- 测试挖出并修复生产 bug：`sanitizeMessages` 对 role=null 的 `Set.of().contains(null)` NPE → 加 null 前置判断。
- `AiControllerMappingTest` 构造器补 ConversationService 参数（5 参→6 参）。

## 浏览器验收（localhost:8088，bu 实测）

| 验收项 | 结果 |
|---|---|
| 织历点 17 日 → `#/schedules?date=2026-09-17`，preset=custom、双日期=17 | ✅ |
| 织程按日分组「📅 2026-09-17 N 条」 | ✅ |
| 未来日程开梭按钮 disabled + title「未到计划开始时间，暂不可开启」；已过时间日程可开梭 | ✅ |
| 绕过前端直调 `POST /schedules/{id}/start`（未来时间）→ `{"code":41010,"message":"未到计划开始时间（…20:00），暂不可开启"}` | ✅ |
| 批量删除：勾 2 条 → confirm 显示数量 → 删除后列表刷新无残留 | ✅ |
| 批量删除事务：ids 含已删除 id → 404 整体回滚（剩余 0 未动）；修正后成功删除 31 条测试数据 | ✅ |
| 织程日期预设：本周 → 2026-09-14 ~ 2026-09-20 闭区间 | ✅ |
| 刷新保持：`#/schedules?date=2026-09-17` 整页刷新后 URL 与筛选原样 | ✅ |
| AI 三栏 + 历史会话恢复 + 「帮我生成未来一周规划」→ 7 天分组（09-17..09-23，19/14 条，不堆一天） | ✅ |
| 采纳幂等：已采纳 planId 二次采纳 → 拒绝并弹统一提示窗 | ✅ |
| 新规划（14 条）默认全选，取消勾选 2 条后批量采纳 12 条 → 落库（API 复核 31→0 清理后账实一致） | ✅ |
| 统一错误弹窗：标题「操作失败」+ 摘要 + 详细信息折叠 + 复制 + 关闭 | ✅ |
| 织史：生成日总结 → 单条删除（confirm）→ 列表清空 | ✅ |
| AI 刷新：`#/ai` 整页刷新后会话列表 / 消息 / 规划面板（2 条剩余）完整恢复 | ✅ |

## 验收中发现并修复的前端 bug
1. `setup` 引用 `deleteReport` 未定义 → 补齐实现。
2. `notifyConfirm` 只接受对象形态参数（`{title,message,confirmText}`），勿用位置参数。
3. `apiFetch` body 传对象（勿预 JSON.stringify，避免二次序列化）。
4. 织历点日期后 `navigateTo` 提前置 currentPage 导致 hashchange 分支不触发 → 显式补 `applyRouteParams` + `syncSchedulesFromFilter`。
5. 浏览器 HTTP 缓存滞留旧 app.js → index.html 资源版本号 + nginx no-store/no-cache。

## 待办/已知
- `frontend/legacy-demo` 旧演示副本仍保留（未交付，仅参考）。
- AI 会话本地草稿防抖保存 800ms；IndexedDB 方案未启用（后端已承担登录用户持久化，游客场景当前直接走新建会话）。
