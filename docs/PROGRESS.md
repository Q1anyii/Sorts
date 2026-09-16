# 梭子 SORTS · 项目进度与续接指南

> **用法**：新会话开始前，把本文档 + `docs/theme-design.md` + `docs/dev-setup.md` 丢给 AI，并粘贴文末的「接续 Prompt」，即可无缝继续开发。
> 最后更新：2026-09-16 · 当前里程碑：**M0 / M1 / M2 / M3 完成**（构建通过，**105 个单测全绿**：common 6 + gateway 19 + user 16 + schedule 64）

---

## 一、项目定位

**梭子（SORTS）** —— 智能日程记录与管理平台。主题：**光阴似箭，日月如梭**。

核心功能：日程记录 + 自动耗时统计 · AI 辅助规划与周期性总结 · 积分激励（光阴砂）与个性化装扮 · 云端同步与多端适配（预留）。

**微服务架构**：请求 → 网关（鉴权 + 限流）→ 各微服务；跨模块调用一律 OpenFeign；每个服务独立数据库，禁止跨库直连。

---

## 二、技术栈与版本矩阵（已锁定，勿随意升级）

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 本机 22，编译目标 **17** | `maven.compiler.release=17` |
| Spring Boot | **3.3.4** | 父 POM 继承 |
| Spring Cloud | **2023.0.3** | |
| Spring Cloud Alibaba | **2023.0.3.2** | 对应 Nacos **2.4.3** |
| Spring AI | 1.0.0（M4 引入） | DeepSeek，OpenAI 协议兼容 |
| ORM | MyBatis-Plus 3.5.7 | `mybatis-plus-spring-boot3-starter` |
| 分布式锁 | Redisson 3.36.0（M5 引入） | |
| 数据库 | MySQL 8 | WSL Docker，**localhost:3307**（root / sorts_dev） |
| 缓存 | Redis 7 | 端口 **6380**（默认端口 +1），WSL Docker |
| 消息队列 | RabbitMQ 3.13 | WSL Docker，5672 / 15672（sorts / sorts_dev） |
| 密码加密 | spring-security-crypto | 只引 crypto，不引整套 Security |
| AI 模型 | **DeepSeek** | 用户已确认 |

版本兼容性已核实：`Boot 3.2/3.3.x → Cloud 2023.0.x → SCA 2023.0.x`。

---

## 三、模块与端口

| 服务 | 模块目录 | 端口 | 状态 |
|---|---|---|---|
| 网关 | `backend/sorts-gateway` | 8080 | ✅ 完成（路由 + 鉴权 + 限流） |
| 用户服务 | `backend/sorts-user` | 8081 | ✅ 完成（注册登录/双令牌/积分） |
| 日程服务 | `backend/sorts-schedule` | 8082 | ✅ 完成（CRUD/计时状态机/日历/统计） |
| AI 服务 | `backend/sorts-ai` | 8083 | ⬜ 待开发 |
| 通知服务 | `backend/sorts-notification` | 8084 | ⬜ 待开发 |
| 商城服务 | `backend/sorts-mall` | 8085 | ⬜ 待开发 |
| 公共模块 | `backend/sorts-common` | — | ✅ 完成（Result/异常/JWT 自动装配） |

命名规范：模块与 Spring `application.name` 一律 **sorts-` 前缀**，Java 包名 `com.sorts.*`，路由 `lb://sorts-xxx`。

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

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/register` | 注册并直接返回令牌；用户名重复抛 409 |
| POST | `/api/v1/auth/login` | 登录；用户不存在与密码错误返回同一提示（防用户名枚举） |
| POST | `/api/v1/auth/refresh` | **无感续期**：校验类型 + Redis 留存值 → 令牌轮换 |
| POST | `/api/v1/auth/logout` | 清除服务端 refresh token |
| GET | `/api/v1/users/me` | 当前用户信息 |
| PUT | `/api/v1/users/me` | 更新资料（仅覆盖非空字段） |
| GET | `/api/v1/users/points` | 光阴砂余额 + 最近流水 |
| POST | `/api/v1/users/points/change` | 积分变动（内部 Feign 调用，防超扣） |

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

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/schedules` | 创建日程（状态默认 PENDING、优先级默认 MEDIUM） |
| GET | `/api/v1/schedules` | 条件分页：view(day/week/month) / date / startDate / endDate / status / priority / tag / keyword / sort / order |
| POST | `/api/v1/schedules/batch` | 批量创建（AI 规划一键采纳） |
| GET | `/api/v1/schedules/active` | 当前穿梭中的日程 |
| GET/PUT/DELETE | `/api/v1/schedules/{id}` | 详情 / 更新（只覆盖非空字段）/ 逻辑删除 |
| POST | `/api/v1/schedules/{id}/start|pause|resume|end|cancel` | 计时状态机五连 |
| GET | `/api/v1/calendar` | 月历（`year`/`month` 缺省当月，补齐每一天含空天） |
| GET | `/api/v1/calendar/week` | 周视图（固定周一 → 周日 7 天） |
| GET | `/api/v1/calendar/today` | 今日概览：今日分布 + 穿梭中 + 即将开始 |
| GET | `/api/v1/statistics/summary` | 汇总（period=day/week/month/year） |
| GET | `/api/v1/statistics/trend` | 最近 N 天趋势（缺省 30，上限 365，空天补零） |
| GET | `/api/v1/statistics/tags` | 标签分布（按时长降序 + 占比，支持 period=all） |

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

