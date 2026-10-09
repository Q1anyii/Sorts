# 2026-10-09 · 自动落梭与超时扫描上线 + 文档同步

## 背景

第一版状态机把「能做什么」做全了（`endableFrom` 覆盖 PENDING/IN_PROGRESS/PAUSED、矩阵 30 格测满），但触发源只有用户请求：
- 用户开梭后不点落梭 → 无限续时
- 用户 PENDING 过了计划开始时间仍未开梭 → 一直停在 PENDING，日历上一直显示「待开始」
- `TIMEOUT` 状态在枚举里定义了却全仓没有任何代码产生它

## 本次改动（commit 89eaefe + fabfc2d）

### 代码

| 文件 | 改动 |
|---|---|
| `ScheduleApplication.java` | 加 `@EnableScheduling` |
| `task/AutoSettleTask.java` | 新增。`@Scheduled(cron="${sorts.schedule.auto-settle.cron:0 * * * * ?}")` 每分钟触发，薄层委托 service，异常兜住防调度线程被杀 |
| `service/AutoSettleService.java` | 新增接口 |
| `service/impl/AutoSettleServiceImpl.java` | 两段扫描：① `markPendingExpiredAsTimeout()` 一条条件 UPDATE 批量标 TIMEOUT（LIMIT 100）；② 查 `IN_PROGRESS/PAUSED AND planned_start_time + INTERVAL planned_duration MINUTE < NOW() LIMIT 100`，逐条复用 `timerService.end` 结算 |
| `mapper/ScheduleMapper.java` | 加 `markPendingExpiredAsTimeout()`：`UPDATE t_schedule SET status='TIMEOUT' WHERE status='PENDING' AND deleted=0 AND planned_start_time + INTERVAL planned_duration MINUTE < NOW() LIMIT 100` |
| `application.yml` | 加 `sorts.schedule.auto-settle.cron`（env `AUTO_SETTLE_CRON`） |
| `AutoSettleServiceImplTest.java` | 5 例：A1 正常结算 / A2 冲突跳过 / A3 异常继续 / A4 空列表 / A5 PENDING 标 TIMEOUT 不触发落梭 |

### 关键设计决策

1. **自动落梭复用 `timerService.end`**：结算、状态机校验、发积分（失败仅告警）全走 end 链路，不新写结算逻辑。
2. **PENDING → TIMEOUT 一条 SQL**：PENDING 没有计时片段、不该发奖励，不走 end（否则按 0 时长误发）；条件更新与开梭请求并发时先到者抢占。
3. **单条失败不中断其余**：BizException（状态冲突）记 info 跳过；其他异常记 error 不中断；下次扫描天然重试。
4. **定时扫描而非 MQ**：零新组件、重启不丢、天然重试；未来要秒级精度再加 MQ，扫描保留作兜底。

### 文档同步

- `docs/auto-settle-plan.md`：状态从「待实现」改为「已上线」，第七节「暂不做」里 PENDING 过期标注那条划掉，测试表补 A5。
- `docs/PROJECT.md`：项目亮点加「到点自动落梭」；测试规模 42 类/392 例 → 43 类/397 例，全量 43 类/396 例 → 44 类/401 例。
- `docs/PROGRESS.md`：最后更新日期、schedule 模块用例数 79 → 84、业务单测类 41 → 42、用例 367 → 372。
- `docs/assets/architecture.html`、`ci-flow.html`：图中测试数同步。
- 项目详解 `04-schedule日程与计时.md`：新增「九、自动落梭与超时扫描」整节（两段设计、并发幂等、为什么不用 MQ、5 例单测表），QA 补 Q13（重复发积分）、Q14（为什么不用 MQ）。

## 已知边界

- `end` 内部是「查-判-改」，自动与手动并发存在毫秒级双发积分窗口；个人项目规模可接受，上多实例时建议把 end 状态更新改成条件更新。
- 多实例部署需 ShedLock；当前单实例靠幂等兜住重复但扫描会放大。
- MQ 延时消息精准触发留作 M5/M7 规划。
