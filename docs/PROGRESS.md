# 梭子 SORTS · 项目进度与续接指南

> **用法**：新会话开始前，把本文档 + `docs/theme-design.md` + `docs/dev-setup.md` + `docs/ide-setup.md` 丢给 AI，并粘贴文末的「接续 Prompt」，即可无缝继续开发。>   
> 最后更新：2026-09-16 · 当前里程碑：**M0 / M1 / M2 / M3 / M4 / M5 完成**（构建通过，**321 个单测全绿**：common 19 + gateway 21 + user 16 + schedule 67 + ai 103 + notification 51 + mall 44）

---

## 一、项目定位

**梭子（SORTS）** —— 智能日程记录与管理平台。主题：**光阴似箭，日月如梭**。

核心功能：日程记录 + 自动耗时统计 · AI 辅助规划与周期性总结 · 积分激励（光阴砂）与个性化装扮 · 云端同步与多端适配（预留）。

**微服务架构**：请求 → 网关（鉴权 + 限流）→ 各微服务；跨模块调用一律 OpenFeign；每个服务独立数据库，禁止跨库直连。

---

## 二、技术栈与版本矩阵（已锁定，勿随意升级）

| 组件                   | 版本                     | 说明                                              |
| -------------------- | ---------------------- | ----------------------------------------------- |
| JDK                  | 本机 22，编译目标 **17**      | `maven.compiler.release=17`                     |
| Spring Boot          | **3.3.4**              | 父 POM 继承                                        |
| Spring Cloud         | **2023.0.3**           |                                                 |
| Spring Cloud Alibaba | **2023.0.3.2**         | 对应 Nacos **2.4.3**                              |
| AI 客户端                | **自实现**（M4，OpenAI 兼容协议）    | Spring AI 1.0.0 锁定 `spring-boot-starter:3.4.5`，与 Boot 3.3.4 冲突，故自实现并藏在 `ChatModelClient` 接口后 |
| ORM                  | MyBatis-Plus 3.5.7     | `mybatis-plus-spring-boot3-starter`             |
| 分布式锁                 | Redisson 3.36.0（M5 引入） |                                                 |
| 数据库                  | MySQL 8                | WSL Docker，**localhost:3307**（root / sorts_dev） |
| 缓存                   | Redis 7                | 端口 **6380**（默认端口 +1），WSL Docker                 |
| 消息队列                 | RabbitMQ 3.13          | WSL Docker，5672 / 15672（sorts / sorts_dev）      |
| 密码加密                 | spring-security-crypto | 只引 crypto，不引整套 Security                         |
| AI 模型                | **DeepSeek**           | 用户已确认                                           |

版本兼容性已核实：`Boot 3.2/3.3.x → Cloud 2023.0.x → SCA 2023.0.x`。

---

## 三、模块与端口

| 服务    | 模块目录                         | 端口   | 状态                       |
| ----- | ---------------------------- | ---- | ------------------------ |
| 网关    | `backend/sorts-gateway`      | 8080 | ✅ 完成（路由 + 鉴权 + 限流）       |
| 用户服务  | `backend/sorts-user`         | 8081 | ✅ 完成（注册登录/双令牌/积分）        |
| 日程服务  | `backend/sorts-schedule`     | 8082 | ✅ 完成（CRUD/计时状态机/日历/统计）   |
| AI 服务 | `backend/sorts-ai`           | 8083 | ✅ 完成（流式对话/工具集/规划/周期总结）    |
| 通知服务  | `backend/sorts-notification` | 8084 | ✅ 完成（通知列表/已读/提醒设置/定时提醒）   |
| 商城服务  | `backend/sorts-mall`         | 8085 | ✅ 完成（商品/购买防超卖/装扮仓库）      |
| 公共模块  | `backend/sorts-common`       | —    | ✅ 完成（Result/异常/JWT 自动装配） |

命名规范：模块与 Spring `application.name` 一律 \*\*sorts-`前缀**，Java 包名`com.sorts.*`，路由 `lb://sorts-xxx\`。

---

## 四、已完成内容明细

### M0 工程底座（✅ 已提交）

- `backend/pom.xml`：父工程，统一管理 Boot / Cloud / SCA / MyBatis-Plus / Redisson / JJWT 版本。
- `sorts-common`（自动装配，各服务引入即用）：
  - `Result<T>` + `ErrorCode`（业务码约定：40x 客户端 / 42x 限流 / 50x 服务端，业务自定义从 20000 起）
  - `BizException` + `GlobalExceptionHandler`（参数校验错误聚合首个字段提示）
  - `JwtUtil` + `JwtProperties`：双令牌签发、类型校验（`access`/`refresh`）、过期与非法区分；密钥 < 32 字节直接拒绝
  - `AuthConstants`：`Authorization`、`X-User-Id`、`X-Username`、`CLAIM_TYPE` 等
- `sorts-gateway`：
  - `AuthGlobalFilter`（Order = HIGHEST+10）：白名单放行 → 校验 access token → 剥离外部伪造的 `X-User-Id` 再写入真实身份 → 401 返回统一 JSON
  - 5 条路由（user/schedule/ai/notification/mall），关闭 `discovery.locator` 防止内部接口暴露
  - 配置全部走环境变量：`NACOS_ADDR` / `JWT_SECRET` / `NACOS_ENABLED` 等
- 工程脚本：`scripts/mvn.sh`（绕过损坏的 mvn）、`scripts/wsl-middleware.sh`（中间件一键起）
- 文档：`docs/theme-design.md`（主题规范）、`docs/dev-setup.md`（开发手册）
- 单测：`JwtUtilTest` 6 个用例全通过

### M1 用户服务（✅ 已完成）

接口（均已对齐 `api-spec.json`）：

| 方法   | 路径                            | 说明                               |
| ---- | ----------------------------- | -------------------------------- |
| POST | `/api/v1/auth/register`       | 注册并直接返回令牌；用户名重复抛 409             |
| POST | `/api/v1/auth/login`          | 登录；用户不存在与密码错误返回同一提示（防用户名枚举）      |
| POST | `/api/v1/auth/refresh`        | **无感续期**：校验类型 + Redis 留存值 → 令牌轮换 |
| POST | `/api/v1/auth/logout`         | 清除服务端 refresh token              |
| GET  | `/api/v1/users/me`            | 当前用户信息                           |
| PUT  | `/api/v1/users/me`            | 更新资料（仅覆盖非空字段）                    |
| GET  | `/api/v1/users/points`        | 光阴砂余额 + 最近流水                     |
| POST | `/api/v1/users/points/change` | 积分变动（内部 Feign 调用，防超扣）            |

关键实现：