---

## 五、关键决策记录

| 议题 | 结论 | 理由 |
|---|---|---|
| 前端路线 | **Vue3 + Vite 工程化重写** | 现有 `frontend/` 是单文件 CDN 演示页，后续功能多 |
| AI 模型 | **DeepSeek**（OpenAI 协议） | 已确认，Tool Calling 支持好、成本低 |
| 消息队列 | **RabbitMQ**（Docker） | 贴合需求文档，异步解耦日程完成→积分/通知 |
| 业务服务是否上 Spring Security | **否**，只用 crypto 做 BCrypt | 鉴权集中在网关，避免过滤器链重复建设 |
| 用户身份传递 | 网关写 `X-User-Id` 头，服务端 `@RequestHeader` 读取 | 简单、可测，避免 ThreadLocal 隐式传递 |
| 主题方向 | **「织锦流光」**（吸收流光与时间刻度） | 三方案对比见 `docs/theme-design.md` |
| 中间件部署 | WSL 内 Docker（Nacos/RabbitMQ）+ 本机 MySQL/Redis | 用户环境，Windows 侧用 localhost 直连 |

### 已知限制 / 待办技术债

1. ~~Redis 端口 / Nacos / RabbitMQ 未就绪~~ → **已解决**（2026-09-16）：全部通过 `scripts/wsl-middleware.sh start` 以 Docker 方式启动，Windows 侧端口探测 6380 / 3307 / 8848 / 5672 / 15672 均可达。
2. **WSL 内已无 MySQL**：数据库统一由 Docker 容器 `sorts-mysql` 承载（3307）；Windows 宿主上原有的 3306 实例不作为项目数据源。
3. 内部接口（`/users/points/change`）目前只依赖网关透传的用户头，缺少服务间密钥校验，M5 需补 `X-Internal-Token` 校验。
4. ~~网关尚未实现限流~~ → **已完成**（M2：Redis 令牌桶 + 429 统一响应）。
5. `sorts-user` 尚无 `@SpringBootTest` 级别的集成测试（需要真实 DB/Redis，计划 M7 用 Testcontainers 或连 WSL 中间件）。
6. WSL 命令受沙箱限制，AI 无法直接执行 WSL 内命令，中间件相关操作需用户手动执行脚本。
7. 网关尚无路由级限流差异化配置（当前全局限流），如需对登录接口单独收紧，在对应路由 `filters` 中覆盖 `RequestRateLimiter` 参数即可。

---

## 六、本地环境约定（重要）

### 构建（Maven 已损坏，必须用脚本）

```bash
bash scripts/mvn.sh                # clean install（全模块 + 单测）
bash scripts/mvn.sh clean test     # 只跑测试
```

> 原因：PATH 中 `E:\develop\...apache-maven-3.9.15/3.9.16` 安装损坏（报 classworlds 错误）。
> `scripts/mvn.sh` 直接用 java 启动 Maven Launcher（受管 Maven 位于 `~/.workbuddy/binaries/maven/apache-maven-3.9.16`），并自动：
> ① 转换 MSYS 路径为 Windows 路径；② 透传 `HTTP_PROXY/HTTPS_PROXY` 给 JVM（代理 `127.0.0.1:8531`，仅加速用，不影响 localhost）。
> `~/.m2/settings.xml` 已配置阿里云镜像。

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

