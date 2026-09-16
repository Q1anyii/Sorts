# 梭子 SORTS · 项目进度与续接指南

> **用法**：新会话开始前，把本文档 + `docs/theme-design.md` + `docs/dev-setup.md` 丢给 AI，并粘贴文末的「接续 Prompt」，即可无缝继续开发。
> 最后更新：2026-09-16 · 当前里程碑：**M0 完成、M1 完成**（构建通过，22 个单测全绿）

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
| 数据库 | MySQL 8 | WSL Ubuntu，localhost:3306 |
| 缓存 | Redis 7 | **端口 6380（默认端口 +1）** |
| 消息队列 | RabbitMQ 3.13（M3/M4 引入） | WSL Docker |
| 密码加密 | spring-security-crypto | 只引 crypto，不引整套 Security |
| AI 模型 | **DeepSeek** | 用户已确认 |

版本兼容性已核实：`Boot 3.2/3.3.x → Cloud 2023.0.x → SCA 2023.0.x`。

---

## 三、模块与端口

| 服务 | 模块目录 | 端口 | 状态 |
|---|---|---|---|
| 网关 | `backend/sorts-gateway` | 8080 | ✅ 骨架完成（路由 + 鉴权过滤器） |
| 用户服务 | `backend/sorts-user` | 8081 | ✅ 完成（注册登录/双令牌/积分） |
| 日程服务 | `backend/sorts-schedule` | 8082 | ⬜ 待开发 |
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

### M1 用户服务（✅ 已完成，待提交）

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

1. **Redis 实际端口为 6379**，与约定（6380 = 默认+1）不一致 —— 配置默认按 6380，需执行 `scripts/wsl-middleware.sh start` 迁移，或用 `REDIS_PORT=6379` 启动服务。
2. **Nacos / RabbitMQ 尚未安装**（检测 8848 / 5672 均无响应）—— 跑 `scripts/wsl-middleware.sh start` 即装即起。
3. 内部接口（`/users/points/change`）目前只依赖网关透传的用户头，缺少服务间密钥校验，M5 需补 `X-Internal-Token` 校验。
4. 网关尚未实现限流（M2 计划：Redis 令牌桶）。
5. `sorts-user` 尚无 `@SpringBootTest` 级别的集成测试（需要真实 DB/Redis，计划 M7 用 Testcontainers 或连 WSL 中间件）。

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

- 远端：`https://github.com/Q1anyii/sorts.git`（origin，已切换为 HTTPS）
- 凭据：Git Credential Manager 存储的 PAT
- **注意**：账户 `Q1anyiii`（SSH 密钥所属）已被 GitHub **封停**，不要用 SSH 推送；使用 `Q1anyii` 的 **fine-grained PAT**，且该令牌需把 `sorts` 仓库加入可访问列表（否则 403 `Write access to repository not granted`）。
- 提交规范：Conventional Commits（`feat(scope): subject`），**每完成一个功能点提交一次；每个模块单测通过后推送**。

---

## 七、后续路线图（含验收标准）

| 里程碑 | 内容 | 验收标准 |
|---|---|---|
| **M2 网关增强** | Redis 限流（令牌桶）、鉴权链路单测、路由单测 | 超过阈值的请求返回 429；过滤器单测覆盖白名单/过期/伪造头 |
| **M3 日程服务** | 日程 CRUD、计时状态机（start/pause/resume/end/cancel）、日历聚合视图、统计接口、完成后发 MQ 加积分（Feign 调 user） | 状态机非法流转被拒；计时区间累加正确；`time_record` 可回溯每段耗时 |
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
│   ├── pom.xml              # 父工程
│   ├── sorts-common/        # ✅ 公共模块
│   ├── sorts-gateway/       # ✅ 网关骨架
│   └── sorts-user/          # ✅ 用户服务
├── frontend/                # 单文件演示版（M6 重写为 Vite 工程）
├── docs/
│   ├── theme-design.md      # 主题设计规范
│   ├── dev-setup.md         # 开发手册
│   └── PROGRESS.md          # 本文档
├── scripts/
│   ├── mvn.sh               # 构建封装（必须用）
│   ├── wsl-middleware.sh    # 中间件一键脚本
│   └── sql/sorts_user.sql   # 用户库建表
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
3. 每个功能点完成 → git 提交一次（Conventional Commits）；每个模块单测通过 → 推送到
   https://github.com/Q1anyii/sorts.git（origin 已配 HTTPS + GCM 凭据）。
4. 每个模块必须配套单元测试，与业务代码同步交付。
5. Redis 端口 6380（默认+1），MySQL 3306，均在 WSL 内，Windows 侧用 localhost。
6. 接口开发前先查 api-spec.json 对齐契约。

当前请继续：M2 网关增强（Redis 限流 + 鉴权链路单测）。
```
