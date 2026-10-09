# 自动落梭方案（Auto-Settle）

> 状态：方案定稿（2026-10-09），待实现
> 目标：到计划结束时间自动结算计时，防止"不点停止无限续时"

---

## 一、背景与问题

- **现状**：`IN_PROGRESS` / `PAUSED` 的日程，用户不点"落梭"就无限续时——时长只在用户主动落梭时结算（服务端时间戳差）
- **关联事实**：`TIMEOUT` 状态在枚举/测试矩阵/文档中已定义，但全仓无产生它的代码（无 `setStatus(TIMEOUT)`），过期日程停留在 `PENDING`
- **目标**：到计划结束时间**自动结算**（复用现有 `end` 链路），防无限续；给终态一个真实来源

## 二、核心决策（已核实 / 已确认）

| 决策点 | 结论 | 依据 |
|---|---|---|
| 结束时间怎么算 | `plannedEnd = plannedStartTime + plannedDuration`，服务端确定性计算 | 不需要计时系统；`plannedDuration` 单位是**分钟**（docs/PROGRESS.md 口径） |
| 自动结算走哪条路 | **复用 `timerService.end(userId, scheduleId)`** | `TimerService.java` L22：`PENDING/IN_PROGRESS/PAUSED → COMPLETED`，结算时长并发放光阴砂，签名/语义完全匹配 |
| PAUSED 到点处理 | **也算 COMPLETED**，时长 = PAUSED 实际片段（分段结算） | 用户已确认：统一自动结算，简单一致 |
| 触发机制 | **@Scheduled 定时扫描**（当前）；MQ 延时留作后续 | 与 `ReminderScanTask` 同模式，零新组件、重启不丢 |
| 扫描放哪个服务 | **sorts-schedule 自己** | 需调用 `TimerService` 本地方法；notification 只做提醒，不碰结算 |

## 三、改动清单（文件级）

```
① ScheduleApplication.java         加 @EnableScheduling
   （已确认：sorts-schedule 目前未开调度）
② 新增 task/AutoSettleTask.java     调度薄层，仿 ReminderScanTask：
   @Scheduled(cron = "0 * * * * ?") scanExpired()  每分钟
   try/catch 兜住异常（调度异常会静默终止后续触发）
③ 新增 service/AutoSettleService + impl  核心逻辑可单测（仿 ReminderService 模式）：
   int scanOnce()：
     - 查询：status IN ('IN_PROGRESS','PAUSED')
            AND planned_start_time + INTERVAL planned_duration MINUTE < NOW()
     - LIMIT 100（批量上限，防一次扫太多）
     - 逐条调 timerService.end(userId, scheduleId)
     - 单条失败不影响其他（记录日志，下次扫描重试）
④ application.yml                 加 cron 配置项（仿 sorts.notification.reminder.cron）
⑤ 不改：TimerServiceImpl / ScheduleStatus / 状态机矩阵 / 积分发放（全部复用）
```

## 四、并发与幂等

- **自动 vs 手动 end 撞车**：都走 `endableFrom` 校验（`IN_PROGRESS`/`PAUSED` 才能 end），先到成功、后到跳过/409
- **⚠️ 待确认**：`TimerServiceImpl.end` 内部若是"先查状态再更新"（查-判-改），自动与手动并发存在双发积分窗口——**建议顺手改为 `UPDATE ... WHERE status IN (...)` 条件更新**（与 mall 库存条件更新同一哲学，一行改动）
- **重复扫描**：@Scheduled 单线程调度器天然不重叠；已结算日程状态变更后下次扫描自然跳过

## 五、失败与重试

- 扫描任务异常：外层 try/catch 兜住（ReminderScanTask L30-33 同款）
- 单条落梭事务失败：回滚，**下次扫描天然重试**（定时扫描相对 MQ 的优势：不需要消息重投机制）
- 积分发放失败：现有链路已兜（失败仅告警不回滚，PointsRewardService）

## 六、测试与验收

| 用例 | 断言 |
|---|---|
| 开梭不停止，到点自动结算 | 状态 COMPLETED，时长 ≈ plannedDuration |
| 暂停后到点自动结算 | 状态 COMPLETED，时长 = 实际已专注片段（分段重算） |
| 自动 vs 手动 end 并发 | 只结算一次、积分只发一次 |
| 扫描任务异常 | 不中断后续条目的结算 |

单测仿 `MetricsIndicatorTest` 风格（构造过期日程 → 驱动 `scanOnce` → 断言），无需启动调度器。

## 七、暂不做（边界）

- MQ 延时消息精准触发（M5/M7 规划，将来可加；扫描保留作兜底）
- `PENDING` 过期标注、`TIMEOUT` 产生路径（未开始日程保持 PENDING，展示层另议）
- 前端区分"手动完成 / 自动完成"来源标记（可选后续）

## 八、实现备注

- 为什么复用 end 而不是新写结算：`end` 已含"结算 + 状态迁移 + 积分发放（失败仅告警）"完整链路，自动落梭只是增加一个触发源，避免逻辑重复
- 为什么当初没做：状态机把"能做什么"做全（endableFrom 覆盖三态、矩阵 30 格测满），但触发源只有用户请求；sorts-schedule 未开调度，主动流转不在第一版范围；`docs/PROJECT.md` 有超时流转设计但实现未排上