- **双令牌无感续期**：access 30 分钟 / refresh 7 天；refresh 存 Redis（`sorts:auth:refresh:{userId}`，`RefreshTokenStore` 封装），续期时**轮换**（旧令牌立即失效，防重放）。
- **防超扣**：积分扣减走条件更新 SQL `UPDATE ... WHERE id=? AND points+? >= 0`，返回 0 行即余额不足；变动必写流水（同事务）。
- 密码 BCrypt；对外 VO 不含密码字段。
- 建表脚本：`scripts/sql/sorts_user.sql`（`t_user`、`t_points_log`）。
- 单测：`AuthServiceImplTest` 8 个 + `UserServiceImplTest` 8 个。

### M2 网关增强（✅ 已完成）

- **限流**：`RequestRateLimiter` + Redis 令牌桶（`spring-boot-starter-data-redis-reactive`）
  - 全局限流配置写在 `default-filters`，额度由环境变量控制：`RATE_LIMIT_REPLENISH`（默认 20 令牌/秒）、`RATE_LIMIT_BURST`（默认 40）
  - `userKeyResolver`：已登录按 **用户** 维度（`rate:user:{id}`），未登录按 **客户端 IP**（`rate:ip:{ip}`，优先 `X-Forwarded-For`）
  - `RateLimitResponseFilter`（order = HIGHEST+20）：把限流器的空体 429 改写为平台统一响应体，并保留 `X-RateLimit-*` 响应头
- **单测 19 个**：
  - `AuthGlobalFilterTest` 10 个：白名单放行、OPTIONS 预检、缺失/非法/过期令牌拒绝、refresh token 冒充 access 被拒、身份透传、**伪造 `X-User-Id` 被剥离且不重复注入**、query 参数传令牌、错误体可反序列化
  - `RateLimitConfigTest` 5 个：用户/ IP 维度、X-Forwarded-For 优先、unknown 兜底、用户维度优先于 IP
  - `RateLimitResponseFilterTest` 4 个：空体 429 改写、带体 429 改写、正常响应透传、order 校验

> 说明：限流不依赖本地 Redis 也能编译与跑单测（`RedisRateLimiter` 仅在请求期访问 Redis）。


### M3 日程服务（✅ 已完成）

接口（全部对齐 `api-spec.json`，网关路由 `lb://sorts-schedule` 已就绪）：

| 方法             | 路径                             | 说明                                                                                                        |        |     |          |         |
| -------------- | ------------------------------ | --------------------------------------------------------------------------------------------------------- | ------ | --- | -------- | ------- |
| POST           | `/api/v1/schedules`            | 创建日程（状态默认 PENDING、优先级默认 MEDIUM）                                                                           |        |     |          |         |
| GET            | `/api/v1/schedules`            | 条件分页：view(day/week/month) / date / startDate / endDate / status / priority / tag / keyword / sort / order |        |     |          |         |
| POST           | `/api/v1/schedules/batch`      | 批量创建（AI 规划一键采纳）                                                                                           |        |     |          |         |
| GET            | `/api/v1/schedules/active`     | 当前穿梭中的日程                                                                                                  |        |     |          |         |
| GET/PUT/DELETE | `/api/v1/schedules/{id}`       | 详情 / 更新（只覆盖非空字段）/ 逻辑删除                                                                                    |        |     |          |         |
| POST           | \`/api/v1/schedules/{id}/start | pause                                                                                                     | resume | end | cancel\` | 计时状态机五连 |
| GET            | `/api/v1/calendar`             | 月历（`year`/`month` 缺省当月，补齐每一天含空天）                                                                          |        |     |          |         |
| GET            | `/api/v1/calendar/week`        | 周视图（固定周一 → 周日 7 天）                                                                                        |        |     |          |         |
| GET            | `/api/v1/calendar/today`       | 今日概览：今日分布 + 穿梭中 + 即将开始                                                                                    |        |     |          |         |
| GET            | `/api/v1/statistics/summary`   | 汇总（period=day/week/month/year）                                                                            |        |     |          |         |
| GET            | `/api/v1/statistics/trend`     | 最近 N 天趋势（缺省 30，上限 365，空天补零）                                                                               |        |     |          |         |
| GET            | `/api/v1/statistics/tags`      | 标签分布（按时长降序 + 占比，支持 period=all）                                                                            |        |     |          |         |

关键实现：

- **时长单位统一为「秒」**：`t_schedule.actual_duration`、`t_time_record.duration` 与统计口径（`totalFocusTime` / `tagDistribution.totalDuration` / `dailyTrend.totalDuration`）全部是秒；仅 `plannedDuration` 是分钟（用户填写的计划值）。此口径与前端演示版、`api-spec.json` 保持一致（前端自行 `/60` 展示）。
- **分段计时状态机**（`TimerServiceImpl`）：每次 `start`/`resume` 开一条 `t_time_record` 片段，`pause`/`end`/`cancel` 结算该片段；总时长按**全部片段秒数重新求和**而非累加，彻底避免多次取整误差。`PENDING → IN_PROGRESS → PAUSED ⇄ IN_PROGRESS → COMPLETED`，非法流转一律 409。
- **并发语义**：同一用户同时只允许一个 `IN_PROGRESS`（`assertNoOtherRunning`，Redis 标记 + DB 状态双重确认）；`COMPLETED/CANCELLED/TIMEOUT` 为终态，不可再改、不可重复落梭（防重复发奖励）。
- **双重存储**：`t_time_record` 为权威来源，Redis（`sorts:timer:segment:{id}` / `sorts:timer:active:{userId}`，24h TTL）只做「当前片段起点」的高速缓存；Redis 键丢失时仍能按 DB 片段开始时间正确结算，DB 缺片段时用 Redis 兜底补记，保证已投入时间不丢。
- **落梭奖励**：`end` 成功后经 OpenFeign 调 `sorts-user` 的 `/users/points/change` 发 5 点光阴砂（`sorts.reward.*` 可配）。**失败只告警不回滚**主流程——M5 引入 RabbitMQ 后此处改为投递事件即可，调用方无需改动。
- **日历/统计聚合策略**：一次 `listInRange` 取回区间内全部日程，内存按自然日分组，避免「每天一条 SQL」的 N+1；个人日程量级下最简且最稳，单测也无需 mock 复杂 SQL。
- **边界防御**：`view`/`period` 非法直接 400（不静默返回全量）；`pageSize` 上限 200（MyBatis-Plus 分页插件 + `maxLimit` 双保险）；`month` 越界拒绝；日均口径只算「已过去天数」，避免月初看月统计被未来空白天数稀释；趋势点上限 365；脏状态数据（枚举非法）按 PENDING 兜底不让概览崩溃。
- **越权隔离**：所有读写先 `requireOwned`，他人日程一律按「不存在」返回（不暴露存在性）。
- 建表脚本：`scripts/sql/sorts_schedule.sql`（`t_schedule`、`t_time_record`）。
- 单测：**64 个**（`ScheduleServiceImplTest` 17 + `TimerServiceImplTest` 18 + `CalendarServiceImplTest` 10 + `StatisticsServiceImplTest` 11 + `DateRangeTest` 8）。

> ⚠️ 踩坑记录：MyBatis-Plus 配置了 `logic-delete-field: deleted` 后，**不能**自己 `setDeleted(1)` 再 `updateById`（逻辑删除字段会被排除在 SET 之外），必须用 `deleteById` 触发生成的逻辑删除；另外分页插件必须显式注册 `PaginationInnerInterceptor`，否则 `selectPage` 不拼 LIMIT 会退化成全表查询。

### M4 AI 服务（✅ 已完成）

接口（全部对齐 `api-spec.json`，网关路由 `lb://sorts-ai` 已就绪）：