| 里程碑 | 内容 | 验收标准 |
|---|---|---|
| ~~**M2 网关增强**~~ ✅ | ~~Redis 限流（令牌桶）、鉴权链路单测、路由单测~~ | 已完成：429 统一响应 + 19 个单测 |
| ~~**M3 日程服务**~~ ✅ | ~~日程 CRUD、计时状态机、日历聚合视图、统计接口、落梭发积分（Feign 调 user）~~ | 已完成：非法流转被拒；总时长按片段重算；`time_record` 可回溯每段耗时；64 个单测 |
| **M4 AI 服务** | Spring AI + DeepSeek 流式输出、**工具集（Tool Calling）**、规划生成、日/月/年总结（异步 + `ai_report` 表） | AI 能通过工具查日程/建日程/查统计；总结报告落库可查询 |
| **M5 商城 + 通知** | 商品/购买（Redisson 锁防超扣）/装扮仓库；通知列表/已读/定时提醒 | 并发购买不超卖；积分与商品发放最终一致 |
| **M6 前端** | Vue3+Vite 工程化重写、主题落地（CSS tokens/织锦日历/流光计时/穿梭过场）、AI 流式对话 UI | 主题规范 100% 落地；移动端可用 |
| **M7 CI/CD + 测试** | GitHub Actions（矩阵构建 6 个服务 → ACR 推送）、Testcontainers 集成测试 | 参考 `E:\工作文件\AgentProject\.github\workflows\acr-cicd.yml`，部署阶段留开关（当前不部署） |

### AI 工具集设计要点（M4，用户重点关注）

非 AI Native 项目 → 由各模块**显式授权** AI 可用能力，工具集自实现：

- `querySchedules(dateRange)`：读取日程，用于规划与总结
- `createSchedules(List<PlanItem>)`：把 AI 生成的规划**一键落库**（对应 `/schedules/batch`）
- `queryStatistics(period)`：取统计汇总供分析
- `savePlanToBoard(planText)`：用户给出规划文本 → AI 解析后写入规划表
- `queryPoints()` / `getProfile()`：个性化建议

实现方式：Spring AI `@Tool` 方法 + 在 ai-service 侧通过 OpenFeign 调用各服务（**权限边界**：只暴露读 + 有限写，写操作全部要求用户确认）。

---

## 八、目录结构现状

```
D:\SORTS(梭子)/
├── api-spec.json            # OpenAPI 契约（开发接口前先查它！）
├── 需求分析.docx             # 需求源文档（已忽略入库）
├── backend/
│   ├── pom.xml              # 父工程（已注册 4 个模块）
│   ├── sorts-common/        # ✅ 公共模块（Result/异常/JWT/PageData）
│   ├── sorts-gateway/       # ✅ 网关（路由 + 鉴权 + 限流）
│   ├── sorts-user/          # ✅ 用户服务
│   └── sorts-schedule/      # ✅ 日程服务（CRUD/计时/日历/统计）
├── frontend/                # 单文件演示版（M6 重写为 Vite 工程）
├── docs/
│   ├── theme-design.md      # 主题设计规范
│   ├── dev-setup.md         # 开发手册
│   └── PROGRESS.md          # 本文档
├── scripts/
│   ├── mvn.sh               # 构建封装（必须用）
│   ├── wsl-middleware.sh    # 中间件一键脚本（start/stop/status/sql/logs）
│   └── sql/
│       ├── 00-init-databases.sql   # 建 5 个库（先执行）
│       ├── sorts_user.sql          # 用户库建表
│       └── sorts_schedule.sql      # 日程库建表
└── .workbuddy/              # 会话数据与构建日志（勿删）
```

---

## 九、新会话接续 Prompt（直接复制）

```
继续开发「梭子 SORTS」项目（工作区 D:\SORTS(梭子)）。

先读这三份文档恢复上下文：
- docs/PROGRESS.md（进度与续接指南）
- docs/dev-setup.md（开发手册、端口、命令）
- docs/theme-design.md（主题规范：光阴似箭，日月如梭）

工程铁律：
1. 构建必须用 bash scripts/mvn.sh（本机 mvn 已损坏），构建后确认单测全绿。
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

当前请继续：M4 AI 服务（Spring AI + DeepSeek 流式输出 + 自实现工具集 Tool Calling + 规划生成 + 日/月/年总结落库 `ai_report`）。
```