| 方法   | 路径                          | 返回                        | 说明                                          |
| ---- | --------------------------- | ------------------------- | ------------------------------------------- |
| POST | `/api/v1/ai/chat`           | SSE（默认）/ JSON            | 流式对话；`?stream=false` 走 JSON                  |
| POST | `/api/v1/ai/plan`           | JSON（默认）/ SSE            | 生成规划；`?stream=true` 走 SSE                   |
| POST | `/api/v1/ai/plan/{planId}/adopt` | JSON                 | 采纳规划 → 批量建日程                                 |
| POST | `/api/v1/ai/summary/daily`  | SSE（默认）/ JSON            | 日报（同步）                                      |
| POST | `/api/v1/ai/summary/monthly` | **202** + reportId        | 月报（异步）                                      |
| POST | `/api/v1/ai/summary/yearly` | **202** + reportId        | 年报（异步）                                      |
| GET  | `/api/v1/ai/reports`        | 分页                        | 报告列表（可按 type 过滤）                             |
| GET  | `/api/v1/ai/reports/{id}`   | JSON                      | 报告详情                                        |

拆分提交（每个功能一次提交）：骨架与配置 → 自实现 DeepSeek 客户端 → 工具集 → 对话助手 → 规划 → 总结报告 → 接口层。

**一、为什么不用 Spring AI**（重要决策）

`spring-ai-starter-model-openai:1.0.0` 的传递依赖把 `spring-boot-starter` **锁定在 3.4.5**，而本项目是 Boot 3.3.4（Cloud 2023.0.3 / SCA 2023.0.3.2 不支持 Boot 3.4）；1.0.0-M5/M6 里程碑版在阿里云镜像上取不到。因此**按 OpenAI 兼容协议自实现了一个薄客户端**，藏在 `ChatModelClient` 接口之后：

```java
LlmResult complete(ChatCompletionRequest request);
LlmResult stream(ChatCompletionRequest request, Consumer<String> onContentDelta);
```

将来升级到 Boot 3.4+ 想换回 Spring AI，只需新增一个实现类（把 `ChatClient` 包进来），业务代码零改动。

- `protocol/`：`ChatMessage` / `ToolCall` / `ToolDefinition` / `ChatCompletionRequest` / `ChatCompletionResponse` / `ChatChunk` / `LlmResult`
- `SseDataParser`：解析 `data:` 行，跳过注释与 `[DONE]`；**JSON 异常不静默丢弃**（静默会表现为「AI 回复缺一截」，极难排查）
- `ToolCallAccumulator`：按 `index` 归并流式 tool_calls，`arguments` 分片**追加**而不是覆盖
- `DeepSeekChatClient`：JDK `HttpClient`，流式增量回调 + usage 统计 + 401/403/429/400 可读错误映射 + `length` 截断告警
- 未配置密钥不阻断启动：`isConfigured()` 为假时接口返回可读的 503，无密钥也能跑单测与联调

**二、工具集（Tool Calling，双钥匙写权限）**

| 工具                 | 级别  | 说明                              |
| ------------------ | --- | ------------------------------- |
| `querySchedules`   | 读   | 日程列表（日期/视图/状态/优先级/标签/关键词）       |
| `queryStatistics`  | 读   | 统计汇总（完成率/专注时长/标签分布/趋势）          |
| `queryPoints`      | 读   | 光阴砂余额与流水                        |
| `getProfile`       | 读   | 昵称/邮箱/余额/装扮                     |
| `createSchedule`   | 写   | 创建单条日程                          |
| `createSchedules`  | 写   | 批量创建（上限 20）                     |

`ToolRegistry` 统一收口三段，避免权限逻辑散落：

1. **声明过滤**——未授权的工具不下发给模型。模型看不见的工具不可能被调用，比「让它调、再拒绝」更省 token 也更难被提示词绕过；
2. **执行前复核**——下发时过滤过一次，执行时再校验一次（模型可能凭历史上下文猜出工具名，声明过滤不足以作为唯一防线）；
3. **异常兜底**——工具失败不以异常打断整轮对话，而是把错误作为「观察结果」回灌，让模型有机会改参数重试或如实告知用户。

**双钥匙**：写工具需要 `sorts.ai.tool.allow-write=true`（服务端）**且**本次请求 `allowWrite=true`（前端在用户点确认后置位）。二者缺一，工具既不下发也不执行。`isWriteDenied()` 供对话服务推导「需要先开启写入」的建议操作。

工具集通过 **OpenFeign** 调用 `sorts-schedule` / `sorts-user`，契约 DTO 在 `client/dto/` 内**自成一套副本**（不直接依赖对方 VO，避免发布节奏被绑死）。`Results.unwrap` 把下游「HTTP 200 + code≠0」显式转为异常——否则坏数据会静默流进 AI 上下文，表现为「AI 一本正经地胡说」。

**三、结果规范化**（把易错点集中处理，而不是反复叮嘱模型）

- `ToolJsonCodec`：工具层专用 ObjectMapper，时间**固定输出 ISO-8601**。若复用全局 mapper，一旦有人打开 `write-dates-as-timestamps`，模型会看到 `[2026,9,16,9,0]` 并当普通数组照抄进回答——不崩溃但结果错，必须在源头掐掉。
- 统计时长**秒 → 分钟**、比率 → 百分比在边界完成：给模型 7200 秒，它会说成「7200 分钟」。
- 上下文预算：趋势点截断至最近 30、`pageSize` 上限 50、批量上限 20。
- `DateTimes` 宽松解析模型常见的时间变体（ISO / 空格 / 斜杠 / `H:mm`）。

**四、对话与规划**

- **工具循环**：模型不再要求调工具即收尾；达到 `max-tool-rounds`（默认 4）上限时**补一次不带工具的调用强制出结论**，否则用户拿到空回复。
- **上下文只存「用户问 + 最终答」**（Redis，`sorts:ai:chat:{userId}:{conversationId}`，TTL 12h）：中间的 tool 结果是**有时效性的快照**，留在上下文里会让模型拿过期数据回答新问题。Redis 故障不阻断对话（记不住上文可接受，不能聊天不可接受）。
- **建议操作由事实推导**，不交给模型生成：创建成功带 `scheduleIds`、权限被拒带 `requiresWritePermission`。模型编出来的按钮点下去会失败，比没有按钮更伤信任。
- **规划先落库再返回**（`planId` = UUID，24h 有效期）：用户可能看半天才采纳，前端刷新不该丢；同一 `planId` **只允许采纳一次**，否则双击「采纳」会得到两份重复日程。建议的 `suggestedStart` 只有 `HH:mm`，**日期由服务端补**——模型不接触日期就不会算错日期。
- **结构化输出用低温度**（规划 0.3、报告 0.6），并由 `JsonPayloads` 容错提取（剥掉 ```` ```json ```` 围栏与寒暄）。

**五、总结报告**

- **同步/异步的划分依据是「用户能不能等」**：日报数据量小、点完就想看 → 同步 + 流式；月/年报区间大、模型耗时长 → 先落 `GENERATING` 记录并立即返回 `reportId`，前端轮询。
- `ReportComposer` 收口取材与撰写，日报/月报/年报共用同一口径（两份实现迟早会给出不同说法）。
- **统计服务不可用不阻断报告**：降级为「仅依据明细」并如实说明数据缺失。
- `ReportGenerator` **独立成 Bean**：`@Async` 依赖代理，同类内自调用会退化成同步执行（「明明加了 `@Async` 却仍然阻塞」最常见的坑），拆开从结构上不给它机会。后台任务异常不外抛，统一落 `FAILED` + 原因，避免记录永远停在进行中。线程池拒绝策略用 `CallerRunsPolicy`：宁可慢一次，也不静默丢任务。
- 完成率与总时长**以统计服务为准**，不采信模型的复述。
- 报告是**快照**而不是视图：生成时的完成率与时长一并写入，之后用户补录旧日程也不会改写历史报告。

**六、JSON 与 SSE 双通道（接口层关键技术点）**

Spring MVC 是按**方法声明的返回类型**挑选返回值处理器的：若把返回类型写成 `Object`，`SseEmitter` 不会被 SSE 处理器接管，而是被交给 Jackson 序列化 —— 前端拿到的是一堆字段而不是事件流。因此用 `params` 条件把两种响应拆成两个处理方法：

- `/chat`、`/summary/daily`：`params = "!stream=false"` → SSE（**默认流式**，符合 api-spec 默认 true）；`params = "stream=false"` → JSON
- `/plan`：`params = "!stream=true"` → JSON（**默认非流式**，符合 api-spec 默认 false）；`params = "stream=true"` → SSE

**传输方式由 query 参数决定**；请求体中的 `stream` 字段为契约兼容保留，不参与路由。流式生成在独立线程执行（SSE 要求请求线程尽快返回 emitter），否则连接超时时间会被当成模型超时时间。SSE 事件约定：

```
event: delta   data: {"content":"增量文本"}
event: done    data: { ...最终结构化结果... }
event: error   data: {"code":503,"message":"..."}
```

**七、数据与建表**

- 建表脚本：`scripts/sql/sorts_ai.sql`（`t_ai_report`、`t_schedule_plan`）
- 对话上下文刻意**不落库**（存 Redis、按会话过期）——对话是可丢弃的临时状态，写库会带来无意义的写放大，也让「用户要求清空历史」变得尴尬
- 配置：`DEEPSEEK_API_KEY`（必填，不入库）/ `AI_TOOL_ALLOW_WRITE`（默认 false）/ `DEEPSEEK_MODEL` / `AI_TEMPERATURE` / `AI_MAX_TOKENS`

- 单测：**103 个**（客户端与工具 54 + 对话与流式 16 + 规划 16 + 报告 17）

> ⚠️ 踩坑记录：① 流式 `tool_calls` 必须按 `index` 归并且参数分片**追加**，否则发给模型的是残缺 JSON；② 工具回灌的 JSON 若依赖环境里的 ObjectMapper，时间格式会随全局配置漂移，必须用专用 mapper 固定 ISO；③ 幂等保护要做在「采纳」上而不是「创建」上，否则用户双击就产生重复日程。

### M5 商城 + 通知（✅ 已完成）

接口（全部对齐 `api-spec.json`）：

| 方法   | 路径                                | 说明                        |
| ---- | --------------------------------- | ------------------------- |
| GET  | `/api/v1/notifications`           | 通知列表（附未读总数）               |
| PUT  | `/api/v1/notifications/{id}/read` | 标记单条已读（幂等）                |
| PUT  | `/api/v1/notifications/read-all`  | 全部标记已读                    |
| GET  | `/api/v1/notifications/settings`  | 提醒设置（未自定义时返回默认值）          |
| PUT  | `/api/v1/notifications/settings`  | 更新提醒设置                    |
| GET  | `/api/v1/mall/items`              | 商品列表（附光阴砂余额）              |
| GET  | `/api/v1/mall/items/{id}`         | 商品详情（附是否已拥有 / 购买人次）       |
| POST | `/api/v1/mall/purchase`           | 购买商品                      |
| GET  | `/api/v1/users/wardrobe`          | 装扮仓库                      |
| PUT  | `/api/v1/users/wardrobe/active`   | 切换当前装扮                    |

内部接口（网关不路由，需 `X-Internal-Token`）：

| 方法   | 路径                                          | 提供方            |
| ---- | ------------------------------------------- | -------------- |
| GET  | `/internal/schedules/upcoming`              | sorts-schedule |
| POST | `/internal/notifications`（另有 `/batch`）      | sorts-notification |

拆分提交（每个功能一次提交）：服务间凭证 → 日程内部接口 → 通知骨架与列表 → 提醒设置 → 定时提醒 → 商城骨架与商品查询 → 购买流程 → 装扮仓库。

**一、服务间调用凭证 `X-Internal-Token`（补掉 M0 遗留技术债）**

- `@InternalApi` 注解 + `InternalApiInterceptor` 只拦**标注过的处理器**。为什么不用路径前缀判断：前缀靠约定，改名或新增路径就会静默失去保护；注解长在代码上，编译期可见、评审时看得见。
- 出站由 `InternalFeignInterceptor` 按**路径白名单**（默认 `/internal/`、`/api/v1/users/points/change`）自动附加凭证 —— 需要人记住的安全约定迟早会漏。
- **网关在鉴权分支与白名单分支都剥离外部伪造的该请求头**：服务间调用不经过网关，所以经网关进来的内部凭证只可能是伪造的。
- 未配置 `sorts.internal.token` 时**拒绝放行**（fail-closed）：配置缺失不该退化成「默认敞开」。凭证比较用 `MessageDigest.isEqual` 定长比较，避免逐字节试探。
- 内部接口一律放 `/internal/**`：网关只路由 `/api/v1/**`，这个前缀在外部网络**根本不存在**，与凭证校验构成两道防线。

**二、通知服务（8084）**

- **列表/已读**：非法类型直接 400（静默忽略筛选会返回全部通知，让调用方误以为筛选生效）；未读角标**独立统计**，否则筛「已读」时角标会莫名归零；已读的属主校验交给 SQL，重复标记幂等。
- **提醒设置**：用户从未改过时返回默认值并标注 `customized=false`，**不预先写一行空记录**——「没有偏好」与「偏好即默认值」语义不同，后者一旦调默认值就会被历史数据钉死。
- **免打扰**：跨天区间（23:00–07:00）集中处理一次；`start == end` 视为「未配置」而不是 24 小时静默；启用免打扰却没给时间直接 400（否则用户以为生效、实际不生效）。
- **定时提醒**：`@Scheduled` 每分钟扫描，业务逻辑收在 `ReminderService.scanOnce()` 里、由 `scanAt(now)` 驱动，因此单测可以直接驱动而不必启动调度器。四类判断——未到点 / 免打扰 / 无渠道 / 重复。
  - **幂等**：`t_reminder_log` 唯一键 + `INSERT IGNORE` **先占额度再发通知**（at-most-once）。提醒是「过期即无价值」的信息，宁可漏一次也不要重复轰炸；反过来做就会在异常重试时连发多条。
  - **免打扰不占额度**：安静时段静默，结束后若日程仍在扫描窗口内会自动补上——用户要的是「那个时间段别响」，不是「那条提醒作废」。
  - **降级**：下游异常或非成功码一律跳过本轮，且不误判成「没有日程」；调度层再兜一层异常（调度任务里抛异常会静默终止后续触发，这是最难排查的一类「定时器不跑了」）。
  - 渠道只落 **APP（站内信）**，EMAIL/SMS 为预留位：勾了也不会发，故未勾选 APP 时不产生通知。

**三、商城服务（8085）**

- **商品浏览**：仅上架商品、非法类型 400、按权重 + id 排序保证翻页稳定；余额获取失败**降级为 null 而不是整页 500**——商城应该能被浏览。已下架商品仍可查详情（用户需要看到自己已拥有的装扮）。
- **购买顺序固定为「加锁 → 扣积分 → 本地事务落库」**，顺序不可颠倒：积分不足是购买最常见的失败原因，先扣可在「尚未占用库存」时快速失败；反过来先落库，失败时就要删除已写好的订单（账目不该被删）。
- **锁**：Redisson 按**商品维度**加锁（`sorts:mall:lock:item:{itemId}`），不同商品互不阻塞、同商品严格串行；抢锁失败返回 429。**Redis 不可用时 fail-closed 拒绝购买**——库存与积分不允许在没有互斥保护的情况下裸奔。
- **事务**：事务性落库放在独立 Bean `PurchasePersister` 上（与 M4 的 `ReportGenerator` 同一个坑：`@Transactional` 依赖代理，同类自调用会静默退化成普通方法调用）；事务内「扣库存 + 写购买记录 + 发装扮」同生共死。
- **双重防超卖**：Redisson 锁 + 数据库条件更新 `UPDATE ... WHERE stock > 0`。后者是最后防线——即便锁因租期到期提前释放，也不可能有事务把库存扣成负数。无限库存（`-1`）跳过扣减，补偿也必须带 `stock >= 0`，否则会把 `-1` 补成 `0` 变成「售罄」。
- **补偿**：本地落库失败即调用退款（`+price`）；补偿本身失败以 ERROR 打出 `userId/itemId/金额` 供对账，**绝不静默**。锁内只做四件事（读商品、查重、扣积分、落库），通知投递移出锁外，避免一次下游抖动就把锁的租期耗光。
- **快照**：购买记录落商品名与成交价，商品后续改名/调价不改写历史账目。
- **装扮仓库**：一次 `selectBatchIds` 取全商品避免 N+1；切换装扮时**类型判定以仓库记录为准**，请求里的 `type` 只做一致性校验——否则前端传错类型就能让多个同类装扮同时生效。皮肤/头像/徽章同类互斥（先全置 0 再置 1），贴纸可叠加；未拥有的装扮按「不存在」返回 404，顺带挡掉通过切换接口白嫖。

**四、网关路由顺序（修掉一个真实缺陷）**

`/api/v1/users/wardrobe/**` 同时命中 mall 与 user 两条路由，而 Spring Cloud Gateway **按声明顺序取第一条匹配项**——原配置把 user 排在前面，装扮接口会被转发到用户服务（404）。已把 mall 路由上移并在配置里写明「顺序即优先级」。

**五、数据与建表**

- 建表脚本：`scripts/sql/sorts_notification.sql`（`t_notification`、`t_reminder_setting`、`t_reminder_log`）、`scripts/sql/sorts_mall.sql`（`t_mall_item`、`t_purchase_record`、`t_wardrobe_item` + 7 件种子商品）
- 种子商品用**显式主键 + `INSERT IGNORE`**：脚本可重复执行且 ID 稳定，前端不必担心商品 ID 漂移。
- 单测：**145 个**（common 内部凭证 13 + gateway 2 + schedule 3 + notification 51 + mall 44 + 提醒设置 12 等）

> ⚠️ 踩坑记录：① `LocalTime.parse("7:00")` 会抛异常（ISO 要求小时两位），手输/历史数据里的 `H:mm` 必须用显式 `DateTimeFormatter` 宽松解析，否则一次脏数据就会中断整轮提醒扫描；② `ReminderSettingService.findEffective(Long)` 与 `findEffective(Collection)` 构成重载后，调用点传 `Collectors.toCollection(...)` 的结果会触发方法引用的推断歧义——改成命名更明确的 `findEffectiveAll` 一次性消除；③ 用 `-pl` 只构建单个子模块时，`sorts-common` 会从**本地仓库**解析（可能是旧版本），必须 `-pl sorts-common,<目标模块>` 或用 `-am` 才会走 reactor。

---

## 五、关键决策记录

| 议题                      | 结论                                           | 理由                                |
| ----------------------- | -------------------------------------------- | --------------------------------- |
| 前端路线                    | **Vue3 + Vite 工程化重写**                        | 现有 `frontend/` 是单文件 CDN 演示页，后续功能多 |
| AI 模型                   | **DeepSeek**（OpenAI 协议）                      | 已确认，Tool Calling 支持好、成本低          |
| 消息队列                    | **RabbitMQ**（Docker）                         | 贴合需求文档，异步解耦日程完成→积分/通知             |
| 业务服务是否上 Spring Security | **否**，只用 crypto 做 BCrypt                     | 鉴权集中在网关，避免过滤器链重复建设                |
| 用户身份传递                  | 网关写 `X-User-Id` 头，服务端 `@RequestHeader` 读取    | 简单、可测，避免 ThreadLocal 隐式传递         |
| 主题方向                    | **「织锦流光」**（吸收流光与时间刻度）                        | 三方案对比见 `docs/theme-design.md`     |
| 中间件部署                   | WSL 内 Docker（Nacos/RabbitMQ）+ 本机 MySQL/Redis | 用户环境，Windows 侧用 localhost 直连      |
| AI SDK 选型               | **自实现 OpenAI 兼容客户端**（M4）                     | Spring AI 1.0.0 锁定 Boot 3.4.5，与本项目 Boot 3.3.4 冲突；藏在 `ChatModelClient` 接口后，将来可无痛换回 |
| AI 写数据权限                | **双钥匙**：服务端开关 + 单次请求用户确认                     | 非 AI Native 项目，写操作必须显式授权，避免「AI 擅自改用户数据」   |
| 同一路径 JSON / SSE 双通道     | `params` 条件拆成两个处理方法                            | Spring MVC 按「方法声明返回类型」选处理器，返回 `Object` 会让 `SseEmitter` 被 Jackson 序列化 |
| AI 对话上下文                 | **Redis**（不落库，12h TTL）                         | 可丢弃的临时状态；「记不住上文」可接受，「不能聊天」不可接受         |
| 服务间调用鉴权（M5）             | `X-Internal-Token` + `@InternalApi` **注解**            | 路径前缀靠约定，改名/新增路径就静默失去保护；注解编译期可见、评审时看得见 |
| 内部接口路径（M5）               | 一律 `/internal/**`（网关**不路由**该前缀）                 | 外部网络根本不可达，与凭证校验构成两道防线                |
| 购买顺序（M5）                 | **加锁 → 扣积分 → 本地事务落库**（顺序不可颠倒）                 | 积分不足是最常见失败，先扣能在「未占库存」时快速失败；反向则失败时要删订单（账目不该被删） |
| 防超卖（M5）                  | Redisson 按商品加锁 **+** `UPDATE ... WHERE stock > 0` | 双保险：锁因租期到期失效时，数据库仍能兜住                     |
| 提醒幂等（M5）                 | `t_reminder_log` 唯一键 + `INSERT IGNORE` 先占额度      | at-most-once：提醒过期即无价值，宁可漏一次也不要重复轰炸         |
| 提醒渠道（M5）                 | 只落 **APP（站内信）**                            | EMAIL/SMS 未接入，勾了也不会发；不勾 APP 时不产生通知，不做假承诺   |

### 已知限制 / 待办技术债

1. ~~Redis 端口 / Nacos / RabbitMQ 未就绪~~ → **已解决**（2026-09-16）：全部通过 `scripts/wsl-middleware.sh start` 以 Docker 方式启动，Windows 侧端口探测 6380 / 3307 / 8848 / 5672 / 15672 均可达。
2. **WSL 内已无 MySQL**：数据库统一由 Docker 容器 `sorts-mysql` 承载（3307）；Windows 宿主上原有的 3306 实例不作为项目数据源。
3. ~~内部接口（`/users/points/change`）目前只依赖网关透传的用户头，缺少服务间密钥校验，M5 需补 `X-Internal-Token` 校验。~~ → **已完成**（M5）：`@InternalApi` + `InternalApiInterceptor`，网关同时剥离外部伪造的 `X-Internal-Token`。
4. ~~网关尚未实现限流~~ → **已完成**（M2：Redis 令牌桶 + 429 统一响应）。
5. `sorts-user` 尚无 `@SpringBootTest` 级别的集成测试（需要真实 DB/Redis，计划 M7 用 Testcontainers 或连 WSL 中间件）。
6. WSL 命令受沙箱限制，AI 无法直接执行 WSL 内命令，中间件相关操作需用户手动执行脚本。
7. 网关尚无路由级限流差异化配置（当前全局限流），如需对登录接口单独收紧，在对应路由 `filters` 中覆盖 `RequestRateLimiter` 参数即可。
8. **AI 服务缺密钥时不可用**：`DEEPSEEK_API_KEY` 必须通过环境变量注入（仓库内不留密钥），未配置时相关接口返回 503 与可读提示；服务本身仍可正常启动、跑单测。
9. **`allowWrite` 是本项目的扩展字段**（api-spec 未定义）：长在 `/ai/chat` 请求体上，用于开启单次对话的写权限；前端需在用户确认后置位。
10. `sorts-ai` 尚无真实调用模型与真实 DB 的集成测试（单测全部用 Mock，覆盖的是编排逻辑与边界），计划 M7 用 Testcontainers 补齐。
11. **统一响应体与 api-spec 的已知偏差**（M0 起确立，全项目一致）：api-spec 的 `ApiResponse.code` 示例为 `200`、分页字段名为 `records`；本项目实际用 `code=0` 表示成功、分页用 `PageData.list`。改动会波及全部服务与前端，故保持现状并在此备案。
12. 月报/年报为异步生成，暂未做「同周期重复生成」的去重：同一用户对同一月份多次点击会生成多条报告（列表按时间倒序展示）。如需收敛，后续可加「同 periodKey 覆盖」策略。
13. **购买链路存在极小的不一致窗口**（M5，重要）：扣积分成功与本地事务提交之间若进程被杀，补偿代码不会执行，表现为「扣了光阴砂没拿到装扮」。彻底消除需要事务性消息（**M7 的 RabbitMQ outbox + 对账任务**）。当前以 ERROR 级别的结构化日志（含 `userId`/`itemId`/金额）兜底，可用 `t_purchase_record` + `t_wardrobe_item` 对账。
14. **提醒渠道只实现了 APP（站内信）**（M5）：`EMAIL` / `SMS` 在 `ReminderChannel` 中作为扩展位存在，设置里可勾选但不会真正发出；接入第三方通道后只需在 `ReminderServiceImpl` 的发送环节分支即可。
15. **定时提醒是单实例语义**（M5）：`@Scheduled` 在多实例部署下每个实例都会扫描。当前靠 `t_reminder_log` 唯一键保证**不会重复推送**（幂等生效），但会产生多余的 Feign 调用。如需多实例，加 ShedLock 或改用 RabbitMQ 延迟队列。
16. **商城只实现了「买」**（M5）：商品上架/下架/改价暂无后台接口，靠 `scripts/sql/sorts_mall.sql` 的种子数据维护；`PROMOTION` 类型的营销推送也还没有触发入口。
17. **购买接口的错误码与 api-spec 存在偏差**（M5）：api-spec 对 `/mall/purchase` 只声明 `400 积分不足或商品已售罄`；本项目沿用既有约定——积分不足透传 `sorts-user` 的 409，售罄/已下架/已拥有用 409，抢锁失败用 429。与第 11 条同属「统一响应体与 api-spec 的已知偏差」，前端请以 `Result.code` 为准。

---

## 六、本地环境约定（重要）


### 构建

| 场景                            | 命令                                                              |
| ----------------------------- | --------------------------------------------------------------- |
| **IDEA 内**（推荐）                | 右键 `backend/pom.xml` → Add as Maven Project，之后用 IDEA 的 Maven 面板 |
| 你自己的终端（Git Bash / PowerShell） | `cd backend && ./mvnw clean install`（或 `mvnw.cmd`）              |
| WSL / Linux / CI              | `cd backend && ./mvnw clean install`                            |
| **AI 沙箱内**                    | `bash scripts/mvn.sh`（必须用这个）                                    |

```bash
bash scripts/mvn.sh                # clean install（全模块 + 单测）
bash scripts/mvn.sh clean test     # 只跑测试
```

> **⚠️ 2026-09-16 更正：此前「本机 mvn 已损坏」是误诊。**>   
> 本机 `mvn -v` 输出 `Apache Maven 3.9.15`，位于 `E:\develop`，**完全正常**。>   
> 真实原因是 WorkBuddy 沙箱给 shell 注入了 `MSYS_NO_PATHCONV=1` 与 `MSYS2_ARG_CONV_EXCL=*`，>   
> **关闭了 MSYS 路径自动转换**；而 Maven 的 `bin/mvn` 是 POSIX sh 脚本，把>   
> `/c/Users/.../plexus-classworlds.jar` 这类 POSIX 路径直接交给原生 `java.exe`，>   
> Windows 版 java 解析不了 → 报 `ClassNotFoundException: ...classworlds.launcher.Launcher`。>   
> `scripts/mvn.sh` 显式用 `cygpath` 把路径转成 `C:/...` 再交给 java，因此沙箱内始终可用。>   
> 对照验证：`mvn -v` 失败，但 `env -u MSYS_NO_PATHCONV -u MSYS2_ARG_CONV_EXCL bash -c 'mvn -v'` 成功。
>
> 仓库已内置 **Maven Wrapper**（`backend/mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`，>   
> 仅脚本模式 + 阿里云镜像源），IDE / CI / 其他开发者无需预装 Maven。>   
> 受管 Maven：`~/.workbuddy/binaries/maven/apache-maven-3.9.16`；`~/.m2/settings.xml` 已配阿里云镜像；>   
> `scripts/mvn.sh` 还会透传 `HTTP_PROXY/HTTPS_PROXY` 给 JVM（代理 `127.0.0.1:8531`，仅加速用，不影响 localhost）。
>
> **IDE 运行细节（JDK/模块/运行配置/报错速查）见 [`docs/ide-setup.md`](./ide-setup.md)。**

### 中间件（WSL 内执行）

```bash
bash scripts/wsl-middleware.sh start    # Redis→6380、Nacos 8848、RabbitMQ 5672/15672、建 5 个库
bash scripts/wsl-middleware.sh status
```

MySQL 账号密码通过 `MYSQL_USER` / `MYSQL_PASSWORD` 传入，服务侧用 `MYSQL_USER`/`MYSQL_PASSWORD`/`REDIS_PORT` 等环境变量覆盖。

### Git 与推送

- 远端：**`sorts`**（注意：远端名不是 `origin`）→ `https://github.com/Q1anyii/sorts.git`
  - 推送命令：`git push -u sorts main`
- 凭据：Git Credential Manager（`git config --global credential.helper manager`）
- **⚠️ 推送失败的已知原因（2026-09-16 诊断）**：
  1. 账户 `Q1anyiii`（SSH 密钥所属）已被 GitHub **封停** → 不要用 SSH 推送。
  2. `Q1anyii` 的 fine-grained PAT 当前 **Contents 权限为只读**，推送报       
     `403 Permission to Q1anyii/sorts.git denied to Q1anyii`，       
     API 写文件报 `Resource not accessible by personal access token`。       
     **修复方式**：GitHub → Settings → Developer settings → Fine-grained tokens → 编辑该令牌       
     → Repository access 勾选 `sorts`（或 All repositories）       
     → Permissions → Repository permissions → **Contents: Read and write** → 保存。       
     或改用 classic token（勾选 `repo` 作用域）。
- 修复凭据后推送命令：`git push -u origin main`（或用内嵌令牌临时推送）
- 提交规范：Conventional Commits（`feat(scope): subject`），**每完成一个功能点提交一次；每个模块单测通过后推送**。
- 待推送提交（本地已提交，等令牌权限修复）：M0 三次提交 + M1 两次提交

---

## 七、后续路线图（含验收标准）

| 里程碑               | 内容                                                                               | 验收标准                                                                    |
| ----------------- | -------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| ~~**M2 网关增强**~~ ✅ | ~~Redis 限流（令牌桶）、鉴权链路单测、路由单测~~                                                    | 已完成：429 统一响应 + 19 个单测                                                   |
| ~~**M3 日程服务**~~ ✅ | ~~日程 CRUD、计时状态机、日历聚合视图、统计接口、落梭发积分（Feign 调 user）~~                                | 已完成：非法流转被拒；总时长按片段重算；`time_record` 可回溯每段耗时；64 个单测                        |
| ~~**M4 AI 服务**~~ ✅    | ~~Spring AI + DeepSeek 流式输出、工具集（Tool Calling）、规划生成、日/月/年总结（异步 + `ai_report` 表）~~ | 已完成：AI 可通过工具查日程/建日程/查统计；总结报告落库可查询；**自实现客户端替代 Spring AI**（版本冲突）；103 个单测 |
| ~~**M5 商城 + 通知**~~ ✅ | ~~商品/购买（Redisson 锁防超扣）/装扮仓库；通知列表/已读/定时提醒~~                     | 已完成：Redisson 按商品加锁 + 条件更新双保险防超卖；积分不足与售罄路径都有单测；定时提醒按用户提前量与免打扰生成并幂等去重；**顺带补掉服务间凭证技术债**；145 个单测 |
| **M6 前端**         | Vue3+Vite 工程化重写、主题落地（CSS tokens/织锦日历/流光计时/穿梭过场）、AI 流式对话 UI、商城与装扮页                       | 主题规范 100% 落地；移动端可用                                                      |
| **M7 CI/CD + 测试** | GitHub Actions（矩阵构建 6 个服务 → ACR 推送）、Testcontainers 集成测试                          | 参考 `E:\工作文件\AgentProject\.github\workflows\acr-cicd.yml`，部署阶段留开关（当前不部署） |

### AI 工具集设计要点（M4 已落地）

非 AI Native 项目 → 由各模块**显式授权** AI 可用能力，工具集自实现：

- `querySchedules(dateRange)`：读取日程，用于规划与总结 → **已落地**
- `createSchedules(List<PlanItem>)`：把 AI 生成的规划**一键落库**（对应 `/schedules/batch`）→ **已落地**（另有单条 `createSchedule`）
- `queryStatistics(period)`：取统计汇总供分析 → **已落地**
- `queryPoints()` / `getProfile()`：个性化建议 → **已落地**

实现方式：在 `sorts-ai` 内通过 OpenFeign 调用各服务（**权限边界**：读工具常开；写工具走**双钥匙**——服务端 `sorts.ai.tool.allow-write` 与单次请求 `allowWrite` 同时为真才下发与执行）。

> 与最初设想的差异：`savePlanToBoard(planText)` 未做成工具，而是独立成 `POST /ai/plan` 端点。原因：规划生成需要「低温度 + 强制 JSON + 落库 + 返回 planId」这一整套流程，塞进工具调用会让模型在一次对话里同时做「写 prompt」和「调工具」两件事，结构上更脆。

---

## 八、目录结构现状

```
D:\SORTS(梭子)/
├── api-spec.json            # OpenAPI 契约（开发接口前先查它！）
├── 需求分析.docx             # 需求源文档（已忽略入库）
├── backend/
│   ├── pom.xml              # 父工程（已注册 7 个模块）
│   ├── sorts-common/        # ✅ 公共模块（Result/异常/JWT/PageData/服务间凭证）
│   ├── sorts-gateway/       # ✅ 网关（路由 + 鉴权 + 限流 + 剥离伪造内部凭证）
│   ├── sorts-user/          # ✅ 用户服务
│   ├── sorts-schedule/      # ✅ 日程服务（CRUD/计时/日历/统计 + 内部提醒取数接口）
│   ├── sorts-ai/            # ✅ AI 服务（llm 客户端 / tool 工具集 / service 对话·规划·报告 / controller）
│   ├── sorts-notification/  # ✅ 通知服务（通知列表·已读 / 提醒设置 / 定时提醒扫描）
│   └── sorts-mall/          # ✅ 商城服务（商品 / 购买防超卖 / 装扮仓库）
├── frontend/                # 单文件演示版（M6 重写为 Vite 工程）
├── docs/
│   ├── theme-design.md      # 主题设计规范
│   ├── dev-setup.md         # 开发手册（中间件、端口、命令、内部凭证与购买一致性约定）
│   ├── ide-setup.md         # IDEA 运行手册（导入 Maven、JDK 17、共享运行配置、报错速查）
│   └── PROGRESS.md          # 本文档
├── .run/                    # IDEA 共享运行配置（6 个服务 + Compound「全部服务」）
├── scripts/
│   ├── mvn.sh               # 构建封装（必须用）
│   ├── wsl-middleware.sh    # 中间件一键脚本（start/stop/status/sql/logs）
│   └── sql/
│       ├── 00-init-databases.sql     # 建 5 个库（先执行）
│       ├── sorts_user.sql            # 用户库建表
│       ├── sorts_schedule.sql        # 日程库建表
│       ├── sorts_ai.sql              # AI 库建表（报告表、规划表）
│       ├── sorts_notification.sql    # 通知库建表（通知、提醒设置、提醒留痕）
│       └── sorts_mall.sql            # 商城库建表（商品、购买记录、装扮仓库 + 种子商品）
└── .workbuddy/              # 会话数据与构建日志（勿删）
```

---

## 九、新会话接续 Prompt（直接复制）

```
继续开发「梭子 SORTS」项目（工作区 D:\SORTS(梭子)）。

先读这四份文档恢复上下文：
- docs/PROGRESS.md（进度与续接指南）
- docs/dev-setup.md（开发手册、端口、命令）
- docs/ide-setup.md（IDE 运行手册：Maven 导入、JDK 17、共享运行配置）
- docs/theme-design.md（主题规范：光阴似箭，日月如梭）

工程铁律：
1. 构建：在 AI 沙箱内必须用 bash scripts/mvn.sh（沙箱禁用了 MSYS 路径转换，裸 mvn 会报 classworlds 错误）；
   用户自己的终端 / IDEA / WSL / CI 用 backend/mvnw 即可。构建后确认单测全绿。
2. 服务名一律 sorts- 前缀，包名 com.sorts.*，跨模块调用用 OpenFeign，禁止跨库直连。
3. 提交粒度：**按功能分段提交，一个功能一次提交**，不要攒一堆再一起提交
   （Conventional Commits）。GitHub 推送由用户本人执行，AI 只负责本地提交。
4. 每个模块必须配套单元测试，与业务代码同步交付。
5. 中间件全部跑在 WSL Docker 中，Windows 侧用 localhost 访问：
   MySQL 3307（root/sorts_dev）、Redis 6380、Nacos 8848、RabbitMQ 5672。
   一键启动：在 WSL 内执行 bash scripts/wsl-middleware.sh start
   新增建表脚本后补执行：bash scripts/wsl-middleware.sh sql
6. 接口开发前先查 api-spec.json 对齐契约。
7. 时长口径：日程/统计对外一律「秒」（plannedDuration 例外，是分钟）。
8. AI 服务：模型走自实现的 OpenAI 兼容客户端（`sorts-ai/.../llm`，藏在 ChatModelClient 接口后）；
   密钥必须走环境变量 DEEPSEEK_API_KEY，未配置时接口返回 503 而非启动失败。
   写工具是「双钥匙」（sorts.ai.tool.allow-write + 请求 allowWrite），不要绕过 ToolRegistry 直连。
9. 内部接口：一律放 /internal/**（网关不路由该前缀），并标 @InternalApi；出站凭证由 common 的
   Feign 拦截器按路径白名单自动加，新增内部路径记得同步 sorts.internal.paths。
   网关会剥离外部伪造的 X-Internal-Token，服务端未配置凭证时 fail-closed。
10. 购买链路顺序不可颠倒：加锁 → 扣积分 → 本地事务落库；事务性落库必须放在独立 Bean
    （@Transactional 依赖代理，同类自调用会静默失效）。库存扣减走条件更新 UPDATE ... WHERE stock > 0。

当前请继续：M6 前端（Vue3 + Vite 工程化重写；主题「织锦流光」落地为 CSS tokens、织锦日历、
流光计时、穿梭过场；AI 流式对话 UI；商城与装扮页）。前端注意三点：
- 流式接口由 query 参数决定传输方式（/ai/chat 默认 SSE，?stream=false 走 JSON；/ai/plan 反之）；
- 分页字段是 list（PageData.list / MallItemPageVO.list），成功码是 code=0 —— 都不是 api-spec 里写的那个；
- AI 写操作需用户先确认，再带 allowWrite=true 发起请求。
```
